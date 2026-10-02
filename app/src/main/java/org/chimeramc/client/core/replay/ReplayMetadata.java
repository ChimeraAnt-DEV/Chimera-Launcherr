package org.chimeramc.client.core.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The metadata burned into a recording.
 *
 * <p>Pure, so the readout the player sees burned into the clip is the readout the tests pin. The
 * rule from the rest of the Mod Menu holds here: a value with no source renders a dash, never a
 * plausible-looking {@code 0}.
 *
 * <p>Honest scope, matching the repo's other counters:
 * <ul>
 *   <li><b>FPS</b> — real, from the inbuilt FPS mod.</li>
 *   <li><b>Timer</b> — real, the recording's own elapsed time.</li>
 *   <li><b>Combo</b> — the launcher's own hit-timing counter (the player's landed-in-window
 *       attacks), not the game's server-side combo. The scope note says so.</li>
 *   <li><b>Ping / Kills</b> — there is no latency or kill source in this build, so they render a
 *       dash rather than an invented number.</li>
 * </ul>
 */
public final class ReplayMetadata {

    public static final String NO_READING = "—";

    /** The fields the player can burn in, in display order. */
    public enum Field {
        FPS,
        PING,
        TIMER,
        KILLS,
        COMBO
    }

    private ReplayMetadata() {
    }

    /** One reading, or {@link #NO_READING} when the source produced nothing. */
    public static String value(Field field, int fps, int pingMs, long elapsedMs, int kills,
                               int combo) {
        if (field == null) return NO_READING;
        switch (field) {
            case FPS:
                return fps > 0 ? fps + " FPS" : NO_READING;
            case PING:
                return pingMs > 0 ? pingMs + " ms" : NO_READING;
            case TIMER:
                return ReplayFormat.duration(Math.max(0L, elapsedMs));
            case KILLS:
                return kills >= 0 ? String.valueOf(kills) : NO_READING;
            case COMBO:
                return combo > 0 ? combo + "x" : NO_READING;
            default:
                return NO_READING;
        }
    }

    /** The burn-in line for a set of enabled fields, e.g. {@code 60 FPS · — · 0:42 · — · 3x}. */
    public static String line(List<Field> fields, int fps, int pingMs, long elapsedMs, int kills,
                              int combo) {
        if (fields == null || fields.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Field field : fields) {
            if (field == null) continue;
            parts.add(value(field, fps, pingMs, elapsedMs, kills, combo));
        }
        return String.join(" \u00b7 ", parts);
    }

    /** The default field set, per the spec. */
    public static List<Field> defaultFields() {
        List<Field> fields = new ArrayList<>();
        fields.add(Field.FPS);
        fields.add(Field.PING);
        fields.add(Field.TIMER);
        fields.add(Field.KILLS);
        fields.add(Field.COMBO);
        return fields;
    }

    /** The file-name stamp for a clip: {@code 2026-09-29_14-23-07}. */
    public static String fileStamp(long epochMs) {
        long seconds = epochMs / 1000L;
        long days = seconds / 86400L;
        long timeOfDay = seconds % 86400L;
        long[] ymd = ReplayClip.civilFromDays(days);
        return String.format(Locale.US, "%04d-%02d-%02d_%02d-%02d-%02d",
                ymd[0], ymd[1], ymd[2],
                timeOfDay / 3600L, (timeOfDay % 3600L) / 60L, timeOfDay % 60L);
    }
}
