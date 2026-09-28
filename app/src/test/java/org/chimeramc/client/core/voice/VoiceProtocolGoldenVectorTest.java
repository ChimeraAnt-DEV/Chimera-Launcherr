package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the exact v4 wire bytes the Go relay must parse.
 *
 * <p>The relay is a separate program, so a field-order mistake on one side is invisible until a
 * real client and a real server try to talk. This test fixes the bytes; the Go test
 * {@code TestParseGoldenBeacon}/{@code TestParseGoldenAudio} in {@code protocol_test.go} asserts
 * the same literals parse into the expected fields. If either layout changes, one of the two
 * suites fails rather than the mismatch shipping.
 */
public class VoiceProtocolGoldenVectorTest {

    /** A private team beacon: every field non-default, so an off-by-one shows up somewhere. */
    private static final String BEACON_HEX =
                    "435604010000000000001092066162633132330550686F6E65047465616D010553717561640000"
                    + "00083F000000013F80000042800000C0400000000000110100000000";

    private static final String AUDIO_HEX =
                    "4356040200000000000000070470656572024D6505776F726C6400000000000000000000000000"
                    + "000000000000000000000000000301000000050908070605";

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    @Test
    public void theBeaconBytesAreExactlyAsTheRelayExpectsThem() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(4242L, VoiceProtocol.TYPE_BEACON,
                "abc123", "Phone", "team", VoiceProtocol.VISIBILITY_PRIVATE, "Squad",
                8, 0.5f, true, 1f, 64f, -3f, 17, VoiceProtocol.CODEC_OPUS, null);
        assertEquals(BEACON_HEX, hex(data));
    }

    @Test
    public void theAudioBytesAreExactlyAsTheRelayExpectsThem() {
        byte[] data = VoiceProtocol.encodeRelayBeacon(7L, VoiceProtocol.TYPE_AUDIO,
                "peer", "Me", "world", VoiceProtocol.VISIBILITY_PUBLIC, "",
                VoiceProtocol.CAPACITY_NONE, 0f, false, 0f, 0f, 0f, 3,
                VoiceProtocol.CODEC_OPUS, new byte[]{9, 8, 7, 6, 5});
        assertEquals(AUDIO_HEX, hex(data));
    }

    @Test
    public void theGoldenVectorsStillDecodeToTheRightFields() {
        VoiceProtocol.Packet beacon = VoiceProtocol.decode(hexToBytes(BEACON_HEX));
        assertNotNull(beacon);
        assertEquals(4242L, beacon.clientId);
        assertEquals("abc123", beacon.peerId);
        assertEquals("Phone", beacon.name);
        assertEquals("team", beacon.channel);
        assertEquals("Squad", beacon.channelName);
        assertEquals(8, beacon.capacity);
        assertTrue(beacon.muted);
        assertTrue(beacon.isOpus());
        assertEquals(17, beacon.sequence);

        VoiceProtocol.Packet audio = VoiceProtocol.decode(hexToBytes(AUDIO_HEX));
        assertNotNull(audio);
        assertEquals(7L, audio.clientId);
        assertEquals("peer", audio.peerId);
        assertEquals("world", audio.channel);
        assertEquals(3, audio.sequence);
        assertEquals(5, audio.payload.length);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
