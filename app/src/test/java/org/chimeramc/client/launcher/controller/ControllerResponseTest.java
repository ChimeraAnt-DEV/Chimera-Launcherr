package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import android.view.MotionEvent;

import org.junit.Test;

/**
 * Covers the controller response math that runs on every controller event.
 *
 * These tests exercise the real {@link ControllerResponse}/{@link ControllerProfile} code
 * paths with no mocks. Android's MotionEvent/KeyEvent constants are static final ints
 * resolved at compile time, so no Android runtime is needed for the axis/remap logic.
 */
public class ControllerResponseTest {

    private static final float EPS = 0.0005f;

    private static ControllerProfile profile(float deadZone, float sensitivity) {
        ControllerProfile profile = new ControllerProfile("test");
        profile.setLeftDeadZone(deadZone);
        profile.setRightDeadZone(deadZone);
        profile.setLeftStickSensitivity(sensitivity);
        profile.setRightStickSensitivity(sensitivity);
        return profile;
    }

    @Test
    public void centreStickProducesZero() {
        ControllerResponse response = new ControllerResponse(profile(0.15f, 1f), false);
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X, 0f), EPS);
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_Y, 0f), EPS);
    }

    @Test
    public void insideDeadZoneIsFlattenedToZero() {
        ControllerResponse response = new ControllerResponse(profile(0.15f, 1f), false);
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X, 0.1f), EPS);
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X, -0.1f), EPS);
        // Exactly at the edge counts as inside.
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X, 0.15f), EPS);
        assertFalse(response.isOutsideDeadZone(MotionEvent.AXIS_X, 0.15f));
    }

    @Test
    public void fullDeflectionReachesFullOutput() {
        ControllerResponse response = new ControllerResponse(profile(0.15f, 1f), false);
        assertEquals(1f, response.adjustAxis(MotionEvent.AXIS_X, 1f), EPS);
        assertEquals(-1f, response.adjustAxis(MotionEvent.AXIS_X, -1f), EPS);
    }

    @Test
    public void signIsPreserved() {
        ControllerResponse response = new ControllerResponse(profile(0.15f, 1f), false);
        assertTrue(response.adjustAxis(MotionEvent.AXIS_X, 0.5f) > 0f);
        assertTrue(response.adjustAxis(MotionEvent.AXIS_X, -0.5f) < 0f);
        assertTrue(response.adjustAxis(MotionEvent.AXIS_Z, -0.8f) < 0f);
    }

    @Test
    public void outputIsMonotonicAcrossTheWholeRange() {
        ControllerResponse response = new ControllerResponse(profile(0.15f, 1f), false);
        float previous = -1f;
        for (float value = 0f; value <= 1.0001f; value += 0.01f) {
            float output = response.adjustAxis(MotionEvent.AXIS_X, value);
            assertTrue("output regressed at " + value, output >= previous - EPS);
            previous = output;
        }
    }

    @Test
    public void snappyCurveGivesMoreOutputThanLinearAndStaysBounded() {
        ControllerProfile profile = profile(0.06f, 1f);
        ControllerResponse linear = new ControllerResponse(profile, false);
        ControllerResponse snappy = new ControllerResponse(profile, true);

        // Past the dead zone the boosted curve must lead, otherwise it does nothing useful.
        float midValue = 0.4f;
        assertTrue(snappy.adjustAxis(MotionEvent.AXIS_X, midValue)
                > linear.adjustAxis(MotionEvent.AXIS_X, midValue));

        // It must still be a valid, non-saturating axis value everywhere.
        for (float value = 0f; value <= 1.0001f; value += 0.005f) {
            float output = snappy.adjustAxis(MotionEvent.AXIS_X, value);
            assertTrue("output out of range at " + value, output >= 0f && output <= 1f);
        }
        assertEquals(1f, snappy.adjustAxis(MotionEvent.AXIS_X, 1f), EPS);
    }

    @Test
    public void snappyOutputIsStillMonotonic() {
        ControllerResponse snappy = new ControllerResponse(profile(0.06f, 1f), true);
        float previous = -1f;
        for (float value = 0f; value <= 1.0001f; value += 0.01f) {
            float output = snappy.adjustAxis(MotionEvent.AXIS_X, value);
            assertTrue("output regressed at " + value, output >= previous - EPS);
            previous = output;
        }
    }

    @Test
    public void sensitivityScalesOutputAndClampsAtFull() {
        ControllerResponse low = new ControllerResponse(profile(0.1f, 0.5f), false);
        ControllerResponse high = new ControllerResponse(profile(0.1f, 2f), false);
        assertTrue(high.adjustAxis(MotionEvent.AXIS_X, 0.5f) > low.adjustAxis(MotionEvent.AXIS_X, 0.5f));
        // High sensitivity must not overshoot the axis range.
        for (float value = 0f; value <= 1.0001f; value += 0.01f) {
            assertTrue(high.adjustAxis(MotionEvent.AXIS_X, value) <= 1f);
        }
    }

    @Test
    public void nonStickAxesPassThroughUntouched() {
        ControllerResponse response = new ControllerResponse(profile(0.5f, 3f), true);
        // Trigger axes must not be dead-zoned or scaled; that would break analogue triggers.
        assertEquals(0.42f, response.adjustAxis(MotionEvent.AXIS_LTRIGGER, 0.42f), EPS);
        assertEquals(0.9f, response.adjustAxis(MotionEvent.AXIS_RTRIGGER, 0.9f), EPS);
        assertEquals(0.5f, response.adjustAxis(MotionEvent.AXIS_HAT_X, 0.5f), EPS);
    }

    @Test
    public void rightStickUsesRightStickDeadZone() {
        ControllerProfile profile = new ControllerProfile("split");
        profile.setLeftDeadZone(0.1f);
        profile.setRightDeadZone(0.5f);
        ControllerResponse response = new ControllerResponse(profile, false);

        // Considered outside the tight left dead zone...
        assertTrue(response.isOutsideDeadZone(MotionEvent.AXIS_X, 0.3f));
        // ...but inside the wide right dead zone.
        assertFalse(response.isOutsideDeadZone(MotionEvent.AXIS_Z, 0.3f));
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_RZ, 0.3f), EPS);
    }

    @Test
    public void extremeDeadZoneStillProducesAValidRange() {
        // A dead zone of 0.99 leaves a tiny but non-zero usable range; the divisor must not
        // blow up into NaN/Infinity.
        ControllerResponse response = new ControllerResponse(profile(0.99f, 1f), false);
        float output = response.adjustAxis(MotionEvent.AXIS_X, 1f);
        assertFalse(Float.isNaN(output));
        assertFalse(Float.isInfinite(output));
        assertEquals(1f, output, EPS);
    }

    @Test
    public void remapTableReturnsConfiguredTarget() {
        ControllerProfile profile = new ControllerProfile("remap");
        profile.setRemap(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B);
        ControllerResponse response = new ControllerResponse(profile, false);
        assertEquals(KeyEvent.KEYCODE_BUTTON_B,
                response.remapKey(KeyEvent.KEYCODE_BUTTON_A));
    }

    @Test
    public void unmappedKeysAndNonPositiveCodesAreIdentity() {
        ControllerProfile profile = new ControllerProfile("remap");
        profile.setRemap(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B);
        ControllerResponse response = new ControllerResponse(profile, false);

        assertEquals(KeyEvent.KEYCODE_BUTTON_X, response.remapKey(KeyEvent.KEYCODE_BUTTON_X));
        // The previous implementation returned these untouched; keeping that avoids
        // swallowing unknown keys.
        assertEquals(0, response.remapKey(0));
        assertEquals(-1, response.remapKey(-1));
    }

    @Test
    public void remapToAKeyOutsideTheTableStillApplies() {
        ControllerProfile profile = new ControllerProfile("wide");
        int highKey = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        profile.setRemap(KeyEvent.KEYCODE_BUTTON_A, highKey);
        ControllerResponse response = new ControllerResponse(profile, false);
        assertEquals(highKey, response.remapKey(KeyEvent.KEYCODE_BUTTON_A));
    }

    @Test
    public void emptyProfileIsIdentityAndUntouched() {
        ControllerResponse response = new ControllerResponse(new ControllerProfile("empty"), false);
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, response.remapKey(KeyEvent.KEYCODE_BUTTON_A));
        // The default dead zone still applies. Referenced through the constant so lowering it
        // does not silently turn this into an assertion about nothing.
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X,
                ControllerProfile.DEFAULT_DEAD_ZONE * 0.9f), EPS);
    }

    @Test
    public void nullProfileFallsBackToDefaultsInsteadOfThrowing() {
        ControllerResponse response = new ControllerResponse(null, false);
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, response.remapKey(KeyEvent.KEYCODE_BUTTON_A));
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X,
                ControllerProfile.DEFAULT_DEAD_ZONE * 0.9f), EPS);
        assertEquals(1f, response.adjustAxis(MotionEvent.AXIS_X, 1f), EPS);
    }

    @Test
    public void driftIsSnappedToZeroOnlyNearCentre() {
        ControllerResponse response = new ControllerResponse(profile(0.05f, 1f), false);
        // Just outside the dead zone but below the drift floor: reported as centre so a
        // resting stick cannot slowly drag the camera.
        assertEquals(0f, response.adjustAxis(MotionEvent.AXIS_X, 0.051f), EPS);
        // Clearly intentional movement is unaffected.
        assertTrue(response.adjustAxis(MotionEvent.AXIS_X, 0.3f) > 0.2f);
    }

    @Test
    public void snappyCurveWithExtremeSensitivityStaysClamped() {
        ControllerResponse response = new ControllerResponse(profile(0f, 3f), true);
        for (float value = 0f; value <= 1.0001f; value += 0.01f) {
            float output = response.adjustAxis(MotionEvent.AXIS_X, value);
            assertTrue("output out of range at " + value, output >= 0f && output <= 1f);
        }
    }
}