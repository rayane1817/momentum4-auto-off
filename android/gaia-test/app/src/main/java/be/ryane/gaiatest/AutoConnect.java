package be.ryane.gaiatest;

/**
 * When the app opens its GAIA link by itself (pure, so it is JVM-testable).
 *
 * MainActivity feeds it Bluetooth events while it is in the foreground and gets back a delay
 * before the next try, or -1 for none. Nothing here runs in the background: no service, no
 * battery exemption. Only a device that looks like a MOMENTUM, or one the user connected by hand
 * before, is ever connected automatically (a paired TV once ended up as the target, T31).
 */
final class AutoConnect {
    /** RFCOMM often fails right after the ACL link comes up: let the profiles settle first. */
    static final long SETTLE_MS = 2000;
    /** Waits after a failed try. After the last one, stop until the headset reconnects or the app returns. */
    static final long[] RETRY_MS = {3000, 6000};
    /** A link that lived this long was fine: losing it later starts a fresh round of tries. */
    static final long STABLE_MS = 10_000;

    private boolean held;   // the user pressed Disconnect: hands off until the headset reconnects or the app returns
    private int tries;      // retries used in this round

    static boolean eligible(String name, boolean connectedByHand) {
        return DevicePick.looksLikeMomentum(name) || connectedByHand;
    }

    /** The app came to the foreground (opened, or back from another app): a Disconnect hold ends. */
    void foreground() { held = false; tries = 0; }

    /** On open, or once the profile proxies answer: connect at once if the target is already up. */
    long check(boolean eligible, boolean headsetUp) {
        return !held && eligible && headsetUp ? 0 : -1;
    }

    /** The target connected to the phone at the Bluetooth level (ACL, A2DP or headset profile). */
    long headsetUp(boolean eligible) {
        held = false;
        tries = 0;
        return eligible ? SETTLE_MS : -1;
    }

    /** The target left the phone: the caller cancels any pending try and waits. */
    void headsetDown() { tries = 0; }

    void userDisconnected() { held = true; }

    void manualConnect() { held = false; tries = 0; }

    boolean held() { return held; }

    /** An automatic try failed. Delay before the next one, or -1 to give up for this round. */
    long tryFailed(boolean headsetUp) {
        if (held || !headsetUp || tries >= RETRY_MS.length) return -1;
        return RETRY_MS[tries++];
    }

    /**
     * The link dropped without a Disconnect tap. Reconnect after the settle time if the headset is
     * still on the phone; a link that keeps dropping right away uses up the round's retries.
     */
    long linkLost(boolean eligible, boolean headsetUp, long upMs) {
        if (held || !eligible || !headsetUp) return -1;
        if (upMs >= STABLE_MS) tries = 0;
        if (tries >= RETRY_MS.length) return -1;
        tries++;
        return SETTLE_MS;
    }

    /** Status while not linked: "Waiting for <name>" when an auto target is off the phone, else null. */
    static String idleText(boolean held, boolean eligible, Boolean headsetUp, String name) {
        if (held || !eligible || !Boolean.FALSE.equals(headsetUp)) return null;
        return "Waiting for " + name;
    }
}
