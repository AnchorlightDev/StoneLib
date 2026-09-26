package dev.anchorlight.stonelib.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopStockTest {

    private static ShopEntry limited(String id, int stock) {
        return new ShopEntry(id, id, List.of(), Material.STONE, 1, 10, "ITEM", Map.of(), stock, null);
    }

    private static ShopEntry unlimited(String id) {
        return new ShopEntry(id, id, List.of(), Material.STONE, 1, 10, "ITEM", Map.of(),
                ShopEntry.UNLIMITED, null);
    }

    @Test
    void unlimitedEntriesNeverRunOut() {
        ShopStock stock = new ShopStock();
        ShopEntry entry = unlimited("iron");
        for (int i = 0; i < 100; i++) {
            assertTrue(stock.tryTake(entry));
        }
        assertFalse(stock.soldOut(entry));
        assertEquals(Integer.MAX_VALUE, stock.remaining(entry));
    }

    @Test
    void limitedEntriesRunOutAfterTheirStock() {
        ShopStock stock = new ShopStock();
        ShopEntry entry = limited("war_axe", 3);

        assertTrue(stock.tryTake(entry));
        assertTrue(stock.tryTake(entry));
        assertTrue(stock.tryTake(entry));
        assertFalse(stock.tryTake(entry));

        assertTrue(stock.soldOut(entry));
        assertEquals(0, stock.remaining(entry));
    }

    @Test
    void restoringPutsAFailedDeliveryBackOnTheShelf() {
        ShopStock stock = new ShopStock();
        ShopEntry entry = limited("war_axe", 1);

        assertTrue(stock.tryTake(entry));
        assertTrue(stock.soldOut(entry));

        stock.restoreOne(entry);
        assertFalse(stock.soldOut(entry));
        assertTrue(stock.tryTake(entry));
    }

    @Test
    void snapshotAndRestoreSurviveARestart() {
        ShopStock before = new ShopStock();
        ShopEntry entry = limited("war_axe", 5);
        before.tryTake(entry);
        before.tryTake(entry);

        Map<String, Integer> saved = before.snapshot();

        ShopStock after = new ShopStock();
        after.restore(saved);
        assertEquals(2, after.sold("war_axe"));
        assertEquals(3, after.remaining(entry));
    }

    @Test
    void countingSalesMeansRaisingStockInConfigAddsUnits() {
        ShopStock stock = new ShopStock();
        stock.restore(Map.of("war_axe", 3));

        // Staff raise the entry from 3 to 5 between restarts; two should become available.
        assertEquals(2, stock.remaining(limited("war_axe", 5)));
        assertEquals(0, stock.remaining(limited("war_axe", 3)));
    }

    @Test
    void resetMakesEverythingAvailableAgain() {
        ShopStock stock = new ShopStock();
        ShopEntry entry = limited("war_axe", 1);
        stock.tryTake(entry);
        stock.reset();
        assertFalse(stock.soldOut(entry));
    }
}
