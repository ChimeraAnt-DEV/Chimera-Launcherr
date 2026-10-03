package org.chimeramc.client.core.cosmetics;

/**
 * Paints a flat two-colour atlas: a base-colour region above an accent-colour region.
 *
 * <p>Shared by the accessory and pet geometry, which are both deliberately flat-shaded — the
 * models are blocky, so a solid fill is the right look and it keeps each texture a few hundred
 * bytes rather than a painted image. A geometry's cube UVs point base cubes at the region starting
 * at {@code (0, 0)} and accent cubes at the region starting at {@code (0, accentStartY)}.
 *
 * <p>Pure integer maths and {@link PngWriter}, so the output is byte-inspectable in a unit test
 * without Android or a device.
 */
public final class FlatColorAtlas {

    private FlatColorAtlas() {
    }

    /**
     * @param baseColor    main colour, 0xAARRGGBB (alpha forced opaque)
     * @param accentColor  secondary colour, 0xAARRGGBB (alpha forced opaque)
     * @param width        atlas width in pixels
     * @param height       atlas height in pixels
     * @param accentStartY first row painted in the accent colour
     */
    public static byte[] paint(int baseColor, int accentColor,
                               int width, int height, int accentStartY) {
        int[] argb = new int[width * height];
        int base = baseColor | 0xFF000000;
        int accent = accentColor | 0xFF000000;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int color = y < accentStartY ? base : accent;
            for (int x = 0; x < width; x++) {
                argb[row + x] = color;
            }
        }
        return PngWriter.encode(width, height, argb);
    }
}
