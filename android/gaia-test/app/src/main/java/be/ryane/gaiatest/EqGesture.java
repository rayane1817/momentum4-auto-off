package be.ryane.gaiatest;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch-to-gain maths for the v4 faders (design v4 6.1), plain Java so it is unit-tested.
 * A gesture starts from the current gains; a drag or a sweep changes the band under the finger;
 * on release, changes() lists [band, gain] for every band that ended different, in band order.
 */
final class EqGesture {
    static final int MAX = 60;                       // +6.0 dB in dB x 10
    private final int[] start = new int[5], now = new int[5];

    void begin(int[] gains) {
        for (int i = 0; i < 5; i++) { start[i] = clamp(gains[i]); now[i] = start[i]; }
    }

    void set(int band, int gain) { if (band >= 0 && band < 5) now[band] = clamp(gain); }

    int[] gains() { return now.clone(); }

    List<int[]> changes() {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < 5; i++) if (now[i] != start[i]) out.add(new int[]{i, now[i]});
        return out;
    }

    /** Column under x for a view of the given width (5 equal columns). */
    static int bandAt(float x, float width) {
        if (width <= 0) return 0;
        return Math.max(0, Math.min(4, (int) (x / (width / 5f))));
    }

    /** Gain for a finger at y on a track from trackTop (+6 dB) to trackTop + trackH (-6 dB), 0.1 dB steps. */
    static int gainAt(float y, float trackTop, float trackH) {
        if (trackH <= 0) return 0;
        float f = (y - trackTop) / trackH;           // 0 = top, 1 = bottom
        return clamp(Math.round(MAX - f * 2 * MAX));
    }

    static int clamp(int g) { return Math.max(-MAX, Math.min(MAX, g)); }
}
