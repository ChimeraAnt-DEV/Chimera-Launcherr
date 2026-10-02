package org.chimeramc.client.core.replay;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The clip library: scans the replays directory, reads each clip's sidecar, and exposes the
 * rename/delete/favorite operations.
 *
 * <p>The scan is file-system work and must not run on the UI thread; the caller wraps it. The one
 * bit of media work — reading a clip's real duration from the file — uses
 * {@link MediaMetadataRetriever} and is a best-effort: an unreadable clip is still listed, with a
 * duration of zero rendered as a dash, rather than vanishing from the library.
 */
public final class ReplayClipRepository {

    private final Context context;

    public ReplayClipRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Scans the replays directory. Returns clips in date-newest order; empty when none exist. */
    public List<ReplayClip> scan() {
        return scan(ReplayStorage.replayDir(context));
    }

    static List<ReplayClip> scan(File dir) {
        List<ReplayClip> clips = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return clips;
        for (File file : files) {
            if (ReplayStorage.isClip(file)) clips.add(readClip(file));
        }
        clips.sort(Comparator.comparingLong(ReplayClip::recordedAtMs).reversed());
        return clips;
    }

    /**
     * Builds a clip descriptor from a file plus its sidecar.
     *
     * <p>Package-visible and free of media reads so the merge rule — sidecar wins for metadata,
     * file wins for size, and a missing sidecar still yields a listed clip — is testable without a
     * device.
     */
    static ReplayClip fromFileAndSidecar(File file, String sidecarText, long durationMs) {
        ReplaySidecar sidecar = ReplaySidecar.parse(sidecarText);
        long recordedAt = sidecar.recordedAtMs();
        if (recordedAt <= 0L) recordedAt = file.lastModified();
        ReplayClip clip = new ReplayClip(file, durationMs, file.length(), recordedAt,
                sidecar.world(), sidecar.gameVersion(), sidecar.gameMode());
        sidecar.applyTo(clip);
        return clip;
    }

    private static ReplayClip readClip(File file) {
        String sidecarText = readSidecarText(file);
        long duration = readDurationMs(file);
        return fromFileAndSidecar(file, sidecarText, duration);
    }

    private static String readSidecarText(File clip) {
        File sidecar = ReplayStorage.sidecarFor(clip);
        if (!sidecar.isFile()) return "";
        try (InputStream in = new FileInputStream(sidecar)) {
            byte[] buffer = new byte[(int) Math.min(sidecar.length(), 64 * 1024)];
            int read = in.read(buffer);
            if (read <= 0) return "";
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static long readDurationMs(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration == null) return 0L;
            return Long.parseLong(duration);
        } catch (Throwable t) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
                // Nothing useful to do; the retriever is discarded either way.
            }
        }
    }

    /**
     * Writes the sidecar for a clip, preserving the fields it already carries.
     *
     * <p>Used to flip favorite/highlight without losing the world/version/mode written at record
     * time.
     */
    public void updateSidecar(ReplayClip clip) {
        if (clip == null) return;
        String existing = readSidecarText(clip.file());
        ReplaySidecar sidecar = ReplaySidecar.parse(existing);
        if (sidecar.get("version", "").isEmpty()) {
            sidecar.set("version", String.valueOf(ReplaySidecar.VERSION));
        }
        if (sidecar.get("recordedAt", "").isEmpty()) {
            sidecar.set("recordedAt", String.valueOf(clip.recordedAtMs()));
        }
        sidecar.set("world", clip.world());
        sidecar.set("gameVersion", clip.gameVersion());
        sidecar.set("gameMode", clip.gameMode());
        sidecar.setFavorite(clip.favorite());
        sidecar.setHighlight(clip.highlight());
        ReplayStorage.writeSidecar(clip.file(), sidecar.serialize());
    }

    /** Renames a clip and its sidecar. Returns the new file, or the old one on failure. */
    public File rename(ReplayClip clip, String newBaseName) {
        if (clip == null || clip.file() == null) return null;
        String safe = sanitizeName(newBaseName);
        if (safe.isEmpty()) return clip.file();
        if (!safe.toLowerCase(Locale.US).endsWith(".mp4")) safe += ".mp4";
        File target = ReplayStorage.uniqueFile(clip.file().getParentFile(), safe);
        File source = clip.file();
        File sourceSidecar = ReplayStorage.sidecarFor(source);
        if (!source.renameTo(target)) return source;
        if (sourceSidecar.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            sourceSidecar.renameTo(ReplayStorage.sidecarFor(target));
        }
        return target;
    }

    /** Deletes a clip and its sidecar. */
    public boolean delete(ReplayClip clip) {
        if (clip == null || clip.file() == null) return false;
        File sidecar = ReplayStorage.sidecarFor(clip.file());
        if (sidecar.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            sidecar.delete();
        }
        return clip.file().delete();
    }

    /** Deletes the given clips, returning how many files were removed. */
    public int deleteAll(List<ReplayClip> clips) {
        if (clips == null) return 0;
        int deleted = 0;
        for (ReplayClip clip : clips) {
            if (delete(clip)) deleted++;
        }
        return deleted;
    }

    /** Strips path separators and control characters so a typed name cannot escape the folder. */
    static String sanitizeName(String raw) {
        if (raw == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c < 0x20) {
                continue;
            }
            builder.append(c);
        }
        return builder.toString().trim();
    }

    /** All clips matching a filter, sorted per the settings. Pure selection over a scan. */
    public List<ReplayClip> filtered(List<ReplayClip> all, ReplayLibrary.Filter filter,
                                     ReplayLibrary.Sort sort) {
        List<ReplayClip> result = new ArrayList<>();
        if (all != null) {
            for (ReplayClip clip : all) {
                if (ReplayLibrary.matches(clip, filter)) result.add(clip);
            }
        }
        ReplayLibrary.sort(result, sort);
        return result;
    }

    /** Convenience for a test that wants the raw names of a scan. */
    static List<String> names(List<ReplayClip> clips) {
        List<String> names = new ArrayList<>();
        for (ReplayClip clip : clips) names.add(clip.name());
        return names;
    }
}
