package org.chimeramc.client.core.voice;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The local listener's per-member mute set.
 *
 * <p>Muting is <b>client-side only</b>. A muted peer is not told, cannot see that they were muted,
 * and is not disconnected from the channel; the listener simply stops mixing that peer's audio.
 * That is deliberate: a "mute" that notified the other party, or that relied on the other party
 * respecting a request, would be a different feature with different privacy properties. Because
 * nothing goes on the wire, this set is plain local state and cannot be enforced by anyone else.
 *
 * <p>Ids are peer ids (see {@link VoicePeer#id}), which are per-session, so a persisted set is
 * best-effort: a peer who reconnects under a new id is unmuted again. That is the honest
 * behaviour for an id that carries no stable identity.
 *
 * <p>Pure and Android-free so "muting one member leaves the others audible" is a unit test.
 */
public final class VoiceMutes {

    private final Set<String> muted = new LinkedHashSet<>();

    /** Adds a peer id to the local mute set; blank/null is ignored. */
    public synchronized void mute(String peerId) {
        if (peerId == null) return;
        String trimmed = peerId.trim();
        if (!trimmed.isEmpty()) muted.add(trimmed);
    }

    /** Removes a peer id from the local mute set. */
    public synchronized void unmute(String peerId) {
        if (peerId != null) muted.remove(peerId.trim());
    }

    /** Flips a peer id's mute state and returns the new state. */
    public synchronized boolean toggle(String peerId) {
        if (isMuted(peerId)) {
            unmute(peerId);
            return false;
        }
        mute(peerId);
        return true;
    }

    /** Whether the listener has muted this peer. */
    public synchronized boolean isMuted(String peerId) {
        return peerId != null && muted.contains(peerId.trim());
    }

    public synchronized boolean isEmpty() {
        return muted.isEmpty();
    }

    public synchronized int size() {
        return muted.size();
    }

    /** An immutable snapshot of the muted ids. */
    public synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(muted));
    }

    public synchronized void clear() {
        muted.clear();
    }

    /**
     * Replaces the set with a comma-separated list of ids, dropping blanks.
     *
     * <p>Used to restore the persisted set; parsing here keeps the storage format in one place so
     * the manager never hand-splits the string.
     */
    public synchronized void restoreFrom(List<String> ids) {
        muted.clear();
        if (ids == null) return;
        for (String id : ids) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) muted.add(trimmed);
        }
    }

    /** Serialises the set as a comma-separated list for persistence. */
    public synchronized String serialize() {
        StringBuilder builder = new StringBuilder();
        for (String id : muted) {
            if (builder.length() > 0) builder.append(',');
            builder.append(id);
        }
        return builder.toString();
    }
}
