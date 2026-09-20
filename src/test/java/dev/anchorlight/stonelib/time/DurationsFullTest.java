package dev.anchorlight.stonelib.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Durations#full}: all three units at once.
 *
 * <p>The distinction being pinned down is against {@link Durations#compact}, which shows only the
 * two most significant units. That is correct where space is tight, and wrong anywhere somebody is
 * watching the value tick: past an hour, compact drops the seconds and the display changes once a
 * minute, which reads as frozen rather than as counting down.
 */
class DurationsFullTest {

    @Test
    void showsAllThreeUnitsPastAnHour() {
        assertEquals("2h 5m 30s", Durations.full(Duration.ofSeconds(2 * 3600 + 5 * 60 + 30)));
    }

    @Test
    void keepsSecondsWhereCompactDropsThem() {
        Duration duration = Duration.ofSeconds(3600 + 59);

        // The whole reason this method exists.
        assertEquals("1h 0m 59s", Durations.full(duration));
        assertEquals("1h 0m", Durations.compact(duration));
    }

    @Test
    void keepsInteriorZeroUnits() {
        // "2h 0m 30s" rather than "2h 30s": the shape of the string must not change from one
        // second to the next as a unit empties, or the fields jump around while being read.
        assertEquals("2h 0m 30s", Durations.full(Duration.ofSeconds(2 * 3600 + 30)));
        assertEquals("1h 0m 0s", Durations.full(Duration.ofHours(1)));
    }

    @Test
    void dropsLeadingZeroUnits() {
        assertEquals("5m 30s", Durations.full(Duration.ofSeconds(5 * 60 + 30)));
        assertEquals("30s", Durations.full(Duration.ofSeconds(30)));
        assertEquals("0s", Durations.full(Duration.ZERO));
    }

    @Test
    void negativeAndNullReadAsZero() {
        assertEquals("0s", Durations.full(Duration.ofSeconds(-90)));
        assertEquals("0s", Durations.full((Duration) null));
    }

    @Test
    void theSecondsOverloadAgrees() {
        assertEquals(Durations.full(Duration.ofSeconds(4000)), Durations.full(4000L));
        assertEquals("0s", Durations.full(-1L));
    }

    @Test
    void longDurationsDoNotOverflowIntoNonsense() {
        // A three-day event window, which is what this is used for.
        assertEquals("72h 0m 0s", Durations.full(Duration.ofDays(3)));
    }
}
