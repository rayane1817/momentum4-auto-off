package be.ryane.gaiatest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    /** Design tokens for the current light/dark configuration (res/values(-night)/colors.xml). */
    private Tok T;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final List<View> gated = new ArrayList<>();            // disabled when not connected
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private int deviceIndex = -1;                                    // index into devices, -1 = none
    private TextView deviceTitle;
    private TextView statusLine, statusDetail, noiseHint, guardUnit, heroName;
    private TextView tileSound, tileListen, tileDevice, tileGuardSub;
    private DotNumerals batteryDots, guardDots;
    private EqFaders eqFaders;
    private Button presetPill;
    private Switch guardSwitch;
    private ImageView guardIcon;
    private boolean guardApplying;
    private long guardPendingUntil;          // after a tap on the guard switch: let the service catch up
    private Button connectBtn;
    private EqCurve eqCurve;
    private boolean userDisconnect, connecting, confirmedOther;
    private TextView guardTarget;
    private TextView vBattery, vModel, vFirmware, vHardware, guardStatus, log;
    private ScrollView logScroll;
    private RoundChoice noise;
    private Segmented antiWind;
    private View customBlock;
    private SeekBar transpSeek, callSeek;
    private Segmented promptSeg;
    private int batteryPct = -1;
    private boolean charging;
    private LinearLayout peersBox;
    private int ownPeer = -1;
    private final java.util.TreeMap<Integer, String[]> peers = new java.util.TreeMap<>();   // index -> {name, connected}; names never logged
    private TextView transpValue, callValue;
    private Switch swSmartPause, swAutoAnswer, swLowLatency, swHiRes, swOnHead, swAutoStart;
    private Switch swBass, swSpeech, swComfort, swTouch;
    private final int[] eqGain = new int[5];                     // current curve, dB x 10
    private final EqSync eqSync = new EqSync();                  // eqGain mirrors the headset?
    /** The app instance holding the GAIA link, used by widgets/tiles to send through it. */
    static volatile MainActivity live;
    private Button guardButton;
    private EditText idleMinutes, secondsField, hexField;

    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    private volatile boolean destroyed;
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

    // ---- Auto-connect (v0.14.2): open the GAIA link as soon as the headset is on the phone, like
    // Smart Control does. Foreground only (onStart..onStop): no service, no battery exemption.
    private final AutoConnect auto = new AutoConnect();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> aclUp = new HashSet<>();   // addresses with an ACL link seen while started
    private BluetoothProfile a2dp, headsetProfile;
    private boolean started;
    private long linkSince;                              // elapsedRealtime when the current link came up
    private final Runnable autoRun = this::autoAttempt;

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (!started) { closeProxy(profile, proxy); return; }
            if (profile == BluetoothProfile.A2DP) a2dp = proxy; else headsetProfile = proxy;
            autoCheck();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) a2dp = null; else headsetProfile = null;
        }
    };

    // The selected headset joining or leaving the phone (ACL, A2DP, headset profile).
    private final BroadcastReceiver linkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) { onHeadsetEvent(intent); }
    };

    // T53: the guard sends 0605 and stops itself while the app is open; redraw the page and tile then.
    private final BroadcastReceiver guardReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) { applyGuardState(); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("m4", MODE_PRIVATE);
        T = new Tok(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(T.bg);
        // Device first: it creates the device picker that the Home status row reads.
        pages[DEVICE] = pageScroll(devicePage());
        pages[HOME] = pageScroll(homePage());
        pages[SOUND] = pageScroll(soundPage());
        pages[LISTENING] = pageScroll(listeningPage());
        pages[GUARD] = pageScroll(guardPage());
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        for (ScrollView pg : pages) frame.addView(pg, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        padForSystemBars(root);
        showPage(HOME);

        setConnected(false);
        swAutoStart.setChecked(prefs.getBoolean("autostart", false));
        refreshGuardSummary();
        updateListenTile();
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
        IntentFilter lf = new IntentFilter();
        lf.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        lf.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        lf.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        lf.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(linkReceiver, lf);
        started = true;
        auto.foreground();
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
            adapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);
        }
        IntentFilter gf = new IntentFilter(AutoOffService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(guardReceiver, gf, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(guardReceiver, gf);
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(btReceiver); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(guardReceiver); } catch (IllegalArgumentException ignored) {}
        try { unregisterReceiver(linkReceiver); } catch (IllegalArgumentException ignored) {}
        started = false;
        main.removeCallbacks(autoRun);
        aclUp.clear();                                   // we miss ACL changes while stopped
        closeProxy(BluetoothProfile.A2DP, a2dp);
        closeProxy(BluetoothProfile.HEADSET, headsetProfile);
        a2dp = null; headsetProfile = null;
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
    // Design v4 (docs/design-spec-v4.md): Nothing X layout + Poweramp faders. Home + four pages
    // (Equaliser, Listening, Device, Auto power-off). Views are built once and only moved between
    // pages, so onFrame() keeps every control current while its page is hidden. Every control sends
    // the frames it sent in v0.13.0; the EQ faders send the same 1001 frame, now also from a sweep.

    /**
     * targetSdk 35 draws edge to edge on Android 15+: pad the root by the system bars, the cutout and
     * the keyboard so nothing sits under the status bar or behind the IME.
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

    // ---- pages

    private static final int HOME = 0, SOUND = 1, LISTENING = 2, DEVICE = 3, GUARD = 4;
    private final ScrollView[] pages = new ScrollView[5];
    private int page = HOME;

    private ScrollView pageScroll(View content) {
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(T.bg);
        s.setFillViewport(true);
        s.addView(content);
        return s;
    }

    private void showPage(int p) {
        page = p;
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == p ? View.VISIBLE : View.GONE);
        pages[p].scrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (page != HOME) showPage(HOME);
        else super.onBackPressed();
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(4), dp(12), dp(32));
        return c;
    }

    /** Line icon from res/drawable, tinted (design v4 section 8). */
    private ImageView icon(int res, int color, int sizeDp) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** 48 dp touch target around a 24 dp icon. */
    private ImageView iconButton(int res, String spoken, View.OnClickListener l) {
        ImageView v = icon(res, T.ink, 24);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        v.setContentDescription(spoken);
        v.setBackground(pressable(T.transparent, 24));
        v.setClickable(true);
        v.setOnClickListener(l);
        return v;
    }

    /** Sub-page header (v4 section 7): back arrow, optional action, large serif title, then the hint. */
    private LinearLayout subPage(String title, View action, boolean hint) {
        LinearLayout c = column();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(iconButton(R.drawable.ic_back, "Back", v -> showPage(HOME)));
        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (action != null) row.addView(action);
        c.addView(row);
        TextView t = text(title, 32, Tok.SERIF, T.ink);
        t.setPadding(dp(4), dp(4), 0, dp(8));
        c.addView(t);
        if (hint) {
            TextView h = caption("Not connected. Values are from the headphones once you connect.");
            h.setPadding(dp(4), 0, 0, dp(8));
            pageHints.add(h);
            c.addView(h);
        }
        return c;
    }

    private final List<TextView> pageHints = new ArrayList<>();
    private final List<View> dimmed = new ArrayList<>();          // alpha only when disconnected, still tappable

    // ---- grouped rows (v4 section 7)

    /** Small grey label, then a group whose rows are separate surface rects 2 dp apart. Call shapeGroup after filling. */
    private LinearLayout group(LinearLayout parent, String label) {
        if (label != null) {
            TextView l = label(label);
            l.setPadding(dp(4), dp(20), 0, dp(8));
            parent.addView(l);
        }
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        parent.addView(g);
        return g;
    }

    /** Row: icon, title (+ subtitle). Returns the row; the caller adds a value, switch or chevron at the end. */
    private LinearLayout row(LinearLayout g, int iconRes, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(64));
        r.setPadding(dp(16), dp(10), dp(12), dp(10));
        if (iconRes != 0) {
            ImageView i = icon(iconRes, T.ink2, 18);
            ((LinearLayout.LayoutParams) i.getLayoutParams()).rightMargin = dp(14);
            r.addView(i);
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, Tok.REGULAR, T.ink));
        if (sub != null) texts.addView(caption(sub));
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        g.addView(r);
        return r;
    }

    private ImageView chevron() { return icon(R.drawable.ic_chevron, T.ink2, 18); }

    private LinearLayout navRow(LinearLayout g, int iconRes, String title, String sub, View.OnClickListener l) {
        LinearLayout r = row(g, iconRes, title, sub);
        r.addView(chevron());
        r.setClickable(true);
        r.setOnClickListener(l);
        return r;
    }

    private Switch switchRowG(LinearLayout g, int iconRes, String title, String sub, OnBool onChange) {
        LinearLayout r = row(g, iconRes, title, sub);
        Switch s = new Switch(this);
        tintSwitch(s);
        s.setContentDescription(title);
        s.setOnCheckedChangeListener((b, checked) -> { if (!applying) onChange.on(checked); updateListenTile(); });
        r.addView(s);
        return s;
    }

    private TextView valueRowG(LinearLayout g, int iconRes, String title) {
        LinearLayout r = row(g, iconRes, title, null);
        TextView v = text(NOT_READ, 15, Tok.MONO, T.ink2);
        v.setFontFeatureSettings(Tok.TABULAR);
        r.addView(v);
        return v;
    }

    /** Outer corners 18 dp, inner 6 dp, 2 dp gaps; clickable rows get a pressed state. */
    private void shapeGroup(LinearLayout g) {
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = g.getChildAt(i);
            float tr = i == 0 ? 18 : 6, br = i == n - 1 ? 18 : 6;
            float[] radii = {dp(tr), dp(tr), dp(tr), dp(tr), dp(br), dp(br), dp(br), dp(br)};
            GradientDrawable base = new GradientDrawable();
            base.setColor(T.surface); base.setCornerRadii(radii);
            if (v.isClickable()) {
                GradientDrawable pressed = new GradientDrawable();
                pressed.setColor(Tok.mix(T.surface, T.ink, 0.08f)); pressed.setCornerRadii(radii);
                StateListDrawable sld = new StateListDrawable();
                sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
                sld.addState(new int[]{}, base);
                v.setBackground(sld);
            } else {
                v.setBackground(base);
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            if (lp == null) lp = new LinearLayout.LayoutParams(-1, -2);
            lp.width = -1;
            lp.topMargin = i == 0 ? 0 : dp(2);
            v.setLayoutParams(lp);
        }
    }

    private StateListDrawable pressable(int fill, float radiusDp) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.mix(fill == T.transparent ? T.bg : fill, T.ink, 0.10f), radiusDp, 0, 0));
        sld.addState(new int[]{}, rounded(fill, radiusDp, 0, 0));
        return sld;
    }

    private LinearLayout panel() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(T.surface, 18, 0, 0));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }

    // ---- Home (v4 section 5)

    private View homePage() {
        LinearLayout c = column();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END);
        top.addView(iconButton(R.drawable.ic_cog, "Settings", v -> showPage(DEVICE)));
        c.addView(top);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        batteryDots = new DotNumerals(this, T, 7, 2);
        batteryDots.set("--", false, "Battery not read");
        hero.addView(batteryDots);
        heroName = text("MOMENTUM 4", 26, Tok.SERIF, T.ink);
        heroName.setPadding(0, dp(14), 0, dp(2));
        hero.addView(heroName);
        statusLine = text("", 13, Tok.REGULAR, T.ink2);
        statusLine.setGravity(Gravity.CENTER);
        hero.addView(statusLine);
        statusDetail = caption("");
        statusDetail.setGravity(Gravity.CENTER);
        statusDetail.setVisibility(View.GONE);
        hero.addView(statusDetail);
        connectBtn = baseBtn("Connect", v -> { if (socket != null) disconnect(); else connect(); });
        connectBtn.setTextSize(14);
        connectBtn.setPadding(dp(22), 0, dp(22), 0);
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, dp(40));
        cbp.topMargin = dp(12);
        hero.addView(connectBtn, cbp);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(24);
        c.addView(hero, hp);

        // Noise control card: 3 round buttons. Same frames, same order as Smart Control Plus (docs/config-spec.md, capture 2).
        LinearLayout nc = panel();
        nc.addView(text("Noise control", 15, Tok.MEDIUM, T.ink));
        noiseHint = caption("Connect to change settings.");
        noiseHint.setPadding(0, dp(2), 0, 0);
        nc.addView(noiseHint);
        noise = new RoundChoice(new String[]{"Adaptive", "Custom", "Off"},
                new int[]{R.drawable.ic_adaptive, R.drawable.ic_custom, R.drawable.ic_off}, idx -> {
            if (idx == 0) { send(a(0x1A04, 1)); send(b2(0x1A00, 3, 1)); ancOn = true; adaptiveOn = true; }
            else if (idx == 1) {
                send(b2(0x1A00, 3, 0)); send(a(0x1A04, 1)); send(a(0x1A02, transpSeek.getProgress()));
                ancOn = true; adaptiveOn = false;
            } else { send(b2(0x1A00, 3, 0)); send(a(0x1A04, 0)); ancOn = false; adaptiveOn = false; }
            customBlock.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
            Widgets.setNoise(this, idx);
        });
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(14);
        nc.addView(noise.view, np);
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        View hl = hairline();
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        hlp.topMargin = dp(14);
        b.addView(hl, hlp);
        TextView[] tv = new TextView[1];
        transpSeek = slider(b, "ANC to transparency", 100, tv,
                p -> p == 0 ? "full ANC" : p == 100 ? "full transparency" : String.valueOf(p),
                p -> { send(a(0x1A02, p)); send(g(0x1A03)); });
        transpValue = tv[0];
        LinearLayout ends = new LinearLayout(this);
        ends.addView(caption("ANC"), new LinearLayout.LayoutParams(0, -2, 1f));
        ends.addView(caption("Transparency"));
        b.addView(ends);
        Button full = outlineBtn("Full transparency", v -> {
            send(a(0x1A02, 100)); send(g(0x1A03));
            applying = true; transpSeek.setProgress(100); applying = false;
        });
        gate(full);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(44));
        fp.topMargin = dp(10);
        b.addView(full, fp);
        TextView aw = label("Anti-wind");
        aw.setPadding(0, dp(14), 0, dp(8));
        b.addView(aw);
        // UI order Off/Auto/Max; wire values 0 = off, 1 = Max, 2 = Auto (T17, confirmed on hardware)
        antiWind = new Segmented(new String[]{"Off", "Auto", "Max"}, 38, idx -> send(b2(0x1A00, 1, antiWindSwap(idx))));
        b.addView(antiWind.view);
        customBlock = b;
        customBlock.setVisibility(View.GONE);
        nc.addView(customBlock);
        c.addView(nc);

        // Tiles: 2 columns, 4 dp gaps (v4 5.4)
        tileSound = tileSub("not read");
        tileListen = tileSub("not read");
        tileGuardSub = tileSub("Off");
        tileDevice = tileSub("not read");
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        View eqTile = tile("Equaliser", tileSound, R.drawable.ic_eq, null, v -> showPage(SOUND));
        View lsTile = tile("Listening", tileListen, R.drawable.ic_ear, null, v -> showPage(LISTENING));
        dimmed.add(eqTile); dimmed.add(lsTile);
        guardSwitch = new Switch(this);
        tintSwitch(guardSwitch);
        guardSwitch.setContentDescription("Auto power-off");
        guardSwitch.setOnCheckedChangeListener((sw, on) -> { if (!guardApplying && on != AutoOffService.running) toggleGuard(); });
        View gTile = tile("Auto power-off", tileGuardSub, R.drawable.ic_moon, guardSwitch, v -> showPage(GUARD));
        guardIcon = lastTileIcon;
        View dTile = tile("Device", tileDevice, R.drawable.ic_headphones, null, v -> showPage(DEVICE));
        r1.addView(eqTile, tileLp(true));
        r1.addView(lsTile, tileLp(false));
        r2.addView(gTile, tileLp(true));
        r2.addView(dTile, tileLp(false));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(4);
        c.addView(r1, rp);
        LinearLayout.LayoutParams rp2 = new LinearLayout.LayoutParams(-1, -2);
        rp2.topMargin = dp(4);
        c.addView(r2, rp2);

        // "Power off now" row
        LinearLayout pw = new LinearLayout(this);
        pw.setGravity(Gravity.CENTER_VERTICAL);
        pw.setPadding(dp(16), 0, dp(12), 0);
        pw.setBackground(pressable(T.surface, 18));
        pw.setClickable(true);
        pw.setContentDescription("Power off now");
        pw.setOnClickListener(v -> powerOffNow());
        ImageView pi = icon(R.drawable.ic_power, T.danger, 20);
        ((LinearLayout.LayoutParams) pi.getLayoutParams()).rightMargin = dp(14);
        pw.addView(pi);
        pw.addView(text("Power off now", 15, Tok.MEDIUM, T.danger), new LinearLayout.LayoutParams(0, -2, 1f));
        pw.addView(chevron());
        gate(pw);
        LinearLayout.LayoutParams pwp = new LinearLayout.LayoutParams(-1, dp(60));
        pwp.topMargin = dp(4);
        c.addView(pw, pwp);
        return c;
    }

    private ImageView lastTileIcon;

    private LinearLayout.LayoutParams tileLp(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(150), 1f);
        if (left) p.rightMargin = dp(2); else p.leftMargin = dp(2);
        return p;
    }

    private TextView tileSub(String s) {
        TextView t = caption(s);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    /** Nothing X tile: title top-left, subtitle under it, optional switch bottom-left, tiny icon bottom-right. */
    private View tile(String title, TextView sub, int iconRes, Switch sw, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(14), dp(14), dp(14));
        t.setBackground(pressable(T.surface, 18));
        t.setClickable(true);
        t.setOnClickListener(l);
        t.setContentDescription(title);
        t.addView(text(title, 15, Tok.MEDIUM, T.ink));
        sub.setPadding(0, dp(2), 0, 0);
        t.addView(sub);
        t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        if (sw != null) bottom.addView(sw);
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        lastTileIcon = icon(iconRes, T.ink2, 18);
        bottom.addView(lastTileIcon);
        t.addView(bottom, new LinearLayout.LayoutParams(-1, dp(32)));
        return t;
    }

    /** Equaliser-page tile with a switch (Bass boost, Speech clarity; v4 6.5). */
    private Switch tileSwitch(LinearLayout parent, boolean left, int iconRes, String title, OnBool onChange) {
        TextView sub = tileSub("Off");
        Switch s = new Switch(this);
        tintSwitch(s);
        s.setContentDescription(title);
        s.setOnCheckedChangeListener((b, checked) -> { sub.setText(checked ? "On" : "Off"); if (!applying) onChange.on(checked); });
        View t = tile(title, sub, iconRes, s, v -> { if (s.isEnabled()) s.toggle(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(120), 1f);
        if (left) p.rightMargin = dp(2); else p.leftMargin = dp(2);
        parent.addView(t, p);
        return s;
    }

    /** Device tile subtitle "M4AEBT · hw 4", from whatever has been read. */
    private void updateDeviceTile() {
        if (tileDevice == null) return;
        if (modelText == null && hwText == null) { tileDevice.setText(NOT_READ); return; }
        tileDevice.setText((modelText == null ? "MOMENTUM 4" : modelText) + (hwText == null ? "" : " · hw " + hwText));
    }

    /** Guard tile subtitle and icon: copper "Armed · 15 min" while running, "Sent · 14:05" after it fired. */
    private void refreshGuardSummary() {
        if (tileGuardSub == null) return;
        GuardState g = guardState();
        tileGuardSub.setText(g.tile);
        tileGuardSub.setTextColor(g.on ? T.copper : T.ink2);
        if (guardIcon != null) guardIcon.setImageTintList(ColorStateList.valueOf(g.on ? T.copper : T.ink2));
    }

    private GuardState guardState() {
        long fired = AutoOffService.firedAt;
        return GuardState.of(AutoOffService.running, fired, AutoOffService.lastSendFailed, prefs.getInt("minutes", 15),
                fired > 0 ? new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(fired)) : "");
    }

    @SuppressWarnings("MissingPermission")
    private void showDeviceName() {
        BluetoothDevice d = selectedDevice();
        curName = d == null ? "MOMENTUM 4" : (d.getName() == null ? d.getAddress() : d.getName());
        if (deviceTitle != null) deviceTitle.setText(DevicePick.rowTitle(d == null ? null : nameOf(d), devices.size()));
        renderStatus();
        refreshGuardTarget();
    }

    @SuppressWarnings("MissingPermission")
    private static String nameOf(BluetoothDevice d) { return d.getName() == null ? d.getAddress() : d.getName(); }

    private static boolean isMomentum(BluetoothDevice d) { return DevicePick.looksLikeMomentum(nameOf(d)); }

    /** Which device the guard, widgets and tiles act on (T35: a stale saved TV caused silent failures). */
    private void refreshGuardTarget() {
        if (guardTarget == null) return;
        BluetoothDevice d = selectedDevice();
        if (d == null) { guardTarget.setText("Pick the headphones on the Device page."); guardTarget.setTextColor(T.ink2); return; }
        boolean m4 = isMomentum(d);
        guardTarget.setText(m4 ? "Switches off: " + nameOf(d)
                : nameOf(d) + " is not a MOMENTUM 4. Pick the headphones on the Device page.");
        guardTarget.setTextColor(m4 ? T.ink : T.danger);
    }

    private String curName = "MOMENTUM 4", modelText, hwText, fwText;
    private int statusKind;
    private String statusText = "";

    /** Status line: pilot dot (filled = live, faint = connecting, hollow = not connected) + state + firmware. */
    private void setStatus(int kind, String s) {
        statusKind = kind;
        statusText = s;
        renderStatus();
    }

    private void renderStatus() {
        if (statusLine == null) return;
        String dot = statusKind == 1 ? "●" : statusKind == 2 ? "◌" : "○";   // 3 = waiting: hollow too
        int col = statusKind == 1 ? T.pilot : statusKind == 2 ? Tok.alpha(T.pilot, 0.4f) : T.ruleStrong;
        String state = statusKind == 1 ? "Connected" : statusKind == 2 ? "Connecting..."
                : statusKind == 3 ? statusText : "Not connected";
        if (statusKind == 1 && fwText != null) state += " · fw " + fwText;
        android.text.SpannableString sp = new android.text.SpannableString(dot + "  " + state);
        sp.setSpan(new android.text.style.ForegroundColorSpan(col), 0, 1, 0);
        statusLine.setText(sp);
        heroName.setText(curName);
        boolean plain = statusKind == 3 || statusText.isEmpty() || statusText.equals("Connected") || statusText.equals("Not connected")
                || statusText.equals("Connecting...");
        statusDetail.setText(plain ? "" : statusText);
        statusDetail.setVisibility(plain ? View.GONE : View.VISIBLE);
    }

    // ---- Device page (v4 section 7)

    private View devicePage() {
        Button refresh = textBtn("Refresh", v -> readAllConfig());
        gate(refresh);
        LinearLayout c = subPage("Device", refresh, true);

        LinearLayout hp = group(c, "Headphones");
        // T50: the closed Spinner drew blank on the Flip6 (no selected-item view). A plain row with
        // the chosen name, the caption and a chevron, opening a single-choice list, cannot go blank.
        LinearLayout pick = navRow(hp, R.drawable.ic_headphones, "MOMENTUM 4", "Tap to choose another paired device",
                v -> chooseDevice());
        deviceTitle = (TextView) ((LinearLayout) pick.getChildAt(1)).getChildAt(0);
        navRow(hp, R.drawable.ic_refresh, "Rescan", "Look again for paired headphones", v -> rescan());
        shapeGroup(hp);

        LinearLayout info = group(c, "Info");
        vBattery = valueRowG(info, R.drawable.ic_bolt, "Battery");
        vModel = valueRowG(info, R.drawable.ic_headphones, "Model");
        vFirmware = valueRowG(info, R.drawable.ic_code, "Firmware");
        vHardware = valueRowG(info, R.drawable.ic_info, "Hardware");
        shapeGroup(info);

        LinearLayout pg = group(c, "Paired with the headphones");
        peersBox = new LinearLayout(this);
        peersBox.setOrientation(LinearLayout.VERTICAL);
        pg.addView(peersBox);
        showPeers();
        shapeGroup(pg);

        LinearLayout tools = group(c, "Tools");
        navRow(tools, R.drawable.ic_list, "Guard log", "Shows in the log below", v -> append(AutoOffService.getLog()));
        navRow(tools, R.drawable.ic_report, "Copy report", "For bug reports. No Bluetooth addresses, no other device names.", v -> copyReport());
        shapeGroup(tools);

        View adv = advancedCard();
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(16);
        c.addView(adv, ap);
        c.addView(logCard());
        return c;
    }

    // ---- Auto power-off page (v4 section 7)

    private View guardPage() {
        LinearLayout c = subPage("Auto power-off", null, false);

        LinearLayout big = new LinearLayout(this);
        big.setOrientation(LinearLayout.VERTICAL);
        big.setPadding(dp(4), dp(8), 0, dp(4));
        guardDots = new DotNumerals(this, T, 7, 2);
        guardDots.set("OFF", false, "Guard off");
        big.addView(guardDots);
        guardUnit = caption("minutes idle, then fully off");
        guardUnit.setPadding(0, dp(10), 0, 0);
        big.addView(guardUnit);
        TextView only = caption("The guard only sees this phone. If you also listen on a PC or tablet, it can switch the headphones off.");
        only.setPadding(0, dp(10), 0, 0);
        big.addView(only);
        c.addView(big);

        LinearLayout st = group(c, "Status");
        LinearLayout s1 = row(st, R.drawable.ic_moon, "", null);
        guardStatus = (TextView) ((LinearLayout) s1.getChildAt(1)).getChildAt(0);
        LinearLayout s2 = row(st, R.drawable.ic_headphones, "", null);
        guardTarget = (TextView) ((LinearLayout) s2.getChildAt(1)).getChildAt(0);
        shapeGroup(st);

        LinearLayout set = group(c, "Settings");
        LinearLayout it = row(set, R.drawable.ic_clock, "Idle time", null);
        idleMinutes = field(InputType.TYPE_CLASS_NUMBER, "");
        idleMinutes.setText(String.valueOf(prefs.getInt("minutes", 15)));
        idleMinutes.setGravity(Gravity.CENTER);
        idleMinutes.setFontFeatureSettings(Tok.TABULAR);
        idleMinutes.setContentDescription("Idle time in minutes");
        it.addView(idleMinutes, new LinearLayout.LayoutParams(dp(64), dp(40)));
        TextView mins = caption("min");
        mins.setPadding(dp(8), 0, 0, 0);
        it.addView(mins);
        swAutoStart = switchRowG(set, R.drawable.ic_play, "Start the guard when the app opens", null, on -> {
            prefs.edit().putBoolean("autostart", on).apply();
            refreshGuardSummary();
        });
        shapeGroup(set);

        guardButton = baseBtn("Start guard", v -> toggleGuard());
        guardButton.setAllCaps(true);
        guardButton.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(52));
        gp.topMargin = dp(24);
        c.addView(guardButton, gp);
        TextView cap = caption("Idle means the phone plays nothing and has no call. If you wear them in silence past the time, they switch off. Wake them with the button.");
        cap.setPadding(dp(4), dp(12), dp(4), 0);
        c.addView(cap);
        return c;
    }

    /** Labelled fader that sends only on release (transparency and call sliders). tvOut[0] receives the value label. */
    private SeekBar slider(LinearLayout parent, String label, int max, TextView[] tvOut, IntLabel fmt, OnInt onRelease) {
        LinearLayout cap = new LinearLayout(this);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.setPadding(0, dp(8), 0, 0);
        cap.addView(text(label, 15, Tok.REGULAR, T.ink), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = text("", 14, Tok.MONO, T.ink2);
        val.setFontFeatureSettings(Tok.TABULAR);
        cap.addView(val);
        parent.addView(cap);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        float density = getResources().getDisplayMetrics().density;
        s.setProgressDrawable(new FaderTrack(T, density, max == 120, max == 4 ? 4 : max == 100 ? 4 : 0));
        GradientDrawable cap6 = rounded(T.faderCap, 7, T.faderEdge, 1.5f);
        cap6.setSize(dp(16), dp(26));
        s.setThumb(cap6);
        s.setSplitTrack(false);
        s.setContentDescription(label);
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

    // ---- Equaliser page (v4 section 6)

    private View soundPage() {
        LinearLayout c = subPage("Equaliser", null, true);

        eqFaders = new EqFaders(this, T);
        eqFaders.setListener(new EqFaders.Listener() {
            public void live(int[] g) { eqCurve.setGains(g); }
            public void commit(List<int[]> changes) {
                for (int[] ch : changes) { send(b2(0x1001, ch[0], ch[1])); eqGain[ch[0]] = ch[1]; }
                eqCurve.setGains(eqGain);
                markPreset();
            }
        });
        gate(eqFaders);
        c.addView(eqFaders, new LinearLayout.LayoutParams(-1, -2));

        android.widget.FrameLayout strip = new android.widget.FrameLayout(this);
        strip.setBackground(rounded(T.surface, 16, 0, 0));
        eqCurve = new EqCurve(this, T);
        strip.addView(eqCurve, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(76));
        sp.topMargin = dp(8);
        c.addView(strip, sp);

        // Poweramp preset row: Flat · preset name · ⋮ (app-side presets; the headset only stores the curve).
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        Button flat = baseBtn("Flat", v -> {
            for (int i = 0; i < 5; i++) send(b2(0x1001, i, 0));
            eqSync.curveWritten();
            setCurve(new int[5]);
        });
        flat.setTextColor(T.ink);
        flat.setBackground(pressable(T.sunk, 20));
        flat.setPadding(dp(18), 0, dp(18), 0);
        gate(flat);
        pr.addView(flat, new LinearLayout.LayoutParams(-2, dp(40)));
        presetPill = baseBtn("Presets", v -> presetSheet());
        presetPill.setTextColor(T.ink);
        StateListDrawable pp = new StateListDrawable();
        pp.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.alpha(T.ink, 0.08f), 20, T.ink2, 1.5f));
        pp.addState(new int[]{}, rounded(T.transparent, 20, T.ink2, 1.5f));
        presetPill.setBackground(pp);
        gate(presetPill);
        LinearLayout.LayoutParams ppl = new LinearLayout.LayoutParams(0, dp(40), 1f);
        ppl.leftMargin = dp(8); ppl.rightMargin = dp(8);
        pr.addView(presetPill, ppl);
        ImageView more = iconButton(R.drawable.ic_more, "More", v -> moreMenu());
        more.setBackground(rounded(T.transparent, 20, T.ink2, 1.5f));
        more.setPadding(dp(8), dp(8), dp(8), dp(8));
        gate(more);
        pr.addView(more, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(-1, -2);
        prp.topMargin = dp(12);
        c.addView(pr, prp);
        TextView hint = caption("Drag a fader, or sweep across all five. Double-tap resets a band to 0.");
        hint.setPadding(dp(4), dp(10), 0, 0);
        c.addView(hint);

        LinearLayout tiles = new LinearLayout(this);
        swBass = tileSwitch(tiles, true, R.drawable.ic_bass, "Bass boost", on -> toggle(0x1008, on));
        swSpeech = tileSwitch(tiles, false, R.drawable.ic_speech, "Speech clarity", on -> send(b2(0x0803, 0, on ? 2 : 1)));
        gate(swBass); gate(swSpeech);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(16);
        c.addView(tiles, tp);
        TextView note = caption("Speech clarity: turn off Hi-Res 24/96 first.");
        note.setPadding(dp(4), dp(8), 0, 0);
        note.setVisibility(View.GONE);
        swSpeech.setTag(note);
        c.addView(note);
        markPreset();
        return c;
    }

    private void moreMenu() {
        new AlertDialog.Builder(this, R.style.AlertTheme)
                .setItems(new String[]{"Save current curve as a preset", "Flat"}, (d, w) -> {
                    if (w == 0) saveCustomDialog();
                    else {
                        for (int i = 0; i < 5; i++) send(b2(0x1001, i, 0));
                        eqSync.curveWritten();
                        setCurve(new int[5]);
                    }
                })
                .show();
    }

    /** Preset list (v4 6.4): curve thumbnail, slot number for custom ones, name, ✓ on the active one. */
    private void presetSheet() {
        android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(20), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(T.surface);
        bg.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        box.setBackground(bg);
        TextView title = text("Presets", 24, Tok.SERIF, T.ink);
        title.setPadding(dp(4), 0, 0, dp(12));
        box.addView(title);
        List<Presets.Preset> all = Presets.all(this);
        int m = Presets.matching(this, eqGain);
        for (int i = 0; i < all.size(); i++) {
            Presets.Preset p = all.get(i);
            boolean sel = i == m && eqSync.mayPublish();
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(12), 0, dp(12), 0);
            r.setBackground(pressable(sel ? T.sunk : T.surface, 12));
            r.setClickable(true);
            r.setContentDescription(p.name + (sel ? ", selected" : ""));
            ImageView th = new ImageView(this);
            th.setImageBitmap(EqCurve.thumbnail(this, T, p.gains, 64, 24));
            th.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(th, new LinearLayout.LayoutParams(dp(64), dp(24)));
            TextView slot = text(p.slot >= 0 ? String.valueOf(p.slot + 1) : "", 13, Tok.MONO, T.ink2);
            slot.setGravity(Gravity.CENTER);
            r.addView(slot, new LinearLayout.LayoutParams(dp(28), -2));
            r.addView(text(p.name, 15, sel ? Tok.MEDIUM : Tok.REGULAR, T.ink), new LinearLayout.LayoutParams(0, -2, 1f));
            if (sel) r.addView(text("✓", 16, Tok.MEDIUM, T.copper));
            r.setOnClickListener(v -> { dlg.dismiss(); applyPreset(p); });
            if (p.slot >= 0) r.setOnLongClickListener(v -> { dlg.dismiss(); manageCustomDialog(p); return true; });
            box.addView(r, new LinearLayout.LayoutParams(-1, dp(52)));
        }
        Button save = textBtn("Save current curve as a preset", v -> { dlg.dismiss(); saveCustomDialog(); });
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(-1, dp(48));
        svp.topMargin = dp(8);
        box.addView(save, svp);
        TextView lp = caption("Long-press a custom preset to rename, overwrite or delete it.");
        lp.setPadding(dp(4), dp(4), 0, 0);
        box.addView(lp);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg.setContentView(sv);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(T.transparent));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    /** All five bands at once, then one preset match (no half-updated curve reaches the widgets). */
    private void setCurve(int[] gainsX10) {
        for (int b = 0; b < 5; b++) eqGain[b] = Math.max(-60, Math.min(60, gainsX10[b]));
        eqFaders.setGains(eqGain);
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

    /** Presets live in the sheet now; after a save, rename or delete only the name pill and tiles need a refresh. */
    private void rebuildPresetChips() { markPreset(); }

    /** Name pill, Equaliser tile and widgets follow the preset whose curve matches exactly. */
    private void markPreset() {
        if (presetPill == null) return;
        int m = Presets.matching(this, eqGain);
        String name = m >= 0 ? Presets.all(this).get(m).name : "Custom curve";
        boolean known = eqSync.mayPublish();
        presetPill.setText(known ? name : "Presets");
        if (tileSound != null) tileSound.setText(known ? name : NOT_READ);
        if (known) Widgets.setEqIdx(this, m);
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
        new AlertDialog.Builder(this, R.style.AlertTheme)
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
        new AlertDialog.Builder(this, R.style.AlertTheme)
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

    // ---- Listening page (v4 section 7)

    private View listeningPage() {
        LinearLayout c = subPage("Listening", null, true);
        LinearLayout pb = group(c, "Playback");
        swSmartPause = switchRowG(pb, R.drawable.ic_pause, "Smart pause", "Pause when you take them off", on -> toggle(0x080C, on));
        swLowLatency = switchRowG(pb, R.drawable.ic_bolt, "Low latency", "From Sennheiser's older app. Effect not measured yet.", on -> toggle(0x0817, on));
        swHiRes = switchRowG(pb, R.drawable.ic_hires, "Hi-Res 24/96", "Takes effect after a restart",
                on -> { send(a(0x0405, on ? 0 : 1)); send(g(0x0406)); applySpeechRule(); });   // 0 = Hi-Res on
        shapeGroup(pb);

        LinearLayout calls = group(c, "Calls");
        swAutoAnswer = switchRowG(calls, R.drawable.ic_call, "Auto-answer calls", null, on -> toggle(0x080A, on));
        swComfort = switchRowG(calls, R.drawable.ic_ear, "Comfort call", null, on -> toggle(0x0814, on));
        LinearLayout ct = new LinearLayout(this);
        ct.setOrientation(LinearLayout.VERTICAL);
        ct.setPadding(dp(16), dp(4), dp(16), dp(8));
        TextView[] tv = new TextView[1];
        callSeek = slider(ct, "Transparency during calls", 4, tv, p -> p == 0 ? "off" : p + " / 4",
                p -> { send(a(0x0805, p)); send(g(0x0806)); });
        callValue = tv[0];
        calls.addView(ct);
        shapeGroup(calls);

        LinearLayout snd = group(c, "Sounds");
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.VERTICAL);
        pr.setPadding(dp(16), dp(10), dp(16), dp(12));
        pr.addView(text("Tones and voice prompts", 15, Tok.REGULAR, T.ink));
        promptSeg = new Segmented(new String[]{"Off", "Tones", "Tones + voice"}, 38,
                idx -> { send(a(0x0801, idx)); send(g(0x0802)); });
        LinearLayout.LayoutParams pgp = new LinearLayout.LayoutParams(-1, -2);
        pgp.topMargin = dp(8);
        pr.addView(promptSeg.view, pgp);
        snd.addView(pr);
        shapeGroup(snd);
        gate(promptSeg.view);

        LinearLayout hd = group(c, "Headphones");
        swOnHead = switchRowG(hd, R.drawable.ic_head, "On-head detection", null, on -> toggle(0x0400, on));
        swTouch = switchRowG(hd, R.drawable.ic_hand, "Touch controls", null, on -> send(a(0x1606, on ? 0 : 1)));   // inverted: 0 = enabled
        shapeGroup(hd);
        gate(swSmartPause); gate(swAutoAnswer); gate(swOnHead); gate(swLowLatency); gate(swHiRes); gate(swComfort); gate(swTouch);
        return c;
    }

    private boolean listenRead;

    /** Home tile: how many of the 7 listening switches are on (Hi-Res counts as a switch). */
    private void updateListenTile() {
        if (tileListen == null || swHiRes == null || swTouch == null) return;
        if (!listenRead) { tileListen.setText(NOT_READ); return; }
        Switch[] all = {swSmartPause, swAutoAnswer, swOnHead, swLowLatency, swComfort, swTouch, swHiRes};
        int n = 0;
        for (Switch s : all) if (s.isChecked()) n++;
        tileListen.setText(n + " of " + all.length + " on");
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
        secondsField.setFontFeatureSettings(Tok.TABULAR);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        sp.leftMargin = dp(8); sp.rightMargin = dp(8);
        tr.addView(secondsField, sp);
        Button setT = outlineBtn("Set", v -> setTimer0());
        gate(setT);
        tr.addView(setT);
        inner.addView(tr);

        inner.addView(caption("Raw frame (hex)"));
        hexField = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, "FF 03 00 00 04 95 06 03");
        hexField.setTypeface(Tok.CODE);
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
        log.setTypeface(Tok.CODE);
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
            c.setBackground(rounded(T.surface, 24, 0, 0));
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
        TextView noteView = note == null ? null : caption(note);
        if (noteView != null) labels.addView(noteView);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = new Switch(this);
        tintSwitch(s);
        s.setOnCheckedChangeListener((b, checked) -> { if (!applying) onChange.on(checked); updateListenTile(); });
        row.addView(s);
        parent.addView(row);
        s.setTag(noteView);                    // the caption under the label, if any
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
        if (tf == Tok.MONO) t.setFontFeatureSettings(Tok.TABULAR);
        t.setTextColor(color);
        return t;
    }

    private TextView body(String s) { return text(s, 16, Tok.REGULAR, T.ink); }

    private TextView caption(String s) { return text(s, 13, Tok.REGULAR, T.ink2); }

    /** v4 section label: small grey sentence case (mono caps dropped, design v4 section 4). */
    private TextView label(String s) { return text(s, 13, Tok.REGULAR, T.ink2); }

    private TextView sectionLabel(String s) {
        TextView t = label(s);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

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
        e.setBackground(rounded(T.sunk, 12, 0, 0));
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
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.mix(T.copper, T.ink, 0.2f), 24, 0, 0));
        sld.addState(new int[]{}, rounded(T.copper, 24, 0, 0));
        b.setBackground(sld);
        b.setTextColor(T.onCopper);
    }

    /** Outline: a secondary action, or the "undo" side of a toggle (Disconnect, Stop guard). */
    private void styleOutline(Button b) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, rounded(Tok.alpha(T.ink, 0.08f), 24, T.ruleStrong, 1));
        sld.addState(new int[]{}, rounded(T.transparent, 24, T.ruleStrong, 1));
        b.setBackground(sld);
        b.setTextColor(T.ink);
    }

    /** v4 "undo" side of a toggle (Disconnect, Stop guard): sunk pill, ink text. */
    private void styleSunk(Button b) {
        b.setBackground(pressable(T.sunk, 26));
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

    /** The headset refuses Speech clarity while Hi-Res is on (0803 -> error 0x83, T39): say so instead of failing. */
    private void applySpeechRule() {
        if (swSpeech == null || swHiRes == null) return;
        boolean linked = out != null, locked = swHiRes.isChecked() && !swSpeech.isChecked();
        swSpeech.setEnabled(linked && !locked);
        swSpeech.setAlpha(linked && !locked ? 1f : DISABLED);
        Object note = swSpeech.getTag();
        if (note instanceof TextView) ((TextView) note).setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private void setGroupEnabled(boolean on) {
        for (View v : gated) { v.setEnabled(on); v.setAlpha(on ? 1f : DISABLED); }
        if (noiseHint != null) noiseHint.setVisibility(on ? View.GONE : View.VISIBLE);
        for (TextView h : pageHints) h.setVisibility(on ? View.GONE : View.VISIBLE);
        for (View v : dimmed) v.setAlpha(on ? 1f : DISABLED);
        applySpeechRule();
    }

    /** Segmented keys on a sunk track; the selected key is ink-filled with the pilot dot. */
    private final class Segmented {
        final LinearLayout view;
        final Button[] buttons;
        final String[] labels;
        final OnInt onSelect;
        int selected = -1;
        final int heightDp;

        Segmented(String[] opts, int heightDp, OnInt onSelect) {
            this.onSelect = onSelect;
            this.labels = opts;
            this.heightDp = heightDp;
            view = new LinearLayout(MainActivity.this);
            view.setBackground(rounded(T.sunk, heightDp / 2f, 0, 0));
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
                buttons[j].setBackground(sel ? rounded(T.ink, (heightDp - 6) / 2f, 0, 0) : null);
                buttons[j].setTextColor(sel ? T.onKey : T.ink);
                buttons[j].setText(sel ? dotted(labels[j], T.pilotOnKey) : labels[j]);
            }
            if (fire) onSelect.on(i);
        }
    }

    /** v4 noise buttons: 60 dp circles with an icon and a label under each; selected = ink circle. */
    private final class RoundChoice {
        final LinearLayout view;
        final ImageView[] circles;
        final TextView[] labels;
        final OnInt onSelect;
        int selected = -1;

        RoundChoice(String[] opts, int[] icons, OnInt onSelect) {
            this.onSelect = onSelect;
            view = new LinearLayout(MainActivity.this);
            circles = new ImageView[opts.length];
            labels = new TextView[opts.length];
            for (int i = 0; i < opts.length; i++) {
                final int idx = i;
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView c = new ImageView(MainActivity.this);
                c.setImageResource(icons[i]);
                c.setPadding(dp(18), dp(18), dp(18), dp(18));
                c.setContentDescription(opts[i]);
                c.setClickable(true);
                c.setOnClickListener(v -> select(idx, true));
                gate(c);
                col.addView(c, new LinearLayout.LayoutParams(dp(60), dp(60)));
                TextView l = text(opts[i], 13, Tok.REGULAR, T.ink2);
                l.setPadding(0, dp(8), 0, 0);
                l.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                col.addView(l);
                circles[i] = c; labels[i] = l;
                view.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            paint();
        }

        void select(int i, boolean fire) {
            selected = i;
            paint();
            if (fire) onSelect.on(i);
        }

        private void paint() {
            for (int j = 0; j < circles.length; j++) {
                boolean sel = j == selected;
                circles[j].setBackground(pressable(sel ? T.ink : T.sunk, 30));
                circles[j].setImageTintList(ColorStateList.valueOf(sel ? T.onKey : T.ink2));
                circles[j].setSelected(sel);
                labels[j].setTextColor(sel ? T.ink : T.ink2);
                labels[j].setTypeface(sel ? Tok.MEDIUM : Tok.REGULAR);
            }
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
        if (f.type() == 3) {            // error reply: payload[0] is an error code, not a value
            // 0x80 = value already set (no change). A refused Speech clarity write (0x83 while Hi-Res is on, T39):
            // read the real sound mode back so the switch doesn't show a state the headset refused.
            if (base == 0x0803 && b0 != 0x80) send(g(0x0804));
            return;
        }
        applying = true;
        switch (base) {
            case 0x0603: if (b0 >= 0) { batteryPct = b0; showBattery(); batteryDots.set(b0 + "%", true, "Battery " + b0 + " percent"); } break;
            case 0x0602: if (b0 >= 0) { charging = b0 == 1; showBattery(); } break;
            case 0x0802: if (b0 >= 0 && b0 <= 2) promptSeg.select(b0, false); break;
            case 0x1407: if (b0 >= 0) { ownPeer = b0; showPeers(); } break;
            case 0x1400: if (f.payload.length >= 2) {                  // count first, then each entry, never past it
                int n = Math.min(Gaia.u16(f.payload, 0), 8);
                peers.clear();
                for (int i = 0; i < n; i++) send(g(0x1401, i));
                showPeers();
            } break;
            case 0x1401: if (f.type() == 2 && f.payload.length >= 3) {
                peers.put(b0, new String[]{ascii(java.util.Arrays.copyOfRange(f.payload, 3, f.payload.length)),
                        (f.payload[2] & 0xFF) == 1 ? "1" : "0"});
                showPeers();
            } break;
            case 0x1206: setValue(vModel, ascii(f.payload)); modelText = ascii(f.payload); updateDeviceTile(); break;
            case 0x1201: if (f.payload.length >= 6)                          // firmware = 3 x u16 BE
                { String fw = Gaia.u16(f.payload, 0) + "." + Gaia.u16(f.payload, 2) + "." + Gaia.u16(f.payload, 4);
                  setValue(vFirmware, fw); fwText = fw; renderStatus(); }
                break;
            case 0x1200: if (f.payload.length >= 2) hwText = String.valueOf(Gaia.u16(f.payload, 0));
                else if (b0 >= 0) hwText = String.valueOf(b0);
                if (hwText != null) { setValue(vHardware, hwText); updateDeviceTile(); }
                break;
            case 0x1A05: if (b0 >= 0) { ancOn = b0 == 1; refreshNoise(); } break;
            case 0x1A01:                                                     // [mode,state] pairs: 1 anti-wind, 3 adaptive
                for (int i = 0; i + 1 < f.payload.length; i += 2) {
                    int mode = f.payload[i] & 0xFF, st = f.payload[i + 1] & 0xFF;
                    if (mode == 3) adaptiveOn = st == 1;
                    if (mode == 1 && st <= 2) antiWind.select(antiWindSwap(st), false);
                }
                refreshNoise();
                break;
            case 0x1A03: if (b0 >= 0 && b0 <= 100) transpSeek.setProgress(b0); break;
            case 0x080D: if (b0 >= 0) { swSmartPause.setChecked(b0 == 1); listenRead = true; updateListenTile(); } break;
            case 0x080B: if (b0 >= 0) { swAutoAnswer.setChecked(b0 == 1); listenRead = true; updateListenTile(); } break;
            case 0x0818: if (b0 >= 0) { swLowLatency.setChecked(b0 == 1); listenRead = true; updateListenTile(); } break;
            case 0x0401: if (b0 >= 0) { swOnHead.setChecked(b0 == 1); listenRead = true; updateListenTile(); } break;
            case 0x0406: if (b0 >= 0) { swHiRes.setChecked(b0 == 0); applySpeechRule(); listenRead = true; updateListenTile(); } break;   // 0 = Hi-Res on
            case 0x0806: if (b0 >= 0 && b0 <= 4) callSeek.setProgress(b0); break;
            case 0x1009: if (b0 >= 0) swBass.setChecked(b0 == 1); break;
            case 0x0804: if (f.payload.length >= 2) swSpeech.setChecked(Gaia.u16(f.payload, 0) == 2); break;
            case 0x0815: if (b0 >= 0) { swComfort.setChecked(b0 == 1); listenRead = true; updateListenTile(); } break;
            case 0x1607: if (b0 >= 0) { swTouch.setChecked(b0 == 0); listenRead = true; updateListenTile(); } break;   // inverted
            case 0x1003: if (f.type() == 2 && f.payload.length >= 5) setCurve(f.payload); break;
            case 0x1002: if (f.type() == 1 && f.payload.length >= 5) setCurve(f.payload); break;   // 1082 notif = all 5 bands
        }
        applying = false;
    }

    private void showBattery() {
        if (batteryPct >= 0) setValue(vBattery, batteryPct + " %" + (charging ? " · charging" : ""));
    }

    /** Devices paired with the headphones. Names stay on this screen: the log and the report hide them. */
    private void showPeers() {
        if (peersBox == null) return;
        peersBox.removeAllViews();
        for (java.util.Map.Entry<Integer, String[]> e : peers.entrySet()) {
            final int idx = e.getKey();
            boolean me = idx == ownPeer, on = "1".equals(e.getValue()[1]);
            String sub = me ? "This phone" : on ? "Connected" : "Tap to connect";
            LinearLayout r = row(peersBox, R.drawable.ic_headphones, e.getValue()[0], sub);
            if (!me && !on) { r.setClickable(true); r.setOnClickListener(v -> { send(a(0x1402, idx)); toast("Connecting that device"); }); }
        }
        if (peers.isEmpty()) row(peersBox, R.drawable.ic_info, "Not read yet", "Tap Refresh");
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
        List<String> names = new ArrayList<>(), addrs = new ArrayList<>();
        boolean sawMomentum = false;
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            String name = nameOf(d);
            if (DevicePick.looksLikeMomentum(name)) sawMomentum = true;
            devices.add(d);
            names.add(name);
            addrs.add(d.getAddress());
        }
        deviceIndex = DevicePick.preselect(addrs, names, keep);
        showDeviceName();
        if (socket == null && !connecting) {
            if (devices.isEmpty()) setStatus(0, "No paired headphones. Pair them in Android Settings > Bluetooth, then tap Rescan.");
            else if (!sawMomentum) setStatus(0, "No MOMENTUM 4 among the paired devices. Pair it, then tap Rescan.");
            else showIdle();
        }
        autoCheck();

        if (devices.isEmpty()) {
            append("No paired Bluetooth devices. Pair the MOMENTUM 4 in Android Settings > Bluetooth, then tap Rescan.");
        } else if (!sawMomentum) {
            append("No MOMENTUM 4 among the paired devices. Pair it in Android Settings > Bluetooth, then tap Rescan. (Pick another device only if you know it speaks GAIA.)");
        }
    }

    private BluetoothDevice selectedDevice() {
        int i = deviceIndex;
        return (i >= 0 && i < devices.size()) ? devices.get(i) : null;
    }

    /** The picker's list: paired devices, the current one checked. Picking only changes the target. */
    private void chooseDevice() {
        if (devices.isEmpty()) { rescan(); return; }
        String[] names = new String[devices.size()];
        for (int i = 0; i < names.length; i++) names[i] = nameOf(devices.get(i));
        new AlertDialog.Builder(this, R.style.AlertTheme)
                .setTitle("Headphones")
                .setSingleChoiceItems(names, deviceIndex, (d, which) -> {
                    deviceIndex = which;
                    showDeviceName();
                    d.dismiss();
                    if (socket == null && !connecting) { main.removeCallbacks(autoRun); showIdle(); autoCheck(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** The Connect button. */
    private void connect() { connect(true); }

    /**
     * Opens the GAIA link to the selected device. byHand = the Connect button: on success the device
     * is remembered as OK to auto-connect. Automatic tries log one short line and retry with back-off.
     */
    @SuppressWarnings("MissingPermission")
    private void connect(boolean byHand) {
        if (socket != null) { append("Already connected."); return; }
        if (connecting) return;
        BluetoothDevice device = selectedDevice();
        if (device == null) { if (byHand) toast("No device"); return; }
        main.removeCallbacks(autoRun);
        if (byHand) auto.manualConnect();
        prefs.edit().putString("dev", device.getAddress()).putString("devName", nameOf(device)).apply();
        append(byHand ? "Connecting..." : "Auto-connecting to " + nameOf(device) + "...");
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
                if (destroyed) { closeQuietly(); return; }   // rotated or closed while connecting: don't keep the link
                runOnUiThread(() -> {
                    linkSince = SystemClock.elapsedRealtime();
                    if (byHand) rememberByHand(device);
                    setConnected(true); registerNotifications(); readAllConfig();
                });
                readLoop(s, s.getInputStream());
            } catch (IOException e) {
                closeQuietly();
                if (byHand) {
                    append("Connect failed: " + e.getMessage()
                            + "\nTry: force stop Smart Control Plus; make sure the MOMENTUM 4 is connected to this device;"
                            + " if it is paired to two other devices (multipoint), disconnect one. Then Connect again.");
                }
                runOnUiThread(() -> {
                    setConnected(false);
                    long next = byHand ? -1 : auto.tryFailed(Boolean.TRUE.equals(headsetUp(device)));
                    if (!byHand) append("Auto-connect failed: " + e.getMessage()
                            + (next >= 0 ? " (next try in " + next / 1000 + " s)" : ""));
                    if (next >= 0 && started) { setStatus(2, "Connecting..."); main.postDelayed(autoRun, next); }
                    else setStatus(0, "Couldn't reach the headphones. They may be off or held by Smart Control (force-stop it).");
                });
            }
        }).start();
    }

    private void readLoop(BluetoothSocket s, InputStream in) {
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
        BluetoothSocket cur = socket;
        if (cur != null && cur != s) return;                 // a newer link is live: leave it alone
        closeQuietly();
        runOnUiThread(() -> {
            setConnected(false);
            if (userDisconnect) { setStatus(0, "Not connected"); return; }
            // Lost without a Disconnect tap (headset switched off, out of range, guard fired):
            // reconnect if it is still on the phone, else wait for it.
            BluetoothDevice t = selectedDevice();
            Boolean up = t == null ? Boolean.FALSE : headsetUp(t);
            long next = t == null ? -1 : auto.linkLost(eligible(t), Boolean.TRUE.equals(up),
                    SystemClock.elapsedRealtime() - linkSince);
            if (next >= 0 && started) { setStatus(2, "Connecting..."); main.postDelayed(autoRun, next); }
            else if (!showWaiting()) setStatus(0, "Connection lost. Tap Connect.");
        });
    }

    // ---- auto-connect helpers (decisions in AutoConnect)

    private boolean eligible(BluetoothDevice d) {
        return AutoConnect.eligible(nameOf(d), prefs.getStringSet("autoOk", new HashSet<>()).contains(d.getAddress()));
    }

    /** A device the user connected by hand may be auto-connected later (similar Sennheiser models). */
    private void rememberByHand(BluetoothDevice d) {
        Set<String> ok = new HashSet<>(prefs.getStringSet("autoOk", new HashSet<>()));
        if (ok.add(d.getAddress())) prefs.edit().putStringSet("autoOk", ok).apply();
    }

    /** Is d connected to the phone (ACL seen, A2DP or headset profile)? null = can't tell yet. */
    @SuppressWarnings("MissingPermission")
    private Boolean headsetUp(BluetoothDevice d) {
        if (d == null || !hasBtPermission()) return null;
        if (aclUp.contains(d.getAddress())) return true;
        if (a2dp == null && headsetProfile == null) return null;
        try {
            for (BluetoothProfile p : new BluetoothProfile[]{a2dp, headsetProfile}) {
                if (p != null && p.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED) return true;
            }
        } catch (SecurityException e) {
            return null;
        }
        return false;
    }

    /** On open, after a re-scan or a pick, once the proxies answer: link at once if the headset is up. */
    private void autoCheck() {
        if (!started || destroyed || socket != null || connecting) return;
        BluetoothDevice t = selectedDevice();
        if (t == null) return;
        Boolean up = headsetUp(t);
        if (up == null) return;                          // the proxy callback calls back
        if (auto.check(eligible(t), up) >= 0) connect(false);
        else if (!up) showWaiting();                     // keeps a more specific text otherwise
    }

    private void autoAttempt() {
        if (!started || destroyed || socket != null || connecting || auto.held()) return;
        BluetoothDevice t = selectedDevice();
        if (t == null || !eligible(t)) { showIdle(); return; }
        if (Boolean.FALSE.equals(headsetUp(t))) { showIdle(); return; }
        connect(false);
    }

    @SuppressWarnings("MissingPermission")
    private void onHeadsetEvent(Intent intent) {
        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        BluetoothDevice t = selectedDevice();
        if (d == null || t == null || !d.getAddress().equals(t.getAddress())) return;
        String a = intent.getAction();
        boolean up;
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) { aclUp.add(d.getAddress()); up = true; }
        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) { aclUp.remove(d.getAddress()); up = false; }
        else {
            int st = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            if (st == BluetoothProfile.STATE_CONNECTED) up = true;
            else if (st == BluetoothProfile.STATE_DISCONNECTED) up = Boolean.TRUE.equals(headsetUp(t));
            else return;
        }
        if (up) {
            long next = auto.headsetUp(eligible(t));     // also ends a Disconnect hold
            if (next < 0 || socket != null || connecting) return;
            append(nameOf(t) + " connected to the phone.");
            setStatus(2, "Connecting...");
            main.removeCallbacks(autoRun);               // ACL, then each profile: try once, after the last
            main.postDelayed(autoRun, next);
        } else {
            auto.headsetDown();
            main.removeCallbacks(autoRun);
            if (socket == null && !connecting) showIdle();
        }
    }

    /** Status while not linked: "Waiting for MOMENTUM 4" when it is off the phone, else "Not connected". */
    private void showIdle() {
        if (!showWaiting()) setStatus(0, "Not connected");
    }

    private boolean showWaiting() {
        BluetoothDevice t = selectedDevice();
        String w = t == null ? null : AutoConnect.idleText(auto.held(), eligible(t), headsetUp(t), nameOf(t));
        if (w == null) return false;
        setStatus(3, w);
        return true;
    }

    private void closeProxy(int profile, BluetoothProfile proxy) {
        if (proxy == null) return;
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if (adapter != null) adapter.closeProfileProxy(profile, proxy);
    }

    private void setConnected(boolean on) {
        if (on) live = this;
        else if (live == this) live = null;
        if (!on) eqSync.disconnected();
        connecting = false;
        if (on) setStatus(1, "Connected");
        else setStatus(0, "Not connected");                      // callers set a more specific text after
        connectBtn.setEnabled(true);
        connectBtn.setText(on ? "Disconnect" : "Connect");
        if (on) styleSunk(connectBtn); else styleFilled(connectBtn);
        setGroupEnabled(on);
    }

    private void send(byte[] frame) {
        String bad = Gaia.singleFrameProblem(frame);          // exactly one complete frame, so blockedReason sees all of it
        if (bad != null) {
            append("BLOCKED (" + bad + "): " + Gaia.hex(frame));
            toast("Not sent: " + bad + ". One complete frame at a time.");
            return;
        }
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
        auto.userDisconnected();
        main.removeCallbacks(autoRun);
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
                0x1009, 0x0804, 0x0603, 0x0602, 0x0802, 0x1407, 0x1206, 0x1201, 0x1200};
        send(g(0x1400));
        for (int x : gets) send(g(x));
        send(g(0x1003, 0));                                          // all 5 EQ gains at once
    }

    /** Ask for change notifications (noise 0x0d, EQ 0x08, audio/device 0x04), as the official app does. */
    private void registerNotifications() {
        for (int feat : new int[]{0x0d, 0x08, 0x04, 0x03}) send(g(0x0007, feat));
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
        new AlertDialog.Builder(this, R.style.AlertTheme)
                .setTitle("Power off the headphones?")
                .setMessage("They switch off fully. Only the button on the headphones turns them back on.")
                .setPositiveButton("Power off", (d, w) -> send(Gaia.powerOff()))
                .setNegativeButton("Cancel", null)
                .show()
                .getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(T.danger);
    }

    private void toggleGuard() {
        guardPendingUntil = System.currentTimeMillis() + 2400;
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
        if (!isMomentum(device) && !confirmedOther) {
            new AlertDialog.Builder(this, R.style.AlertTheme)
                    .setTitle("Use " + nameOf(device) + "?")
                    .setMessage("This is not a MOMENTUM 4. The guard would try to switch off this device instead of your headphones.")
                    .setPositiveButton("Use it anyway", (d, w) -> { confirmedOther = true; startGuard(); confirmedOther = false; })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        prefs.edit().putInt("minutes", minutes).putString("dev", device.getAddress()).putString("devName", nameOf(device)).apply();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        // No link drop here any more: the guard's own socket works while the app holds one (T42),
        // and with auto-connect a dropped link would only come straight back.
        AutoOffService.start(this, device.getAddress(), minutes * 60_000L);
        append("Idle guard started (" + minutes + " min).");
        refreshGuardButton();
    }

    /** The service flips `running` asynchronously, so re-check shortly after a start/stop. */
    private void refreshGuardButton() {
        applyGuardState();
        if (guardButton != null) {
            guardButton.postDelayed(this::applyGuardState, 600);
            guardButton.postDelayed(this::applyGuardState, 2600);
        }
    }

    private void applyGuardState() {
        if (guardButton == null) return;
        boolean on = AutoOffService.running;
        guardButton.setText(on ? "Stop guard" : "Start guard");
        if (on) styleSunk(guardButton); else styleFilled(guardButton);
        if (guardSwitch != null && guardSwitch.isChecked() != on && System.currentTimeMillis() >= guardPendingUntil) {
            guardApplying = true;
            guardSwitch.setChecked(on);
            guardApplying = false;
        }
        GuardState g = guardState();
        guardDots.set(g.dots, g.on, g.dotsDesc);
        guardUnit.setText(g.unit);
        refreshGuardSummary();
        guardStatus.setText(g.status);
        guardStatus.setTextColor(g.failed ? T.danger : T.ink2);
        refreshGuardTarget();
    }

    /** Shared text must not carry a renamed headset's name (audit F-11): swap it for the safe label. */
    @SuppressWarnings("MissingPermission")
    private String scrubNames(String text) {
        BluetoothDevice d = selectedDevice();
        return Report.scrub(text, d == null ? null : nameOf(d), prefs.getString("devName", ""));
    }

    private void copyLog() {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", scrubNames(Report.redact(log.getText().toString()))));
        toast("Log copied");
    }

    /** Device > Copy report: one paste-able block for Reddit or the GitHub report form. */
    @SuppressWarnings("MissingPermission")
    private void copyReport() {
        String ver = "?";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        BluetoothDevice d = selectedDevice();
        StringBuilder r = new StringBuilder("M4 app report\n");
        r.append("App: ").append(ver).append('\n');
        r.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        r.append("Headphones: ").append(Report.safeName(d == null ? null : nameOf(d)))
                .append(", connected now: ").append(out != null ? "yes" : "no").append('\n');
        r.append("Model: ").append(vModel.getText()).append(", firmware: ").append(vFirmware.getText())
                .append(", hardware: ").append(vHardware.getText()).append(", battery: ").append(vBattery.getText()).append('\n');
        r.append("Guard: ").append(AutoOffService.running ? "running" : "stopped")
                .append(", idle time ").append(prefs.getInt("minutes", 15)).append(" min, autostart ")
                .append(prefs.getBoolean("autostart", false) ? "on" : "off").append('\n');
        r.append("\n-- guard log (last 40) --\n").append(scrubNames(Report.tail(AutoOffService.getLog(), 40)));
        r.append("\n\n-- app log (last 40) --\n").append(scrubNames(Report.tail(log.getText().toString(), 40))).append('\n');
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("M4 report", r.toString()));
        toast("Report copied. Paste it in your comment or the report form.");
    }

    private void append(String line) {
        android.util.Log.i("M4GAIA", line);   // readable over USB: adb logcat -s M4GAIA
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        runOnUiThread(() -> { log.append(stamp + " " + line + "\n"); logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN)); });
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() { destroyed = true; if (live == this) live = null; closeQuietly(); sender.shutdown(); super.onDestroy(); }
}
