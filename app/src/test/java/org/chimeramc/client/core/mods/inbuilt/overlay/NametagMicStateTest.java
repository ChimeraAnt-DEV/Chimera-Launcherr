package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The nametag icon has three states and a precedence between them. The critical rule is that a
 * mute (either theirs or yours) beats speaking, because the icon's whole job is to say whether you
 * can hear the player right now.
 */
public class NametagMicStateTest {

    @Test
    public void aSelfMutedPeerShowsMutedEvenWhileLoud() {
        assertEquals(NametagMicState.State.MUTED,
                NametagMicState.resolve(0.9f, true, false));
    }

    @Test
    public void aPeerYouMutedShowsMutedEvenWhileLoud() {
        assertEquals(NametagMicState.State.MUTED,
                NametagMicState.resolve(0.9f, false, true));
    }

    @Test
    public void aboveTheFloorAndUnmutedIsSpeaking() {
        assertEquals(NametagMicState.State.SPEAKING,
                NametagMicState.resolve(0.5f, false, false));
    }

    @Test
    public void quietAndUnmutedIsIdle() {
        assertEquals(NametagMicState.State.IDLE,
                NametagMicState.resolve(0f, false, false));
        assertEquals(NametagMicState.State.IDLE,
                NametagMicState.resolve(MicIconStyle.AUDIBLE_FLOOR, false, false));
    }

    @Test
    public void onlySpeakingAnimates() {
        assertTrue(NametagMicState.animates(NametagMicState.State.SPEAKING));
        assertFalse(NametagMicState.animates(NametagMicState.State.IDLE));
        assertFalse(NametagMicState.animates(NametagMicState.State.MUTED));
    }

    @Test
    public void speakingFillGrowsFromTheFloorToFullGreen() {
        assertEquals(0f, MicIconStyle.speakingFill(0f), 0.0001f);
        assertEquals(0f, MicIconStyle.speakingFill(MicIconStyle.AUDIBLE_FLOOR), 0.0001f);
        assertTrue(MicIconStyle.speakingFill(0.5f) > 0f);
        assertTrue(MicIconStyle.speakingFill(0.5f) < 1f);
        assertEquals(1f, MicIconStyle.speakingFill(1f), 0.0001f);
    }

    @Test
    public void iconStyleIsClampedToKnownValues() {
        assertEquals(MicIconStyle.STYLE_CLASSIC, MicIconStyle.clamp(-1));
        assertEquals(MicIconStyle.STYLE_SMOOTH, MicIconStyle.clamp(1));
        assertEquals(MicIconStyle.STYLE_SMOOTH, MicIconStyle.clamp(99));
    }
}
