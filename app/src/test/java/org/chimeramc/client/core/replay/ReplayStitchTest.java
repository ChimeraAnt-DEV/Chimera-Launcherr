package org.chimeramc.client.core.replay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pins the stitching rules: how many clips make a reel, which are usable, the order, and the name.
 * The container mux itself needs real MP4 files and a device, so it is exercised on-device.
 */
public class ReplayStitchTest {

    private static ReplayClip existing(String path) {
        return new ReplayClip(new File(path), 10_000L, 500L, 0L, "world", "1.26.60", "survival");
    }

    @Test
    public void twoExistingClipsAreEnough() throws IOException {
        File a = File.createTempFile("reel_a", ".mp4");
        File b = File.createTempFile("reel_b", ".mp4");
        try {
            assertTrue(ReplayStitch.isStitchable(Arrays.asList(existing(a.getAbsolutePath()),
                    existing(b.getAbsolutePath()))));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            a.delete();
            //noinspection ResultOfMethodCallIgnored
            b.delete();
        }
    }

    @Test
    public void oneClipOrNoneIsNotAReel() throws IOException {
        File a = File.createTempFile("reel_single", ".mp4");
        try {
            assertFalse(ReplayStitch.isStitchable(Collections.singletonList(existing(a.getAbsolutePath()))));
            assertFalse(ReplayStitch.isStitchable(Collections.emptyList()));
            assertFalse(ReplayStitch.isStitchable(null));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            a.delete();
        }
    }

    @Test
    public void missingFilesAreDroppedSoAStaleCardCannotFailTheReel() throws IOException {
        File real = File.createTempFile("reel_real", ".mp4");
        try {
            List<ReplayClip> ordered = ReplayStitch.ordered(Arrays.asList(
                    existing("/gone/one.mp4"),
                    existing(real.getAbsolutePath()),
                    null));
            assertEquals(1, ordered.size());
            assertFalse(ReplayStitch.isStitchable(ordered));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            real.delete();
        }
    }

    @Test
    public void duplicatesAreRemovedByPath() throws IOException {
        File a = File.createTempFile("reel_dup", ".mp4");
        File b = File.createTempFile("reel_dup_b", ".mp4");
        try {
            List<ReplayClip> ordered = ReplayStitch.ordered(Arrays.asList(
                    existing(a.getAbsolutePath()),
                    existing(a.getAbsolutePath()),
                    existing(b.getAbsolutePath())));
            assertEquals(2, ordered.size());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            a.delete();
            //noinspection ResultOfMethodCallIgnored
            b.delete();
        }
    }

    @Test
    public void orderIsPreserved() throws IOException {
        File a = File.createTempFile("reel_order_a", ".mp4");
        File b = File.createTempFile("reel_order_b", ".mp4");
        try {
            List<ReplayClip> ordered = ReplayStitch.ordered(Arrays.asList(
                    existing(b.getAbsolutePath()),
                    existing(a.getAbsolutePath())));
            assertEquals(b.getAbsolutePath(), ordered.get(0).file().getAbsolutePath());
            assertEquals(a.getAbsolutePath(), ordered.get(1).file().getAbsolutePath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            a.delete();
            //noinspection ResultOfMethodCallIgnored
            b.delete();
        }
    }

    @Test
    public void theReelIsCappedSoSelectAllCannotAskForAHundredWayMux() throws IOException {
        List<ReplayClip> many = new ArrayList<>();
        List<File> files = new ArrayList<>();
        try {
            for (int i = 0; i < ReplayStitch.MAX_CLIPS + 5; i++) {
                File f = File.createTempFile("reel_cap_" + i, ".mp4");
                files.add(f);
                many.add(existing(f.getAbsolutePath()));
            }
            assertEquals(ReplayStitch.MAX_CLIPS, ReplayStitch.ordered(many).size());
        } finally {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    @Test
    public void theReelNameIsStableSoUniqueNamingMakesTheSecondCopy() {
        assertEquals("highlight_reel.mp4", ReplayStitch.stitchedName());
    }
}
