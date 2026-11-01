package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ReportTest {
    @Test public void redactsAddresses() {
        String r = Report.redact("connect 80:C3:BA:12:6a:24 then aa:bb:cc:dd:ee:ff");
        assertFalse(r.contains("80:C3"));
        assertEquals("connect XX:XX:XX:XX:XX:XX then XX:XX:XX:XX:XX:XX", r);
    }

    @Test public void keepsFramesAndTimes() {
        assertEquals("12:30:45 -> FF 03 00 00 04 95 06 05", Report.redact("12:30:45 -> FF 03 00 00 04 95 06 05"));
    }

    @Test public void tailKeepsLastLines() { assertEquals("c\nd", Report.tail("a\nb\nc\nd\n", 2)); }

    @Test public void hidesNonMomentumNames() {
        assertEquals("MOMENTUM 4", Report.safeName("MOMENTUM 4"));
        assertEquals("(not a MOMENTUM)", Report.safeName("Ryane's TV"));
    }
}
