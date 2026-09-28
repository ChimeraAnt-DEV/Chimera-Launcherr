package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Pins the distance rule's three states, because the native position feed is fail-closed and the
 * middle state is easy to get wrong.
 *
 * <p>A feed is installed but the game has no world loaded — before the first frame, on a loading
 * screen, after leaving a session — reads as "no position". If that state were measured as a real
 * origin, every peer would be graded against (0,0,0) and audio would drop for no reason the player
 * could see. The rule under test is: a real position uses distance, a missing one falls back to
 * the channel rule, and a position on an unreachable channel is always silent.
 *
 * <p>No mocks: the decision is a pure function, and the packets are built by the real encoder, so
 * the same bytes the wire carries are what the rule sees.
 */
public class VoicePositionGainTest {

    private static final String WORLD = VoiceChannel.WORLD;
    private static final String TEAM = "team-abc123";
    private static final String OTHER_TEAM = "team-zzz999";
    private static final float RANGE = 48f;

    /** A decoded packet whose sender sits at the given position. */
    private static VoiceProtocol.Packet packetAt(float x, float y, float z, String channel) {
        byte[] data = VoiceProtocol.encodeBeacon("peer0001", "Peer", channel,
                VoiceProtocol.VISIBILITY_PUBLIC, "", VoiceProtocol.CAPACITY_NONE,
                0f, false, x, y, z, 7);
        VoiceProtocol.Packet p = VoiceProtocol.decode(data);
        if (p == null) throw new AssertionError("encoder produced an undecodable packet");
        return p;
    }

    @Test
    public void aPeerAtTheListenerPositionIsFullGain() {
        float gain = VoiceChatModule.gainFor(packetAt(10f, 64f, -5f, WORLD), WORLD,
                WORLD, RANGE, new float[]{10f, 64f, -5f});
        assertEquals(1f, gain, 1e-6);
    }

    @Test
    public void aPeerBeyondRangeIsSilent() {
        float gain = VoiceChatModule.gainFor(packetAt(200f, 64f, 0f, WORLD), WORLD,
                WORLD, RANGE, new float[]{0f, 64f, 0f});
        assertEquals(0f, gain, 1e-6);
    }

    /**
     * The state this whole class exists for: a feed with no live read must behave exactly like
     * channel mode, not like "a peer 200 blocks away from the origin".
     */
    @Test
    public void noLivePositionFallsBackToTheChannelRuleNotTheOrigin() {
        // A peer far from (0,0,0). If the missing position were treated as the origin, this would
        // be 0f; the channel rule says a same-channel peer is audible at full gain.
        float farPeer = VoiceChatModule.gainFor(packetAt(500f, 70f, 500f, WORLD), WORLD,
                WORLD, RANGE, null);
        assertEquals(1f, farPeer, 1e-6);
    }

    /**
     * A peer on a channel the listener cannot hear is silent in both states — the channel rule is
     * not a blanket "everyone is audible" when the position is missing. Two different private
     * channels do not hear each other; the open world channel is heard in both directions.
     */
    @Test
    public void anUnreachableChannelIsSilentEvenWithoutAPosition() {
        assertEquals(0f, VoiceChatModule.gainFor(packetAt(0f, 0f, 0f, TEAM), TEAM,
                OTHER_TEAM, RANGE, null), 1e-6);
        assertEquals(0f, VoiceChatModule.gainFor(packetAt(0f, 0f, 0f, TEAM), TEAM,
                OTHER_TEAM, RANGE, new float[]{0f, 0f, 0f}), 1e-6);
    }

    /** The open world channel is audible to everyone, in both directions. */
    @Test
    public void theWorldChannelIsHeardFromAPrivateChannelAndViceVersa() {
        assertEquals(1f, VoiceChatModule.gainFor(packetAt(0f, 0f, 0f, WORLD), WORLD,
                TEAM, RANGE, null), 1e-6);
        assertEquals(1f, VoiceChatModule.gainFor(packetAt(0f, 0f, 0f, TEAM), TEAM,
                WORLD, RANGE, null), 1e-6);
    }

    @Test
    public void aPrivateChannelIsHeardByItsOwnMembers() {
        float gain = VoiceChatModule.gainFor(packetAt(5f, 64f, 5f, TEAM), TEAM,
                TEAM, RANGE, new float[]{0f, 64f, 0f});
        assertEquals(1f, gain, 1e-6);
    }

    /** Gain falls off monotonically with distance, so a nearer peer is never quieter. */
    @Test
    public void gainFallsOffWithDistance() {
        float near = VoiceChatModule.gainFor(packetAt(5f, 64f, 0f, WORLD), WORLD,
                WORLD, RANGE, new float[]{0f, 64f, 0f});
        float mid = VoiceChatModule.gainFor(packetAt(30f, 64f, 0f, WORLD), WORLD,
                WORLD, RANGE, new float[]{0f, 64f, 0f});
        assertEquals(1f, near, 1e-6);
        assertEquals(true, mid < near);
        assertEquals(true, mid > 0f);
    }

    @Test
    public void aFeedValueThatIsMissingOrMalformedIsRejected() {
        assertNull(VoiceChatModule.sanitizePosition(null));
        assertNull(VoiceChatModule.sanitizePosition(new float[]{1f, 2f}));
        assertNull(VoiceChatModule.sanitizePosition(new float[]{Float.NaN, 0f, 0f}));
        assertNull(VoiceChatModule.sanitizePosition(new float[]{0f, Float.NaN, 0f}));
        assertNull(VoiceChatModule.sanitizePosition(new float[]{0f, 0f, Float.POSITIVE_INFINITY}));
        assertNull(VoiceChatModule.sanitizePosition(new float[]{Float.NEGATIVE_INFINITY, 0f, 0f}));
    }

    @Test
    public void aFiniteFeedValueIsAcceptedUnchanged() {
        float[] position = {1.5f, 64f, -2.5f};
        assertSame(position, VoiceChatModule.sanitizePosition(position));
    }
}
