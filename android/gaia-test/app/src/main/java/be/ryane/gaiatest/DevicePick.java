package be.ryane.gaiatest;

import java.util.List;
import java.util.Locale;

/** Which paired device the Device page picks (pure, so it is JVM-testable). */
final class DevicePick {
    private DevicePick() {}

    static boolean looksLikeMomentum(String name) {
        return name != null && name.toUpperCase(Locale.ROOT).contains("MOMENTUM");
    }

    /**
     * Index to select after a (re)scan: the device with address {@code keep} (current choice or
     * the remembered one); with nothing kept, a MOMENTUM; otherwise the first device. -1 when empty.
     */
    static int preselect(List<String> addrs, List<String> names, String keep) {
        if (addrs.isEmpty()) return -1;
        String k = keep == null ? "" : keep;
        int pick = 0;
        for (int i = 0; i < addrs.size(); i++) {
            if (addrs.get(i).equals(k) || (k.isEmpty() && looksLikeMomentum(names.get(i)))) pick = i;
        }
        return pick;
    }

    /** Title of the picker row: the chosen name, or a hint when nothing is paired. */
    static String rowTitle(String chosenName, int count) {
        if (count == 0) return "No paired headphones";
        return chosenName == null ? "Choose the headphones" : chosenName;
    }
}
