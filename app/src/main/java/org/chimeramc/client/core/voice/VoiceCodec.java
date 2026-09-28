package org.chimeramc.client.core.voice;

/**
 * Encodes and decodes 20&nbsp;ms frames of 16&nbsp;kHz mono audio between raw PCM and Opus.
 *
 * <p>This is the seam that keeps {@link VoiceProtocol} and every transport free of a codec
 * dependency: the protocol carries an opaque payload plus a {@link VoiceProtocol#CODEC_OPUS} or
 * {@link VoiceProtocol#CODEC_PCM} byte, and only this class knows what the bytes mean.
 *
 * <p><b>Why Opus on the relay and PCM on the LAN.</b> Raw PCM at 16&nbsp;kHz mono is 640 bytes per
 * 20&nbsp;ms frame — about 256&nbsp;kbit/s per talker. On a shared LAN that is free, and it needs no
 * codec. Over the internet it is the difference between a trickle and a stream, so the relay path
 * encodes: Opus at ~24&nbsp;kbit/s is roughly a tenth of the bandwidth for voice-quality audio. The
 * two paths therefore use different codecs and advertise which one a frame is in, so a listener
 * decodes correctly no matter which transport delivered it.
 *
 * <p>The implementation is Concentus, a pure-Java port of libopus (BSD-3, compatible with this
 * project's Apache-2.0). Pure Java matters: a native libopus would need one build per ABI, and this
 * APK ships arm64 only. If the codec cannot be constructed for any reason, {@link #isAvailable()}
 * is false and the caller falls back to PCM — voice keeps working, just at higher bandwidth.
 */
public final class VoiceCodec {

    private static final int SAMPLE_RATE = VoiceAudioEngine.SAMPLE_RATE;
    private static final int CHANNELS = 1;
    /** 20 ms at 16 kHz, matching {@link VoiceAudioEngine#FRAME_SAMPLES}. */
    private static final int FRAME_SAMPLES = VoiceAudioEngine.FRAME_SAMPLES;
    private static final int MAX_PACKET_BYTES = 512;
    /** Bits per second the encoder targets: voice-clear, far below raw PCM's 256 kbit/s. */
    private static final int BITRATE = 24000;

    private final OpusEncoderRef encoder;
    private final OpusDecoderRef decoder;
    private final boolean available;

    /**
     * A tiny reflective indirection over Concentus, so this class links even if the dependency is
     * absent and the JVM unit tests never need the codec on the classpath.
     *
     * <p>Reflection here is deliberate and narrow: it is the difference between "the relay path
     * degrades to PCM" and "the app fails to start". A hard import would make the whole voice
     * package un-loadable without the jar, which is exactly the fragility a fallback is meant to
     * avoid. The cost is one reflection call at construction, never per frame.
     */
    private interface OpusEncoderRef {
        byte[] encode(short[] samples);
    }

    private interface OpusDecoderRef {
        short[] decode(byte[] packet);
    }

    private VoiceCodec(OpusEncoderRef encoder, OpusDecoderRef decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.available = encoder != null && decoder != null;
    }

    /**
     * Creates a codec, or one whose {@link #isAvailable()} is false when the Opus dependency is
     * not on the classpath. Never throws.
     */
    public static VoiceCodec create() {
        try {
            Class<?> encoderClass = Class.forName("org.concentus.OpusEncoder");
            Class<?> decoderClass = Class.forName("org.concentus.OpusDecoder");
            Class<?> applicationClass = Class.forName("org.concentus.OpusApplication");

            Object voip = null;
            for (Object constant : applicationClass.getEnumConstants()) {
                if (constant.toString().equals("OPUS_APPLICATION_VOIP")) {
                    voip = constant;
                    break;
                }
            }
            if (voip == null) return new VoiceCodec(null, null);

            Object encoder = encoderClass
                    .getConstructor(int.class, int.class, applicationClass)
                    .newInstance(SAMPLE_RATE, CHANNELS, voip);
            Object decoder = decoderClass
                    .getConstructor(int.class, int.class)
                    .newInstance(SAMPLE_RATE, CHANNELS);

            // Ask for the target bitrate; a failure here is not fatal, the default is still Opus.
            try {
                encoderClass.getMethod("setBitrate", int.class).invoke(encoder, BITRATE);
            } catch (Throwable ignored) {
            }

            java.lang.reflect.Method encodeMethod = encoderClass.getMethod(
                    "encode", short[].class, int.class, int.class, byte[].class, int.class, int.class);
            java.lang.reflect.Method decodeMethod = decoderClass.getMethod(
                    "decode", byte[].class, int.class, int.class, short[].class, int.class, int.class,
                    boolean.class);

            final Object encoderRef = encoder;
            final Object decoderRef = decoder;
            OpusEncoderRef encoderAdapter = samples -> {
                try {
                    byte[] out = new byte[MAX_PACKET_BYTES];
                    int written = (Integer) encodeMethod.invoke(encoderRef, samples, 0,
                            samples.length, out, 0, out.length);
                    if (written <= 0) return null;
                    byte[] trimmed = new byte[written];
                    System.arraycopy(out, 0, trimmed, 0, written);
                    return trimmed;
                } catch (Throwable t) {
                    return null;
                }
            };
            OpusDecoderRef decoderAdapter = packet -> {
                try {
                    short[] out = new short[FRAME_SAMPLES];
                    int samples = (Integer) decodeMethod.invoke(decoderRef, packet, 0,
                            packet.length, out, 0, out.length, false);
                    if (samples <= 0) return null;
                    return out;
                } catch (Throwable t) {
                    return null;
                }
            };
            return new VoiceCodec(encoderAdapter, decoderAdapter);
        } catch (Throwable t) {
            return new VoiceCodec(null, null);
        }
    }

    /** Whether Opus encoding is usable; when false the caller must use PCM. */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Encodes one PCM frame to Opus, or returns null when encoding is unavailable or fails. A null
     * result means "do not send this frame", never "send it as PCM" — silently changing codec
     * mid-stream would be worse than dropping one frame.
     */
    public byte[] encode(byte[] pcm, int length) {
        if (!available || pcm == null || length < 2) return null;
        short[] samples = pcmToShorts(pcm, length);
        return encoder.encode(samples);
    }

    /**
     * Decodes one Opus frame back to PCM, or returns null when decoding fails. A lost or corrupt
     * frame is dropped rather than substituted, so a glitch is one missing frame and not noise.
     */
    public byte[] decode(byte[] opus, int length) {
        if (!available || opus == null || length <= 0) return null;
        byte[] packet = opus;
        if (length != opus.length) {
            packet = new byte[length];
            System.arraycopy(opus, 0, packet, 0, length);
        }
        short[] samples = decoder.decode(packet);
        return samples == null ? null : shortsToPcm(samples);
    }

    /** Little-endian 16-bit PCM to signed samples. Pure, so it is unit-testable. */
    static short[] pcmToShorts(byte[] pcm, int length) {
        int count = length / 2;
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
        }
        return out;
    }

    /** Signed samples back to little-endian 16-bit PCM. Pure, so it is unit-testable. */
    static byte[] shortsToPcm(short[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            out[i * 2] = (byte) (samples[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return out;
    }
}
