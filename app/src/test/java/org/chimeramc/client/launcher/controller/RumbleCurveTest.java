package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The rumble-strength mapping. Pure, so no device or vibrator is involved. */
public class RumbleCurveTest {

    @Test
    public void zeroStrengthIsOffRatherThanMinimumVibration() {
        assertTrue(RumbleCurve.isOff(0));
        assertEquals(0, RumbleCurve.amplitude(0));
    }

    @Test
    public void fullStrengthReachesThePlatformMaximum() {
        assertEquals(RumbleCurve.MAX_AMPLITUDE, RumbleCurve.amplitude(100));
    }

    @Test
    public void onePercentIsStillPerceptible() {
        // A naive strength/100*255 rounds 1% to 3, but the low end must not collapse to a value a
        // vibrator cannot render distinctly.
        int amplitude = RumbleCurve.amplitude(1);
        assertTrue("1% should produce a usable amplitude, got " + amplitude,
                amplitude >= RumbleCurve.MIN_AMPLITUDE);
        assertTrue(amplitude < RumbleCurve.amplitude(100));
    }

    @Test
    public void outOfRangeStrengthIsClampedNotRejected() {
        assertEquals(0, RumbleCurve.amplitude(-40));
        assertEquals(RumbleCurve.MAX_AMPLITUDE, RumbleCurve.amplitude(500));
        assertEquals(0, RumbleCurve.clampStrength(-1));
        assertEquals(100, RumbleCurve.clampStrength(101));
    }

    @Test
    public void amplitudeGrowsWithStrength() {
        int previous = -1;
        for (int strength = 0; strength <= 100; strength += 10) {
            int amplitude = RumbleCurve.amplitude(strength);
            assertTrue("amplitude must not fall as strength rises", amplitude >= previous);
            previous = amplitude;
        }
    }

    @Test
    public void isOffOnlyForZeroOrBelow() {
        assertFalse(RumbleCurve.isOff(1));
        assertFalse(RumbleCurve.isOff(100));
        assertTrue(RumbleCurve.isOff(-5));
    }
}
