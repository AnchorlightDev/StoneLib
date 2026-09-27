package dev.anchorlight.stonelib.ownership;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who placed a block - lava, fire, a respawn anchor - so damage it later causes can be attributed.
 *
 * <h2>Keys</h2>
 * Positions are keyed by world UUID plus one packed {@code long} per block, never by a
 * {@code Block} or a formatted string. {@code Block} objects are throwaway views, so a map keyed by
 * them (and a {@code WeakHashMap} especially) loses entries as soon as the view is collected; and a
 * string key allocates on every lookup, which turns a radius search into thousands of garbage
 * strings per damage tick.
 *
 * <h2>Expiry</h2>
 * Every entry carries an expiry. An owner that never expires grants the placer's protection - or
 * blame - to anything that later happens near that spot for the rest of the server's uptime, long
 * after the block itself has changed. Expired entries read as absent immediately and are removed by
 * {@link #purgeExpired()}, which is cheap enough to run on a timer.
 *
 * <p>Thread-safe, and free of Bukkit types, so it is safe from any Folia region thread.
 */
public final class BlockOwnership {

    private final Map<UUID, Map<Long, Entry>> worlds = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long ttlMillis;

    /** @param ttl how long an entry is honoured after it is recorded */
    public BlockOwnership(Duration ttl) {
        this(ttl, System::currentTimeMillis);
    }

    /** For tests: supply the clock in milliseconds. */
    public BlockOwnership(Duration ttl, LongSupplier clock) {
        this.clock = clock;
        setTtl(ttl);
    }

    /** Changes the lifetime of entries recorded from now on, e.g. after a config reload. */
    public void setTtl(Duration ttl) {
        this.ttlMillis = ttl == null ? 0 : Math.max(0, ttl.toMillis());
    }

    /** The owner and position found by {@link #nearest}. */
    public record Match(UUID owner, int x, int y, int z, double distanceSquared) {
    }

    private record Entry(UUID owner, long expiresAt) {
    }

    public void put(UUID world, int x, int y, int z, UUID owner) {
        if (world == null || owner == null || ttlMillis <= 0) {
            return;
        }
        worlds.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>())
                .put(BlockPositions.pack(x, y, z), new Entry(owner, clock.getAsLong() + ttlMillis));
    }

    public void remove(UUID world, int x, int y, int z) {
        Map<Long, Entry> blocks = worlds.get(world);
        if (blocks != null) {
            blocks.remove(BlockPositions.pack(x, y, z));
        }
    }

    /** The owner recorded at exactly this block, or null. */
    public UUID owner(UUID world, int x, int y, int z) {
        Map<Long, Entry> blocks = worlds.get(world);
        if (blocks == null) {
            return null;
        }
        Entry entry = blocks.get(BlockPositions.pack(x, y, z));
        return live(entry) ? entry.owner() : null;
    }

    /**
     * The closest recorded owner within {@code radius} blocks (a cube, measured from block centres
     * by Euclidean distance), or null. Ties resolve to the first found, which is stable.
     *
     * <p>This returns the <em>nearest</em> owner, not the first one a scan happens to reach, which
     * matters when two players have both placed lava near the victim.
     */
    public Match nearest(UUID world, double px, double py, double pz, int radius) {
        Map<Long, Entry> blocks = worlds.get(world);
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        int bx = (int) Math.floor(px);
        int by = (int) Math.floor(py);
        int bz = (int) Math.floor(pz);
        long now = clock.getAsLong();
        Match best = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = bx + dx;
                    int y = by + dy;
                    int z = bz + dz;
                    Entry entry = blocks.get(BlockPositions.pack(x, y, z));
                    if (entry == null || entry.expiresAt() <= now) {
                        continue;
                    }
                    double cx = x + 0.5 - px;
                    double cy = y + 0.5 - py;
                    double cz = z + 0.5 - pz;
                    double distance = cx * cx + cy * cy + cz * cz;
                    if (best == null || distance < best.distanceSquared()) {
                        best = new Match(entry.owner(), x, y, z, distance);
                    }
                }
            }
        }
        return best;
    }

    /** Removes expired entries. @return how many were removed */
    public int purgeExpired() {
        long now = clock.getAsLong();
        int[] removed = {0};
        worlds.values().forEach(blocks -> blocks.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expiresAt() <= now;
            if (expired) {
                removed[0]++;
            }
            return expired;
        }));
        worlds.values().removeIf(Map::isEmpty);
        return removed[0];
    }

    /** Live entries across every world. */
    public int size() {
        long now = clock.getAsLong();
        return worlds.values().stream()
                .mapToInt(blocks -> (int) blocks.values().stream().filter(e -> e.expiresAt() > now).count())
                .sum();
    }

    public void clear() {
        worlds.clear();
    }

    private boolean live(Entry entry) {
        return entry != null && entry.expiresAt() > clock.getAsLong();
    }
}
