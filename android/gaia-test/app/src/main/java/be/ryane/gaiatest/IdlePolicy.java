package be.ryane.gaiatest;

/**
 * Decides when to fire the power-off, kept pure so it can be unit-tested.
 *
 * "In use" = worn (on-head) OR audio streaming. The headphones must be
 * continuously out of use for the whole timeout before we fire. Any sign of
 * use resets the clock. Powering off cannot be undone remotely, so the bias
 * is heavily towards NOT firing: unknown on-head state counts as in use.
 */
final class IdlePolicy {

    /** How confident we are about the on-head signal on this firmware. */
    enum HeadSignal { ON, OFF, UNKNOWN }

    private final long timeoutMs;
    private long lastInUseAt;      // last time we saw the headset in use
    private boolean fired;

    IdlePolicy(long timeoutMs, long now) {
        this.timeoutMs = timeoutMs;
        this.lastInUseAt = now;    // start the clock from "in use", never fire instantly
    }

    /**
     * Feed the latest state. Returns true exactly once, when it is time to
     * power off. After that it stays false (one-shot).
     */
    boolean evaluate(HeadSignal head, boolean audioActive, long now) {
        if (fired) return false;
        boolean inUse = audioActive || head == HeadSignal.ON || head == HeadSignal.UNKNOWN;
        if (inUse) {
            lastInUseAt = now;
            return false;
        }
        if (now - lastInUseAt >= timeoutMs) {
            fired = true;
            return true;
        }
        return false;
    }

    long idleFor(long now) {
        return now - lastInUseAt;
    }

    boolean hasFired() {
        return fired;
    }
}
