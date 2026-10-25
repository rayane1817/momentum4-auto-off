package be.ryane.gaiatest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EqSyncTest {
    @Test public void coldStartDoesNotPublish() { assertFalse(new EqSync().mayPublish()); }

    @Test public void publishesAfterTheCurveIsRead() {
        EqSync s = new EqSync();
        s.curveRead();
        assertTrue(s.mayPublish());
    }

    @Test public void publishesAfterAFullWrite() {
        EqSync s = new EqSync();
        s.curveWritten();
        assertTrue(s.mayPublish());
    }

    @Test public void disconnectStopsPublishingUntilTheNextRead() {
        EqSync s = new EqSync();
        s.curveRead();
        s.disconnected();
        assertFalse(s.mayPublish());
        s.curveRead();
        assertTrue(s.mayPublish());
    }
}
