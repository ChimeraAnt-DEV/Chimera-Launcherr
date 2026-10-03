package org.chimeramc.client.core.cosmetics;

/**
 * Paints the 64x64 atlas {@link AccessoryGeometry} addresses: a flat base-colour region and a flat
 * accent-colour region.
 *
 * <p>The accessory geometry is deliberately flat-shaded — the model is blocky, so a solid fill is
 * the right look and it keeps the texture a few hundred bytes rather than a painted image. The
 * geometry's cube UVs point every base cube at the region at {@code (0,0)} and every accent cube
 * at {@code (0,32)}, so two flat rectangles are all the texture needs to be.
 *
 * <p>Pure integer maths and {@link PngWriter}, so the output is byte-inspectable in a unit test
 * without Android or a device.
 */
public final class AccessoryTexturePainter {

    public static final int TEXTURE_WIDTH = AccessoryGeometry.TEXTURE_WIDTH;
    public static final int TEXTURE_HEIGHT = AccessoryGeometry.TEXTURE_HEIGHT;

    private AccessoryTexturePainter() {
    }

    /**
     * @param baseColor   the accessory's main colour, 0xAARRGGBB
     * @param accentColor the two-tone colour for brims, straps and trim, 0xAARRGGBB
     */
    public static byte[] paint(int baseColor, int accentColor) {
        return FlatColorAtlas.paint(baseColor, accentColor,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, AccessoryGeometry.UV_ACCENT_Y);
    }
}
