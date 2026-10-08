package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The character preview's light model.
 *
 * <p>The property that matters is continuity: the front must be brighter than the back, the top
 * brighter than the bottom, and no face may fall to a flat black or blow out to pure white. Those
 * are what separate a render that reads as three-dimensional from the hard-stepped ramp this
 * replaced.
 */
public class PreviewLightingTest {

    @Test
    public void theLitFrontIsBrighterThanTheShadowedBack() {
        int front = PreviewLighting.overlayAlphaFor(SkinModel.Face.FRONT, 1f);
        int back = PreviewLighting.overlayAlphaFor(SkinModel.Face.BACK, 1f);
        assertTrue("the front must be less darkened than the back", front < back);
    }

    @Test
    public void theTopIsBrighterThanTheBottom() {
        int top = PreviewLighting.overlayAlphaFor(SkinModel.Face.TOP, 1f);
        int bottom = PreviewLighting.overlayAlphaFor(SkinModel.Face.BOTTOM, 1f);
        assertTrue("the top must be less darkened than the bottom", top < bottom);
    }

    @Test
    public void noFaceGoesFullyBlackOrBlowsOut() {
        for (SkinModel.Face face : SkinModel.Face.values()) {
            int alpha = PreviewLighting.overlayAlphaFor(face, 1f);
            assertTrue("overlay alpha in range for " + face,
                    alpha >= 0 && alpha <= PreviewLighting.MAX_DARKEN_ALPHA);
            double intensity = PreviewLighting.intensityFor(face, 1f);
            assertTrue("intensity in range for " + face, intensity >= 0.25 && intensity <= 1.0);
        }
    }

    @Test
    public void theOverlayLayerIsShadedMoreGentlyThanTheBase() {
        for (SkinModel.Face face : SkinModel.Face.values()) {
            int base = PreviewLighting.overlayAlphaFor(face, 1f);
            int overlay = PreviewLighting.overlayAlphaForOverlayLayer(face, 1f);
            assertTrue("the second layer must not be dimmed as hard as the base (" + face + ")",
                    overlay <= base);
        }
    }

    @Test
    public void flatQuadShadingPreservesAlphaAndDarkensTheBack() {
        int color = 0xFF3366CC;
        int front = PreviewLighting.shadeColor(color, SkinModel.Face.FRONT, 1f);
        int back = PreviewLighting.shadeColor(color, SkinModel.Face.BACK, 1f);
        assertEquals("alpha preserved", 0xFF, front >>> 24);
        assertTrue("the back must be darker", (back >>> 16 & 0xFF) < (front >>> 16 & 0xFF));
    }

    @Test
    public void aNearerFaceIsLitAtLeastAsMuchAsAFartherOne() {
        int near = PreviewLighting.overlayAlphaFor(SkinModel.Face.LEFT, 1.2f);
        int far = PreviewLighting.overlayAlphaFor(SkinModel.Face.LEFT, 0.8f);
        assertTrue("a nearer face must not be darker than a farther one", near <= far);
    }

    @Test
    public void aNonFinitePerspectiveDoesNotProduceANonFiniteResult() {
        int alpha = PreviewLighting.overlayAlphaFor(SkinModel.Face.FRONT, Float.NaN);
        assertTrue(alpha >= 0 && alpha <= PreviewLighting.MAX_DARKEN_ALPHA);
        double intensity = PreviewLighting.intensityFor(SkinModel.Face.FRONT, Float.POSITIVE_INFINITY);
        assertTrue(Double.isFinite(intensity));
    }

    /**
     * An animated limb shades from its rotated normal, so the same face at a different bone
     * rotation must come out a different brightness. If it did not, a swinging leg would slide
     * through its arc at a constant shade and read as a flat sticker.
     */
    @Test
    public void aRotatedNormalShadesDifferentlyFromTheRestingNormal() {
        int color = 0xFF3366CC;
        int resting = PreviewLighting.shadeColorForNormal(color, 0, 0, 1);
        int swung = PreviewLighting.shadeColorForNormal(color, 0, -0.9, 0.44);
        assertTrue("a rotated normal must change the shade", resting != swung);
    }

    @Test
    public void aRotatedNormalStaysInRangeAndPreservesAlpha() {
        for (double angle = -Math.PI; angle <= Math.PI; angle += Math.PI / 8) {
            double nx = Math.sin(angle);
            double ny = -Math.cos(angle);
            int shaded = PreviewLighting.shadeColorForNormal(0xFF3366CC, nx, ny, 0);
            assertEquals("alpha preserved", 0xFF, shaded >>> 24);
            assertTrue("channels in range", (shaded & 0xFF) <= 0xCC);
        }
    }

    /**
     * A textured face is shaded with a grey MULTIPLY factor on the bitmap, so a transparent texel
     * stays transparent and a hat overlay never darkens the head beneath it. The grey must be in
     * range, actually darker on a face turned away from the light (or a hat reads as a flat paper
     * cut-out), and the overlay layer must be softer than the base so hair and eyes stay readable.
     */
    @Test
    public void aTexturedFaceGetsAUsableShadeGrey() {
        int lit = PreviewLighting.shadeGreyFor(SkinModel.Face.FRONT, 1f);
        int shadowed = PreviewLighting.shadeGreyFor(SkinModel.Face.BACK, 1f);
        assertTrue("grey in range", lit >= 0 && lit <= 255);
        assertTrue("grey in range", shadowed >= 0 && shadowed <= 255);
        assertTrue("a shadowed face is darker", shadowed < lit);
        // The overlay layer is softened toward white, so it is never darker than the base.
        assertTrue("overlay softer",
                PreviewLighting.shadeGreyForOverlayLayer(SkinModel.Face.BACK, 1f)
                        >= PreviewLighting.shadeGreyFor(SkinModel.Face.BACK, 1f));
        // An arbitrary (bone-rotated) normal must also produce a usable grey.
        int normalLit = PreviewLighting.shadeGreyForNormal(0, 1, 0, 1f);
        int normalShadowed = PreviewLighting.shadeGreyForNormal(0, -1, 0, 1f);
        assertTrue("normal grey in range", normalLit >= 0 && normalLit <= 255);
        assertTrue("normal grey in range", normalShadowed >= 0 && normalShadowed <= 255);
        assertTrue("a shadowed normal is darker", normalShadowed < normalLit);
    }
}
