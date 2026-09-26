package dev.anchorlight.stonelib.region;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's in-progress two-corner selection, keyed by UUID. Pair it with
 * {@link SelectionWand} for click-to-select, or set corners from commands.
 *
 * <p>Memory-only; call {@link #clear(UUID)} on quit if selections shouldn't outlive a session.
 */
public final class SelectionManager {

    /** One selected corner. */
    public record Point(String world, int x, int y, int z) {
    }

    private final Map<UUID, Point> first = new ConcurrentHashMap<>();
    private final Map<UUID, Point> second = new ConcurrentHashMap<>();

    public void setFirst(UUID player, String world, int x, int y, int z) {
        first.put(player, new Point(world, x, y, z));
    }

    public void setSecond(UUID player, String world, int x, int y, int z) {
        second.put(player, new Point(world, x, y, z));
    }

    public Optional<Point> first(UUID player) {
        return Optional.ofNullable(first.get(player));
    }

    public Optional<Point> second(UUID player) {
        return Optional.ofNullable(second.get(player));
    }

    /** The selected cuboid, or empty until both corners are set in the same world. */
    public Optional<Cuboid> selection(UUID player) {
        Point a = first.get(player);
        Point b = second.get(player);
        if (a == null || b == null || !a.world().equals(b.world())) {
            return Optional.empty();
        }
        return Optional.of(new Cuboid(a.world(), a.x(), a.y(), a.z(), b.x(), b.y(), b.z()));
    }

    public void clear(UUID player) {
        first.remove(player);
        second.remove(player);
    }
}
