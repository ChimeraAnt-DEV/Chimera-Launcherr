package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector;
import org.chimeramc.client.core.mods.inbuilt.overlay.ReachIndicator;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Reach Indicator rule: which peer the crosshair is on, and how far. Pure, so no device.
 * The important properties are that a peer off the crosshair is not reported, that the nearest
 * of several is chosen, and that a no-data case returns null rather than a zero reading.
 */
public class ReachIndicatorTest {

    /** Camera at the origin looking along +Z, level. */
    private static HitboxProjector.Camera camera() {
        return new HitboxProjector.Camera(0f, 1.62f, 0f, 0f, 0f, 70f, 1920, 1080);
    }

    private static ReachIndicator.VoicePeerPosition peer(String id, float x, float y, float z) {
        return new ReachIndicator.VoicePeerPosition(id, id, x, y, z);
    }

    @Test
    public void aPeerUnderTheCrosshairIsReportedWithItsDistance() {
        List<ReachIndicator.VoicePeerPosition> peers =
                Collections.singletonList(peer("p1", 0f, 0f, 5f));
        ReachIndicator.Reading reading = ReachIndicator.read(camera(), peers);
        assertNotNull(reading);
        assertEquals("p1", reading.peerId);
        // Centre of the box is 0.9 up from the feet; camera is at 1.62, so the vertical term is
        // small and the distance is dominated by the 5-block forward gap.
        assertEquals(5.0f, reading.distanceBlocks, 0.2f);
    }

    @Test
    public void aPeerBehindTheCameraIsNotReported() {
        List<ReachIndicator.VoicePeerPosition> peers =
                Collections.singletonList(peer("p1", 0f, 0f, -5f));
        assertNull(ReachIndicator.read(camera(), peers));
    }

    @Test
    public void aPeerOffToTheSideIsNotReported() {
        List<ReachIndicator.VoicePeerPosition> peers =
                Collections.singletonList(peer("p1", 8f, 0f, 5f));
        assertNull(ReachIndicator.read(camera(), peers));
    }

    @Test
    public void theNearestOfSeveralUnderTheCrosshairWins() {
        List<ReachIndicator.VoicePeerPosition> peers = Arrays.asList(
                peer("far", 0f, 0f, 9f),
                peer("near", 0f, 0f, 3f),
                peer("mid", 0f, 0f, 6f));
        ReachIndicator.Reading reading = ReachIndicator.read(camera(), peers);
        assertNotNull(reading);
        assertEquals("near", reading.peerId);
    }

    @Test
    public void noCameraOrNoPeersReadsAsNothing() {
        assertNull(ReachIndicator.read(null, Collections.singletonList(peer("p1", 0f, 0f, 5f))));
        assertNull(ReachIndicator.read(camera(), null));
        assertNull(ReachIndicator.read(camera(), Collections.emptyList()));
    }

    @Test
    public void aNonFinitePositionIsSkipped() {
        List<ReachIndicator.VoicePeerPosition> peers = Arrays.asList(
                peer("bad", Float.NaN, 0f, 5f),
                peer("good", 0f, 0f, 5f));
        ReachIndicator.Reading reading = ReachIndicator.read(camera(), peers);
        assertNotNull(reading);
        assertEquals("good", reading.peerId);
    }

    @Test
    public void theDistanceIsFormattedWithOneDecimal() {
        List<ReachIndicator.VoicePeerPosition> peers =
                Collections.singletonList(peer("p1", 0f, 0f, 5f));
        ReachIndicator.Reading reading = ReachIndicator.read(camera(), peers);
        assertNotNull(reading);
        String text = reading.format();
        assertTrue(text, text.endsWith(" blocks"));
        assertTrue(text, text.contains("."));
    }
}
