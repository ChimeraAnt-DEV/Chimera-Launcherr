package org.chimeramc.client.core.replay;

/**
 * The Tier 3 trim rules: turning a chosen start/end window into a saved clip.
 *
 * <p>Pure, so the clamping is a unit test. A trim never produces a zero-length or inverted clip,
 * and it never asks the encoder for a window outside the source: the player can drag a handle past
 * the other one, and the result still has to be a valid export.
 */
public final class ReplayTrim {

    /** Shortest clip a trim will produce; below this the export is not worth keeping. */
    public static final long MIN_LENGTH_MS = 1_000L;

    private final long startMs;
    private final long endMs;

    public ReplayTrim(long startMs, long endMs, long sourceDurationMs) {
        long duration = Math.max(0L, sourceDurationMs);
        long s = Math.max(0L, Math.min(startMs, duration));
        long e = Math.max(0L, Math.min(endMs, duration));
        if (e < s) {
            long swap = s;
            s = e;
            e = swap;
        }
        // A window shorter than the minimum is widened to the minimum where the source allows,
        // anchored at the start so the player's chosen entry point is preserved.
        if (e - s < MIN_LENGTH_MS) {
            e = Math.min(duration, s + MIN_LENGTH_MS);
            if (e - s < MIN_LENGTH_MS) s = Math.max(0L, e - MIN_LENGTH_MS);
        }
        this.startMs = s;
        this.endMs = e;
    }

    public long startMs() {
        return startMs;
    }

    public long endMs() {
        return endMs;
    }

    public long lengthMs() {
        return endMs - startMs;
    }

    /** True when the source was long enough to produce a valid window at all. */
    public boolean isExportable() {
        return lengthMs() >= MIN_LENGTH_MS;
    }

    /** The fraction of the source the window covers, 0..1, for the scrubber's filled track. */
    public float startFraction(long sourceDurationMs) {
        if (sourceDurationMs <= 0L) return 0f;
        return Math.max(0f, Math.min(1f, startMs / (float) sourceDurationMs));
    }

    public float endFraction(long sourceDurationMs) {
        if (sourceDurationMs <= 0L) return 1f;
        return Math.max(0f, Math.min(1f, endMs / (float) sourceDurationMs));
    }

    /** The exported file name for a source clip: {@code name_trimmed.mp4}. */
    public static String trimmedName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return "clip_trimmed.mp4";
        int dot = sourceName.lastIndexOf('.');
        String base = dot > 0 ? sourceName.substring(0, dot) : sourceName;
        return base + "_trimmed.mp4";
    }
}
