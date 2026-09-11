package dev.anchorlight.stonelib.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Buckets regions by (world, chunk) so a position lookup only scans the regions that overlap the
 * position's chunk, instead of every region the plugin knows about. Cheap enough to query on every
 * block-boundary move.
 *
 * <p>The index is rebuilt wholesale and swapped atomically, so reads never need a lock and never
 * see a half-built index. Rebuild it whenever the region set changes.
 *
 * <pre>{@code
 * RegionIndex<Arena> arenas = new RegionIndex<>(Arena::bounds);
 * arenas.rebuild(arenaService.all());
 *
 * arenas.find("world", x, y, z).ifPresent(arena -> ...);
 * }</pre>
 *
 * @param <T> the region type; any object that can report its {@link Cuboid}
 */
public final class RegionIndex<T> {

    private final Function<? super T, Cuboid> boundsOf;
    private volatile Map<String, Map<Long, List<T>>> index = Collections.emptyMap();

    public RegionIndex(Function<? super T, Cuboid> boundsOf) {
        this.boundsOf = Objects.requireNonNull(boundsOf, "boundsOf");
    }

    public synchronized void rebuild(Collection<? extends T> regions) {
        Map<String, Map<Long, List<T>>> next = new HashMap<>();
        for (T region : regions) {
            Cuboid bounds = boundsOf.apply(region);
            Map<Long, List<T>> worldBuckets = next.computeIfAbsent(bounds.world(), key -> new HashMap<>());
            for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
                for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
                    worldBuckets.computeIfAbsent(chunkKey(chunkX, chunkZ), key -> new ArrayList<>()).add(region);
                }
            }
        }
        this.index = next;
    }

    public Cuboid boundsOf(T region) {
        return boundsOf.apply(region);
    }

    /** Every region overlapping the chunk, in insertion order. Never null. */
    public List<T> candidatesFor(String world, int chunkX, int chunkZ) {
        Map<Long, List<T>> worldBuckets = index.get(world);
        if (worldBuckets == null) {
            return List.of();
        }
        List<T> candidates = worldBuckets.get(chunkKey(chunkX, chunkZ));
        return candidates == null ? List.of() : Collections.unmodifiableList(candidates);
    }

    /** Every region containing the block, in insertion order. */
    public List<T> allAt(String world, int x, int y, int z) {
        List<T> result = new ArrayList<>();
        for (T candidate : candidatesFor(world, x >> 4, z >> 4)) {
            if (boundsOf.apply(candidate).contains(x, y, z)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** The first region (in insertion order) containing the block. */
    public Optional<T> find(String world, int x, int y, int z) {
        for (T candidate : candidatesFor(world, x >> 4, z >> 4)) {
            if (boundsOf.apply(candidate).contains(x, y, z)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
