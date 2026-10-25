package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The widget path must end well inside Android's 10 s limit for a foreground broadcast (T30 ANR). */
public class RemoteBudgetTest {
    private static final long ANR_LIMIT_MS = 10_000, MARGIN_MS = 2_000;

    @Test public void widgetsUseOneAttempt() { assertEquals(1, Remote.WIDGET_ATTEMPTS); }

    @Test public void widgetSendFitsBeforeTheDeadline() {
        // EQ = 5 frames (the longest), noise = 2.
        assertTrue(Remote.worstCaseMs(Remote.WIDGET_ATTEMPTS, 5) < Remote.WIDGET_DEADLINE_MS);
        assertTrue(Remote.WIDGET_DEADLINE_MS <= ANR_LIMIT_MS - MARGIN_MS);
    }

    @Test public void oldWidgetPathWouldNotFit() {
        // v0.10: 2 attempts, each connect ~5.1 s with the headset off, plus 0.8 s gaps.
        assertTrue(2 * 5_100 + 2 * 800 > ANR_LIMIT_MS);
    }

    @Test public void tileKeepsItsRetryButIsBounded() {
        assertEquals(2, Remote.TILE_ATTEMPTS);
        assertTrue(Remote.worstCaseMs(Remote.TILE_ATTEMPTS, 5) < 15_000);   // was ~15 s greyed (T30)
    }
}
