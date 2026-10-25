package be.ryane.gaiatest;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.TileService;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.List;

/**
 * Shared state and actions for the home-screen widgets and Quick Settings tiles.
 * State shown = last mode/preset set or read by this app (prefs), because a widget cannot
 * read the headset without connecting.
 */
final class Widgets {
    static final String ACT_NOISE = "be.ryane.gaiatest.NOISE", ACT_EQ_STEP = "be.ryane.gaiatest.EQ_STEP";
    static final String EXTRA_MODE = "mode", EXTRA_STEP = "step";

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("m4", Context.MODE_PRIVATE); }
    static int noise(Context c) { return prefs(c).getInt("noise", -1); }
    static int eqIdx(Context c) { return prefs(c).getInt("eqIdx", -1); }

    /** Called by the app when it learns the state from the headset. Refreshes widgets and tiles. */
    static void setNoise(Context c, int mode) {
        if (noise(c) == mode) return;
        prefs(c).edit().putInt("noise", mode).apply();
        refresh(c);
    }

    static void setEqIdx(Context c, int idx) {
        if (eqIdx(c) == idx) return;
        prefs(c).edit().putInt("eqIdx", idx).apply();
        refresh(c);
    }

    static String eqName(Context c) {
        List<Presets.Preset> all = Presets.all(c);
        int i = eqIdx(c);
        return i >= 0 && i < all.size() ? all.get(i).name : "Custom curve";
    }

    // ---- actions (from widgets and tiles)

    /** attempts: Remote.WIDGET_ATTEMPTS or TILE_ATTEMPTS. after runs before the failure toast. */
    static void applyNoise(Context c, int mode, int attempts, Runnable after) {
        Remote.send(c, Remote.noiseFrames(mode), attempts, (ok, msg) -> {
            if (ok) setNoise(c, mode);
            if (after != null) after.run();
            if (!ok) toast(c, "Noise control: " + msg);
        });
    }

    /** step = +1 next preset, -1 previous. Cycles built-ins then custom presets. */
    static void stepEq(Context c, int step, int attempts, Runnable after) {
        List<Presets.Preset> all = Presets.all(c);
        int cur = eqIdx(c);
        int next = cur < 0 ? (step > 0 ? 0 : all.size() - 1) : Math.floorMod(cur + step, all.size());
        Presets.Preset p = all.get(next);
        Remote.send(c, Presets.frames(p.gains), attempts, (ok, msg) -> {
            if (ok) setEqIdx(c, next);
            if (after != null) after.run();
            if (!ok) toast(c, "EQ: " + msg);
        });
    }

    /**
     * Ends a widget click broadcast: on the normal result or at WIDGET_DEADLINE_MS, whichever is
     * first, so Android never reaches its 10 s ANR limit. A late result still shows its toast.
     */
    static Runnable finishInTime(BroadcastReceiver.PendingResult pr) {
        Runnable finish = Watchdog.once(pr::finish);
        Watchdog.runAfter(Remote.WIDGET_DEADLINE_MS, finish);
        return finish;
    }

    static void toast(Context c, String m) { Toast.makeText(c.getApplicationContext(), m, Toast.LENGTH_SHORT).show(); }

    // ---- redraw

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, NoiseWidget.class))) m.updateAppWidget(id, noiseViews(c));
        for (int id : m.getAppWidgetIds(new ComponentName(c, EqWidget.class))) m.updateAppWidget(id, eqViews(c));
        try {
            TileService.requestListeningState(c, new ComponentName(c, NoiseTile.class));
            TileService.requestListeningState(c, new ComponentName(c, EqTile.class));
        } catch (RuntimeException ignored) {}   // tile not added
    }

    /**
     * Design v2 (spec 4): every colour comes from @color resources inside the layouts, and the
     * selected state is shown by toggling views, never by setTextColor(int), so the launcher can
     * repaint the widget for light or dark mode without the app.
     */
    static RemoteViews noiseViews(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_noise);
        int mode = noise(c);
        int[] ids = {R.id.w_adaptive, R.id.w_custom, R.id.w_off};
        int[] on = {R.id.w_adaptive_on, R.id.w_custom_on, R.id.w_off_on};
        int[] off = {R.id.w_adaptive_off, R.id.w_custom_off, R.id.w_off_off};
        for (int i = 0; i < 3; i++) {
            boolean sel = i == mode;
            v.setInt(ids[i], "setBackgroundResource", sel ? R.drawable.w_key_on : 0);
            v.setViewVisibility(on[i], sel ? View.VISIBLE : View.GONE);
            v.setViewVisibility(off[i], sel ? View.GONE : View.VISIBLE);
            v.setOnClickPendingIntent(ids[i], broadcast(c, NoiseWidget.class, ACT_NOISE, EXTRA_MODE, i, 10 + i));
        }
        boolean known = mode >= 0;
        v.setTextViewText(R.id.w_title, known ? prefs(c).getString("devName", "M4") : "Tap a mode. Not read yet.");
        v.setViewVisibility(R.id.w_note, known ? View.VISIBLE : View.GONE);
        v.setOnClickPendingIntent(R.id.w_title, openApp(c));
        return v;
    }

    static RemoteViews eqViews(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_eq);
        List<Presets.Preset> all = Presets.all(c);
        int i = eqIdx(c);
        boolean known = i >= 0 && i < all.size();
        v.setTextViewText(R.id.w_eq_name, eqName(c));
        v.setViewVisibility(R.id.w_eq_dot, known ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_eq_pos, known ? "Preset " + (i + 1) + " of " + all.size() : "");
        v.setViewVisibility(R.id.w_eq_pos, known ? View.VISIBLE : View.GONE);
        v.setOnClickPendingIntent(R.id.w_eq_prev, broadcast(c, EqWidget.class, ACT_EQ_STEP, EXTRA_STEP, -1, 20));
        v.setOnClickPendingIntent(R.id.w_eq_next, broadcast(c, EqWidget.class, ACT_EQ_STEP, EXTRA_STEP, 1, 21));
        v.setOnClickPendingIntent(R.id.w_eq_center, openApp(c));
        return v;
    }

    private static PendingIntent broadcast(Context c, Class<?> cls, String action, String key, int val, int req) {
        Intent i = new Intent(c, cls).setAction(action).putExtra(key, val);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Home-screen widget: Adaptive / Custom / Off. */
    public static class NoiseWidget extends AppWidgetProvider {
        @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, noiseViews(c)); }
        @Override public void onReceive(Context c, Intent i) {
            if (ACT_NOISE.equals(i.getAction())) {
                Runnable finish = finishInTime(goAsync());
                applyNoise(c, i.getIntExtra(EXTRA_MODE, Remote.NOISE_OFF), Remote.WIDGET_ATTEMPTS, finish);
            } else super.onReceive(c, i);
        }
    }

    /** Home-screen widget: previous / next EQ preset. */
    public static class EqWidget extends AppWidgetProvider {
        @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, eqViews(c)); }
        @Override public void onReceive(Context c, Intent i) {
            if (ACT_EQ_STEP.equals(i.getAction())) {
                Runnable finish = finishInTime(goAsync());
                stepEq(c, i.getIntExtra(EXTRA_STEP, 1), Remote.WIDGET_ATTEMPTS, finish);
            } else super.onReceive(c, i);
        }
    }

    private Widgets() {}
}
