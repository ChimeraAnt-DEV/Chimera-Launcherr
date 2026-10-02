package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pure "recent hit registry" for the Custom Kill Effects module.
 *
 * <p>The module spawns a client-side particle burst when a player you hit dies. Bedrock gives a
 * client no kill event, so credit is inferred: if a player you hit within the last
 * {@link #CREDIT_WINDOW_MS} milliseconds is seen to die, the kill is credited to you. That is a
 * heuristic and it is labelled as one - the module never claims a server-confirmed kill.
 *
 * <p>Two deliberate rules:
 *
 * <ul>
 *   <li><b>A hit is attributed to a specific player.</b> The registry keys by player id, so a hit
 *       on one player cannot credit a different player's death.</li>
 *   <li><b>The window is short and single-use.</b> A hit older than the window is dropped, and a
 *       death consumes the hit, so one hit cannot credit two deaths or a death minutes later.</li>
 * </ul>
 *
 * <p>Pure and clock-injected (the caller passes {@code nowMs}), so the window rules are
 * unit-testable without a device and without a real clock.
 */
public final class KillCreditRegistry {

    /** A hit credits a death only if the death happens within this long after it. */
    public static final long CREDIT_WINDOW_MS = 2000L;

    /** One recorded hit: who was hit, and when. */
    public static final class Hit {
        public final String playerId;
        public final long atMs;

        Hit(String playerId, long atMs) {
            this.playerId = playerId;
            this.atMs = atMs;
        }
    }

    private final List<Hit> recent = new ArrayList<>();

    /**
     * Records a hit you landed on {@code playerId} at {@code nowMs}.
     *
     * <p>Keeps the newest hit per player: a second hit refreshes the window rather than stacking,
     * so a burst of clicks does not leave several stale credits behind.
     */
    public void recordHit(String playerId, long nowMs) {
        if (playerId == null) return;
        for (Iterator<Hit> it = recent.iterator(); it.hasNext(); ) {
            if (playerId.equals(it.next().playerId)) it.remove();
        }
        recent.add(new Hit(playerId, nowMs));
    }

    /**
     * Whether a death of {@code playerId} at {@code nowMs} is credited to you.
     *
     * <p>Consumes the matching hit on a positive answer, so the same hit cannot credit two
     * deaths. A death with no in-window hit returns false - the module then draws nothing rather
     * than a burst for a kill that may not be yours.
     */
    public boolean creditKill(String playerId, long nowMs) {
        if (playerId == null) return false;
        prune(nowMs);
        for (Iterator<Hit> it = recent.iterator(); it.hasNext(); ) {
            Hit hit = it.next();
            if (playerId.equals(hit.playerId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Drops hits older than the window; called on every mutation so the list stays small. */
    public void prune(long nowMs) {
        for (Iterator<Hit> it = recent.iterator(); it.hasNext(); ) {
            if (nowMs - it.next().atMs > CREDIT_WINDOW_MS) it.remove();
        }
    }

    /** Number of live hits, for tests and diagnostics. */
    public int pendingHits() {
        return recent.size();
    }

    public void reset() {
        recent.clear();
    }
}
