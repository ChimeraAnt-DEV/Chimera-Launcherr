package org.chimeramc.client.core.cosmetics;

/**
 * Paints a two-region atlas with form shading and a surface texture, rather than a flat fill.
 *
 * <p>{@link FlatColorAtlas} is a single solid colour per region, which is why an equipped pet
 * reads as a flat blob: no matter how good the geometry is, a uniform fill has no form, no
 * material and no marking. This painter keeps the same two-region layout (so the geometry UVs are
 * unchanged) but gives each region:
 *
 * <ul>
 *   <li>a vertical light falloff, so the top of a face is a little brighter than the bottom —
 *       the cheapest form cue, and one that survives the blocky box UVs;</li>
 *   <li>a low-amplitude deterministic surface texture (fur grain, scale fleck), so the surface
 *       reads as a material instead of vinyl;</li>
 *   <li>a darker edge on the accent region, so two-tone parts (a shell, a stripe) separate.</li>
 * </ul>
 *
 * <p>It is still procedural, so it cannot reach the detail of a hand-painted texture — that needs
 * authored assets (see the cosmetics notes). It is a real step up from a solid fill, and it is
 * pure integer maths over {@link PngWriter}, so the output is byte-inspectable in a unit test.
 */
public final class PaintedAtlas {

    /** Brightest row in a region, as a fraction of the source colour. */
    private static final float TOP_LIGHT = 1.14f;
    /** Darkest row in a region. */
    private static final float BOTTOM_LIGHT = 0.74f;
    /** Peak-to-peak amplitude of the surface texture, as a fraction of the source colour. */
    private static final float TEXTURE_AMPLITUDE = 0.10f;

    private PaintedAtlas() {
    }

    /**
     * @param baseColor    main colour, 0xAARRGGBB (alpha forced opaque)
     * @param accentColor  secondary colour, 0xAARRGGBB (alpha forced opaque)
     * @param width        atlas width in pixels
     * @param height       atlas height in pixels
     * @param accentStartY first row painted in the accent colour
     * @param seed         surface-texture seed, so two species do not share the same grain
     */
    public static byte[] paint(int baseColor, int accentColor,
                               int width, int height, int accentStartY, int seed) {
        int[] argb = new int[width * height];
        int base = baseColor | 0xFF000000;
        int accent = accentColor | 0xFF000000;
        for (int y = 0; y < height; y++) {
            boolean inAccent = y >= accentStartY;
            int source = inAccent ? accent : base;
            int regionStart = inAccent ? accentStartY : 0;
            int regionEnd = inAccent ? height : accentStartY;
            int regionHeight = Math.max(1, regionEnd - regionStart);
            // 0 at the region's top, 1 at its bottom.
            float t = (y - regionStart) / (float) regionHeight;
            float light = TOP_LIGHT + (BOTTOM_LIGHT - TOP_LIGHT) * t;
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float grain = texture(x, y, seed);
                float factor = light * (1f + grain * TEXTURE_AMPLITUDE);
                argb[row + x] = scale(source, factor);
            }
        }
        return PngWriter.encode(width, height, argb);
    }

    /**
     * A deterministic, symmetric surface value in {@code [-1, 1]}. Two cheap integer hashes at
     * different frequencies give a grain that reads as fur/scale without a lookup table; the
     * value is quantised so it survives PNG compression as visible texture.
     */
    private static float texture(int x, int y, int seed) {
        int h1 = hash(x, y, seed);
        int h2 = hash(x * 3 + 1, y * 3 + 7, seed ^ 0x9E3779B9);
        // Average two frequencies so the grain is not a single regular pattern.
        float a = (h1 & 0xFF) / 127.5f - 1f;
        float b = (h2 & 0xFF) / 127.5f - 1f;
        return (a + b) * 0.5f;
    }

    private static int hash(int x, int y, int seed) {
        int h = seed;
        h = h * 31 + x;
        h = h * 31 + y;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        return h;
    }

    /** Scales a colour's channels by a factor, clamped to [0,255], alpha left opaque. */
    private static int scale(int color, float factor) {
        int r = clamp((int) (((color >> 16) & 0xFF) * factor));
        int g = clamp((int) (((color >> 8) & 0xFF) * factor));
        int b = clamp((int) ((color & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
