package org.chimeramc.client.core.replay;

import java.util.Locale;

/**
 * Text formatting for the Replay tab.
 *
 * <p>Pure and locale-stable, so a card's duration and size read the same on every device and the
 * rules are JVM tests. A value with no reading renders a dash, never {@code 0:00} or {@code 0 B},
 * matching the Mod Menu stats rule: a zero reads as a real measurement.
 */
public final class ReplayFormat {

    public static final String NO_READING = "—";

    /** Storage sizes stop at GB; a replay library never legitimately reaches TB. */
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    private ReplayFormat() {
    }

    /** Clock text: {@code 0:07}, {@code 1:42}, {@code 12:03}. Negative reads as a dash. */
    public static String duration(long millis) {
        if (millis < 0L) return NO_READING;
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /** Size text: {@code 812 KB}, {@code 46.2 MB}, {@code 1.4 GB}. */
    public static String size(long bytes) {
        if (bytes < 0L) return NO_READING;
        if (bytes >= GB) return String.format(Locale.US, "%.1f GB", bytes / (double) GB);
        if (bytes >= MB) return String.format(Locale.US, "%.1f MB", bytes / (double) MB);
        if (bytes >= KB) return String.format(Locale.US, "%d KB", bytes / KB);
        return bytes + " B";
    }

    /** Percentage for the storage bar, clamped to 0..100. A zero cap has no percentage. */
    public static int storagePercent(long usedBytes, long capBytes) {
        if (capBytes <= 0L) return 0;
        long percent = Math.round(usedBytes * 100.0 / capBytes);
        return (int) Math.max(0L, Math.min(100L, percent));
    }

    /** Clip-length limit text: {@code 5 min}, {@code 30 min}. */
    public static String minutes(int minutes) {
        return minutes + " min";
    }
}
