package org.chimeramc.client.core.cosmetics;

/**
 * The weave rules shared by the in-game cape texture and the launcher preview.
 *
 * <p>Both need to answer the same question — "what colour is the cloth at this point?" — but one
 * paints a 10x16 texture and the other shades a projected mesh. Keeping the rule in one pure place
 * is what stops the preview and the pack from drifting: a player who picks "Wave" must see a wave
 * in the menu and a wave on their character.
 *
 * <p>All coordinates are normalised 0..1 across the cloth's back panel, so the rule is independent
 * of the texture resolution and of the mesh's column count.
 */
public final class CapePatterns {

    /**
     * The "OF" monogram, a 6x5 blocky glyph. '#' is ink. Shared by the in-game texture painter and
     * the launcher preview so the two cannot draw a different mark.
     */
    private static final String[] OPTIFINE_MONOGRAM = {
            "###.##",
            "#.#.#.",
            "#.#.##",
            "#.#.#.",
            "###.#."
    };

    /** The monogram is drawn centred, occupying this fraction of the cloth panel. */
    private static final float OF_U0 = 0.15f, OF_U1 = 0.85f, OF_V0 = 0.38f, OF_V1 = 0.66f;

    /**
     * Whether the point is inside the Optifine "OF" monogram, in normalised cloth coordinates.
     *
     * <p>The monogram is lettering, not a weave, so it is deliberately not part of
     * {@link #colorAt}: both renderers ask this separately and paint the accent colour where it is
     * true, which keeps the letters a solid, readable colour instead of being re-shaded as cloth.
     */
    public static boolean optifineMonogramAt(float u, float v) {
        if (u < OF_U0 || u >= OF_U1 || v < OF_V0 || v >= OF_V1) return false;
        int gx = (int) ((u - OF_U0) / (OF_U1 - OF_U0) * OPTIFINE_MONOGRAM[0].length());
        int gy = (int) ((v - OF_V0) / (OF_V1 - OF_V0) * OPTIFINE_MONOGRAM.length);
        if (gy < 0 || gy >= OPTIFINE_MONOGRAM.length) return false;
        String row = OPTIFINE_MONOGRAM[gy];
        if (gx < 0 || gx >= row.length()) return false;
        return row.charAt(gx) == '#';
    }

    private CapePatterns() {
    }

    /**
     * The cloth colour at a normalised point.
     *
     * @param pattern the weave
     * @param u       0..1 across the cloth, left to right
     * @param v       0..1 down the cloth, top to bottom
     * @param base    the base cloth colour
     * @param accent  the pattern's secondary colour
     * @return the ARGB colour at that point
     */
    public static int colorAt(CosmeticCatalog.CapePattern pattern, float u, float v,
                              int base, int accent) {
        if (pattern == null) return base;
        switch (pattern) {
            case SOLID:
                return base;
            case VERTICAL_STRIPES:
                return (((int) (u * 10)) % 3 == 0) ? accent : base;
            case HORIZONTAL_STRIPES:
                return (((int) (v * 16)) % 3 == 0) ? accent : base;
            case GRADIENT:
                return lerp(base, accent, clamp01(v));
            case SPLIT:
                return u < 0.5f ? base : accent;
            case GRID:
                return (((int) (u * 10)) % 4 == 0 || ((int) (v * 16)) % 4 == 0) ? accent : base;
            case CHECKER:
                return ((((int) (u * 10)) + ((int) (v * 16))) & 1) == 0 ? accent : base;
            case CHEVRON: {
                int d = Math.abs((int) (u * 10) - 4);
                return (((int) (v * 16) + d) % 4 == 0) ? accent : base;
            }
            case HORIZON:
                return v < 0.5f ? base : shade(accent, 0.9f);
            case WAVE: {
                double w = Math.sin(u * Math.PI * 2.0);
                float line = 0.5f + (float) (w * 0.2);
                return Math.abs(v - line) < 0.09f ? accent : base;
            }
            case CAMO: {
                int h = (((int) (v * 16)) * 31 + ((int) (u * 10)) * 17) % 11;
                if (h < 3) return accent;
                if (h < 5) return shade(accent, 0.85f);
                return base;
            }
            case STAR: {
                float dx = Math.abs(u - 0.5f);
                float dy = Math.abs(v - 0.5f);
                if ((dx <= 0.12f && dy <= 0.3f) || (dy <= 0.12f && dx <= 0.3f)) return accent;
                return base;
            }
            default:
                return base;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** Pure ARGB scaling toward black, preserving alpha. */
    static int shade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.round(((color >>> 16) & 0xFF) * factor);
        int g = Math.round(((color >>> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int lerp(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
