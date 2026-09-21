package dev.anchorlight.stonelib.region;

/**
 * Packs a chunk coordinate pair into a single {@code long} for use as a map key, and converts
 * block coordinates to chunk coordinates.
 *
 * <p>Both operations look trivial and are quietly wrong if written by hand. The pack is reversible
 * only because the two halves never overlap, which holds only while the mask and the shift agree;
 * and {@link #ofBlock} must <em>floor</em> rather than truncate, because {@code -1 / 16} is
 * {@code 0} and block {@code -1} is in chunk {@code -1}. Getting either wrong merges or misplaces
 * chunks either side of the origin — which is where spawn, and therefore most of what a plugin
 * cares about, tends to be.
 *
 * <pre>{@code
 * Map<Long, List<Region>> buckets = new HashMap<>();
 * buckets.computeIfAbsent(ChunkKey.of(chunkX, chunkZ), k -> new ArrayList<>()).add(region);
 *
 * long key = ChunkKey.ofBlock(block.getX(), block.getZ());
 * int chunkX = ChunkKey.unpackX(key);
 * }</pre>
 */
public final class ChunkKey {

    private ChunkKey() {
    }

    /** Packs chunk coordinates into one key. Reversible via {@link #unpackX} / {@link #unpackZ}. */
    public static long of(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    /** Packs the chunk containing the given block coordinates. */
    public static long ofBlock(int blockX, int blockZ) {
        return of(chunkOf(blockX), chunkOf(blockZ));
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    /**
     * Block coordinate to chunk coordinate.
     *
     * <p>An arithmetic shift rather than a division: {@code >> 4} floors, where {@code / 16}
     * truncates toward zero and puts block {@code -1} in chunk {@code 0}.
     */
    public static int chunkOf(int blockCoordinate) {
        return blockCoordinate >> 4;
    }
}
