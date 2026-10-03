package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The cape's cloth motion, as pure maths.
 *
 * <p>These pin the shape the in-game animation JSON is built from: standing still must leave the
 * cape at rest, speed must lean it further back, a jump must flare it, and the flutter must be a
 * travelling wave rather than a pulse. A cape that snaps to full extension at a walk reads as a
 * flag, so the amplitude relationships are the assertions, not just "it moves".
 */
public class CapeAnimationCurveTest {

    @Test
    public void aStandingStillCapeHangsAtRest() {
        assertEquals(0.0, CapeAnimationCurve.leanDegrees(0.0, false, 0.0, 0.0), 1e-9);
        assertEquals(0.0, CapeAnimationCurve.swayDegrees(0.0, 0.0), 1e-9);
    }

    @Test
    public void speedLeansTheCapeBack() {
        // Negative X leans the cloth back, which is what reads as trailing behind the player.
        double walking = CapeAnimationCurve.leanDegrees(0.3, false, 0.0, 0.0);
        double sprinting = CapeAnimationCurve.leanDegrees(1.0, false, 0.0, 0.0);
        assertTrue("a walk must lean back", walking < 0.0);
        assertTrue("a sprint must lean further than a walk", sprinting < walking);
    }

    @Test
    public void jumpingFlaresTheCapeMoreThanWalkingAtTheSameSpeed() {
        double grounded = CapeAnimationCurve.leanDegrees(0.5, false, 0.0, 0.0);
        double airborne = CapeAnimationCurve.leanDegrees(0.5, true, 0.0, 0.0);
        assertTrue("a jump must flare the cape", airborne < grounded);
    }

    @Test
    public void verticalSpeedChangesTheLeanInBothDirections() {
        // A cape trails opposite to motion: rising streams it down/back (more lean), falling lets
        // the hem billow up (less lean). Both must differ from level flight, and from each other.
        double level = CapeAnimationCurve.leanDegrees(0.5, false, 0.0, 0.0);
        double rising = CapeAnimationCurve.leanDegrees(0.5, false, 0.8, 0.0);
        double falling = CapeAnimationCurve.leanDegrees(0.5, false, -0.8, 0.0);
        assertTrue("rising must stream the cape further back", rising < level);
        assertTrue("falling must let the hem billow up", falling > level);
        assertTrue("rising and falling must differ", Math.abs(rising - falling) > 1e-6);
    }

    @Test
    public void theFlutterTravelsWithDistanceMoved() {
        // Two positions the same distance apart must differ, or the fold does not travel.
        double a = CapeAnimationCurve.swayDegrees(1.0, 0.0);
        double b = CapeAnimationCurve.swayDegrees(1.0, 0.1);
        assertTrue("sway must change as the player moves", Math.abs(a - b) > 1e-6);
    }

    @Test
    public void flutterAndSwayStayWithinTheirAmplitudes() {
        for (double d = 0.0; d < 20.0; d += 0.01) {
            double sway = CapeAnimationCurve.swayDegrees(1.0, d);
            assertTrue("sway within amplitude", Math.abs(sway)
                    <= CapeAnimationCurve.SWAY_AMPLITUDE_DEG + 1e-9);
        }
    }

    @Test
    public void extremeAndNonFiniteInputsClampRatherThanExplode() {
        // A long fall and a NaN reading must not produce a NaN rotation the renderer has to draw.
        double fastFall = CapeAnimationCurve.leanDegrees(1.0, false, -1000.0, 0.0);
        assertTrue(Double.isFinite(fastFall));
        assertTrue(fastFall <= 0.0);
        double nan = CapeAnimationCurve.leanDegrees(Double.NaN, false, Double.NaN, 0.0);
        assertTrue(Double.isFinite(nan));
        double nanSway = CapeAnimationCurve.swayDegrees(Double.NaN, 0.0);
        assertTrue(Double.isFinite(nanSway));
    }

    @Test
    public void theExpressionsAreBuiltFromTheSameConstantsAsTheJava() {
        // A literal drifting between the JSON and the Java is how the cape animates in one place
        // and not the other, so every constant the Java uses must appear in the expression.
        String lean = CapeAnimationCurve.leanExpression();
        assertTrue("speed query", lean.contains("query.modified_move_speed"));
        assertTrue("jump query", lean.contains("query.is_jumping"));
        assertTrue("vertical query", lean.contains("query.vertical_speed"));
        assertTrue("distance query", lean.contains("query.modified_distance_moved"));
        assertTrue("walk lean", lean.contains("28.0"));
        assertTrue("jump flare", lean.contains("12.0"));
        assertTrue("vertical lean", lean.contains("10.0"));
        assertTrue("flutter", lean.contains("7.0"));
        assertTrue("flutter frequency", lean.contains("55.0"));
        assertTrue("speed is clamped", lean.contains("math.clamp(query.modified_move_speed"));

        String sway = CapeAnimationCurve.swayExpression();
        assertTrue("sway amplitude", sway.contains("5.0"));
        assertTrue("sway frequency", sway.contains("41.0"));
        assertTrue("sway distance query", sway.contains("query.modified_distance_moved"));
    }
}
