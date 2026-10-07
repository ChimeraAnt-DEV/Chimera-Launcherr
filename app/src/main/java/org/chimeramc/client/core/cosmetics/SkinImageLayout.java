package org.chimeramc.client.core.cosmetics;

/**
 * The verified {@code SerializedSkinRef} field layout, recovered by disassembling the shipped
 * 1.26.60.28 {@code libminecraftpe.so}.
 *
 * <p>This is the map a cape-pixel substitution needs: the renderer reads a player's cape pixels
 * from the {@code mce::Image} embedded in {@code SerializedSkinRef}, so writing a custom cape means
 * reaching the right member of that struct. The offsets were recovered from the accessor bodies —
 * each {@code SerializedSkinRef::getX()} logs its own name and returns {@code this + offset}, so
 * the constant-folding {@code add x0, x19, #imm} before each {@code ret} is the field's offset.
 *
 * <p><b>What is proven and what is not.</b> The offsets here are read off the real binary and are
 * exact. What is <em>not</em> recoverable from a stripped binary is the internal layout of
 * {@code mce::Image} — where inside its {@link #IMAGE_SIZE} bytes the pixel-buffer pointer sits —
 * because the class exports no method symbols and emits no diagnostic strings. So this class
 * describes the skin struct precisely and the image struct only by its size. A substitution that
 * writes pixels must therefore either recover the {@code mce::Image} buffer field or replace the
 * whole image struct, which is why the native swap primitive is a fixed-size struct copy rather
 * than a pointer poke.
 *
 * <p>Pure constants and arithmetic, so the layout math is unit-testable.
 */
public final class SkinImageLayout {

    /**
     * {@code mce::Image} is 0x30 bytes. Proven, not assumed: {@code getImageData()} sits at
     * {@code +0x78} and {@code getCapeImageData()} at {@code +0xa8}, and nothing in between —
     * exactly one image.
     */
    public static final int IMAGE_SIZE = 0x30;

    /** {@code SerializedSkinRef::getImageData()} — the base skin texture ({@code mce::Image}). */
    public static final int IMAGE_DATA = 0x78;

    /** {@code SerializedSkinRef::getCapeImageData()} — the cape texture ({@code mce::Image}). */
    public static final int CAPE_IMAGE_DATA = 0xa8;

    /** {@code SerializedSkinRef::getAnimatedImageData()} — the first Persona animated strip. */
    public static final int ANIMATED_IMAGE_DATA = 0xd8;

    /** {@code SerializedSkinRef::getGeometryData()} — the geometry JSON string. */
    public static final int GEOMETRY_DATA = 0x100;

    /** {@code SerializedSkinRef::getGeometryDataMutable()} — the mutable geometry. */
    public static final int GEOMETRY_DATA_MUTABLE = 0x120;

    /** {@code SerializedSkinRef::getAnimationData()} — the animation JSON string. */
    public static final int ANIMATION_DATA = 0x130;

    /** {@code SerializedSkinRef::getCapeId()} — the cape texture id string. */
    public static final int CAPE_ID = 0x148;

    /** {@code SerializedSkinRef::getSkinColor()} — the tint colour. */
    public static final int SKIN_COLOR = 0x1a8;

    /** {@code SerializedSkinRef::getIsTrustedSkinFlag()} — the trusted-skin flag. */
    public static final int TRUSTED_SKIN_FLAG = 0x1b8;

    /** {@code SerializedSkinRef::getProfileHash()} — the profile hash. */
    public static final int PROFILE_HASH = 0x1c0;

    private SkinImageLayout() {
    }

    /**
     * The address of the cape image inside a skin struct, or 0 when the base pointer is null.
     *
     * <p>Pure so the arithmetic is testable without a device; the native side performs the same
     * add on the real pointer.
     */
    public static long capeImageAddress(long skinRefAddress) {
        if (skinRefAddress == 0L) return 0L;
        return skinRefAddress + CAPE_IMAGE_DATA;
    }

    /** True when two image members do not overlap and the layout is self-consistent. */
    public static boolean layoutIsConsistent() {
        return CAPE_IMAGE_DATA > IMAGE_DATA
                && CAPE_IMAGE_DATA - IMAGE_DATA == IMAGE_SIZE
                && ANIMATED_IMAGE_DATA - CAPE_IMAGE_DATA == IMAGE_SIZE;
    }
}
