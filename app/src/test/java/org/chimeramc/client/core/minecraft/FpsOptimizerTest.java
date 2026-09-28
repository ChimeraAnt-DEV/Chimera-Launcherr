package org.chimeramc.client.core.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the host-quieting rule. No mocks: the decision is a pure function, so a real call is the
 * whole thing under test.
 */
public class FpsOptimizerTest {

    @Test
    public void disabledMeansNothingIsDone() {
        assertEquals(FpsOptimizer.Action.NONE,
                FpsOptimizer.decide(false, true, 0, false, false));
    }

    @Test
    public void noSessionMeansNothingIsDone() {
        // The module is a session-scoped optimization; with no game running there is nothing to
        // protect and the launcher must behave exactly as before.
        assertEquals(FpsOptimizer.Action.NONE,
                FpsOptimizer.decide(true, false, 0, false, false));
    }

    @Test
    public void aRunningSessionQuietsTheHost() {
        assertEquals(FpsOptimizer.Action.QUIET_HOST,
                FpsOptimizer.decide(true, true, 0, false, false));
        assertTrue(FpsOptimizer.servesCachedData(FpsOptimizer.Action.QUIET_HOST));
        assertFalse(FpsOptimizer.pausesSpeculativeWork(FpsOptimizer.Action.QUIET_HOST));
    }

    @Test
    public void thermalPressureShedsBackgroundWork() {
        assertEquals(FpsOptimizer.Action.SHED_BACKGROUND,
                FpsOptimizer.decide(true, true, 3, false, false));
        assertTrue(FpsOptimizer.servesCachedData(FpsOptimizer.Action.SHED_BACKGROUND));
        assertTrue(FpsOptimizer.pausesSpeculativeWork(FpsOptimizer.Action.SHED_BACKGROUND));
    }

    @Test
    public void batterySaverShedsBackgroundWork() {
        assertEquals(FpsOptimizer.Action.SHED_BACKGROUND,
                FpsOptimizer.decide(true, true, 0, true, false));
    }

    @Test
    public void lowMemoryShedsBackgroundWork() {
        assertEquals(FpsOptimizer.Action.SHED_BACKGROUND,
                FpsOptimizer.decide(true, true, 0, false, true));
    }

    @Test
    public void coolThermalStatusDoesNotShed() {
        // Severity 1/2 are "light" and "moderate" on the platform ladder; the game may still be
        // near its target frame rate, so the module must not over-react and pause work a player
        // is waiting on.
        assertEquals(FpsOptimizer.Action.QUIET_HOST,
                FpsOptimizer.decide(true, true, 2, false, false));
    }

    @Test
    public void describeNeverClaimsAFrameRate() {
        // The module cannot measure the game's FPS from the launcher; a number here would be the
        // same dishonesty as promising a ping. None of the labels may contain a digit followed by
        // "fps" in any casing.
        for (FpsOptimizer.Action action : FpsOptimizer.Action.values()) {
            String text = FpsOptimizer.describe(action).toLowerCase();
            assertFalse("describe() must not promise an FPS figure: " + text,
                    text.matches(".*\\d+\\s*fps.*"));
        }
    }
}
