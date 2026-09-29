package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Formats the Mod Menu's live stats strip (FPS / ping / battery).
 *
 * <p>Pure so the "no reading yet" states are unit-testable and cannot drift into a plausible
 * looking number. A stat with no reading renders an em dash, never {@code 0} — a "0 FPS" or a
 * "0 ms" would read as a real measurement and send the player chasing a problem that is not there.
 */
public final class ModStatsFormatter {

    /** The text for a stat that has no reading, used for all three. */
    public static final String NO_READING = "—";

    private ModStatsFormatter() {
    }

    /** FPS text, or the em dash when the native counter has not produced a value yet. */
    public static String fps(int fps) {
        return fps <= 0 ? NO_READING : fps + " FPS";
    }

    /** Ping text. There is no latency source wired yet, so an unknown reads as the em dash. */
    public static String ping(int milliseconds) {
        return milliseconds <= 0 ? NO_READING : milliseconds + " ms";
    }

    /**
     * Battery text with a charging marker.
     *
     * @param percent 0..100, or a negative value when the reading is unavailable
     * @param charging whether the device is on external power
     */
    public static String battery(int percent, boolean charging) {
        if (percent < 0) return NO_READING;
        int clamped = Math.min(100, percent);
        return charging ? clamped + "%+" : clamped + "%";
    }
}
