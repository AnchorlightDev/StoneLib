package dev.anchorlight.stonelib.map;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapScreenTest {

    @Test
    void rightHandIsClockwiseOfTheViewersDirection() {
        assertEquals(BlockFace.WEST, MapScreen.rightOf(BlockFace.NORTH));
        assertEquals(BlockFace.EAST, MapScreen.rightOf(BlockFace.SOUTH));
        assertEquals(BlockFace.NORTH, MapScreen.rightOf(BlockFace.EAST));
        assertEquals(BlockFace.SOUTH, MapScreen.rightOf(BlockFace.WEST));
    }

    @Test
    void cellsAreRowMajorFromTheTopLeftAsTheViewerSeesIt() {
        // Faces south: the viewer stands south looking north, so the screen runs towards +X.
        MapScreen.Layout layout = new MapScreen.Layout(null, 10, 64, 5, BlockFace.SOUTH, 3, 2);
        List<MapScreen.Cell> cells = MapScreen.cells(layout);
        assertEquals(6, cells.size());
        assertEquals(new MapScreen.Cell(10, 65, 5, 0), cells.get(0)); // top-left
        assertEquals(new MapScreen.Cell(12, 65, 5, 2), cells.get(2)); // top-right
        assertEquals(new MapScreen.Cell(10, 64, 5, 3), cells.get(3)); // bottom-left = origin
        assertEquals(new MapScreen.Cell(12, 64, 5, 5), cells.get(5));
    }

    @Test
    void eastFacingScreenRunsTowardsNorth() {
        List<MapScreen.Cell> cells = MapScreen.cells(new MapScreen.Layout(null, 0, 70, 0, BlockFace.EAST, 2, 1));
        assertEquals(new MapScreen.Cell(0, 70, 0, 0), cells.get(0));
        assertEquals(new MapScreen.Cell(0, 70, -1, 1), cells.get(1));
    }

    @Test
    void layoutRejectsVerticalFacesAndEmptySizes() {
        assertThrows(IllegalArgumentException.class, () -> new MapScreen.Layout(null, 0, 0, 0, BlockFace.UP, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MapScreen.Layout(null, 0, 0, 0, BlockFace.NORTH, 0, 1));
    }

    @Test
    void defaultOptionsAreGlowingVisibleWallMountedAndSaved() {
        MapScreen.Options o = MapScreen.Options.DEFAULT;
        org.junit.jupiter.api.Assertions.assertTrue(o.glow() && !o.invisible() && !o.floating() && o.persistent());
    }
}
