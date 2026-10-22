package be.ryane.gaiatest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Foreground service that watches the MOMENTUM 4 and sends the confirmed
 * power-off (0495:0605) once it has been out of use for the chosen time.
 *
 * It holds the GAIA control channel, so Smart Control Plus must be
 * force-stopped while this runs. A foreground service with an ongoing
 * notification is used so Samsung's background freezer leaves it alone.
 *
 * Safety: powering off cannot be undone remotely (wake with the button), so
 * the service fires only when the headset is out of use for the whole window,
 * and treats an unknown on-head state as "in use" (never fires on a guess).
 */
public class AutoOffService extends Service {
    static final String CHANNEL = "autooff";
    static final String EXTRA_ADDR = "addr";
    static final String EXTRA_TIMEOUT_MS = "timeout_ms";
    static final String EXTRA_REQUIRE_OFFHEAD = "require_offhead";
    private static final int NOTIF_ID = 1;
    private static final long POLL_MS = 20_000;

    static volatile boolean running;
    static volatile String lastStatus = "";

    private volatile boolean stop;
    private Thread worker;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;
        String addr = intent.getStringExtra(EXTRA_ADDR);
        long timeout = intent.getLongExtra(EXTRA_TIMEOUT_MS, 15 * 60_000L);
        boolean requireOffHead = intent.getBooleanExtra(EXTRA_REQUIRE_OFFHEAD, true);
        Notification n = buildNotification("Watching for idle...");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        running = true;
        stop = false;
        worker = new Thread(() -> run(addr, timeout, requireOffHead));
        worker.start();
        return START_STICKY;
    }

    private void run(String addr, long timeout, boolean requireOffHead) {
        BluetoothSocket sock = null;
        try {
            BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
            BluetoothDevice device = adapter.getRemoteDevice(addr);
            sock = device.createRfcommSocketToServiceRecord(Gaia.SERVICE_UUID);
            sock.connect();
            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();
            Gaia.Parser parser = new Gaia.Parser();

            // Does this firmware answer the on-head query? Decide the mode up front.
            boolean offHeadWorks = probeOffHead(out, in, parser);
            if (requireOffHead && !offHeadWorks) {
                status("On-head detection not available on this firmware. Not firing. Stop and use audio-only mode if you accept the risk.");
                // Keep the connection and notification but never fire.
            }

            IdlePolicy policy = new IdlePolicy(timeout, now());
            boolean audioActive = false;
            long lastPoll = 0;

            while (!stop) {
                // Drain any notifications (audio stream state arrives unsolicited).
                Boolean audio = readAudioState(in, parser, 1000);
                if (audio != null) audioActive = audio;

                if (now() - lastPoll >= POLL_MS) {
                    lastPoll = now();
                    IdlePolicy.HeadSignal head = IdlePolicy.HeadSignal.UNKNOWN;
                    if (offHeadWorks) head = queryOffHead(out, in, parser);
                    else if (!requireOffHead) head = IdlePolicy.HeadSignal.OFF; // audio-only mode

                    boolean fire = policy.evaluate(head, audioActive, now());
                    long idleMin = policy.idleFor(now()) / 60000;
                    status(String.format("head=%s audio=%s idle=%dm%s", head, audioActive, idleMin,
                            (requireOffHead && !offHeadWorks) ? " (disabled: no on-head)" : ""));

                    if (fire && !(requireOffHead && !offHeadWorks)) {
                        status("Idle timeout reached. Sending power-off (0605).");
                        out.write(Gaia.powerOff());
                        out.flush();
                        sleep(2000);     // let the headset act before the link drops
                        status("Power-off sent. Headset should be off. Stopping.");
                        break;
                    }
                }
                sleep(500);
            }
        } catch (IOException e) {
            status("Disconnected: " + e.getMessage());
        } finally {
            if (sock != null) try { sock.close(); } catch (IOException ignored) {}
            running = false;
            stopForeground(true);
            stopSelf();
        }
    }

    /** One-shot test: ask for physical state, see if we get a real answer. */
    private boolean probeOffHead(OutputStream out, InputStream in, Gaia.Parser parser) throws IOException {
        out.write(Gaia.build(Gaia.SENNHEISER, Gaia.CMD_PHYSICAL_STATE_GET));
        out.flush();
        long end = now() + 1500;
        while (now() < end) {
            for (Gaia.Frame f : readSome(in, parser, 400)) {
                if (f.vendor == Gaia.SENNHEISER && f.base() == Gaia.CMD_PHYSICAL_STATE_GET) {
                    return f.type() == 2 && f.payload.length >= 1;   // got a real response, not an error
                }
            }
        }
        return false;
    }

    private IdlePolicy.HeadSignal queryOffHead(OutputStream out, InputStream in, Gaia.Parser parser) throws IOException {
        out.write(Gaia.build(Gaia.SENNHEISER, Gaia.CMD_PHYSICAL_STATE_GET));
        out.flush();
        long end = now() + 1500;
        while (now() < end) {
            for (Gaia.Frame f : readSome(in, parser, 400)) {
                if (f.vendor == Gaia.SENNHEISER && f.base() == Gaia.CMD_PHYSICAL_STATE_GET && f.type() == 2 && f.payload.length >= 1) {
                    int state = f.payload[0] & 0xFF;   // 1 in case, 2 off head, 3 on head
                    if (state == 3) return IdlePolicy.HeadSignal.ON;
                    if (state == 1 || state == 2) return IdlePolicy.HeadSignal.OFF;
                    return IdlePolicy.HeadSignal.UNKNOWN;
                }
            }
        }
        return IdlePolicy.HeadSignal.UNKNOWN;   // no answer this round: treat as in use
    }

    /** Returns latest audio-active state if an audio notification arrived, else null. */
    private Boolean readAudioState(InputStream in, Gaia.Parser parser, int ms) throws IOException {
        Boolean result = null;
        for (Gaia.Frame f : readSome(in, parser, ms)) {
            if (f.vendor == Gaia.SENNHEISER && f.base() == Gaia.CMD_AUDIO_STREAM_NOTIF && f.payload.length >= 1) {
                result = (f.payload[0] & 0xFF) != 0xFF;   // FF = no stream
            }
        }
        return result;
    }

    private java.util.List<Gaia.Frame> readSome(InputStream in, Gaia.Parser parser, int ms) throws IOException {
        // Non-blocking-ish read: InputStream.available then read, bounded by ms.
        java.util.List<Gaia.Frame> frames = new java.util.ArrayList<>();
        long end = now() + ms;
        byte[] buf = new byte[512];
        while (now() < end) {
            int avail = in.available();
            if (avail <= 0) { sleep(50); continue; }
            int n = in.read(buf, 0, Math.min(avail, buf.length));
            if (n < 0) throw new IOException("stream closed");
            frames.addAll(parser.feed(buf, n));
            if (!frames.isEmpty()) break;
        }
        return frames;
    }

    private void status(String s) {
        lastStatus = s;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(s));
    }

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "Auto power-off", NotificationManager.IMPORTANCE_LOW));
            }
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("M4 auto power-off")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_power_off)
                .setOngoing(true)
                .build();
    }

    static void start(Context ctx, String addr, long timeoutMs, boolean requireOffHead) {
        Intent i = new Intent(ctx, AutoOffService.class);
        i.putExtra(EXTRA_ADDR, addr);
        i.putExtra(EXTRA_TIMEOUT_MS, timeoutMs);
        i.putExtra(EXTRA_REQUIRE_OFFHEAD, requireOffHead);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, AutoOffService.class));
    }

    @Override
    public void onDestroy() {
        stop = true;
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static long now() { return System.currentTimeMillis(); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
