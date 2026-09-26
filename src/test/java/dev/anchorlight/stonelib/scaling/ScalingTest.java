package dev.anchorlight.stonelib.scaling;

import dev.anchorlight.stonelib.scaling.ScaledTarget.Spec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalingTest {

    @Test
    void fixedTargetIgnoresPopulation() {
        Spec spec = Spec.fixed(12);
        assertEquals(12, ScaledTarget.compute(spec, 1));
        assertEquals(12, ScaledTarget.compute(spec, 40));
    }

    @Test
    void perPlayerMultipliesByPopulation() {
        Spec spec = new Spec(Scaling.PER_PLAYER, 100, 10, 0, 1, 1000);
        assertEquals(30, ScaledTarget.compute(spec, 3));
        assertEquals(200, ScaledTarget.compute(spec, 20));
    }

    @Test
    void fractionOfOnlineRoundsUp() {
        Spec spec = new Spec(Scaling.FRACTION_OF_ONLINE, 10, 0, 0.5, 1, 100);
        assertEquals(2, ScaledTarget.compute(spec, 3));
        assertEquals(5, ScaledTarget.compute(spec, 9));
    }

    @Test
    void clampsBetweenMinAndMax() {
        Spec spec = new Spec(Scaling.PER_PLAYER, 100, 10, 0, 25, 60);
        assertEquals(25, ScaledTarget.compute(spec, 1));
        assertEquals(60, ScaledTarget.compute(spec, 40));
    }

    @Test
    void neverReturnsBelowOne() {
        Spec spec = new Spec(Scaling.FRACTION_OF_ONLINE, 1, 0, 0.001, 1, 10);
        assertTrue(ScaledTarget.compute(spec, 1) >= 1);
        assertTrue(ScaledTarget.compute(spec, 0) >= 1);
    }

    @Test
    void progressFloorStopsAShrinkingPopulationInvalidatingWorkDone() {
        Spec spec = new Spec(Scaling.PER_PLAYER, 100, 10, 0, 1, 1000);
        // Twenty players banked 200; then all but two log off.
        int recomputed = ScaledTarget.compute(spec, 2);
        assertEquals(20, recomputed);
        assertEquals(200, ScaledTarget.atLeastProgress(recomputed, 200));
    }

    @Test
    void parseFallsBackOnATypoRatherThanThrowing() {
        assertEquals(Scaling.PER_PLAYER, ScaledTarget.parse("per_player", Scaling.NONE));
        assertEquals(Scaling.PER_PLAYER, ScaledTarget.parse("  PER_PLAYER ", Scaling.NONE));
        assertEquals(Scaling.NONE, ScaledTarget.parse("per-player", Scaling.NONE));
        assertEquals(Scaling.NONE, ScaledTarget.parse(null, Scaling.NONE));
    }

    @Test
    void populationWindowTakesThePeakNotTheLiveCount() {
        AtomicInteger online = new AtomicInteger(20);
        PopulationWindow window = new PopulationWindow(online::get, Duration.ofMinutes(30));
        window.sample();

        // Half the server logs off. The target must not get easier because they left.
        online.set(3);
        assertEquals(20, window.peak());
    }

    @Test
    void populationWindowNeverReturnsBelowTheLiveCount() {
        AtomicInteger online = new AtomicInteger(0);
        PopulationWindow window = new PopulationWindow(online::get, Duration.ofMinutes(30));
        // No sample taken yet - a target resolved now must not scale against an empty server.
        online.set(8);
        assertEquals(8, window.peak());
    }

    @Test
    void populationWindowClears() {
        AtomicInteger online = new AtomicInteger(15);
        PopulationWindow window = new PopulationWindow(online::get, Duration.ofMinutes(30));
        window.sample();
        assertEquals(1, window.size());
        window.clear();
        assertEquals(0, window.size());
    }
}
