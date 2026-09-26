package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Pure decision logic for the Select Hit module.
 *
 * <p>Bedrock gives an attack a short invulnerability window after it lands: a second click
 * inside that window is discarded, so a player who spam-clicks actually lands <em>fewer</em>
 * hits than one who waits for the window to close. This solver tracks the timestamps of the
 * player's own attack input and reports when the next hit can land.
 *
 * <p>Honest scope: this is a metronome over input the player already produced. It cannot read
 * the server's cooldown, cannot make a hit land, and does not click for the player — it tells
 * the player when their own next click will count, which is the same information a practiced
 * player carries in their head.
 *
 * <p>No Android or game types here on purpose: the timing rules are unit-testable on their own,
 * and the overlay is a thin renderer over {@link Decision}.
 */
public final class HitTimingSolver {

    /** Bedrock's post-hit invulnerability window. Clicks inside it are discarded. */
    public static final long DEFAULT_COOLDOWN_MS = 500L;

    /** A gap longer than this means combat restarted, so the streak resets. */
    public static final long STREAK_RESET_MS = 2500L;

    /** Streak length at which the module stops asking for a longer pause. */
    public static final int MAX_COMBO = 5;

    /** What the player should do right now. */
    public enum Advice {
        /** No attack yet, or combat has lapsed — hit freely. */
        READY,
        /** Inside the window: a click now would be discarded. */
        WAIT,
        /** The window has closed; the next click lands and continues the combo. */
        HIT
    }

    /** Immutable result of one evaluation. */
    public static final class Decision {
        public final Advice advice;
        /** Milliseconds left before a hit can land; 0 when {@link #advice} is HIT or READY. */
        public final long remainingMs;
        /** Consecutive landed hits in the current streak. */
        public final int combo;
        /** 0..1 fraction of the cooldown already elapsed. */
        public final float progress;

        Decision(Advice advice, long remainingMs, int combo, float progress) {
            this.advice = advice;
            this.remainingMs = remainingMs;
            this.combo = combo;
            this.progress = progress;
        }

        /**
         * True when a click right now will land.
         *
         * <p>{@link Advice#READY} is <em>not</em> green: nothing has been swung yet in this
         * engagement, so claiming "hit now" would be an unearned instruction. The indicator
         * stays in its neutral state until the first attack, then tracks the real window.
         */
        public boolean isGreen() {
            return advice == Advice.HIT;
        }

        /** True when the indicator has no live window to report. */
        public boolean isIdle() {
            return advice == Advice.READY;
        }
    }

    private long cooldownMs = DEFAULT_COOLDOWN_MS;
    private long lastAttackAt = Long.MIN_VALUE;
    private int combo;

    public void setCooldownMs(int ms) {
        cooldownMs = Math.max(50L, Math.min(2000L, ms));
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void reset() {
        lastAttackAt = Long.MIN_VALUE;
        combo = 0;
    }

    /**
     * Records that the player attacked at {@code nowMs}.
     *
     * <p>A click inside the window is not a landed hit, so it must not extend the window or
     * advance the combo — recording it as one would make the indicator lie about when the next
     * hit can land, which is the exact thing this module exists to get right.
     */
    public void onAttack(long nowMs) {
        if (lastAttackAt == Long.MIN_VALUE) {
            lastAttackAt = nowMs;
            combo = 1;
            return;
        }
        long elapsed = nowMs - lastAttackAt;
        if (elapsed < 0) {
            // Clock went backwards (or a stale event); treat as a fresh engagement.
            lastAttackAt = nowMs;
            combo = 1;
            return;
        }
        if (elapsed > STREAK_RESET_MS) {
            lastAttackAt = nowMs;
            combo = 1;
            return;
        }
        if (elapsed >= cooldownMs) {
            // A landed hit: the window was respected.
            lastAttackAt = nowMs;
            combo = Math.min(MAX_COMBO, combo + 1);
            return;
        }
        // Discarded click: leave the window and the streak exactly where they were.
    }

    /** Evaluates the advice for {@code nowMs} without changing any state. */
    public Decision evaluate(long nowMs) {
        if (lastAttackAt == Long.MIN_VALUE) {
            return new Decision(Advice.READY, 0L, 0, 1f);
        }
        long elapsed = nowMs - lastAttackAt;
        if (elapsed < 0 || elapsed > STREAK_RESET_MS) {
            return new Decision(Advice.READY, 0L, 0, 1f);
        }
        if (elapsed >= cooldownMs) {
            return new Decision(Advice.HIT, 0L, combo, 1f);
        }
        long remaining = cooldownMs - elapsed;
        return new Decision(Advice.WAIT, remaining, combo, elapsed / (float) cooldownMs);
    }

    public int getCombo() {
        return combo;
    }
}
