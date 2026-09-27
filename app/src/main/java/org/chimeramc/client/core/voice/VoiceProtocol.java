package org.chimeramc.client.core.voice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The wire format for the proximity voice link, as pure encode/decode.
 *
 * <p>Being separate from the socket is what makes the framing testable: a round-trip of every
 * packet type, a truncated datagram and a datagram from a different protocol all have to be
 * handled before any of this can be trusted on a network. The transport sends and receives
 * bytes; this class only decides what the bytes mean.
 *
 * <p>A datagram begins with the two-byte magic {@code CV} and a one-byte format version, so a
 * stray packet from another app on the same multicast group is discarded rather than parsed as
 * coordinates.
 *
 * <p><b>Version 2</b> adds a channel visibility byte and a human-readable channel name. The
 * channel <em>id</em> is still the free text that decides who shares a room, so none of the
 * channel-matching rules change; the new fields only let the client show a directory (name and
 * member count) and keep private rooms off it. Version 1 packets are still decoded -- an updated
 * client must be able to hear an older one -- and default to the public, unnamed channel, which
 * is exactly what a v1 packet describes since v1 had no notion of a private room.
 */
public final class VoiceProtocol {

    public static final byte[] MAGIC = {'C', 'V'};

    /** The current format version. */
    public static final byte VERSION = 2;
    /** The previous format version, still accepted so an older peer is still heard. */
    public static final byte VERSION_LEGACY = 1;

    /** A channel anyone may see and join; listed in the directory. */
    public static final byte VISIBILITY_PUBLIC = 0;
    /** A channel that is only reachable by typing its id/code; never listed. */
    public static final byte VISIBILITY_PRIVATE = 1;

    /** A periodic position/channel advertisement; also serves as a liveness beacon. */
    public static final byte TYPE_BEACON = 1;
    /** An uncompressed audio frame from a talker. */
    public static final byte TYPE_AUDIO = 2;
    /** A clean shutdown notice, so a peer leaves the list without waiting for the stale timer. */
    public static final byte TYPE_BYE = 3;

    /** The largest payload we will accept, so a hostile datagram cannot allocate unbounded memory. */
    public static final int MAX_PAYLOAD = 4096;

    private VoiceProtocol() {
    }

    /** A decoded packet; exactly one of the state/audio shapes is populated per type. */
    public static final class Packet {
        public final byte type;
        public final String peerId;
        public final String name;
        public final String channel;
        /** {@link #VISIBILITY_PUBLIC} or {@link #VISIBILITY_PRIVATE}. */
        public final byte visibility;
        /** Human-readable channel name, or "" when the sender named no channel. */
        public final String channelName;
        public final float x, y, z;
        public final int sequence;
        public final byte[] payload;

        private Packet(byte type, String peerId, String name, String channel,
                       byte visibility, String channelName,
                       float x, float y, float z, int sequence, byte[] payload) {
            this.type = type;
            this.peerId = peerId;
            this.name = name;
            this.channel = channel;
            this.visibility = visibility;
            this.channelName = channelName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sequence = sequence;
            this.payload = payload;
        }

        public boolean isPrivate() {
            return visibility == VISIBILITY_PRIVATE;
        }
    }

    /** Encodes a beacon on the open, unnamed channel (the v2 default). */
    public static byte[] encodeBeacon(String peerId, String name, String channel,
                                      float x, float y, float z, int sequence) {
        return encodeBeacon(peerId, name, channel, VISIBILITY_PUBLIC, "", x, y, z, sequence);
    }

    public static byte[] encodeBeacon(String peerId, String name, String channel,
                                      byte visibility, String channelName,
                                      float x, float y, float z, int sequence) {
        return encode(TYPE_BEACON, peerId, name, channel, visibility, channelName,
                x, y, z, sequence, null);
    }

    public static byte[] encodeAudio(String peerId, String name, String channel,
                                     float x, float y, float z, int sequence, byte[] audio) {
        return encodeAudio(peerId, name, channel, VISIBILITY_PUBLIC, "", x, y, z, sequence, audio);
    }

    public static byte[] encodeAudio(String peerId, String name, String channel,
                                     byte visibility, String channelName,
                                     float x, float y, float z, int sequence, byte[] audio) {
        return encode(TYPE_AUDIO, peerId, name, channel, visibility, channelName,
                x, y, z, sequence, audio);
    }

    public static byte[] encodeBye(String peerId, String name, String channel,
                                   float x, float y, float z) {
        return encodeBye(peerId, name, channel, VISIBILITY_PUBLIC, "", x, y, z);
    }

    public static byte[] encodeBye(String peerId, String name, String channel,
                                   byte visibility, String channelName,
                                   float x, float y, float z) {
        return encode(TYPE_BYE, peerId, name, channel, visibility, channelName,
                x, y, z, 0, null);
    }

    private static byte[] encode(byte type, String peerId, String name, String channel,
                                 byte visibility, String channelName,
                                 float x, float y, float z, int sequence, byte[] audio) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(type);
            writeString(out, peerId);
            writeString(out, name);
            writeString(out, VoiceChannel.normalize(channel));
            out.writeByte(normalizeVisibility(visibility));
            writeString(out, channelName == null ? "" : channelName.trim());
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);
            out.writeInt(sequence);
            if (audio != null && audio.length > 0) {
                int length = Math.min(audio.length, MAX_PAYLOAD);
                out.writeInt(length);
                out.write(audio, 0, length);
            } else {
                out.writeInt(0);
            }
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            // A ByteArrayOutputStream cannot fail; treat it as an empty datagram.
            return new byte[0];
        }
    }

    /** Decodes a datagram, or returns null when it is not one of ours or is malformed. */
    public static Packet decode(byte[] data) {
        if (data == null || data.length < MAGIC.length + 2) return null;
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1]) return null;
        byte version = data[2];
        if (version != VERSION && version != VERSION_LEGACY) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            in.skipBytes(MAGIC.length);
            in.readByte(); // version, already checked
            byte type = in.readByte();
            if (type != TYPE_BEACON && type != TYPE_AUDIO && type != TYPE_BYE) return null;
            String peerId = readString(in);
            String name = readString(in);
            String channel = readString(in);
            byte visibility = VISIBILITY_PUBLIC;
            String channelName = "";
            if (version >= VERSION) {
                visibility = normalizeVisibility(in.readByte());
                channelName = readString(in);
            }
            float x = in.readFloat();
            float y = in.readFloat();
            float z = in.readFloat();
            int sequence = in.readInt();
            int length = in.readInt();
            if (length < 0 || length > MAX_PAYLOAD) return null;
            byte[] payload = new byte[length];
            in.readFully(payload);
            return new Packet(type, peerId, name, channel, visibility, channelName,
                    x, y, z, sequence, payload);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Clamps a decoded visibility to one of the two known values. */
    public static byte normalizeVisibility(byte visibility) {
        return visibility == VISIBILITY_PRIVATE ? VISIBILITY_PRIVATE : VISIBILITY_PUBLIC;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) bytes = Arrays.copyOf(bytes, 255);
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedByte();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
