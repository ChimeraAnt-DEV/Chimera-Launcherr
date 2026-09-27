package org.chimeramc.client.core.voice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A live directory of the channels peers are currently on, built entirely client-side from the
 * beacons already arriving.
 *
 * <p>There is no server and no registry to query: every peer advertises its channel in each
 * beacon, so grouping the current {@link VoiceRegistry} snapshot by channel id yields the
 * directory for free. The most recent name and visibility seen for a channel win, so a channel
 * that is renamed or switched public/private is shown as it is now, not as it first appeared.
 *
 * <p><b>Private channels are not listed.</b> They are join-by-code only; a channel that appears
 * in a browsable list is public by definition. A private beacon still updates the directory's
 * record of that channel (so a later public switch is shown immediately) but never produces a
 * row, and an entirely private directory is empty rather than hiding a specific room.
 *
 * <p>Pure and Android-free, so "one world channel, a team channel with two members, and a hidden
 * private room" is a unit test rather than a device test.
 */
public final class VoiceChannelDirectory {

    /** One listed channel: its identity, display name, and how many peers are on it. */
    public static final class Channel {
        public final String id;
        public final String name;
        public final int memberCount;
        /** Advertised max member count, or {@link VoiceProtocol#CAPACITY_NONE} for no cap. */
        public final int capacity;
        /** True when the local listener is currently on this channel. */
        public final boolean current;

        Channel(String id, String name, int memberCount, int capacity, boolean current) {
            this.id = id;
            this.name = name;
            this.memberCount = memberCount;
            this.capacity = capacity;
            this.current = current;
        }

        /** Whether the channel has reached its advertised cap. */
        public boolean isFull() {
            return VoiceChannelCapacity.isFull(memberCount, capacity);
        }
    }

    private VoiceChannelDirectory() {
    }

    /**
     * Groups the peers by channel and returns the public ones, busiest first then by name.
     *
     * @param peers           the current registry snapshot
     * @param listenerChannel the local channel, marked so the UI can highlight it
     */
    public static List<Channel> build(Collection<VoicePeer> peers, String listenerChannel) {
        String listener = VoiceChannel.normalize(listenerChannel);
        Map<String, Record> byChannel = new LinkedHashMap<>();
        if (peers != null) {
            for (VoicePeer peer : peers) {
                if (peer == null || peer.channel == null) continue;
                Record record = byChannel.get(peer.channel);
                if (record == null) {
                    record = new Record();
                    byChannel.put(peer.channel, record);
                }
                record.count++;
                // Latest advertisement wins, so a rename or a visibility switch is reflected
                // immediately rather than frozen at first sight.
                if (peer.lastSeenMs >= record.lastSeenMs) {
                    record.name = peer.channelName;
                    record.visibility = peer.visibility;
                    record.capacity = peer.capacity;
                    record.lastSeenMs = peer.lastSeenMs;
                }
            }
        }

        List<Channel> listed = new ArrayList<>();
        for (Map.Entry<String, Record> entry : byChannel.entrySet()) {
            Record record = entry.getValue();
            if (record.visibility == VoiceProtocol.VISIBILITY_PRIVATE) continue;
            listed.add(new Channel(entry.getKey(), record.name, record.count,
                    record.capacity, entry.getKey().equals(listener)));
        }
        listed.sort((a, b) -> {
            if (a.memberCount != b.memberCount) return Integer.compare(b.memberCount, a.memberCount);
            return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
        });
        return listed;
    }

    /** Members of one channel, for the "current channel" panel. */
    public static List<VoicePeer> membersOf(Collection<VoicePeer> peers, String channel) {
        String target = VoiceChannel.normalize(channel);
        List<VoicePeer> members = new ArrayList<>();
        if (peers == null) return members;
        for (VoicePeer peer : peers) {
            if (peer != null && target.equals(peer.channel)) members.add(peer);
        }
        members.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return members;
    }

    private static final class Record {
        int count;
        String name = "";
        byte visibility = VoiceProtocol.VISIBILITY_PUBLIC;
        int capacity = VoiceProtocol.CAPACITY_NONE;
        long lastSeenMs = Long.MIN_VALUE;
    }
}
