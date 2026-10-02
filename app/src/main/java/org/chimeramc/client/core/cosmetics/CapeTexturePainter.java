package org.chimeramc.client.core.cosmetics;

/**
 * Paints the cloth of a Bedrock cape into a 64x32 texture.
 *
 * <p>Bedrock's cape and elytra share one 64x32 image. The area the player actually sees when a
 * cape hangs on their back is the 10x16 rectangle at x=12, y=1; around it sit the edge strips
 * (top y=0, bottom y=17..22, and the two 1px side columns) which are sampled when the cape
 * swings and folds. The right half of the image (x >= 32) is the elytra wings.
 *
 * <p>This class fills the whole cape region including those strips, because a texture that only
 * paints the flat front shows unpainted black edges the moment the cape moves. For the elytra it
 * writes the same palette into the wing rectangle so a cape equipped over an elytra does not
 * flash a second, unrelated colour.
 *
 * <p>Pure integer maths and {@link PngWriter}, so the output is byte-inspectable in a unit test
 * without Android or a device.
 */
public final class CapeTexturePainter {

    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 32;

    /** Visible cloth, and the strips the game samples when the cape sways. */
    static final int CLOTH_X = 12;
    static final int CLOTH_Y = 1;
    static final int CLOTH_WIDTH = 10;
    static final int CLOTH_HEIGHT = 16;

    private CapeTexturePainter() {
    }

    /**
     * Renders a cape texture with a plain weave.
     *
     * @param baseColor cloth colour, 0xAARRGGBB
     * @param trimColor colour of the border band and the brand mark
     * @param branded   whether the Chimera mark is drawn on the cloth
     */
    public static byte[] paint(int baseColor, int trimColor, boolean branded) {
        return paint(baseColor, trimColor, trimColor, CosmeticCatalog.CapePattern.SOLID, branded);
    }

    /**
     * Renders a cape texture with a pattern.
     *
     * @param baseColor   cloth colour, 0xAARRGGBB
     * @param trimColor   colour of the border band and the brand mark
     * @param accentColor secondary colour the pattern weave uses
     * @param pattern     the weave; {@code null} is treated as solid
     * @param branded     whether the Chimera mark is drawn on the cloth
     */
    public static byte[] paint(int baseColor, int trimColor, int accentColor,
                               CosmeticCatalog.CapePattern pattern, boolean branded) {
        int[] pixels = new int[TEXTURE_WIDTH * TEXTURE_HEIGHT];

        paintCapeRegion(pixels, baseColor, trimColor, accentColor, pattern, branded);
        paintElytraRegion(pixels, baseColor, trimColor);

        return PngWriter.encode(TEXTURE_WIDTH, TEXTURE_HEIGHT, pixels);
    }

    /**
     * Fills the cape half of the sheet.
     *
     * <p>The cape is a thin 10x16 box, so the sheet is laid out as faces rather than one flat
     * rectangle: the visible back panel is the 10x16 at (12,1); the front panel (against the
     * player) is the 10x16 at (1,1); the side strips are the single columns at x=0 and x=11; and
     * the top and bottom edges are the single rows at y=0. Every face the game can sample while
     * the cape swings is painted, because leaving one transparent makes the cape show holes and
     * read as a flapping paper sheet instead of cloth.
     */
    private static void paintCapeRegion(int[] pixels, int baseColor, int trimColor,
                                        int accentColor, CosmeticCatalog.CapePattern pattern,
                                        boolean branded) {
        // The whole cape box first, in a mid shade: this covers the top/bottom edge rows and the
        // two side columns, which are only sampled at a glancing angle but are visible then.
        fillRect(pixels, 0, 0, 22, 17, shade(baseColor, 0.85f));

        // Front panel, against the player's back: darker so the cape has two distinct sides.
        fillRect(pixels, 1, 1, 10, 16, shade(baseColor, 0.72f));

        // Visible back panel: the main artwork the player sees on their character, now carrying
        // the weave. Painting the pattern here rather than only the flat colour is what makes the
        // hundred-plus cape variants actually look different in-game.
        fillRect(pixels, 12, 1, 10, 16, baseColor);
        paintPattern(pixels, CLOTH_X, CLOTH_Y, CLOTH_WIDTH, CLOTH_HEIGHT, baseColor, accentColor,
                pattern);

        // Border band, inset by one pixel inside the back panel. The game's capes have their trim
        // just inside the silhouette, so painting the very edge would put the band where it is
        // never sampled.
        fillRect(pixels, CLOTH_X, CLOTH_Y, CLOTH_WIDTH, 1, trimColor);
        fillRect(pixels, CLOTH_X, CLOTH_Y + CLOTH_HEIGHT - 1, CLOTH_WIDTH, 1, trimColor);
        fillRect(pixels, CLOTH_X, CLOTH_Y, 1, CLOTH_HEIGHT, trimColor);
        fillRect(pixels, CLOTH_X + CLOTH_WIDTH - 1, CLOTH_Y, 1, CLOTH_HEIGHT, trimColor);

        if (branded) {
            paintMark(pixels, CLOTH_X + 3, CLOTH_Y + 5, 4, 6, trimColor);
        }
    }

