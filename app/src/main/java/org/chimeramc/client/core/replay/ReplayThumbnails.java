package org.chimeramc.client.core.replay;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;

import java.io.File;

/**
 * Clip thumbnails, decoded off the UI thread and cached.
 *
 * <p>A grid of clips with no frame previews reads as a file manager, not a paid app, so each card
 * gets a real frame pulled from the video. Decoding is expensive, so frames are cached in a small
 * {@link LruCache} keyed by path and modification time; an undecodable clip yields no bitmap and
 * the card falls back to its gradient placeholder rather than blocking.
 */
public final class ReplayThumbnails {

    /** Where in the clip to grab the frame — a few seconds in avoids the menu/fade at the start. */
    public static final long CAPTURE_POSITION_MS = 3_000L;

    private static final int MAX_CACHE_BYTES = 12 * 1024 * 1024;
    private static final int THUMB_WIDTH = 320;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            MAX_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };

    private ReplayThumbnails() {
    }

    /** A cached thumbnail, or null. Never decodes; safe on the UI thread. */
    public static Bitmap cached(ReplayClip clip) {
        if (clip == null || clip.file() == null) return null;
        return CACHE.get(key(clip.file()));
    }

    /** Decodes and caches a thumbnail. Call off the UI thread. */
    public static Bitmap load(ReplayClip clip) {
        if (clip == null || clip.file() == null) return null;
        File file = clip.file();
        String key = key(file);
        Bitmap existing = CACHE.get(key);
        if (existing != null) return existing;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(CAPTURE_POSITION_MS * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime(0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (frame == null) return null;
            Bitmap scaled = scale(frame);
            CACHE.put(key, scaled);
            return scaled;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Bitmap scale(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0 || width <= THUMB_WIDTH) return source;
        int targetHeight = Math.max(1, Math.round(height * (THUMB_WIDTH / (float) width)));
        try {
            return Bitmap.createScaledBitmap(source, THUMB_WIDTH, targetHeight, true);
        } catch (Throwable t) {
            return source;
        }
    }

    private static String key(File file) {
        return file.getAbsolutePath() + "#" + file.lastModified();
    }

    /** Drops a clip's cached frame, e.g. after a trim replaced the file. */
    public static void invalidate(File file) {
        if (file != null) CACHE.remove(key(file));
    }

    public static void clear() {
        CACHE.evictAll();
    }
}
