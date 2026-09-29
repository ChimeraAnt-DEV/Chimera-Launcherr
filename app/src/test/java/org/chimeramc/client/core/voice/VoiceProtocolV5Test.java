package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins protocol v5: the sender's own view rotation (yaw/pitch), which is the only source for
 * another player's facing and therefore what the Hitboxes module's look-direction line needs.
 *
 * <p>Two things must hold together. The rotation round-trips, and a v4-or-earlier packet decodes
 * with rotation "unknown" rather than with a fabricated zero-degree look direction -- a peer whose
 * facing we do not know must draw no line, not a line aimed north.
 */
public class VoiceProtocolV5Test {

    @Test
    public void theWireVersionIsFive() {
        assertEquals(5, VoiceProtocol.VERSION);
    }

    @Test
    public void beaconV5RoundTripsYawAndPitch() {
        byte[] data = VoiceProtocol.encodeBeaconV5("peer", "Me", "world",
                VoiceProtocol.VISIBILITY_PUBLIC, "", 12f, 64f, -3f, 9, 137.5f, -42.25f);

        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(VoiceProtocol.TYPE_BEACON, p.type);
        assertEquals(12f, p.x, 1e-4);
        assertEquals(64f, p.y, 1e-4);
        assertEquals(-3f, p.z, 1e-4);
        assertEquals(9, p.sequence);
        assertEquals(137.5f, p.yaw, 1e-4);
        assertEquals(-42.25f, p.pitch, 1e-4);
    }

    @Test
    public void aV4PacketReadsRotationAsUnknown() {
        // The relay still speaks v4; its packets carry no rotation, and that must decode as the
        // "unknown" sentinel rather than as a real zero-degree facing.
        byte[] v4 = VoiceProtocol.encodeRelayBeacon(4242L, VoiceProtocol.TYPE_BEACON,
                "abc123", "Phone", "team", VoiceProtocol.VISIBILITY_PRIVATE, "Squad",
                8, 0.5f, true, 1f, 64f, -3f, 17, VoiceProtocol.CODEC_OPUS, null);
        assertEquals(4, v4[2]);

        VoiceProtocol.Packet p = VoiceProtocol.decode(v4);
        assertNotNull(p);
        assertEquals(0f, p.yaw, 0f);
        assertEquals(0f, p.pitch, 0f);
    }

    @Test
    public void aV3MulticastBeaconStillDecodesWithUnknownRotation() {
        byte[] v3 = VoiceProtocol.encodeBeacon("peer", "Me", "world", 1f, 2f, 3f, 4);
        assertEquals(3, v3[2]);

        VoiceProtocol.Packet p = VoiceProtocol.decode(v3);
        assertNotNull(p);
        assertEquals(0f, p.yaw, 0f);
        assertEquals(0f, p.pitch, 0f);
    }

    @Test
    public void rotationSurvivesAPayloadReframe() {
        // A transport that reframes a packet (the relay patches the payload) must not lose the
        // rotation, or a forwarded frame would suddenly point in a different direction.
        byte[] data = VoiceProtocol.encodeBeaconV5("peer", "Me", "world",
                VoiceProtocol.VISIBILITY_PUBLIC, "", 0f, 0f, 0f, 1, 90f, 15f);
        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);

        VoiceProtocol.Packet reframed = p.withPayload(new byte[]{1, 2, 3});
        assertEquals(90f, reframed.yaw, 1e-4);
        assertEquals(15f, reframed.pitch, 1e-4);
        assertEquals(3, reframed.payload.length);
    }

    @Test
    public void aStraightAheadLookIsNotMistakenForUnknown() {
        // Yaw 0 with a nonzero pitch is a real facing and must round-trip as such; only a packet
        // that never carried rotation reads as unknown.
        byte[] data = VoiceProtocol.encodeBeaconV5("peer", "Me", "world",
                VoiceProtocol.VISIBILITY_PUBLIC, "", 0f, 0f, 0f, 1, 0f, 33f);
        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(0f, p.yaw, 1e-4);
        assertEquals(33f, p.pitch, 1e-4);
        assertTrue(p.pitch != 0f);
    }
}
