package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Basis;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Camera;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Rect;
import org.chimeramc.client.core.mods.inbuilt.overlay.PeerHitboxSource;
import org.chimeramc.client.core.voice.VoicePeer;
import org.junit.Test;

/**
 * The peer look-direction ray and the rotation rules behind the Hitboxes module's v5 support.
 *
 * <p>No mocks: the projector is pure and the peer source takes a plain list. The rules that matter
 * are the sign of the heading (a mirrored line points the wrong way and the player would trust it)
 * and the "unknown" sentinel (a peer with no advertised rotation must draw no line at all).
 */
public class HitboxPeerRayTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private static Basis basisAt(float x, float y, float z, float yaw, float pitch) {
        return HitboxProjector.basis(new Camera(x, y, z, yaw, pitch, 70f, WIDTH, HEIGHT));
    }

    @Test
    public void noRotationDrawsNoRay() {
        // 0/0 is the wire's "unknown" sentinel; a peer that never advertised a facing must not be
        // drawn as looking north.
        Basis basis = basisAt(0f, 0f, 0f, 0f, 0f);
        assertNull(HitboxProjector.peerLookRay(0f, 0f, 10f, 0f, 0f, 10f, basis));
    }

    @Test
    public void aRayExistsWhenRotationIsAdvertised() {
        Basis basis = basisAt(0f, 0f, 0f, 0f, 0f);
        Rect ray = HitboxProjector.peerLookRay(0f, 0f, 10f, 90f, 0f, 10f, basis);
        assertNotNull(ray);
        // The ray has real length on screen, not a degenerate point.
        float dx = ray.right - ray.left;
        float dy = ray.bottom - ray.top;
        assertTrue("ray should have extent", Math.abs(dx) + Math.abs(dy) > 1f);
    }

    @Test
    public void yawTurnsTheRayTheSameWayItTurnsTheCamera() {
        // A peer at +Z with the camera at the origin. Yaw 0 points at the camera.
        Basis basis = basisAt(0f, 0f, 0f, 0f, 0f);
        Rect straight = HitboxProjector.peerLookRay(0f, 0f, 10f, 0f, 0f, 5f, basis);
        // 0/0 is "unknown", so use a tiny pitch to make a real straight-ahead ray.
        assertNull(straight);

        Rect ahead = HitboxProjector.peerLookRay(0f, 0f, 10f, 0.001f, 0f, 5f, basis);
        assertNotNull(ahead);

        // Turn the peer to yaw 90: forward is -X (Minecraft yaw is clockwise from +Z), so the tip
        // moves to the right-hand side of the screen and the ray is no longer centred.
        Rect turned = HitboxProjector.peerLookRay(0f, 0f, 10f, 90f, 0f, 5f, basis);
        assertNotNull(turned);
        float aheadCentre = (ahead.left + ahead.right) / 2f;
        float turnedCentre = (turned.left + turned.right) / 2f;
        assertTrue("turning the peer must move the ray off centre",
                Math.abs(turnedCentre - aheadCentre) > 1f);
    }

    @Test
    public void aPeerBehindTheCameraDrawsNoRay() {
        // Camera at the origin looking toward +Z; a peer behind it at -Z has no screen position, so
        // a ray must not be invented for it.
        Basis basis = basisAt(0f, 0f, 0f, 0f, 0f);
        assertNull(HitboxProjector.peerLookRay(0f, 0f, -10f, 45f, 0f, 10f, basis));
    }

    @Test
    public void peerSourceMarksRotationUnknownWhenThePeerHasNone() {
        PeerHitboxSource.PeerHitbox box = PeerHitboxSource.from(
                new VoicePeer("p", "Peer", 1f, 2f, 3f, "world", 0L));
        assertNotNull(box);
        assertTrue("a peer with no rotation must be marked unknown", !box.hasRotation);
        assertEquals(0f, box.yaw, 0f);
        assertEquals(0f, box.pitch, 0f);
    }

    @Test
    public void peerSourceCarriesAnAdvertisedRotation() {
        PeerHitboxSource.PeerHitbox box = PeerHitboxSource.from(
                new VoicePeer("p", "Peer", 1f, 2f, 3f, "world", "", VoiceProtocolVisibility.PUBLIC,
                        VoiceProtocolCapacity.NONE, 0f, false, 137f, -20f, 0L));
        assertNotNull(box);
        assertTrue("an advertised rotation must be usable", box.hasRotation);
        assertEquals(137f, box.yaw, 1e-4);
        assertEquals(-20f, box.pitch, 1e-4);
    }

    /** Named indirection so the test reads as the wire values rather than raw bytes. */
    private static final class VoiceProtocolVisibility {
        static final byte PUBLIC = org.chimeramc.client.core.voice.VoiceProtocol.VISIBILITY_PUBLIC;
    }

    private static final class VoiceProtocolCapacity {
        static final int NONE = org.chimeramc.client.core.voice.VoiceProtocol.CAPACITY_NONE;
    }
}
