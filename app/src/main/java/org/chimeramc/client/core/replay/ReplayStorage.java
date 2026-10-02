package org.chimeramc.client.core.replay;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Where recorded clips live.
 *
 * <p>The spec names {@code ChimeraAnt-DEV/replays/}. It sits under the app's own external files
 * directory (not public storage) so no runtime storage permission is needed and the library is
 * removed with the app; there is an internal-files fallback for a device with no external volume.
 * One place resolves the path so the recorder, the library and the trimmer cannot disagree about
 * where a clip is.
 */
public final class ReplayStorage {

    /** The folder the spec names. */
    public static final String REPLAYS_DIR = "ChimeraAnt-DEV/replays";

    /** Sidecar suffix for the per-clip metadata (world, version, game mode, flags). */
    public static final String SIDECAR_SUFFIX = ".meta";

    private ReplayStorage() {
    }

    /** The replays directory, creating it if needed. Never null. */
    public static File replayDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, REPLAYS_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** The sidecar file that carries a clip's non-file metadata. */
    public static File sidecarFor(File clip) {
        return new File(clip.getParentFile(), clip.getName() + SIDECAR_SUFFIX);
    }

    /** The file name for a new recording: {@code replay_2026-09-29_14-23-07.mp4}. */
    public static String newClipName(long epochMs) {
        return "replay_" + ReplayMetadata.fileStamp(epochMs) + ".mp4";
    }

    /**
     * A file in the replays directory whose name no existing clip uses.
     *
     * <p>Recording onto an existing name would destroy a clip the player still has, so a collision
     * appends a counter rather than overwriting.
     */
    public static File uniqueFile(File dir, String desiredName) {
        File candidate = new File(dir, desiredName);
        if (!candidate.exists()) return candidate;
        int dot = desiredName.lastIndexOf('.');
        String base = dot > 0 ? desiredName.substring(0, dot) : desiredName;
        String ext = dot > 0 ? desiredName.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            candidate = new File(dir, String.format(Locale.US, "%s_%d%s", base, i, ext));
            if (!candidate.exists()) return candidate;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    /** Total bytes of every file in the replays directory, sidecars included. */
    public static long usageBytes(Context context) {
        return dirUsageBytes(replayDir(context));
    }

    /** Total bytes of every file in a directory. Null or unreadable reads as zero. */
    public static long dirUsageBytes(File dir) {
        long total = 0L;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0L;
        for (File file : files) {
            if (file != null && file.isFile()) total += file.length();
        }
        return total;
    }

    /** Writes a small text sidecar next to a clip. Silently a no-op on failure. */
    public static void writeSidecar(File clip, String contents) {
        try (OutputStream out = new FileOutputStream(sidecarFor(clip))) {
            out.write(contents.getBytes("UTF-8"));
        } catch (Exception ignored) {
            // A missing sidecar only costs metadata on a later scan, not the clip.
        }
    }

    /** True when the file is a clip the library should list. */
    public static boolean isClip(File file) {
        return file != null && file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".mp4");
    }

    /** True when the external volume is mounted and writable. */
    public static boolean isExternalAvailable(Context context) {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                && context.getExternalFilesDir(null) != null;
    }
}
