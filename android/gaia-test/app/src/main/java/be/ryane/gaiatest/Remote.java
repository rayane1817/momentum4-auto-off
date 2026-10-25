package be.ryane.gaiatest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot sends for widgets and Quick Settings tiles: if the app already holds the GAIA link,
 * send through it; otherwise connect, send, wait briefly, disconnect. Nothing stays connected,
 * so the headset stays free for the app, the guard and Smart Control.
 */
final class Remote {
    interface Done { void done(boolean ok, String msg); }

    static final int NOISE_ADAPTIVE = 0, NOISE_CUSTOM = 1, NOISE_OFF = 2;
    static final String[] NOISE_NAMES = {"Adaptive", "Custom", "Off"};

    /** Same frames and order as Smart Control Plus (docs/config-spec.md, capture 2). */
    static List<byte[]> noiseFrames(int mode) {
        List<byte[]> f = new ArrayList<>();
        if (mode == NOISE_ADAPTIVE) { f.add(s(0x1A04, 1)); f.add(s2(0x1A00, 3, 1)); }
        else if (mode == NOISE_CUSTOM) { f.add(s2(0x1A00, 3, 0)); f.add(s(0x1A04, 1)); }
        else { f.add(s2(0x1A00, 3, 0)); f.add(s(0x1A04, 0)); }
        return f;
    }

    static void send(Context ctx, List<byte[]> frames, Done done) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        for (byte[] fr : frames) {
            String why = Gaia.blockedReason(fr);
            if (why != null) { main.post(() -> done.done(false, "blocked: " + why)); return; }
        }
        MainActivity live = MainActivity.live;
        if (live != null && live.isLinked()) {
            main.post(() -> { for (byte[] fr : frames) live.sendFrame(fr); done.done(true, "sent via app"); });
            return;
        }
        new Thread(() -> {
            String addr = app.getSharedPreferences("m4", Context.MODE_PRIVATE).getString("dev", "");
            String result = oneShot(app, addr, frames);
            main.post(() -> done.done(result == null, result == null ? "sent" : result));
        }, "m4-remote").start();
    }

    @SuppressWarnings("MissingPermission")
    private static String oneShot(Context app, String addr, List<byte[]> frames) {
        if (addr.isEmpty()) return "Open the app once and pick the headphones";
        BluetoothSocket sock = null;
        try {
            BluetoothAdapter adapter = ((BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (adapter == null || !adapter.isEnabled()) return "Bluetooth is off";
            BluetoothDevice device = adapter.getRemoteDevice(addr);
            IOException last = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    sock = device.createRfcommSocketToServiceRecord(Gaia.SERVICE_UUID);
                    sock.connect();
                    OutputStream out = sock.getOutputStream();
                    for (byte[] fr : frames) {
                        out.write(fr); out.flush();
                        Log.i("M4REMOTE", "-> " + Gaia.hex(fr));
                        Thread.sleep(60);
                    }
                    Thread.sleep(400);           // let the headset apply before the link closes
                    return null;
                } catch (IOException e) {
                    last = e;
                    close(sock); sock = null;
                    Thread.sleep(800);
                }
            }
            Log.i("M4REMOTE", "failed: " + last);
            return "Headphones busy or off (close Smart Control, check they are on)";
        } catch (SecurityException e) {
            return "Nearby devices permission missing: open the app";
        } catch (InterruptedException e) {
            return "interrupted";
        } catch (RuntimeException e) {
            return "error: " + e.getMessage();
        } finally {
            close(sock);
        }
    }

    private static void close(BluetoothSocket s) { if (s != null) try { s.close(); } catch (IOException ignored) {} }
    private static byte[] s(int cmd, int v) { return Gaia.build(Gaia.SENNHEISER, cmd, (byte) v); }
    private static byte[] s2(int cmd, int a, int b) { return Gaia.build(Gaia.SENNHEISER, cmd, (byte) a, (byte) b); }

    private Remote() {}
}
