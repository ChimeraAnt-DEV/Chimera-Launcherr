package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitTimingSolver;
import org.junit.Test;

/**
 * Timing rules for the Select Hit module. No mocks: the solver is pure.
 */
public class HitTimingSolverTest {

    private static final long COOLDOWN = HitTimingSolver.DEFAULT_COOLDOWN_MS;

    @Test
    public void nothingAttackedReadsAsReady() {
        HitTimingSolver solver = new HitTimingSolver();
        HitTimingSolver.Decision decision = solver.evaluate(0L);
        assertEquals(HitTimingSolver.Advice.READY, decision.advice);
        // READY is deliberately not green: nothing has been swung yet, so "hit now" would be an
        // unearned instruction. The indicator is idle until the first attack.
        assertFalse(decision.isGreen());
        assertTrue(decision.isIdle());
        assertEquals(0, decision.combo);
    }

    @Test
    public void aHitInsideTheWindowReadsAsWait() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(1000L);
        HitTimingSolver.Decision decision = solver.evaluate(1100L);
        assertEquals(HitTimingSolver.Advice.WAIT, decision.advice);
        assertFalse(decision.isGreen());
        assertEquals(COOLDOWN - 100L, decision.remainingMs);
    }

    @Test
    public void theWindowClosingReadsAsHit() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(1000L);
        assertEquals(HitTimingSolver.Advice.HIT, solver.evaluate(1000L + COOLDOWN).advice);
        assertEquals(HitTimingSolver.Advice.HIT, solver.evaluate(1000L + COOLDOWN + 50L).advice);
    }

    /** Progress runs 0..1 across the window, which is what the timing bar draws. */
    @Test
    public void progressTracksTheWindow() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(0L);
        assertEquals(0f, solver.evaluate(0L).progress, 0.001f);
        assertEquals(0.5f, solver.evaluate(COOLDOWN / 2).progress, 0.001f);
        assertEquals(1f, solver.evaluate(COOLDOWN).progress, 0.001f);
    }

    @Test
    public void aDiscardedClickDoesNotAdvanceTheCombo() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(1000L);
        assertEquals(1, solver.getCombo());
        // Well inside the window: this click would be discarded by the game.
        solver.onAttack(1100L);
        assertEquals("a discarded click must not count as a hit", 1, solver.getCombo());
        // And it must not push the window out, or the indicator would lie.
        assertEquals(HitTimingSolver.Advice.WAIT, solver.evaluate(1200L).advice);
        assertEquals(COOLDOWN - 200L, solver.evaluate(1200L).remainingMs);
    }

    @Test
    public void aRespectedClickAdvancesTheCombo() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(0L);
        solver.onAttack(COOLDOWN);
        solver.onAttack(COOLDOWN * 2);
        assertEquals(3, solver.getCombo());
    }

    @Test
    public void comboIsCappedSoTheIndicatorStaysReadable() {
        HitTimingSolver solver = new HitTimingSolver();
        for (int i = 0; i <= HitTimingSolver.MAX_COMBO + 3; i++) {
            solver.onAttack(i * COOLDOWN);
        }
        assertEquals(HitTimingSolver.MAX_COMBO, solver.getCombo());
    }

    @Test
    public void aLongPauseResetsTheStreak() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(0L);
        solver.onAttack(COOLDOWN);
        assertEquals(2, solver.getCombo());
        long later = COOLDOWN + HitTimingSolver.STREAK_RESET_MS + 1L;
        assertEquals(HitTimingSolver.Advice.READY, solver.evaluate(later).advice);
        solver.onAttack(later);
        assertEquals("combat restarted, so the streak restarts", 1, solver.getCombo());
    }

    @Test
    public void aBackwardsClockIsTreatedAsAFreshEngagement() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(5000L);
        solver.onAttack(1000L);
        assertEquals(1, solver.getCombo());
        assertEquals(HitTimingSolver.Advice.WAIT, solver.evaluate(1000L).advice);
    }

    @Test
    public void cooldownIsClampedToSomethingUsable() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.setCooldownMs(0);
        assertTrue(solver.getCooldownMs() >= 50L);
        solver.setCooldownMs(100000);
        assertTrue(solver.getCooldownMs() <= 2000L);
    }

    @Test
    public void resetClearsTheStreak() {
        HitTimingSolver solver = new HitTimingSolver();
        solver.onAttack(0L);
        solver.reset();
        assertEquals(0, solver.getCombo());
        assertEquals(HitTimingSolver.Advice.READY, solver.evaluate(0L).advice);
    }
}
