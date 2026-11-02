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

    // audit F-11: a renamed headset may carry a personal name, and reports get pasted in public
    @Test public void customHeadsetNameIsHidden() {
        assertEquals("MOMENTUM (custom name hidden)", Report.safeName("John Smith's MOMENTUM 4"));
        assertEquals("MOMENTUM (custom name hidden)", Report.safeName("MOMENTUM 4 of Ryane"));
        assertEquals("MOMENTUM 4 Wireless", Report.safeName("MOMENTUM 4 Wireless"));
    }

    @Test public void scrubReplacesNamesInLogs() {
        String log = "10:00:01 Auto-connecting to John Smith's MOMENTUM 4...\n10:00:05 John Smith's MOMENTUM 4 connected to the phone.";
        String clean = Report.scrub(log, "John Smith's MOMENTUM 4", "John Smith's MOMENTUM 4", null, "");
        assertFalse(clean.contains("John"));
        assertEquals("10:00:01 Auto-connecting to MOMENTUM (custom name hidden)...\n10:00:05 MOMENTUM (custom name hidden) connected to the phone.", clean);
    }

    @Test public void scrubKeepsStockNameAndOtherText() {
        assertEquals("a MOMENTUM 4 b", Report.scrub("a MOMENTUM 4 b", "MOMENTUM 4"));
        assertEquals("nothing here", Report.scrub("nothing here", (String) null));
    }
}
