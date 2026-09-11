package dev.anchorlight.StoneLib.render;

import dev.anchorlight.StoneLib.scheduler.SchedulerService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * One async loop that draws every registered {@link Renderable}, culled to the players who can
 * actually see it.
 *
 * <p>Deliberately <em>one</em> loop for the whole plugin rather than a task per player: a hundred
 * players with active cosmetics is one iteration over a hundred entries, not a hundred scheduled
 * tasks competing for the scheduler.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>Player positions cannot be read safely off the server thread, so the loop is two tasks that
 * behave as one:</p>
 *
 * <ol>
 *   <li>a very cheap sync task that snapshots every online player's position each tick, and</li>
 *   <li>the async render task, which culls and draws against that snapshot.</li>
 * </ol>
 *
 * <p>The snapshot does no allocation per renderable and no world access, so the cost on the server
 * thread stays flat as cosmetics are added. Renderables see positions up to one tick old.</p>
 */
public final class RenderLoop {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final long periodTicks;
    private final double defaultViewDistance;

    /**
     * Owner to {@link Renderable#key()} to renderable. Nested rather than flat because a player
     * wears several cosmetics at once, and removing everything for one player on quit has to be a
     * single lookup.
     */
    private final Map<UUID, Map<String, Renderable>> renderables = new ConcurrentHashMap<>();
    private final Map<UUID, Location> positions = new ConcurrentHashMap<>();
    private final AtomicLong tick = new AtomicLong();

    private volatile List<Player> onlineSnapshot = List.of();
    private volatile boolean running;

    /**
     * @param periodTicks         ticks between frames; 1 for smooth trails, higher for cheaper
     *                            effects
     * @param defaultViewDistance how far away a renderable is visible, in blocks, unless it
     *                            overrides {@link Renderable#viewDistance()}
     */
    public RenderLoop(Plugin plugin, SchedulerService scheduler, long periodTicks, double defaultViewDistance) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.periodTicks = Math.max(1, periodTicks);
        this.defaultViewDistance = defaultViewDistance;
    }

    /** Starts both tasks. Safe to call twice; the second call does nothing. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.runTimer(this::snapshot, 0L, periodTicks);
        scheduler.runAsyncTimer(this::renderFrame, periodTicks, periodTicks);
    }

    /**
     * Stops drawing and cleans up every renderable. The tasks themselves are owned by the
     * {@link SchedulerService}, so {@code cancelAll()} on disable cancels them.
     */
    public synchronized void stop() {
        running = false;
        for (UUID owner : List.copyOf(renderables.keySet())) {
            remove(owner);
        }
    }

    /**
     * Registers a renderable, replacing and cleaning up whatever the owner had under the same
     * {@link Renderable#key()}. Renderables with different keys coexist.
     */
    public void add(Renderable renderable) {
        Renderable previous = renderables
                .computeIfAbsent(renderable.owner(), owner -> new ConcurrentHashMap<>())
                .put(renderable.key(), renderable);
        if (previous != null) {
            cleanUp(previous);
        }
    }

    /** Removes everything an owner has and cleans it up. */
    public void remove(UUID owner) {
        Map<String, Renderable> removed = renderables.remove(owner);
        positions.remove(owner);
        if (removed != null) {
            removed.values().forEach(this::cleanUp);
        }
    }

    /** Removes just one of an owner's renderables. */
    public void remove(UUID owner, String key) {
        Map<String, Renderable> owned = renderables.get(owner);
        if (owned == null) {
            return;
        }
        Renderable removed = owned.remove(key);
        if (owned.isEmpty()) {
            renderables.remove(owner);
        }
        if (removed != null) {
            cleanUp(removed);
        }
    }

    /** One of an owner's renderables, or null. */
    public Renderable get(UUID owner, String key) {
        Map<String, Renderable> owned = renderables.get(owner);
        return owned == null ? null : owned.get(key);
    }

    /** How many renderables are active in total. For a diagnostics command. */
    public int size() {
        int total = 0;
        for (Map<String, Renderable> owned : renderables.values()) {
            total += owned.size();
        }
        return total;
    }

    /** Runs on the server thread: copy out what the async pass needs, and nothing more. */
    private void snapshot() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<UUID, Location> updated = new HashMap<>(online.size());
        for (Player player : online) {
            updated.put(player.getUniqueId(), player.getLocation());
        }
        positions.keySet().retainAll(updated.keySet());
        positions.putAll(updated);
        onlineSnapshot = online;
    }

    /** Runs off the server thread: cull, then draw. */
    private void renderFrame() {
        if (!running || renderables.isEmpty()) {
            return;
        }
        long frame = tick.incrementAndGet();
        List<Player> online = onlineSnapshot;

        for (Map.Entry<UUID, Map<String, Renderable>> entry : renderables.entrySet()) {
            UUID ownerId = entry.getKey();

            Location anchor = positions.get(ownerId);
            if (anchor == null) {
                // Owner is offline. The join/quit listener owns removal; skip quietly.
                continue;
            }

            // Culling depends only on the owner's position, so it is done once for all of their
            // renderables rather than once each.
            Player owner = null;
            for (Player player : online) {
                if (player.getUniqueId().equals(ownerId)) {
                    owner = player;
                    break;
                }
            }
            List<Player> defaultViewers = null;

            for (Renderable renderable : entry.getValue().values()) {
                try {
                    if (!renderable.active()) {
                        continue;
                    }

                    List<Player> viewers;
                    if (renderable.viewDistance() > 0) {
                        viewers = cull(online, anchor, renderable.viewDistance());
                    } else {
                        if (defaultViewers == null) {
                            defaultViewers = cull(online, anchor, defaultViewDistance);
                        }
                        viewers = defaultViewers;
                    }
                    if (viewers.isEmpty()) {
                        continue;
                    }

                    renderable.render(new RenderContext(frame, owner, anchor.clone(), viewers));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Renderable " + renderable.key() + " for " + ownerId
                                    + " threw; it stays registered", e);
                }
            }
        }
    }

    /**
     * Distance culling. Compares squared distances and rejects on world first, so the common case
     * of a player on another world costs a reference comparison.
     */
    private List<Player> cull(List<Player> online, Location anchor, double distance) {
        double maxSquared = distance * distance;
        List<Player> viewers = new ArrayList<>();
        for (Player player : online) {
            Location position = positions.get(player.getUniqueId());
            if (position == null || position.getWorld() == null || !position.getWorld().equals(anchor.getWorld())) {
                continue;
            }
            if (position.distanceSquared(anchor) <= maxSquared) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /** Clean-up may touch the world, so it always runs on the main thread. */
    private void cleanUp(Renderable renderable) {
        if (Bukkit.isPrimaryThread()) {
            runCleanUp(renderable);
        } else {
            scheduler.runSync(() -> runCleanUp(renderable));
        }
    }

    private void runCleanUp(Renderable renderable) {
        try {
            renderable.cleanUp();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Clean-up failed for " + renderable.owner(), e);
        }
    }
}
