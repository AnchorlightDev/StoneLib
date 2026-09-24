package dev.anchorlight.stonelib.ownership;

/**
 * Packs a block position into one {@code long}, using the same layout as vanilla's
 * {@code BlockPos.asLong}: 26 bits of X, 26 bits of Z, 12 bits of Y.
 *
 * <p>That covers X and Z to &plusmn;33,554,431 (past the world border) and Y from -2048 to 2047
 * (well beyond the -64 to 320 of a vanilla overworld). Negative coordinates round-trip correctly,
 * which is the part a hand-rolled {@code x << 32 | z} gets wrong.
 */
public final class BlockPositions {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int X_SHIFT = XZ_BITS + Y_BITS;
    private static final int Z_SHIFT = Y_BITS;

    private BlockPositions() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public static int x(long packed) {
        return (int) (packed >> X_SHIFT);
    }

    public static int y(long packed) {
        return (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long packed) {
        return (int) (packed << (64 - X_SHIFT) >> (64 - XZ_BITS));
    }
}
