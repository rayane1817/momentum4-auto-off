package be.ryane.gaiatest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

/** Frames sent by widgets, tiles and preset chips (capture 2, T17, T26). */
public class FramesTest {
    private static byte[] b(String hex) { return Gaia.parseHex(hex); }

    @Test public void noiseFramesMatchSmartControl() {
        List<byte[]> a = Remote.noiseFrames(Remote.NOISE_ADAPTIVE);
        assertArrayEquals(b("FF 03 00 01 04 95 1A 04 01"), a.get(0));
        assertArrayEquals(b("FF 03 00 02 04 95 1A 00 03 01"), a.get(1));
        List<byte[]> c = Remote.noiseFrames(Remote.NOISE_CUSTOM);
        assertArrayEquals(b("FF 03 00 02 04 95 1A 00 03 00"), c.get(0));
        assertArrayEquals(b("FF 03 00 01 04 95 1A 04 01"), c.get(1));
        List<byte[]> o = Remote.noiseFrames(Remote.NOISE_OFF);
        assertArrayEquals(b("FF 03 00 02 04 95 1A 00 03 00"), o.get(0));
        assertArrayEquals(b("FF 03 00 01 04 95 1A 04 00"), o.get(1));
    }

    @Test public void rockPresetIsFiveSignedBandWrites() {
        List<byte[]> f = Presets.frames(Presets.BUILTIN[1]);    // Rock: 0, +2.0, +2.5, +1.5, -2.0 dB
        assertEquals(5, f.size());
        assertArrayEquals(b("FF 03 00 02 04 95 10 01 00 00"), f.get(0));
        assertArrayEquals(b("FF 03 00 02 04 95 10 01 04 EC"), f.get(4));
    }

    @Test public void gainsAreClampedToSixDb() {
        List<byte[]> f = Presets.frames(new int[]{100, -100, 60, -60, 0});
        assertEquals((byte) 60, f.get(0)[9]);
        assertEquals((byte) -60, f.get(1)[9]);
        assertEquals((byte) 60, f.get(2)[9]);
        assertEquals((byte) -60, f.get(3)[9]);
    }

    @Test public void antiWindUiToWireIsOffAutoMaxTo021() {
        assertEquals(0, MainActivity.antiWindSwap(0));
        assertEquals(2, MainActivity.antiWindSwap(1));   // Auto = wire 2 (T17)
        assertEquals(1, MainActivity.antiWindSwap(2));   // Max = wire 1
        for (int i = 0; i < 3; i++) assertEquals(i, MainActivity.antiWindSwap(MainActivity.antiWindSwap(i)));
    }
}
