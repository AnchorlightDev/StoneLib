package dev.anchorlight.stonelib.map;

import dev.anchorlight.stonelib.entity.EntityTags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A rectangle of item frames holding maps - a picture screen, a notice board, a slideshow wall.
 * The frames are spawned once and kept; {@link #show} swaps which maps they hold.
 *
 * <p>A screen is described by a {@link Layout}: the bottom-left frame cell <i>as seen by someone
 * looking at the screen</i>, the direction the screen faces (towards the viewer), and its size in
 * maps. Tile {@code (0, 0)} is the top-left, matching {@link TileSlicer}'s order, so
 * {@code show(pool.mapIds(...))} lines up without any flipping.
 *
 * <p><b>Floating screens.</b> An item frame can only be spawned against a solid block. With
 * {@link Options#floating()} set, each empty backing cell gets a temporary block for the instant
 * of the spawn, the frame is made {@linkplain ItemFrame#setFixed fixed} (so it no longer needs
 * support), and the cell is put back exactly as it was. The frames then hang in mid-air.
 *
 * <p>Every frame is tagged with {@link EntityTags} under the screen's id, role {@value #ROLE} and
 * its tile index, so {@link #adopt} finds them again after a restart and
 * {@link EntityTags#removeOrphans} cleans up screens that were deleted.
 *
 * <p>Main thread only.
 */
public final class MapScreen {

    /** The {@link EntityTags} role given to a screen's frames. */
    public static final String ROLE = "map-frame";

    /**
     * Where a screen is and how big.
     *
     * @param world  the world it is in
     * @param x      bottom-left frame cell X, as seen by a viewer
     * @param y      bottom-left frame cell Y
     * @param z      bottom-left frame cell Z
     * @param facing the horizontal direction the screen faces (towards the viewer)
     * @param width  maps across
     * @param height maps down
     */
    public record Layout(World world, int x, int y, int z, BlockFace facing, int width, int height) {

        private static final Set<BlockFace> HORIZONTAL = Set.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

        public Layout {
            if (!HORIZONTAL.contains(facing)) {
                throw new IllegalArgumentException("A map screen must face NORTH, EAST, SOUTH or WEST, not " + facing);
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width and height must be positive");
            }
        }

        public int tiles() {
            return width * height;
        }
    }

    /**
     * How the frames look.
     *
     * @param glow      glow item frames, so the maps show at full brightness in a dim room
     * @param invisible hide the frame border (the map still shows); some client and server
     *                  combinations have been seen to hide the map too, so check it on yours
     * @param floating  hang the frames in mid-air instead of on a wall
     * @param persistent save the frames with the world. Turn it off for screens that are put up
     *                   only while something is showing: nothing is left behind by a crash, but
     *                   the plugin must call {@link #spawn} again when the chunk reloads
     */
    public record Options(boolean glow, boolean invisible, boolean floating, boolean persistent) {

        /** Glowing, visible borders, on a wall, saved with the world. */
        public static final Options DEFAULT = new Options(true, false, false, true);
    }

    /** One frame cell, and its index in row-major tile order. */
    public record Cell(int x, int y, int z, int index) {
    }

    private final EntityTags tags;
    private final String id;
    private final Layout layout;
    private final Options options;
    private final ItemFrame[] frames;

    public MapScreen(EntityTags tags, String id, Layout layout, Options options) {
        this.tags = tags;
        this.id = id;
        this.layout = layout;
        this.options = options == null ? Options.DEFAULT : options;
        this.frames = new ItemFrame[layout.tiles()];
    }

    public String id() {
        return id;
    }

    public Layout layout() {
        return layout;
    }

    public Options options() {
        return options;
    }

    /**
     * The block the viewer's right hand points along when looking at a screen that faces
     * {@code facing}. A viewer faces the opposite way to the screen.
     */
    public static BlockFace rightOf(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;   // viewer looks south; right is west
            case SOUTH -> BlockFace.EAST;   // viewer looks north; right is east
            case EAST -> BlockFace.NORTH;   // viewer looks west; right is north
            case WEST -> BlockFace.SOUTH;   // viewer looks east; right is south
            default -> throw new IllegalArgumentException("Not horizontal: " + facing);
        };
    }

    /** Every frame cell of {@code layout}, row-major from the top-left as the viewer sees it. */
    public static List<Cell> cells(Layout layout) {
        BlockFace right = rightOf(layout.facing());
        List<Cell> cells = new ArrayList<>(layout.tiles());
        for (int ty = 0; ty < layout.height(); ty++) {
            int up = layout.height() - 1 - ty;
            for (int tx = 0; tx < layout.width(); tx++) {
                cells.add(new Cell(
                        layout.x() + right.getModX() * tx,
                        layout.y() + up,
                        layout.z() + right.getModZ() * tx,
                        ty * layout.width() + tx));
            }
        }
        return cells;
    }

    /**
     * The layout for a screen whose bottom-left cell is {@code cell} as seen by a viewer looking
     * in {@code viewerFacing} (the direction the player creating it is looking).
     */
    public static Layout facingViewer(Block cell, BlockFace viewerFacing, int width, int height) {
        return new Layout(cell.getWorld(), cell.getX(), cell.getY(), cell.getZ(), viewerFacing.getOppositeFace(), width, height);
    }

    /** Whether every chunk the screen's frames sit in is loaded. Spawning into an unloaded chunk loads it. */
    public boolean chunksLoaded() {
        for (Cell cell : cells(layout)) {
            if (!layout.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) {
                return false;
            }
        }
        return true;
    }

    /** Whether any of the screen's frames sit in chunk ({@code chunkX}, {@code chunkZ}) of {@code world}. */
    public boolean touchesChunk(World world, int chunkX, int chunkZ) {
        if (!world.equals(layout.world())) {
            return false;
        }
        for (Cell cell : cells(layout)) {
            if ((cell.x() >> 4) == chunkX && (cell.z() >> 4) == chunkZ) {
                return true;
            }
        }
        return false;
    }

    /** Whether all of the screen's frames are present. */
    public boolean isSpawned() {
        for (ItemFrame frame : frames) {
            if (frame == null || !frame.isValid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Picks up this screen's frames that are already in the world (after a restart), by tag.
     * Returns how many were found; spawn the rest with {@link #spawn}.
     */
    public int adopt() {
        int found = 0;
        for (Entity entity : tags.find(List.of(layout.world()), id)) {
            if (entity instanceof ItemFrame frame && ROLE.equals(tags.role(entity).orElse(null))) {
                int index = tags.index(entity).orElse(-1);
                if (index >= 0 && index < frames.length && frames[index] == frame) {
                    continue; // already ours
                }
                if (index >= 0 && index < frames.length && (frames[index] == null || !frames[index].isValid())) {
                    frames[index] = frame;
                    found++;
                } else {
                    entity.remove(); // a duplicate, or left over from a screen of another size
                }
            }
        }
        return found;
    }

    /**
     * Spawns any frames that are missing. Returns how many could not be spawned (for example,
     * the cell is solid, or a protection plugin cancelled the spawn).
     */
    public int spawn() {
        int failed = 0;
        for (Cell cell : cells(layout)) {
            ItemFrame existing = frames[cell.index()];
            if (existing != null && existing.isValid()) {
                continue;
            }
            ItemFrame spawned = spawnFrame(cell);
            if (spawned == null) {
                failed++;
            } else {
                frames[cell.index()] = spawned;
            }
        }
        return failed;
    }

    private ItemFrame spawnFrame(Cell cell) {
        World world = layout.world();
        Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
        if (!block.isPassable()) {
            return null;
        }
        Block backing = block.getRelative(layout.facing().getOppositeFace());
        // A full snapshot, so a sign's text or a banner's pattern survives the swap.
        BlockState original = null;
        if (options.floating() && !backing.getType().isSolid()) {
            original = backing.getState();
            backing.setType(Material.BARRIER, false);
        }
        try {
            Class<? extends ItemFrame> type = options.glow() ? GlowItemFrame.class : ItemFrame.class;
            ItemFrame frame = world.spawn(block.getLocation(), type, f -> {
                f.setFacingDirection(layout.facing(), true);
                f.setFixed(true);
                f.setInvulnerable(true);
                f.setPersistent(options.persistent());
                f.setSilent(true);
                f.setVisible(!options.invisible());
                tags.tag(f, id, ROLE, cell.index());
            });
            return frame.isValid() ? frame : null;
        } catch (IllegalArgumentException noSupport) {
            return null;
        } finally {
            if (original != null) {
                original.update(true, false);
            }
        }
    }

    /**
     * Puts map {@code mapIds[i]} in tile {@code i}'s frame (row-major). A missing frame is
     * skipped; a {@code mapIds} shorter than the screen leaves the rest empty.
     */
    @SuppressWarnings("deprecation") // Bukkit.getMap(int) is the only id-to-view lookup.
    public void show(int[] mapIds) {
        for (int i = 0; i < frames.length; i++) {
            ItemFrame frame = frames[i];
            if (frame == null || !frame.isValid()) {
                continue;
            }
            if (mapIds == null || i >= mapIds.length) {
                frame.setItem(null, false);
                continue;
            }
            MapView view = Bukkit.getMap(mapIds[i]);
            if (view == null) {
                frame.setItem(null, false);
                continue;
            }
            ItemStack item = new ItemStack(Material.FILLED_MAP);
            if (item.getItemMeta() instanceof MapMeta meta) {
                meta.setMapView(view);
                item.setItemMeta(meta);
            }
            frame.setItem(item, false);
        }
    }

    /** Empties every frame. */
    public void clear() {
        show(new int[0]);
    }

    /** Removes the frames from the world. */
    public void remove() {
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] != null && frames[i].isValid()) {
                frames[i].remove();
            }
            frames[i] = null;
        }
        tags.removeGroup(List.of(layout.world()), id);
    }

    /** The frames that are present, by tile index. */
    public Map<Integer, ItemFrame> frames() {
        Map<Integer, ItemFrame> present = new TreeMap<>();
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] != null && frames[i].isValid()) {
                present.put(i, frames[i]);
            }
        }
        return present;
    }

    @Override
    public String toString() {
        return "MapScreen[" + id + " " + layout + " " + options + " frames=" + Arrays.stream(frames).filter(f -> f != null).count() + "]";
    }
}
