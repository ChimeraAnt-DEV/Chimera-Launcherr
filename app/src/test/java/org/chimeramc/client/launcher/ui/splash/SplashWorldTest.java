package org.chimeramc.client.launcher.ui.splash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/**
 * The splash world's motion rules, pinned without a device.
 *
 * <p>The world is pure arithmetic precisely so these hold on a headless build machine: a star that
 * drifts off the frame, an alpha that leaves {@code [0,1]}, a terrain height outside its band or a
 * shooting star that never re-arms are all silent on the build machine and obvious on a phone. A
 * fixed seed makes the run deterministic, so a change to the motion shows up as a failing number
 * rather than a shrug.
 */
public class SplashWorldTest {

    private static SplashWorld seeded(long seed) {
        SplashWorld world = new SplashWorld();
        world.seed(new Random(), seed);
        return world;
    }

    @Test
    public void starsStayInTheFrameUnderParallax() {
        SplashWorld world = seeded(11L);
        for (int frame = 0; frame < 600; frame++) {
            world.update(16f);
            for (int i = 0; i < world.starCount(); i++) {
                float px = wrap01(world.starX(i) + world.starParallax(world.starLayer(i)));
                assertTrue("star x in [0,1]: " + px, px >= 0f && px <= 1f);
                assertTrue("star y in the sky band: " + world.starY(i),
                        world.starY(i) >= 0f && world.starY(i) <= SplashWorld.STAR_BAND);
            }
        }
    }

    @Test
    public void starAlphaAlwaysStaysInRange() {
        SplashWorld world = seeded(12L);
        for (int frame = 0; frame < 400; frame++) {
            world.update(24f);
            for (int i = 0; i < world.starCount(); i++) {
                float alpha = world.starAlpha(i);
                assertTrue("alpha >= 0: " + alpha, alpha >= 0f);
                assertTrue("alpha <= 1: " + alpha, alpha <= 1f);
            }
        }
    }

    @Test
    public void parallaxOffsetStaysBounded() {
        SplashWorld world = seeded(13L);
        for (int frame = 0; frame < 1000; frame++) {
            world.update(33f);
            for (int layer = 0; layer < 2; layer++) {
                float p = world.starParallax(layer);
                assertTrue("parallax wraps into [-1,1]: " + p, p >= -1f && p <= 1f);
            }
        }
    }

    @Test
    public void terrainHeightsStayInsideTheirBand() {
        SplashWorld world = seeded(14L);
        for (int layer = 0; layer < 2; layer++) {
            for (int c = 0; c < SplashWorld.TERRAIN_COLUMNS; c++) {
                float height = world.terrainHeight(layer, c);
                assertTrue("terrain height positive: " + height, height > 0f);
                assertTrue("terrain height bounded: " + height, height <= 0.30f);
            }
        }
        // A negative column index wraps rather than reading out of bounds.
        assertEquals(world.terrainHeight(0, 0), world.terrainHeight(0, SplashWorld.TERRAIN_COLUMNS),
                0.0001f);
    }

    @Test
    public void blocksBobAndStayNearTheFrame() {
        SplashWorld world = seeded(15L);
        for (int frame = 0; frame < 500; frame++) {
            world.update(16f);
            for (int i = 0; i < world.blockCount(); i++) {
                float x = world.blockX(i);
                float y = world.blockY(i);
                assertTrue("block x in [0,1]: " + x, x >= 0f && x <= 1f);
                // Bobbing is bounded by the amplitude around the seeded y, which is in [0.18,0.70].
                assertTrue("block y stays on screen: " + y, y > -0.05f && y < 0.95f);
            }
        }
    }

    @Test
    public void aShootingStarAppearsThenReArms() {
        SplashWorld world = seeded(16L);
        int sightings = 0;
        boolean wasActive = false;
        // Run ~60 seconds of scene time; several stars must have come and gone.
        for (int frame = 0; frame < 3600; frame++) {
            world.update(16f);
            boolean active = world.shootingStarActive();
            if (active && !wasActive) sightings++;
            wasActive = active;
            if (active) {
                float alpha = world.shootingStarAlpha();
                assertTrue("shooting star alpha in range", alpha >= 0f && alpha <= 1f);
            }
        }
        assertTrue("a shooting star must appear more than once: " + sightings, sightings >= 2);
    }

    @Test
    public void updateWithNoTimeDoesNothing() {
        SplashWorld world = seeded(17L);
        float before = world.time();
        world.update(0f);
        world.update(-5f);
        assertEquals(before, world.time(), 0.0001f);
        assertFalse(world.shootingStarActive());
    }

    private static float wrap01(float value) {
        float v = value % 1f;
        return v < 0f ? v + 1f : v;
    }
}
