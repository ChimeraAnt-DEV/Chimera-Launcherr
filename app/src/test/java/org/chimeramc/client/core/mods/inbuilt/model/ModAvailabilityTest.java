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

    /**
     * The Hitboxes module draws peers from the voice feed, so it must not be greyed out even
     * though part of what it can draw needs the native entity list. Greying the whole module was
     * the defect: it made the working peer boxes unreachable.
     */
    @Test
    public void hitboxStaysUsableBecauseThePeerRouteWorks() {
        assertFalse(ModAvailability.isUnavailable(ModIds.HITBOX));
        assertTrue(ModAvailability.isInteractive(ModIds.HITBOX));
        assertNull(ModAvailability.unavailableReason(ModIds.HITBOX));
    }

    /**
     * Only the hitbox outputs that need the native entity list are disabled, and the working ones
     * are not. This is what stops the config dialog offering a switch that can never draw.
     */
    @Test
    public void onlyNativeHitboxOutputsAreDisabledOptions() {
        for (String nativeOutput : new String[]{
                "hitbox_show_mobs", "hitbox_show_items", "hitbox_show_projectiles",
                "hitbox_show_thrown_items", "hitbox_show_crit_line", "hitbox_show_combo_box"}) {
            assertFalse(nativeOutput + " needs the game, so must be disabled",
                    ModAvailability.isOptionInteractive(ModIds.HITBOX, nativeOutput));
            assertEquals(ModAvailability.REASON_NO_GAME_DATA,
                    ModAvailability.optionUnavailableReason(ModIds.HITBOX, nativeOutput));
        }
        for (String peerOutput : new String[]{
                "hitbox_show_players", "hitbox_show_look_line", "hitbox_show_target_box"}) {
            assertTrue(peerOutput + " works from the voice feed, so must stay enabled",
                    ModAvailability.isOptionInteractive(ModIds.HITBOX, peerOutput));
            assertNull(ModAvailability.optionUnavailableReason(ModIds.HITBOX, peerOutput));
        }
    }

    /** Options of other modules are never disabled by the hitbox rule. */
    @Test
    public void otherModulesOptionsAreNotAffected() {
        assertTrue(ModAvailability.isOptionInteractive(ModIds.ARMOR_HUD, "hitbox_show_mobs"));
        assertTrue(ModAvailability.isOptionInteractive(ModIds.AUTO_SPRINT, "hitbox_show_items"));
        assertTrue(ModAvailability.isOptionInteractive(ModIds.HITBOX, null));
    }

    @Test
    public void unavailableModulesAllCarryTheNoGameDataReason() {
        for (String id : new String[]{
                ModIds.ARMOR_HUD, ModIds.CRYSTAL_OPTIMIZER}) {
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
