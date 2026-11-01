package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuardRetryTest {
    @Test public void growsThenCaps() {
        assertEquals(60_000L, GuardRetry.delayMs(1));
        assertEquals(300_000L, GuardRetry.delayMs(2));
        assertEquals(1_800_000L, GuardRetry.delayMs(3));
        assertEquals(1_800_000L, GuardRetry.delayMs(50));
    }

    @Test public void neverFasterThanThePoll() {
        for (int f = 1; f < 10; f++) assertTrue(GuardRetry.delayMs(f) >= 15_000L);
    }

    @Test public void describesMinutes() { assertEquals("5 min", GuardRetry.describe(300_000L)); }
}
