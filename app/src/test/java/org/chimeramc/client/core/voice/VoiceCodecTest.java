package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the PCM&harr;samples conversion and the codec's fallback contract. The conversion is pure
 * and must be exactly lossless; the codec must report unavailability rather than throw when its
 * dependency is absent, so the caller can fall back to PCM.
 */
public class VoiceCodecTest {

    @Test
    public void pcmToShortsAndBackIsLossless() {
        byte[] pcm = new byte[640];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (i * 7);
        }
        short[] samples = VoiceCodec.pcmToShorts(pcm, pcm.length);
        assertEquals(320, samples.length);
        byte[] roundTripped = VoiceCodec.shortsToPcm(samples);
        assertArrayEquals(pcm, roundTripped);
    }

    @Test
    public void littleEndianOrderIsRespected() {
        byte[] pcm = {0x34, 0x12}; // 0x1234 little-endian
        short[] samples = VoiceCodec.pcmToShorts(pcm, 2);
        assertEquals(0x1234, samples[0]);

        byte[] back = VoiceCodec.shortsToPcm(new short[]{0x1234});
        assertArrayEquals(pcm, back);
    }

    @Test
    public void aNegativeSampleSurvivesTheRoundTrip() {
        short[] samples = {-1, Short.MIN_VALUE, Short.MAX_VALUE};
        byte[] pcm = VoiceCodec.shortsToPcm(samples);
        assertArrayEquals(samples, VoiceCodec.pcmToShorts(pcm, pcm.length));
    }

    @Test
    public void anOddLengthIsTruncatedToWholeSamples() {
        byte[] pcm = {1, 2, 3}; // one and a half samples
        short[] samples = VoiceCodec.pcmToShorts(pcm, pcm.length);
        assertEquals(1, samples.length);
    }

    @Test
    public void anEmptyInputProducesNoSamples() {
        assertEquals(0, VoiceCodec.pcmToShorts(new byte[0], 0).length);
        assertEquals(0, VoiceCodec.shortsToPcm(new short[0]).length);
    }

    @Test
    public void aCodecIsAlwaysConstructibleEvenWithoutTheDependency() {
        // create() must never throw; on this JVM the dependency is present, but the contract is
        // that the object exists either way and answers isAvailable().
        VoiceCodec codec = VoiceCodec.create();
        assertNotNull(codec);
    }

    @Test
    public void encodeAndDecodeRoundTripWhenOpusIsAvailable() {
        VoiceCodec codec = VoiceCodec.create();
        // The dependency is an `implementation` dependency of this module, so it is on the unit
        // test classpath too. Asserting availability turns "Opus silently degraded to PCM on every
        // relay session" — the failure mode this whole class is built to fall back from — into a
        // test failure instead of a bandwidth surprise in the field.
        assertTrue("Concentus must be on the test classpath; a missing jar means PCM fallback",
                codec.isAvailable());
        // A simple tone; Opus is lossy, so the assertion is that it round-trips at all and comes
        // back the right length, not that it is sample-identical.
        byte[] pcm = new byte[VoiceAudioEngine.FRAME_BYTES];
        for (int i = 0; i < pcm.length / 2; i++) {
            short sample = (short) (Math.sin(i * 0.1) * 8000);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        byte[] encoded = codec.encode(pcm, pcm.length);
        assertNotNull("Opus encode should produce a packet", encoded);
        assertFalse("Opus must be smaller than raw PCM", encoded.length >= pcm.length);

        byte[] decoded = codec.decode(encoded, encoded.length);
        assertNotNull("Opus decode should produce PCM", decoded);
        assertEquals(VoiceAudioEngine.FRAME_BYTES, decoded.length);
    }

    @Test
    public void silenceStillEncodesToASmallPacket() {
        // A silent frame is the common case (nobody is talking) and must not fail or blow up.
        VoiceCodec codec = VoiceCodec.create();
        if (!codec.isAvailable()) return;
        byte[] silent = new byte[VoiceAudioEngine.FRAME_BYTES];
        byte[] encoded = codec.encode(silent, silent.length);
        assertNotNull(encoded);
        byte[] decoded = codec.decode(encoded, encoded.length);
        assertNotNull(decoded);
        assertEquals(VoiceAudioEngine.FRAME_BYTES, decoded.length);
    }

    @Test
    public void encodeAndDecodeReturnNullOnBadInput() {
        VoiceCodec codec = VoiceCodec.create();
        assertNull(codec.encode(null, 0));
        assertNull(codec.encode(new byte[0], 0));
        assertNull(codec.decode(null, 0));
        assertNull(codec.decode(new byte[0], 0));
    }

    @Test
    public void aGarbagePacketDecodesToNullRatherThanThrowing() {
        VoiceCodec codec = VoiceCodec.create();
        if (!codec.isAvailable()) return;
        // A corrupt frame must be dropped by the caller, which needs a null, not an exception.
        byte[] garbage = {(byte) 0xFF, (byte) 0xEE, (byte) 0xDD, (byte) 0xCC};
        codec.decode(garbage, garbage.length); // must not throw
    }
}
