package dev.anchorlight.stonelib.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Finding somewhere on real terrain to put something.
 *
 * <p>On a superflat world "the surface" is a constant and picking a spot is picking an {@code x}
 * and a {@code z}. On generated terrain it is none of those things: the column may top out in
 * ocean, in lava, in the canopy of a tree, on a one-block spire, or inside an overhang with the
 * actual ground forty blocks below. Dropping a supply crate or a boss at
 * {@code getHighestBlockAt(x, z) + 1} lands it in the sea or in the leaves often enough to be the
 * thing players notice.
 *
 * <p>So every lookup here is heightmap-aware and validated: {@link #surface} resolves a column
 * ignoring leaves, and {@link #standable} then rejects what is left if it is liquid, unstable or
 * too cramped to stand in. Callers that need a point rather than a column use
 * {@link #randomInsideBorder}, which samples until something passes.
 */
public final class Terrain {

    /** Headroom a placement needs above the ground block, in blocks. */
    private static final int REQUIRED_HEADROOM = 2;

    private static final Random RANDOM = new Random();

    private Terrain() {
    }

    // --------------------------------------------------------------- columns

    /**
     * The location just above the highest ground block in a column, ignoring leaves.
     *
     * <p>{@link HeightMap#MOTION_BLOCKING_NO_LEAVES} is the right map here: it skips foliage, so a
     * point under a forest canopy resolves to the forest floor rather than to the top of a tree,
     * but it still stops at water, so an ocean column resolves to the surface of the sea rather
     * than the sea bed. Whether that is acceptable is {@link #standable}'s question, not this one.
     *
     * @return the centre of the block above the ground, or null if the world is missing
     */
    public static Location surface(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    /**
     * The same column resolved to the sea or lava bed rather than to the liquid surface.
     *
     * <p>Use this when something must rest on solid ground even in water - a structure footing,
     * say - rather than float on it.
     */
    public static Location floor(World world, int x, int z) {
        if (world == null) {
            return null;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    // -------------------------------------------------------------- validity

    /**
     * Whether a surface location is somewhere a player or a mob could actually be put.
     *
     * <p>Rejects: a missing world, anything below the world floor or above its ceiling, a column
     * standing in or on liquid, ground that will not hold weight, and anywhere without
     * {@value #REQUIRED_HEADROOM} blocks of clear space above.
     */
    public static boolean standable(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        int y = location.getBlockY();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - REQUIRED_HEADROOM) {
            return false;
        }
        Block ground = world.getBlockAt(location.getBlockX(), y - 1, location.getBlockZ());
        if (!isSolidFooting(ground.getType())) {
            return false;
        }
        for (int offset = 0; offset < REQUIRED_HEADROOM; offset++) {
            Block above = world.getBlockAt(location.getBlockX(), y + offset, location.getBlockZ());
            if (!above.getType().isAir() && !isPassable(above.getType())) {
                return false;
            }
            if (above.isLiquid()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block can be stood on: solid, not liquid, and not something that gives way. */
    public static boolean isSolidFooting(Material material) {
        if (material == null || !material.isSolid()) {
            return false;
        }
        return switch (material) {
            // Burns, melts or drops the thing standing on it.
            case MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE,
                 POWDER_SNOW, SCAFFOLDING, CACTUS -> false;
            default -> true;
        };
    }

    /** Whether a block can be occupied - air, or something you can walk through. */
    public static boolean isPassable(Material material) {
        if (material == null) {
            return true;
        }
        if (material.isAir()) {
            return true;
        }
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, SNOW,
                 VINE, GLOW_LICHEN -> true;
            default -> false;
        };
    }

    // --------------------------------------------------------------- sampling

    /**
     * A random standable point inside the world border.
     *
     * <p>Samples uniformly by area rather than by radius - taking a uniform distance from the
     * centre clusters points near the middle, which on a growing border means supply drops keep
     * landing in the same trampled acre around spawn.
     *
     * @param minDistance keep-out radius around the border centre, so drops do not land on the hub
     * @param attempts    how many candidates to try before giving up
     * @return a standable location, or null when {@code attempts} candidates all failed. Callers
     *         must handle null: on a mostly-ocean map there may genuinely be nowhere to stand, and
     *         quietly returning the centre would stack everything on the hub.
     */
    public static Location randomInsideBorder(World world, double minDistance, int attempts) {
        return randomInsideBorder(world, minDistance, attempts, null);
    }

    /**
     * As {@link #randomInsideBorder(World, double, int)}, with an extra caller-supplied test - a
     * biome check, a distance-from-something check, a "not inside a claimed region" check.
     */
    public static Location randomInsideBorder(World world, double minDistance, int attempts,
                                              Predicate<Location> extraTest) {
        if (world == null) {
            return null;
        }
        WorldBorder border = world.getWorldBorder();
        Location centre = border.getCenter();
        // Inset so a point never lands exactly on the ring, where the player would take border damage.
        double radius = Math.max(1.0, border.getSize() / 2.0 - 6.0);
        double inner = Math.clamp(minDistance, 0.0, radius);

        for (int i = 0; i < Math.max(1, attempts); i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            // sqrt of a uniform sample spreads points evenly over the annulus by area.
            double t = RANDOM.nextDouble();
            double distance = Math.sqrt(inner * inner + t * (radius * radius - inner * inner));
            int x = (int) Math.round(centre.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(centre.getZ() + Math.sin(angle) * distance);

            Location candidate = surface(world, x, z);
            if (candidate == null || !border.isInside(candidate) || !standable(candidate)) {
                continue;
            }
            if (extraTest != null && !extraTest.test(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /**
     * Points evenly spaced around a circle, each resolved to the surface.
     *
     * <p>Unstandable points are skipped rather than substituted, so the returned list may be
     * shorter than {@code count} - and may be empty. A ring of mob spawns with three of its eight
     * points silently collapsed onto the centre is worse than a ring of five.
     */
    public static List<Location> ring(World world, Location centre, double radius, int count) {
        List<Location> out = new ArrayList<>();
        if (world == null || centre == null || count <= 0) {
            return out;
        }
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            int x = (int) Math.round(centre.getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(centre.getZ() + Math.sin(angle) * radius);
            Location point = surface(world, x, z);
            if (standable(point)) {
                out.add(point);
            }
        }
        return out;
    }

    /**
     * Searches outward from a point for somewhere standable.
     *
     * <p>For when a location is already chosen - a configured coordinate, a structure origin - and
     * the question is only whether the ground nearby will do.
     *
     * @param maxRadius how far out to search, in blocks
     */
    public static Location nearestStandable(World world, int x, int z, int maxRadius) {
        Location direct = surface(world, x, z);
        if (standable(direct)) {
            return direct;
        }
        for (int r = 1; r <= Math.max(1, maxRadius); r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Perimeter of the square only; the interior was covered by a smaller r.
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    Location candidate = surface(world, x + dx, z + dz);
                    if (standable(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** {@code x, y, z} for a log line. Null-safe. */
    public static String describe(Location location) {
        if (location == null) {
            return "unknown";
        }
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
