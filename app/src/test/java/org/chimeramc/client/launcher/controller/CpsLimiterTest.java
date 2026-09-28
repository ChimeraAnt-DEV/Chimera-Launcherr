package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the two per-button rate behaviours.
 *
 * They are separate on purpose: the limit drops over-rate presses (safe, stops double-fire) while
 * repeat injects downs at a fixed rate (opt-in, autoclicker-like). A test here guards the
 * boundary between them so a future change cannot turn a limit into a hidden autoclicker.
 */
public class CpsLimiterTest {

    @Test
    public void limitOfZeroAlwaysAllows() {
        CpsLimiter limiter = new CpsLimiter();
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.allowPress(96, 0, i));
        }
    }

    @Test
    public void pressesAboveTheCapAreDropped() {
        CpsLimiter limiter = new CpsLimiter();
        int allowed = 0;
        // All within the same one-second window.
        for (int i = 0; i < 20; i++) {
            if (limiter.allowPress(96, 5, i * 10L)) allowed++;
        }
        assertEquals(5, allowed);
    }

    @Test
    public void windowResetsAfterOneSecond() {
        CpsLimiter limiter = new CpsLimiter();
        for (int i = 0; i < 5; i++) assertTrue(limiter.allowPress(96, 5, i));
        assertFalse(limiter.allowPress(96, 5, 500L));
        // A new window opens once a full second has elapsed since the window start.
        assertTrue(limiter.allowPress(96, 5, 1000L));
    }

    @Test
    public void windowCountsFromTheStartNotTheLastPress() {
        CpsLimiter limiter = new CpsLimiter();
        assertTrue(limiter.allowPress(96, 2, 0L));
        assertTrue(limiter.allowPress(96, 2, 900L));
        assertFalse(limiter.allowPress(96, 2, 950L));
        // Counting from the window start, the window rolls at 1000ms regardless of the idle gap.
        assertTrue(limiter.allowPress(96, 2, 1000L));
    }

    @Test
    public void rateIsClampedToTheMaximum() {
        CpsLimiter limiter = new CpsLimiter();
        int allowed = 0;
        for (int i = 0; i < 100; i++) {
            if (limiter.allowPress(96, 1000, i)) allowed++;
        }
        assertEquals(CpsLimiter.MAX_CPS, allowed);
    }

    @Test
    public void buttonsAreCountedIndependently() {
        CpsLimiter limiter = new CpsLimiter();
        assertTrue(limiter.allowPress(96, 1, 0L));
        assertFalse(limiter.allowPress(96, 1, 1L));
        // A different button has its own window.
        assertTrue(limiter.allowPress(97, 1, 1L));
    }

    @Test
    public void holdRepeatIsOffByDefault() {
        CpsLimiter limiter = new CpsLimiter();
        assertFalse(limiter.beginHold(96, 0, 0L));
        assertFalse(limiter.pollRepeat(96, 0, 1000L));
    }

    @Test
    public void holdRepeatsAtTheConfiguredRate() {
        CpsLimiter limiter = new CpsLimiter();
        assertTrue(limiter.beginHold(96, 10, 0L));
        // The first repeat is one interval out, not immediately, so the initial press is not
        // doubled.
        assertFalse(limiter.pollRepeat(96, 10, 0L));
        assertFalse(limiter.pollRepeat(96, 10, 50L));
        assertTrue(limiter.pollRepeat(96, 10, 100L));
        assertFalse(limiter.pollRepeat(96, 10, 150L));
        assertTrue(limiter.pollRepeat(96, 10, 200L));
    }

    @Test
    public void aLateTickDoesNotBurstRepeats() {
        CpsLimiter limiter = new CpsLimiter();
        limiter.beginHold(96, 10, 0L);
        // A very late frame should fire once and re-align, not fire the whole backlog.
        assertTrue(limiter.pollRepeat(96, 10, 950L));
        assertFalse(limiter.pollRepeat(96, 10, 960L));
    }

    @Test
    public void endHoldStopsRepeats() {
        CpsLimiter limiter = new CpsLimiter();
        limiter.beginHold(96, 10, 0L);
        limiter.endHold(96);
        assertFalse(limiter.pollRepeat(96, 10, 500L));
    }

    @Test
    public void resetClearsEveryButton() {
        CpsLimiter limiter = new CpsLimiter();
        limiter.beginHold(96, 10, 0L);
        limiter.allowPress(97, 1, 0L);
        limiter.reset();
        assertFalse(limiter.pollRepeat(96, 10, 500L));
        assertTrue(limiter.allowPress(97, 1, 1L));
    }

    @Test
    public void intervalIsTheReciprocalOfTheRate() {
        assertEquals(100L, CpsLimiter.intervalMs(10));
        assertEquals(50L, CpsLimiter.intervalMs(20));
        assertEquals(0L, CpsLimiter.intervalMs(0));
        // Never zero for an on rate, so a division by interval cannot happen.
        assertTrue(CpsLimiter.intervalMs(30) > 0);
    }

    @Test
    public void attackButtonsAreFlaggedAsAutoclickerLike() {
        assertTrue(CpsLimiter.isAutoclickerLike(android.view.KeyEvent.KEYCODE_BUTTON_R2));
        assertTrue(CpsLimiter.isAutoclickerLike(android.view.KeyEvent.KEYCODE_BUTTON_R1));
        assertFalse(CpsLimiter.isAutoclickerLike(android.view.KeyEvent.KEYCODE_BUTTON_A));
    }

    @Test
    public void profileRoundTripsLimitsAndRates() {
        ControllerProfile profile = new ControllerProfile("test");
        profile.setClickLimit(96, 12);
        profile.setRepeatRate(97, 8);
        ControllerProfile copy = profile.copy();
        assertEquals(12, copy.getClickLimit(96));
        assertEquals(8, copy.getRepeatRate(97));
        // Clearing removes the entry rather than storing a zero.
        copy.setClickLimit(96, 0);
        assertEquals(CpsLimiter.OFF, copy.getClickLimit(96));
    }
}
