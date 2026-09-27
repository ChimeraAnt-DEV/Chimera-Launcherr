package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Capacity is the one rule that could quietly refuse a join, so its boundaries and the
 * "private channels have no cap" carve-out are pinned here. The rule is advisory by design, so a
 * non-positive capacity must never read as full.
 */
public class VoiceChannelCapacityTest {

    @Test
    public void aRoomIsFullOnlyAtOrAboveItsCap() {
        assertFalse(VoiceChannelCapacity.isFull(2, 3));
        assertTrue(VoiceChannelCapacity.isFull(3, 3));
        assertTrue(VoiceChannelCapacity.isFull(4, 3));
    }

    @Test
    public void noCapNeverReadsAsFull() {
        assertFalse(VoiceChannelCapacity.isFull(999, VoiceProtocol.CAPACITY_NONE));
        assertFalse(VoiceChannelCapacity.isFull(999, -1));
        assertTrue(VoiceChannelCapacity.canJoin(999, VoiceProtocol.CAPACITY_NONE, false));
    }

    @Test
    public void privateChannelsAlwaysAdmitByCode() {
        // The code bounds who can join, so a private channel is never refused for capacity.
        assertTrue(VoiceChannelCapacity.canJoin(999, 2, true));
    }

    @Test
    public void aFullPublicRoomRefuses() {
        assertFalse(VoiceChannelCapacity.canJoin(8, 8, false));
        assertTrue(VoiceChannelCapacity.canJoin(7, 8, false));
    }

    @Test
    public void hostCapacityIsClampedToTheSettingsRange() {
        assertEquals(VoiceChannelCapacity.MIN_HOST_CAPACITY,
                VoiceChannelCapacity.clampHostCapacity(1));
        assertEquals(VoiceChannelCapacity.MAX_HOST_CAPACITY,
                VoiceChannelCapacity.clampHostCapacity(9999));
        assertEquals(VoiceProtocol.CAPACITY_NONE, VoiceChannelCapacity.clampHostCapacity(0));
        assertEquals(8, VoiceChannelCapacity.clampHostCapacity(8));
    }

    @Test
    public void describeShowsSlashOnlyWhenCapped() {
        assertEquals("3/8", VoiceChannelCapacity.describe(3, 8));
        assertEquals("3", VoiceChannelCapacity.describe(3, VoiceProtocol.CAPACITY_NONE));
    }
}
