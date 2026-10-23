package be.ryane.gaiatest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
    private Segmented noise;
    private View transpBlock;
    private SeekBar transpSeek;
    private TextView transpValue;
    private Switch swSmartPause, swAutoAnswer, swLowLatency, swSidetone, swAutoStart;
    private Button guardButton;
    private EditText idleMinutes, secondsField, hexField;

    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    private boolean ancOn, transpOn, applying;
    private SharedPreferences prefs;

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
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        } else {
            loadDevices();
        }
        swAutoStart.setChecked(prefs.getBoolean("autostart", false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshGuardButton();
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

        deviceSpinner = new Spinner(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8);
        h.addView(deviceSpinner, sp);

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
        noise = new Segmented(new String[]{"ANC", "Transparency", "Off"}, idx -> {
            if (idx == 0) { send(a(0x1804, 0)); send(a(0x1A04, 1)); }
            else if (idx == 1) { send(a(0x1A04, 0)); send(a(0x1804, 1)); }
            else { send(a(0x1A04, 0)); send(a(0x1804, 0)); }
            transpBlock.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        });
        c.addView(noise.view);

        transpBlock = transparencyBlock();
        transpBlock.setVisibility(View.GONE);
        c.addView(transpBlock);
        return cardWrap(c);
    }

    private View transparencyBlock() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);

        LinearLayout cap = new LinearLayout(this);
        TextView t = caption("Transparency level");
        cap.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        transpValue = new TextView(this);
        transpValue.setTextColor(TEXT);
        transpValue.setTypeface(Typeface.DEFAULT_BOLD);
        cap.addView(transpValue);
        b.addView(cap);

        transpSeek = new SeekBar(this);
        transpSeek.setMax(100);
        transpSeek.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        transpSeek.getThumb().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        transpSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { transpValue.setText(String.valueOf(p)); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {     // debounce: send only on release
                if (!applying) { send(a(0x1A02, s.getProgress())); send(g(0x1A03)); }
            }
        });
        gate(transpSeek);
        b.addView(transpSeek);
        return b;
    }

    private View listeningCard() {
        LinearLayout c = card("LISTENING");
        swSmartPause = switchRow(c, "Smart pause", on -> toggle(0x080C, on));
        swAutoAnswer = switchRow(c, "Auto-answer calls", on -> toggle(0x080A, on));
        swLowLatency = switchRow(c, "Low latency", on -> toggle(0x0817, on));
        swSidetone = switchRow(c, "Sidetone", on -> toggle(0x0805, on));
        gate(swSmartPause); gate(swAutoAnswer); gate(swLowLatency); gate(swSidetone);
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

    // ----------------------------------------------------- frame shorthands

    private static byte[] a(int cmd, int v) { return Gaia.build(Gaia.SENNHEISER, cmd, (byte) v); }
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
            case 0x1A05: ancOn = b0 == 1; refreshNoise(); break;
            case 0x1805: transpOn = b0 == 1; refreshNoise(); break;
            case 0x1A03: if (b0 >= 0) { transpSeek.setProgress(b0); transpValue.setText(String.valueOf(b0)); } break;
            case 0x080D: swSmartPause.setChecked(b0 == 1); break;
            case 0x080B: swAutoAnswer.setChecked(b0 == 1); break;
            case 0x0818: swLowLatency.setChecked(b0 == 1); break;
            case 0x0806: swSidetone.setChecked(b0 == 1); break;
        }
        applying = false;
    }

    private void refreshNoise() {
        int idx = transpOn ? 1 : ancOn ? 0 : 2;
        noise.select(idx, false);
        transpBlock.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
    }

    private static String ascii(byte[] p) {
        StringBuilder s = new StringBuilder();
        for (byte x : p) { if (x == 0) break; if (x >= 32 && x < 127) s.append((char) x); }
        return s.length() == 0 ? "—" : s.toString();
    }

    // ----------------------------------------------------------- bluetooth

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadDevices();
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
        String remembered = prefs.getString("dev", "");
        int preselect = 0;
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            String name = d.getName() == null ? d.getAddress() : d.getName();
            if (d.getAddress().equals(remembered) || (remembered.isEmpty() && name.toUpperCase(Locale.ROOT).contains("MOMENTUM")))
                preselect = devices.size();
            devices.add(d);
            names.add(name);
        }
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        if (!devices.isEmpty()) deviceSpinner.setSelection(preselect);
        if (prefs.getBoolean("autostart", false) && selectedDevice() != null) startGuard();
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
                runOnUiThread(() -> { setConnected(true); readAllConfig(); });
                readLoop(s.getInputStream());
            } catch (IOException e) {
                append("Connect failed: " + e.getMessage() + "\nForce stop Smart Control Plus and retry.");
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
        int[] gets = {0x1A05, 0x1805, 0x1A03, 0x080D, 0x080B, 0x0818, 0x0806,
                0x0603, 0x1206, 0x1201, 0x1200};
        for (int x : gets) send(g(x));
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
        guardStatus.setText("Guard running: off after " + minutes + " min idle.");
        append("Idle guard started (" + minutes + " min).");
        refreshGuardButton();
    }

    private void refreshGuardButton() {
        if (guardButton != null) guardButton.setText(AutoOffService.running ? "STOP GUARD" : "START GUARD");
    }

    private void copyLog() {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", log.getText()));
        toast("Log copied");
    }

    private void append(String line) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        runOnUiThread(() -> { log.append(stamp + " " + line + "\n"); logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN)); });
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() { closeQuietly(); sender.shutdown(); super.onDestroy(); }
}
