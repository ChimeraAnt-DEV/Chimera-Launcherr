package org.chimeramc.client.core.voice;

/**
 * One other launcher user heard on the voice link, as last advertised.
 *
 * <p>Immutable so a discovery thread can publish a fresh snapshot without a lock and the audio
 * path can read one without ever seeing a half-updated peer.
 */
public final class VoicePeer {

    public final String id;
    public final String name;
    public final float x;
    public final float y;
    public final float z;
    /** The channel this peer is talking on; see {@link VoiceChannel}. */
    public final String channel;
    /** Uptime milliseconds when this peer last advertised. */
    public final long lastSeenMs;

    public VoicePeer(String id, String name, float x, float y, float z,
                     String channel, long lastSeenMs) {
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "Player" : name.trim();
        this.x = x;
        this.y = y;
        this.z = z;
        this.channel = VoiceChannel.normalize(channel);
        this.lastSeenMs = lastSeenMs;
    }

    /** Whether this peer is currently talking, inferred from a recent audio frame. */
    public boolean isFresh(long nowMs, long staleMs) {
        return nowMs - lastSeenMs <= staleMs;
    }

    /** Euclidean distance to a point, in blocks. */
    public float distanceTo(float px, float py, float pz) {
        float dx = x - px;
        float dy = y - py;
        float dz = z - pz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public VoicePeer seenAt(long uptimeMs) {
        return new VoicePeer(id, name, x, y, z, channel, uptimeMs);
    }

    public VoicePeer withState(float nx, float ny, float nz, String nChannel) {
        return new VoicePeer(id, name, nx, ny, nz, nChannel, lastSeenMs);
    }
}
