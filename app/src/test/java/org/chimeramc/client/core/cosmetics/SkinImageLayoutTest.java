package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The verified {@code SerializedSkinRef} layout — the map a cape-pixel substitution needs. Uses the
 * offsets recovered from the shipped 1.26.60.28 binary, so a typo in an offset or the image size is
 * caught here rather than only on a device.
 */
public class SkinImageLayoutTest {

    @Test
    public void theImageMembersAreOneImageStrideApart() {
        // getImageData at +0x78 and getCapeImageData at +0xa8 are exactly 0x30 apart, proving the
        // mce::Image type is 0x30 bytes and that nothing else sits between them.
        assertEquals(0x30, SkinImageLayout.IMAGE_SIZE);
        assertEquals(SkinImageLayout.IMAGE_DATA + SkinImageLayout.IMAGE_SIZE,
                SkinImageLayout.CAPE_IMAGE_DATA);
        assertEquals(SkinImageLayout.CAPE_IMAGE_DATA + SkinImageLayout.IMAGE_SIZE,
                SkinImageLayout.ANIMATED_IMAGE_DATA);
    }

    @Test
    public void theVerifiedOffsetsAreExact() {
        assertEquals(0x78, SkinImageLayout.IMAGE_DATA);
        assertEquals(0xa8, SkinImageLayout.CAPE_IMAGE_DATA);
        assertEquals(0xd8, SkinImageLayout.ANIMATED_IMAGE_DATA);
        assertEquals(0x100, SkinImageLayout.GEOMETRY_DATA);
        assertEquals(0x130, SkinImageLayout.ANIMATION_DATA);
        assertEquals(0x148, SkinImageLayout.CAPE_ID);
        assertTrue("layout consistent", SkinImageLayout.layoutIsConsistent());
    }

    @Test
    public void theCapeImageAddressIsBasePlusOffset() {
        assertEquals(0x1000L + 0xa8, SkinImageLayout.capeImageAddress(0x1000L));
        assertEquals(NativeCosmeticsBridge.capeImageAddress(0x2000L), 0x2000L + 0xa8);
    }

    @Test
    public void aNullSkinAddressYieldsNoAddress() {
        assertEquals(0L, SkinImageLayout.capeImageAddress(0L));
    }

    /** The swap must refuse anything that could corrupt memory. */
    @Test
    public void theSwapRefusesNullAndShortInputs() {
        assertFalse(NativeCosmeticsBridge.swapCapeImage(0L, new byte[0x30]));
        assertFalse(NativeCosmeticsBridge.swapCapeImage(0x1000L, null));
        assertFalse("an image shorter than the struct is refused",
                NativeCosmeticsBridge.swapCapeImage(0x1000L, new byte[0x10]));
        // Without the native library even a well-formed call is a safe no-op.
        assertFalse(NativeCosmeticsBridge.swapCapeImage(0x1000L, new byte[0x30]));
    }
}
