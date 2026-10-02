package org.chimeramc.client.core.replay;

/**
 * Counts consecutive kills credited to the player, as pure arithmetic.
 *
 * <p>A "kill streak" is a run of kills that happen close together. The counter does not care how a
 * kill was credited (that is {@code KillCreditRegistry}'s job); it only decides whether the run is
 * still alive, so a break longer than {@link #STREAK_WINDOW_MS} starts a fresh streak at one.
 *
 * <p>Pure and clock-injected (the caller passes {@code nowMs}) so the streak rules are
 * unit-testable without a device and without a real clock.
 */
public final class KillStreakCounter {

    /** A gap longer than this ends the streak; the next kill starts a new one. */
    public static final long STREAK_WINDOW_MS = 30_000L;

    private long lastKillMs = Long.MIN_VALUE;
    private int streak;

    /**
     * Records a credited kill and returns the streak length it produced.
     *
     * @return 1 for the first kill, 2 for the next within the window, and so on
     */
    public int onKill(long nowMs) {
        if (lastKillMs == Long.MIN_VALUE || nowMs - lastKillMs > STREAK_WINDOW_MS
                || nowMs < lastKillMs) {
            streak = 1;
        } else {
            streak++;
        }
        lastKillMs = nowMs;
        return streak;
    }

    /** The current streak length without recording a kill. */
    public int streak() {
        return streak;
    }

    public void reset() {
        lastKillMs = Long.MIN_VALUE;
        streak = 0;
    }
}
