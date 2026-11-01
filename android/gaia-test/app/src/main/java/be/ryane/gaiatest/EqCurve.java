package be.ryane.gaiatest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Read-only EQ summary (design v4 6.2): the smooth curve through the 5 gains in copper, drawn over
 * a 34 x 7 dot matrix whose dots under the curve are lit. Also draws the small preset thumbnails.
 */
final class EqCurve extends View {
    private static final int COLS = 34, ROWS = 7;
    private final int[] gains = new int[5];          // dB x 10
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG), lit = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    EqCurve(Context c, Tok t) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        off.setColor(t.rule);
        lit.setColor(t.matrixLit);
        line.setColor(t.copper); line.setStrokeWidth(2 * density); line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("EQ curve");
    }

    void setGains(int[] g) { System.arraycopy(g, 0, gains, 0, 5); invalidate(); }

    /** Curve height at x in [0,1], as a fraction of +/-6 dB (-1..1). Same smoothing as the drawn path. */
    static float at(int[] g, float x) {
        float pos = Math.max(0, Math.min(1, x)) * 4;
        int i = Math.min(3, (int) pos);
        float t = pos - i, s = t * t * (3 - 2 * t);          // smoothstep = horizontal tangents at the bands
        float a = clamp(g[i]) / 60f, b = clamp(g[i + 1]) / 60f;
        return a + (b - a) * s;
    }

    private static int clamp(int v) { return Math.max(-60, Math.min(60, v)); }

    static Path path(int[] g, float left, float top, float w, float h) {
        Path p = new Path();
        int steps = 48;
        for (int k = 0; k <= steps; k++) {
            float x = k / (float) steps;
            float y = top + h / 2 - h / 2 * at(g, x);
            if (k == 0) p.moveTo(left + x * w, y); else p.lineTo(left + x * w, y);
        }
        return p;
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 10 * density;
        float iw = w - 2 * pad, ih = h - 2 * pad;
        float r = Math.min(1.8f * density, Math.min(iw / COLS, ih / ROWS) / 2.4f);
        for (int col = 0; col < COLS; col++) {
            float fx = (col + 0.5f) / COLS;
            float curveY = pad + ih / 2 - ih / 2 * at(gains, fx);
            for (int row = 0; row < ROWS; row++) {
                float y = pad + (row + 0.5f) * ih / ROWS;
                c.drawCircle(pad + fx * iw, y, r, y >= curveY ? lit : off);
            }
        }
        c.drawPath(path(gains, pad, pad, iw, ih), line);
    }

    /** Small curve picture for preset rows and the EQ widget: copper line over a hairline, transparent background. */
    static Bitmap thumbnail(Context ctx, Tok t, int[] g, int wDp, int hDp) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int w = Math.max(1, Math.round(wDp * d)), h = Math.max(1, Math.round(hDp * d));
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
        hair.setColor(t.rule); hair.setStrokeWidth(d);
        c.drawLine(0, h / 2f, w, h / 2f, hair);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(t.copper); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f * d);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        float pad = 2 * d;
        c.drawPath(path(g, pad, pad, w - 2 * pad, h - 2 * pad), p);
        return bm;
    }
}
