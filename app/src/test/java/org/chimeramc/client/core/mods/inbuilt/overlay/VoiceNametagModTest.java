package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * The nametag icon module's seam contract: with no tag provider it draws nothing and says so, it
 * degrades to "nothing" rather than crashing if the provider throws, and turning the module off
 * stops it reading. No Context is needed, so this is a plain JVM test.
 */
public class VoiceNametagModTest {

    @After
    public void tearDown() {
        VoiceNametagMod.setTagSource(null);
        VoiceNametagMod.setEnabled(false, null);
    }

    @Test
    public void withNoProviderTheModuleIsActiveButReportsAwaitingData() {
        VoiceNametagMod.setTagSource(null);
        VoiceNametagMod.setEnabled(true, null);
        assertTrue(VoiceNametagMod.isActive());
        assertTrue(VoiceNametagMod.isAwaitingGameData());
        assertTrue(VoiceNametagMod.readTags().isEmpty());
    }

    @Test
    public void aProviderSuppliesTheTags() {
        NametagIconProjector.Tag tag = new NametagIconProjector.Tag(
                "peer", "Peer", 0f, 0f, 5f, 2f, "world");
        VoiceNametagMod.setTagSource(() -> Collections.singletonList(tag));
        VoiceNametagMod.setEnabled(true, null);
        assertFalse(VoiceNametagMod.isAwaitingGameData());
        List<NametagIconProjector.Tag> tags = VoiceNametagMod.readTags();
        assertEquals(1, tags.size());
        assertEquals("peer", tags.get(0).peerId);
    }

    @Test
    public void aThrowingOrNullReturningProviderDegradesToNothing() {
        VoiceNametagMod.setEnabled(true, null);
        VoiceNametagMod.setTagSource(() -> {
            throw new IllegalStateException("feed down");
        });
        assertTrue(VoiceNametagMod.readTags().isEmpty());

        VoiceNametagMod.setTagSource(() -> null);
        assertTrue(VoiceNametagMod.readTags().isEmpty());
    }

    @Test
    public void disablingStopsReadsEvenWithAProvider() {
        VoiceNametagMod.setTagSource(() -> Collections.singletonList(
                new NametagIconProjector.Tag("peer", "Peer", 0f, 0f, 5f, 2f, "world")));
        VoiceNametagMod.setEnabled(false, null);
        assertFalse(VoiceNametagMod.isActive());
        assertTrue(VoiceNametagMod.readTags().isEmpty());
    }
}
