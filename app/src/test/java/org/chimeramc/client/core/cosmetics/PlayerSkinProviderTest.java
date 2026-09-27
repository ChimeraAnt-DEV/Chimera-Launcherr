package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for the skin atlas normalisation rule.
 *
 * <p>This is the half of {@link PlayerSkinProvider} that can be checked without a device: the
 * decode and the lookup need a Context and a Bitmap, but the sizing decision is arithmetic, and
 * getting it wrong is invisible until a real skin renders with its limbs in the wrong place.
 */
public class PlayerSkinProviderTest {

    @Test
    public void aLegacy64x32SkinKeepsItsHeightAndSitsInTheTopHalf() {
        // Filling 64x64 instead would double the height and move every region, so the arms would
        // sample the hat overlay's texels.
        assertArrayEquals(new int[]{64, 32}, PlayerSkinProvider.atlasDrawSize(64, 32));
    }

    @Test
    public void aModern64x64SkinIsUnchanged() {
        assertArrayEquals(new int[]{64, 64}, PlayerSkinProvider.atlasDrawSize(64, 64));
    }

    @Test
    public void anHd128x128SkinIsHalved() {
        assertArrayEquals(new int[]{64, 64}, PlayerSkinProvider.atlasDrawSize(128, 128));
    }

    @Test
    public void anHd128x64SkinHalvesBothAxesIndependently() {
        // A wide HD skin must not be stretched to the atlas height.
        assertArrayEquals(new int[]{64, 32}, PlayerSkinProvider.atlasDrawSize(128, 64));
    }

    @Test
    public void a256x128SkinQuarters() {
        assertArrayEquals(new int[]{64, 32}, PlayerSkinProvider.atlasDrawSize(256, 128));
    }

    @Test
    public void aSmallSkinIsNeverScaledUp() {
        assertArrayEquals(new int[]{32, 16}, PlayerSkinProvider.atlasDrawSize(32, 16));
    }

    @Test
    public void aNonMultipleWidthStillLandsInsideTheAtlas() {
        int[] size = PlayerSkinProvider.atlasDrawSize(100, 50);
        assertEquals(true, size[0] <= SkinModel.ATLAS_SIZE);
        assertEquals(true, size[1] <= SkinModel.ATLAS_SIZE);
        assertEquals(true, size[0] > 0);
        assertEquals(true, size[1] > 0);
    }

    @Test
    public void aDegenerateSizeFallsBackToTheFullAtlas() {
        assertArrayEquals(new int[]{64, 64}, PlayerSkinProvider.atlasDrawSize(0, 0));
        assertArrayEquals(new int[]{64, 64}, PlayerSkinProvider.atlasDrawSize(-8, 32));
    }
}
