package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * The wire format is the one piece of the voice link that must survive a hostile or foreign
 * datagram, so a round trip of every packet type plus the malformed cases are pinned here.
 */
public class VoiceProtocolTest {

    @Test
    public void beaconRoundTrips() {
        byte[] encoded = VoiceProtocol.encodeBeacon("abc123", "Phone", "team",
                1.5f, 64f, -3.25f, 7);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.TYPE_BEACON, packet.type);
        assertEquals("abc123", packet.peerId);
        assertEquals("Phone", packet.name);
        assertEquals("team", packet.channel);
        assertEquals(1.5f, packet.x, 0.0001f);
        assertEquals(64f, packet.y, 0.0001f);
        assertEquals(-3.25f, packet.z, 0.0001f);
        assertEquals(7, packet.sequence);
        assertEquals(0, packet.payload.length);
    }

    @Test
    public void audioRoundTripsWithItsPayload() {
        byte[] audio = {1, 2, 3, 4, 5, 6};
        byte[] encoded = VoiceProtocol.encodeAudio("id", "Name", "world",
                0f, 0f, 0f, 3, audio);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.TYPE_AUDIO, packet.type);
        assertEquals(audio.length, packet.payload.length);
        for (int i = 0; i < audio.length; i++) {
            assertEquals(audio[i], packet.payload[i]);
        }
    }

    @Test
    public void byeRoundTrips() {
        byte[] encoded = VoiceProtocol.encodeBye("id", "Name", "world", 0f, 0f, 0f);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.TYPE_BYE, packet.type);
    }

    @Test
    public void blankChannelIsNormalisedToWorldOnTheWire() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "",
                0f, 0f, 0f, 0);
        assertEquals(VoiceChannel.WORLD, VoiceProtocol.decode(encoded).channel);
    }

    @Test
    public void aForeignDatagramIsRejected() {
        // Leading bytes are not our magic.
        assertNull(VoiceProtocol.decode(new byte[]{'X', 'Y', 1, 2, 3}));
    }

    @Test
    public void aWrongVersionIsRejected() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "world", 0f, 0f, 0f, 0);
        encoded[2] = 99;
        assertNull(VoiceProtocol.decode(encoded));
    }

    @Test
    public void aTruncatedDatagramIsRejected() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "world", 0f, 0f, 0f, 0);
        byte[] truncated = new byte[encoded.length - 4];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);
        assertNull(VoiceProtocol.decode(truncated));
        assertNull(VoiceProtocol.decode(null));
        assertNull(VoiceProtocol.decode(new byte[0]));
    }

    @Test
    public void anOverlongNameIsTruncatedNotRejected() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 600; i++) longName.append('a');
        byte[] encoded = VoiceProtocol.encodeBeacon("id", longName.toString(), "world",
                0f, 0f, 0f, 0);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(255, packet.name.length());
    }

    @Test
    public void oversizedAudioIsClampedToThePayloadLimit() {
        byte[] huge = new byte[VoiceProtocol.MAX_PAYLOAD + 500];
        byte[] encoded = VoiceProtocol.encodeAudio("id", "Name", "world", 0f, 0f, 0f, 1, huge);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.MAX_PAYLOAD, packet.payload.length);
    }

    @Test
    public void registryEvictsStalePeers() {
        VoiceRegistry registry = new VoiceRegistry();
        registry.put(new VoicePeer("a", "A", 0f, 0f, 0f, "world", 1000L));
        registry.put(new VoicePeer("b", "B", 0f, 0f, 0f, "world", 9000L));
        registry.evictStale(9000L);
        assertEquals(1, registry.size());
        assertEquals("b", registry.peers().iterator().next().id);
    }

    @Test
    public void registryOnlyReturnsAudiblePeers() {
        VoiceRegistry registry = new VoiceRegistry();
        registry.put(new VoicePeer("near", "Near", 1f, 0f, 0f, "team", 0L));
        registry.put(new VoicePeer("far", "Far", 500f, 0f, 0f, "team", 0L));
        registry.put(new VoicePeer("other", "Other", 1f, 0f, 0f, "blue", 0L));

        // A listener on a private channel hears its own channel and the open one, but not a
        // different private channel; the far peer is out of range.
        List<VoiceRegistry.Audible> audible =
                registry.audible(0f, 0f, 0f, "team", 12f);
        assertEquals(1, audible.size());
        assertEquals("near", audible.get(0).peer.id);
        assertTrue(audible.get(0).gain > 0f);
    }

    @Test
    public void aWorldListenerHearsEveryChannelInRange() {
        VoiceRegistry registry = new VoiceRegistry();
        registry.put(new VoicePeer("a", "A", 1f, 0f, 0f, "blue", 0L));
        registry.put(new VoicePeer("b", "B", 2f, 0f, 0f, "team", 0L));
        registry.put(new VoicePeer("far", "Far", 500f, 0f, 0f, "world", 0L));

        // The open channel reaches across; only the out-of-range peer is omitted.
        assertEquals(2, registry.audible(0f, 0f, 0f, VoiceChannel.WORLD, 12f).size());
    }

    @Test
    public void beaconCarriesVisibilityAndChannelName() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "chimera-7f2q",
                VoiceProtocol.VISIBILITY_PRIVATE, "Squad", 1f, 2f, 3f, 4);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.VISIBILITY_PRIVATE, packet.visibility);
        assertEquals("Squad", packet.channelName);
        assertEquals("chimera-7f2q", packet.channel);
        assertTrue(packet.isPrivate());
    }

    @Test
    public void publicIsTheDefaultVisibilityOnTheWire() {
        byte[] encoded = VoiceProtocol.encodeBeacon("id", "Name", "world", 0f, 0f, 0f, 0);
        VoiceProtocol.Packet packet = VoiceProtocol.decode(encoded);
        assertEquals(VoiceProtocol.VISIBILITY_PUBLIC, packet.visibility);
        assertEquals("", packet.channelName);
    }

    @Test
    public void aVersionOneDatagramStillDecodesAsPublicWorld() {
        // Hand-build a v1 beacon: magic, version 1, type, three strings, three floats, seq, len.
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(buffer);
        try {
            out.write(new byte[]{'C', 'V'});
            out.writeByte(1);
            out.writeByte(VoiceProtocol.TYPE_BEACON);
            writeLegacyString(out, "oldpeer");
            writeLegacyString(out, "Old Phone");
            writeLegacyString(out, "world");
            out.writeFloat(1f);
            out.writeFloat(2f);
            out.writeFloat(3f);
            out.writeInt(9);
            out.writeInt(0);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        VoiceProtocol.Packet packet = VoiceProtocol.decode(buffer.toByteArray());
        assertEquals("oldpeer", packet.peerId);
        assertEquals("world", packet.channel);
        assertEquals(VoiceProtocol.VISIBILITY_PUBLIC, packet.visibility);
        assertEquals("", packet.channelName);
        assertTrue(packet.sequence == 9);
    }

    private static void writeLegacyString(java.io.DataOutputStream out, String value)
            throws java.io.IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    @Test
    public void normalizeVisibilityClampsUnknownBytesToPublic() {
        assertEquals(VoiceProtocol.VISIBILITY_PUBLIC, VoiceProtocol.normalizeVisibility((byte) 9));
        assertEquals(VoiceProtocol.VISIBILITY_PRIVATE, VoiceProtocol.normalizeVisibility((byte) 1));
    }
}
