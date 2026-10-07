package org.chimeramc.client.core.cosmetics;

/**
 * Alpha-testing rule for the cosmetics preview.
 *
 * <p>A Minecraft skin and a cosmetic atlas are RGBA images whose unused pixels are fully
 * transparent, and whose outer layers (hats, jackets, accessories) are mostly transparent with a
 * few opaque pixels. The preview paints each face by mapping its texture across a projected quad
 * with {@code drawBitmapMesh}, which has no source rectangle and <em>bilinear-filters</em> the
 * sample. Filtering blends the transparent background into the opaque texels at every edge, so the
 * quad ends up a soft translucent sheet instead of a hard-edged shape: the character's body reads
 * as hollow or see-through from the front, side and back, because the interior faces of the head
 * and torso are painted through the semi-transparent skin.
 *
 * <p>The fix is alpha testing, the same rule a GPU applies with {@code alphaTest}: a texel below
 * {@link #ALPHA_THRESHOLD} is discarded outright, and an opaque texel is kept at full alpha. The
 * preview realises it as a hard-edged cutout mask (drawn unfiltered) applied with
 * {@code PorterDuff.DST_IN}, which reproduces the discard without a per-pixel shader.
 *
 * <p>This class is deliberately Android-free: it reads pixels through a {@link PixelSource} so the
 * threshold, the "is anything opaque here" and the "is everything opaque here" rules are all
 * unit-testable with a fake source, no device and no mocks.
 */
public final class SkinAlphaFilter {

    /**
     * A texel is opaque when its alpha reaches this. 25 is high enough to discard the faint
     * anti-aliased halo a texture editor leaves around a shape and low enough that a deliberately
     * semi-transparent cosmetic pixel (a glass pane, a ghost) is not punched out.
     */
    public static final int ALPHA_THRESHOLD = 25;

    /** The alpha byte of an ARGB colour. */
    public static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** Whether a texel survives the alpha test. */
    public static boolean isOpaque(int argb) {
        return alphaOf(argb) >= ALPHA_THRESHOLD;
    }

    /** Whether a texel is fully (or near-fully) opaque, so the mask pass can be skipped. */
    public static boolean isFullyOpaque(int argb) {
        return alphaOf(argb) >= 255;
    }

    /**
     * Whether any texel in the region survives the alpha test. A region with none is dropped
     * entirely, so an unused overlay costs nothing and never paints an invisible quad.
     */
    public static boolean hasAnyOpaque(PixelSource source) {
        if (source == null) return false;
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                if (isOpaque(source.argbAt(x, y))) return true;
            }
        }
        return false;
    }

    /**
     * Whether every texel survives the alpha test at full alpha. A fully opaque region needs no
     * cutout mask, which is the common case for a skin's base layer and keeps the per-frame cost to
     * only the layers that actually have holes.
     */
    public static boolean isFullyOpaque(PixelSource source) {
        if (source == null) return false;
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                if (!isFullyOpaque(source.argbAt(x, y))) return false;
            }
        }
        return true;
    }

    /**
     * Writes a hard-edged alpha mask for the region into {@code out} (row-major, {@code w*h}):
     * {@code 0xFFFFFFFF} where a texel survives the test, {@code 0x00000000} where it is discarded.
     *
     * <p>Drawn unfiltered with {@code DST_IN} over a face, this is what turns the bilinear-blended
     * edge back into a crisp cutout — the preview's equivalent of a shader's {@code discard}.
     *
     * @return true when the mask has at least one transparent texel (i.e. it is worth applying)
     */
    public static boolean buildMask(PixelSource source, int[] out) {
        if (source == null || out == null) return false;
        int w = source.width();
        int h = source.height();
        if (out.length < w * h) return false;
        boolean anyTransparent = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isOpaque(source.argbAt(x, y))) {
                    out[y * w + x] = 0xFFFFFFFF;
                } else {
                    out[y * w + x] = 0x00000000;
                    anyTransparent = true;
                }
            }
        }
        return anyTransparent;
    }

    /** An integer-addressable pixel grid, so the rule needs no Android Bitmap. */
    public interface PixelSource {
        int width();

        int height();

        /** The ARGB colour at {@code (x, y)}; callers keep the coordinates in range. */
        int argbAt(int x, int y);
    }

    private SkinAlphaFilter() {
    }
}
