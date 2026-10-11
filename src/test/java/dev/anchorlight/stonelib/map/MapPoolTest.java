package dev.anchorlight.stonelib.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPoolTest {

    @Test
    void mintsOncePerSlotAndReusesAfterwards() throws Exception {
        AtomicInteger next = new AtomicInteger(100);
        MapIdStore store = MapIdStore.memory();
        MapPool pool = new MapPool(store, next::getAndIncrement);

        List<MapIdStore.Tile> first = pool.ensureAllocated("lobby", 2, 2, 3);
        assertEquals(12, first.size());
        assertEquals(112, next.get());

        List<MapIdStore.Tile> again = pool.ensureAllocated("lobby", 2, 2, 3);
        assertEquals(first, again);
        assertEquals(112, next.get(), "no new ids for slots that already have them");
    }

    @Test
    void slotIdsAreRowMajorEvenWhenTheStoreReturnsThemShuffled() throws Exception {
        MapIdStore shuffled = new MapIdStore() {
            @Override
            public List<Tile> load(String owner, int slot) {
                return List.of(new Tile(1, 1, 4), new Tile(0, 0, 1), new Tile(1, 0, 2), new Tile(0, 1, 3));
            }

            @Override
            public void save(String owner, int slot, List<Tile> tiles) {
                throw new AssertionError("should not mint");
            }
        };
        assertArrayEquals(new int[]{1, 2, 3, 4}, new MapPool(shuffled, () -> -1).mapIds("x", 0, 2, 2));
    }

    @Test
    void readingAnUnallocatedSlotMintsNothing() throws Exception {
        MapPool pool = new MapPool(MapIdStore.memory(), () -> {
            throw new AssertionError("should not mint");
        });
        assertTrue(pool.slot("nobody", 0).isEmpty());
    }

    @Test
    void memoryStoreDeleteForgetsOnlyThatOwner() throws Exception {
        AtomicInteger next = new AtomicInteger();
        MapIdStore store = MapIdStore.memory();
        MapPool pool = new MapPool(store, next::getAndIncrement);
        pool.ensureSlot("a", 0, 1, 1);
        pool.ensureSlot("ab", 0, 1, 1);
        store.delete("a");
        assertTrue(pool.slot("a", 0).isEmpty());
        assertEquals(1, pool.slot("ab", 0).size());
    }

    @Test
    void rejectsEmptySizes() {
        MapPool pool = new MapPool(MapIdStore.memory(), () -> 0);
        assertThrows(IllegalArgumentException.class, () -> pool.ensureSlot("x", 0, 0, 1));
    }
}
