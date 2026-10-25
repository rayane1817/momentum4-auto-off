package be.ryane.gaiatest;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Design tokens (docs/design-spec-v2.md section 2), resolved from res/values(-night)/colors.xml
 * for the current configuration. The only place colours are read; no colour literals elsewhere.
 */
final class Tok {
    final int bg, surface, sunk, ink, ink2, rule, ruleStrong, onKey, copper, onCopper, pilot, pilotOnKey,
            danger, onDanger, transparent;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    /** Readouts: the system sans with tabular digits (set via TABULAR), not a monospace family:
     *  stock Android falls back to a wide typewriter mono (Nokia run, design spec 10.3). */
    static final Typeface MONO = REGULAR;
    static final String TABULAR = "tnum";
    /** Only the raw log keeps a real monospace face. */
    static final Typeface CODE = Typeface.MONOSPACE;

    Tok(Context c) {
        bg = c.getColor(R.color.bg);
        surface = c.getColor(R.color.surface);
        sunk = c.getColor(R.color.sunk);
        ink = c.getColor(R.color.ink);
        ink2 = c.getColor(R.color.ink2);
        rule = c.getColor(R.color.rule);
        ruleStrong = c.getColor(R.color.rule_strong);
        onKey = c.getColor(R.color.on_key);
        copper = c.getColor(R.color.copper);
        onCopper = c.getColor(R.color.on_copper);
        pilot = c.getColor(R.color.pilot);
        pilotOnKey = c.getColor(R.color.pilot_on_key);
        danger = c.getColor(R.color.danger);
        onDanger = c.getColor(R.color.on_danger);
        transparent = c.getColor(R.color.transparent);
    }

    /** colour with a new alpha (0..1), e.g. a pressed overlay or a 45 % switch track. */
    static int alpha(int color, float a) { return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF); }

    /** Mix a toward b by t (0..1): pressed states without new colour literals. */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }
}
