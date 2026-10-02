package org.chimeramc.client.core.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * The replay library's storage rules: the configurable cap, the 90% warning, and the auto-prune.
 *
 * <p>Pure, so the "which clip gets deleted" decision is a unit test rather than something a player
 * discovers when a clip disappears. The rule is deliberately conservative:
 * <ul>
 *   <li>Only <em>non-favorited</em> clips are ever pruned. A favorite is an explicit keep, so the
 *       library can exceed its cap rather than delete one; the UI reports that state instead.</li>
 *   <li>Pruning is oldest-first, so the newest recording survives.</li>
 *   <li>Pruning stops as soon as the incoming clip fits, so a small recording frees the minimum.</li>
 * </ul>
 */
public final class ReplayStoragePolicy {

    /** Default cap, per the spec. */
    public static final long DEFAULT_CAP_BYTES = 5L * 1024L * 1024L * 1024L;
    /** The warning threshold: 90% of the cap. */
    public static final float WARN_FRACTION = 0.90f;

    public static final long MIN_CAP_BYTES = 512L * 1024L * 1024L;
    public static final long MAX_CAP_BYTES = 100L * 1024L * 1024L * 1024L;

    /** How the current usage reads. */
    public enum Level {
        /** Under the warning threshold. */
        OK,
        /** At or above 90% of the cap, but not over. */
        WARN,
        /** Over the cap. Only reachable when favorites pin the library above it. */
        OVER
    }

    private ReplayStoragePolicy() {
    }

    public static Level level(long usedBytes, long capBytes) {
        if (capBytes <= 0L) return Level.OK;
        if (usedBytes > capBytes) return Level.OVER;
        if (usedBytes >= (long) (capBytes * WARN_FRACTION)) return Level.WARN;
        return Level.OK;
    }

    /** True once usage is at or above 90% of the cap. */
    public static boolean shouldWarn(long usedBytes, long capBytes) {
        return level(usedBytes, capBytes) != Level.OK;
    }

    /** Bytes still free under the cap; never negative. */
    public static long freeBytes(long usedBytes, long capBytes) {
        return Math.max(0L, capBytes - usedBytes);
    }

    /** Keeps a stored cap inside the supported range. */
    public static long clampCapBytes(long capBytes) {
        if (capBytes < MIN_CAP_BYTES) return MIN_CAP_BYTES;
        if (capBytes > MAX_CAP_BYTES) return MAX_CAP_BYTES;
        return capBytes;
    }

    /**
     * The clips to delete so that {@code usedBytes + incomingBytes} fits under the cap.
     *
     * <p>Returns the oldest non-favorited clips first, and only as many as are needed. An empty
     * list means nothing has to go. Favorites are skipped entirely; when the library is over the
     * cap purely because of favorites the result is empty and the caller must surface
     * {@link Level#OVER} rather than silently dropping a clip the player pinned.
     *
     * @param clips         the library, any order
     * @param usedBytes     current total on disk
     * @param capBytes      the configured cap
     * @param incomingBytes the size the new clip is expected to add
     */
    public static List<ReplayClip> selectPrunable(List<ReplayClip> clips, long usedBytes,
                                                  long capBytes, long incomingBytes) {
        List<ReplayClip> prunable = new ArrayList<>();
        if (clips == null || clips.isEmpty() || capBytes <= 0L) return prunable;
        long projected = usedBytes + Math.max(0L, incomingBytes);
        if (projected <= capBytes) return prunable;

        List<ReplayClip> candidates = new ArrayList<>();
        for (ReplayClip clip : clips) {
            if (clip != null && !clip.favorite()) candidates.add(clip);
        }
        candidates.sort((a, b) -> Long.compare(a.recordedAtMs(), b.recordedAtMs()));

        long freed = 0L;
        for (ReplayClip clip : candidates) {
            if (projected - freed <= capBytes) break;
            prunable.add(clip);
            freed += clip.sizeBytes();
        }
        return prunable;
    }
}
