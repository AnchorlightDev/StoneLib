package dev.anchorlight.stonelib.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * A screen's permanent set of map ids: {@code width x height} maps per slot, for {@code slots}
 * slots (one per slide). Ids are minted once and reused for the life of the screen, so changing
 * what a slot shows only redraws its maps, and switching slide is just handing the frames a
 * different slot's maps - which the client already has cached, so the switch is instant.
 *
 * <p>Minting a map id never repeats for a slot that already has ids in the {@link MapIdStore}, so
 * calling {@link #ensureAllocated} on every load is safe and leaks nothing.
 *
 * <p>Bukkit-free: the caller supplies the minting function, typically
 * {@code () -> Bukkit.createMap(world).getId()}, which must run on the main thread.
 *
 * <pre>{@code
 * MapPool pool = new MapPool(store, () -> Bukkit.createMap(world).getId());
 * List<MapIdStore.Tile> slide0 = pool.ensureSlot("lobby", 0, 4, 3);
 * }</pre>
 *
 * <p>Generalised from PixelPost's board map pool.
 */
public final class MapPool {

    private final MapIdStore store;
    private final IntSupplier mint;

    public MapPool(MapIdStore store, IntSupplier mint) {
        this.store = store;
        this.mint = mint;
    }

    /**
     * Makes sure every one of {@code slots} slots has its {@code width x height} map ids, minting
     * only for slots that have none yet, and returns all of them, slot by slot, row-major.
     */
    public List<MapIdStore.Tile> ensureAllocated(String owner, int width, int height, int slots) throws Exception {
        List<MapIdStore.Tile> all = new ArrayList<>(width * height * slots);
        for (int slot = 0; slot < slots; slot++) {
            all.addAll(ensureSlot(owner, slot, width, height));
        }
        return all;
    }

    /** One slot's tiles, row-major (top row first), minted and stored first if it has none. */
    public List<MapIdStore.Tile> ensureSlot(String owner, int slot, int width, int height) throws Exception {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        List<MapIdStore.Tile> existing = store.load(owner, slot);
        if (existing.isEmpty()) {
            List<MapIdStore.Tile> minted = new ArrayList<>(width * height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    minted.add(new MapIdStore.Tile(x, y, mint.getAsInt()));
                }
            }
            store.save(owner, slot, minted);
            return minted;
        }
        return sorted(existing);
    }

    /** One slot's stored tiles, row-major, without minting anything; empty when not allocated. */
    public List<MapIdStore.Tile> slot(String owner, int slot) throws Exception {
        return sorted(store.load(owner, slot));
    }

    /** One slot's map ids in row-major order, minting first if needed. */
    public int[] mapIds(String owner, int slot, int width, int height) throws Exception {
        return ensureSlot(owner, slot, width, height).stream().mapToInt(MapIdStore.Tile::mapId).toArray();
    }

    private static List<MapIdStore.Tile> sorted(List<MapIdStore.Tile> tiles) {
        List<MapIdStore.Tile> copy = new ArrayList<>(tiles);
        copy.sort(Comparator.comparingInt(MapIdStore.Tile::tileY).thenComparingInt(MapIdStore.Tile::tileX));
        return copy;
    }
}
