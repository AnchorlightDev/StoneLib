package dev.anchorlight.stonelib.block;

import dev.anchorlight.stonelib.region.Cuboid;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.EnumSet;
import java.util.Set;

/**
 * Block placement that never destroys what is already there: fills only replace empty space (air
 * and fire), and clears only remove the block type the plugin placed.
 *
 * <p>Use it for decorative fills inside player builds - portal surfaces, barriers, highlights - and
 * reuse {@link #wouldOverwrite} to veto vanilla placements, such as from a
 * {@code PortalCreateEvent}.
 *
 * <pre>{@code
 * Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
 * portal.setAxis(Axis.X);
 * int placed = SafeBlocks.fill(world, gate, portal);
 * int blocked = SafeBlocks.countOccupied(world, gate, Material.NETHER_PORTAL);
 * }</pre>
 */
public final class SafeBlocks {

    /** Blocks that count as empty space. Fire is included because lighting a portal frame replaces it. */
    private static final Set<Material> EMPTY = EnumSet.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.FIRE, Material.SOUL_FIRE);

    private SafeBlocks() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    public static boolean isEmpty(Material material) {
        return EMPTY.contains(material);
    }

    /** True when placing {@code planned} would change a block that isn't empty space. */
    public static boolean wouldOverwrite(Material existing, Material planned) {
        return existing != planned && !isEmpty(existing);
    }

    /**
     * Sets every empty block in the cuboid to {@code data}, without physics updates. Loads chunks as
     * needed; keep cuboids modest.
     *
     * @return how many blocks were placed
     */
    public static int fill(World world, Cuboid bounds, BlockData data) {
        Material planned = data.getMaterial();
        int placed = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material existing = block.getType();
                    if (existing != planned && !wouldOverwrite(existing, planned)) {
                        block.setBlockData(data, false);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /**
     * Turns every {@code type} block in the cuboid into air, without physics updates. Other blocks
     * are untouched.
     *
     * @return how many blocks were cleared
     */
    public static int clear(World world, Cuboid bounds, Material type) {
        int cleared = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == type) {
                        block.setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    /** How many blocks in the cuboid a {@link #fill} of {@code planned} would leave untouched. */
    public static int countOccupied(World world, Cuboid bounds, Material planned) {
        int occupied = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (wouldOverwrite(world.getBlockAt(x, y, z).getType(), planned)) {
                        occupied++;
                    }
                }
            }
        }
        return occupied;
    }
}
