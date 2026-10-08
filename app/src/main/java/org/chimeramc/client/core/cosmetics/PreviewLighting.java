package org.chimeramc.client.core.cosmetics;

/**
 * The light model the character preview shades with, as pure arithmetic.
 *
 * <p>The preview used to shade each face from a hand-written ramp that flipped hard between a
 * bright front and a very dark back, and it darkened a skin's hat/hair overlay by the same amount
 * as the base layer. That reads as a flat, muddy cardboard cut-out. This class replaces it with a
 * single directional light so the brightness is continuous around the model, and it separates the
 * two jobs the renderer needs: a translucent overlay to darken a textured face, and a colour
 * multiply for the flat accessory and pet quads.
 *
 * <p>Keeping it Android-free means the ramp is unit-testable — the difference between "looks 3D"
 * and "looks flat" is a handful of numbers, and a regression there is otherwise invisible until
 * someone looks at a device.
 */
public final class PreviewLighting {

    /** Light direction, normalised. Upper front-right, the direction a key light usually sits. */
    private static final double LX = 0.30;
    private static final double LY = 0.55;
    private static final double LZ = 0.78;

    /** How bright an unlit face still is; below this a back face goes flat black. */
    public static final double AMBIENT = 0.52;

    /**
     * A soft fill light from the opposite side, as a fraction of the key. A single directional
     * light leaves every face turned away from it at the same flat ambient, which is what makes a
     * render read as a lit cut-out. A weak counter-fill lifts the shadow side without inverting the
     * key, so a box keeps a clear light side and shadow side while the shadow side still shows its
     * form. Kept below the gap that separates the front from the back, so the front stays the
     * brighter of the two.
     */
    public static final double FILL = 0.20;

    /**
     * Alpha of a black overlay at the darkest a face ever gets.
     *
     * <p>Kept moderate on purpose: a heavy black overlay on the shadow side reads as grime or a
     * hard facet seam rather than shade. A lighter, wider ramp keeps the model reading as one
     * continuous lit surface, which is the "smooth, not plastic" property.
     */
    public static final int MAX_DARKEN_ALPHA = 104;

    /** How much of the light ramp a skin's overlay layer takes, so hats and eyes stay readable. */
    public static final double OVERLAY_SHARE = 0.35;

    private PreviewLighting() {
    }

    /**
     * A translucent black overlay for a textured face: more opaque the less the face is lit.
     *
     * <p>Alpha rather than a colour multiply because the face is a bitmap; the overlay is painted
     * on top with the source texture showing through.
     */
    public static int overlayAlphaFor(SkinModel.Face face, float perspective) {
        double intensity = intensityFor(face, perspective);
        double alpha = (1.0 - intensity) * MAX_DARKEN_ALPHA;
        return (int) Math.round(clamp(alpha, 0.0, MAX_DARKEN_ALPHA));
    }

    /** The same overlay for a skin's second layer, softened so hats and eyes are not dimmed flat. */
    public static int overlayAlphaForOverlayLayer(SkinModel.Face face, float perspective) {
        return (int) Math.round(overlayAlphaFor(face, perspective) * OVERLAY_SHARE);
    }

    /**
     * A grey multiplier (0..255) for a textured face, applied as a {@code MULTIPLY} colour filter on
     * the bitmap.
     *
     * <p>Multiplying the texture keeps a transparent texel transparent, so a hat overlay's holes
     * never darken the head beneath it — unlike a black overlay drawn over the whole quad, which
     * films the holes. The value is the same ramp as {@link #shadeColor}'s factor, expressed as an
     * 8-bit grey.
     */
    public static int shadeGreyFor(SkinModel.Face face, float perspective) {
        double intensity = intensityFor(face, perspective);
        double factor = 0.60 + 0.40 * intensity;
        return (int) Math.round(clamp(factor, 0.0, 1.0) * 255.0);
    }

    /** The softer grey for a skin's second layer, so hats and eyes stay readable. */
    public static int shadeGreyForOverlayLayer(SkinModel.Face face, float perspective) {
        int base = shadeGreyFor(face, perspective);
        // Blend toward full white by OVERLAY_SHARE, matching the overlay alpha softening.
        return (int) Math.round(255 - (255 - base) * OVERLAY_SHARE);
    }

    /** The grey multiplier for an arbitrary (bone-rotated) normal, for an authored mesh face. */
    public static int shadeGreyForNormal(double nx, double ny, double nz, float perspective) {
        double intensity = intensityForNormal(nx, ny, nz);
        double p = clamp(perspective, 0.8, 1.25);
        intensity *= 0.92 + 0.08 * ((p - 0.8) / 0.45);
        intensity = clamp(intensity, 0.25, 1.0);
        double factor = 0.60 + 0.40 * intensity;
        return (int) Math.round(clamp(factor, 0.0, 1.0) * 255.0);
    }

    /** A flat quad's colour, multiplied toward black by the face's shadow. */
    public static int shadeColor(int color, SkinModel.Face face, float perspective) {
        double intensity = intensityFor(face, perspective);
        // Keep a floor so a flat quad on a back face is shaded, not turned into a silhouette.
        double factor = 0.60 + 0.40 * intensity;
        int a = (color >>> 24) & 0xFF;
        int r = (int) Math.round(((color >>> 16) & 0xFF) * factor);
        int g = (int) Math.round(((color >>> 8) & 0xFF) * factor);
        int b = (int) Math.round((color & 0xFF) * factor);
        return (a << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    /** The face's brightness in {@code [AMBIENT, 1]}, before any overlay alpha is derived. */
    public static double intensityFor(SkinModel.Face face, float perspective) {
        double intensity = intensityForNormal(face.nx, face.ny, face.nz);
        // A nearer face is a touch brighter and a farther one a touch dimmer, the cheap depth cue
        // that keeps a perspective render from reading flat.
        double p = clamp(perspective, 0.8, 1.25);
        intensity *= 0.92 + 0.08 * ((p - 0.8) / 0.45);
        return clamp(intensity, 0.25, 1.0);
    }

    /**
     * Brightness for an arbitrary surface normal, for surfaces that are not axis-aligned faces.
     *
     * <p>The cape's cloth is a deforming mesh, so its normals are not one of the six box faces.
     * Sharing this one light with the box faces is what keeps the cape lit by the same sun as the
     * character wearing it.
     */
    public static double intensityForNormal(double nx, double ny, double nz) {
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-6 || Double.isNaN(length)) return AMBIENT;
        double dot = (nx * LX + ny * LY + nz * LZ) / length;
        double diffuse = Math.max(0.0, dot);
        // A weak counter-fill opposite the key. It never turns a shadowed face brighter than a lit
        // one, because its contribution is a fraction of the key's and the key is zero on the faces
        // the fill lifts most.
        double fill = Math.max(0.0, -dot) * FILL;
        return clamp(AMBIENT + (1.0 - AMBIENT) * diffuse + fill, 0.25, 1.0);
    }

    /** A flat colour for an arbitrary normal, multiplied toward black by the surface's shadow. */
    public static int shadeColorForNormal(int color, double nx, double ny, double nz) {
        double intensity = intensityForNormal(nx, ny, nz);
        double factor = 0.60 + 0.40 * intensity;
        int a = (color >>> 24) & 0xFF;
        int r = (int) Math.round(((color >>> 16) & 0xFF) * factor);
        int g = (int) Math.round(((color >>> 8) & 0xFF) * factor);
        int b = (int) Math.round((color & 0xFF) * factor);
        return (a << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return value < min ? min : Math.min(value, max);
    }
}
