package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the head-look transform that keeps a worn hat on a turning head: the rotation happens about
 * the neck pivot, pitch and yaw are both applied, and the idle look stays bounded so the character
 * glances rather than swivels.
 */
public class HeadLookTransformTest {

    private static final float EPS = 0.001f;

    @Test
    public void thePivotIsTheNeck() {
        // The vanilla head bone's pivot; the accessory's acc bone uses the same point.
        assertEquals(0f, HeadLookTransform.PIVOT_X, EPS);
        assertEquals(24f, HeadLookTransform.PIVOT_Y, EPS);
        assertEquals(0f, HeadLookTransform.PIVOT_Z, EPS);
    }

    @Test
    public void anUnrotatedPointIsUnchanged() {
        float[] p = {3f, 28f, -1f};
        HeadLookTransform.apply(0f, 0f, p);
        assertEquals(3f, p[0], EPS);
        assertEquals(28f, p[1], EPS);
        assertEquals(-1f, p[2], EPS);
    }

    @Test
    public void thePivotItselfNeverMoves() {
        float[] p = {HeadLookTransform.PIVOT_X, HeadLookTransform.PIVOT_Y, HeadLookTransform.PIVOT_Z};
        HeadLookTransform.apply(35f, 120f, p);
        assertEquals(0f, p[0], EPS);
        assertEquals(24f, p[1], EPS);
        assertEquals(0f, p[2], EPS);
    }

    @Test
    public void yawTurnsThePointAboutTheNeck() {
        // A point above and forward of the neck, yawed 90 degrees, swings to the side while keeping
        // its height — the signature of a rotation about the vertical axis through the pivot.
        float[] p = {0f, 32f, 4f};
        HeadLookTransform.apply(0f, 90f, p);
        assertEquals(32f, p[1], EPS);
        assertEquals(4f, p[0], 0.01f);
        assertEquals(0f, p[2], 0.01f);
    }

    @Test
    public void pitchTipsThePointForward() {
        // A point above the neck, pitched forward, moves along Z (its height drops as it leans).
        float[] p = {0f, 32f, 0f};
        HeadLookTransform.apply(45f, 0f, p);
        assertEquals(0f, p[0], EPS);
        assertTrue("pitched forward should move the point in Z", Math.abs(p[2]) > 1f);
        assertTrue("a forward lean drops the point", p[1] < 32f);
    }

    @Test
    public void theIdleLookIsBounded() {
        // Sweep a full period; neither pitch nor yaw may exceed a gentle head tilt.
        float[] out = new float[2];
        for (int ms = 0; ms < 40000; ms += 50) {
            HeadLookTransform.idleLook(ms, out);
            assertTrue(Math.abs(out[0]) <= 6.01f);
            assertTrue(Math.abs(out[1]) <= 14.01f);
        }
    }

    @Test
    public void theIdleLookActuallyMoves() {
        float[] a = new float[2];
        float[] b = new float[2];
        HeadLookTransform.idleLook(0, a);
        HeadLookTransform.idleLook(1500, b);
        assertTrue(Math.abs(a[1] - b[1]) > 0.5f);
    }

    @Test
    public void applyVectorRotatesWithoutTranslating() {
        // A forward vector yawed 90 degrees points to the side, and stays a unit vector (no pivot
        // translation leaked in).
        float[] v = {0f, 0f, 1f};
        HeadLookTransform.applyVector(0f, 90f, v);
        assertEquals(1f, v[0], 0.01f);
        assertEquals(0f, v[1], EPS);
        assertEquals(0f, v[2], 0.01f);
    }

    @Test
    public void aVectorAndAPointAboveThePivotRotateTheSameWay() {
        // The vector form must agree with the point form for a point offset from the pivot, or a
        // rotated face's normal would disagree with its rotated corners.
        float[] point = {0f, 32f, 0f};
        HeadLookTransform.apply(20f, 40f, point);
        float[] vector = {0f, 8f, 0f};
        HeadLookTransform.applyVector(20f, 40f, vector);
        assertEquals(vector[0], point[0], 0.01f);
        assertEquals(vector[1] + HeadLookTransform.PIVOT_Y, point[1], 0.01f);
        assertEquals(vector[2], point[2], 0.01f);
    }
}
