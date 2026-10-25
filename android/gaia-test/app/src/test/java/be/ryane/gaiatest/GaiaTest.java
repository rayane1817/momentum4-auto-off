package be.ryane.gaiatest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Framing, parser, blocklist and decoders, checked against bytes from the test log. */
public class GaiaTest {
    private static byte[] b(String hex) { return Gaia.parseHex(hex); }

    @Test public void buildsPowerOffAsInT9() {
        assertArrayEquals(b("FF 03 00 00 04 95 06 05"), Gaia.powerOff());
    }

    @Test public void parserSplitsJoinedFramesAndKeepsAPartialOne() {
        Gaia.Parser p = new Gaia.Parser();
        byte[] data = b("00 FF 03 00 01 04 95 07 03 32  FF 03 00 05 04 95 10 82 00 14 19 0F EC  FF 03 00 01 04");
        List<Gaia.Frame> f = p.feed(data, data.length);
        assertEquals(2, f.size());
        assertEquals(0x0703, f.get(0).command);
        assertEquals(2, f.get(0).type());
        assertEquals(0x0603, f.get(0).base());
        assertEquals(1, f.get(1).type());
        assertEquals(0x1002, f.get(1).base());
        assertArrayEquals(b("00 14 19 0F EC"), f.get(1).payload);

        byte[] rest = b("95 1A 85 01");                    // the rest of the third frame
        f = p.feed(rest, rest.length);
        assertEquals(1, f.size());
        assertEquals(0x1A85, f.get(0).command);
        assertArrayEquals(b("01"), f.get(0).payload);
    }

    @Test public void errorReplyIsNotDecodedAsAValue() {
        // 0603 error (0783) with code 05: must not read as "battery 5 %".
        String d = Gaia.describe(new Gaia.Frame(3, Gaia.SENNHEISER, 0x0783, b("05")));
        assertFalse(d, d.contains("battery"));
        assertTrue(d, d.contains("refused (invalid parameter)"));
    }

    @Test public void decodesFirmwareAndEqFromTheLog() {
        assertTrue(Gaia.describe(new Gaia.Frame(3, Gaia.SENNHEISER, 0x1301, b("00 03 00 26 00 03"))).endsWith("firmware = 3.38.3"));
        assertTrue(Gaia.describe(new Gaia.Frame(3, Gaia.SENNHEISER, 0x1082, b("00 14 19 0F EC"))).endsWith("EQ dB: 0.0 2.0 2.5 1.5 -2.0"));
        assertTrue(Gaia.describe(new Gaia.Frame(3, Gaia.SENNHEISER, 0x1200 | 0x100, b("00 04 00 00"))).endsWith("hw revision = 4"));
    }

    private static String blocked(int vendor, int cmd) { return Gaia.blockedReason(Gaia.build(vendor, cmd)); }

    @Test public void blocksDangerousCommands() {
        int[] sennheiser = {0x0040, 0x0200, 0x03FF, 0x1403, 0x1405, 0x1406, 0x0607, 0x0609, 0x06FF, 0x1500};
        for (int c : sennheiser) assertNotNull(String.format("%04X", c), blocked(Gaia.SENNHEISER, c));
        assertNotNull(blocked(Gaia.QUALCOMM, 0x0C00));
        assertNotNull(blocked(Gaia.QUALCOMM, 0x0DFF));
    }

    @Test public void allowsWhatTheAppSends() {
        int[] ok = {0x0605, 0x0600, 0x0601, 0x0603, 0x0007, 0x1A00, 0x1A04, 0x1A02, 0x1001, 0x1003, 0x0405, 0x0805, 0x0814, 0x1606};
        for (int c : ok) assertNull(String.format("%04X", c), blocked(Gaia.SENNHEISER, c));
        assertNull(Gaia.blockedReason(b("FF 03 00")));        // too short to be a frame
    }
}
