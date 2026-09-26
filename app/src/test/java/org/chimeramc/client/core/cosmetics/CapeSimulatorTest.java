package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Motion and shape tests for the cape cloth.
 *
 * <p>These are the properties that make the cape look right rather than merely run: the rest
 * shape must match Minecraft's cape dimensions, a settled cape must hang straight, and a
 * disturbed one must come back to rest instead of ringing forever. Frame-rate independence is
 * asserted because the fixed-timestep accumulator is the whole reason the cloth is stable.
 */
public class CapeSimulatorTest {

    private static final float EPS = 0.02f;

    @Test
    public void restWidthMatchesMinecraftCapeWidth() {
        // Minecraft's cape texture is 10 px wide out of 16, so the cloth is 0.625 blocks across.
        assertEquals(0.625f, CapeSimulator.WIDTH_BLOCKS, 1e-5f);
        assertEquals(1f, CapeSimulator.HEIGHT_BLOCKS, 1e-5f);
    }

    @Test
    public void restShapeHangsBelowTheAnchor() {
        CapeSimulator sim = new CapeSimulator();
        sim.reset(0f, 0f, 0f);

        // The top row is pinned at the anchor, everything below hangs downward.
        for (int c = 0; c < CapeSimulator.COLS; c++) {
            assertEquals("top row must sit on the anchor", 0f, sim.y(c), EPS);
        }
        int bottomLeft = (CapeSimulator.ROWS - 1) * CapeSimulator.COLS;
        assertTrue("the hem must hang below the anchor", sim.y(bottomLeft) < -0.9f);
    }

    @Test
    public void restShapeIsHorizontalAtTheTop() {
        CapeSimulator sim = new CapeSimulator();
        sim.reset(0f, 0f, 0f);
        float left = sim.x(0);
        float right = sim.x(CapeSimulator.COLS - 1);
        assertEquals(-CapeSimulator.WIDTH_BLOCKS / 2f, left, EPS);
        assertEquals(CapeSimulator.WIDTH_BLOCKS / 2f, right, EPS);
    }

    @Test
    public void settlingAfterADisturbanceReturnsToRest() {
        CapeSimulator sim = new CapeSimulator();
        sim.reset(0f, 0f, 0f);

        int hem = (CapeSimulator.ROWS - 1) * CapeSimulator.COLS + 1;
        // Push the hem with sustained wind, then let it settle with no input at all.
        for (int i = 0; i < 600; i++) {
            sim.step(CapeSimulator.FIXED_DT, 0f, 0f, 0f, 1f, 0f, 3f);
        }
        for (int i = 0; i < 900; i++) {
            sim.step(CapeSimulator.FIXED_DT, 0f, 0f, 0f, 0f, 0f, 0f);
        }
        // A cape that never stops moving would keep the overlay redrawing forever, which is the
        // bug this pins: it must actually come back to its rest pose.
        assertEquals(-CapeSimulator.HEIGHT_BLOCKS, sim.y(hem), 0.25f);
        assertEquals(0f, sim.z(hem), 0.25f);
    }

    @Test
    public void frameRateDoesNotChangeTheRestPose() {
        // The same total time delivered in different slice sizes must land in the same place.
        float y60 = runFor(1.0f, CapeSimulator.FIXED_DT);
        float y30 = runFor(1.0f, 2f * CapeSimulator.FIXED_DT);
        assertEquals(y60, y30, 0.05f);
    }

    private float runFor(float seconds, float dt) {
        CapeSimulator sim = new CapeSimulator();
        sim.reset(0f, 0f, 0f);
        int steps = Math.round(seconds / dt);
        for (int i = 0; i < steps; i++) {
            sim.step(dt, 0f, 0f, 0f, 0f, 0f, 0f);
        }
        return sim.y((CapeSimulator.ROWS - 1) * CapeSimulator.COLS);
    }

    @Test
    public void aLongStallDoesNotExplodeTheCloth() {
        CapeSimulator sim = new CapeSimulator();
        sim.reset(0f, 0f, 0f);
        // A five-second hitch must be clamped, not integrated in one giant slice.
        sim.step(5f, 0f, 0f, 0f, 0f, 0f, 0f);
        for (int i = 0; i < sim.size(); i++) {
            assertTrue("finite position after a stall", Float.isFinite(sim.x(i)));
            assertTrue("finite position after a stall", Float.isFinite(sim.y(i)));
            assertTrue("finite position after a stall", Float.isFinite(sim.z(i)));
            assertTrue("cloth must stay near the anchor", Math.abs(sim.y(i)) < 5f);
        }
    }

    @Test
    public void anchoredParticlesFollowTheirAnchor() {
        CapeSimulator sim = new CapeSimulator();
        sim.reset(0f, 0f, 0f);
        sim.step(CapeSimulator.FIXED_DT, 10f, 5f, 2f, 0f, 0f, 0f);
        // The top row is pinned: moving the anchor moves those particles with it exactly.
        assertEquals(10f - CapeSimulator.WIDTH_BLOCKS / 2f, sim.x(0), 0.02f);
        assertEquals(5f, sim.y(0), 0.02f);
        assertEquals(2f, sim.z(0), 0.02f);
    }

    @Test
    public void gridHasTheStatedSize() {
        CapeSimulator sim = new CapeSimulator();
        assertEquals(CapeSimulator.COLS * CapeSimulator.ROWS, sim.size());
    }
}
