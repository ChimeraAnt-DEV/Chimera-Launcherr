package org.chimeramc.client.core.voice;

/**
 * One other launcher user heard on the voice link, as last advertised.
 *
 * <p>Immutable so a discovery thread can publish a fresh snapshot without a lock and the audio
 * path can read one without ever seeing a half-updated peer.
 *
 * <p>A peer also advertises the channel it is on as a pair: the {@code channel} id decides who
 * shares a room (and never changes meaning), while {@code channelName}/{@code visibility} are
 * display metadata the directory groups by. A peer on the open channel carries no name and is
 * always public; a peer that named a private room is filtered out of the directory and reachable
 * only by its code.
 */
public final class VoicePeer {

    public final String id;
    public final String name;
    public final float x;
    public final float y;
    public final float z;
    /** The channel this peer is talking on; see {@link VoiceChannel}. */
    public final String channel;
    /** Human-readable channel name as advertised, or the channel id when unnamed. */
    public final String channelName;
    /** {@link VoiceProtocol#VISIBILITY_PUBLIC} or {@link VoiceProtocol#VISIBILITY_PRIVATE}. */
    public final byte visibility;
    /** Advertised max member count for the peer's channel, or {@link VoiceProtocol#CAPACITY_NONE}. */
    public final int capacity;
    /** The peer's live, smoothed mic level in {@code [0,1]}, as last advertised. */
    public final float level;
    /** True when the peer has muted their own microphone (their own choice, not the listener's). */
    public final boolean selfMuted;
    /** Uptime milliseconds when this peer last advertised. */
    public final long lastSeenMs;

    public VoicePeer(String id, String name, float x, float y, float z,
                     String channel, long lastSeenMs) {
        this(id, name, x, y, z, channel, "", VoiceProtocol.VISIBILITY_PUBLIC,
                VoiceProtocol.CAPACITY_NONE, 0f, false, lastSeenMs);
    }

    public VoicePeer(String id, String name, float x, float y, float z,
                     String channel, String channelName, byte visibility, long lastSeenMs) {
        this(id, name, x, y, z, channel, channelName, visibility,
                VoiceProtocol.CAPACITY_NONE, 0f, false, lastSeenMs);
    }

    public VoicePeer(String id, String name, float x, float y, float z,
                     String channel, String channelName, byte visibility,
                     int capacity, float level, boolean selfMuted, long lastSeenMs) {
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "Player" : name.trim();
        this.x = x;
        this.y = y;
        this.z = z;
        this.channel = VoiceChannel.normalize(channel);
        String trimmed = channelName == null ? "" : channelName.trim();
        // An unnamed channel falls back to its id, so a directory row always has something to
        // show and two ways of naming the same room cannot render as two different labels.
        this.channelName = trimmed.isEmpty() ? this.channel : trimmed;
        this.visibility = VoiceProtocol.normalizeVisibility(visibility);
        this.capacity = VoiceProtocol.normalizeCapacity(capacity);
        this.level = VoiceProtocol.clampLevel(level);
        this.selfMuted = selfMuted;
        this.lastSeenMs = lastSeenMs;
    }

    /** Whether this peer is still fresh enough to count as present. */
    public boolean isFresh(long nowMs, long staleMs) {
        return nowMs - lastSeenMs <= staleMs;
    }

    /** Whether this peer is on the open channel (no named room). */
    public boolean isOnOpenChannel() {
        return VoiceChannel.WORLD.equals(channel);
    }

    public boolean isPrivateChannel() {
        return visibility == VoiceProtocol.VISIBILITY_PRIVATE;
    }

    /** Euclidean distance to a point, in blocks. */
    public float distanceTo(float px, float py, float pz) {
        float dx = x - px;
        float dy = y - py;
        float dz = z - pz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public VoicePeer seenAt(long uptimeMs) {
        return new VoicePeer(id, name, x, y, z, channel, channelName, visibility,
                capacity, level, selfMuted, uptimeMs);
    }

    public VoicePeer withState(float nx, float ny, float nz, String nChannel) {
        return new VoicePeer(id, name, nx, ny, nz, nChannel, channelName, visibility,
                capacity, level, selfMuted, lastSeenMs);
    }

    public VoicePeer withChannel(String nChannel, String nChannelName, byte nVisibility) {
        return new VoicePeer(id, name, x, y, z, nChannel, nChannelName, nVisibility,
                capacity, level, selfMuted, lastSeenMs);
    }

    /** Returns a copy carrying a fresh level/mute pair, as advertised on the wire. */
    public VoicePeer withLevel(float nLevel, boolean nMuted) {
        return new VoicePeer(id, name, x, y, z, channel, channelName, visibility,
                capacity, nLevel, nMuted, lastSeenMs);
    }
}
