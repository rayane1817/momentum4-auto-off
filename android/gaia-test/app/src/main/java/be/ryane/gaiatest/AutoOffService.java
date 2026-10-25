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
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service that sends the confirmed power-off (0495:0605) once the
 * headphones have been idle for the chosen time.
 *
 * Idle is judged from the PHONE, not the headset: the headset's own on-head
 * sensor and audio-stream state are unreliable here (the sensor reports
 * on-head when the M4 is on a desk, and Android keeps the audio route open
 * even when nothing plays). So "in use" = audio actually rendering
 * (AudioManager.isMusicActive) OR a call in progress. When neither has been
 * true for the whole timeout, the service connects, sends 0605, and stops.
 *
 * It does NOT hold the control channel while waiting, so the headphones and
 * Smart Control Plus keep working normally until the moment of power-off.
 *
 * Powering off cannot be undone remotely (wake with the button), so the bias
 * is towards not firing: any audio or call resets the clock.
 */
public class AutoOffService extends Service {
    static final String CHANNEL = "autooff";
    static final String EXTRA_ADDR = "addr";
    static final String EXTRA_TIMEOUT_MS = "timeout_ms";
    private static final int NOTIF_ID = 1;
    private static final long POLL_MS = 15_000;

    static volatile boolean running;
    /** For the app's guard panel only: when 0605 was last sent OK, and whether the last attempt failed. */
    static volatile long firedAt;
    static volatile boolean lastSendFailed;
    private static final StringBuilder LOG = new StringBuilder();

    private volatile boolean stop;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;
        String addr;
        long timeout;
        if (intent == null) {
            // Sticky restart after Android killed the process while the guard ran (e.g. Samsung MARS):
            // resume with the saved device and time instead of silently leaving the headphones on.
            android.content.SharedPreferences p = getSharedPreferences("m4", MODE_PRIVATE);
            addr = p.getString("dev", "");
            timeout = p.getInt("minutes", 15) * 60_000L;
            if (addr.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
            log("guard resumed after the app was killed");
        } else {
            addr = intent.getStringExtra(EXTRA_ADDR);
            timeout = intent.getLongExtra(EXTRA_TIMEOUT_MS, 15 * 60_000L);
        }
        Notification n = buildNotification("Watching for idle...");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        running = true;
        stop = false;
        new Thread(() -> run(addr, timeout)).start();
        return START_STICKY;
    }

    private void run(String addr, long timeout) {
        log("guard started, timeout " + (timeout / 60000) + " min; idle = no audio playing and no call");
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            long lastInUse = now();
            while (!stop) {
                boolean music = am != null && am.isMusicActive();
                int mode = am == null ? AudioManager.MODE_NORMAL : am.getMode();
                boolean inCall = mode != AudioManager.MODE_NORMAL;
                boolean inUse = music || inCall;
                if (inUse) lastInUse = now();
                long idleMs = now() - lastInUse;

                String s = String.format(Locale.ROOT, "music=%s call=%s idle=%dm%02ds",
                        music, inCall, idleMs / 60000, (idleMs / 1000) % 60);
                appendLog(s);                       // full detail in the guard log every poll, as before
                long left = Math.max(0, timeout - idleMs);
                notifyText(inUse ? "In use: audio or a call" : "Idle. Switches off in " + ((left + 59_999) / 60_000) + " min");

                if (idleMs >= timeout) {
                    log("idle timeout reached, sending power-off");
                    if (sendPowerOff(addr)) {
                        firedAt = System.currentTimeMillis();
                        lastSendFailed = false;
                        status("Power-off sent. Headset should be off.");
                        log("power-off sent OK (0605)");
                    } else {
                        lastSendFailed = true;
                        status("Could not reach the headset to power off. Will retry next cycle.");
                        log("power-off send FAILED; staying armed");
                        // Do not reset the clock; try again next poll so a brief
                        // connection failure does not disarm the guard.
                        sleepUntilNextPoll();
                        continue;
                    }
                    break;
                }
                sleepUntilNextPoll();
            }
        } catch (Throwable t) {
            log("guard crashed: " + t);
        } finally {
            if (!stop) running = false;   // when stopped, onDestroy already cleared it; a newer guard may be running now
            stopForeground(true);
            stopSelf();
            log("guard stopped");
        }
    }

    /** Per-attempt connect limit. Longer than the widgets' 4.5 s: the guard has no broadcast deadline. */
    static final long CONNECT_DEADLINE_MS = 8000;

    private boolean sendPowerOff(String addr) {
        BluetoothSocket sock = null;
        try {
            BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
            BluetoothDevice device = adapter.getRemoteDevice(addr);
            for (int attempt = 1; attempt <= 3 && !stop; attempt++) {
                Watchdog dog = null;
                try {
                    sock = device.createRfcommSocketToServiceRecord(Gaia.SERVICE_UUID);
                    // connect() has no timeout of its own; the watchdog closes the socket so it throws.
                    dog = Watchdog.closeAfter(sock, CONNECT_DEADLINE_MS);
                    sock.connect();
                    dog.close();
                    if (dog.fired()) throw new IOException("connect timeout after " + CONNECT_DEADLINE_MS + " ms");
                    OutputStream out = sock.getOutputStream();
                    out.write(Gaia.powerOff());
                    out.flush();
                    log("sent " + Gaia.hex(Gaia.powerOff()) + " (attempt " + attempt + ")");
                    sleep(2000);     // let the headset act before the link drops
                    return true;
                } catch (IOException e) {
                    String why = dog != null && dog.fired() ? "connect timeout after " + CONNECT_DEADLINE_MS + " ms" : e.getMessage();
                    log("connect/send attempt " + attempt + " failed: " + why);
                    closeQuietly(sock);
                    sock = null;
                    sleep(3000);
                } finally {
                    if (dog != null) dog.close();
                }
            }
            return false;
        } catch (Throwable t) {
            log("power-off error: " + t);
            return false;
        } finally {
            closeQuietly(sock);
        }
    }

    private void sleepUntilNextPoll() {
        long end = now() + POLL_MS;
        while (!stop && now() < end) sleep(500);
    }

    // ---- visible log (readable from the app via getLog) ----

    static synchronized String getLog() {
        return LOG.length() == 0 ? "(no guard activity yet)" : LOG.toString();
    }

    private static synchronized void appendLog(String line) {
        android.util.Log.i("M4GUARD", line);  // adb logcat -s M4GUARD
        LOG.append(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date())).append(" ").append(line).append("\n");
        if (LOG.length() > 8000) LOG.delete(0, LOG.length() - 8000);
    }

    private void log(String line) {
        appendLog(line);
    }

    private void status(String s) {
        appendLog(s);
        notifyText(s);
    }

    private String lastNotif;

    /** Redraw the ongoing notification only when its text changes (was every 15 s poll). */
    private void notifyText(String text) {
        if (text.equals(lastNotif)) return;
        lastNotif = text;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    private static volatile boolean channelReady;

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26 && !channelReady) {   // create once, not on every update
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "Auto power-off", NotificationManager.IMPORTANCE_LOW));
            }
            channelReady = true;
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("M4 auto power-off")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_power_off)
                .setOngoing(true)
                .build();
    }

    static void start(Context ctx, String addr, long timeoutMs) {
        Intent i = new Intent(ctx, AutoOffService.class);
        i.putExtra(EXTRA_ADDR, addr);
        i.putExtra(EXTRA_TIMEOUT_MS, timeoutMs);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, AutoOffService.class));
    }

    private static void closeQuietly(BluetoothSocket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
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
