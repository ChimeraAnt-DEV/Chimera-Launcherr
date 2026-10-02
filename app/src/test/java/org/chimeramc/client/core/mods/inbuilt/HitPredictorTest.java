package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitPredictor;
import org.junit.Test;

/**
 * The Hit Prediction geometry. The two rules that matter: the marker is placed from the smoothed
 * velocity (not the last frame), and a target moving too fast produces no marker at all rather
 * than a misleading one.
 */
public class HitPredictorTest {

    @Test
    public void aStationaryTargetPredictsItsOwnPosition() {
        HitPredictor.Prediction p = HitPredictor.predict(
                "p1", new float[]{0f, 0f, 0f}, 3f, 4f, 5f, 1000);
        assertNotNull(p);
        assertEquals(3f, p.x, 0.001f);
        assertEquals(4f, p.y, 0.001f);
        assertEquals(5f, p.z, 0.001f);
        assertEquals(0f, p.leadBlocks, 0.001f);
    }

    @Test
    public void aMovingTargetIsProjectedAlongItsVelocity() {
        // 2 blocks per second for 1 second = 2 blocks of lead.
        HitPredictor.Prediction p = HitPredictor.predict(
                "p1", new float[]{0f, 0f, 2f}, 0f, 0f, 0f, 1000);
        assertNotNull(p);
        assertEquals(2f, p.z, 0.001f);
        assertEquals(2f, p.leadBlocks, 0.001f);
    }

    @Test
    public void theLookAheadScalesTheLead() {
        HitPredictor.Prediction half = HitPredictor.predict(
                "p1", new float[]{0f, 0f, 2f}, 0f, 0f, 0f, 500);
        HitPredictor.Prediction full = HitPredictor.predict(
                "p1", new float[]{0f, 0f, 2f}, 0f, 0f, 0f, 1000);
        assertNotNull(half);
        assertNotNull(full);
        assertEquals(1f, half.z, 0.001f);
        assertEquals(2f, full.z, 0.001f);
    }

    @Test
    public void aFastTargetGetsNoMarker() {
        float fast = HitPredictor.UNRELIABLE_SPEED + 1f;
        assertNull(HitPredictor.predict("p1", new float[]{0f, 0f, fast}, 0f, 0f, 0f, 1000));
    }

    @Test
    public void aTargetAtTheSpeedLimitStillGetsAMarker() {
        assertNotNull(HitPredictor.predict(
                "p1", new float[]{0f, 0f, HitPredictor.UNRELIABLE_SPEED}, 0f, 0f, 0f, 1000));
    }

    @Test
    public void unknownOrNonFiniteVelocityReadsAsNoMarker() {
        assertNull(HitPredictor.predict("p1", null, 0f, 0f, 0f, 1000));
        assertNull(HitPredictor.predict("p1", new float[]{0f, 0f}, 0f, 0f, 0f, 1000));
        assertNull(HitPredictor.predict("p1", new float[]{Float.NaN, 0f, 0f}, 0f, 0f, 0f, 1000));
        assertNull(HitPredictor.predict("p1", new float[]{0f, Float.POSITIVE_INFINITY, 0f},
                0f, 0f, 0f, 1000));
    }

    @Test
    public void lookAheadIsClampedIntoRange() {
        assertEquals(HitPredictor.MIN_LOOK_AHEAD_MS, HitPredictor.clampLookAheadMs(1));
        assertEquals(HitPredictor.MAX_LOOK_AHEAD_MS, HitPredictor.clampLookAheadMs(999999));
        assertEquals(1000, HitPredictor.clampLookAheadMs(1000));
        // A bad pref reads as the default rather than zero.
        assertEquals(HitPredictor.DEFAULT_LOOK_AHEAD_MS, HitPredictor.clampLookAheadMs(0));
        assertEquals(HitPredictor.DEFAULT_LOOK_AHEAD_MS, HitPredictor.clampLookAheadMs(-5));
    }

    @Test
    public void theLeadIsFormattedWithOneDecimal() {
        assertTrue(HitPredictor.formatLead(1.234f).startsWith("1.2"));
    }
}
