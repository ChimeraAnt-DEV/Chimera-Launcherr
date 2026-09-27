package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The directory is built purely from peers already heard, so the two rules that matter -- private
 * channels never list, and a rename/visibility switch is shown as of now -- are pinned here.
 */
public class VoiceChannelDirectoryTest {

    private static VoicePeer peer(String id, String channel, String name, byte visibility, long seen) {
        return new VoicePeer(id, id, 0f, 0f, 0f, channel, name, visibility, seen);
    }

    @Test
    public void groupsPeersByChannelAndCountsThem() {
        List<VoicePeer> peers = Arrays.asList(
                peer("a", "team", "Team", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("b", "team", "Team", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("c", "world", "", VoiceProtocol.VISIBILITY_PUBLIC, 1));

        List<VoiceChannelDirectory.Channel> listed = VoiceChannelDirectory.build(peers, "world");
        assertEquals(2, listed.size());
        // Busiest first.
        assertEquals("team", listed.get(0).id);
        assertEquals(2, listed.get(0).memberCount);
        assertEquals("world", listed.get(1).id);
    }

    @Test
    public void privateChannelsAreNeverListed() {
        List<VoicePeer> peers = Arrays.asList(
                peer("a", "world", "", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("b", "chimera-7f2q", "Squad", VoiceProtocol.VISIBILITY_PRIVATE, 1));

        List<VoiceChannelDirectory.Channel> listed = VoiceChannelDirectory.build(peers, "world");
        assertEquals(1, listed.size());
        assertEquals("world", listed.get(0).id);
    }

    @Test
    public void theLatestAdvertisementWinsForNameAndVisibility() {
        // The channel was first seen private then switched public; the directory must show it
        // public rather than freezing the first sighting.
        List<VoicePeer> peers = Arrays.asList(
                peer("a", "team", "Old", VoiceProtocol.VISIBILITY_PRIVATE, 1),
                peer("b", "team", "Renamed", VoiceProtocol.VISIBILITY_PUBLIC, 5));

        List<VoiceChannelDirectory.Channel> listed = VoiceChannelDirectory.build(peers, "world");
        assertEquals(1, listed.size());
        assertEquals("Renamed", listed.get(0).name);
    }

    @Test
    public void theListenerChannelIsMarkedCurrent() {
        List<VoicePeer> peers = Arrays.asList(
                peer("a", "team", "Team", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("b", "world", "", VoiceProtocol.VISIBILITY_PUBLIC, 1));

        List<VoiceChannelDirectory.Channel> listed = VoiceChannelDirectory.build(peers, "team");
        for (VoiceChannelDirectory.Channel channel : listed) {
            if (channel.id.equals("team")) assertTrue(channel.current);
            else assertFalse(channel.current);
        }
    }

    @Test
    public void tiesBreakByNameSoTheOrderIsStable() {
        List<VoicePeer> peers = Arrays.asList(
                peer("a", "zulu", "Zulu", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("b", "alpha", "Alpha", VoiceProtocol.VISIBILITY_PUBLIC, 1));

        List<VoiceChannelDirectory.Channel> listed = VoiceChannelDirectory.build(peers, "world");
        assertEquals("alpha", listed.get(0).id);
        assertEquals("zulu", listed.get(1).id);
    }

    @Test
    public void membersOfReturnsOnlyTheNamedChannel() {
        List<VoicePeer> peers = Arrays.asList(
                peer("a", "team", "Team", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("b", "team", "Team", VoiceProtocol.VISIBILITY_PUBLIC, 1),
                peer("c", "world", "", VoiceProtocol.VISIBILITY_PUBLIC, 1));

        List<VoicePeer> members = VoiceChannelDirectory.membersOf(peers, "team");
        assertEquals(2, members.size());
    }

    @Test
    public void emptyAndNullInputsYieldEmptyLists() {
        assertTrue(VoiceChannelDirectory.build(null, "world").isEmpty());
        assertTrue(VoiceChannelDirectory.build(new ArrayList<>(), "world").isEmpty());
        assertTrue(VoiceChannelDirectory.membersOf(null, "world").isEmpty());
    }
}
