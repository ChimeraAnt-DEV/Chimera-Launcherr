package org.chimeramc.client.core.replay;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The text sidecar written beside each clip.
 *
 * <p>A flat {@code key=value} file, parsed and written by hand so both directions are JVM-testable
 * and a corrupt sidecar degrades to "unknown metadata" rather than an unreadable library. The
 * format is intentionally not JSON: it is tiny, human-inspectable, and has no dependency.
 *
 * <p>Unknown keys are preserved on a rewrite so a newer version's field is not dropped by an older
 * one that only meant to flip the favorite flag.
 */
public final class ReplaySidecar {

    public static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_RECORDED_AT = "recordedAt";
    private static final String KEY_WORLD = "world";
    private static final String KEY_GAME_VERSION = "gameVersion";
    private static final String KEY_GAME_MODE = "gameMode";
    private static final String KEY_FAVORITE = "favorite";
    private static final String KEY_HIGHLIGHT = "highlight";

    private final Map<String, String> values = new LinkedHashMap<>();

    public static ReplaySidecar parse(String text) {
        ReplaySidecar sidecar = new ReplaySidecar();
        if (text == null) return sidecar;
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            sidecar.values.put(key, value);
        }
        return sidecar;
    }

    public static ReplaySidecar of(long recordedAtMs, String world, String gameVersion,
                                   String gameMode) {
        ReplaySidecar sidecar = new ReplaySidecar();
        sidecar.values.put(KEY_VERSION, String.valueOf(VERSION));
        sidecar.values.put(KEY_RECORDED_AT, String.valueOf(recordedAtMs));
        sidecar.values.put(KEY_WORLD, world == null ? "" : world);
        sidecar.values.put(KEY_GAME_VERSION, gameVersion == null ? "" : gameVersion);
        sidecar.values.put(KEY_GAME_MODE, gameMode == null ? "" : gameMode);
        sidecar.values.put(KEY_FAVORITE, "0");
        sidecar.values.put(KEY_HIGHLIGHT, "0");
        return sidecar;
    }

    public ReplaySidecar set(String key, String value) {
        if (key != null && !key.isEmpty()) values.put(key, value == null ? "" : value);
        return this;
    }

    public String get(String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    public long recordedAtMs() {
        return parseLong(get(KEY_RECORDED_AT, "0"), 0L);
    }

    public String world() {
        return get(KEY_WORLD, "");
    }

    public String gameVersion() {
        return get(KEY_GAME_VERSION, "");
    }

    public String gameMode() {
        return get(KEY_GAME_MODE, "");
    }

    public boolean favorite() {
        return "1".equals(get(KEY_FAVORITE, "0"));
    }

    public boolean highlight() {
        return "1".equals(get(KEY_HIGHLIGHT, "0"));
    }

    public ReplaySidecar setFavorite(boolean favorite) {
        return set(KEY_FAVORITE, favorite ? "1" : "0");
    }

    public ReplaySidecar setHighlight(boolean highlight) {
        return set(KEY_HIGHLIGHT, highlight ? "1" : "0");
    }

    public String serialize() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return builder.toString();
    }

    /** Applies this sidecar's metadata onto a clip descriptor. */
    public void applyTo(ReplayClip clip) {
        if (clip == null) return;
        clip.setFavorite(favorite());
        clip.setHighlight(highlight());
    }

    static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
