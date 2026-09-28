package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins protocol v4: the client id and codec byte the relay adds, the new packet types, and the
 * v1-v3 compatibility that keeps a LAN peer audible to a build that also speaks the relay.
 */
public class VoiceProtocolV4Test {

    @Test
    public void relayBeaconRoundTripsTheClientIdAndCodec() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(4242L, VoiceProtocol.TYPE_BEACON,
                "abc123", "Phone", "team", VoiceProtocol.VISIBILITY_PRIVATE, "Squad",
                8, 0.5f, true, 1f, 64f, -3f, 17, VoiceProtocol.CODEC_OPUS, null);

        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(VoiceProtocol.TYPE_BEACON, p.type);
        assertEquals(4242L, p.clientId);
        assertEquals(VoiceProtocol.CODEC_OPUS, p.codec);
        assertTrue(p.isOpus());
        assertEquals("abc123", p.peerId);
        assertEquals("Phone", p.name);
        assertEquals("team", p.channel);
        assertEquals("Squad", p.channelName);
        assertTrue(p.isPrivate());
        assertEquals(8, p.capacity);
        assertEquals(0.5f, p.level, 1e-6);
        assertTrue(p.muted);
        assertEquals(17, p.sequence);
    }

    @Test
    public void relayAudioCarriesTheOpusPayload() {
        byte[] payload = {9, 8, 7, 6, 5};
        byte[] data = VoiceProtocol.encodeRelayBeacon(7L, VoiceProtocol.TYPE_AUDIO,
                "peer", "Me", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                VoiceProtocol.CAPACITY_NONE, 0f, false, 0f, 0f, 0f, 3,
                VoiceProtocol.CODEC_OPUS, payload);

        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(VoiceProtocol.TYPE_AUDIO, p.type);
        assertArrayEquals(payload, p.payload);
        assertEquals(7L, p.clientId);
    }

    @Test
    public void theWireVersionIsFourAndTheLanVersionIsThree() {
        byte[] relay = VoiceProtocol.encodeRelayBeacon(1L, VoiceProtocol.TYPE_BEACON,
                "p", "n", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, 0, VoiceProtocol.CODEC_PCM, null);
        assertEquals(4, relay[2]);

        byte[] lan = VoiceProtocol.encodeBeacon("p", "n", "world", 0f, 0f, 0f, 0);
        assertEquals(3, lan[2]);
    }

    @Test
    public void helloAckDecodesWithTheAssignedIdAndHeartbeat() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(99L, VoiceProtocol.TYPE_HELLO_ACK,
                "", "relay", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, 3000, VoiceProtocol.CODEC_PCM, new byte[0]);

        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(VoiceProtocol.TYPE_HELLO_ACK, p.type);
        assertEquals(99L, p.clientId);
        assertEquals(3000, p.sequence);
    }

    @Test
    public void helloAndPongHaveDistinctTypes() {
        VoiceProtocol.Packet hello = VoiceProtocol.decode(
                VoiceProtocol.encodeHello("Phone", "world", "secret".getBytes()));
        assertNotNull(hello);
        assertEquals(VoiceProtocol.TYPE_HELLO, hello.type);
        assertEquals("secret", new String(hello.payload));

        VoiceProtocol.Packet pong = VoiceProtocol.decode(VoiceProtocol.encodePong(5L));
        assertNotNull(pong);
        assertEquals(VoiceProtocol.TYPE_PONG, pong.type);
        assertEquals(5L, pong.clientId);
    }

    @Test
    public void noticeCarriesItsReason() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(0L, VoiceProtocol.TYPE_NOTICE,
                "", "", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, VoiceProtocol.NOTICE_SERVER_FULL,
                VoiceProtocol.CODEC_PCM, "full".getBytes());
        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(VoiceProtocol.TYPE_NOTICE, p.type);
        assertEquals(VoiceProtocol.NOTICE_SERVER_FULL, p.sequence);
        assertEquals("full", new String(p.payload));
    }

    @Test
    public void anUnknownPacketTypeIsRejected() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(1L, VoiceProtocol.TYPE_BEACON,
                "p", "n", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, 0, VoiceProtocol.CODEC_PCM, null);
        data[3] = 99; // corrupt the type byte
        assertNull(VoiceProtocol.decode(data));
    }

    @Test
    public void aVersionFivePacketIsRejected() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(1L, VoiceProtocol.TYPE_BEACON,
                "p", "n", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, 0, VoiceProtocol.CODEC_PCM, null);
        data[2] = 5;
        assertNull(VoiceProtocol.decode(data));
    }

    @Test
    public void anUnknownCodecByteReadsAsPcm() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(1L, VoiceProtocol.TYPE_AUDIO,
                "p", "n", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, 0, VoiceProtocol.CODEC_OPUS, new byte[]{1});
        // Layout ends: ... codec byte, 4-byte payload length, payload. With a 1-byte payload the
        // codec sits six bytes from the end.
        data[data.length - 6] = 42;
        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        assertEquals(VoiceProtocol.CODEC_PCM, p.codec);
        assertFalse(p.isOpus());
    }

    @Test
    public void aTruncatedV4PacketIsRejected() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(1L, VoiceProtocol.TYPE_AUDIO,
                "p", "n", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                0, 0f, false, 0f, 0f, 0f, 0, VoiceProtocol.CODEC_OPUS, new byte[]{1, 2, 3, 4});
        byte[] truncated = new byte[data.length - 2];
        System.arraycopy(data, 0, truncated, 0, truncated.length);
        assertNull(VoiceProtocol.decode(truncated));
    }

    @Test
    public void aV3PacketStillDecodesWithTheV4Decoder() {
        // The whole compatibility promise: a LAN peer on the older format is still understood.
        byte[] legacy = VoiceProtocol.encodeBeacon("lanpeer", "Lan", "world",
                VoiceProtocol.VISIBILITY_PUBLIC, "", 4, 0.3f, false, 5f, 6f, 7f, 9);
        VoiceProtocol.Packet p = VoiceProtocol.decode(legacy);
        assertNotNull(p);
        assertEquals(VoiceProtocol.TYPE_BEACON, p.type);
        assertEquals("lanpeer", p.peerId);
        assertEquals(0L, p.clientId); // no id on the v3 wire
        assertEquals(VoiceProtocol.CODEC_PCM, p.codec);
        assertEquals(4, p.capacity);
    }

    @Test
    public void withPayloadKeepsEveryOtherField() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(11L, VoiceProtocol.TYPE_AUDIO,
                "p", "n", "team", VoiceProtocol.VISIBILITY_PRIVATE, "Squad",
                5, 0.25f, true, 1f, 2f, 3f, 44, VoiceProtocol.CODEC_OPUS, new byte[]{1});
        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        assertNotNull(p);
        VoiceProtocol.Packet copy = p.withPayload(new byte[]{9, 9});
        assertEquals(p.type, copy.type);
        assertEquals(p.clientId, copy.clientId);
        assertEquals(p.peerId, copy.peerId);
        assertEquals(p.sequence, copy.sequence);
        assertEquals(p.codec, copy.codec);
        assertEquals(p.channelName, copy.channelName);
        assertArrayEquals(new byte[]{9, 9}, copy.payload);
    }
}
