package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoConnectTest {
    @Test public void onlyMomentumOrConnectedByHand() {
        assertTrue(AutoConnect.eligible("MOMENTUM 4", false));
        assertTrue(AutoConnect.eligible("Ryane's Momentum 4", false));
        assertFalse(AutoConnect.eligible("[TV] Samsung 7 Series", false));   // T31
        assertFalse(AutoConnect.eligible(null, false));
        assertTrue(AutoConnect.eligible("ACCENTUM Plus", true));
    }

    @Test public void openWithHeadsetAlreadyConnected() {
        AutoConnect a = new AutoConnect();
        a.foreground();
        assertEquals(0, a.check(true, true));
        assertEquals(-1, a.check(true, false));
        assertEquals(-1, a.check(false, true));
    }

    @Test public void headsetArrivesWaitsForSettle() {
        assertEquals(AutoConnect.SETTLE_MS, new AutoConnect().headsetUp(true));
        assertEquals(-1, new AutoConnect().headsetUp(false));
    }

    @Test public void retriesBackOffThenStop() {
        AutoConnect a = new AutoConnect();
        a.headsetUp(true);
        assertEquals(3000, a.tryFailed(true));
        assertEquals(6000, a.tryFailed(true));
        assertEquals(-1, a.tryFailed(true));
        // the headset reconnecting starts a fresh round
        a.headsetDown();
        a.headsetUp(true);
        assertEquals(3000, a.tryFailed(true));
    }

    @Test public void noRetryWhenHeadsetLeft() {
        AutoConnect a = new AutoConnect();
        a.headsetUp(true);
        assertEquals(-1, a.tryFailed(false));
    }

    @Test public void disconnectHoldsUntilHeadsetReconnects() {
        AutoConnect a = new AutoConnect();
        a.userDisconnected();
        assertTrue(a.held());
        assertEquals(-1, a.check(true, true));
        assertEquals(-1, a.linkLost(true, true, 60_000));
        assertEquals(-1, a.tryFailed(true));
        assertEquals(AutoConnect.SETTLE_MS, a.headsetUp(true));
        assertFalse(a.held());
    }

    @Test public void disconnectHoldsUntilAppReturns() {
        AutoConnect a = new AutoConnect();
        a.userDisconnected();
        a.foreground();
        assertEquals(0, a.check(true, true));
    }

    @Test public void manualConnectClearsHold() {
        AutoConnect a = new AutoConnect();
        a.userDisconnected();
        a.manualConnect();
        assertFalse(a.held());
    }

    @Test public void stableLinkLossReconnects() {
        AutoConnect a = new AutoConnect();
        for (int i = 0; i < 5; i++) assertEquals(AutoConnect.SETTLE_MS, a.linkLost(true, true, 60_000));
    }

    @Test public void flappingLinkStops() {
        AutoConnect a = new AutoConnect();
        assertEquals(AutoConnect.SETTLE_MS, a.linkLost(true, true, 500));
        assertEquals(AutoConnect.SETTLE_MS, a.linkLost(true, true, 500));
        assertEquals(-1, a.linkLost(true, true, 500));
    }

    @Test public void linkLostWithHeadsetGoneWaits() {
        assertEquals(-1, new AutoConnect().linkLost(true, false, 60_000));
        assertEquals(-1, new AutoConnect().linkLost(false, true, 60_000));
    }

    @Test public void idleText() {
        assertEquals("Waiting for MOMENTUM 4", AutoConnect.idleText(false, true, false, "MOMENTUM 4"));
        assertNull(AutoConnect.idleText(false, true, true, "MOMENTUM 4"));    // up but not linked
        assertNull(AutoConnect.idleText(false, true, null, "MOMENTUM 4"));    // can't tell yet
        assertNull(AutoConnect.idleText(true, true, false, "MOMENTUM 4"));    // after Disconnect
        assertNull(AutoConnect.idleText(false, false, false, "Living room TV"));
    }
}
