package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Protocol v3 adds capacity, live level and self-mute to the beacon and audio packets without
 * changing who can hear whom. These tests pin both halves of that promise: the new fields survive
 * a round trip, and a v2 or v1 peer still decodes with neutral defaults so it stays audible.
 */
public class VoiceProtocolV3Test {

    @Test
    public void capacityLevelAndMuteRoundTripOnABeacon() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "team",
                VoiceProtocol.VISIBILITY_PUBLIC, "Team", 12, 0.6f, true,
                1f, 2f, 3f, 4);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(12, packet.capacity);
        assertEquals(0.6f, packet.level, 0.0001f);
        assertTrue(packet.muted);
        assertEquals("Team", packet.channelName);
    }

    @Test
    public void capacityLevelAndMuteRoundTripOnAudio() {
        byte[] audio = {9, 8, 7};
        byte[] encoded = VoiceProtocol.encodeAudio("id", "Name", "team",
                VoiceProtocol.VISIBILITY_PRIVATE, "Squad", 4, 0.25f, false,
                0f, 0f, 0f, 1, audio);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(4, packet.capacity);
        assertEquals(0.25f, packet.level, 0.0001f);
        assertFalse(packet.muted);
        assertEquals(audio.length, packet.payload.length);
    }

    @Test
    public void aLevelAboveOneIsClampedAndNaNReadsAsZero() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "team",
                VoiceProtocol.VISIBILITY_PUBLIC, "", 0, 5f, false, 0f, 0f, 0f, 0);
        assertEquals(1f, VoiceProtocol.decode(encoded).level, 0.0001f);
        assertEquals(0f, VoiceProtocol.clampLevel(Float.NaN), 0.0001f);
        assertEquals(0f, VoiceProtocol.clampLevel(-1f), 0.0001f);
    }

    @Test
    public void capacityIsClampedToTheWireMaximum() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "team",
                VoiceProtocol.VISIBILITY_PUBLIC, "", 9999, 0f, false, 0f, 0f, 0f, 0);
        assertEquals(VoiceProtocol.MAX_CAPACITY, VoiceProtocol.decode(encoded).capacity);
        assertEquals(VoiceProtocol.CAPACITY_NONE, VoiceProtocol.normalizeCapacity(0));
        assertEquals(VoiceProtocol.CAPACITY_NONE, VoiceProtocol.normalizeCapacity(-3));
    }

    @Test
    public void aVersionTwoDatagramStillDecodesWithNeutralV3Fields() {
        // Hand-build a v2 beacon: magic, version 2, type, three strings, visibility, name,
        // three floats, seq, len. It carries no capacity/level/mute.
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(buffer);
        try {
            out.write(new byte[]{'C', 'V'});
            out.writeByte(2);
            out.writeByte(VoiceProtocol.TYPE_BEACON);
            writeString(out, "v2peer");
            writeString(out, "V2 Phone");
            writeString(out, "team");
            out.writeByte(VoiceProtocol.VISIBILITY_PRIVATE);
            writeString(out, "Squad");
            out.writeFloat(1f);
            out.writeFloat(2f);
            out.writeFloat(3f);
            out.writeInt(11);
            out.writeInt(0);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        VoiceProtocol.Packet packet = VoiceProtocol.decode(buffer.toByteArray());
        assertEquals("v2peer", packet.peerId);
        assertEquals(VoiceProtocol.VISIBILITY_PRIVATE, packet.visibility);
        assertEquals(VoiceProtocol.CAPACITY_NONE, packet.capacity);
        assertEquals(0f, packet.level, 0.0001f);
        assertFalse(packet.muted);
    }

    @Test
    public void aLegacyBeaconDefaultsToNoCapacityZeroLevelAndNotMuted() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "world", 0f, 0f, 0f, 0);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.CAPACITY_NONE, packet.capacity);
        assertEquals(0f, packet.level, 0.0001f);
        assertFalse(packet.muted);
    }

    @Test
    public void aTruncatedVersionThreeBodyIsRejected() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "team",
                VoiceProtocol.VISIBILITY_PUBLIC, "Team", 8, 0.5f, true,
                1f, 2f, 3f, 4);
        // Cut into the capacity/level/mute block; decode must reject rather than read past it.
        byte[] truncated = new byte[encoded.length - 14];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);
        assertNull(VoiceProtocol.decode(truncated));
    }

    private static void writeString(java.io.DataOutputStream out, String value)
            throws java.io.IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeByte(bytes.length);
        out.write(bytes);
    }
}
