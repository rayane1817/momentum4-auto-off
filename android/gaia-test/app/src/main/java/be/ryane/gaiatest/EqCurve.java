package be.ryane.gaiatest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Read-only EQ summary: the 5 gains as a smooth line over a +/-6 dB grid (design spec 3.3). */
final class EqCurve extends View {
    private final int[] gains = new int[5];          // dB x 10
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), zero = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    EqCurve(Context c, Tok t) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        grid.setColor(t.rule); grid.setStrokeWidth(density);
        zero.setColor(t.ruleStrong); zero.setStrokeWidth(density);
        line.setColor(t.copper); line.setStrokeWidth(2 * density); line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
        dot.setColor(t.copper);
        setContentDescription("EQ curve");
    }

    void setGains(int[] g) { System.arraycopy(g, 0, gains, 0, 5); invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 6 * density;
        float top = pad, bottom = h - pad, mid = (top + bottom) / 2;
        for (int db = -6; db <= 6; db += 3) {
            float y = mid - (bottom - top) / 2 * db / 6f;
            c.drawLine(0, y, w, y, db == 0 ? zero : grid);
        }
        float[] xs = new float[5], ys = new float[5];
        for (int i = 0; i < 5; i++) {
            xs[i] = pad + (w - 2 * pad) * i / 4f;
            ys[i] = mid - (bottom - top) / 2 * Math.max(-60, Math.min(60, gains[i])) / 60f;
        }
        Path p = new Path();
        p.moveTo(xs[0], ys[0]);
        for (int i = 0; i < 4; i++) {                   // smooth: horizontal-tangent cubic per segment
            float cx = (xs[i] + xs[i + 1]) / 2;
            p.cubicTo(cx, ys[i], cx, ys[i + 1], xs[i + 1], ys[i + 1]);
        }
        c.drawPath(p, line);
        for (int i = 0; i < 5; i++) c.drawCircle(xs[i], ys[i], 2.5f * density, dot);
    }
}
