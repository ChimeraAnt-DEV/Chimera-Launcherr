package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * The mute set is viewer-only state, so the important properties are that it is per-peer, that it
 * survives a save/restore round trip, and that clearing it really clears.
 */
public class VoiceMutesTest {

    @Test
    public void mutesOnePeerWithoutAffectingAnother() {
        VoiceMutes mutes = new VoiceMutes();
        mutes.mute("a");
        assertTrue(mutes.isMuted("a"));
        assertFalse(mutes.isMuted("b"));
        assertEquals(1, mutes.size());
    }

    @Test
    public void toggleFlipsAndReportsTheNewState() {
        VoiceMutes mutes = new VoiceMutes();
        assertTrue(mutes.toggle("a"));
        assertTrue(mutes.isMuted("a"));
        assertFalse(mutes.toggle("a"));
        assertFalse(mutes.isMuted("a"));
    }

    @Test
    public void serialiseThenRestorePreservesTheSet() {
        VoiceMutes original = new VoiceMutes();
        original.mute("a");
        original.mute("b");
        VoiceMutes restored = new VoiceMutes();
        restored.restoreFrom(Arrays.asList(original.serialize().split(",")));
        assertTrue(restored.isMuted("a"));
        assertTrue(restored.isMuted("b"));
        assertFalse(restored.isMuted("c"));
    }

    @Test
    public void blankIdsAreIgnored() {
        VoiceMutes mutes = new VoiceMutes();
        mutes.mute(null);
        mutes.mute("   ");
        assertTrue(mutes.isEmpty());
    }

    @Test
    public void clearEmptiesTheSet() {
        VoiceMutes mutes = new VoiceMutes();
        mutes.mute("a");
        mutes.clear();
        assertTrue(mutes.isEmpty());
    }
}
