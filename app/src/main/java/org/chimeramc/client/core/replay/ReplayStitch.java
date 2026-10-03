package org.chimeramc.client.core.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure rules for stitching several clips into one highlight reel.
 *
 * <p>Trim already exists, but there is no way to combine a few favorited clips into a single
 * sequence, which is the natural next step once auto-highlights start populating the library. This
 * class holds only the decisions - how many clips are enough, in what order, and what the result
 * is called - so they are unit-testable on the JVM; the container-level concatenation lives in
 * {@link ReplayExporter}.
 *
 * <p>Order is the order the caller supplied (the library's current sort), not a re-sort: the
 * player picked the sequence by picking the sort, and reordering it here would silently ignore
 * that choice.
 */
public final class ReplayStitch {

    /** Fewer than this is not a reel; a single clip already plays on its own. */
    public static final int MIN_CLIPS = 2;

    /** An upper bound so a "select all" cannot ask for a hundred-way mux in one go. */
    public static final int MAX_CLIPS = 20;

    private ReplayStitch() {
    }

    /** True when the list holds enough distinct, existing clips to stitch. */
    public static boolean isStitchable(List<ReplayClip> clips) {
        return ordered(clips).size() >= MIN_CLIPS;
    }

    /**
     * The clips to stitch, in order, with duplicates and unusable entries removed.
     *
     * <p>De-duplicates by file path so a clip cannot be concatenated with itself, and drops any
     * entry whose file is missing - a stale card must not fail the whole reel.
     */
    public static List<ReplayClip> ordered(List<ReplayClip> clips) {
        List<ReplayClip> result = new ArrayList<>();
        if (clips == null) return result;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ReplayClip clip : clips) {
            if (clip == null || clip.file() == null || !clip.file().isFile()) continue;
            String path = clip.file().getAbsolutePath();
            if (!seen.add(path)) continue;
            result.add(clip);
            if (result.size() >= MAX_CLIPS) break;
        }
        return result;
    }

    /**
     * The file name for a stitched reel.
     *
     * <p>A fixed base name, not a timestamp: the caller writes through the repository's unique-name
     * helper, so a second reel becomes {@code highlight_reel_2.mp4} rather than overwriting the
     * first. A generated timestamp would also make the reel's own name unrepeatable in a test.
     */
    public static String stitchedName() {
        return "highlight_reel.mp4";
    }
}
