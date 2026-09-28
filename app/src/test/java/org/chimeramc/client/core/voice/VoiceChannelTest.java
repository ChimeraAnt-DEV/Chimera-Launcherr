package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The channel rules decide who can hear whom and at what gain. They are the whole proximity and
 * multi-channel contract, so they are pinned here without a socket or a device.
 */
public class VoiceChannelTest {

    @Test
    public void sameChannelAlwaysHears() {
        assertTrue(VoiceChannel.canHear("team", "team"));
        assertTrue(VoiceChannel.canHear("world", "world"));
    }

    @Test
    public void worldIsHeardAcrossChannelsInBothDirections() {
        // A talker on the open channel reaches a listener on a private one...
        assertTrue(VoiceChannel.canHear("team", VoiceChannel.WORLD));
        // ...and a listener on the open channel hears a private talker, so switching to a team
        // channel never cuts a player standing in front of you off.
        assertTrue(VoiceChannel.canHear(VoiceChannel.WORLD, "team"));
    }

    @Test
    public void twoDifferentPrivateChannelsDoNotHearEachOther() {
        assertFalse(VoiceChannel.canHear("red", "blue"));
    }

    @Test
    public void blankChannelsNormaliseToWorld() {
        assertEquals(VoiceChannel.WORLD, VoiceChannel.normalize(null));
        assertEquals(VoiceChannel.WORLD, VoiceChannel.normalize("  "));
        assertEquals("team", VoiceChannel.normalize("  TEAM "));
    }

    @Test
    public void gainIsFullAtTheCentreAndZeroAtTheEdge() {
        assertEquals(1f, VoiceChannel.gain(0f, 12f, "world", "world"), 0.0001f);
        assertEquals(0f, VoiceChannel.gain(12f, 12f, "world", "world"), 0.0001f);
        assertEquals(0f, VoiceChannel.gain(30f, 12f, "world", "world"), 0.0001f);
    }

    @Test
    public void gainFallsOffLinearlyBetweenTheTwoRadii() {
        // Inner radius is 12 * 0.35 = 4.2; at the midpoint of the falloff band, gain is 0.5.
        float mid = (4.2f + 12f) / 2f;
        assertEquals(0.5f, VoiceChannel.gain(mid, 12f, "world", "world"), 0.001f);
    }

    @Test
    public void aChannelThatDoesNotReachContributesNoGain() {
        assertEquals(0f, VoiceChannel.gain(1f, 12f, "red", "blue"), 0.0001f);
    }

    @Test
    public void aNonPositiveRangeIsSilence() {
        assertEquals(0f, VoiceChannel.gain(0f, 0f, "world", "world"), 0.0001f);
        assertEquals(0f, VoiceChannel.gain(0f, -5f, "world", "world"), 0.0001f);
    }

    @Test
    public void squaredDistanceOverloadMatchesTheLinearOne() {
        float linear = VoiceChannel.gain(6f, 12f, "world", "world");
        float squared = VoiceChannel.gainFromSquared(36f, 12f, "world", "world");
        assertEquals(linear, squared, 0.0001f);
    }

    @Test
    public void inRangeExcludesTheEdgeItself() {
        assertTrue(VoiceChannel.inRange(11.9f, 12f));
        assertFalse(VoiceChannel.inRange(12f, 12f));
        assertFalse(VoiceChannel.inRange(-1f, 12f));
        assertFalse(VoiceChannel.inRange(1f, 0f));
    }

    @Test
    public void generatedCodeHasTheAdvertisedShape() {
        String code = VoiceChannel.generateCode(new java.util.Random(7));
        assertTrue(code.startsWith(VoiceChannel.CODE_PREFIX));
        assertEquals(VoiceChannel.CODE_PREFIX.length() + 4, code.length());
        assertTrue(VoiceChannel.isJoinCode(code));
    }

    @Test
    public void codeAlphabetHasNoAmbiguousCharacters() {
        // A code is read aloud and typed by hand, so O/0 and I/1/L must never appear.
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 200; i++) {
            String body = VoiceChannel.generateCode(random).substring(VoiceChannel.CODE_PREFIX.length());
            for (char c : body.toCharArray()) {
                assertFalse("ambiguous character " + c, "O0IL1".indexOf(c) >= 0);
            }
        }
    }

    @Test
    public void isJoinCodeRejectsPlainNamesAndMalformedCodes() {
        assertFalse(VoiceChannel.isJoinCode("team"));
        assertFalse(VoiceChannel.isJoinCode("GLOWBERRY-TOOLONG"));
        assertFalse(VoiceChannel.isJoinCode("GLOWBERRY-"));
        assertFalse(VoiceChannel.isJoinCode("GLOWBERRY-OOOO")); // ambiguous letters are not in the alphabet
        assertTrue(VoiceChannel.isJoinCode(" glowberry-7f2q "));
    }

    @Test
    public void joinCodeIsJustAChannelIdSoTypingItJoinsTheSameRoom() {
        String code = VoiceChannel.generateCode(new java.util.Random(3));
        // Normalising then comparing is exactly what both peers do, so the code is the whole
        // invite: no other state has to travel for the two to land in the same channel.
        assertEquals(code.toLowerCase(java.util.Locale.ROOT), VoiceChannel.normalize(code));
        assertTrue(VoiceChannel.canHear(code, VoiceChannel.normalize(code)));
    }
}
