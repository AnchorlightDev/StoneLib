package dev.anchorlight.stonelib.display;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Named bossbars, each shown to whoever should currently be able to see it.
 *
 * <p>Two visibility models, because events need both. A <em>global</em> bar is shown to everyone
 * online - a community goal filling, time left in the event. A <em>scoped</em> bar is shown only to
 * players within a radius of a point - a boss fight, a world event happening in one place. Scoped
 * is the one worth having a class for: the audience changes as people walk around, so it has to be
 * recomputed, and the show/hide calls have to be applied as a delta or the bar flickers every tick.
 *
 * <p>Call {@link #tick()} on a repeating task - every 10 to 20 ticks is plenty - to refresh
 * audiences. Call {@link #removeAll()} in {@code onDisable}: a bossbar left showing survives the
 * plugin being unloaded, and only a relog clears it.
 *
 * <p>Main thread only.
 */
public final class ProgressBars {

    /** One tracked bar and the rule for who sees it. */
    private static final class Tracked {
        final BossBar bar;
        /** Null for a global bar. */
        Location centre;
        double radius;
        final Set<UUID> viewers = new HashSet<>();

        Tracked(BossBar bar, Location centre, double radius) {
            this.bar = bar;
            this.centre = centre;
            this.radius = radius;
        }

        boolean global() {
            return centre == null;
        }
    }

    private final Map<String, Tracked> bars = new LinkedHashMap<>();

    /**
     * Creates or replaces a bar shown to everyone online.
     *
     * @param id       caller-chosen key, used to update and remove the bar later
     * @param progress initial fill, clamped to [0, 1]
     */
    public BossBar global(String id, Component name, float progress,
                          BossBar.Color colour, BossBar.Overlay overlay) {
        return register(id, name, progress, colour, overlay, null, 0);
    }

    /**
     * Creates or replaces a bar shown only within {@code radius} blocks of {@code centre}.
     *
     * <p>Players in another world never see it, whatever the radius.
     */
    public BossBar scoped(String id, Component name, float progress,
                          BossBar.Color colour, BossBar.Overlay overlay,
                          Location centre, double radius) {
        return register(id, name, progress, colour, overlay, centre, radius);
    }

    private BossBar register(String id, Component name, float progress,
                             BossBar.Color colour, BossBar.Overlay overlay,
                             Location centre, double radius) {
        remove(id);
        BossBar bar = BossBar.bossBar(name, Math.clamp(progress, 0f, 1f), colour, overlay);
        bars.put(id, new Tracked(bar, centre == null ? null : centre.clone(), radius));
        tick();
        return bar;
    }

    /** Whether a bar with this id is currently registered. */
    public boolean has(String id) {
        return bars.containsKey(id);
    }

    public BossBar get(String id) {
        Tracked tracked = bars.get(id);
        return tracked == null ? null : tracked.bar;
    }

    /** Updates the fill of an existing bar. No-op when the id is unknown. */
    public void progress(String id, float progress) {
        Tracked tracked = bars.get(id);
        if (tracked != null) {
            tracked.bar.progress(Math.clamp(progress, 0f, 1f));
        }
    }

    /** Updates the label of an existing bar. No-op when the id is unknown. */
    public void name(String id, Component name) {
        Tracked tracked = bars.get(id);
        if (tracked != null) {
            tracked.bar.name(name);
        }
    }

    public void colour(String id, BossBar.Color colour) {
        Tracked tracked = bars.get(id);
        if (tracked != null) {
            tracked.bar.color(colour);
        }
    }

    /** Moves a scoped bar, e.g. to follow a boss. No-op for a global bar or an unknown id. */
    public void moveTo(String id, Location centre) {
        Tracked tracked = bars.get(id);
        if (tracked != null && centre != null && !tracked.global()) {
            tracked.centre = centre.clone();
        }
    }

    /** Hides a bar from everyone currently seeing it and forgets it. */
    public void remove(String id) {
        Tracked tracked = bars.remove(id);
        if (tracked == null) {
            return;
        }
        for (UUID uuid : tracked.viewers) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(tracked.bar);
            }
        }
        tracked.viewers.clear();
    }

    /**
     * Removes every bar. Call this in {@code onDisable}.
     *
     * <p>A bossbar is client state: one left showing when the plugin unloads stays on screen until
     * the player relogs, and no later reload will clear it because nothing remembers it exists.
     */
    public void removeAll() {
        for (String id : new ArrayList<>(bars.keySet())) {
            remove(id);
        }
    }

    /**
     * Recomputes who can see each bar and applies the difference.
     *
     * <p>Only the delta is applied - players who newly qualify are shown the bar and players who no
     * longer qualify have it hidden. Re-showing a bar to someone already seeing it every tick is
     * what makes a scoped bar flicker.
     */
    public void tick() {
        if (bars.isEmpty()) {
            return;
        }
        Map<String, Set<UUID>> wanted = new HashMap<>();
        for (Map.Entry<String, Tracked> entry : bars.entrySet()) {
            wanted.put(entry.getKey(), audienceFor(entry.getValue()));
        }
        for (Map.Entry<String, Tracked> entry : bars.entrySet()) {
            Tracked tracked = entry.getValue();
            Set<UUID> target = wanted.get(entry.getKey());

            for (UUID uuid : target) {
                if (tracked.viewers.contains(uuid)) {
                    continue;
                }
                Player player = org.bukkit.Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.showBossBar(tracked.bar);
                }
            }
            for (UUID uuid : new ArrayList<>(tracked.viewers)) {
                if (target.contains(uuid)) {
                    continue;
                }
                Player player = org.bukkit.Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.hideBossBar(tracked.bar);
                }
            }
            tracked.viewers.clear();
            tracked.viewers.addAll(target);
        }
    }

    private Set<UUID> audienceFor(Tracked tracked) {
        Set<UUID> out = new HashSet<>();
        if (tracked.global()) {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                out.add(player.getUniqueId());
            }
            return out;
        }
        Location centre = tracked.centre;
        if (centre == null || centre.getWorld() == null) {
            return out;
        }
        double radiusSquared = tracked.radius * tracked.radius;
        for (Player player : centre.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(centre) <= radiusSquared) {
                out.add(player.getUniqueId());
            }
        }
        return out;
    }

    /** Drops a disconnecting player from every audience, so nothing holds their UUID. */
    public void forget(UUID uuid) {
        for (Tracked tracked : bars.values()) {
            tracked.viewers.remove(uuid);
        }
    }

    /** Registered bar ids, for diagnostics. */
    public List<String> ids() {
        return List.copyOf(bars.keySet());
    }
}
