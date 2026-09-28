package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the mixer's summing and clipping. Two talkers at once is the case the relay makes normal,
 * and interleaving their frames instead of summing them is what it must not do.
 */
public class VoiceMixerTest {

    /** A PCM frame of {@code frames} samples whose first sample is {@code sample}. */
    private static byte[] frame(int sample, int frames) {
        byte[] out = new byte[frames * 2];
        putSample(out, 0, sample);
        return out;
    }

    /** A PCM frame carrying the given sample values, one per sample. */
    private static byte[] samples(int... values) {
        byte[] out = new byte[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            putSample(out, i, values[i]);
        }
        return out;
    }

    private static void putSample(byte[] pcm, int index, int sample) {
        pcm[index * 2] = (byte) (sample & 0xFF);
        pcm[index * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }

    private static int sampleAt(byte[] pcm, int index) {
        return (short) ((pcm[index * 2] & 0xFF) | (pcm[index * 2 + 1] << 8));
    }

    @Test
    public void twoTalkersAreSummed() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        mixer.add(frame(1000, 2), 4, 1f);
        mixer.add(frame(2000, 2), 4, 1f);

        byte[] mixed = mixer.mix();
        assertNotNull(mixed);
        assertEquals(3000, sampleAt(mixed, 0));
    }

    @Test
    public void gainsAreAppliedBeforeSumming() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        mixer.add(frame(1000, 2), 4, 0.5f);
        mixer.add(frame(1000, 2), 4, 0.25f);

        byte[] mixed = mixer.mix();
        assertNotNull(mixed);
        assertEquals(750, sampleAt(mixed, 0));
    }

    @Test
    public void theSumSaturatesInsteadOfWrapping() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        mixer.add(frame(30000, 2), 4, 1f);
        mixer.add(frame(30000, 2), 4, 1f);

        byte[] mixed = mixer.mix();
        assertNotNull(mixed);
        // Wrapping would turn this into a large negative sample, which is loud noise.
        assertEquals(Short.MAX_VALUE, sampleAt(mixed, 0));
    }

    @Test
    public void theSumSaturatesAtTheNegativeRailToo() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        mixer.add(frame(-30000, 2), 4, 1f);
        mixer.add(frame(-30000, 2), 4, 1f);

        byte[] mixed = mixer.mix();
        assertNotNull(mixed);
        assertEquals(Short.MIN_VALUE, sampleAt(mixed, 0));
    }

    @Test
    public void aSingleFullGainSourceIsPassedThroughUnchanged() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        byte[] input = frame(1234, 2);
        mixer.add(input, 4, 1f);

        byte[] mixed = mixer.mix();
        assertSame("one full-gain talker must not allocate a new frame", input, mixed);
    }

    @Test
    public void anEmptyMixerProducesNothing() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        assertNull(mixer.mix());
    }

    @Test
    public void aZeroGainSourceContributesNothing() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        mixer.add(frame(1000, 2), 4, 0f);
        assertNull("a muted peer must not create a frame", mixer.mix());
    }

    @Test
    public void aShortSourceIsZeroPaddedToTheFrameSize() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(8); // four samples
        mixer.add(samples(1000), 2, 1f); // only one sample, not four
        mixer.add(samples(500, 600, 700, 800), 8, 1f);

        byte[] mixed = mixer.mix();
        assertNotNull(mixed);
        assertEquals(8, mixed.length);
        assertEquals(1500, sampleAt(mixed, 0));
        assertEquals(600, sampleAt(mixed, 1));
        assertEquals(700, sampleAt(mixed, 2));
        assertEquals(800, sampleAt(mixed, 3));
    }

    @Test
    public void mixIntoDeliversTheFrameAndClearsTheQueue() {
        VoiceMixer mixer = new VoiceMixer();
        mixer.setFrameBytes(4);
        mixer.add(frame(1000, 2), 4, 1f);
        mixer.add(frame(1000, 2), 4, 1f);

        final int[] received = {0};
        boolean produced = mixer.mixInto((pcm, len) -> received[0] = sampleAt(pcm, 0));
        assertTrue(produced);
        assertEquals(2000, received[0]);
        assertEquals(0, mixer.size());
        assertNull(mixer.mix());
    }
}
