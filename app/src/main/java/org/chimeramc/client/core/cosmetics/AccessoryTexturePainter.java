package org.chimeramc.client.core.cosmetics;

/**
 * Paints the 64x64 atlas {@link AccessoryGeometry} addresses: a shaded base-colour region and a
 * shaded accent-colour region.
 *
 * <p>The geometry's cube UVs point every base cube at the region at {@code (0,0)} and every accent
 * cube at {@code (0,32)}. Rather than a flat fill (which reads as a plastic blob up close), each
 * region gets the form shading and surface grain from {@link PaintedAtlas}, so a hat has a lit top
 * and a shaded underside and the surface reads as fabric rather than vinyl.
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
        return PaintedAtlas.paint(baseColor, accentColor,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, AccessoryGeometry.UV_ACCENT_Y,
                baseColor ^ (accentColor * 31));
    }
}
