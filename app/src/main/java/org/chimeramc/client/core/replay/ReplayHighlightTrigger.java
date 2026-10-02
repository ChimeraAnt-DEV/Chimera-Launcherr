package org.chimeramc.client.core.replay;

/**
 * The Tier 4 highlight trigger: decides when the last N seconds of play should be saved.
 *
 * <p>Pure and clock-injected, so the "on death / on 3+ kill streak / on combo over N" rules are
 * unit tests rather than something a player has to observe in a match. It holds no ring buffer
 * itself — the recorder owns the rolling buffer and asks this class whether the current event is
 * a trigger.
 *
 * <p>A trigger fires at most once per {@link #COOLDOWN_MS} window: a multi-kill can raise a
 * streak and a combo in the same instant, and saving the same 30 seconds three times would be
 * three near-identical clips.
 */
public final class ReplayHighlightTrigger {

    /** Minimum gap between two saved highlights. */
    public static final long COOLDOWN_MS = 8_000L;

    /** The clip length a highlight captures. */
    public static final int HIGHLIGHT_SECONDS = 30;

    /** The kinds of event a player can ask to auto-capture. */
    public enum Kind {
        DEATH,
        KILL_STREAK,
        COMBO
    }

    /** What a caller reports about an event; the trigger decides if it fires. */
    public static final class Event {
        public final Kind kind;
        /** Streak length for {@link Kind#KILL_STREAK}, hit count for {@link Kind#COMBO}. */
        public final int count;

        public Event(Kind kind, int count) {
            this.kind = kind;
            this.count = count;
        }

        public static Event death() {
            return new Event(Kind.DEATH, 0);
        }

        public static Event killStreak(int streak) {
            return new Event(Kind.KILL_STREAK, streak);
        }

        public static Event combo(int hits) {
            return new Event(Kind.COMBO, hits);
        }
    }

    private final boolean deathEnabled;
    private final boolean killStreakEnabled;
    private final int killStreakThreshold;
    private final boolean comboEnabled;
    private final int comboThreshold;

    private long lastFiredMs = Long.MIN_VALUE;

    public ReplayHighlightTrigger(boolean deathEnabled, boolean killStreakEnabled,
                                  int killStreakThreshold, boolean comboEnabled,
                                  int comboThreshold) {
        this.deathEnabled = deathEnabled;
        this.killStreakEnabled = killStreakEnabled;
        this.killStreakThreshold = Math.max(1, killStreakThreshold);
        this.comboEnabled = comboEnabled;
        this.comboThreshold = Math.max(1, comboThreshold);
    }

    /** True when this event should save a highlight, given the clock. */
    public boolean shouldFire(Event event, long nowMs) {
        if (event == null || !enabledFor(event.kind)) return false;
        if (!thresholdMet(event)) return false;
        if (lastFiredMs != Long.MIN_VALUE && nowMs - lastFiredMs < COOLDOWN_MS) return false;
        lastFiredMs = nowMs;
        return true;
    }

    private boolean enabledFor(Kind kind) {
        switch (kind) {
            case DEATH:
                return deathEnabled;
            case KILL_STREAK:
                return killStreakEnabled;
            case COMBO:
                return comboEnabled;
            default:
                return false;
        }
    }

    private boolean thresholdMet(Event event) {
        switch (event.kind) {
            case DEATH:
                return true;
            case KILL_STREAK:
                return event.count >= killStreakThreshold;
            case COMBO:
                return event.count >= comboThreshold;
            default:
                return false;
        }
    }

    /** Clears the cooldown, e.g. when a new session starts. */
    public void reset() {
        lastFiredMs = Long.MIN_VALUE;
    }
}
