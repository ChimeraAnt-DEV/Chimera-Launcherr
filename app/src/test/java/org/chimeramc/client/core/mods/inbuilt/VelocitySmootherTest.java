package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.VelocitySmoother;
import org.junit.Test;

/**
 * The Hit Prediction velocity history. The load-bearing rule is that a single jittery sample
 * must not move the marker as far as a real direction change: the velocity is averaged across
 * the whole window, not taken from the last frame.
 */
public class VelocitySmootherTest {

    @Test
    public void beforeTwoSamplesThereIsNoVelocity() {
        VelocitySmoother smoother = new VelocitySmoother();
        assertNull(smoother.velocity());
        smoother.add(0f, 0f, 0f, 0L);
        assertFalse(smoother.hasVelocity());
        assertNull(smoother.velocity());
        assertEquals(1, smoother.sampleCount());
    }

    @Test
    public void aSteadyRunReportsItsSpeed() {
        VelocitySmoother smoother = new VelocitySmoother();
        // 1 block per 100 ms along +Z is 10 blocks per second.
        for (int i = 0; i < 5; i++) {
            smoother.add(0f, 0f, i * 1f, i * 100L);
        }
        float[] v = smoother.velocity();
        assertNotNull(v);
        assertEquals(0f, v[0], 0.001f);
        assertEquals(10f, v[2], 0.001f);
        assertEquals(10f, smoother.speed(), 0.001f);
    }

    @Test
    public void oneJitterySampleBarelyMovesTheAverage() {
        VelocitySmoother steady = new VelocitySmoother();
        VelocitySmoother jittery = new VelocitySmoother();
        for (int i = 0; i < 4; i++) {
            steady.add(0f, 0f, i * 1f, i * 100L);
            jittery.add(0f, 0f, i * 1f, i * 100L);
        }
        // A single frame that jumped an extra 5 blocks.
        steady.add(0f, 0f, 4f, 400L);
        jittery.add(0f, 0f, 9f, 400L);

        float steadySpeed = steady.speed();
        float jitterySpeed = jittery.speed();
        assertTrue("a jitter must not scale the speed by the jump",
                jitterySpeed < steadySpeed * 2.5f);
    }

    @Test
    public void theWindowIsBounded() {
        VelocitySmoother smoother = new VelocitySmoother();
        for (int i = 0; i < 50; i++) smoother.add(i, 0f, 0f, i * 50L);
        assertEquals(VelocitySmoother.WINDOW, smoother.sampleCount());
    }

    @Test
    public void latestIsTheMostRecentSample() {
        VelocitySmoother smoother = new VelocitySmoother();
        smoother.add(1f, 2f, 3f, 0L);
        smoother.add(4f, 5f, 6f, 100L);
        float[] latest = smoother.latest();
        assertNotNull(latest);
        assertEquals(4f, latest[0], 0.001f);
        assertEquals(5f, latest[1], 0.001f);
        assertEquals(6f, latest[2], 0.001f);
    }

    @Test
    public void aBackwardsClockIsRejectedRatherThanReturningANegativeSpeed() {
        VelocitySmoother smoother = new VelocitySmoother();
        smoother.add(0f, 0f, 0f, 500L);
        smoother.add(0f, 0f, 5f, 400L);
        assertNull(smoother.velocity());
    }

    @Test
    public void resetClearsTheHistory() {
        VelocitySmoother smoother = new VelocitySmoother();
        smoother.add(0f, 0f, 0f, 0L);
        smoother.add(1f, 0f, 0f, 100L);
        smoother.reset();
        assertEquals(0, smoother.sampleCount());
        assertNull(smoother.latest());
    }
}
