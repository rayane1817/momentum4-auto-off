package be.ryane.gaiatest;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Fader track (design spec 3.3): a thin sunk track, copper fill and optional ticks.
 * centered = fill grows from the middle (EQ: boost right, cut left); otherwise from the left.
 * SeekBar drives it through setLevel (0..10000).
 */
final class FaderTrack extends Drawable {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean centered;
    private final int ticks;          // number of intervals (0 = no ticks)
    private final float h, tickH;

    FaderTrack(Tok t, float density, boolean centered, int ticks) {
        this.centered = centered;
        this.ticks = ticks;
        h = 4 * density;
        tickH = 10 * density;
        track.setColor(t.sunk);
        fill.setColor(t.copper);
        tick.setColor(t.ruleStrong);
        tick.setStrokeWidth(density);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float cy = b.exactCenterY(), r = h / 2;
        c.drawRoundRect(new RectF(b.left, cy - r, b.right, cy + r), r, r, track);
        if (ticks > 0) {
            for (int i = 0; i <= ticks; i++) {
                float x = b.left + b.width() * i / (float) ticks;
                c.drawLine(x, cy - tickH / 2, x, cy + tickH / 2, tick);
            }
        }
        float pos = b.left + b.width() * getLevel() / 10000f;
        float from = centered ? b.exactCenterX() : b.left;
        c.drawRoundRect(new RectF(Math.min(from, pos), cy - r, Math.max(from, pos), cy + r), r, r, fill);
    }

    @Override protected boolean onLevelChange(int level) { invalidateSelf(); return true; }
    @Override public void setAlpha(int a) { track.setAlpha(a); fill.setAlpha(a); tick.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
