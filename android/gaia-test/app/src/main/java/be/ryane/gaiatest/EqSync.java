package be.ryane.gaiatest;

/**
 * Whether the app's EQ curve mirrors the headset. Until it does (curve read with 1003 / 1082,
 * or all five bands written), the default all-zero curve must not overwrite the preset shown
 * by the EQ widget and tile (v0.10 bug: a cold start reset them to "Neutral").
 */
final class EqSync {
    private boolean known;

    /** All five bands read from the headset (1003 response or 1082 notification). */
    void curveRead() { known = true; }

    /** All five bands written by the app (a preset or Flat). */
    void curveWritten() { known = true; }

    /** Link lost: widgets may change the headset while the app is not looking. */
    void disconnected() { known = false; }

    /** True when the matching preset may be pushed to the widgets and tiles. */
    boolean mayPublish() { return known; }
}
