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
    // palette
    private static final int BG = 0xFFF2F3F5, CARD = 0xFFFFFFFF, TEXT = 0xFF1A1C1E, TEXT2 = 0xFF5F6368,
            DIVIDER = 0xFFE3E5E8, ACCENT = 0xFF0E7C66, ACCENT_D = 0xFF0A5E4D, DANGER = 0xFFC0392B,
            DANGER_D = 0xFF9B2D22, SWITCH_OFF = 0xFFB0B4B8;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final List<View> gated = new ArrayList<>();            // disabled when not connected
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private Spinner deviceSpinner;
    private TextView statusPill;
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
    private static final String[] PRESET_NAMES = {"Neutral", "Rock", "Pop", "Dance", "Hip-Hop", "Classical", "Movie", "Jazz"};
    private static final int[][] PRESETS = {                     // dB x 10 per band
            {0, 0, 0, 0, 0}, {0, 20, 25, 15, -20}, {0, -25, 0, 25, 0}, {35, 20, -15, 15, 30},
            {30, 15, -15, 0, -15}, {-20, -15, 0, 35, 40}, {0, 0, 20, 20, -20}, {-32, 0, 22, 22, 0}};
    private final Button[] presetBtns = new Button[PRESETS.length];
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(header());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(12));
        content.addView(deviceCard());
        content.addView(noiseCard());
        content.addView(eqCard());
        content.addView(listeningCard());
        content.addView(guardCard());
        content.addView(powerCard());
        content.addView(advancedCard());
        content.addView(logCard());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

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

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackgroundColor(CARD);
        h.setElevation(dp(3));
        h.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout titleRow = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText("MOMENTUM 4");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        statusPill = new TextView(this);
        statusPill.setTextSize(12);
        statusPill.setPadding(dp(12), dp(6), dp(12), dp(6));
        titleRow.addView(statusPill);
        h.addView(titleRow);

        LinearLayout devRow = new LinearLayout(this);
        devRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dr = new LinearLayout.LayoutParams(-1, -2);
        dr.topMargin = dp(8);
        deviceSpinner = new Spinner(this);
        devRow.addView(deviceSpinner, new LinearLayout.LayoutParams(0, -2, 1f));
        devRow.addView(textBtn("Rescan", v -> rescan()));
        h.addView(devRow, dr);

        LinearLayout btns = new LinearLayout(this);
        LinearLayout.LayoutParams br = new LinearLayout.LayoutParams(-1, -2);
        br.topMargin = dp(8);
        Button connect = primaryBtn("Connect", v -> connect());
        Button disc = outlineBtn("Disconnect", v -> disconnect());
        btns.addView(connect, new LinearLayout.LayoutParams(0, -2, 1f));
        btns.addView(space(dp(8)));
        btns.addView(disc, new LinearLayout.LayoutParams(0, -2, 1f));
        h.addView(btns, br);
        return h;
    }

    private View deviceCard() {
        LinearLayout c = card("DEVICE");
        LinearLayout titleRow = (LinearLayout) ((ViewGroup) c.getParent()).getChildAt(0);
        Button refresh = textBtn("Refresh", v -> readAllConfig());
        gate(refresh);
        titleRow.addView(refresh);
        vBattery = valueRow(c, "Battery");
        vModel = valueRow(c, "Model");
        vFirmware = valueRow(c, "Firmware");
        vHardware = valueRow(c, "Hardware");
        return cardWrap(c);
    }

    private View noiseCard() {
        LinearLayout c = card("NOISE CONTROL");
        // Same frames, same order as Smart Control Plus (docs/config-spec.md, capture 2).
        noise = new Segmented(new String[]{"Adaptive", "Custom", "Off"}, idx -> {
            if (idx == 0) { send(a(0x1A04, 1)); send(b2(0x1A00, 3, 1)); ancOn = true; adaptiveOn = true; }
            else if (idx == 1) {
                send(b2(0x1A00, 3, 0)); send(a(0x1A04, 1)); send(a(0x1A02, transpSeek.getProgress()));
                ancOn = true; adaptiveOn = false;
            } else { send(b2(0x1A00, 3, 0)); send(a(0x1A04, 0)); ancOn = false; adaptiveOn = false; }
            customBlock.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        });
        c.addView(noise.view);

        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        TextView[] tv = new TextView[1];
        transpSeek = slider(b, "ANC  ↔  Transparency", 100, tv,
                p -> p == 0 ? "full ANC" : p == 100 ? "full transparency" : String.valueOf(p),
                p -> { send(a(0x1A02, p)); send(g(0x1A03)); });
        transpValue = tv[0];
        TextView aw = caption("Anti-wind");
        aw.setPadding(0, dp(8), 0, dp(4));
        b.addView(aw);
        // UI order Off/Auto/Max; wire values 0 = off, 1 = Max, 2 = Auto (T17, confirmed on hardware)
        antiWind = new Segmented(new String[]{"Off", "Auto", "Max"}, idx -> send(b2(0x1A00, 1, antiWindSwap(idx))));
        b.addView(antiWind.view);
        customBlock = b;
        customBlock.setVisibility(View.GONE);
        c.addView(customBlock);
        return cardWrap(c);
    }

    /** Labelled SeekBar that sends only on release. tvOut[0] receives the value label. */
    private SeekBar slider(LinearLayout parent, String label, int max, TextView[] tvOut, IntLabel fmt, OnInt onRelease) {
        LinearLayout cap = new LinearLayout(this);
        cap.setPadding(0, dp(4), 0, 0);
        cap.addView(caption(label), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = new TextView(this);
        val.setTextColor(TEXT);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        cap.addView(val);
        parent.addView(cap);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        s.getThumb().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) { val.setText(fmt.f(p)); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) { if (!applying) onRelease.on(sb.getProgress()); }
        });
        val.setText(fmt.f(0));
        gate(s);
        parent.addView(s);
        tvOut[0] = val;
        return s;
    }

    private View eqCard() {
        LinearLayout c = card("EQUALIZER");
        LinearLayout titleRow = (LinearLayout) ((ViewGroup) c.getParent()).getChildAt(0);
        Button flat = textBtn("Flat", v -> {
            for (int i = 0; i < 5; i++) { send(b2(0x1001, i, 0)); setEq(i, 0); }
        });
        gate(flat);
        titleRow.addView(flat);

        // Smart Control Plus presets (app-side; the headset only stores the curve).
        // Values from omarchy-momentum4 presets.json, checked against our own capture 2.
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int idx = i;
            Button b = outlineBtn(PRESET_NAMES[i], v -> applyPreset(idx));
            gate(b);
            presetBtns[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.rightMargin = dp(6);
            chips.addView(b, lp);
        }
        hs.addView(chips);
        c.addView(hs);

        for (int i = 0; i < 5; i++) {
            final int band = i;
            TextView[] tv = new TextView[1];
            // 0..120 = -6.0..+6.0 dB in 0.1 dB steps (as in Smart Control); wire value = dB x 10, signed byte
            eqSeek[i] = slider(c, EQ_BANDS[i], 120, tv, p -> String.format(Locale.ROOT, "%+.1f dB", (p - 60) / 10.0),
                    p -> { send(b2(0x1001, band, p - 60)); eqGain[band] = p - 60; markPreset(); });
            eqValue[i] = tv[0];
            eqSeek[i].setProgress(60);
        }
        swBass = switchRow(c, "Bass boost", on -> toggle(0x1008, on));
        swSpeech = switchRow(c, "Speech clarity", on -> send(b2(0x0803, 0, on ? 2 : 1)));
        gate(swBass); gate(swSpeech);
        return cardWrap(c);
    }

    private void setEq(int band, int gainX10) {
        eqGain[band] = gainX10;
        eqSeek[band].setProgress(Math.max(0, Math.min(120, gainX10 + 60)));
        markPreset();
    }

    private void applyPreset(int idx) {
        for (int b = 0; b < 5; b++) { send(b2(0x1001, b, PRESETS[idx][b])); setEq(b, PRESETS[idx][b]); }
    }

    /** Highlight the preset whose curve matches exactly, like Smart Control does. */
    private void markPreset() {
        for (int i = 0; i < PRESETS.length; i++) {
            if (presetBtns[i] == null) continue;
            boolean m = java.util.Arrays.equals(PRESETS[i], eqGain);
            presetBtns[i].setBackground(rounded(m ? ACCENT : CARD, 10, m ? 0 : DIVIDER, 1));
            presetBtns[i].setTextColor(m ? Color.WHITE : TEXT);
        }
    }

    private View listeningCard() {
        LinearLayout c = card("LISTENING");
        swSmartPause = switchRow(c, "Smart pause", on -> toggle(0x080C, on));
        swAutoAnswer = switchRow(c, "Auto-answer calls", on -> toggle(0x080A, on));
        swOnHead = switchRow(c, "On-head detection", on -> toggle(0x0400, on));
        swLowLatency = switchRow(c, "Low latency", on -> toggle(0x0817, on));
        swComfort = switchRow(c, "Comfort call", on -> toggle(0x0814, on));
        swTouch = switchRow(c, "Touch controls", on -> send(a(0x1606, on ? 0 : 1)));   // inverted: 0 = enabled
        gate(swComfort); gate(swTouch);
        swHiRes = switchRow(c, "Hi-Res 24/96 (restart the headphones after changing)",
                on -> { send(a(0x0405, on ? 0 : 1)); send(g(0x0406)); });   // 0 = Hi-Res on
        gate(swSmartPause); gate(swAutoAnswer); gate(swOnHead); gate(swLowLatency); gate(swHiRes);
        TextView[] tv = new TextView[1];
        callSeek = slider(c, "Transparency during calls", 4, tv, p -> p == 0 ? "off" : p + " / 4",
                p -> { send(a(0x0805, p)); send(g(0x0806)); });
        callValue = tv[0];
        return cardWrap(c);
    }

    private View guardCard() {
        LinearLayout c = card("AUTO POWER-OFF");
        TextView cap = caption("Switches the headphones fully off after they've been idle (no audio, no call) for the set time. They stay usable until then; wake them with the button.");
        cap.setPadding(0, 0, 0, dp(8));
        c.addView(cap);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbl = body("Idle timeout");
        row.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1f));
        idleMinutes = new EditText(this);
        idleMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        idleMinutes.setText(String.valueOf(prefs.getInt("minutes", 15)));
        idleMinutes.setGravity(Gravity.CENTER);
        row.addView(idleMinutes, new LinearLayout.LayoutParams(dp(72), -2));
        row.addView(body("  min"));
        c.addView(row);

        swAutoStart = switchRow(c, "Start automatically when the app opens", on -> prefs.edit().putBoolean("autostart", on).apply());

        guardButton = primaryBtn("START GUARD", v -> toggleGuard());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(48));
        gp.topMargin = dp(8);
        c.addView(guardButton, gp);

        guardStatus = caption("");
        c.addView(guardStatus);
        c.addView(textBtn("Show guard log", v -> append(AutoOffService.getLog())));
        return cardWrap(c);
    }

    private View powerCard() {
        LinearLayout c = card("POWER");
        Button p = dangerBtn("POWER OFF NOW", v -> powerOffNow());
        gate(p);
        c.addView(p, new LinearLayout.LayoutParams(-1, dp(48)));
        c.addView(caption("Fully off, same as the case or a 3-second button hold. Can only be turned back on with the button."));
        return cardWrap(c);
    }

    private View advancedCard() {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);

        inner.addView(caption("Firmware auto-off timer (does not truly switch off on this firmware)"));
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        Button readT = outlineBtn("Read", v -> send(g(0x0601, 0)));
        gate(readT);
        tr.addView(readT);
        secondsField = new EditText(this);
        secondsField.setInputType(InputType.TYPE_CLASS_NUMBER);
        secondsField.setHint("seconds");
        tr.addView(secondsField, new LinearLayout.LayoutParams(0, -2, 1f));
        Button setT = outlineBtn("Set", v -> setTimer0());
        gate(setT);
        tr.addView(setT);
        inner.addView(tr);

        inner.addView(caption("Raw frame (hex)"));
        hexField = new EditText(this);
        hexField.setHint("FF 03 00 00 04 95 06 03");
        hexField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inner.addView(hexField);
        Button sendRaw = outlineBtn("Send raw", v -> sendRaw());
        gate(sendRaw);
        inner.addView(sendRaw);

        return cardWrap(collapsible("ADVANCED", inner, true));
    }

    private View logCard() {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextColor(TEXT);
        log.setTextIsSelectable(true);
        logScroll = new ScrollView(this);
        logScroll.addView(log);
        inner.addView(logScroll, new LinearLayout.LayoutParams(-1, dp(200)));
        inner.addView(textBtn("Copy", v -> copyLog()));
        return cardWrap(collapsible("LOG", inner, true));
    }

    // ------------------------------------------------------------- helpers

    /** A styled card; returns the inner content column. Title row is child 0 of the card. */
    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 16, 0, 0));
        c.setElevation(dp(1));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(sectionHeader(title), new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(titleRow);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        c.addView(body);
        // the returned column is `body`, but callers also need the title row and card;
        // we attach body's parent (card) via getParent(), so return body.
        return body;
    }

    private View cardWrap(View v) {
        // v is a collapsible root (tagged), or a card body whose parent is the card.
        View card = "collapsible".equals(v.getTag()) ? v : (View) v.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView valueRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView l = body(label);
        l.setTextColor(TEXT2);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = new TextView(this);
        val.setText("—");
        val.setTextColor(TEXT);
        val.setTextSize(15);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(val);
        parent.addView(row);
        return val;
    }

    private Switch switchRow(LinearLayout parent, String label, OnBool onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(body(label), new LinearLayout.LayoutParams(0, -2, 1f));
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
        c.setBackground(rounded(CARD, 16, 0, 0));
        c.setElevation(dp(1));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = sectionHeader(title);
        headerRow.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chev = new TextView(this);
        chev.setTextColor(TEXT2);
        chev.setText(startCollapsed ? "▸" : "▾");
        headerRow.addView(chev);
        inner.setVisibility(startCollapsed ? View.GONE : View.VISIBLE);
        headerRow.setOnClickListener(v -> {
            boolean show = inner.getVisibility() == View.GONE;
            inner.setVisibility(show ? View.VISIBLE : View.GONE);
            chev.setText(show ? "▾" : "▸");
        });
        c.addView(headerRow);
        c.addView(inner);
        return c;
    }

    private TextView sectionHeader(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase(Locale.ROOT));
        t.setTextColor(TEXT2);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(15);
        return t;
    }

    private TextView caption(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT2);
        t.setTextSize(12);
        return t;
    }

    private Button primaryBtn(String s, View.OnClickListener l) { return styledBtn(s, ACCENT, ACCENT_D, Color.WHITE, l); }
    private Button dangerBtn(String s, View.OnClickListener l) { return styledBtn(s, DANGER, DANGER_D, Color.WHITE, l); }

    private Button outlineBtn(String s, View.OnClickListener l) {
        Button b = baseBtn(s, l);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(0x11000000, 10, DIVIDER, 1));
        sld.addState(new int[]{}, rounded(CARD, 10, DIVIDER, 1));
        b.setBackground(sld);
        b.setTextColor(TEXT);
        return b;
    }

    private Button textBtn(String s, View.OnClickListener l) {
        Button b = new Button(this, null);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(ACCENT);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(4), dp(4), dp(4), dp(4));
        b.setOnClickListener(l);
        return b;
    }

    private Button styledBtn(String s, int fill, int pressed, int textColor, View.OnClickListener l) {
        Button b = baseBtn(s, l);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed, 10, 0, 0));
        sld.addState(new int[]{}, rounded(fill, 10, 0, 0));
        b.setBackground(sld);
        b.setTextColor(textColor);
        return b;
    }

    private Button baseBtn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
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
        ColorStateList csl = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT, SWITCH_OFF});
        if (Build.VERSION.SDK_INT >= 23) {
            s.setThumbTintList(csl);
            s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{0x660E7C66, 0x33000000}));
        }
    }

    private View space(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    private void gate(View v) { gated.add(v); }

    private void setGroupEnabled(boolean on) {
        for (View v : gated) { v.setEnabled(on); v.setAlpha(on ? 1f : 0.4f); }
    }

    /** Segmented 3-way control built from buttons in a rounded container. */
    private final class Segmented {
        final LinearLayout view;
        final Button[] buttons;
        final OnInt onSelect;
        int selected = -1;

        Segmented(String[] opts, OnInt onSelect) {
            this.onSelect = onSelect;
            view = new LinearLayout(MainActivity.this);
            view.setBackground(rounded(0xFFEDEFF1, 10, DIVIDER, 1));
            view.setPadding(dp(3), dp(3), dp(3), dp(3));
            buttons = new Button[opts.length];
            for (int i = 0; i < opts.length; i++) {
                final int idx = i;
                Button b = new Button(MainActivity.this);
                b.setText(opts[i]);
                b.setAllCaps(false);
                if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
                b.setBackgroundColor(Color.TRANSPARENT);
                b.setTextColor(TEXT);
                b.setOnClickListener(v -> select(idx, true));
                gate(b);
                buttons[i] = b;
                view.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1f));
            }
        }

        void select(int i, boolean fire) {
            selected = i;
            for (int j = 0; j < buttons.length; j++) {
                boolean sel = j == i;
                buttons[j].setBackground(rounded(sel ? ACCENT : Color.TRANSPARENT, 8, 0, 0));
                buttons[j].setTextColor(sel ? Color.WHITE : TEXT);
                buttons[j].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
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
            case 0x0603: if (b0 >= 0) vBattery.setText(b0 + " %"); break;
            case 0x1206: vModel.setText(ascii(f.payload)); break;
            case 0x1201: if (f.payload.length >= 6)                          // firmware = 3 x u16 BE
                    vFirmware.setText(Gaia.u16(f.payload, 0) + "." + Gaia.u16(f.payload, 2) + "." + Gaia.u16(f.payload, 4));
                break;
            case 0x1200: if (f.payload.length >= 2) vHardware.setText(String.valueOf(Gaia.u16(f.payload, 0)));
                else if (b0 >= 0) vHardware.setText(String.valueOf(b0));
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
            case 0x1003: if (f.type() == 2 && f.payload.length >= 5) for (int i = 0; i < 5; i++) setEq(i, f.payload[i]); break;
            case 0x1002:                                                     // 1082 notif = all 5 bands, 1102 resp = one band
                if (f.type() == 1 && f.payload.length >= 5) {
                    for (int i = 0; i < 5; i++) setEq(i, f.payload[i]);
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
    }

    private static String ascii(byte[] p) {
        StringBuilder s = new StringBuilder();
        for (byte x : p) { if (x == 0) break; if (x >= 32 && x < 127) s.append((char) x); }
        return s.length() == 0 ? "—" : s.toString();
    }

    // ----------------------------------------------------------- bluetooth

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadDevices();
            else append("Nearby devices permission denied. The app needs it to find the headphones. Grant it in Android Settings > Apps, then tap Rescan.");
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
                runOnUiThread(() -> setConnected(false));
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
        runOnUiThread(() -> setConnected(false));
    }

    private void setConnected(boolean on) {
        statusPill.setText(on ? "Connected" : "Disconnected");
        statusPill.setTextColor(on ? Color.WHITE : TEXT2);
        statusPill.setBackground(rounded(on ? ACCENT : DIVIDER, 12, 0, 0));
        setGroupEnabled(on);
    }

    private void send(byte[] frame) {
        String blocked = Gaia.blockedReason(frame);
        if (blocked != null) { append("BLOCKED (" + blocked + "): " + Gaia.hex(frame)); return; }
        OutputStream o = out;
        if (o == null) { toast("Connect first"); return; }
        sender.execute(() -> {
            try { o.write(frame); o.flush(); append("-> " + Gaia.hex(frame)); }
            catch (IOException e) { append("Send failed: " + e.getMessage()); }
        });
    }

    private void disconnect() { closeQuietly(); append("Disconnected by user."); runOnUiThread(() -> setConnected(false)); }

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
                .setMessage("Sends 0605. They switch off fully and can only be turned back on with the button.")
                .setPositiveButton("Power off", (d, w) -> send(Gaia.powerOff()))
                .setNegativeButton("Cancel", null)
                .show();
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
        guardButton.setText(on ? "STOP GUARD" : "START GUARD");
        guardStatus.setText(on ? "Guard running: off after " + prefs.getInt("minutes", 15) + " min idle." : "Guard stopped.");
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
    protected void onDestroy() { closeQuietly(); sender.shutdown(); super.onDestroy(); }
}