    /**
     * Paints a pattern weave into the cloth rectangle.
     *
     * <p>The per-pixel rule lives in {@link CapePatterns} so the texture and the launcher preview
     * cannot disagree about what a weave looks like. A weave never touches the outermost row or
     * column, which the trim band owns.
     */
    private static void paintPattern(int[] pixels, int x, int y, int width, int height,
                                     int baseColor, int accentColor,
                                     CosmeticCatalog.CapePattern pattern) {
        if (pattern == null || pattern == CosmeticCatalog.CapePattern.SOLID) return;
        for (int row = 1; row < height - 1; row++) {
            float v = row / (float) (height - 1);
            for (int col = 1; col < width - 1; col++) {
                float u = col / (float) (width - 1);
                int color = CapePatterns.colorAt(pattern, u, v, baseColor, accentColor);
                if (color != baseColor) {
                    pixels[(y + row) * TEXTURE_WIDTH + (x + col)] = color;
                }
            }
        }
    }

    /**
     * Fills the elytra wing area with the same palette.
     *
     * <p>The wings occupy the right half of the sheet. They are only sampled when an elytra is
     * worn, and matching the cape keeps the two from clashing if the player has both.
     */
    private static void paintElytraRegion(int[] pixels, int baseColor, int trimColor) {
        fillRect(pixels, 32, 0, 32, 16, baseColor);
        // Wing outline so the two wings stay visually separate in flight.
        fillRect(pixels, 32, 0, 32, 1, trimColor);
        fillRect(pixels, 47, 0, 1, 16, trimColor);
    }

    /**
     * Draws the brand mark as a small blocky glyph.
     *
     * <p>Deliberately a filled rectangle-and-notch shape rather than the launcher's vector ant:
     * at 4x6 pixels inside a 64x32 sheet there is no room for detail, and a shape that reads as a
     * deliberate mark at that size is worth more than an unreadable attempt at the real logo.
     */
    private static void paintMark(int[] pixels, int x, int y, int width, int height, int color) {
        fillRect(pixels, x, y, width, 1, color);
        fillRect(pixels, x, y + height - 1, width, 1, color);
        fillRect(pixels, x, y, 1, height, color);
        fillRect(pixels, x + width - 1, y, 1, height, color);
        // A single notch through the middle so the mark is not a plain box.
        fillRect(pixels, x + width / 2, y, 1, height, color);
    }

    private static void fillRect(int[] pixels, int x, int y, int width, int height, int color) {
        for (int py = y; py < y + height; py++) {
            if (py < 0 || py >= TEXTURE_HEIGHT) continue;
            for (int px = x; px < x + width; px++) {
                if (px < 0 || px >= TEXTURE_WIDTH) continue;
                pixels[py * TEXTURE_WIDTH + px] = color;
            }
        }
    }

    /** Scales a colour's RGB toward black, preserving alpha. */
    static int shade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.round(((color >>> 16) & 0xFF) * factor);
        int g = Math.round(((color >>> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
