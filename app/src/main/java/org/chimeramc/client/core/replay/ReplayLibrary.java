package org.chimeramc.client.core.replay;

/**
 * The replay library's sort and filter rules.
 *
 * <p>Pure, so the ordering the UI shows is the ordering the tests pin. Favorites are a filter, not
 * a sort key: a favorited clip stays in date order where the player expects it, and the Favorites
 * filter is what narrows the list.
 */
public final class ReplayLibrary {

    /** How the grid is ordered. */
    public enum Sort {
        DATE_NEWEST,
        DATE_OLDEST,
        DURATION_LONGEST,
        DURATION_SHORTEST,
        SIZE_LARGEST,
        SIZE_SMALLEST
    }

    /** Which clips are shown. */
    public enum Filter {
        ALL,
        FAVORITES,
        HIGHLIGHTS
    }

    private ReplayLibrary() {
    }

    public static boolean matches(ReplayClip clip, Filter filter) {
        if (clip == null) return false;
        if (filter == null || filter == Filter.ALL) return true;
        if (filter == Filter.FAVORITES) return clip.favorite();
        return clip.highlight();
    }

    /**
     * Sort in place. Favorites are <em>not</em> hoisted: a player scanning by date should see the
     * real timeline, and {@link Filter#FAVORITES} is the way to narrow it.
     *
     * <p>Stable on every key by falling back to the recorded timestamp, so two clips of the same
     * length or size keep a deterministic, meaningful order rather than an arbitrary one.
     */
    public static void sort(java.util.List<ReplayClip> clips, Sort sort) {
        if (clips == null || sort == null) return;
        clips.sort((a, b) -> {
            int primary;
            switch (sort) {
                case DATE_OLDEST:
                    primary = Long.compare(a.recordedAtMs(), b.recordedAtMs());
                    break;
                case DURATION_LONGEST:
                    primary = Long.compare(b.durationMs(), a.durationMs());
                    break;
                case DURATION_SHORTEST:
                    primary = Long.compare(a.durationMs(), b.durationMs());
                    break;
                case SIZE_LARGEST:
                    primary = Long.compare(b.sizeBytes(), a.sizeBytes());
                    break;
                case SIZE_SMALLEST:
                    primary = Long.compare(a.sizeBytes(), b.sizeBytes());
                    break;
                case DATE_NEWEST:
                default:
                    primary = Long.compare(b.recordedAtMs(), a.recordedAtMs());
                    break;
            }
            if (primary != 0) return primary;
            return Long.compare(b.recordedAtMs(), a.recordedAtMs());
        });
    }

    /** Total bytes held by the list, for the storage usage bar. */
    public static long totalBytes(java.util.List<ReplayClip> clips) {
        long total = 0L;
        if (clips == null) return 0L;
        for (ReplayClip clip : clips) {
            if (clip != null) total += clip.sizeBytes();
        }
        return total;
    }
}
