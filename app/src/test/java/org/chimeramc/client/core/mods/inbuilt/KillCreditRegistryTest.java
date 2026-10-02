package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.KillCreditRegistry;
import org.junit.Test;

/**
 * The Custom Kill Effects credit heuristic. Pure and clock-injected, so every window boundary is
 * testable. The rules that must hold: a hit credits only the player it was on, only inside the
 * window, and only once.
 */
public class KillCreditRegistryTest {

    @Test
    public void aHitInsideTheWindowCreditsTheDeath() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        assertTrue(registry.creditKill("p1", 1000L + KillCreditRegistry.CREDIT_WINDOW_MS));
    }

    @Test
    public void aDeathJustOutsideTheWindowIsNotCredited() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        assertFalse(registry.creditKill("p1",
                1000L + KillCreditRegistry.CREDIT_WINDOW_MS + 1L));
    }

    @Test
    public void aHitOnOnePlayerDoesNotCreditAnothersDeath() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        assertFalse(registry.creditKill("p2", 1100L));
        // The hit on p1 is still live afterwards.
        assertTrue(registry.creditKill("p1", 1100L));
    }

    @Test
    public void oneHitCreditsOnlyOneDeath() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        assertTrue(registry.creditKill("p1", 1100L));
        assertFalse(registry.creditKill("p1", 1200L));
    }

    @Test
    public void aSecondHitRefreshesTheWindowRatherThanStacking() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        registry.recordHit("p1", 1900L);
        assertEquals(1, registry.pendingHits());
        // The first hit's window has passed, but the refreshed one is still live.
        assertTrue(registry.creditKill("p1", 2500L));
    }

    @Test
    public void aDeathWithNoHitIsNotCredited() {
        KillCreditRegistry registry = new KillCreditRegistry();
        assertFalse(registry.creditKill("p1", 1000L));
    }

    @Test
    public void pruneDropsStaleHits() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        registry.recordHit("p2", 5000L);
        registry.prune(5000L);
        assertEquals(1, registry.pendingHits());
        assertFalse(registry.creditKill("p1", 5000L));
        assertTrue(registry.creditKill("p2", 5000L));
    }

    @Test
    public void nullsAreIgnoredRatherThanThrowing() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit(null, 1000L);
        assertEquals(0, registry.pendingHits());
        assertFalse(registry.creditKill(null, 1000L));
    }

    @Test
    public void resetClearsEverything() {
        KillCreditRegistry registry = new KillCreditRegistry();
        registry.recordHit("p1", 1000L);
        registry.reset();
        assertEquals(0, registry.pendingHits());
        assertFalse(registry.creditKill("p1", 1000L));
    }
}
