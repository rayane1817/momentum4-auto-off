package be.ryane.gaiatest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Design v3 signature (docs/design-spec-v3.md 3.4): digits and a few caps on our own 5 x 7 dot grid.
 * Lit dots in ink, unlit dots faint (the grid stays visible like an LED panel). When "live"
 * (value read from the headphones / guard armed) the last lit column of the last glyph is copper.
 * Our own glyph table: no font files, no third-party dot-matrix faces.
 */
final class DotNumerals extends View {
    private static final Map<Character, String[]> G = new HashMap<>();
    static {
        g('0', "01110", "10001", "10011", "10101", "11001", "10001", "01110");
        g('1', "00100", "01100", "00100", "00100", "00100", "00100", "01110");
        g('2', "01110", "10001", "00001", "00010", "00100", "01000", "11111");
        g('3', "11110", "00001", "00001", "01110", "00001", "00001", "11110");
        g('4', "00010", "00110", "01010", "10010", "11111", "00010", "00010");
        g('5', "11111", "10000", "11110", "00001", "00001", "10001", "01110");
        g('6', "00110", "01000", "10000", "11110", "10001", "10001", "01110");
        g('7', "11111", "00001", "00010", "00100", "01000", "01000", "01000");
        g('8', "01110", "10001", "10001", "01110", "10001", "10001", "01110");
        g('9', "01110", "10001", "10001", "01111", "00001", "00010", "01100");
        g('%', "11001", "11010", "00010", "00100", "01000", "01011", "10011");
        g(':', "00000", "00100", "00100", "00000", "00100", "00100", "00000");
        g('-', "00000", "00000", "00000", "11111", "00000", "00000", "00000");
        g('S', "01111", "10000", "10000", "01110", "00001", "00001", "11110");
        g('E', "11111", "10000", "10000", "11110", "10000", "10000", "11111");
        g('N', "10001", "11001", "10101", "10011", "10001", "10001", "10001");
        g('T', "11111", "00100", "00100", "00100", "00100", "00100", "00100");
        g('O', "01110", "10001", "10001", "10001", "10001", "10001", "01110");
        g('F', "11111", "10000", "10000", "11110", "10000", "10000", "10000");
        g(' ', "00000", "00000", "00000", "00000", "00000", "00000", "00000");
    }
    private static void g(char c, String... rows) { G.put(c, rows); }

    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint(Paint.ANTI_ALIAS_FLAG),
            hot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dot, gap;
    private String text = "--";
    private boolean live;

    DotNumerals(Context c, Tok t, float dotDp, float gapDp) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        dot = dotDp * d;
        gap = gapDp * d;
        lit.setColor(t.ink);
        dim.setColor(Tok.alpha(t.ink2, 0.25f));
        hot.setColor(t.copper);
    }

    /** text: shown on the grid; spoken: what TalkBack reads ("Battery 62 percent"). */
    void set(String text, boolean live, String spoken) {
        this.text = text == null || text.isEmpty() ? "--" : text.toUpperCase(java.util.Locale.ROOT);
        this.live = live;
        setContentDescription(spoken);
        requestLayout();
        invalidate();
    }

    private float pitch() { return dot + gap; }

    @Override protected void onMeasure(int w, int h) {
        int n = text.length();
        float width = n * 5 * pitch() + (n - 1) * pitch() - gap;
        float height = 7 * pitch() - gap;
        setMeasuredDimension(resolveSize(Math.round(width) + getPaddingLeft() + getPaddingRight(), w),
                resolveSize(Math.round(height) + getPaddingTop() + getPaddingBottom(), h));
    }

    @Override protected void onDraw(Canvas c) {
        float r = dot / 2, x0 = getPaddingLeft(), y0 = getPaddingTop();
        int last = text.length() - 1;
        for (int i = 0; i < text.length(); i++) {
            String[] rows = G.containsKey(text.charAt(i)) ? G.get(text.charAt(i)) : G.get(' ');
            int hotCol = -1;
            if (live && i == last) {
                for (int col = 4; col >= 0 && hotCol < 0; col--)
                    for (String row : rows) if (row.charAt(col) == '1') { hotCol = col; break; }
            }
            float gx = x0 + i * 6 * pitch();
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    boolean on = rows[row].charAt(col) == '1';
                    Paint p = !on ? dim : col == hotCol ? hot : lit;
                    c.drawCircle(gx + col * pitch() + r, y0 + row * pitch() + r, r, p);
                }
            }
        }
    }
}
