package be.ryane.gaiatest;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Time limits for blocking Bluetooth calls. BluetoothSocket.connect() has no timeout and takes
 * 5 s or more per attempt when the headset is off; closing the socket from another thread makes
 * connect() throw at once. Plain Java, so it is unit-tested without a device.
 */
final class Watchdog implements AutoCloseable {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "m4-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledFuture<?> task;
    private final AtomicBoolean fired = new AtomicBoolean();

    /** Closes target after ms unless close() is called first. */
    static Watchdog closeAfter(Closeable target, long ms) { return new Watchdog(target, ms); }

    private Watchdog(Closeable target, long ms) {
        task = TIMER.schedule(() -> {
            fired.set(true);
            try { target.close(); } catch (IOException ignored) {}
        }, ms, TimeUnit.MILLISECONDS);
    }

    /** True if the time ran out and the target was closed. */
    boolean fired() { return fired.get(); }

    /** Cancels the timer (the guarded call finished in time). */
    @Override public void close() { task.cancel(false); }

    /** Runs r once after ms, on the watchdog thread. */
    static void runAfter(long ms, Runnable r) { TIMER.schedule(r, ms, TimeUnit.MILLISECONDS); }

    /** Wraps r so it runs at most once, however many callers (deadline and normal end) call it. */
    static Runnable once(Runnable r) {
        AtomicBoolean done = new AtomicBoolean();
        return () -> { if (done.compareAndSet(false, true)) r.run(); };
    }
}
