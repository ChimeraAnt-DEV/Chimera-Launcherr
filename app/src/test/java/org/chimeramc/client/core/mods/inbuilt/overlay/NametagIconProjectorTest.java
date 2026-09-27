package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The nametag projector is pure geometry, so the rules that would otherwise only show up on a
 * device -- icons landing beside the name, behind-camera tags being dropped, and the same-channel
 * filter -- are pinned here. The camera convention is shared with {@link HitboxProjector}, so a
 * yaw mismatch would mirror every icon; these tests catch that too.
 */
public class NametagIconProjectorTest {

    private static HitboxProjector.Camera camera(float yaw, float pitch) {
        return new HitboxProjector.Camera(0f, 0f, 0f, yaw, pitch, 70f, 1080, 720);
    }

    @Test
    public void aTagAheadProjectsBesideAndAboveTheLabelCentre() {
        List<NametagIconProjector.Tag> tags = Collections.singletonList(
                new NametagIconProjector.Tag("peer", "Peer", 0f, 0f, 5f, 2f, "world"));
        List<NametagIconProjector.Placed> placed =
                NametagIconProjector.project(camera(0f, 0f), tags, "world");

        assertEquals(1, placed.size());
        NametagIconProjector.Placed icon = placed.get(0);
        // Upright and centred horizontally, but raised above the vertical centre by the label.
        assertTrue("icon should sit above the screen centre", icon.centerY < 360f);
        assertTrue("icon should sit right of centre", icon.centerX > 1080f / 2f);
        assertTrue(icon.size > 0f);
    }

    @Test
    public void aTagBehindTheCameraIsDropped() {
        List<NametagIconProjector.Tag> tags = Collections.singletonList(
                new NametagIconProjector.Tag("behind", "Behind", 0f, 0f, -5f, 2f, "world"));
        assertTrue(NametagIconProjector.project(camera(0f, 0f), tags, "world").isEmpty());
    }

    @Test
    public void turningTheCameraKeepsTheAheadTagCentred() {
        // Yaw 90 points the camera along -X (yaw is clockwise from +Z), so a tag at -X is ahead.
        List<NametagIconProjector.Tag> tags = Collections.singletonList(
                new NametagIconProjector.Tag("peer", "Peer", -5f, 0f, 0f, 2f, "world"));
        List<NametagIconProjector.Placed> placed =
                NametagIconProjector.project(camera(90f, 0f), tags, "world");
        assertEquals(1, placed.size());
        // Directly ahead: the icon's horizontal offset is only its right-hand shift, not a mirror.
        assertTrue(placed.get(0).centerX > 1080f / 2f);
    }

    @Test
    public void aTagOnAnInaudibleChannelIsDropped() {
        List<NametagIconProjector.Tag> tags = Collections.singletonList(
                new NametagIconProjector.Tag("other", "Other", 0f, 0f, 5f, 2f, "blue"));
        // Listener is on the private channel "team", so "blue" is not heard.
        assertTrue(NametagIconProjector.project(camera(0f, 0f), tags, "team").isEmpty());
    }

    @Test
    public void aWorldListenerHearsTagsOnAnyChannel() {
        List<NametagIconProjector.Tag> tags = Arrays.asList(
                new NametagIconProjector.Tag("a", "A", 0f, 0f, 5f, 2f, "blue"),
                new NametagIconProjector.Tag("b", "B", 0f, 0f, 6f, 2f, "team"));
        assertEquals(2, NametagIconProjector.project(
                camera(0f, 0f), tags, org.chimeramc.client.core.voice.VoiceChannel.WORLD).size());
    }

    @Test
    public void aVeryDistantTagProjectsTooSmallToReadAndIsDropped() {
        List<NametagIconProjector.Tag> tags = Collections.singletonList(
                new NametagIconProjector.Tag("far", "Far", 0f, 0f, 100000f, 2f, "world"));
        assertTrue(NametagIconProjector.project(camera(0f, 0f), tags, "world").isEmpty());
    }

    @Test
    public void nearerIconsSortLastSoTheyPaintOnTop() {
        List<NametagIconProjector.Tag> tags = Arrays.asList(
                new NametagIconProjector.Tag("far", "Far", 0f, 0f, 40f, 2f, "world"),
                new NametagIconProjector.Tag("near", "Near", 0f, 0f, 5f, 2f, "world"));
        List<NametagIconProjector.Placed> placed =
                NametagIconProjector.project(camera(0f, 0f), tags, "world");
        assertEquals("near", placed.get(placed.size() - 1).peerId);
    }

    @Test
    public void emptyInputsProjectNothingRatherThanThrowing() {
        assertTrue(NametagIconProjector.project(null, null, "world").isEmpty());
        assertTrue(NametagIconProjector.project(camera(0f, 0f),
                NametagIconProjector.emptyTags(), "world").isEmpty());
    }
}
