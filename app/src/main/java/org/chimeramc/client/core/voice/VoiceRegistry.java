package org.chimeramc.client.core.voice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks who is nearby and what the listener should hear right now.
 *
 * <p>A single mutable registry per session; {@link #snapshot} returns an immutable list so the
 * audio thread can work from a stable view while the discovery thread keeps updating. The
 * registry holds no Android types, so the "who is audible at this distance on this channel"
 * question is unit-testable without a device.
 *
 * <p>Peers expire: a peer that stopped advertising is dropped by {@link #evictStale}, so a
 * player who left the world does not stay in the mix at the last position they were seen.
 */
public final class VoiceRegistry {

    /** A peer is forgotten once it has not advertised for this long. */
    public static final long STALE_MS = 4000L;

    private final Map<String, VoicePeer> peers = new LinkedHashMap<>();

    /** One audible entry: the peer and the gain the listener should apply. */
    public static final class Audible {
        public final VoicePeer peer;
        public final float gain;

        Audible(VoicePeer peer, float gain) {
            this.peer = peer;
            this.gain = gain;
        }
    }

    /** Inserts or refreshes a peer. */
    public synchronized void put(VoicePeer peer) {
        if (peer == null || peer.id == null || peer.id.isEmpty()) return;
        peers.put(peer.id, peer);
    }

    public synchronized void remove(String id) {
        if (id != null) peers.remove(id);
    }

    public synchronized void clear() {
        peers.clear();
    }

    /** Drops peers last seen longer than {@link #STALE_MS} ago. */
    public synchronized void evictStale(long nowMs) {
        List<String> expired = new ArrayList<>();
        for (VoicePeer peer : peers.values()) {
            if (!peer.isFresh(nowMs, STALE_MS)) expired.add(peer.id);
        }
        for (String id : expired) peers.remove(id);
    }

    public synchronized int size() {
        return peers.size();
    }

    public synchronized Collection<VoicePeer> peers() {
        return Collections.unmodifiableList(new ArrayList<>(peers.values()));
    }

    /**
     * The peers audible from {@code (px,py,pz)} on {@code listenerChannel}, nearest-mixed first.
     *
     * <p>Gain is computed per peer through {@link VoiceChannel#gain}, so a channel that does not
     * reach contributes nothing and an out-of-range peer is omitted entirely rather than mixed
     * at zero.
     */
    public synchronized List<Audible> audible(float px, float py, float pz,
                                              String listenerChannel, float rangeBlocks) {
        List<Audible> result = new ArrayList<>();
        for (VoicePeer peer : peers.values()) {
            float gain = VoiceChannel.gain(peer.distanceTo(px, py, pz), rangeBlocks,
                    listenerChannel, peer.channel);
            if (gain > 0f) result.add(new Audible(peer, gain));
        }
        result.sort((a, b) -> Float.compare(b.gain, a.gain));
        return result;
    }

    /** Immutable copy for a caller that will read without holding the lock. */
    public synchronized List<VoicePeer> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(peers.values()));
    }
}
