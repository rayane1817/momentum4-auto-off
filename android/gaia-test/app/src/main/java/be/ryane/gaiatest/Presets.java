package be.ryane.gaiatest;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** EQ presets: Smart Control's 8 built-ins plus 5 user slots, stored on the phone (the headset only keeps the curve). */
final class Presets {
    static final int CUSTOM_SLOTS = 5;
    static final String[] BUILTIN_NAMES = {"Neutral", "Rock", "Pop", "Dance", "Hip-Hop", "Classical", "Movie", "Jazz"};
    static final int[][] BUILTIN = {                     // dB x 10 per band (63 Hz .. 8 kHz), verified vs capture 2
            {0, 0, 0, 0, 0}, {0, 20, 25, 15, -20}, {0, -25, 0, 25, 0}, {35, 20, -15, 15, 30},
            {30, 15, -15, 0, -15}, {-20, -15, 0, 35, 40}, {0, 0, 20, 20, -20}, {-32, 0, 22, 22, 0}};

    static final class Preset {
        final String name; final int[] gains; final int slot;   // slot -1 = built-in, 0..4 = custom
        Preset(String name, int[] gains, int slot) { this.name = name; this.gains = gains; this.slot = slot; }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("m4", Context.MODE_PRIVATE); }

    /** Built-ins first, then the filled custom slots. */
    static List<Preset> all(Context c) {
        List<Preset> l = new ArrayList<>();
        for (int i = 0; i < BUILTIN.length; i++) l.add(new Preset(BUILTIN_NAMES[i], BUILTIN[i], -1));
        for (int s = 0; s < CUSTOM_SLOTS; s++) { Preset p = custom(c, s); if (p != null) l.add(p); }
        return l;
    }

    static Preset custom(Context c, int slot) {
        String v = prefs(c).getString("cp" + slot, null);
        if (v == null) return null;
        try {
            int bar = v.indexOf('|');
            String[] g = v.substring(bar + 1).split(",");
            int[] gains = new int[5];
            for (int i = 0; i < 5; i++) gains[i] = clamp(Integer.parseInt(g[i].trim()));
            return new Preset(v.substring(0, bar), gains, slot);
        } catch (RuntimeException e) { return null; }
    }

    static void saveCustom(Context c, int slot, String name, int[] gains) {
        String n = name.replace("|", " ").trim();
        if (n.isEmpty()) n = "Custom " + (slot + 1);
        StringBuilder s = new StringBuilder(n).append('|');
        for (int i = 0; i < 5; i++) s.append(i == 0 ? "" : ",").append(clamp(gains[i]));
        prefs(c).edit().putString("cp" + slot, s.toString()).apply();
    }

    static void deleteCustom(Context c, int slot) { prefs(c).edit().remove("cp" + slot).apply(); }

    /** Index into all() whose curve matches exactly, or -1. */
    static int matching(Context c, int[] gains) {
        List<Preset> l = all(c);
        for (int i = 0; i < l.size(); i++) if (Arrays.equals(l.get(i).gains, gains)) return i;
        return -1;
    }

    /** The five 1001 writes that apply a curve, as Smart Control does. */
    static List<byte[]> frames(int[] gains) {
        List<byte[]> f = new ArrayList<>();
        for (int b = 0; b < 5; b++) f.add(Gaia.build(Gaia.SENNHEISER, 0x1001, (byte) b, (byte) clamp(gains[b])));
        return f;
    }

    static int clamp(int g) { return Math.max(-60, Math.min(60, g)); }   // UI range -6.0..+6.0 dB

    private Presets() {}
}
