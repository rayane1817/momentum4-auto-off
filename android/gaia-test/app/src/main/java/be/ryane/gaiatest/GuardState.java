package be.ryane.gaiatest;

/**
 * What the Auto power-off page and the Home tile show for the guard (pure, JVM-testable).
 * "Sent" = the guard sent 0605 and stopped itself (T53: the page used to stay "Armed").
 */
final class GuardState {
    final boolean on, sent, failed;
    final String dots, dotsDesc, unit, status, tile;

    private GuardState(boolean on, boolean sent, boolean failed, String dots, String dotsDesc,
                       String unit, String status, String tile) {
        this.on = on; this.sent = sent; this.failed = failed;
        this.dots = dots; this.dotsDesc = dotsDesc; this.unit = unit; this.status = status; this.tile = tile;
    }

    /** @param firedHhmm firedAt as "HH:mm" (formatted by the caller, so tests don't depend on the time zone). */
    static GuardState of(boolean running, long firedAt, boolean lastSendFailed, int minutes, String firedHhmm) {
        if (running) {
            boolean failed = lastSendFailed;
            return new GuardState(true, false, failed, String.valueOf(minutes), "Guard on, " + minutes + " minutes idle",
                    "minutes idle, then fully off",
                    failed ? "Couldn't reach the headphones. They may be off already; the guard tries again later."
                           : "Armed. Off after " + minutes + " min with nothing playing and no call.",
                    "Armed · " + minutes + " min");
        }
        if (firedAt > 0) {
            return new GuardState(false, true, false, "SENT", "Power-off sent", "power-off sent",
                    "Power-off sent at " + firedHhmm + ". Wake them with the button.",
                    "Sent · " + firedHhmm);
        }
        return new GuardState(false, false, false, "OFF", "Guard off", "the guard is off",
                "The guard is not running.", "Off");
    }
}
