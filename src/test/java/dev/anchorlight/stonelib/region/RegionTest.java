package dev.anchorlight.stonelib.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTest {

    private record Area(String id, Cuboid bounds, boolean enabled) {
    }

    private static Area area(String id, Cuboid bounds) {
        return new Area(id, bounds, true);
    }

    private static RegionIndex<Area> indexOf(Area... areas) {
        RegionIndex<Area> index = new RegionIndex<>(Area::bounds);
        index.rebuild(List.of(areas));
        return index;
    }

    // --- Cuboid ------------------------------------------------------------------

    @Test
    void cuboidNormalisesInvertedCorners() {
        Cuboid cuboid = new Cuboid("world", 12, 124, 5, 10, 120, 5);
        assertEquals(10, cuboid.minX());
        assertEquals(12, cuboid.maxX());
        assertEquals(120, cuboid.minY());
        assertEquals(124, cuboid.maxY());
    }

    @Test
    void cuboidContainsInsideAndBoundaryButNotOutside() {
        Cuboid cuboid = new Cuboid("world", 10, 120, 5, 12, 124, 5);
        assertTrue(cuboid.contains(11, 122, 5));
        assertTrue(cuboid.contains(10, 120, 5));
        assertTrue(cuboid.contains(12, 124, 5));
        assertFalse(cuboid.contains(13, 122, 5));
        assertFalse(cuboid.contains(11, 125, 5));
        assertFalse(cuboid.contains("other", 11, 122, 5));
    }

    @Test
    void cuboidHandlesNegativeCoordinates() {
        Cuboid cuboid = new Cuboid("world", -20, 60, -8, -10, 70, -2);
        assertTrue(cuboid.contains(-15, 65, -5));
        assertFalse(cuboid.contains(-25, 65, -5));
    }

    @Test
    void cuboidReportsSizesAndVolume() {
        Cuboid cuboid = new Cuboid("world", 0, 60, 0, 2, 63, 0);
        assertEquals(3, cuboid.sizeX());
        assertEquals(4, cuboid.sizeY());
        assertEquals(1, cuboid.sizeZ());
        assertEquals(12L, cuboid.volume());
        assertEquals(8_000_000_000L, new Cuboid("world", 0, 0, 0, 1999, 1999, 1999).volume());
    }

    @Test
    void cuboidComputesChunkSpanAcrossBoundary() {
        Cuboid cuboid = new Cuboid("world", -1, 60, 0, 17, 70, 0);
        assertEquals(-1, cuboid.minChunkX());
        assertEquals(1, cuboid.maxChunkX());
    }

    // --- RegionIndex -------------------------------------------------------------

    @Test
    void indexFindsRegionInEveryIntersectedChunk() {
        RegionIndex<Area> index = indexOf(area("wide", new Cuboid("world", -1, 60, 0, 17, 70, 0)));
        assertEquals(1, index.candidatesFor("world", -1, 0).size());
        assertEquals(1, index.candidatesFor("world", 0, 0).size());
        assertEquals(1, index.candidatesFor("world", 1, 0).size());
        assertTrue(index.candidatesFor("world", 2, 0).isEmpty());
    }

    @Test
    void indexIsolatesWorlds() {
        RegionIndex<Area> index = indexOf(area("p", new Cuboid("world_nether", 0, 60, 0, 1, 61, 1)));
        assertTrue(index.candidatesFor("world", 0, 0).isEmpty());
        assertEquals(1, index.candidatesFor("world_nether", 0, 0).size());
    }

    @Test
    void indexRebuildReplacesContents() {
        RegionIndex<Area> index = indexOf(area("p", new Cuboid("world", 0, 60, 0, 1, 61, 1)));
        index.rebuild(List.of());
        assertTrue(index.candidatesFor("world", 0, 0).isEmpty());
    }

    @Test
    void indexFindAndAllAtCheckExactBounds() {
        Area small = area("small", new Cuboid("world", 0, 60, 0, 1, 61, 1));
        Area big = area("big", new Cuboid("world", 0, 60, 0, 5, 65, 5));
        RegionIndex<Area> index = indexOf(small, big);

        assertEquals(Optional.of(small), index.find("world", 1, 60, 1));
        assertEquals(List.of(small, big), index.allAt("world", 1, 60, 1));
        assertEquals(List.of(big), index.allAt("world", 4, 60, 4));
        assertTrue(index.find("world", 9, 60, 9).isEmpty());
    }

    // --- RegionTracker -----------------------------------------------------------

    @Test
    void trackerReportsEnterOnceAndExitOnLeave() {
        Area gate = area("gate", new Cuboid("world", 0, 60, 0, 2, 62, 2));
        RegionTracker<Area> tracker = new RegionTracker<>(indexOf(gate), Area::id);
        UUID player = UUID.randomUUID();

        RegionTracker.Transition<Area> enter = tracker.update(player, "world", 1, 61, 1);
        assertEquals(Optional.of(gate), enter.entered());
        assertTrue(enter.exited().isEmpty());

        assertFalse(tracker.update(player, "world", 1, 61, 2).changed());

        RegionTracker.Transition<Area> leave = tracker.update(player, "world", 10, 61, 10);
        assertEquals(Optional.of(gate), leave.exited());
        assertTrue(leave.entered().isEmpty());

        assertTrue(tracker.update(player, "world", 1, 61, 1).entered().isPresent());
    }

    @Test
    void trackerMovingOutsideNeverChanges() {
        RegionTracker<Area> tracker = new RegionTracker<>(indexOf(area("gate", new Cuboid("world", 0, 60, 0, 2, 62, 2))), Area::id);
        assertFalse(tracker.update(UUID.randomUUID(), "world", 10, 61, 10).changed());
    }

    @Test
    void trackerTracksPlayersIndependently() {
        RegionTracker<Area> tracker = new RegionTracker<>(indexOf(area("gate", new Cuboid("world", 0, 60, 0, 2, 62, 2))), Area::id);
        assertTrue(tracker.update(UUID.randomUUID(), "world", 1, 61, 1).entered().isPresent());
        assertTrue(tracker.update(UUID.randomUUID(), "world", 1, 61, 1).entered().isPresent());
    }

    @Test
    void trackerReportsBothSidesWhenCrossingDirectlyIntoAdjacentRegion() {
        Area a = area("a", new Cuboid("world", 0, 60, 0, 1, 62, 0));
        Area b = area("b", new Cuboid("world", 2, 60, 0, 3, 62, 0));
        RegionTracker<Area> tracker = new RegionTracker<>(indexOf(a, b), Area::id);
        UUID player = UUID.randomUUID();

        tracker.update(player, "world", 1, 60, 0);
        RegionTracker.Transition<Area> cross = tracker.update(player, "world", 2, 60, 0);
        assertEquals(Optional.of(a), cross.exited());
        assertEquals(Optional.of(b), cross.entered());
    }

    @Test
    void trackerIgnoresInactiveRegions() {
        Area disabled = new Area("gate", new Cuboid("world", 0, 60, 0, 2, 62, 2), false);
        RegionTracker<Area> tracker = new RegionTracker<>(indexOf(disabled), Area::id, Area::enabled);
        assertFalse(tracker.update(UUID.randomUUID(), "world", 1, 61, 1).changed());
    }

    @Test
    void trackerDoesNotRetriggerWhenRegionIsReplacedBySameKey() {
        Area original = area("gate", new Cuboid("world", 0, 60, 0, 2, 62, 2));
        RegionIndex<Area> index = indexOf(original);
        RegionTracker<Area> tracker = new RegionTracker<>(index, Area::id);
        UUID player = UUID.randomUUID();
        tracker.update(player, "world", 1, 61, 1);

        index.rebuild(List.of(area("gate", new Cuboid("world", 0, 60, 0, 3, 62, 3))));
        assertFalse(tracker.update(player, "world", 1, 61, 1).changed());
    }

    @Test
    void trackerClearForgetsPlayer() {
        RegionTracker<Area> tracker = new RegionTracker<>(indexOf(area("gate", new Cuboid("world", 0, 60, 0, 2, 62, 2))), Area::id);
        UUID player = UUID.randomUUID();
        tracker.update(player, "world", 1, 61, 1);
        tracker.clear(player);
        assertTrue(tracker.currentRegion(player).isEmpty());
        assertTrue(tracker.update(player, "world", 1, 61, 1).entered().isPresent());
    }

    // --- SelectionManager --------------------------------------------------------

    @Test
    void selectionNeedsBothCornersInSameWorld() {
        SelectionManager selections = new SelectionManager();
        UUID admin = UUID.randomUUID();
        selections.setFirst(admin, "world", 0, 60, 0);
        assertTrue(selections.selection(admin).isEmpty());

        selections.setSecond(admin, "world_nether", 2, 62, 2);
        assertTrue(selections.selection(admin).isEmpty());

        selections.setSecond(admin, "world", 2, 62, 2);
        assertEquals(Optional.of(new Cuboid("world", 0, 60, 0, 2, 62, 2)), selections.selection(admin));
    }

    @Test
    void selectionsArePerPlayerAndClearable() {
        SelectionManager selections = new SelectionManager();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        selections.setFirst(a, "world", 0, 60, 0);
        selections.setSecond(a, "world", 2, 62, 2);
        assertTrue(selections.selection(a).isPresent());
        assertTrue(selections.selection(b).isEmpty());

        selections.clear(a);
        assertTrue(selections.selection(a).isEmpty());
    }
}
