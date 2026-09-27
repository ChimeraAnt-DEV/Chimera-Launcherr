package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The speaking meter is maths over a PCM frame, so it is pinned here without an audio device.
 */
public class VoiceAudioEngineLevelTest {

    /** Builds a constant-amplitude 16-bit little-endian mono frame. */
    private static byte[] tone(int amplitude, int samples) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            pcm[i * 2] = (byte) (amplitude & 0xFF);
            pcm[i * 2 + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        return pcm;
    }

    @Test
    public void silenceHasZeroRms() {
        assertEquals(0f, VoiceAudioEngine.rms(tone(0, 100), 200), 0.0001f);
    }

    @Test
    public void aKnownAmplitudeHasTheExpectedRms() {
        // RMS of a constant signal is its absolute amplitude: 16384 / 32768 = 0.5.
        assertEquals(0.5f, VoiceAudioEngine.rms(tone(16384, 100), 200), 0.001f);
    }

    @Test
    public void fullScaleIsOneAndNegativeSamplesUseMagnitude() {
        assertEquals(1f, VoiceAudioEngine.rms(tone(32767, 50), 100), 0.001f);
        // -32768 must read as the same magnitude as +32768, not wrap to zero.
        assertEquals(1f, VoiceAudioEngine.rms(tone(-32768, 50), 100), 0.001f);
    }

    @Test
    public void degenerateFramesDoNotThrow() {
        assertEquals(0f, VoiceAudioEngine.rms(null, 0), 0.0001f);
        assertEquals(0f, VoiceAudioEngine.rms(new byte[]{1}, 1), 0.0001f);
        assertEquals(0f, VoiceAudioEngine.rms(new byte[0], 0), 0.0001f);
    }

    @Test
    public void aMostlyQuietFrameWithOneSpikeReadsLow() {
        // Peak-based metering would jump to full on this frame; RMS reports the low average
        // energy, which is the whole reason the meter uses RMS instead of peak.
        byte[] pcm = tone(0, 100);
        int spike = 30000;
        pcm[50] = (byte) (spike & 0xFF);
        pcm[51] = (byte) ((spike >> 8) & 0xFF);
        float rms = VoiceAudioEngine.rms(pcm, 200);
        assertTrue("one loud sample in a quiet frame must not read as loud", rms < 0.1f);
    }
}
