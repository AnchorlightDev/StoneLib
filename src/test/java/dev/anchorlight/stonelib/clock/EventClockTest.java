package dev.anchorlight.stonelib.clock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventClockTest {

    private static final Logger LOG = Logger.getLogger(EventClockTest.class.getName());

    private static EventClock clockOver(Duration before, Duration after) {
        EventClock clock = new EventClock(Duration.ofDays(3), LOG);
        clock.apply(new EventWindow(Instant.now().minus(before), Instant.now().plus(after),
                ZoneOffset.UTC, true));
        return clock;
    }

    @Test
    void progressIsHalfwayThroughAnEvenWindow() {
        EventClock clock = clockOver(Duration.ofHours(12), Duration.ofHours(12));
        assertEquals(0.5, clock.progress(), 0.01);
        assertTrue(clock.live());
        assertFalse(clock.preEvent());
        assertFalse(clock.finished());
    }

    @Test
    void progressClampsOutsideTheWindow() {
        EventClock future = new EventClock(LOG);
        future.apply(new EventWindow(Instant.now().plus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(2)), ZoneOffset.UTC, true));
        assertEquals(0.0, future.progress(), 0.001);
        assertTrue(future.preEvent());

        EventClock past = new EventClock(LOG);
        past.apply(new EventWindow(Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC, true));
        assertEquals(1.0, past.progress(), 0.001);
        assertTrue(past.finished());
        assertFalse(past.live());
    }

    @Test
    void simulationOverridesProgressAndDrivesRemaining() {
        EventClock clock = clockOver(Duration.ofHours(1), Duration.ofHours(23));
        clock.simulate(0.75);

        assertTrue(clock.simulating());
        assertEquals(0.75, clock.progress(), 0.0001);
        // remaining must follow the simulation, or duration-based endgame gates cannot be tested.
        assertEquals(6, clock.remaining().toHours());
        assertEquals(18, clock.elapsed().toHours());
    }

    @Test
    void simulationClampsAndClears() {
        EventClock clock = clockOver(Duration.ofHours(1), Duration.ofHours(1));
        clock.simulate(5.0);
        assertEquals(1.0, clock.progress(), 0.0001);
        clock.simulate(-5.0);
        assertEquals(0.0, clock.progress(), 0.0001);

        clock.simulate(null);
        assertFalse(clock.simulating());
        assertNull(clock.simulatedProgress());
    }

    @Test
    void simulationCannotActuallyEndTheEvent() {
        EventClock clock = clockOver(Duration.ofHours(1), Duration.ofHours(1));
        clock.simulate(1.0);
        // p is 1.0, but the real clock has not passed the end, so the event is still running.
        assertFalse(clock.finished());
    }

    @Test
    void applyClearsAnActiveSimulation() {
        EventClock clock = clockOver(Duration.ofHours(1), Duration.ofHours(1));
        clock.simulate(0.9);
        clock.apply(new EventWindow(Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(1)), ZoneOffset.UTC, true));
        assertFalse(clock.simulating());
    }

    @Test
    void intervalScaleStretchesLongRunsButNeverShrinksShortOnes() {
        EventClock threeWeeks = new EventClock(Duration.ofDays(3), LOG);
        threeWeeks.apply(new EventWindow(Instant.now(), Instant.now().plus(Duration.ofDays(21)),
                ZoneOffset.UTC, true));
        assertEquals(7.0, threeWeeks.intervalScale(), 0.01);

        EventClock oneDay = new EventClock(Duration.ofDays(3), LOG);
        oneDay.apply(new EventWindow(Instant.now(), Instant.now().plus(Duration.ofDays(1)),
                ZoneOffset.UTC, true));
        assertEquals(1.0, oneDay.intervalScale(), 0.001);
    }

    @Test
    void windowParsesAnOffsetString() {
        EventWindow window = EventWindow.parse("2026-10-02T18:00:00+11:00",
                "2026-10-05T18:00:00+11:00", "Australia/Sydney", Duration.ofDays(3), LOG);
        assertTrue(window.valid());
        assertEquals(Duration.ofDays(3), window.duration());
    }

    @Test
    void windowParsesTheDateThatYamlProducesForAnUnquotedTimestamp() {
        Instant start = Instant.parse("2026-10-02T07:00:00Z");
        Instant end = Instant.parse("2026-10-05T07:00:00Z");
        EventWindow window = EventWindow.parse(Date.from(start), Date.from(end),
                "UTC", Duration.ofDays(3), LOG);

        assertTrue(window.valid());
        assertEquals(start, window.start());
        assertEquals(end, window.end());
    }

    @Test
    void windowParsesAZonelessLocalTimestampInTheConfiguredZone() {
        EventWindow window = EventWindow.parse("2026-10-02T18:00:00", "2026-10-03T18:00:00",
                "UTC", Duration.ofDays(3), LOG);
        assertTrue(window.valid());
        assertEquals(Instant.parse("2026-10-02T18:00:00Z"), window.start());
    }

    @Test
    void windowIsMarkedInvalidWhenItCannotBeRead() {
        EventWindow missing = EventWindow.parse(null, null, "UTC", Duration.ofDays(3), LOG);
        assertFalse(missing.valid());

        EventWindow nonsense = EventWindow.parse("not a date", "also not a date",
                "UTC", Duration.ofDays(3), LOG);
        assertFalse(nonsense.valid());

        EventWindow backwards = EventWindow.parse("2026-10-05T18:00:00", "2026-10-02T18:00:00",
                "UTC", Duration.ofDays(3), LOG);
        assertFalse(backwards.valid());
        assertTrue(backwards.end().isAfter(backwards.start()));
    }

    @Test
    void unknownTimezoneFallsBackRatherThanThrowing() {
        EventWindow window = EventWindow.parse("2026-10-02T18:00:00", "2026-10-03T18:00:00",
                "Mars/Olympus_Mons", Duration.ofDays(3), LOG);
        assertEquals(ZoneId.systemDefault(), window.zone());
    }
}
