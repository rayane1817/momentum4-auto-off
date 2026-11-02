package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuardStateTest {
    @Test public void armed() {
        GuardState g = GuardState.of(true, 0, false, 15, "");
        assertTrue(g.on);
        assertEquals("15", g.dots);
        assertEquals("Armed · 15 min", g.tile);
        assertEquals("Armed. Off after 15 min with nothing playing and no call.", g.status);
    }

    // T53: after the guard sent 0605 and stopped itself, page and tile say Sent, not Armed.
    @Test public void firedAndStoppedShowsSent() {
        GuardState g = GuardState.of(false, 1_000L, false, 15, "14:05");
        assertFalse(g.on);
        assertTrue(g.sent);
        assertEquals("SENT", g.dots);
        assertEquals("Sent · 14:05", g.tile);
        assertEquals("Power-off sent at 14:05. Wake them with the button.", g.status);
        assertFalse(g.status.contains("Armed"));
    }

    @Test public void armedWinsOverAnOldSend() {
        assertEquals("Armed · 5 min", GuardState.of(true, 1_000L, false, 5, "09:00").tile);
    }

    @Test public void failedSendWhileArmed() {
        GuardState g = GuardState.of(true, 0, true, 15, "");
        assertTrue(g.failed);
        assertTrue(g.status.startsWith("Couldn't reach the headphones."));
        assertEquals("Armed · 15 min", g.tile);
    }

    @Test public void neverStarted() {
        GuardState g = GuardState.of(false, 0, false, 15, "");
        assertEquals("OFF", g.dots);
        assertEquals("Off", g.tile);
        assertEquals("The guard is not running.", g.status);
    }
}
