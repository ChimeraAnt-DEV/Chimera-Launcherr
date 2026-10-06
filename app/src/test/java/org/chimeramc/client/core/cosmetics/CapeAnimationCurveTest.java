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
    public void theFlapTermLeansTheCapeEvenWithoutSpeed() {
        // query.cape_flap_amount is the signal vanilla computes for a real cape. It must move the
        // cloth on its own, so a player with a vanilla/Persona cape sees motion even if
        // modified_move_speed is small.
        double resting = CapeAnimationCurve.leanDegrees(0.0, false, 0.0, 0.0, 0.0);
        double flapping = CapeAnimationCurve.leanDegrees(0.0, false, 0.0, 0.0, 1.0);
        assertEquals("no flap at rest", 0.0, resting, 1e-9);
        assertTrue("a full flap must lean the cape back", flapping < 0.0);
        assertTrue("a full flap must be a visible rotation",
                Math.abs(flapping) >= 20.0);
    }

    @Test
    public void realisticWalkAndRunSpeedsProduceVisibleMotion() {
        // modified_move_speed is game-scaled and small at a walk; the lean must still be a clearly
        // visible rotation there, not a fraction of a degree. These are the values the official
        // sheep walk controller blends over (query.modified_move_speed 0.0-1.0).
        double walk = CapeAnimationCurve.leanDegrees(0.3, false, 0.0, 0.0);
        double run = CapeAnimationCurve.leanDegrees(0.8, false, 0.0, 0.0);
        assertTrue("a walk must visibly move the cape (>= 5 deg)", Math.abs(walk) >= 5.0);
        assertTrue("a run must visibly move the cape (>= 15 deg)", Math.abs(run) >= 15.0);
        assertTrue("a run must lean further than a walk", run < walk);
    }

    @Test
    public void theFlapAndSpeedTermsCombineRatherThanReplaceEachOther() {
        double both = CapeAnimationCurve.leanDegrees(0.5, false, 0.0, 0.0, 0.5);
        double speedOnly = CapeAnimationCurve.leanDegrees(0.5, false, 0.0, 0.0, 0.0);
        double flapOnly = CapeAnimationCurve.leanDegrees(0.0, false, 0.0, 0.0, 0.5);
        assertTrue("flap adds to speed", both < speedOnly);
        assertTrue("speed adds to flap", both < flapOnly);
    }

    @Test
    public void theExpressionsAreBuiltFromTheSameConstantsAsTheJava() {
        // A literal drifting between the JSON and the Java is how the cape animates in one place
        // and not the other, so every constant the Java uses must appear in the expression.
        String lean = CapeAnimationCurve.leanExpression();
        assertTrue("speed query", lean.contains("query.modified_move_speed"));
        assertTrue("cape flap query", lean.contains("query.cape_flap_amount"));
        assertTrue("flap lean", lean.contains("42.0"));
        assertTrue("jump query", lean.contains("query.is_jumping"));
        assertTrue("vertical query", lean.contains("query.vertical_speed"));
        assertTrue("distance query", lean.contains("query.modified_distance_moved"));
        assertTrue("walk lean", lean.contains("28.0"));
        assertTrue("jump flare", lean.contains("12.0"));
        assertTrue("vertical lean", lean.contains("10.0"));
        assertTrue("flutter", lean.contains("13.0"));
        assertTrue("flutter frequency", lean.contains("60.0"));
        assertTrue("speed is clamped", lean.contains("math.clamp(query.modified_move_speed"));

        String sway = CapeAnimationCurve.swayExpression();
        assertTrue("sway amplitude", sway.contains("10.0"));
        assertTrue("sway frequency", sway.contains("44.0"));
        assertTrue("sway distance query", sway.contains("query.modified_distance_moved"));
    }

    @Test
    public void segmentSharesRampFromShouldersToHemAndSumToOne() {
        int total = CapeGeometry.SEGMENT_COUNT;
        double sum = 0.0;
        for (int i = 1; i <= total; i++) {
            sum += CapeAnimationCurve.segmentShare(i, total);
        }
        // The sum is what keeps the total bend equal to the single-bone lean; a non-unit sum would
        // make the segmented cape lean further than the one it replaces.
        assertEquals(1.0, sum, 1e-9);
        // The hem carries the most and the shoulders the least, which is what reads as a fold.
        assertTrue("hem carries more than shoulders",
                CapeAnimationCurve.segmentShare(total, total)
                        > CapeAnimationCurve.segmentShare(1, total));
        assertTrue("every segment carries some bend",
                CapeAnimationCurve.segmentShare(1, total) > 0.0);
    }

    @Test
    public void aStandingStillChainIsAtRest() {
        int total = CapeGeometry.SEGMENT_COUNT;
        for (int i = 1; i <= total; i++) {
            assertEquals("segment " + i + " lean at rest", 0.0,
                    CapeAnimationCurve.segmentLeanDegrees(i, total, 0.0, false, 0.0, 0.0), 1e-9);
            assertEquals("segment " + i + " sway at rest", 0.0,
                    CapeAnimationCurve.segmentSwayDegrees(i, total, 0.0, 0.0, 0.0), 1e-9);
        }
    }

    @Test
    public void theChainFoldsRatherThanSnappingAsOneRigidBody() {
        // A single rigid bone rotates every part of the cloth by the same amount at the same
        // instant. The phase lag must make the segments differ, or the cape is still a plank.
        int total = CapeGeometry.SEGMENT_COUNT;
        boolean anyDifference = false;
        for (int i = 2; i <= total; i++) {
            double above = CapeAnimationCurve.segmentLeanDegrees(i - 1, total, 1.0, false, 0.0, 0.5);
            double here = CapeAnimationCurve.segmentLeanDegrees(i, total, 1.0, false, 0.0, 0.5);
            if (Math.abs(above - here) > 1e-6) {
                anyDifference = true;
                break;
            }
        }
        assertTrue("segments must not move in lockstep", anyDifference);
    }

    @Test
    public void turningRipplesTheClothButTheYawTermStaysBounded() {
        // The body yaw wraps, so the turn term is fed through a sine; it must never grow without
        // bound as the player spins.
        for (double yaw = -10000.0; yaw <= 10000.0; yaw += 37.0) {
            double term = CapeAnimationCurve.turnSwayDegrees(1.0, yaw);
            assertTrue("turn term bounded", Math.abs(term)
                    <= CapeAnimationCurve.TURN_SWAY_DEG + 1e-9);
        }
        assertTrue("a quarter turn must move the cloth",
                Math.abs(CapeAnimationCurve.turnSwayDegrees(1.0, 0.0)
                        - CapeAnimationCurve.turnSwayDegrees(1.0, 90.0)) > 1e-6);
        assertEquals("turning in place with no speed must not sway", 0.0,
                CapeAnimationCurve.turnSwayDegrees(0.0, 90.0), 1e-9);
    }

    @Test
    public void theSegmentExpressionsBakeInTheShareAndThePhaseLag() {
        int total = CapeGeometry.SEGMENT_COUNT;
        String first = CapeAnimationCurve.segmentLeanExpression(1, total);
        String second = CapeAnimationCurve.segmentLeanExpression(2, total);
        // The first segment's phase offset is zero, so its distance query is the bare one.
        assertTrue("first segment uses the bare distance query",
                first.contains("query.modified_distance_moved")
                        && !first.contains("query.modified_distance_moved -"));
        // The second subtracts one segment's worth of lag inside the sine.
        assertTrue("second segment lags by the phase step",
                second.contains("query.modified_distance_moved - 0.022"));
        // The share literal is present in both.
        assertTrue("first share literal", first.contains("0.007352941176470588")
                || first.contains(String.valueOf(CapeAnimationCurve.segmentShare(1, total))));
        assertTrue("sway uses the bounded body yaw",
                CapeAnimationCurve.segmentSwayExpression(3, total).contains("query.body_y_rotation"));
        assertTrue("turn sway constant is present",
                CapeAnimationCurve.segmentSwayExpression(3, total).contains("4.0"));
    }
}
