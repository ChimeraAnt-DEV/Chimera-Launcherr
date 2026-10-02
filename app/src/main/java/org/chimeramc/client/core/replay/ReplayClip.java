package org.chimeramc.client.core.replay;

import java.io.File;
import java.util.Locale;

/**
 * One recorded clip in the Replay library.
 *
 * <p>An immutable descriptor: the file on disk plus the metadata the library needs to sort, filter
 * and render a card. The metadata that the recorder knows but the file does not (game version,
 * world, game mode) lives in a sidecar written at record time, so a scan of the replays folder can
 * rebuild a {@code ReplayClip} without the recorder being alive.
 *
 * <p>Not Android-free by accident: it takes a {@link File}, but holds no Android types, so the
 * library rules over it are JVM tests.
 */
public final class ReplayClip {

    private final File file;
    private final long durationMs;
    private final long sizeBytes;
    private final long recordedAtMs;
    private final String world;
    private final String gameVersion;
    private final String gameMode;

    private boolean favorite;
    private boolean highlight;

    public ReplayClip(File file, long durationMs, long sizeBytes, long recordedAtMs,
                      String world, String gameVersion, String gameMode) {
        this.file = file;
        this.durationMs = Math.max(0L, durationMs);
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.recordedAtMs = Math.max(0L, recordedAtMs);
        this.world = world == null ? "" : world;
        this.gameVersion = gameVersion == null ? "" : gameVersion;
        this.gameMode = gameMode == null ? "" : gameMode;
    }

    public File file() {
        return file;
    }

    public String name() {
        return file == null ? "" : file.getName();
    }

    /** The clip's name without the {@code .mp4} extension, for display. */
    public String displayName() {
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public long durationMs() {
        return durationMs;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long recordedAtMs() {
        return recordedAtMs;
    }

    public String world() {
        return world;
    }

    public String gameVersion() {
        return gameVersion;
    }

    public String gameMode() {
        return gameMode;
    }

    public boolean favorite() {
        return favorite;
    }

    public boolean highlight() {
        return highlight;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void setHighlight(boolean highlight) {
        this.highlight = highlight;
    }

    /** A copy pointing at the same file but with different metadata, e.g. after a rename. */
    public ReplayClip withFile(File newFile) {
        ReplayClip copy = new ReplayClip(newFile, durationMs, sizeBytes, recordedAtMs,
                world, gameVersion, gameMode);
        copy.favorite = favorite;
        copy.highlight = highlight;
        return copy;
    }

    /**
     * The date shown on a card, as {@code 2026-09-29 14:23}. Formatted from the raw millis rather
     * than a platform date formatter so the value is stable across locales and testable.
     */
    public String dateLabel() {
        long seconds = recordedAtMs / 1000L;
        long days = seconds / 86400L;
        long timeOfDay = seconds % 86400L;
        long[] ymd = civilFromDays(days);
        long hour = timeOfDay / 3600L;
        long minute = (timeOfDay % 3600L) / 60L;
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d",
                ymd[0], ymd[1], ymd[2], hour, minute);
    }

    /**
     * Converts days-since-epoch to a civil date, Howard Hinnant's algorithm.
     *
     * <p>Done by hand because the library rules need a deterministic string on the JVM and a
     * {@code java.time} call would need the desugaring the rest of the module does not use.
     */
    static long[] civilFromDays(long z) {
        z += 719468L;
        long era = (z >= 0 ? z : z - 146096L) / 146097L;
        long doe = z - era * 146097L;
        long yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L;
        long y = yoe + era * 400L;
        long doy = doe - (365L * yoe + yoe / 4L - yoe / 100L);
        long mp = (5L * doy + 2L) / 153L;
        long d = doy - (153L * mp + 2L) / 5L + 1L;
        long m = mp < 10L ? mp + 3L : mp - 9L;
        return new long[]{m <= 2L ? y + 1L : y, m, d};
    }
}
