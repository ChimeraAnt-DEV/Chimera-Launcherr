package org.chimeramc.client.core.replay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Pins the pure rules behind sharing a clip: the share subject and the declared MIME type. The
 * intent itself needs a Context and a real file, so it is exercised on a device.
 */
public class ReplaySharingTest {

    private static ReplayClip clip(String name) {
        return new ReplayClip(new File("/replays/" + name), 30_000L, 1_000L, 0L,
                "world", "1.26.60", "survival");
    }

    @Test
    public void theShareTitleDropsTheExtension() {
        assertEquals("clip_2026", ReplaySharing.shareTitle(clip("clip_2026.mp4")));
    }

    @Test
    public void aBlankNameFallsBackToAStableLabel() {
        // An empty subject reads like a broken share; the fallback keeps it meaningful.
        assertEquals(ReplaySharing.FALLBACK_TITLE, ReplaySharing.shareTitle(null));
        ReplayClip nameless = new ReplayClip(new File(""), 30_000L, 1_000L, 0L,
                "world", "1.26.60", "survival");
        assertEquals(ReplaySharing.FALLBACK_TITLE, ReplaySharing.shareTitle(nameless));
    }

    @Test
    public void aClipIsAlwaysSharedAsVideo() {
        assertEquals(ReplaySharing.MIME_TYPE, ReplaySharing.mimeType(new File("/replays/x.mp4")));
        // A renamed file must not be declared as something a receiver cannot play.
        assertEquals(ReplaySharing.MIME_TYPE, ReplaySharing.mimeType(new File("/replays/x.txt")));
        assertEquals(ReplaySharing.MIME_TYPE, ReplaySharing.mimeType(null));
    }

    @Test
    public void onlyRealFilesAreShareable() throws IOException {
        File real = File.createTempFile("glowberry_share", ".mp4");
        try {
            assertTrue(ReplaySharing.isShareable(real));
            assertFalse(ReplaySharing.isShareable(new File("/definitely/not/here.mp4")));
            assertFalse(ReplaySharing.isShareable(null));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            real.delete();
        }
    }

    @Test
    public void extensionIsLowerCasedAndDotless() {
        assertEquals("mp4", ReplaySharing.extension("Clip.MP4"));
        assertEquals("", ReplaySharing.extension("noext"));
        assertEquals("", ReplaySharing.extension("trailing."));
        assertEquals("", ReplaySharing.extension(null));
    }
}
