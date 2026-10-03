package org.chimeramc.client.core.replay;

import java.io.File;
import java.util.Locale;

/**
 * The pure rules behind sharing a clip: what to call the shared item and which MIME type to
 * declare.
 *
 * <p>Kept Android-free so the naming and typing are unit-testable on the JVM; the actual
 * {@code ACTION_SEND} intent is assembled by {@code ReplayShareSheet}, which needs a Context.
 *
 * <p>The title is the clip's display name without the extension, because a share sheet that shows
 * {@code clip_2026-10-02_14-03-11.mp4} as the subject reads like a file dump. A blank name falls
 * back to a stable label rather than an empty subject.
 */
public final class ReplaySharing {

    /** The MIME type a recorded clip is shared as. */
    public static final String MIME_TYPE = "video/mp4";

    /** Shown when the clip has no usable name, so the subject is never empty. */
    public static final String FALLBACK_TITLE = "GlowberryClient clip";

    private ReplaySharing() {
    }

    /** A human-readable share subject: the clip name without its extension. */
    public static String shareTitle(ReplayClip clip) {
        if (clip == null) return FALLBACK_TITLE;
        String display = clip.displayName();
        if (display == null || display.trim().isEmpty()) return FALLBACK_TITLE;
        return display.trim();
    }

    /**
     * The MIME type to declare for a file.
     *
     * <p>Always {@code video/mp4}: every clip this app records is an MP4, and guessing from the
     * extension would let a renamed file be declared as something a receiver cannot play. A file
     * with no name at all still gets the video type rather than {@code *&#47;*}.
     */
    public static String mimeType(File file) {
        return MIME_TYPE;
    }

    /** True when a file looks like a clip worth sharing (exists and is a regular file). */
    public static boolean isShareable(File file) {
        return file != null && file.isFile();
    }

    /** The extension of a file name, lower-cased and without the dot, or "" when there is none. */
    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
