package org.chimeramc.client.core.mods.inbuilt.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.model.ModAvailability;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.junit.Test;

/**
 * The badge/grey-out rule. It is the single source of truth for three things (the badge, the
 * grey-out, the refused toggle), so a regression here shows as the exact defect the badge exists
 * to fix: a module that looks usable but does nothing.
 */
public class ModAvailabilityTest {

    @Test
    public void modulesNeedingGameDataAreUnavailable() {
        assertTrue(ModAvailability.isUnavailable(ModIds.ARMOR_HUD));
        assertTrue(ModAvailability.isUnavailable(ModIds.CRYSTAL_OPTIMIZER));
        assertTrue(ModAvailability.isUnavailable(ModIds.HITBOX));
    }

    @Test
    public void workingModulesAreAvailableAndInteractive() {
        String[] working = {
                ModIds.AUTO_SPRINT, ModIds.ZOOM, ModIds.FPS_DISPLAY, ModIds.CPS_DISPLAY,
                ModIds.SNAPLOOK, ModIds.GYRO, ModIds.HIT_REGISTRATION, ModIds.HIT_TIMING,
                ModIds.VOICE_CHAT, ModIds.MOD_MENU
        };
        for (String id : working) {
            assertFalse(id + " must stay usable", ModAvailability.isUnavailable(id));
            assertTrue(id + " must stay clickable", ModAvailability.isInteractive(id));
            assertNull(id + " must carry no badged reason",
                    ModAvailability.unavailableReason(id));
        }
    }

    @Test
    public void unavailableModulesAllCarryTheNoGameDataReason() {
        for (String id : new String[]{
                ModIds.ARMOR_HUD, ModIds.CRYSTAL_OPTIMIZER, ModIds.HITBOX}) {
            assertEquals(ModAvailability.REASON_NO_GAME_DATA, ModAvailability.unavailableReason(id));
            assertFalse(id + " must not be clickable", ModAvailability.isInteractive(id));
        }
    }

    @Test
    public void nullAndUnknownIdsAreTreatedAsUsable() {
        // A module added without a game-data need must not be greyed out by default; the badge is
        // reserved for modules that are known not to work.
        assertFalse(ModAvailability.isUnavailable(null));
        assertFalse(ModAvailability.isUnavailable("some_future_mod"));
        assertTrue(ModAvailability.isInteractive("some_future_mod"));
    }

    @Test
    public void availabilityFollowsRequiresGameDataWithoutDrifting() {
        // The two must agree by construction: availability is derived from the game-data need,
        // so a module cannot be greyed out for one reason and excused for another.
        for (String id : new String[]{
                ModIds.ARMOR_HUD, ModIds.CRYSTAL_OPTIMIZER, ModIds.HITBOX,
                ModIds.AUTO_SPRINT, ModIds.ZOOM, ModIds.HIT_REGISTRATION}) {
            assertEquals(ModIds.requiresGameData(id), ModAvailability.isUnavailable(id));
        }
    }
}
