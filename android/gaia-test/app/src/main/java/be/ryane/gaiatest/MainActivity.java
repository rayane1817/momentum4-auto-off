package be.ryane.gaiatest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
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
    /** Design tokens for the current light/dark configuration (res/values(-night)/colors.xml). */
    private Tok T;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final List<View> gated = new ArrayList<>();            // disabled when not connected
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private Spinner deviceSpinner;
    private TextView deviceName, headerBattery, statusLine, noiseHint, guardBig, guardDot;
    private Button connectBtn;
    private EqCurve eqCurve;
    private final List<String> presetNames = new ArrayList<>();
    private boolean userDisconnect, connecting;
    private TextView vBattery, vModel, vFirmware, vHardware, guardStatus, log;
    private ScrollView logScroll;
    private Segmented noise, antiWind;
    private View customBlock;
    private SeekBar transpSeek, callSeek;
    private TextView transpValue, callValue;
    private Switch swSmartPause, swAutoAnswer, swLowLatency, swHiRes, swOnHead, swAutoStart;
    private Switch swBass, swSpeech, swComfort, swTouch;
    private static final String[] EQ_BANDS = {"63 Hz", "250 Hz", "1 kHz", "4 kHz", "8 kHz"};
    private final SeekBar[] eqSeek = new SeekBar[5];
    private final int[] eqGain = new int[5];                     // current curve, dB x 10
    private final EqSync eqSync = new EqSync();                  // eqGain mirrors the headset?
    private LinearLayout presetChips;
    private final List<Button> presetBtns = new ArrayList<>();
    /** The app instance holding the GAIA link, used by widgets/tiles to send through it. */
    static volatile MainActivity live;
    private final TextView[] eqValue = new TextView[5];
    private final java.util.ArrayDeque<Integer> pendingBands = new java.util.ArrayDeque<>();   // 1002 GETs in flight
    private Button guardButton;
    private EditText idleMinutes, secondsField, hexField;

    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    private boolean ancOn, adaptiveOn, applying;
    private SharedPreferences prefs;

    // Re-scan the paired-device list when Bluetooth turns on or a device is paired,
    // so the picker is never stuck empty (the Galaxy Tab "doesn't detect" case).
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (hasBtPermission()) loadDevices();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("m4", MODE_PRIVATE);
        T = new Tok(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(T.surface);                       // shows behind the status bar, like the header
        root.addView(header());
        root.addView(hairline());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        content.addView(noiseCard());
        content.addView(eqCard());
        content.addView(guardCard());
        content.addView(listeningCard());
        content.addView(deviceCard());
        content.addView(powerCard());
        content.addView(advancedCard());
        content.addView(logCard());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(T.bg);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        padForSystemBars(root);

        setConnected(false);
        swAutoStart.setChecked(prefs.getBoolean("autostart", false));
        if (!hasBtPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        } else {
            loadDevices();
        }
        // Auto-start the idle guard once at launch, if the user asked for it.
        if (prefs.getBoolean("autostart", false) && selectedDevice() != null) startGuard();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(btReceiver, f);
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(btReceiver); } catch (IllegalArgumentException ignored) {}
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasBtPermission()) loadDevices();   // pick up anything paired while we were away
        refreshGuardButton();
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void rescan() {
        if (!hasBtPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        } else {
            loadDevices();
        }
    }

    // ---------------------------------------------------------------- views
    // Design v2, direction A "Copper and graphite": docs/design-spec-v2.md. Restyle only:
    // every control sends exactly the frames it sent in v0.10.1.

    /**
     * targetSdk 35 draws edge to edge on Android 15+: without this the header (status, device
     * picker, Rescan) sits under the status bar. The status bar strip takes the header's colour;
     * the keyboard also pushes the content up (fields in Advanced and the guard panel).
     */
    private void padForSystemBars(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackgroundColor(T.surface);
        h.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout l1 = new LinearLayout(this);
        l1.setGravity(Gravity.CENTER_VERTICAL);
        deviceName = text("M4", 24, Tok.MEDIUM, T.ink);
        l1.addView(deviceName, new LinearLayout.LayoutParams(0, -2, 1f));
        headerBattery = text("", 28, Tok.MONO, T.ink);
        l1.addView(headerBattery);
        h.addView(l1);

        statusLine = caption("");
        statusLine.setPadding(0, dp(2), 0, dp(8));
        h.addView(statusLine);

        LinearLayout l3 = new LinearLayout(this);
        l3.setGravity(Gravity.CENTER_VERTICAL);
        TextView hp = body("Headphones");
        hp.setTextColor(T.ink2);
        l3.addView(hp);
        deviceSpinner = new Spinner(this);
        deviceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { showDeviceName(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) { showDeviceName(); }
        });
        l3.addView(deviceSpinner, new LinearLayout.LayoutParams(0, -2, 1f));
        l3.addView(textBtn("Rescan", v -> rescan()));
        h.addView(l3);

        connectBtn = baseBtn("Connect", v -> { if (socket != null) disconnect(); else connect(); });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48));
        cp.topMargin = dp(8);
        h.addView(connectBtn, cp);
        return h;
    }

    @SuppressWarnings("MissingPermission")
    private void showDeviceName() {
        BluetoothDevice d = selectedDevice();
        String n = d == null ? "M4" : (d.getName() == null ? d.getAddress() : d.getName());
        deviceName.setText(n);
        prefs.edit().putString("devName", n).apply();
    }

    /** Header status line: pilot dot (filled = live, hollow = not connected, faint = busy) + text. */
    private void setStatus(int kind, String s) {
        String dot = kind == 1 ? "●" : kind == 2 ? "◌" : "○";
        int col = kind == 1 ? T.pilot : kind == 2 ? Tok.alpha(T.pilot, 0.4f) : T.ruleStrong;
        android.text.SpannableString sp = new android.text.SpannableString(dot + "  " + s);
        sp.setSpan(new android.text.style.ForegroundColorSpan(col), 0, 1, 0);
        statusLine.setText(sp);
    }

    private View deviceCard() {
        LinearLayout c = card("Device", false);
        Button refresh = textBtn("Refresh", v -> readAllConfig());
        gate(refresh);
        titleRowOf(c).addView(refresh);
        vBattery = valueRow(c, "Battery");
        vModel = valueRow(c, "Model");
        vFirmware = valueRow(c, "Firmware");
        vHardware = valueRow(c, "Hardware");
        return cardWrap(c);
    }

    private View noiseCard() {
        LinearLayout c = card("Noise control", true);
        noiseHint = caption("Connect to change settings.");
        noiseHint.setPadding(0, 0, 0, dp(8));
        c.addView(noiseHint);
        // Same frames, same order as Smart Control Plus (docs/config-spec.md, capture 2).
        noise = new Segmented(new String[]{"Adaptive", "Custom", "Off"}, 48, idx -> {
            if (idx == 0) { send(a(0x1A04, 1)); send(b2(0x1A00, 3, 1)); ancOn = true; adaptiveOn = true; }
            else if (idx == 1) {
                send(b2(0x1A00, 3, 0)); send(a(0x1A04, 1)); send(a(0x1A02, transpSeek.getProgress()));
                ancOn = true; adaptiveOn = false;
            } else { send(b2(0x1A00, 3, 0)); send(a(0x1A04, 0)); ancOn = false; adaptiveOn = false; }
            customBlock.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
            Widgets.setNoise(this, idx);
        });
        c.addView(noise.view);

        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        b.setLayoutParams(lp);
        TextView[] tv = new TextView[1];
        transpSeek = slider(b, "ANC to transparency", 100, tv,
                p -> p == 0 ? "full ANC" : p == 100 ? "full transparency" : String.valueOf(p),
                p -> { send(a(0x1A02, p)); send(g(0x1A03)); });
        transpValue = tv[0];
        LinearLayout ends = new LinearLayout(this);
        ends.addView(caption("ANC"), new LinearLayout.LayoutParams(0, -2, 1f));
        ends.addView(caption("Transparency"));
        b.addView(ends);
        TextView aw = caption("Anti-wind");
        aw.setPadding(0, dp(16), 0, dp(6));
        b.addView(aw);
        // UI order Off/Auto/Max; wire values 0 = off, 1 = Max, 2 = Auto (T17, confirmed on hardware)
        antiWind = new Segmented(new String[]{"Off", "Auto", "Max"}, 40, idx -> send(b2(0x1A00, 1, antiWindSwap(idx))));
        b.addView(antiWind.view);
        customBlock = b;
        customBlock.setVisibility(View.GONE);
        c.addView(customBlock);
        return cardWrap(c);
    }

    /** Labelled fader that sends only on release. tvOut[0] receives the value label. */
    private SeekBar slider(LinearLayout parent, String label, int max, TextView[] tvOut, IntLabel fmt, OnInt onRelease) {
        LinearLayout cap = new LinearLayout(this);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.setPadding(0, dp(8), 0, 0);
        cap.addView(body(label), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = text("", 16, Tok.MONO, T.ink);
        cap.addView(val);
        parent.addView(cap);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgressTintList(ColorStateList.valueOf(T.copper));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(T.ruleStrong));
        GradientDrawable cap6 = rounded(T.ink, 6, T.surface, 2);
        cap6.setSize(dp(14), dp(24));
        s.setThumb(cap6);
        s.setSplitTrack(false);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) { val.setText(fmt.f(p)); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) { if (!applying) onRelease.on(sb.getProgress()); }
        });
        val.setText(fmt.f(0));
        gate(s);
        parent.addView(s, new LinearLayout.LayoutParams(-1, dp(40)));
        tvOut[0] = val;
        return s;
    }

    private View eqCard() {
        LinearLayout c = card("Sound", true);
        Button flat = textBtn("Flat", v -> {
            for (int i = 0; i < 5; i++) send(b2(0x1001, i, 0));
            eqSync.curveWritten();
            setCurve(new int[5]);
        });
        gate(flat);
        titleRowOf(c).addView(flat);

        // Smart Control Plus presets (app-side; the headset only stores the curve).
        // Values from omarchy-momentum4 presets.json, checked against our own capture 2.
        // Plus up to 5 custom presets saved on the phone (long-press a custom one to rename, overwrite or delete).
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        presetChips = new LinearLayout(this);
        hs.addView(presetChips);
        c.addView(hs);

        eqCurve = new EqCurve(this, T);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(96));
        ep.topMargin = dp(12);
        ep.bottomMargin = dp(4);
        c.addView(eqCurve, ep);

        for (int i = 0; i < 5; i++) {
            final int band = i;
            TextView[] tv = new TextView[1];
            // 0..120 = -6.0..+6.0 dB in 0.1 dB steps (as in Smart Control); wire value = dB x 10, signed byte
            eqSeek[i] = slider(c, EQ_BANDS[i], 120, tv, p -> String.format(Locale.ROOT, "%+.1f dB", (p - 60) / 10.0),
                    p -> { send(b2(0x1001, band, p - 60)); eqGain[band] = p - 60; eqCurve.setGains(eqGain); markPreset(); });
            eqValue[i] = tv[0];
            eqSeek[i].setProgress(60);
        }
        Button save = textBtn("Save curve as custom preset", v -> saveCustomDialog());
        gate(save);
        c.addView(save);
        rebuildPresetChips();
        swBass = switchRow(c, "Bass boost", null, on -> toggle(0x1008, on));
        swSpeech = switchRow(c, "Speech clarity", null, on -> send(b2(0x0803, 0, on ? 2 : 1)));
        gate(swBass); gate(swSpeech);
        return cardWrap(c);
    }

    private void setEq(int band, int gainX10) {
        eqGain[band] = gainX10;
        eqSeek[band].setProgress(Math.max(0, Math.min(120, gainX10 + 60)));
        eqCurve.setGains(eqGain);
        markPreset();
    }

    /** All five bands at once, then one preset match (no half-updated curve reaches the widgets). */
    private void setCurve(int[] gainsX10) {
        for (int b = 0; b < 5; b++) {
            eqGain[b] = gainsX10[b];
            eqSeek[b].setProgress(Math.max(0, Math.min(120, gainsX10[b] + 60)));
        }
        eqCurve.setGains(eqGain);
        markPreset();
    }

    private void setCurve(byte[] payload) {
        int[] g = new int[5];
        for (int b = 0; b < 5; b++) g[b] = payload[b];
        eqSync.curveRead();
        setCurve(g);
    }

    private void applyPreset(Presets.Preset p) {
        for (byte[] f : Presets.frames(p.gains)) send(f);
        eqSync.curveWritten();
        setCurve(p.gains);
    }

    private void rebuildPresetChips() {
        presetChips.removeAllViews();
        gated.removeAll(presetBtns);
        presetBtns.clear();
        presetNames.clear();
        for (Presets.Preset p : Presets.all(this)) {
            String label = p.slot < 0 ? p.name : (p.slot + 1) + " " + p.name;   // custom: slot number first
            Button b = baseBtn(label, v -> applyPreset(p));
            b.setTypeface(Tok.MEDIUM);
            b.setTextSize(15);
            b.setPadding(dp(14), 0, dp(14), 0);
            if (p.slot >= 0) b.setOnLongClickListener(v -> { manageCustomDialog(p); return true; });
            boolean on = out != null;
            b.setEnabled(on); b.setAlpha(on ? 1f : DISABLED);
            gate(b);
            presetBtns.add(b);
            presetNames.add(label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.rightMargin = dp(8);
            presetChips.addView(b, lp);
        }
        markPreset();
    }

    /** Highlight the preset whose curve matches exactly, like Smart Control does (pilot dot + ink fill). */
    private void markPreset() {
        if (presetChips == null) return;
        int m = Presets.matching(this, eqGain);
        for (int i = 0; i < presetBtns.size(); i++) {
            boolean sel = i == m;
            Button b = presetBtns.get(i);
            b.setBackground(rounded(sel ? T.ink : T.sunk, 18, 0, 0));
            b.setTextColor(sel ? T.onKey : T.ink);
            b.setText(sel ? dotted(presetNames.get(i), T.pilotOnKey) : presetNames.get(i));
        }
        if (eqSync.mayPublish()) Widgets.setEqIdx(this, m);
    }

    private void saveCustomDialog() {
        String[] slots = new String[Presets.CUSTOM_SLOTS];
        int firstFree = -1;
        for (int i = 0; i < slots.length; i++) {
            Presets.Preset p = Presets.custom(this, i);
            slots[i] = "Slot " + (i + 1) + ": " + (p == null ? "(empty)" : p.name);
            if (p == null && firstFree < 0) firstFree = i;
        }
        final int[] chosen = {firstFree < 0 ? 0 : firstFree};
        EditText name = field(InputType.TYPE_CLASS_TEXT, "Name, e.g. Commute");
        name.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Save custom preset")
                .setView(padded(name))
                .setSingleChoiceItems(slots, chosen[0], (d, w) -> chosen[0] = w)
                .setPositiveButton("Save", (d, w) -> {
                    Presets.saveCustom(this, chosen[0], name.getText().toString(), eqGain.clone());
                    rebuildPresetChips();
                    Widgets.refresh(this);
                    toast("Saved in slot " + (chosen[0] + 1));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void manageCustomDialog(Presets.Preset p) {
        EditText name = field(InputType.TYPE_CLASS_TEXT, "");
        name.setText(p.name);
        name.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Custom preset " + (p.slot + 1))
                .setView(padded(name))
                .setPositiveButton("Rename", (d, w) -> { Presets.saveCustom(this, p.slot, name.getText().toString(), p.gains); rebuildPresetChips(); Widgets.refresh(this); })
                .setNeutralButton("Overwrite with current curve", (d, w) -> { Presets.saveCustom(this, p.slot, name.getText().toString(), eqGain.clone()); rebuildPresetChips(); Widgets.refresh(this); })
                .setNegativeButton("Delete", (d, w) -> { Presets.deleteCustom(this, p.slot); rebuildPresetChips(); Widgets.refresh(this); })
                .show();
    }

    boolean isLinked() { return out != null; }

    /** Used by widgets/tiles while the app holds the link. */
    void sendFrame(byte[] frame) { send(frame); }

    private View listeningCard() {
        LinearLayout c = card("Listening", false);
        swSmartPause = switchRow(c, "Smart pause", null, on -> toggle(0x080C, on));
        swAutoAnswer = switchRow(c, "Auto-answer calls", null, on -> toggle(0x080A, on));
        swOnHead = switchRow(c, "On-head detection", null, on -> toggle(0x0400, on));
        swLowLatency = switchRow(c, "Low latency", null, on -> toggle(0x0817, on));
        swComfort = switchRow(c, "Comfort call", null, on -> toggle(0x0814, on));
        swTouch = switchRow(c, "Touch controls", null, on -> send(a(0x1606, on ? 0 : 1)));   // inverted: 0 = enabled
        gate(swComfort); gate(swTouch);
        swHiRes = switchRow(c, "Hi-Res 24/96", "Takes effect after you restart the headphones.",
                on -> { send(a(0x0405, on ? 0 : 1)); send(g(0x0406)); });   // 0 = Hi-Res on
        gate(swSmartPause); gate(swAutoAnswer); gate(swOnHead); gate(swLowLatency); gate(swHiRes);
        TextView[] tv = new TextView[1];
        callSeek = slider(c, "Transparency during calls", 4, tv, p -> p == 0 ? "off" : p + " / 4",
                p -> { send(a(0x0805, p)); send(g(0x0806)); });
        callValue = tv[0];
        return cardWrap(c);
    }

    private View guardCard() {
        LinearLayout c = card("Auto power-off", true);
        guardDot = text("", 16, Tok.REGULAR, T.pilot);
        titleRowOf(c).addView(guardDot);

        guardBig = text("Off", 28, Tok.MONO, T.ink);
        c.addView(guardBig);
        guardStatus = caption("");
        guardStatus.setPadding(0, 0, 0, dp(12));
        c.addView(guardStatus);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.addView(body("Idle time"), new LinearLayout.LayoutParams(0, -2, 1f));
        idleMinutes = field(InputType.TYPE_CLASS_NUMBER, "");
        idleMinutes.setText(String.valueOf(prefs.getInt("minutes", 15)));
        idleMinutes.setGravity(Gravity.CENTER);
        idleMinutes.setTypeface(Tok.MONO);
        row.addView(idleMinutes, new LinearLayout.LayoutParams(dp(72), dp(44)));
        TextView mins = body("min");
        mins.setPadding(dp(8), 0, 0, 0);
        row.addView(mins);
        c.addView(row);

        swAutoStart = switchRow(c, "Start the guard when the app opens", null, on -> prefs.edit().putBoolean("autostart", on).apply());

        guardButton = baseBtn("Start guard", v -> toggleGuard());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(48));
        gp.topMargin = dp(8);
        c.addView(guardButton, gp);

        TextView cap = caption("Idle means the phone plays nothing and has no call. If you wear them in silence past the time, they switch off. Wake them with the button.");
        cap.setPadding(0, dp(12), 0, 0);
        c.addView(cap);
        c.addView(textBtn("Guard log", v -> append(AutoOffService.getLog())));
        return cardWrap(c);
    }

    private View powerCard() {
        LinearLayout c = card(null, false);
        Button p = baseBtn("Power off now", v -> powerOffNow());
        p.setTextColor(T.danger);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.alpha(T.danger, 0.12f), 10, T.danger, 1.5f));
        sld.addState(new int[]{}, rounded(T.transparent, 10, T.danger, 1.5f));
        p.setBackground(sld);
        gate(p);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(48));
        pp.topMargin = dp(4);
        c.addView(p, pp);
        TextView cap = caption("Fully off, like the case or a 3-second hold. Only the button turns them back on.");
        cap.setPadding(0, dp(8), 0, 0);
        c.addView(cap);
        return cardWrap(c);
    }

    private View advancedCard() {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);

        inner.addView(caption("Firmware timer. On 3.38.3 it only reaches standby, not off."));
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        tr.setPadding(0, dp(8), 0, dp(8));
        Button readT = outlineBtn("Read", v -> send(g(0x0601, 0)));
        gate(readT);
        tr.addView(readT);
        secondsField = field(InputType.TYPE_CLASS_NUMBER, "seconds");
        secondsField.setTypeface(Tok.MONO);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        sp.leftMargin = dp(8); sp.rightMargin = dp(8);
        tr.addView(secondsField, sp);
        Button setT = outlineBtn("Set", v -> setTimer0());
        gate(setT);
        tr.addView(setT);
        inner.addView(tr);

        inner.addView(caption("Raw frame (hex)"));
        hexField = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, "FF 03 00 00 04 95 06 03");
        hexField.setTypeface(Tok.MONO);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(44));
        hp.topMargin = dp(4); hp.bottomMargin = dp(8);
        inner.addView(hexField, hp);
        Button sendRaw = outlineBtn("Send raw", v -> sendRaw());
        gate(sendRaw);
        inner.addView(sendRaw);

        return cardWrap(collapsible("Advanced", inner, true));
    }

    private View logCard() {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        log = new TextView(this);
        log.setTypeface(Tok.MONO);
        log.setTextSize(11);
        log.setTextColor(T.ink2);
        log.setTextIsSelectable(true);
        log.setPadding(dp(8), dp(8), dp(8), dp(8));
        logScroll = new ScrollView(this);
        logScroll.setBackground(rounded(T.sunk, 10, 0, 0));
        logScroll.addView(log);
        inner.addView(logScroll, new LinearLayout.LayoutParams(-1, dp(200)));
        inner.addView(textBtn("Copy", v -> copyLog()));
        return cardWrap(collapsible("Log", inner, true));
    }

    // ------------------------------------------------------------- helpers

    private static final float DISABLED = 0.38f;

    /**
     * Primary = a surface panel with a title1 heading (Noise, Sound, Auto power-off).
     * Secondary = no panel, a hairline above and a title2 heading. Returns the content column;
     * the title row is the first child of the column's parent.
     */
    private LinearLayout card(String title, boolean primary) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setTag(primary ? "primary" : "secondary");
        if (primary) {
            c.setBackground(rounded(T.surface, 14, 0, 0));
            c.setPadding(dp(16), dp(16), dp(16), dp(16));
        } else {
            c.addView(hairline());
            c.setPadding(0, 0, 0, dp(8));
        }
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        if (title != null) {
            TextView t = primary ? text(title, 20, Tok.MEDIUM, T.ink) : text(title, 16, Tok.MEDIUM, T.ink2);
            t.setPadding(0, primary ? 0 : dp(12), 0, dp(8));
            titleRow.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        c.addView(titleRow);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        c.addView(body);
        return body;
    }

    private LinearLayout titleRowOf(LinearLayout body) {
        ViewGroup c = (ViewGroup) body.getParent();
        return (LinearLayout) c.getChildAt(c.indexOfChild(body) - 1);
    }

    private View cardWrap(View v) {
        // v is a collapsible root (tagged), or a card body whose parent is the card.
        View card = "collapsible".equals(v.getTag()) ? v : (View) v.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = "primary".equals(card.getTag()) ? dp(24) : dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private View hairline() {
        View v = new View(this);
        v.setBackgroundColor(T.rule);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        return v;
    }

    private TextView valueRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        TextView l = body(label);
        l.setTextColor(T.ink2);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = text(NOT_READ, 16, Tok.MONO, T.ink2);
        row.addView(val);
        parent.addView(row);
        return val;
    }

    private static final String NOT_READ = "not read";

    /** A readout value: monospace ink once known, "not read" in ink2 before. */
    private void setValue(TextView v, String s) {
        v.setText(s);
        v.setTextColor(NOT_READ.equals(s) ? T.ink2 : T.ink);
    }

    private Switch switchRow(LinearLayout parent, String label, String note, OnBool onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(body(label));
        if (note != null) labels.addView(caption(note));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = new Switch(this);
        tintSwitch(s);
        s.setOnCheckedChangeListener((b, checked) -> { if (!applying) onChange.on(checked); });
        row.addView(s);
        parent.addView(row);
        return s;
    }

    private View collapsible(String title, View inner, boolean startCollapsed) {
        LinearLayout c = new LinearLayout(this);
        c.setTag("collapsible");
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(hairline());
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setMinimumHeight(dp(52));
        headerRow.addView(text(title, 16, Tok.MEDIUM, T.ink2), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chev = text(startCollapsed ? "›" : "⌄", 18, Tok.REGULAR, T.ink2);
        headerRow.addView(chev);
        inner.setVisibility(startCollapsed ? View.GONE : View.VISIBLE);
        headerRow.setOnClickListener(v -> {
            boolean show = inner.getVisibility() == View.GONE;
            inner.setVisibility(show ? View.VISIBLE : View.GONE);
            chev.setText(show ? "⌄" : "›");
        });
        c.addView(headerRow);
        c.addView(inner);
        return c;
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    private TextView body(String s) { return text(s, 16, Tok.REGULAR, T.ink); }

    private TextView caption(String s) { return text(s, 13, Tok.REGULAR, T.ink2); }

    /** "● label" with the dot in a given colour (the pilot dot). */
    private CharSequence dotted(String label, int dotColor) {
        android.text.SpannableString sp = new android.text.SpannableString("●  " + label);
        sp.setSpan(new android.text.style.ForegroundColorSpan(dotColor), 0, 1, 0);
        return sp;
    }

    private EditText field(int inputType, String hint) {
        EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setHint(hint);
        e.setTextColor(T.ink);
        e.setHintTextColor(T.ink2);
        e.setTextSize(16);
        e.setBackground(rounded(T.sunk, 10, 0, 0));
        e.setPadding(dp(12), 0, dp(12), 0);
        return e;
    }

    private View padded(View v) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(v, new android.widget.FrameLayout.LayoutParams(-1, dp(48)));
        return f;
    }

    /** Copper fill: the one primary action of a panel. */
    private void styleFilled(Button b) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.mix(T.copper, T.ink, 0.2f), 10, 0, 0));
        sld.addState(new int[]{}, rounded(T.copper, 10, 0, 0));
        b.setBackground(sld);
        b.setTextColor(T.onCopper);
    }

    /** Outline: a secondary action, or the "undo" side of a toggle (Disconnect, Stop guard). */
    private void styleOutline(Button b) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.alpha(T.ink, 0.08f), 10, T.ruleStrong, 1));
        sld.addState(new int[]{}, rounded(T.transparent, 10, T.ruleStrong, 1));
        b.setBackground(sld);
        b.setTextColor(T.ink);
    }

    private Button outlineBtn(String s, View.OnClickListener l) {
        Button b = baseBtn(s, l);
        styleOutline(b);
        return b;
    }

    private Button textBtn(String s, View.OnClickListener l) {
        Button b = new Button(this, null);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Tok.MEDIUM);
        b.setTextColor(T.copper);
        b.setBackgroundColor(T.transparent);
        b.setPadding(dp(4), dp(8), dp(4), dp(8));
        b.setOnClickListener(l);
        return b;
    }

    private Button baseBtn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Tok.MEDIUM);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    private GradientDrawable rounded(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void tintSwitch(Switch s) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{T.copper, T.surface}));
        s.setTrackTintList(new ColorStateList(st, new int[]{Tok.alpha(T.copper, 0.45f), T.ruleStrong}));
    }

    private void gate(View v) { gated.add(v); }

    private void setGroupEnabled(boolean on) {
        for (View v : gated) { v.setEnabled(on); v.setAlpha(on ? 1f : DISABLED); }
        if (noiseHint != null) noiseHint.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    /** Segmented keys on a sunk track; the selected key is ink-filled with the pilot dot. */
    private final class Segmented {
        final LinearLayout view;
        final Button[] buttons;
        final String[] labels;
        final OnInt onSelect;
        int selected = -1;

        Segmented(String[] opts, int heightDp, OnInt onSelect) {
            this.onSelect = onSelect;
            this.labels = opts;
            view = new LinearLayout(MainActivity.this);
            view.setBackground(rounded(T.sunk, 10, 0, 0));
            view.setPadding(dp(3), dp(3), dp(3), dp(3));
            buttons = new Button[opts.length];
            for (int i = 0; i < opts.length; i++) {
                final int idx = i;
                Button b = new Button(MainActivity.this);
                b.setText(opts[i]);
                b.setAllCaps(false);
                b.setTextSize(15);
                b.setTypeface(Tok.MEDIUM);
                if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
                b.setBackgroundColor(T.transparent);
                b.setTextColor(T.ink);
                b.setOnClickListener(v -> select(idx, true));
                gate(b);
                buttons[i] = b;
                view.addView(b, new LinearLayout.LayoutParams(0, dp(heightDp - 6), 1f));
            }
        }

        void select(int i, boolean fire) {
            selected = i;
            for (int j = 0; j < buttons.length; j++) {
                boolean sel = j == i;
                buttons[j].setBackground(sel ? rounded(T.ink, 8, 0, 0) : null);
                buttons[j].setTextColor(sel ? T.onKey : T.ink);
                buttons[j].setText(sel ? dotted(labels[j], T.pilotOnKey) : labels[j]);
            }
            if (fire) onSelect.on(i);
        }
    }

    interface OnBool { void on(boolean checked); }
    interface OnInt { void on(int index); }
    interface IntLabel { String f(int v); }

    // ----------------------------------------------------- frame shorthands

    private static byte[] a(int cmd, int v) { return Gaia.build(Gaia.SENNHEISER, cmd, (byte) v); }
    /** Maps UI index (Off, Auto, Max) to wire value (0, 2, 1) and back: the mapping is its own inverse. */
    static int antiWindSwap(int v) { return v == 1 ? 2 : v == 2 ? 1 : v; }

    private static byte[] b2(int cmd, int x, int y) { return Gaia.build(Gaia.SENNHEISER, cmd, (byte) x, (byte) y); }
    private static byte[] g(int cmd) { return Gaia.build(Gaia.SENNHEISER, cmd); }
    private static byte[] g(int cmd, int v) { return Gaia.build(Gaia.SENNHEISER, cmd, (byte) v); }

    private void toggle(int setCmd, boolean on) { send(a(setCmd, on ? 1 : 0)); }

    // -------------------------------------------------------- device state

    /** Update the UI from a reply/notification without firing writes. */
    private void onFrame(Gaia.Frame f) {
        if (f.vendor != Gaia.SENNHEISER) return;
        int base = f.base();
        int b0 = f.payload.length >= 1 ? f.payload[0] & 0xFF : -1;
        applying = true;
        switch (base) {
            case 0x0603: if (b0 >= 0) { setValue(vBattery, b0 + " %"); headerBattery.setText(b0 + " %"); } break;
            case 0x1206: setValue(vModel, ascii(f.payload)); break;
            case 0x1201: if (f.payload.length >= 6)                          // firmware = 3 x u16 BE
                    setValue(vFirmware, Gaia.u16(f.payload, 0) + "." + Gaia.u16(f.payload, 2) + "." + Gaia.u16(f.payload, 4));
                break;
            case 0x1200: if (f.payload.length >= 2) setValue(vHardware, String.valueOf(Gaia.u16(f.payload, 0)));
                else if (b0 >= 0) setValue(vHardware, String.valueOf(b0));
                break;
            case 0x1A05: if (b0 >= 0 && f.type() != 3) { ancOn = b0 == 1; refreshNoise(); } break;
            case 0x1A01:                                                     // [mode,state] pairs: 1 anti-wind, 3 adaptive
                if (f.type() == 3) break;
                for (int i = 0; i + 1 < f.payload.length; i += 2) {
                    int mode = f.payload[i] & 0xFF, st = f.payload[i + 1] & 0xFF;
                    if (mode == 3) adaptiveOn = st == 1;
                    if (mode == 1 && st <= 2) antiWind.select(antiWindSwap(st), false);
                }
                refreshNoise();
                break;
            case 0x1A03: if (b0 >= 0 && b0 <= 100 && f.type() != 3) transpSeek.setProgress(b0); break;
            case 0x080D: swSmartPause.setChecked(b0 == 1); break;
            case 0x080B: swAutoAnswer.setChecked(b0 == 1); break;
            case 0x0818: swLowLatency.setChecked(b0 == 1); break;
            case 0x0401: if (f.type() != 3 && b0 >= 0) swOnHead.setChecked(b0 == 1); break;
            case 0x0406: if (f.type() != 3 && b0 >= 0) swHiRes.setChecked(b0 == 0); break;   // 0 = Hi-Res on
            case 0x0806: if (f.type() != 3 && b0 >= 0 && b0 <= 4) callSeek.setProgress(b0); break;
            case 0x1009: if (f.type() != 3 && b0 >= 0) swBass.setChecked(b0 == 1); break;
            case 0x0804: if (f.type() != 3 && f.payload.length >= 2) swSpeech.setChecked(Gaia.u16(f.payload, 0) == 2); break;
            case 0x0815: if (f.type() != 3 && b0 >= 0) swComfort.setChecked(b0 == 1); break;
            case 0x1607: if (f.type() != 3 && b0 >= 0) swTouch.setChecked(b0 == 0); break;   // inverted
            case 0x1003: if (f.type() == 2 && f.payload.length >= 5) setCurve(f.payload); break;
            case 0x1002:                                                     // 1082 notif = all 5 bands, 1102 resp = one band
                if (f.type() == 1 && f.payload.length >= 5) {
                    setCurve(f.payload);
                } else if (f.type() == 2 && f.payload.length >= 1) {
                    Integer band = pendingBands.poll();
                    if (band != null) setEq(band, f.payload[0]);
                } else if (f.type() == 3) {
                    pendingBands.poll();
                }
                break;
        }
        applying = false;
    }

    private void refreshNoise() {
        int idx = !ancOn ? 2 : adaptiveOn ? 0 : 1;
        noise.select(idx, false);
        customBlock.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        Widgets.setNoise(this, idx);
    }

    private static String ascii(byte[] p) {
        StringBuilder s = new StringBuilder();
        for (byte x : p) { if (x == 0) break; if (x >= 32 && x < 127) s.append((char) x); }
        return s.length() == 0 ? NOT_READ : s.toString();
    }

    // ----------------------------------------------------------- bluetooth

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadDevices();
            else {
                append("Nearby devices permission denied. The app needs it to find the headphones. Grant it in Android Settings > Apps, then tap Rescan.");
                setStatus(0, "Needs the Nearby devices permission. Allow it in Settings > Apps, then tap Rescan.");
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void loadDevices() {
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if (adapter == null) {
            append("This device has no Bluetooth.");
            return;
        }
        if (adapter.getState() == BluetoothAdapter.STATE_TURNING_ON) return;   // a STATE_ON broadcast follows
        if (!adapter.isEnabled()) {
            append("Bluetooth is off. Turn it on; the list refreshes automatically.");
            if (socket == null) setStatus(0, "Bluetooth is off. The list fills in when you turn it on.");
            return;
        }
        // Keep the current choice selected across a re-scan; fall back to the
        // remembered device, then anything that looks like a MOMENTUM.
        BluetoothDevice current = selectedDevice();
        String keep = current != null ? current.getAddress() : prefs.getString("dev", "");
        devices.clear();
        List<String> names = new ArrayList<>();
        int preselect = 0;
        boolean sawMomentum = false;
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            String name = d.getName() == null ? d.getAddress() : d.getName();
            boolean isM4 = name.toUpperCase(Locale.ROOT).contains("MOMENTUM");
            if (isM4) sawMomentum = true;
            if (d.getAddress().equals(keep) || (keep.isEmpty() && isM4)) preselect = devices.size();
            devices.add(d);
            names.add(name);
        }
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        if (!devices.isEmpty()) deviceSpinner.setSelection(preselect);
        showDeviceName();
        if (socket == null && !connecting) {
            if (devices.isEmpty()) setStatus(0, "No paired headphones. Pair them in Android Settings > Bluetooth, then tap Rescan.");
            else if (!sawMomentum) setStatus(0, "No MOMENTUM 4 among the paired devices. Pair it, then tap Rescan.");
            else setStatus(0, "Not connected");
        }

        if (devices.isEmpty()) {
            append("No paired Bluetooth devices. Pair the MOMENTUM 4 in Android Settings > Bluetooth, then tap Rescan.");
        } else if (!sawMomentum) {
            append("No MOMENTUM 4 among the paired devices. Pair it in Android Settings > Bluetooth, then tap Rescan. (Pick another device only if you know it speaks GAIA.)");
        }
    }

    private BluetoothDevice selectedDevice() {
        int i = deviceSpinner.getSelectedItemPosition();
        return (i >= 0 && i < devices.size()) ? devices.get(i) : null;
    }

    @SuppressWarnings("MissingPermission")
    private void connect() {
        if (socket != null) { append("Already connected."); return; }
        BluetoothDevice device = selectedDevice();
        if (device == null) { toast("No device"); return; }
        prefs.edit().putString("dev", device.getAddress()).apply();
        append("Connecting...");
        connecting = true;
        userDisconnect = false;
        setStatus(2, "Connecting...");
        connectBtn.setEnabled(false);
        new Thread(() -> {
            try {
                BluetoothSocket s = device.createRfcommSocketToServiceRecord(Gaia.SERVICE_UUID);
                s.connect();
                socket = s;
                out = s.getOutputStream();
                runOnUiThread(() -> { setConnected(true); registerNotifications(); readAllConfig(); });
                readLoop(s.getInputStream());
            } catch (IOException e) {
                append("Connect failed: " + e.getMessage()
                        + "\nTry: force stop Smart Control Plus; make sure the MOMENTUM 4 is connected to this device;"
                        + " if it is paired to two other devices (multipoint), disconnect one. Then Connect again.");
                closeQuietly();
                runOnUiThread(() -> {
                    setConnected(false);
                    setStatus(0, "Couldn't reach the headphones. They may be off or held by Smart Control (force-stop it).");
                });
            }
        }).start();
    }

    private void readLoop(InputStream in) {
        Gaia.Parser parser = new Gaia.Parser();
        byte[] buf = new byte[1024];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                for (Gaia.Frame f : parser.feed(buf, n)) {
                    append("<- " + Gaia.describe(f));
                    runOnUiThread(() -> onFrame(f));
                }
            }
        } catch (IOException ignored) {
        }
        append("Disconnected.");
        closeQuietly();
        runOnUiThread(() -> {
            setConnected(false);
            setStatus(0, userDisconnect ? "Not connected" : "Connection lost. Tap Connect.");
        });
    }

    private void setConnected(boolean on) {
        live = on ? this : null;
        if (!on) eqSync.disconnected();
        connecting = false;
        if (on) setStatus(1, "Connected");
        else setStatus(0, "Not connected");                      // callers set a more specific text after
        connectBtn.setEnabled(true);
        connectBtn.setText(on ? "Disconnect" : "Connect");
        if (on) styleOutline(connectBtn); else styleFilled(connectBtn);
        setGroupEnabled(on);
    }

    private void send(byte[] frame) {
        String blocked = Gaia.blockedReason(frame);
        if (blocked != null) {
            append("BLOCKED (" + blocked + "): " + Gaia.hex(frame));
            toast("Blocked for safety: this command can reset or unpair the headphones.");
            return;
        }
        OutputStream o = out;
        if (o == null) { toast("Connect first"); return; }
        sender.execute(() -> {
            try { o.write(frame); o.flush(); append("-> " + Gaia.hex(frame)); }
            catch (IOException e) { append("Send failed: " + e.getMessage()); }
        });
    }

    private void disconnect() {
        userDisconnect = true;
        closeQuietly();
        append("Disconnected by user.");
        runOnUiThread(() -> { setConnected(false); setStatus(0, "Not connected"); });
    }

    private void closeQuietly() {
        BluetoothSocket s = socket;
        socket = null; out = null;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------- actions

    private void readAllConfig() {
        if (out == null) { toast("Connect first"); return; }
        int[] gets = {0x1A05, 0x1A01, 0x1A03, 0x080D, 0x080B, 0x0818, 0x0815, 0x1607, 0x0806, 0x0401, 0x0406,
                0x1009, 0x0804, 0x0603, 0x1206, 0x1201, 0x1200};
        for (int x : gets) send(g(x));
        send(g(0x1003, 0));                                          // all 5 EQ gains at once
    }

    /** Ask for change notifications (noise 0x0d, EQ 0x08, audio/device 0x04), as the official app does. */
    private void registerNotifications() {
        for (int feat : new int[]{0x0d, 0x08, 0x04}) send(g(0x0007, feat));
    }

    private void setTimer0() {
        if (out == null) { toast("Connect first"); return; }
        try {
            int secs = Integer.parseInt(secondsField.getText().toString().trim());
            if (secs < 0 || secs > 0xFFFF) { toast("0 to 65535"); return; }
            send(Gaia.build(Gaia.SENNHEISER, 0x0600, (byte) 0, (byte) (secs >> 8), (byte) secs));
            send(g(0x0601, 0));
        } catch (NumberFormatException e) { toast("Enter seconds"); }
    }

    private void sendRaw() {
        try { send(Gaia.parseHex(hexField.getText().toString())); }
        catch (IllegalArgumentException e) { toast("Bad hex: " + e.getMessage()); }
    }

    private void powerOffNow() {
        if (out == null) { toast("Connect first"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Power off the headphones?")
                .setMessage("They switch off fully. Only the button on the headphones turns them back on.")
                .setPositiveButton("Power off", (d, w) -> send(Gaia.powerOff()))
                .setNegativeButton("Cancel", null)
                .show()
                .getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(T.danger);
    }

    private void toggleGuard() {
        if (AutoOffService.running) { AutoOffService.stop(this); append("Idle guard stopped."); }
        else startGuard();
        refreshGuardButton();
    }

    private void startGuard() {
        BluetoothDevice device = selectedDevice();
        if (device == null) { toast("Pick the headphones first"); return; }
        int minutes;
        try { minutes = Integer.parseInt(idleMinutes.getText().toString().trim()); }
        catch (NumberFormatException e) { toast("Enter minutes"); return; }
        if (minutes < 1) { toast("At least 1 minute"); return; }
        prefs.edit().putInt("minutes", minutes).putString("dev", device.getAddress()).apply();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        closeQuietly();
        runOnUiThread(() -> setConnected(false));
        AutoOffService.start(this, device.getAddress(), minutes * 60_000L);
        append("Idle guard started (" + minutes + " min).");
        refreshGuardButton();
    }

    /** The service flips `running` asynchronously, so re-check shortly after a start/stop. */
    private void refreshGuardButton() {
        applyGuardState();
        if (guardButton != null) {
            guardButton.postDelayed(this::applyGuardState, 600);
            guardButton.postDelayed(this::applyGuardState, 2000);
        }
    }

    private void applyGuardState() {
        if (guardButton == null) return;
        boolean on = AutoOffService.running;
        int min = prefs.getInt("minutes", 15);
        guardButton.setText(on ? "Stop guard" : "Start guard");
        if (on) styleOutline(guardButton); else styleFilled(guardButton);
        guardBig.setText(on ? min + " min" : "Off");
        guardStatus.setText(on ? "Armed. Off after " + min + " min with nothing playing and no call." : "The guard is not running.");
        guardDot.setText(on ? "●" : "");
    }

    private void copyLog() {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", log.getText()));
        toast("Log copied");
    }

    private void append(String line) {
        android.util.Log.i("M4GAIA", line);   // readable over USB: adb logcat -s M4GAIA
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        runOnUiThread(() -> { log.append(stamp + " " + line + "\n"); logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN)); });
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() { if (live == this) live = null; closeQuietly(); sender.shutdown(); super.onDestroy(); }
}
