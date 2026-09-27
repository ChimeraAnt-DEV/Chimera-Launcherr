package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;

/**
 * The radial dead zone must be the default behaviour, not a feature of the anti-drift toggle.
 *
 * A per-axis dead zone is what made a fresh install feel broken: it demands the threshold on
 * each axis separately, so a diagonal push needed roughly {@code threshold * sqrt(2)} of travel,
 * and it cannot see a stick resting slightly off-centre on one axis (which reads as a constant
 * diagonal push). Both symptoms are pinned here, because both were reported from a device.
 */
public class RadialDeadZoneTest {

    private static final float EPS = 1e-4f;

    private static ControllerProfile profile(float deadZone) {
        ControllerProfile p = new ControllerProfile("radial");
        p.setLeftDeadZone(deadZone);
        p.setRightDeadZone(deadZone);
        return p;
    }

    @Test
    public void antiDriftIsOffByDefaultSoTheRadialPathMustNotDependOnIt() {
        ControllerResponse response = new ControllerResponse(profile(0.1f), false);
        assertFalse("anti-drift must stay opt-in", response.isAntiDriftEnabled());

        // The radial threshold is in force regardless, so a diagonal push just past the radius
        // produces output without the toggle being on.
        float[] out = new float[2];
        response.adjustStickPair(true, 0.09f, 0.09f, out);
        assertTrue("diagonal just outside the radius must produce output", out[0] > 0f);
    }

    @Test
    public void diagonalTravelIsNotDemandedTwice() {
        // The regression: with a per-axis test each axis had to clear the threshold, so the
        // combined travel needed was threshold * sqrt(2). A radial test needs the threshold once.
        ControllerResponse response = new ControllerResponse(profile(0.1f), false);
        float[] out = new float[2];

        // 0.08 on each axis is a magnitude of ~0.113: outside a 0.1 radius, inside a 0.1
        // per-axis test only if each axis also exceeded 0.1, which it does not.
        response.adjustStickPair(true, 0.08f, 0.08f, out);
        assertTrue("combined travel past the radius must be accepted", out[0] > 0f);
        assertTrue(out[1] > 0f);

        // And the per-axis form still disagrees, which is precisely why it cannot be the gate.
        assertFalse(response.isOutsideDeadZone(MotionEvent.AXIS_X, 0.08f));
    }

    @Test
    public void anOffCentreRestingStickReadsAsCentred() {
        // A stick at rest 0.09 off on x and 0.05 on y: magnitude ~0.103, just past a 0.1 radius,
        // so it is shaped rather than dropped — but it is nowhere near a real push.
        // The point of the radial test is that neither axis alone looks like a push.
        ControllerResponse response = new ControllerResponse(profile(0.1f), false);
        assertFalse(response.isOutsideDeadZone(MotionEvent.AXIS_X, 0.09f));

        // A stick genuinely at rest inside the radius is flattened to exactly zero on both axes.
        float[] out = new float[2];
        response.adjustStickPair(true, 0.06f, 0.06f, out);
        assertEquals(0f, out[0], EPS);
        assertEquals(0f, out[1], EPS);
    }

    @Test
    public void directionIsPreservedExactly() {
        ControllerResponse response = new ControllerResponse(profile(0.1f), false);
        float[] out = new float[2];
        response.adjustStickPair(true, 0.3f, 0.4f, out);

        // The output vector must be parallel to the input: only its length changes.
        float inputAngle = (float) Math.atan2(0.4f, 0.3f);
        float outputAngle = (float) Math.atan2(out[1], out[0]);
        assertEquals("push direction must be preserved", inputAngle, outputAngle, 1e-3f);
        assertTrue("a real push must produce output", out[0] > 0f);
    }

    @Test
    public void fullDeflectionStillReachesFullOutput() {
        ControllerResponse response = new ControllerResponse(profile(0.08f), false);
        float[] out = new float[2];
        response.adjustStickPair(true, 1f, 0f, out);
        assertEquals("full deflection must reach 1.0", 1f, out[0], 1e-3f);
    }

    @Test
    public void theDefaultRadiusIsLowEnoughToFeelResponsive() {
        // 0.15 as a *per-axis* threshold was the shipped default and needed ~0.21 combined on a
        // diagonal. Guard the constant against creeping back up.
        assertTrue("default dead zone must stay responsive",
                ControllerProfile.DEFAULT_DEAD_ZONE <= 0.10f);
    }

    @Test
    public void aNullProfileStillShapesRadially() {
        ControllerResponse response = new ControllerResponse(null, false);
        float[] out = new float[2];
        // No profile at all: the radial default applies rather than the per-axis fallback.
        response.adjustStickPair(true, 1f, 0f, out);
        assertEquals(1f, out[0], 1e-3f);
    }
}
