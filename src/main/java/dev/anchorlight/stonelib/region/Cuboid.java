package dev.anchorlight.stonelib.region;

import java.util.Objects;

/**
 * An axis-aligned, inclusive block-coordinate box inside one world.
 *
 * <p>The canonical constructor normalises the corners, so callers can pass any two opposite
 * corners (for example two wand clicks) without sorting them first. The world is stored by name so
 * a cuboid can be created, persisted and compared without the world being loaded.
 *
 * <pre>{@code
 * Cuboid gate = new Cuboid("world", 12, 64, 5, 10, 68, 5);
 * gate.contains(11, 66, 5); // true
 * gate.sizeX();             // 3
 * }</pre>
 */
public record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Cuboid {
        Objects.requireNonNull(world, "world");
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }
        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        if (minZ > maxZ) {
            int tmp = minZ;
            minZ = maxZ;
            maxZ = tmp;
        }
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return world.equals(worldName) && contains(x, y, z);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** Number of blocks inside the cuboid. A {@code long} because large selections overflow an int. */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public int minChunkX() {
        return minX >> 4;
    }

    public int maxChunkX() {
        return maxX >> 4;
    }

    public int minChunkZ() {
        return minZ >> 4;
    }

    public int maxChunkZ() {
        return maxZ >> 4;
    }
}
