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

    /** Device name only when it is a MOMENTUM; anything else may be a personal name. */
    static String safeName(String name) {
        if (name == null || name.isEmpty()) return "(none)";
        return name.toUpperCase(java.util.Locale.ROOT).contains("MOMENTUM") ? name : "(not a MOMENTUM)";
    }
}
