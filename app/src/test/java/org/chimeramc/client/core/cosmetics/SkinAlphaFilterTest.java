package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the alpha-test rule that makes the preview draw a hard silhouette instead of a translucent
 * sheet: a texel below the threshold is discarded, a fully opaque region needs no cutout, and the
 * generated mask marks exactly the surviving texels.
 */
public class SkinAlphaFilterTest {

    /** A simple int grid, so the rule is exercised with no Android {@code Bitmap}. */
    private static SkinAlphaFilter.PixelSource source(int w, int h, int... pixels) {
        return new SkinAlphaFilter.PixelSource() {
            @Override
            public int width() {
                return w;
            }

            @Override
            public int height() {
                return h;
            }

            @Override
            public int argbAt(int x, int y) {
                return pixels[y * w + x];
            }
        };
    }

    @Test
    public void alphaOfReadsTheTopByte() {
        assertEquals(0, SkinAlphaFilter.alphaOf(0x00FFFFFF));
        assertEquals(128, SkinAlphaFilter.alphaOf(0x80ABCDEF));
        assertEquals(255, SkinAlphaFilter.alphaOf(0xFFFFFFFF));
    }

    @Test
    public void aFullyTransparentTexelIsDiscarded() {
        assertFalse(SkinAlphaFilter.isOpaque(0x00FFFFFF));
        assertFalse(SkinAlphaFilter.isOpaque(0x10FF0000));
    }

    @Test
    public void aFaintHaloIsDiscardedButADeliberateAlphaIsKept() {
        // Just under the threshold is anti-aliasing noise and is cut.
        assertFalse(SkinAlphaFilter.isOpaque((SkinAlphaFilter.ALPHA_THRESHOLD - 1) << 24));
        // The threshold itself survives, so a glass pane at 26/255 is not punched out.
        assertTrue(SkinAlphaFilter.isOpaque(SkinAlphaFilter.ALPHA_THRESHOLD << 24));
    }

    @Test
    public void anEmptyRegionHasNothingOpaque() {
        SkinAlphaFilter.PixelSource s = source(2, 1, 0x00000000, 0x00FFFFFF);
        assertFalse(SkinAlphaFilter.hasAnyOpaque(s));
    }

    @Test
    public void aRegionWithOneOpaqueTexelCounts() {
        SkinAlphaFilter.PixelSource s = source(2, 1, 0x00000000, 0xFF123456);
        assertTrue(SkinAlphaFilter.hasAnyOpaque(s));
    }

    @Test
    public void aFullyOpaqueRegionNeedsNoCutout() {
        SkinAlphaFilter.PixelSource s = source(2, 1, 0xFF000000, 0xFFFFFFFF);
        assertTrue(SkinAlphaFilter.isFullyOpaque(s));
    }

    @Test
    public void aRegionWithAHoleIsNotFullyOpaque() {
        SkinAlphaFilter.PixelSource s = source(2, 1, 0xFF000000, 0x00000000);
        assertFalse(SkinAlphaFilter.isFullyOpaque(s));
    }

    @Test
    public void buildMaskMarksSurvivorsAndReportsHoles() {
        SkinAlphaFilter.PixelSource s = source(2, 2,
                0xFF112233, 0x00000000,
                0x00000000, 0xFFFF00FF);
        int[] out = new int[4];
        boolean anyTransparent = SkinAlphaFilter.buildMask(s, out);
        assertTrue(anyTransparent);
        assertEquals(0xFFFFFFFF, out[0]);
        assertEquals(0x00000000, out[1]);
        assertEquals(0x00000000, out[2]);
        assertEquals(0xFFFFFFFF, out[3]);
    }

    @Test
    public void buildMaskOfAFullyOpaqueRegionReportsNoHoles() {
        SkinAlphaFilter.PixelSource s = source(1, 1, 0xFFAABBCC);
        int[] out = new int[1];
        assertFalse(SkinAlphaFilter.buildMask(s, out));
        assertEquals(0xFFFFFFFF, out[0]);
    }

    @Test
    public void buildMaskRejectsAnUndersizedTarget() {
        SkinAlphaFilter.PixelSource s = source(2, 2, 0xFF000000, 0xFF000000,
                0xFF000000, 0xFF000000);
        assertFalse(SkinAlphaFilter.buildMask(s, new int[3]));
    }
}
