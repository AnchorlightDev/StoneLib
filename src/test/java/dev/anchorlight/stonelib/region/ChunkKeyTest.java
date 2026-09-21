package dev.anchorlight.stonelib.region;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both operations here look obviously right and are quietly wrong for negative coordinates, which
 * is where spawn is. Worth pinning down rather than eyeballing.
 */
class ChunkKeyTest {

    @Test
    void blockToChunkFloorsRatherThanTruncatingTowardZero() {
        assertEquals(0, ChunkKey.chunkOf(0));
        assertEquals(0, ChunkKey.chunkOf(15));
        assertEquals(1, ChunkKey.chunkOf(16));
        // The cases a plain `/ 16` gets wrong.
        assertEquals(-1, ChunkKey.chunkOf(-1));
        assertEquals(-1, ChunkKey.chunkOf(-16));
        assertEquals(-2, ChunkKey.chunkOf(-17));
        assertEquals(-100, ChunkKey.chunkOf(-1600));
    }

    @Test
    void blockToChunkAgreesWithFloorDivisionAcrossASweptRange() {
        for (int block = -2000; block <= 2000; block++) {
            assertEquals(Math.floorDiv(block, 16), ChunkKey.chunkOf(block), "block " + block);
        }
    }

    @Test
    void packingRoundTripsIncludingAcrossTheOrigin() {
        int[] coordinates = {0, 1, -1, 15, -15, 16, -16, 1000, -1000, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int x : coordinates) {
            for (int z : coordinates) {
                long key = ChunkKey.of(x, z);
                assertEquals(x, ChunkKey.unpackX(key), "x for (" + x + "," + z + ")");
                assertEquals(z, ChunkKey.unpackZ(key), "z for (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void distinctChunksNeverCollideOntoOneKey() {
        Set<Long> seen = new HashSet<>();
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                assertTrue(seen.add(ChunkKey.of(x, z)), "collision at (" + x + "," + z + ")");
            }
        }
        assertEquals(121 * 121, seen.size());
    }

    @Test
    void mirroredCoordinatesAreNotConfusedWithEachOther() {
        // A naive additive key merges these pairs.
        assertNotEquals(ChunkKey.of(1, -1), ChunkKey.of(-1, 1));
        assertNotEquals(ChunkKey.of(5, 3), ChunkKey.of(3, 5));
    }

    @Test
    void packingFromBlockCoordinatesMatchesPackingTheChunk() {
        assertEquals(ChunkKey.of(0, 0), ChunkKey.ofBlock(5, 5));
        assertEquals(ChunkKey.of(-1, -1), ChunkKey.ofBlock(-1, -1));
        assertEquals(ChunkKey.of(-2, 3), ChunkKey.ofBlock(-17, 50));
    }
}
