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
     * Renders a cape texture.
     *
     * @param baseColor cloth colour, 0xAARRGGBB
     * @param trimColor colour of the border band and the brand mark
     * @param branded   whether the Chimera mark is drawn on the cloth
     */
    public static byte[] paint(int baseColor, int trimColor, boolean branded) {
        int[] pixels = new int[TEXTURE_WIDTH * TEXTURE_HEIGHT];

        paintCapeRegion(pixels, baseColor, trimColor, branded);
        paintElytraRegion(pixels, baseColor, trimColor);

        return PngWriter.encode(TEXTURE_WIDTH, TEXTURE_HEIGHT, pixels);
    }

    /**
     * Fills the cape half of the sheet.
     *
     * <p>The border is drawn as a one-pixel band inset from the cloth rectangle, not as the outer
     * edge of the sheet: the game's capes have their trim just inside the silhouette, and painting
     * the very edge would put the band where it is never sampled.
     */
    private static void paintCapeRegion(int[] pixels, int baseColor, int trimColor, boolean branded) {
        fillRect(pixels, CLOTH_X - 1, CLOTH_Y - 1, CLOTH_WIDTH + 2, CLOTH_HEIGHT + 2, baseColor);

        // The bottom fold strip is a darker shade of the cloth so the cape does not read as a
        // flat slab when it swings out.
        int shade = shade(baseColor, 0.78f);
        fillRect(pixels, CLOTH_X - 1, CLOTH_Y + CLOTH_HEIGHT, CLOTH_WIDTH + 2, 2, shade);

        // Border band, inset by one pixel inside the cloth.
        int bandX = CLOTH_X;
        int bandY = CLOTH_Y;
        int bandW = CLOTH_WIDTH;
        int bandH = CLOTH_HEIGHT;
        fillRect(pixels, bandX, bandY, bandW, 1, trimColor);
        fillRect(pixels, bandX, bandY + bandH - 1, bandW, 1, trimColor);
        fillRect(pixels, bandX, bandY, 1, bandH, trimColor);
        fillRect(pixels, bandX + bandW - 1, bandY, 1, bandH, trimColor);

        if (branded) {
            paintMark(pixels, CLOTH_X + 3, CLOTH_Y + 5, 4, 6, trimColor);
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
