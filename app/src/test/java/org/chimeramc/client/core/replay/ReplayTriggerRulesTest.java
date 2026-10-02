package org.chimeramc.client.core.replay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the two pure rules behind the Replay highlight triggers: the death transition and the
 * kill-streak window.
 *
 * <p>Both decide whether a highlight is saved, so the "must have seen a live reading first",
 * "debounce", "plausible value" and "streak window" rules are tested without a device, a clock or
 * a mock.
 */
public class ReplayTriggerRulesTest {

    // --- DeathWatcher ---------------------------------------------------------------------------

    @Test
    public void aDeathIsReportedOnlyAfterALiveReading() {
        DeathWatcher watcher = new DeathWatcher();
        // A zero reading before any live one is not a death: the feed may simply be missing.
        assertFalse(watcher.onSample(0f, true, 0L));
        assertFalse(watcher.onSample(20f, true, 100L));
        assertTrue(watcher.onSample(0f, true, 200L));
    }

    @Test
    public void aMissingReadingNeverProducesADeath() {
        DeathWatcher watcher = new DeathWatcher();
        assertFalse(watcher.onSample(0f, false, 0L));
        assertFalse(watcher.onSample(0f, false, 1_000L));
    }

    @Test
    public void anImplausibleReadingIsIgnored() {
        DeathWatcher watcher = new DeathWatcher();
        // 200 is not a health value; it is a wrong offset. It must not arm the watcher.
        assertFalse(watcher.onSample(200f, true, 0L));
        assertFalse(watcher.onSample(0f, true, 100L));
    }

    @Test
    public void repeatedZeroIsDebouncedToASingleDeath() {
        DeathWatcher watcher = new DeathWatcher();
        watcher.onSample(20f, true, 0L);
        assertTrue(watcher.onSample(0f, true, 100L));
        // The death screen keeps reporting zero; those frames are the same death.
        assertFalse(watcher.onSample(0f, true, 200L));
        assertFalse(watcher.onSample(0f, true, 300L));
    }

    @Test
    public void respawnReArmsTheNextDeath() {
        DeathWatcher watcher = new DeathWatcher();
        watcher.onSample(20f, true, 0L);
        assertTrue(watcher.onSample(0f, true, 100L));
        assertFalse(watcher.onSample(20f, true, 200L));
        // A second death well past the debounce is a new event.
        assertTrue(watcher.onSample(0f, true, DeathWatcher.DEBOUNCE_MS + 1_000L));
    }

    // --- KillStreakCounter ----------------------------------------------------------------------

    @Test
    public void theFirstKillStartsAStreakOfOne() {
        KillStreakCounter counter = new KillStreakCounter();
        assertEquals(1, counter.onKill(0L));
    }

    @Test
    public void killsWithinTheWindowIncrement() {
        KillStreakCounter counter = new KillStreakCounter();
        assertEquals(1, counter.onKill(0L));
        assertEquals(2, counter.onKill(1_000L));
        assertEquals(3, counter.onKill(2_000L));
    }

    @Test
    public void aLongGapResetsTheStreak() {
        KillStreakCounter counter = new KillStreakCounter();
        counter.onKill(0L);
        counter.onKill(1_000L);
        // Just past the window (elapsed 30_001) starts a fresh streak.
        assertEquals(1, counter.onKill(1_000L + KillStreakCounter.STREAK_WINDOW_MS + 1L));
    }

    @Test
    public void aBackwardsClockStartsAFreshStreak() {
        KillStreakCounter counter = new KillStreakCounter();
        counter.onKill(10_000L);
        // A clock that went backwards must not produce a negative-elapsed streak.
        assertEquals(1, counter.onKill(5_000L));
    }

    @Test
    public void resetClearsTheStreak() {
        KillStreakCounter counter = new KillStreakCounter();
        counter.onKill(0L);
        counter.onKill(1_000L);
        counter.reset();
        assertEquals(0, counter.streak());
        assertEquals(1, counter.onKill(2_000L));
    }
}
