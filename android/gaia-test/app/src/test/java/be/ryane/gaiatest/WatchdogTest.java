package be.ryane.gaiatest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class WatchdogTest {
    /** Stands in for BluetoothSocket: connect() blocks until close() is called. */
    static final class FakeSocket implements Closeable {
        final CountDownLatch closed = new CountDownLatch(1);
        void connect() throws InterruptedException { closed.await(); }
        @Override public void close() { closed.countDown(); }
    }

    @Test public void closesAHangingConnectAfterTheTimeout() throws Exception {
        FakeSocket s = new FakeSocket();
        long t0 = System.nanoTime();
        Watchdog dog = Watchdog.closeAfter(s, 150);
        s.connect();                                   // would block forever without the watchdog
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        dog.close();
        assertTrue(dog.fired());
        assertTrue("took " + ms + " ms", ms >= 100 && ms < 2000);
    }

    @Test public void cancelledInTimeLeavesTheSocketOpen() throws Exception {
        FakeSocket s = new FakeSocket();
        Watchdog dog = Watchdog.closeAfter(s, 150);
        dog.close();
        assertFalse(s.closed.await(400, TimeUnit.MILLISECONDS));
        assertFalse(dog.fired());
    }

    @Test public void onceRunsOnlyOnce() throws Exception {
        AtomicInteger n = new AtomicInteger();
        Runnable r = Watchdog.once(n::incrementAndGet);
        CountDownLatch late = new CountDownLatch(1);
        Watchdog.runAfter(50, () -> { r.run(); late.countDown(); });   // the deadline
        r.run();                                                         // the normal end
        assertTrue(late.await(2, TimeUnit.SECONDS));
        r.run();
        assertEquals(1, n.get());
    }
}
