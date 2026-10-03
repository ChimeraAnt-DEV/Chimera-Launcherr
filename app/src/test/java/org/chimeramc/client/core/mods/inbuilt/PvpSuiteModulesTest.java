package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.model.ModAvailability;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.junit.Test;

/**
 * Placement rules for the PvP Suite (V1.1).
 *
 * <p>The spec is explicit that the four new modules sit alongside the existing modules in the
 * General tab, and that no new PvP section or tab is added. These pin exactly that: the suite
 * modules are not PvP-classified (so they do not form their own section), they are usable (the
 * peer route works without a native feed), and they do not drag the PvP section apart.
 */
public class PvpSuiteModulesTest {

    private static final String[] SUITE = {
            ModIds.REACH_INDICATOR, ModIds.TRAJECTORY_PREDICTION,
            ModIds.HIT_PREDICTION, ModIds.KILL_EFFECTS
    };

    /** The two whose output comes only from the voice peer feed and the local view. */
    private static final String[] PEER_ROUTE = {
            ModIds.REACH_INDICATOR, ModIds.HIT_PREDICTION
    };

    @Test
    public void theSuiteIsNotClassifiedAsPvp() {
        // If they were, the menu would draw a second PvP section and the spec's "no new PvP
        // section" rule would be broken.
        for (String id : SUITE) {
            assertFalse(id + " must not form a PvP section", ModIds.isPvpModule(id));
        }
    }

    @Test
    public void theSuiteIsNotClassifiedAsVoice() {
        for (String id : SUITE) {
            assertFalse(id + " must not be a voice module", ModIds.isVoiceModule(id));
        }
    }

    @Test
    public void thePeerRouteModulesStayUsableAndClickable() {
        // Reach Indicator and Hit Prediction read peers from the voice feed and the local view;
        // neither needs the native entity feed, so both work today.
        for (String id : PEER_ROUTE) {
            assertFalse(id + " must stay usable", ModAvailability.isUnavailable(id));
            assertTrue(id + " must stay clickable", ModAvailability.isInteractive(id));
            assertNull(id + " must carry no badged reason", ModAvailability.unavailableReason(id));
        }
    }

    @Test
    public void theGameDataModulesAreGreyedOutRatherThanSilentlyDoingNothing() {
        // Trajectory Prediction needs the held item and Custom Kill Effects needs the opponent
        // death feed. No provider is installed for either, so an enabled switch would change
        // nothing on screen -- which reads as a broken module. They are badged instead.
        for (String id : new String[]{ModIds.TRAJECTORY_PREDICTION, ModIds.KILL_EFFECTS}) {
            assertTrue(id + " must be badged", ModAvailability.isUnavailable(id));
            assertFalse(id + " must not be clickable", ModAvailability.isInteractive(id));
            assertEquals(ModAvailability.REASON_NO_GAME_DATA,
                    ModAvailability.unavailableReason(id));
        }
    }

    @Test
    public void theSuiteDoesNotMoveTheExistingPvpRun() {
        // Adding the suite must not split the PvP run: grouping the full list still leaves one
        // contiguous PvP block at the end.
        java.util.List<UnifiedMod> mods = new java.util.ArrayList<>();
        for (String id : SUITE) mods.add(mod(id));
        mods.add(mod(ModIds.HITBOX));
        mods.add(mod(ModIds.QUICK_DROP));
        mods.add(mod(ModIds.HIT_TIMING));

        java.util.List<UnifiedMod> ordered = InbuiltModuleProvider.groupPvpLast(mods);
        int runs = 0;
        boolean inPvpRun = false;
        for (UnifiedMod mod : ordered) {
            boolean pvp = ModIds.isPvpModule(mod.getId());
            if (pvp && !inPvpRun) runs++;
            inPvpRun = pvp;
        }
        assertEquals("PvP must remain a single section", 1, runs);
    }

    private static UnifiedMod mod(String id) {
        return new UnifiedMod(id, id, id, "inbuilt", UnifiedMod.Source.INBUILT,
                false, null, false, "inbuilt", "Inbuilt Features", null, null);
    }
}
