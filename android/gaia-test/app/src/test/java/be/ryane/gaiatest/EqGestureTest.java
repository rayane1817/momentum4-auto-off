package be.ryane.gaiatest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class EqGestureTest {
    @Test public void columns() {
        assertEquals(0, EqGesture.bandAt(0, 500));
        assertEquals(2, EqGesture.bandAt(250, 500));
        assertEquals(4, EqGesture.bandAt(499, 500));
        assertEquals(4, EqGesture.bandAt(900, 500));     // past the edge
        assertEquals(0, EqGesture.bandAt(-5, 500));
    }

    @Test public void heightToGain() {
        assertEquals(60, EqGesture.gainAt(100, 100, 240));      // top = +6.0
        assertEquals(0, EqGesture.gainAt(220, 100, 240));       // middle = 0
        assertEquals(-60, EqGesture.gainAt(340, 100, 240));     // bottom = -6.0
        assertEquals(60, EqGesture.gainAt(0, 100, 240));        // clamped above
        assertEquals(-60, EqGesture.gainAt(999, 100, 240));     // clamped below
        assertEquals(25, EqGesture.gainAt(170, 100, 240));      // +2.5 dB
    }

    @Test public void tapWithoutMoveSendsNothing() {
        EqGesture g = new EqGesture();
        g.begin(new int[]{10, 0, 0, 0, -10});
        assertTrue(g.changes().isEmpty());
    }

    @Test public void sweepSendsOneFramePerChangedBandInOrder() {
        EqGesture g = new EqGesture();
        g.begin(new int[]{0, 0, 0, 0, 0});
        g.set(3, 20); g.set(1, -15); g.set(2, 0); g.set(1, -20);   // band 2 ends unchanged
        List<int[]> c = g.changes();
        assertEquals(2, c.size());
        assertArrayEquals(new int[]{1, -20}, c.get(0));
        assertArrayEquals(new int[]{3, 20}, c.get(1));
    }

    @Test public void backToStartIsNoChange() {
        EqGesture g = new EqGesture();
        g.begin(new int[]{5, 5, 5, 5, 5});
        g.set(0, 30); g.set(0, 5);
        assertTrue(g.changes().isEmpty());
    }
}
