package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The rolling frame-rate window, as pure arithmetic. Pins the cases that would otherwise make the
 * FPS readout lie: an empty window, a duplicate stamp, and frames outside the one-second window.
 */
public class FrameRateCounterTest {

    private static final long MS = 1_000_000L;

    @Test
    public void anEmptyWindowReadsZero() {
        FrameRateCounter counter = new FrameRateCounter();
        assertEquals(0, counter.fps());
        counter.record(0L);
        assertEquals("one frame is not yet a rate", 0, counter.fps());
    }

    @Test
    public void sixtyFramesInASecondReadSixty() {
        FrameRateCounter counter = new FrameRateCounter();
        long t = 0;
        for (int i = 0; i < 60; i++) {
            counter.record(t);
            t += 1000L / 60L * MS;
        }
        // Evaluate at the last stamp; 60 frames span just under a second.
        assertTrue("about 60 fps, got " + counter.fps(),
                counter.fps() >= 59 && counter.fps() <= 60);
    }

    @Test
    public void aDuplicateStampIsIgnored() {
        FrameRateCounter counter = new FrameRateCounter();
        counter.record(0L);
        counter.record(0L);
        counter.record(100 * MS);
        counter.record(100 * MS);
        // Only two distinct frames exist, and the second is the newest.
        assertEquals(2, counter.fps());
    }

    @Test
    public void framesOlderThanASecondFallOutOfTheWindow() {
        FrameRateCounter counter = new FrameRateCounter();
        // 120 frames at 60 fps (2 seconds), then evaluate at the end: only the last second counts.
        long t = 0;
        for (int i = 0; i < 120; i++) {
            counter.record(t);
            t += 1000L / 60L * MS;
        }
        long now = 120L * 1000L / 60L * MS;
        assertTrue("window must drop the first second, got " + counter.fpsAt(now),
                counter.fpsAt(now) <= 61);
    }

    @Test
    public void resetClearsTheWindow() {
        FrameRateCounter counter = new FrameRateCounter();
        for (int i = 0; i < 10; i++) counter.record(i * 16L * MS);
        counter.reset();
        assertEquals(0, counter.fps());
    }
}
