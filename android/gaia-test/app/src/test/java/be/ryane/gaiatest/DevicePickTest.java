package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class DevicePickTest {
    private static final List<String> ADDRS = Arrays.asList("A", "B", "C");
    private static final List<String> NAMES = Arrays.asList("TV", "MOMENTUM 4", "Car");

    @Test public void keepsTheCurrentChoice() { assertEquals(2, DevicePick.preselect(ADDRS, NAMES, "C")); }

    @Test public void nothingKeptPicksTheMomentum() { assertEquals(1, DevicePick.preselect(ADDRS, NAMES, "")); }

    @Test public void nullKeepActsLikeEmpty() { assertEquals(1, DevicePick.preselect(ADDRS, NAMES, null)); }

    @Test public void unknownKeepFallsBackToFirst() { assertEquals(0, DevicePick.preselect(ADDRS, NAMES, "gone")); }

    @Test public void emptyListSelectsNothing() {
        assertEquals(-1, DevicePick.preselect(Collections.emptyList(), Collections.emptyList(), ""));
    }

    @Test public void momentumMatchIgnoresCase() {
        assertTrue(DevicePick.looksLikeMomentum("Momentum 4"));
        assertFalse(DevicePick.looksLikeMomentum("Ryane's TV"));
        assertFalse(DevicePick.looksLikeMomentum(null));
    }

    // T50: the row must never be blank.
    @Test public void rowTitleIsNeverBlank() {
        assertEquals("MOMENTUM 4", DevicePick.rowTitle("MOMENTUM 4", 3));
        assertEquals("No paired headphones", DevicePick.rowTitle(null, 0));
        assertEquals("Choose the headphones", DevicePick.rowTitle(null, 2));
    }
}
