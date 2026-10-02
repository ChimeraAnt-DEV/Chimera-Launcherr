package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.TrajectorySolver;
import org.junit.Test;

import java.util.List;

/**
 * The Trajectory Prediction flight model. Pure arithmetic, so these run on the JVM with no
 * device. The load-bearing rules are: the three projectiles have genuinely different arcs, a
 * full-draw arrow outranges a partial one, and an unknown item predicts nothing.
 */
public class TrajectorySolverTest {

    @Test
    public void itemNamesMapToTheRightProjectile() {
        assertEquals(TrajectorySolver.Projectile.ARROW,
                TrajectorySolver.forItem("minecraft:arrow"));
        assertEquals(TrajectorySolver.Projectile.ARROW,
                TrajectorySolver.forItem("minecraft:tipped_arrow"));
        assertEquals(TrajectorySolver.Projectile.ENDER_PEARL,
                TrajectorySolver.forItem("minecraft:ender_pearl"));
        assertEquals(TrajectorySolver.Projectile.SNOWBALL,
                TrajectorySolver.forItem("minecraft:snowball"));
        assertEquals(TrajectorySolver.Projectile.SNOWBALL,
                TrajectorySolver.forItem("minecraft:egg"));
    }

    @Test
    public void nonProjectilesAndNullPredictNothing() {
        assertNull(TrajectorySolver.forItem(null));
        assertNull(TrajectorySolver.forItem("minecraft:diamond_sword"));
        assertNull(TrajectorySolver.forItem("minecraft:bow"));
        assertTrue(TrajectorySolver.predict(null, 1f, 0f, 0f, 0f, 0f, 0f, 32).isEmpty());
    }

    @Test
    public void aHorizontalShotStartsAtTheOriginAndMovesForward() {
        List<TrajectorySolver.Sample> arc = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ARROW, 1f, 0f, 65f, 0f, 0f, 0f, 32);
        assertFalse(arc.isEmpty());
        TrajectorySolver.Sample first = arc.get(0);
        assertEquals(0f, first.x, 0.001f);
        assertEquals(65f, first.y, 0.001f);
        assertEquals(0f, first.z, 0.001f);

        TrajectorySolver.Sample last = arc.get(arc.size() - 1);
        // Yaw 0 is +Z, so a level shot travels along +Z and only drops.
        assertTrue("moves forward", last.z > 1f);
        assertTrue("gravity pulls it down", last.y < 65f);
    }

    @Test
    public void theArrowDropsFasterThanThePearl() {
        List<TrajectorySolver.Sample> arrow = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ARROW, 1f, 0f, 65f, 0f, 0f, 0f, 20);
        List<TrajectorySolver.Sample> pearl = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ENDER_PEARL, 0f, 0f, 65f, 0f, 0f, 0f, 20);
        float arrowDrop = 65f - arrow.get(arrow.size() - 1).y;
        float pearlDrop = 65f - pearl.get(pearl.size() - 1).y;
        assertTrue("arrow must drop more than the pearl", arrowDrop > pearlDrop);
    }

    @Test
    public void aFullDrawArrowFliesFartherThanAPartialOne() {
        List<TrajectorySolver.Sample> full = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ARROW, 1f, 0f, 65f, 0f, 0f, 0f, 40);
        List<TrajectorySolver.Sample> weak = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ARROW, 0f, 0f, 65f, 0f, 0f, 0f, 40);
        float fullRange = full.get(full.size() - 1).z;
        float weakRange = weak.get(weak.size() - 1).z;
        assertTrue("charge must add range", fullRange > weakRange);
    }

    @Test
    public void anUpwardShotRisesBeforeItFalls() {
        List<TrajectorySolver.Sample> arc = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ARROW, 1f, 0f, 65f, 0f, 0f, 30f, 20);
        float peak = 65f;
        for (TrajectorySolver.Sample sample : arc) peak = Math.max(peak, sample.y);
        assertTrue("an upward shot must gain height", peak > 65f);
    }

    @Test
    public void theArcIsBoundedAndNeverEmpty() {
        List<TrajectorySolver.Sample> arc = TrajectorySolver.predict(
                TrajectorySolver.Projectile.SNOWBALL, 0.5f, 0f, 65f, 0f, 0f, -90f, 12);
        assertFalse(arc.isEmpty());
        assertTrue(arc.size() <= 12);
        // A shot straight down must terminate at the ground rather than running forever.
        for (TrajectorySolver.Sample sample : arc) assertTrue(sample.y >= 0f);
    }

    @Test
    public void everySampleCarriesAFiniteTime() {
        List<TrajectorySolver.Sample> arc = TrajectorySolver.predict(
                TrajectorySolver.Projectile.ARROW, 1f, 0f, 65f, 0f, 0f, 0f, 16);
        float last = -1f;
        for (TrajectorySolver.Sample sample : arc) {
            assertFalse(Float.isNaN(sample.timeSeconds));
            assertTrue(sample.timeSeconds >= last);
            last = sample.timeSeconds;
        }
    }

    @Test
    public void distanceUsesAPeriodNotAComma() {
        // A comma decimal would render "3,2 blocks", which reads as a list of two numbers.
        assertEquals("3.2 blocks", TrajectorySolver.formatDistance(3.2f));
        assertNotNull(TrajectorySolver.formatDistance(0f));
    }
}
