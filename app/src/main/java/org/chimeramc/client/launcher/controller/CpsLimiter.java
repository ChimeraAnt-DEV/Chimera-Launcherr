package org.chimeramc.client.launcher.controller;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-button click-rate limiting and hold-to-repeat scheduling.
 *
 * <p>Two separate behaviours, deliberately not merged:
 *
 * <ul>
 *   <li><b>Limit</b> — a cap on how many presses per second a button may deliver. Presses above
 *       the cap are dropped. This is the safe default: it stops a worn or double-firing button
 *       from registering twice for one press, and is not an autoclicker.
 *   <li><b>Repeat</b> — while the button is held, inject down/up at a fixed rate. This is
 *       opt-in per button because on the attack button it is functionally an autoclicker, which
 *       many PvP servers ban.
 * </ul>
 *
 * <p>Pure: it takes the current time as a parameter rather than reading a clock, so the rate
 * rules are unit-testable without a device or a {@code SystemClock} stub. Not thread-safe; it is
 * only ever touched from the input thread.
 */
public final class CpsLimiter {

    /** The largest rate the UI offers, in clicks per second. */
    public static final int MAX_CPS = 30;

    /** A limit/rate of zero means "off". */
    public static final int OFF = 0;

    /** Button-left state for the limiter, keyed by key code. */
    private static final class State {
        /** Rolling one-second window start; -1 until the first press. */
        long windowStartMs = -1L;
        /** Presses counted within the current window. */
        int count;
        /** Next time a held button should repeat; 0 while no hold is active. */
        long nextRepeatMs;
    }

    private final Map<Integer, State> states = new HashMap<>();

    /**
     * Whether a press should be allowed through, given the button's limit.
     *
     * <p>A limit of {@link #OFF} (or anything below 1) always allows. Otherwise presses are
     * counted over a rolling one-second window; the window restarts once a full second has
     * elapsed since its start. Counting from the window start rather than the last press is what
     * keeps the rate stable under a burst rather than letting an idle gap reset the cap.
     */
    public boolean allowPress(int keyCode, int limit, long nowMs) {
        if (limit <= OFF) return true;
        int cap = Math.min(limit, MAX_CPS);
        State state = states.computeIfAbsent(keyCode, k -> new State());
        // -1 is "no window yet"; using 0 would collide with a real timestamp at boot, and the
        // limiter is fed event times which legitimately start near zero.
        if (state.windowStartMs < 0L || nowMs - state.windowStartMs >= 1000L) {
            state.windowStartMs = nowMs;
            state.count = 0;
        }
        if (state.count >= cap) return false;
        state.count++;
        return true;
    }

    /**
     * Starts a hold on a button, if a repeat rate is configured.
     *
     * <p>Returns true when repeating is active. The first repeat is scheduled one interval after
     * the initial press so the initial press is not doubled.
     */
    public boolean beginHold(int keyCode, int repeatCps, long nowMs) {
        if (repeatCps <= OFF) {
            states.remove(keyCode);
            return false;
        }
        int cap = Math.min(repeatCps, MAX_CPS);
        State state = states.computeIfAbsent(keyCode, k -> new State());
        state.nextRepeatMs = nowMs + intervalMs(cap);
        return true;
    }

    /** Ends a hold, so no further repeats are scheduled. */
    public void endHold(int keyCode) {
        State state = states.get(keyCode);
        if (state == null) return;
        state.nextRepeatMs = 0L;
        if (state.count == 0 && state.windowStartMs < 0L) {
            states.remove(keyCode);
        }
    }

    /**
     * Whether a held button is due to repeat, advancing its schedule when it is.
     *
     * <p>Due-time is aligned to the interval rather than to "now", so a late tick does not drift
     * the rate downward over a long hold.
     */
    public boolean pollRepeat(int keyCode, int repeatCps, long nowMs) {
        if (repeatCps <= OFF) return false;
        State state = states.get(keyCode);
        if (state == null || state.nextRepeatMs == 0L) return false;
        if (nowMs < state.nextRepeatMs) return false;
        int cap = Math.min(repeatCps, MAX_CPS);
        long interval = intervalMs(cap);
        // Catch up without firing a burst when a tick was very late.
        long behind = nowMs - state.nextRepeatMs;
        long steps = behind / interval + 1;
        state.nextRepeatMs += steps * interval;
        return true;
    }

    /** Clears all per-button state, e.g. when the active profile changes. */
    public void reset() {
        states.clear();
    }

    /** Milliseconds between repeats for a rate. */
    public static long intervalMs(int cps) {
        if (cps <= OFF) return 0L;
        int clamped = Math.min(cps, MAX_CPS);
        return Math.max(1L, Math.round(1000.0 / clamped));
    }

    /**
     * Whether a button's repeat is likely to be read as an autoclicker by a server.
     *
     * <p>Used only to decide whether to warn; the rule is not enforced, because the launcher
     * cannot know a given server's policy.
     */
    public static boolean isAutoclickerLike(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_BUTTON_R1:
            case android.view.KeyEvent.KEYCODE_BUTTON_R2:
            case android.view.KeyEvent.KEYCODE_BUTTON_1:
            case android.view.KeyEvent.KEYCODE_BUTTON_2:
                return true;
            default:
                return false;
        }
    }
}
