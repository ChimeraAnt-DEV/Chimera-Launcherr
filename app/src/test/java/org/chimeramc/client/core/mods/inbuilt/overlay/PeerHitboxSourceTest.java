package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.voice.VoicePeer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pins the peer-feed hitbox rules: the "no entity list" route that turns voice-protocol positions
 * into player boxes.
 *
 * <p>No mocks -- the source and the projector are pure, so these run the real geometry a device
 * would.
 */
public class PeerHitboxSourceTest {

    private static VoicePeer peer(String id, float x, float y, float z) {
        return new VoicePeer(id, id, x, y, z, "world", 0L);
    }

    @Test
    public void aPeerBecomesAPlayerBoxOfTheStandardSize() {
        PeerHitboxSource.PeerHitbox box = PeerHitboxSource.from(peer("alice", 10f, 64f, -5f));
        assertNotNull(box);
        assertEquals(PeerHitboxSource.Kind.PEER, box.kind);
        assertEquals("alice", box.peerId);
        assertEquals(0.6f, box.entity.width, 1e-6f);
        assertEquals(0.6f, box.entity.depth, 1e-6f);
        assertEquals(1.8f, box.entity.height, 1e-6f);
        assertEquals(10f, box.entity.x, 1e-6f);
        assertEquals(64f, box.entity.y, 1e-6f);
        assertEquals(-5f, box.entity.z, 1e-6f);
    }

    @Test
    public void aPeerWithAMalformedPositionIsSkippedNotGuessed() {
        assertNull(PeerHitboxSource.from(null));
        assertNull(PeerHitboxSource.from(peer("nan", Float.NaN, 64f, 0f)));
        assertNull(PeerHitboxSource.from(peer("inf", 0f, 64f, Float.POSITIVE_INFINITY)));
        assertNull(PeerHitboxSource.from(peer("ninf", 0f, 64f, Float.NEGATIVE_INFINITY)));
    }

    @Test
    public void buildSkipsMalformedPeersAndKeepsTheRest() {
        List<VoicePeer> peers = new ArrayList<>();
        peers.add(peer("good", 1f, 2f, 3f));
        peers.add(peer("bad", Float.NaN, 0f, 0f));
        List<PeerHitboxSource.PeerHitbox> boxes = PeerHitboxSource.build(() -> peers);
        assertEquals(1, boxes.size());
        assertEquals("good", boxes.get(0).peerId);
    }

    @Test
    public void aThrowingOrNullSourceYieldsNothingRatherThanCrashing() {
        assertTrue(PeerHitboxSource.build(null).isEmpty());
        assertTrue(PeerHitboxSource.build(() -> null).isEmpty());
        assertTrue(PeerHitboxSource.build(() -> {
            throw new IllegalStateException("feed died");
        }).isEmpty());
        assertTrue(PeerHitboxSource.build(Collections::emptyList).isEmpty());
    }

    @Test
    public void aPeerAheadOfTheCameraProjectsInsideTheScreenAndBehindItDoesNot() {
        HitboxProjector.Camera camera = new HitboxProjector.Camera(
                0f, 65f, 0f, 0f, 0f, 70f, 1920, 1080);
        HitboxProjector.Basis basis = HitboxProjector.basis(camera);
        assertNotNull(basis);

        PeerHitboxSource.PeerHitbox ahead = PeerHitboxSource.from(peer("ahead", 0f, 64f, 5f));
        HitboxProjector.Projected visible = HitboxProjector.projectEntity(ahead.entity, basis);
        assertNotNull(visible);
        assertTrue(visible.box.right > visible.box.left);
        assertTrue(visible.box.bottom > visible.box.top);
        assertEquals(1920 / 2f, (visible.box.left + visible.box.right) / 2f, 1.0f);

        PeerHitboxSource.PeerHitbox behind = PeerHitboxSource.from(peer("behind", 0f, 64f, -5f));
        assertNull(HitboxProjector.projectEntity(behind.entity, basis));
    }

    @Test
    public void aCloserPeerProjectsLargerThanOneFurtherAway() {
        HitboxProjector.Camera camera = new HitboxProjector.Camera(
                0f, 65f, 0f, 0f, 0f, 70f, 1920, 1080);
        HitboxProjector.Basis basis = HitboxProjector.basis(camera);

        HitboxProjector.Projected near = HitboxProjector.projectEntity(
                PeerHitboxSource.from(peer("near", 0f, 64f, 3f)).entity, basis);
        HitboxProjector.Projected far = HitboxProjector.projectEntity(
                PeerHitboxSource.from(peer("far", 0f, 64f, 30f)).entity, basis);
        assertNotNull(near);
        assertNotNull(far);
        assertTrue(near.box.height() > far.box.height());
    }

    @Test
    public void basisIsDegenerateSafeWhenLookingStraightUp() {
        HitboxProjector.Camera camera = new HitboxProjector.Camera(
                0f, 64f, 0f, 0f, 90f, 70f, 800, 600);
        assertNotNull(HitboxProjector.basis(camera));
    }

    @Test
    public void thePlayerBoxMatchesTheProjectorsOwnPlayerFactory() {
        HitboxProjector.Entity expected = HitboxProjector.Entity.player(3f, 4f, 5f);
        PeerHitboxSource.PeerHitbox actual = PeerHitboxSource.from(peer("p", 3f, 4f, 5f));
        assertNotNull(actual);
        assertEquals(expected.width, actual.entity.width, 1e-6f);
        assertEquals(expected.height, actual.entity.height, 1e-6f);
        assertEquals(expected.depth, actual.entity.depth, 1e-6f);
        assertEquals(expected.minX(), actual.entity.minX(), 1e-6f);
        assertEquals(expected.maxY(), actual.entity.maxY(), 1e-6f);
    }

    @Test
    public void buildPreservesOrderAndCount() {
        List<VoicePeer> peers = Arrays.asList(
                peer("a", 0f, 0f, 0f), peer("b", 1f, 1f, 1f), peer("c", 2f, 2f, 2f));
        List<PeerHitboxSource.PeerHitbox> boxes = PeerHitboxSource.build(() -> peers);
        assertEquals(3, boxes.size());
        assertEquals("a", boxes.get(0).peerId);
        assertEquals("b", boxes.get(1).peerId);
        assertEquals("c", boxes.get(2).peerId);
        assertFalse(boxes.isEmpty());
    }
}
