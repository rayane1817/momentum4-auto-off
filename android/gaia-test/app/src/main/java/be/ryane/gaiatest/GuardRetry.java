package be.ryane.gaiatest;

/**
 * Back-off for the guard after a failed power-off. The usual cause is that the headphones are
 * already off (case or button), so retrying every poll would page Bluetooth forever. The guard
 * stays armed: any audio or call resets the failures, and the next idle timeout fires normally.
 */
final class GuardRetry {
    private GuardRetry() {}

    /** Wait before the next try after this many failed attempts in a row (1-based). */
    static long delayMs(int failures) {
        if (failures <= 1) return 60_000L;          // a brief glitch: try again in 1 min
        if (failures == 2) return 5 * 60_000L;
        return 30 * 60_000L;                        // most likely already off: check twice an hour
    }

    static String describe(long ms) {
        long min = Math.max(1, ms / 60_000L);
        return min + " min";
    }
}
