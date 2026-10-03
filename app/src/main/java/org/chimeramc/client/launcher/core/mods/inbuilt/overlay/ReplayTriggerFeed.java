package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.os.SystemClock;

import org.chimeramc.client.core.replay.DeathWatcher;
import org.chimeramc.client.core.replay.KillStreakCounter;
import org.chimeramc.client.core.replay.ReplayManager;
import org.chimeramc.client.preloader.PreloaderInput;

/**
 * The single place the in-game client's own signals drive the Replay highlight triggers.
 *
 * <p>{@link ReplayManager} exposes {@code onDeath}, {@code onKillStreak} and {@code onCombo}, but
 * nothing called them, so the whole Tier-4 auto-highlight system sat unused and clips only saved
 * on a manual record. This feed connects the signals the client already has:
 *
 * <ul>
 *   <li><b>Death</b> — the local player's health, read through the preloader's local-player feed.
 *       The reading is version-configured and fail-closed: with no offset, or an implausible
 *       value, no death is ever reported.</li>
 *   <li><b>Combo</b> — the same landed-hit signal {@link HitTimingMod} uses for Select Hit. A
 *       combo is recorded when the solver advances its streak, i.e. when a click landed after the
 *       window.</li>
 *   <li><b>Kill streak</b> — consecutive opponent deaths credited to the player's own hits.
 *       Credit is inferred by the existing {@link KillCreditRegistry} heuristic (a hit you landed
 *       within two seconds followed by that player's death), so this needs no native entity feed.</li>
 * </ul>
 *
 * <p>All three are polled from the overlay manager's frame tick, which is the game's own tick, so
 * the signal is sampled at the rate the game runs rather than a second timer.
 */
public final class ReplayTriggerFeed {

    private static final DeathWatcher DEATH_WATCHER = new DeathWatcher();
    private static final KillStreakCounter STREAK_COUNTER = new KillStreakCounter();

    private static int lastCombo;
    private static String lastCreditedKill;

    private ReplayTriggerFeed() {}

    /** Clears per-session state; call when a game session starts. */
    public static void reset() {
        DEATH_WATCHER.reset();
        STREAK_COUNTER.reset();
        lastCombo = 0;
        lastCreditedKill = null;
    }

    /**
     * Advances the feed by one game frame.
     *
     * <p>A no-op when no Replay manager exists (the launcher process before init) so the tick can
     * be called unconditionally.
     */
    public static void tick(long nowMs) {
        ReplayManager manager = ReplayManager.get();
        if (manager == null) return;
        pollDeath(manager, nowMs);
        pollCombo(manager);
        pollKillStreak(manager, nowMs);
    }

    private static void pollDeath(ReplayManager manager, long nowMs) {
        float[] health = PreloaderInput.readLocalPlayerHealth();
        boolean available = health != null && health.length >= 1;
        float value = available ? health[0] : 0f;
        if (DEATH_WATCHER.onSample(value, available, nowMs)) {
            manager.onDeath();
        }
    }

    private static void pollCombo(ReplayManager manager) {
        int combo = HitTimingMod.getCombo();
        // Fire only on a fresh landed hit; the solver clamps at MAX_COMBO, so a value that did not
        // move is the same hit being re-read, not a new one.
        if (combo > lastCombo) {
            manager.onCombo(combo);
        }
        lastCombo = combo;
    }

    private static void pollKillStreak(ReplayManager manager, long nowMs) {
        String credited = KillEffectsMod.pollCreditedKill();
        if (credited == null || credited.equals(lastCreditedKill)) return;
        lastCreditedKill = credited;
        manager.onKillStreak(STREAK_COUNTER.onKill(nowMs));
    }
}
