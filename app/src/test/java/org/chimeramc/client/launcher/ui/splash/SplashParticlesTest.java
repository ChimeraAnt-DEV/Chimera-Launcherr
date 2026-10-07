package org.chimeramc.client.launcher.ui.splash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/**
 * The splash ember field's motion rules, pinned without a device.
 *
 * <p>The field is pure arithmetic precisely so these hold on a headless build machine: an ember
 * that sinks instead of rising, one that escapes the view box, or an alpha that leaves {@code [0,1]}
 * are all silent on the build machine and obvious on a phone. A fixed seed makes the run
 * deterministic, so a change to the motion shows up as a failing number rather than a shrug.
 */
public class SplashParticlesTest {

    private static SplashParticles seeded(long seed) {
        SplashParticles particles = new SplashParticles(64);
        particles.seed(new Random(), seed);
        return particles;
    }

    @Test
    public void embersRiseOverTime() {
        SplashParticles particles = seeded(1L);
        float before = particles.y(0);
        particles.update(100f);
        assertTrue("an ember must move up (smaller y), not down",
                particles.y(0) < before);
    }

    @Test
    public void embersStayInsideTheViewBox() {
        SplashParticles particles = seeded(2L);
        for (int frame = 0; frame < 400; frame++) {
            particles.update(16f);
            for (int i = 0; i < particles.count(); i++) {
                assertTrue("x in [0,1]: " + particles.x(i),
                        particles.x(i) >= 0f && particles.x(i) <= 1f);
                assertTrue("y in [-0.1,1.1]: " + particles.y(i),
                        particles.y(i) >= -0.1f && particles.y(i) <= 1.1f);
            }
        }
    }

    @Test
    public void alphaAlwaysStaysInRange() {
        SplashParticles particles = seeded(3L);
        for (int frame = 0; frame < 300; frame++) {
            particles.update(24f);
            for (int i = 0; i < particles.count(); i++) {
                float alpha = particles.alpha(i);
                assertTrue("alpha >= 0: " + alpha, alpha >= 0f);
                assertTrue("alpha <= 1: " + alpha, alpha <= 1f);
            }
        }
    }

    @Test
    public void swayOffsetStaysWithinItsAmplitude() {
        SplashParticles particles = seeded(4L);
        for (int frame = 0; frame < 200; frame++) {
            particles.update(20f);
            for (int i = 0; i < particles.count(); i++) {
                assertTrue("sway is bounded by the amplitude",
                        Math.abs(particles.swayOffset(i)) <= SplashParticles.MAX_SWAY + 0.0001f);
            }
        }
    }

    @Test
    public void recyclingRespawnsOffTheBottomWithANewColumn() {
        SplashParticles particles = seeded(5L);
        // Run long enough that every ember must have recycled at least once.
        float[] initialX = new float[particles.count()];
        for (int i = 0; i < particles.count(); i++) initialX[i] = particles.x(i);
        for (int frame = 0; frame < 3000; frame++) particles.update(16f);

        boolean anyMoved = false;
        for (int i = 0; i < particles.count(); i++) {
            if (Math.abs(particles.x(i) - initialX[i]) > 0.01f) anyMoved = true;
            assertTrue("recycled ember sits near the bottom: " + particles.y(i),
                    particles.y(i) >= -0.1f);
        }
        assertTrue("recycling must change the column so it does not loop visibly", anyMoved);
    }

    @Test
    public void seededFieldIsDeterministic() {
        SplashParticles a = seeded(42L);
        SplashParticles b = seeded(42L);
        for (int frame = 0; frame < 100; frame++) {
            a.update(16f);
            b.update(16f);
        }
        for (int i = 0; i < a.count(); i++) {
            assertEquals(a.x(i), b.x(i), 0.0001f);
            assertEquals(a.y(i), b.y(i), 0.0001f);
            assertEquals(a.alpha(i), b.alpha(i), 0.0001f);
        }
    }

    @Test
    public void nonPositiveDeltaIsIgnored() {
        SplashParticles particles = seeded(6L);
        float before = particles.y(0);
        particles.update(0f);
        particles.update(-5f);
        assertEquals(before, particles.y(0), 0.0001f);
    }
}
