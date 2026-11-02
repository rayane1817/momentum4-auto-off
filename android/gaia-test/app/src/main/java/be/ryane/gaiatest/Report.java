package be.ryane.gaiatest;

import java.util.regex.Pattern;

/**
 * Plain-text report for community testing (Device > Copy report). No Bluetooth addresses and no
 * names of other paired devices: only the phone model, Android version, the headphones' own
 * readouts and the tail of the app's logs.
 */
final class Report {
    private Report() {}

    private static final Pattern MAC = Pattern.compile("(?i)\\b[0-9a-f]{2}(?::[0-9a-f]{2}){5}\\b");

    /** Replaces every Bluetooth address with XX:XX:XX:XX:XX:XX. */
    static String redact(String s) {
        return s == null ? "" : MAC.matcher(s).replaceAll("XX:XX:XX:XX:XX:XX");
    }

    /** Last n lines of a log, redacted. */
    static String tail(String log, int n) {
        if (log == null || log.isEmpty()) return "(empty)";
        String[] lines = log.split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, lines.length - n); i < lines.length; i++) b.append(lines[i]).append('\n');
        return redact(b.toString().trim());
    }

    private static final Pattern STOCK_NAME = Pattern.compile("(?i)^MOMENTUM( 4)?( Wireless)?$");

    /**
     * Only the stock name goes into a report. A renamed headset ("Ryane's MOMENTUM 4") may hold a
     * personal name, and reports get pasted in public (audit F-11), so it is replaced.
     */
    static String safeName(String name) {
        if (name == null || name.isEmpty()) return "(none)";
        if (STOCK_NAME.matcher(name.trim()).matches()) return name.trim();
        return name.toUpperCase(java.util.Locale.ROOT).contains("MOMENTUM") ? "MOMENTUM (custom name hidden)" : "(not a MOMENTUM)";
    }

    /** Replaces each given device name wherever it appears in text with its safe label. Longest name first. */
    static String scrub(String text, String... names) {
        if (text == null) return "";
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String n : names) if (n != null && n.trim().length() >= 2 && !list.contains(n.trim())) list.add(n.trim());
        list.sort((a, b) -> b.length() - a.length());
        String out = text;
        for (String n : list) out = out.replace(n, safeName(n));
        return out;
    }
}
