package org.chimeramc.client.core.replay;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tier 3 export: putting a clip somewhere the player can share it from.
 *
 * <p>Two jobs:
 * <ul>
 *   <li><b>Export to the gallery</b> — copies the file into the shared {@code Movies/GlowberryClient}
 *       collection through {@code MediaStore} on API 29+, and a plain file copy on API 28, which is
 *       the only path this app's {@code minSdk} leaves. No storage permission is requested for the
 *       MediaStore route.</li>
 *   <li><b>Trim</b> — a real re-mux of the selected window into a new clip. A trim that only
 *       recorded a start/end in metadata would not be a trim, so this produces a shorter file.</li>
 * </ul>
 *
 * <p>Everything reports success or failure; a failed export leaves the original clip untouched.
 */
public final class ReplayExporter {

    private static final String TAG = "ReplayExporter";
    private static final String ALBUM = "GlowberryClient";

    private ReplayExporter() {
    }

    /** Copies a clip into the shared gallery. Returns the resulting Uri, or null on failure. */
    public static Uri exportToGallery(Context context, ReplayClip clip) {
        if (clip == null || clip.file() == null || !clip.file().isFile()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return exportViaMediaStore(context, clip.file());
        }
        return exportViaFileCopy(context, clip.file());
    }

    private static Uri exportViaMediaStore(Context context, File source) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/" + ALBUM);
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri target = null;
        try {
            target = context.getContentResolver().insert(collection, values);
            if (target == null) return null;
            try (OutputStream out = context.getContentResolver().openOutputStream(target);
                 InputStream in = new FileInputStream(source)) {
                if (out == null) return null;
                copy(in, out);
            }
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(target, values, null, null);
            return target;
        } catch (Throwable t) {
            Log.w(TAG, "MediaStore export failed", t);
            if (target != null) {
                try {
                    context.getContentResolver().delete(target, null, null);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }
    }

    private static Uri exportViaFileCopy(Context context, File source) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), ALBUM);
            if (!dir.exists() && !dir.mkdirs()) return null;
            File target = ReplayStorage.uniqueFile(dir, source.getName());
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }
            return Uri.fromFile(target);
        } catch (Throwable t) {
            Log.w(TAG, "Gallery export failed", t);
            return null;
        }
    }

    /**
     * Trims a clip to the given window, writing a new {@code *_trimmed.mp4} beside it.
     *
     * <p>Re-muxes the container: it copies the encoded samples in the window without decoding, so
     * the trim is fast and lossless, and it does not touch the source clip.
     */
    public static File trim(Context context, ReplayClip clip, ReplayTrim trim) {
        if (clip == null || clip.file() == null || trim == null || !trim.isExportable()) return null;
        File source = clip.file();
        if (!source.isFile()) return null;

        long durationUs = readDurationUs(source);
        long startUs = trim.startMs() * 1000L;
        long endUs = trim.endMs() * 1000L;
        if (durationUs > 0L) {
            startUs = Math.min(startUs, durationUs);
            endUs = Math.min(endUs, durationUs);
        }
        if (endUs <= startUs) return null;

        File target = ReplayStorage.uniqueFile(source.getParentFile(),
                ReplayTrim.trimmedName(source.getName()));
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            muxer = new MediaMuxer(target.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int trackCount = extractor.getTrackCount();
            int[] muxerTrack = new int[trackCount];
            for (int i = 0; i < trackCount; i++) {
                muxerTrack[i] = muxer.addTrack(extractor.getTrackFormat(i));
            }
            muxer.start();
            muxerStarted = true;
            for (int i = 0; i < trackCount; i++) {
                extractor.selectTrack(i);
                extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 1024);
                android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
                while (true) {
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) break;
                    long time = extractor.getSampleTime();
                    if (time > endUs) break;
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = Math.max(0L, time - startUs);
                    info.flags = extractor.getSampleFlags();
                    muxer.writeSampleData(muxerTrack[i], buffer, info);
                    if (!extractor.advance()) break;
                }
                extractor.unselectTrack(i);
            }
            return target;
        } catch (Throwable t) {
            Log.w(TAG, "Trim failed", t);
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return null;
        } finally {
            try {
                extractor.release();
            } catch (Throwable ignored) {
            }
            if (muxer != null) {
                if (muxerStarted) {
                    try {
                        muxer.stop();
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    muxer.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static long readDurationUs(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (value == null) return 0L;
            return Long.parseLong(value) * 1000L;
        } catch (Throwable t) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
}
