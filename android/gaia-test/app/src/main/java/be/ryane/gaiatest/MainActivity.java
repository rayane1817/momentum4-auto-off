package be.ryane.gaiatest;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private Spinner deviceSpinner;
    private TextView log;
    private ScrollView logScroll;
    private EditText secondsField, hexField;
    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    // One sender thread keeps frames in the order they were tapped.
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner);
        root.addView(row(button("Connect", v -> connect()), button("Disconnect", v -> disconnect())));

        root.addView(label("Read"));
        root.addView(row(
                button("Auto-off timer (0)", v -> send(Gaia.build(Gaia.SENNHEISER, 0x0601, (byte) 0))),
                button("Limbo timer (1)", v -> send(Gaia.build(Gaia.SENNHEISER, 0x0601, (byte) 1)))));
        root.addView(row(
                button("Timers 2-4", v -> {
                    for (int t = 2; t <= 4; t++) send(Gaia.build(Gaia.SENNHEISER, 0x0601, (byte) t));
                }),
                button("Battery", v -> send(Gaia.build(Gaia.SENNHEISER, 0x0603)))));

        root.addView(label("Set (seconds)"));
        secondsField = new EditText(this);
        secondsField.setInputType(InputType.TYPE_CLASS_NUMBER);
        secondsField.setHint("seconds, e.g. 60");
        root.addView(secondsField);
        root.addView(row(
                button("Set limbo (1)", v -> setTimer(1)),
                button("Set auto-off (0)", v -> setTimer(0))));

        root.addView(label("Raw frame (hex)"));
        hexField = new EditText(this);
        hexField.setHint("FF 03 00 01 04 95 06 01 01");
        hexField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(hexField);
        root.addView(row(button("Send raw", v -> sendRaw()), button("Copy log", v -> copyLog())));

        log = new TextView(this);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextIsSelectable(true);
        logScroll = new ScrollView(this);
        logScroll.addView(log);
        root.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        append("Close Smart Control Plus first (Settings > Apps > Smart Control > Force stop).");
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        } else {
            loadDevices();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadDevices();
        else append("Bluetooth permission denied: the app can't work without it.");
    }

    @SuppressWarnings("MissingPermission")
    private void loadDevices() {
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            append("Bluetooth is off. Turn it on and reopen the app.");
            return;
        }
        devices.clear();
        List<String> names = new ArrayList<>();
        int preselect = 0;
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            String name = d.getName() == null ? d.getAddress() : d.getName();
            if (name.toUpperCase(Locale.ROOT).contains("MOMENTUM")) preselect = devices.size();
            devices.add(d);
            names.add(name + "  " + d.getAddress());
        }
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        deviceSpinner.setSelection(preselect);
        if (devices.isEmpty()) append("No paired devices found.");
    }

    @SuppressWarnings("MissingPermission")
    private void connect() {
        if (socket != null) {
            append("Already connected.");
            return;
        }
        int i = deviceSpinner.getSelectedItemPosition();
        if (i < 0 || i >= devices.size()) return;
        BluetoothDevice device = devices.get(i);
        append("Connecting to " + device.getName() + " (GAIA service)...");
        new Thread(() -> {
            try {
                BluetoothSocket s = device.createRfcommSocketToServiceRecord(Gaia.SERVICE_UUID);
                s.connect();
                socket = s;
                out = s.getOutputStream();
                append("Connected.");
                readLoop(s.getInputStream());
            } catch (IOException e) {
                append("Connect failed: " + e.getMessage()
                        + "\nIs Smart Control Plus still running? Force stop it and try again.");
                closeQuietly();
            }
        }).start();
    }

    private void readLoop(InputStream in) {
        Gaia.Parser parser = new Gaia.Parser();
        byte[] buf = new byte[1024];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                for (Gaia.Frame f : parser.feed(buf, n)) append("<- " + Gaia.describe(f));
            }
        } catch (IOException e) {
            // Socket closed: reported below.
        }
        append("Disconnected.");
        closeQuietly();
    }

    private void setTimer(int timer) {
        String text = secondsField.getText().toString().trim();
        if (text.isEmpty()) {
            toast("Enter seconds first");
            return;
        }
        int secs = Integer.parseInt(text);
        if (secs < 0 || secs > 0xFFFF) {
            toast("0 to 65535 seconds");
            return;
        }
        if (timer == 1 && secs > 0 && secs < 30) {
            toast("Use at least 30 s for limbo, to be safe");
            return;
        }
        send(Gaia.build(Gaia.SENNHEISER, 0x0600, (byte) timer, (byte) (secs >> 8), (byte) secs));
        // Read it back so the log shows what the headset stored.
        send(Gaia.build(Gaia.SENNHEISER, 0x0601, (byte) timer));
    }

    private void sendRaw() {
        try {
            send(Gaia.parseHex(hexField.getText().toString()));
        } catch (IllegalArgumentException e) {
            toast("Bad hex: " + e.getMessage());
        }
    }

    private void send(byte[] frame) {
        String blocked = Gaia.blockedReason(frame);
        if (blocked != null) {
            append("BLOCKED (" + blocked + "): " + Gaia.hex(frame));
            return;
        }
        OutputStream o = out;
        if (o == null) {
            toast("Connect first");
            return;
        }
        sender.execute(() -> {
            try {
                o.write(frame);
                o.flush();
                append("-> " + Gaia.hex(frame));
            } catch (IOException e) {
                append("Send failed: " + e.getMessage());
            }
        });
    }

    private void disconnect() {
        closeQuietly();
        append("Disconnected by user.");
    }

    private void closeQuietly() {
        BluetoothSocket s = socket;
        socket = null;
        out = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("M4 GAIA log", log.getText()));
        toast("Log copied");
    }

    private void append(String line) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        runOnUiThread(() -> {
            log.append(stamp + " " + line + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private LinearLayout row(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        closeQuietly();
        sender.shutdown();
        super.onDestroy();
    }
}
