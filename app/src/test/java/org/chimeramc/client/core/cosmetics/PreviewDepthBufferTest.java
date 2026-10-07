package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The preview's cape-occlusion rule. The camera convention (pinned by {@code SkinModelTest}) is
 * that <b>smaller depth is nearer</b>, so the buffer keeps the minimum depth per pixel and a cape is
 * drawn only when it is at least as near as the body recorded there. That is what stops the cape
 * showing through the skin.
 */
public class PreviewDepthBufferTest {

    /** A quad spanning [x0,x1] x [y0,y1] in grid order (TL, TR, BL, BR). */
    private static float[] quad(float x0, float y0, float x1, float y1) {
        return new float[]{x0, y0, x1, y0, x0, y1, x1, y1};
    }

    @Test
    public void anEmptyBufferNeverOccludes() {
        PreviewDepthBuffer buffer = new PreviewDepthBuffer();
        buffer.prepare(32, 32);
        // Before any body face is recorded, everything is visible, whatever its depth.
        assertTrue(buffer.test(10f, 10f, 5f));
        assertTrue(buffer.test(10f, 10f, 500f));
    }

    @Test
    public void aNearerBodyPixelOccludesAFartherCape() {
        PreviewDepthBuffer buffer = new PreviewDepthBuffer();
        buffer.prepare(32, 32);
        // Small depth = near. Record the body near the camera (depth 60) over the middle.
        buffer.accept(quad(8f, 8f, 24f, 24f), 60f);

        // A cape farther away (larger depth) is hidden where the body is, visible off the body.
        assertFalse("cape behind the skin must be occluded", buffer.test(16f, 16f, 120f));
        assertTrue("cape off the body is visible", buffer.test(2f, 2f, 120f));
    }

    @Test
    public void aNearerCapeDrawsOverTheBody() {
        PreviewDepthBuffer buffer = new PreviewDepthBuffer();
        buffer.prepare(32, 32);
        buffer.accept(quad(8f, 8f, 24f, 24f), 100f);
        // Viewed from behind, the cape is nearer than the body and must show.
        assertTrue("cape in front of the skin must draw", buffer.test(16f, 16f, 60f));
    }

    @Test
    public void theNearestBodySurfaceWins() {
        PreviewDepthBuffer buffer = new PreviewDepthBuffer();
        buffer.prepare(32, 32);
        buffer.accept(quad(0f, 0f, 32f, 32f), 140f);   // far surface
        buffer.accept(quad(8f, 8f, 24f, 24f), 60f);    // near surface over part of it (smaller)
        // At the near patch, a cape at 80 is behind it; at 40 it is in front.
        assertFalse(buffer.test(16f, 16f, 80f));
        assertTrue(buffer.test(16f, 16f, 40f));
        // Off the near patch only the far surface applies, so a cape at 100 is in front.
        assertTrue(buffer.test(2f, 2f, 100f));
    }

    @Test
    public void prepareClearsBetweenFrames() {
        PreviewDepthBuffer buffer = new PreviewDepthBuffer();
        buffer.prepare(16, 16);
        buffer.accept(quad(0f, 0f, 16f, 16f), 20f);
        assertFalse(buffer.test(8f, 8f, 100f));
        buffer.prepare(16, 16);
        assertTrue("a fresh frame has no occlusion", buffer.test(8f, 8f, 100f));
    }

    @Test
    public void degenerateSizesAreHandled() {
        PreviewDepthBuffer buffer = new PreviewDepthBuffer();
        buffer.prepare(0, 0);
        assertTrue("no buffer never occludes", buffer.test(5f, 5f, 1f));
        buffer.prepare(4, 4);
        assertTrue(buffer.test(100f, 100f, 1f)); // out of bounds is visible
    }
}
