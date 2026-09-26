package dev.anchorlight.stonelib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarsTest {

    /** Counts the glyphs inside the filled colour tag. */
    private static int filledGlyphs(String bar, String colour) {
        int open = bar.indexOf("<" + colour + ">") + colour.length() + 2;
        int close = bar.indexOf("</" + colour + ">");
        return close - open;
    }

    @Test
    void halfProgressFillsHalfTheBar() {
        assertEquals(5, filledGlyphs(Bars.progress(50, 100, 10), "green"));
    }

    @Test
    void anyRealProgressShowsAtLeastOneGlyph() {
        // 1/1000 of a 10-wide bar rounds to zero, which reads as a broken tracker.
        assertEquals(1, filledGlyphs(Bars.progress(1, 1000, 10), "green"));
    }

    @Test
    void anIncompleteGoalNeverLooksFull() {
        // 999/1000 rounds to 10 of 10, which reads as done when it is not.
        assertEquals(9, filledGlyphs(Bars.progress(999, 1000, 10), "green"));
    }

    @Test
    void zeroAndCompleteAreExact() {
        assertEquals(0, filledGlyphs(Bars.progress(0, 100, 10), "green"));
        assertEquals(10, filledGlyphs(Bars.progress(100, 100, 10), "green"));
        assertEquals(10, filledGlyphs(Bars.progress(500, 100, 10), "green"));
    }

    @Test
    void aZeroTargetDoesNotDivideByZero() {
        String bar = Bars.progress(5, 0, 10);
        assertTrue(bar.contains("green"));
        assertEquals(10, filledGlyphs(bar, "green"));
    }

    @Test
    void fractionAndPercentClamp() {
        assertEquals(0.5f, Bars.fraction(1, 2), 0.0001);
        assertEquals(1.0f, Bars.fraction(5, 2), 0.0001);
        assertEquals(0.0f, Bars.fraction(-5, 2), 0.0001);
        assertEquals(50, Bars.percent(1, 2));
        assertEquals(100, Bars.percent(9, 2));
    }
}
