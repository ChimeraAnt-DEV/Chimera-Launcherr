package org.chimeramc.client.core.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * Sums several peers' PCM frames into one output frame.
 *
 * <p>On a LAN with a handful of talkers, writing each frame straight to the {@code AudioTrack} as
 * it arrives mostly works. It stops working when more than one person talks at once: two
 * concurrent {@code write} calls interleave their samples into the same stream, which is not a mix
 * but a splice, and the result is a stutter rather than two voices. The relay makes simultaneous
 * talkers normal, so the frames have to be summed on a single clock.
 *
 * <p>The mix is a saturating sum: each source is scaled by its own gain first (distance, channel,
 * local mute, volume), then the samples are added and clamped. Clamping rather than normalising
 * keeps a quiet peer audible when a loud one is also talking — normalising would duck everyone to
 * the loudest source, which reads as the quiet player cutting out.
 *
 * <p>Pure arithmetic over arrays, so the summing and the clipping are unit-tested directly.
 */
public final class VoiceMixer {

    /** One frame from one peer: its PCM and the gain it should be heard at. */
    public static final class Source {
        public final byte[] pcm;
        public final int length;
        public final float gain;

        public Source(byte[] pcm, int length, float gain) {
            this.pcm = pcm;
            this.length = length;
            this.gain = gain;
        }
    }

    private final List<Source> sources = new ArrayList<>();
    private int frameBytes = VoiceAudioEngine.FRAME_BYTES;

    /** Sets the output frame size; a source shorter than this is treated as ending early. */
    public void setFrameBytes(int frameBytes) {
        if (frameBytes > 0) this.frameBytes = frameBytes;
    }

    public int frameBytes() {
        return frameBytes;
    }

    public void add(byte[] pcm, int length, float gain) {
        if (pcm == null || length <= 0 || gain <= 0f) return;
        sources.add(new Source(pcm, Math.min(length, pcm.length), gain));
    }

    public void clear() {
        sources.clear();
    }

    public int size() {
        return sources.size();
    }

    /**
     * Mixes the queued sources into one frame, or returns null when nothing was queued.
     *
     * <p>The output is always {@link #frameBytes()} long so the playback clock stays even; a source
     * that ran short simply stops contributing after its last sample. With a single source at full
     * gain the input is returned unchanged, so the common one-talker case allocates nothing.
     */
    public byte[] mix() {
        if (sources.isEmpty()) return null;
        if (sources.size() == 1) {
            Source only = sources.get(0);
            if (only.gain >= 0.999f && only.length >= frameBytes) {
                return only.pcm;
            }
        }

        int[] accumulator = new int[frameBytes / 2];
        for (Source source : sources) {
            float gain = Math.min(source.gain, 1f);
            int samples = Math.min(source.length, frameBytes) / 2;
            for (int i = 0; i < samples; i++) {
                int sample = (short) ((source.pcm[i * 2] & 0xFF) | (source.pcm[i * 2 + 1] << 8));
                accumulator[i] += Math.round(sample * gain);
            }
        }

        byte[] out = new byte[frameBytes];
        for (int i = 0; i < accumulator.length; i++) {
            int value = accumulator[i];
            if (value > Short.MAX_VALUE) value = Short.MAX_VALUE;
            if (value < Short.MIN_VALUE) value = Short.MIN_VALUE;
            out[i * 2] = (byte) (value & 0xFF);
            out[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return out;
    }

    /**
     * Mixes a batch and hands the result to a sink, clearing the queue.
     *
     * @return true when a frame was produced
     */
    public boolean mixInto(FrameConsumer consumer) {
        byte[] mixed = mix();
        clear();
        if (mixed == null) return false;
        if (consumer != null) consumer.accept(mixed, mixed.length);
        return true;
    }

    /** Receives a mixed frame. */
    public interface FrameConsumer {
        void accept(byte[] pcm, int length);
    }
}
