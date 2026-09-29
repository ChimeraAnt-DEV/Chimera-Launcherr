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
 *
 * <p><b>Version 3</b> adds the sender's advertised channel capacity, its live microphone level
 * and its self-mute state. The capacity lets the directory show a public room's limit and lets a
 * joiner refuse a full one; the level and mute state are what the in-world nametag icon reads so
 * the speaking animation reflects the audio actually being sent rather than a decorative pulse.
 * None of these change who can hear whom, so a v1 or v2 peer is still fully audible; its packets
 * simply carry no level/mute/capacity and the UI degrades to the neutral state.
 *
 * <p><b>Version 4</b> adds the server-assigned client id and the audio codec byte, and the packet
 * types a relay conversation needs (hello/ack/ping/pong/notice). It is the format the
 * {@code VoiceRelayTransport} speaks. The <em>multicast</em> transport deliberately keeps sending
 * v3 with raw PCM: every existing LAN peer understands v3 and PCM, and a v4 header on the local
 * group would be a needless break. The version on the wire is therefore a property of the
 * transport, not of this class -- {@link #encodeBeacon} stays v3 and the relay builds v4 itself.
 * On decode, v1-v4 are all accepted so one build can hear both a LAN peer and a relay peer.
 *
 * <p><b>Version 5</b> adds the sender's own view rotation (yaw and pitch, in degrees) right
 * after the sequence number. The Hitboxes module needs it: a peer box alone shows where a
 * player is, but a look-direction line needs which way they are facing, and the only source for
 * another player's facing is that player telling us. Two floats is the whole change. v1-v4 decode
 * unchanged with yaw/pitch read as 0 ("unknown"), which draws no look line rather than a line
 * pointing along a made-up axis.
 */
public final class VoiceProtocol {

    public static final byte[] MAGIC = {'C', 'V'};

    /** The current format version, as used by the relay transport. */
    public static final byte VERSION = 5;
    /** The version the multicast transport still sends, for LAN compatibility. */
    public static final byte VERSION_MULTICAST = 3;

    /**
     * The version the relay wire speaks.
     *
     * <p>Pinned separately from {@link #VERSION} on purpose. The relay is a separate Go program
     * whose parser is fixed by cross-language golden vectors, so bumping {@link #VERSION} for a
     * LAN-only extension must not silently change what the relay is sent. A v5 rotation field on
     * the relay wire would have to land in the Go parser in the same commit; until then the relay
     * stays on the v4 shape it was pinned against.
     */
    public static final byte VERSION_RELAY = 4;
    /** The oldest format version still accepted, so an older peer is still heard. */
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
    /** Relay only: the first packet a client sends, to be assigned an id. */
    public static final byte TYPE_HELLO = 4;
    /** Relay only: the server's reply, carrying the assigned client id. */
    public static final byte TYPE_HELLO_ACK = 5;
    /** Relay only: a server keepalive probe. */
    public static final byte TYPE_PING = 6;
    /** Relay only: a client's reply to a ping, and its own keepalive. */
    public static final byte TYPE_PONG = 7;
    /** Relay only: the server refused something; the reason is in {@link Packet#sequence}. */
    public static final byte TYPE_NOTICE = 8;

    /** Audio codec: raw 16-bit little-endian mono PCM, the LAN default. */
    public static final byte CODEC_PCM = 0;
    /** Audio codec: Opus, what the relay transport prefers for its bandwidth saving. */
    public static final byte CODEC_OPUS = 1;

    /** A relay notice reason: the server is at capacity. */
    public static final int NOTICE_SERVER_FULL = 1;
    /** A relay notice reason: the shared password was wrong or missing. */
    public static final int NOTICE_BAD_PASSWORD = 2;
    /** A relay notice reason: the channel is at capacity. */
    public static final int NOTICE_CHANNEL_FULL = 3;
    /** A relay notice reason: the server speaks a different protocol version. */
    public static final int NOTICE_BAD_PROTOCOL = 4;
    /** The join token was missing, malformed, expired, or bound to a different device. */
    public static final int NOTICE_BAD_TOKEN = 5;
    /** The address or device is banned from the relay. */
    public static final int NOTICE_BANNED = 6;

    /** The largest payload we will accept, so a hostile datagram cannot allocate unbounded memory. */
    public static final int MAX_PAYLOAD = 4096;

    /** A capacity of 0 means "no cap"; it is the v1/v2 default and the private-channel default. */
    public static final int CAPACITY_NONE = 0;
    /** The largest capacity a host may advertise; a value above this is clamped on decode. */
    public static final int MAX_CAPACITY = 100;

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
        /** Advertised max member count, or {@link #CAPACITY_NONE}; v1/v2 packets report none. */
        public final int capacity;
        /** The sender's live, smoothed mic level in {@code [0,1]}; 0 for v1/v2 packets. */
        public final float level;
        /** True when the sender has muted their own microphone. */
        public final boolean muted;
        public final float x, y, z;
        public final int sequence;
        /**
         * The sender's view yaw/pitch in degrees, or 0 when unknown (v1-v4 packets).
         *
         * <p>0 is deliberately the "unknown" sentinel: a v5 sender that genuinely looks straight
         * along yaw 0 still advertises pitch too, and a peer with no rotation information must draw
         * no direction line rather than one aimed along an arbitrary axis.
         */
        public final float yaw, pitch;
        /** {@link #CODEC_PCM} or {@link #CODEC_OPUS}; PCM for a v3-or-earlier packet. */
        public final byte codec;
        /** The server-assigned client id, or 0 outside the relay. */
        public final long clientId;
        /**
         * A stable, client-supplied device id, or "" outside the relay.
         *
         * <p>It is not trusted for identity -- a client could put anything here -- but it is what a
         * device-bound token and a device ban bind to, so a banned or token-bound device is refused
         * regardless of the IP it connects from.
         */
        public final String deviceId;
        public final byte[] payload;

        private Packet(byte type, String peerId, String name, String deviceId, String channel,
                       byte visibility, String channelName,
                       int capacity, float level, boolean muted,
                       float x, float y, float z, int sequence, float yaw, float pitch,
                       byte codec, long clientId,
                       byte[] payload) {
            this.type = type;
            this.peerId = peerId;
            this.name = name;
            this.deviceId = deviceId;
            this.channel = channel;
            this.visibility = visibility;
            this.channelName = channelName;
            this.capacity = capacity;
            this.level = level;
            this.muted = muted;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sequence = sequence;
            this.yaw = yaw;
            this.pitch = pitch;
            this.codec = codec;
            this.clientId = clientId;
            this.payload = payload;
        }

        public boolean isPrivate() {
            return visibility == VISIBILITY_PRIVATE;
        }

        /** Whether this packet's audio payload is Opus rather than raw PCM. */
        public boolean isOpus() {
            return codec == CODEC_OPUS;
        }

        /** A copy carrying a different payload, so a transport can reframe without re-decoding. */
        public Packet withPayload(byte[] newPayload) {
            return new Packet(type, peerId, name, deviceId, channel, visibility, channelName,
                    capacity, level, muted, x, y, z, sequence, yaw, pitch, codec, clientId,
                    newPayload);
        }
    }

    /** Encodes a beacon on the open, unnamed channel with no cap (the legacy default). */
    public static byte[] encodeBeacon(String peerId, String name, String channel,
                                      float x, float y, float z, int sequence) {
        return encodeBeacon(peerId, name, channel, VISIBILITY_PUBLIC, "", CAPACITY_NONE,
                0f, false, x, y, z, sequence);
    }

    public static byte[] encodeBeacon(String peerId, String name, String channel,
                                      byte visibility, String channelName,
                                      float x, float y, float z, int sequence) {
        return encodeBeacon(peerId, name, channel, visibility, channelName, CAPACITY_NONE,
                0f, false, x, y, z, sequence);
    }

    /** Full beacon: advertises capacity, the sender's live level and its self-mute state. */
    public static byte[] encodeBeacon(String peerId, String name, String channel,
                                      byte visibility, String channelName, int capacity,
                                      float level, boolean muted,
                                      float x, float y, float z, int sequence) {
        return encode(TYPE_BEACON, peerId, name, channel, visibility, channelName,
                capacity, level, muted, x, y, z, sequence, null);
    }

    public static byte[] encodeAudio(String peerId, String name, String channel,
                                     float x, float y, float z, int sequence, byte[] audio) {
        return encodeAudio(peerId, name, channel, VISIBILITY_PUBLIC, "", CAPACITY_NONE,
                0f, false, x, y, z, sequence, audio);
    }

    public static byte[] encodeAudio(String peerId, String name, String channel,
                                     byte visibility, String channelName,
                                     float x, float y, float z, int sequence, byte[] audio) {
        return encodeAudio(peerId, name, channel, visibility, channelName, CAPACITY_NONE,
                0f, false, x, y, z, sequence, audio);
    }

    /**
     * Full audio packet.
     *
     * <p>The sender's live level rides along so a listener can light the talker's nametag icon
     * from the audio it is actually receiving, rather than waiting on the 1 Hz beacon. Mute state
     * rides along too, so a peer that mutes is reflected on the very next frame.
     */
    public static byte[] encodeAudio(String peerId, String name, String channel,
                                     byte visibility, String channelName, int capacity,
                                     float level, boolean muted,
                                     float x, float y, float z, int sequence, byte[] audio) {
        return encode(TYPE_AUDIO, peerId, name, channel, visibility, channelName,
                capacity, level, muted, x, y, z, sequence, audio);
    }

    public static byte[] encodeBye(String peerId, String name, String channel,
                                   float x, float y, float z) {
        return encodeBye(peerId, name, channel, VISIBILITY_PUBLIC, "", x, y, z);
    }

    public static byte[] encodeBye(String peerId, String name, String channel,
                                   byte visibility, String channelName,
                                   float x, float y, float z) {
        return encode(TYPE_BYE, peerId, name, channel, visibility, channelName,
                CAPACITY_NONE, 0f, false, x, y, z, 0, null);
    }

    /**
     * A version-5 LAN beacon that also carries the sender's view rotation.
     *
     * <p>Sent only when a peer must be able to draw another player's look direction; the plain
     * {@link #encodeBeacon} still speaks v3 so any existing LAN peer keeps working. A receiver that
     * does not understand v5 would drop it, so this is opt-in at the call site rather than a
     * blanket change to the multicast version.
     */
    public static byte[] encodeBeaconV5(String peerId, String name, String channel,
                                        byte visibility, String channelName,
                                        float x, float y, float z, int sequence,
                                        float yaw, float pitch) {
        return encode(VERSION, 0L, TYPE_BEACON, peerId, name, "", channel, visibility, channelName,
                CAPACITY_NONE, 0f, false, x, y, z, sequence, yaw, pitch, CODEC_PCM, null);
    }

    private static byte[] encode(byte type, String peerId, String name, String channel,
                                 byte visibility, String channelName, int capacity,
                                 float level, boolean muted,
                                 float x, float y, float z, int sequence, byte[] audio) {
        // The legacy entry points are the multicast path, which keeps sending v3 so every
        // existing LAN peer still understands it.
        return encode(VERSION_MULTICAST, 0L, type, peerId, name, channel, visibility,
                channelName, capacity, level, muted, x, y, z, sequence, CODEC_PCM, audio);
    }

    /**
     * The full encoder, parameterised by version-4 fields.
     *
     * <p>{@code version} selects the wire shape: v3 omits the client id and codec byte, v4 writes
     * both. The multicast path calls this with v3 (so LAN peers are unaffected) and the relay
     * path with v4. Keeping one encoder means the two shapes cannot drift.
     */
    public static byte[] encode(byte version, long clientId, byte type, String peerId, String name,
                                String channel, byte visibility, String channelName, int capacity,
                                float level, boolean muted,
                                float x, float y, float z, int sequence, byte codec, byte[] audio) {
        return encode(version, clientId, type, peerId, name, "", channel, visibility, channelName,
                capacity, level, muted, x, y, z, sequence, codec, audio);
    }

    /**
     * The full encoder, parameterised by version-4 fields, including the device id.
     *
     * <p>{@code version} selects the wire shape: v3 omits the client id, device id and codec byte;
     * v4 writes all three. The multicast path calls this with v3 (so LAN peers are unaffected) and
     * the relay path with v4. Keeping one encoder means the two shapes cannot drift.
     */
    public static byte[] encode(byte version, long clientId, byte type, String peerId, String name,
                                String deviceId, String channel, byte visibility, String channelName,
                                int capacity, float level, boolean muted,
                                float x, float y, float z, int sequence, byte codec, byte[] audio) {
        return encode(version, clientId, type, peerId, name, deviceId, channel, visibility,
                channelName, capacity, level, muted, x, y, z, sequence, 0f, 0f, codec, audio);
    }

    /**
     * The full encoder, including the v5 view rotation.
     *
     * <p>{@code version} selects the wire shape: v3 omits the client id, device id and codec byte;
     * v4 writes all three; v5 appends yaw/pitch after the sequence number. A caller that has no
     * rotation information passes 0/0 and the receiver reads "unknown".
     */
    public static byte[] encode(byte version, long clientId, byte type, String peerId, String name,
                                String deviceId, String channel, byte visibility, String channelName,
                                int capacity, float level, boolean muted,
                                float x, float y, float z, int sequence, float yaw, float pitch,
                                byte codec, byte[] audio) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(MAGIC);
            out.writeByte(version);
            out.writeByte(type);
            if (version >= 4) {
                out.writeLong(clientId);
            }
            writeString(out, peerId);
            writeString(out, name);
            if (version >= 4) {
                writeString(out, deviceId == null ? "" : deviceId);
            }
            writeString(out, VoiceChannel.normalize(channel));
            out.writeByte(normalizeVisibility(visibility));
            writeString(out, channelName == null ? "" : channelName.trim());
            out.writeInt(normalizeCapacity(capacity));
            out.writeFloat(clampLevel(level));
            out.writeByte(muted ? 1 : 0);
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);
            out.writeInt(sequence);
            if (version >= 5) {
                out.writeFloat(yaw);
                out.writeFloat(pitch);
            }
            if (version >= 4) {
                out.writeByte(normalizeCodec(codec));
            }
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

    /**
     * Encodes a v3 packet for the multicast transport. Exposed so the session can frame any type
     * on the LAN path without a codec byte, which is exactly what an existing v3 peer expects.
     */
    public static byte[] encodeLegacy(byte type, String peerId, String name, String channel,
                                      byte visibility, String channelName, int capacity,
                                      float level, boolean muted,
                                      float x, float y, float z, int sequence, byte[] payload) {
        return encode(type, peerId, name, channel, visibility, channelName, capacity,
                level, muted, x, y, z, sequence, payload);
    }

    /** Encodes a v4 beacon for the relay, carrying the assigned client id and the codec. */
    public static byte[] encodeRelayBeacon(long clientId, byte type, String peerId, String name,
                                           String channel, byte visibility, String channelName,
                                           int capacity, float level, boolean muted,
                                           float x, float y, float z, int sequence, byte codec,
                                           byte[] audio) {
        return encode(VERSION_RELAY, clientId, type, peerId, name, "", channel, visibility,
                channelName, capacity, level, muted, x, y, z, sequence, codec, audio);
    }

    /** As above, but stamps the sender's device id so the relay can bind it to a token/ban. */
    public static byte[] encodeRelayBeacon(long clientId, byte type, String peerId, String name,
                                           String deviceId, String channel, byte visibility,
                                           String channelName, int capacity, float level,
                                           boolean muted,
                                           float x, float y, float z, int sequence, byte codec,
                                           byte[] audio) {
        return encode(VERSION_RELAY, clientId, type, peerId, name, deviceId, channel, visibility,
                channelName, capacity, level, muted, x, y, z, sequence, codec, audio);
    }

    /**
     * A v4 HELLO: the first packet a client sends to a relay, to be assigned an id.
     *
     * <p>The payload is the credential: either a signed join token ({@code v1.…}) or the shared
     * password. The device id rides in its own field so the relay can bind both to a device even
     * when only a password is in use.
     */
    public static byte[] encodeHello(String name, String deviceId, String channel, byte[] credential) {
        return encode(VERSION_RELAY, 0L, TYPE_HELLO, "", name, deviceId, channel, VISIBILITY_PUBLIC,
                "",
                CAPACITY_NONE, 0f, false, 0f, 0f, 0f, 0, CODEC_OPUS, credential);
    }

    /** Backwards-compatible HELLO with no device id; the relay treats it as unbound. */
    public static byte[] encodeHello(String name, String channel, byte[] credential) {
        return encodeHello(name, "", channel, credential);
    }

    /** A v4 PONG, sent as a keepalive and in reply to a server PING. */
    public static byte[] encodePong(long clientId) {
        return encode(VERSION_RELAY, clientId, TYPE_PONG, "", "", "", VoiceChannel.WORLD,
                VISIBILITY_PUBLIC, "", CAPACITY_NONE, 0f, false, 0f, 0f, 0f, 0, CODEC_PCM, null);
    }

    /** Decodes a datagram, or returns null when it is not one of ours or is malformed. */
    public static Packet decode(byte[] data) {
        if (data == null || data.length < MAGIC.length + 2) return null;
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1]) return null;
        byte version = data[2];
        if (version < VERSION_LEGACY || version > VERSION) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            in.skipBytes(MAGIC.length);
            in.readByte(); // version, already checked
            byte type = in.readByte();
            if (!isKnownType(type)) return null;
            long clientId = 0L;
            if (version >= 4) {
                clientId = in.readLong();
            }
            String peerId = readString(in);
            String name = readString(in);
            // The device id is a v4 field, written right after the name, so read it here rather
            // than with the v3 tail -- field order, not grouping, is what the wire cares about.
            String deviceId = "";
            if (version >= 4) {
                deviceId = readString(in);
            }
            String channel = readString(in);
            byte visibility = VISIBILITY_PUBLIC;
            String channelName = "";
            int capacity = CAPACITY_NONE;
            float level = 0f;
            boolean muted = false;
            if (version >= 2) {
                visibility = normalizeVisibility(in.readByte());
                channelName = readString(in);
            }
            if (version >= 3) {
                capacity = normalizeCapacity(in.readInt());
                level = clampLevel(in.readFloat());
                muted = in.readByte() != 0;
            }
            float x = in.readFloat();
            float y = in.readFloat();
            float z = in.readFloat();
            int sequence = in.readInt();
            // v5 rotation sits between the sequence and the codec byte; an older packet simply
            // has no rotation, which reads as the "unknown" sentinel.
            float yaw = 0f;
            float pitch = 0f;
            if (version >= 5) {
                yaw = in.readFloat();
                pitch = in.readFloat();
            }
            byte codec = CODEC_PCM;
            if (version >= 4) {
                codec = normalizeCodec(in.readByte());
            }
            int length = in.readInt();
            if (length < 0 || length > MAX_PAYLOAD) return null;
            byte[] payload = new byte[length];
            in.readFully(payload);
            return new Packet(type, peerId, name, deviceId, channel, visibility, channelName,
                    capacity, level, muted, x, y, z, sequence, yaw, pitch, codec, clientId, payload);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Whether a packet type is one this class understands; an unknown type is rejected. */
    private static boolean isKnownType(byte type) {
        switch (type) {
            case TYPE_BEACON:
            case TYPE_AUDIO:
            case TYPE_BYE:
            case TYPE_HELLO:
            case TYPE_HELLO_ACK:
            case TYPE_PING:
            case TYPE_PONG:
            case TYPE_NOTICE:
                return true;
            default:
                return false;
        }
    }

    /** Clamps a decoded codec to one of the two known values. */
    public static byte normalizeCodec(byte codec) {
        return codec == CODEC_OPUS ? CODEC_OPUS : CODEC_PCM;
    }

    /** Clamps a decoded visibility to one of the two known values. */
    public static byte normalizeVisibility(byte visibility) {
        return visibility == VISIBILITY_PRIVATE ? VISIBILITY_PRIVATE : VISIBILITY_PUBLIC;
    }

    /** Clamps an advertised capacity to {@code [CAPACITY_NONE, MAX_CAPACITY]}. */
    public static int normalizeCapacity(int capacity) {
        if (capacity <= 0) return CAPACITY_NONE;
        return Math.min(capacity, MAX_CAPACITY);
    }

    /** Clamps a decoded/encoded level into {@code [0,1]}; NaN reads as zero. */
    public static float clampLevel(float level) {
        if (Float.isNaN(level) || level <= 0f) return 0f;
        return Math.min(level, 1f);
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
