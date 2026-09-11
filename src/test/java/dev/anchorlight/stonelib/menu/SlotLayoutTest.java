package dev.anchorlight.stonelib.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotLayoutTest {

    @Test
    void explicitSlotsAreHonouredAndAutoSlotsAvoidThem() {
        List<SlotLayout.Assignment> result = SlotLayout.assign(List.of("a", "b"), Map.of("a", 4), 9);
        assertEquals(new SlotLayout.Assignment("a", 4), result.get(0));
        assertNotEquals(4, result.get(1).slot());
    }

    @Test
    void singleEntryIsCentred() {
        assertEquals(4, SlotLayout.assign(List.of("only"), Map.of(), 9).get(0).slot());
    }

    @Test
    void autoSlotsAreSymmetricDistinctAndInRange() {
        assertArrayEquals(new int[] {1, 4, 7}, SlotLayout.centredOffsets(3, 9));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, SlotLayout.centredOffsets(9, 9));
        assertArrayEquals(new int[] {2, 6}, SlotLayout.centredOffsets(2, 9));
    }

    @Test
    void rejectsInvalidLayouts() {
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.assign(List.of("a", "b"), Map.of("a", 2, "b", 2), 9));
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.assign(List.of("a"), Map.of("a", 9), 9));
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.assign(List.of("a"), Map.of("a", -1), 9));
        assertThrows(IllegalArgumentException.class, () -> SlotLayout.assign(List.of("a", "b", "c"), Map.of(), 2));
    }
}
