package org.chimeramc.client.core.voice;

/**
 * The byte pipe a voice session runs over.
 *
 * <p>There are two: {@link VoiceTransport}, a LAN multicast group, and
 * {@link VoiceRelayTransport}, a server that forwards between clients in the same channel. The
 * module talks to this interface and never to either one directly, which is what lets the same
 * session logic run on both.
 *
 * <p>The link carries opaque datagrams; {@link VoiceProtocol} owns their meaning and
 * {@link VoiceCodec} owns the audio inside them. {@link #prefersOpus()} is the one piece of
 * transport-specific policy the caller needs: the relay path encodes to save bandwidth, the LAN
 * path stays on raw PCM so existing local peers keep working.
 */
public interface VoiceLink {

    /** What a received datagram triggers. Runs on the receive thread. */
    interface Listener {
        void onDatagram(byte[] data, int length);

        /** Called when the link stops on its own (socket error, server gone). */
        void onStopped(String reason);
    }

    /** Opens the link; returns false when it cannot start at all. */
    boolean start();

    void stop();

    boolean isRunning();

    /** Sends one datagram; a failure is reported as false, never thrown. */
    boolean send(byte[] data);

    /**
     * Whether the session should encode audio as Opus on this link.
     *
     * <p>True for the relay (bandwidth over the internet), false for multicast (no codec on the
     * LAN, and an existing peer only understands PCM).
     */
    default boolean prefersOpus() {
        return false;
    }
}
