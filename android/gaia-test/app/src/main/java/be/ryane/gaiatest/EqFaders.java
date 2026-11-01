package be.ryane.gaiatest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Five vertical Poweramp-style EQ faders in one view (design v4 6.1): thin track, tick dashes at
 * +6 / 0 / -6 dB, a tall cap with a grip, and a glowing copper fill from the bottom up to the cap.
 * Drag one fader or sweep across several to draw a curve; a double-tap resets that band to 0 dB.
 * Values update live; frames go out only on release, one per changed band (EqGesture).
 */
final class EqFaders extends View {
    interface Listener {
        void live(int[] gains);              // while dragging: labels, curve
        void commit(List<int[]> changes);    // on release: [band, gain] per changed band, band order
    }

    private static final String[] HZ = {"63", "250", "1K", "4K", "8K"};
    private static final String[] HZ_SPOKEN = {"63 hertz", "250 hertz", "1 kilohertz", "4 kilohertz", "8 kilohertz"};
    private final int[] gains = new int[5], startGains = new int[5];
    private final EqGesture gesture = new EqGesture();
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), cap = new Paint(Paint.ANTI_ALIAS_FLAG),
            edge = new Paint(Paint.ANTI_ALIAS_FLAG), grip = new Paint(Paint.ANTI_ALIAS_FLAG),
            hz = new Paint(Paint.ANTI_ALIAS_FLAG), val = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final int slop;
    private final RectF r = new RectF();
    private Listener listener;
    private boolean dragging, moved;
    private float downX, downY;
    private int lastTapBand = -1;
    private long lastTapAt;

    EqFaders(Context c, Tok t) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        track.setColor(t.ruleStrong); track.setStrokeWidth(d);
        tick.setColor(t.ink2); tick.setStrokeWidth(d);
        fill.setColor(t.copper); fill.setStrokeWidth(3 * d); fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setShadowLayer(10 * d, 0, 0, t.glow);
        cap.setColor(t.faderCap);
        edge.setColor(t.faderEdge); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(1.5f * d);
        grip.setColor(t.ink); grip.setStrokeWidth(3 * d); grip.setStrokeCap(Paint.Cap.ROUND);
        hz.setColor(t.ink); hz.setTextSize(15 * c.getResources().getDisplayMetrics().scaledDensity);
        hz.setTypeface(android.graphics.Typeface.create(Tok.REGULAR, android.graphics.Typeface.BOLD));
        hz.setTextAlign(Paint.Align.CENTER);
        val.setColor(t.ink2); val.setTextSize(13 * c.getResources().getDisplayMetrics().scaledDensity);
        val.setTypeface(Tok.MONO); val.setTextAlign(Paint.Align.CENTER);
        if (Build.VERSION.SDK_INT >= 21) val.setFontFeatureSettings(Tok.TABULAR);
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);   // glow on lines needs it before API 28
        setFocusable(true);
        describe();
    }

    void setListener(Listener l) { listener = l; }

    /** From the headset or a preset. Ignored mid-drag so the finger keeps control. */
    void setGains(int[] g) {
        if (dragging) return;
        for (int i = 0; i < 5; i++) gains[i] = EqGesture.clamp(g[i]);
        describe();
        invalidate();
    }

    private float trackTop() { return 34 * d; }
    private float trackH() { return 256 * d; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Math.round(340 * d));
    }

    @Override protected void onDraw(Canvas c) {
        float colW = getWidth() / 5f, top = trackTop(), bottom = top + trackH(), mid = (top + bottom) / 2;
        for (int i = 0; i < 5; i++) {
            float cx = colW * (i + 0.5f);
            c.drawLine(cx, top, cx, bottom, track);
            for (float y : new float[]{top, mid, bottom}) {
                c.drawLine(cx - 28 * d, y, cx - 22 * d, y, tick);
                c.drawLine(cx + 22 * d, y, cx + 28 * d, y, tick);
            }
            float cy = mid - (trackH() / 2) * gains[i] / (float) EqGesture.MAX;
            c.drawLine(cx, Math.min(bottom, cy + 30 * d), cx, bottom, fill);
            r.set(cx - 18 * d, cy - 30 * d, cx + 18 * d, cy + 30 * d);
            c.drawRoundRect(r, 15 * d, 15 * d, cap);
            c.drawRoundRect(r, 15 * d, 15 * d, edge);
            c.drawLine(cx - 7 * d, cy, cx + 7 * d, cy, grip);
            c.drawText(HZ[i], cx, bottom + 34 * d, hz);
            c.drawText(format(gains[i]), cx, bottom + 50 * d, val);
        }
    }

    static String format(int g) { return String.format(Locale.ROOT, "%+.1f", g / 10.0).replace("+0.0", "0.0").replace("-0.0", "0.0"); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gesture.begin(gains);
                System.arraycopy(gains, 0, startGains, 0, 5);
                dragging = true; moved = false;
                downX = e.getX(); downY = e.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) moved = true;
                if (moved) {
                    gesture.set(EqGesture.bandAt(e.getX(), getWidth()), EqGesture.gainAt(e.getY(), trackTop(), trackH()));
                    System.arraycopy(gesture.gains(), 0, gains, 0, 5);
                    invalidate();
                    if (listener != null) listener.live(gains.clone());
                }
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                if (moved) {
                    commit();
                } else {
                    int band = EqGesture.bandAt(e.getX(), getWidth());
                    long now = e.getEventTime();
                    if (band == lastTapBand && now - lastTapAt < 350) {          // double-tap: band to 0 dB
                        gesture.begin(gains);
                        gesture.set(band, 0);
                        System.arraycopy(gesture.gains(), 0, gains, 0, 5);
                        invalidate();
                        commit();
                        lastTapBand = -1;
                    } else {
                        lastTapBand = band; lastTapAt = now;
                        performClick();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                System.arraycopy(startGains, 0, gains, 0, 5);   // nothing sent: show the headset's values again
                setGainsFromStart();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void setGainsFromStart() { describe(); invalidate(); if (listener != null) listener.live(gains.clone()); }

    private void commit() {
        describe();
        List<int[]> ch = gesture.changes();
        if (!ch.isEmpty() && listener != null) listener.commit(ch);
    }

    @Override public boolean performClick() { return super.performClick(); }

    /** TalkBack reads all five bands; the Bands values also show in the curve strip and labels. */
    private void describe() {
        StringBuilder s = new StringBuilder("Equaliser faders. ");
        for (int i = 0; i < 5; i++) s.append(HZ_SPOKEN[i]).append(' ').append(format(gains[i])).append(" dB. ");
        setContentDescription(s.toString().trim());
    }
}
