package dev.anchorlight.stonelib.aggregation;

import dev.anchorlight.stonelib.region.ChunkKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowedCounterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private WindowedCounter<String> counter(Duration window, Duration reportInterval, int maxKeys) {
        return new WindowedCounter<>(window, reportInterval, maxKeys, now::get);
    }

    private WindowedCounter<String> counter() {
        return counter(Duration.ofMinutes(10), Duration.ofSeconds(30), 100);
    }

    private void advance(Duration by) {
        now.addAndGet(by.toMillis());
    }

    // --- counting ----------------------------------------------------------------

    @Test
    void sumsRepeatedRecordsIntoOneCounter() {
        WindowedCounter<String> counter = counter();
        for (int i = 0; i < 500; i++) {
            counter.record("alice", "broken", 1);
        }
        assertEquals(500, counter.peek("alice").count("broken"));
    }

    @Test
    void keepsCountersSeparate() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 3);
        counter.record("alice", "placed", 7);

        assertEquals(3, counter.peek("alice").count("broken"));
        assertEquals(7, counter.peek("alice").count("placed"));
    }

    @Test
    void keepsKeysSeparate() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 5);
        counter.record("bob", "broken", 9);

        assertEquals(5, counter.peek("alice").count("broken"));
        assertEquals(9, counter.peek("bob").count("broken"));
        assertEquals(2, counter.trackedKeys());
    }

    @Test
    void unknownCounterIsZeroRatherThanAbsent() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 1);
        assertEquals(0, counter.peek("alice").count("never-recorded"));
    }

    @Test
    void peekingAnUnknownKeyReturnsNothingAndTracksNothing() {
        WindowedCounter<String> counter = counter();
        assertNull(counter.peek("nobody"));
        assertEquals(0, counter.trackedKeys());
    }

    // --- buckets -----------------------------------------------------------------

    @Test
    void countsDistinctBucketsAsWellAsTotals() {
        WindowedCounter<String> counter = counter();
        // Twelve blocks spread over three chunks: the spread is the interesting part.
        for (int chunk = 0; chunk < 3; chunk++) {
            for (int i = 0; i < 4; i++) {
                counter.record("alice", "broken", ChunkKey.of(chunk, 0), 1);
            }
        }

        assertEquals(12, counter.peek("alice").count("broken"));
        assertEquals(3, counter.peek("alice").distinctBuckets());
    }

    @Test
    void tallliesEachBucketSeparately() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", ChunkKey.of(0, 0), 10);
        counter.record("alice", "broken", ChunkKey.of(1, 0), 4);

        var perBucket = counter.peek("alice").bucketCounts("broken");
        assertEquals(10, perBucket.get(ChunkKey.of(0, 0)));
        assertEquals(4, perBucket.get(ChunkKey.of(1, 0)));
    }

    @Test
    void distinctBucketsSpanEveryCounter() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", ChunkKey.of(0, 0), 1);
        counter.record("alice", "placed", ChunkKey.of(9, 9), 1);

        assertEquals(2, counter.peek("alice").distinctBuckets());
    }

    @Test
    void bucketCountsForACounterRecordedWithoutBucketsIsEmpty() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 5);
        assertTrue(counter.peek("alice").bucketCounts("broken").isEmpty());
        assertEquals(0, counter.peek("alice").distinctBuckets());
    }

    @Test
    void bucketsSurviveNegativeChunkCoordinates() {
        WindowedCounter<String> counter = counter();
        // Either side of the origin, which is where a hand-rolled key merges chunks.
        counter.record("alice", "broken", ChunkKey.of(-1, -1), 1);
        counter.record("alice", "broken", ChunkKey.of(1, 1), 1);
        counter.record("alice", "broken", ChunkKey.of(-1, 1), 1);
        counter.record("alice", "broken", ChunkKey.of(1, -1), 1);

        assertEquals(4, counter.peek("alice").distinctBuckets());
    }

    // --- reporting ---------------------------------------------------------------

    @Test
    void reportsOnlyWhatCrossesThePredicate() {
        WindowedCounter<String> counter = counter();
        counter.record("quiet", "broken", 5);
        counter.record("loud", "broken", 500);

        List<WindowedCounter.Window<String>> due = counter.drainReportable(w -> w.count("broken") >= 400);
        assertEquals(1, due.size());
        assertEquals("loud", due.get(0).key());
    }

    @Test
    void reportsImmediatelyOnTheFirstEventThatCrosses() {
        // A window that trips on its first event must not wait out a report
        // interval first — for an emergency that delay is the whole cost.
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 1000);

        assertEquals(1, counter.drainReportable(w -> w.count("broken") >= 400).size());
    }

    @Test
    void doesNotReportTheSameKeyAgainInsideTheInterval() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 500);

        assertEquals(1, counter.drainReportable(w -> w.count("broken") >= 400).size());
        advance(Duration.ofSeconds(5));
        counter.record("alice", "broken", 500);
        assertTrue(counter.drainReportable(w -> w.count("broken") >= 400).isEmpty());
    }

    @Test
    void reportsAgainOnceTheIntervalHasPassed() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 500);
        counter.drainReportable(w -> w.count("broken") >= 400);

        advance(Duration.ofSeconds(31));
        List<WindowedCounter.Window<String>> due = counter.drainReportable(w -> w.count("broken") >= 400);

        assertEquals(1, due.size());
        // Cumulative for the window, so a consumer can fold reports in with a
        // max per field instead of summing and double-counting.
        assertEquals(500, due.get(0).count("broken"));
    }

    @Test
    void successiveReportsGrowWithTheWindow() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 500);
        assertEquals(500, counter.drainReportable(w -> true).get(0).count("broken"));

        advance(Duration.ofSeconds(31));
        counter.record("alice", "broken", 300);
        assertEquals(800, counter.drainReportable(w -> true).get(0).count("broken"));
    }

    // --- window roll -------------------------------------------------------------

    @Test
    void startsOverOnceTheWindowElapses() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 500);
        assertEquals(500, counter.peek("alice").count("broken"));

        advance(Duration.ofMinutes(11));
        counter.record("alice", "broken", 7);

        assertEquals(7, counter.peek("alice").count("broken"));
        assertEquals(0, counter.peek("alice").distinctBuckets());
    }

    @Test
    void aRolledWindowCanReportImmediatelyAgain() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 500);
        counter.drainReportable(w -> w.count("broken") >= 400);

        advance(Duration.ofMinutes(11));
        counter.record("alice", "broken", 500);

        assertEquals(1, counter.drainReportable(w -> w.count("broken") >= 400).size());
    }

    @Test
    void peekingRollsAnExpiredWindowRatherThanReturningStaleCounts() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 500);

        advance(Duration.ofMinutes(11));
        assertEquals(0, counter.peek("alice").count("broken"));
    }

    // --- housekeeping ------------------------------------------------------------

    @Test
    void sweepsKeysThatHaveGoneQuiet() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 1);
        assertEquals(1, counter.trackedKeys());

        advance(Duration.ofMinutes(11));
        assertEquals(1, counter.sweepIdle());
        assertEquals(0, counter.trackedKeys());
    }

    @Test
    void keepsKeysThatAreStillActive() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 1);
        advance(Duration.ofMinutes(1));

        assertEquals(0, counter.sweepIdle());
        assertEquals(1, counter.trackedKeys());
    }

    @Test
    void forgetsAKeyOnDemand() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", 1);

        assertNotNull(counter.forget("alice"));
        assertEquals(0, counter.trackedKeys());
        assertNull(counter.forget("alice"));
    }

    @Test
    void refusesNewKeysPastTheCapAndCountsTheDrops() {
        // The cap is what stops a pathological case turning the map into a leak.
        WindowedCounter<String> counter = counter(Duration.ofMinutes(10), Duration.ofSeconds(30), 2);
        assertNotNull(counter.record("a", "x", 1));
        assertNotNull(counter.record("b", "x", 1));
        assertNull(counter.record("c", "x", 1));

        assertEquals(2, counter.trackedKeys());
        assertEquals(1, counter.droppedKeys());
        // An existing key still records fine; only new ones are refused.
        assertNotNull(counter.record("a", "x", 1));
    }

    @Test
    void roomFreedBySweepingIsReusable() {
        WindowedCounter<String> counter = counter(Duration.ofMinutes(10), Duration.ofSeconds(30), 1);
        counter.record("a", "x", 1);
        assertNull(counter.record("b", "x", 1));

        advance(Duration.ofMinutes(11));
        counter.sweepIdle();

        assertNotNull(counter.record("b", "x", 1));
    }

    @Test
    void reportedWindowsAreReadOnlyToTheConsumer() {
        WindowedCounter<String> counter = counter();
        counter.record("alice", "broken", ChunkKey.of(0, 0), 1);
        WindowedCounter.Window<String> window = counter.drainReportable(w -> true).get(0);

        assertThrowsUnsupported(() -> window.counts().put("broken", 999L));
        assertThrowsUnsupported(() -> window.bucketIds().clear());
        assertThrowsUnsupported(() -> window.bucketCounts("broken").clear());
    }

    private static void assertThrowsUnsupported(Runnable action) {
        boolean threw = false;
        try {
            action.run();
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        assertTrue(threw, "expected the exposed collection to be unmodifiable");
    }

    // --- a worked example --------------------------------------------------------

    @Test
    void separatesAQuarryFromARampage() {
        // The case the primitive exists for: both break a lot of blocks, but one
        // is concentrated and the other is spread across the map.
        WindowedCounter<UUID> counter = new WindowedCounter<>(
                Duration.ofMinutes(10), Duration.ofSeconds(30), 100, now::get);

        UUID miner = UUID.randomUUID();
        UUID griefer = UUID.randomUUID();

        // A quarry: 800 blocks, two chunks.
        for (int i = 0; i < 800; i++) {
            counter.record(miner, "broken", ChunkKey.of(i % 2, 0), 1);
        }
        // A rampage: 400 blocks, forty chunks.
        for (int i = 0; i < 400; i++) {
            counter.record(griefer, "broken", ChunkKey.of(i % 40, i % 40), 1);
        }

        List<WindowedCounter.Window<UUID>> spread = counter.drainReportable(w -> w.distinctBuckets() >= 8);

        assertEquals(1, spread.size());
        assertEquals(griefer, spread.get(0).key());
        assertFalse(spread.stream().anyMatch(w -> w.key().equals(miner)));
    }
}
