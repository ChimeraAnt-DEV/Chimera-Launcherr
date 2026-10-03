package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the default Steve texture layout, with no device.
 *
 * <p>The layout is painted onto a 64x64 atlas the renderer samples by region, so a coordinate typo
 * paints a limb in the wrong place and is invisible on a headless build machine. Painting the
 * table into an int grid and checking the texels the model actually samples is what catches that.
 */
public class SteveSkinLayoutTest {

    private static int[] paint() {
        int[] atlas = new int[SkinModel.ATLAS_SIZE * SkinModel.ATLAS_SIZE];
        for (SteveSkinLayout.Region r : SteveSkinLayout.regions()) {
            for (int y = r.v; y < r.v + r.h; y++) {
                for (int x = r.u; x < r.u + r.w; x++) {
                    atlas[y * SkinModel.ATLAS_SIZE + x] = r.color;
                }
            }
        }
        return atlas;
    }

    private static int texel(int[] atlas, int u, int v) {
        return atlas[v * SkinModel.ATLAS_SIZE + u];
    }

    @Test
    public void everyRegionStaysInsideTheAtlas() {
        for (SteveSkinLayout.Region r : SteveSkinLayout.regions()) {
            assertTrue("u in range: " + r.u, r.u >= 0 && r.u + r.w <= SkinModel.ATLAS_SIZE);
            assertTrue("v in range: " + r.v, r.v >= 0 && r.v + r.h <= SkinModel.ATLAS_SIZE);
            assertTrue("w positive", r.w > 0);
            assertTrue("h positive", r.h > 0);
        }
    }

    @Test
    public void theHeadHasHairOnTopAndBackAndSkinOnTheFace() {
        int[] atlas = paint();
        // The renderer samples the head base faces at these regions (see SkinModel's head box).
        assertEquals(SteveSkinLayout.HAIR, texel(atlas, 8, 0));   // TOP
        assertEquals(SteveSkinLayout.HAIR, texel(atlas, 24, 8));  // BACK
        assertEquals(SteveSkinLayout.SKIN, texel(atlas, 8, 8));   // FRONT
        assertEquals(SteveSkinLayout.SKIN, texel(atlas, 0, 8));   // RIGHT
        assertEquals(SteveSkinLayout.SKIN, texel(atlas, 16, 8));  // LEFT
    }

    @Test
    public void theFaceReadsAsAFaceWithEyes() {
        int[] atlas = paint();
        assertEquals(SteveSkinLayout.EYE_WHITE, texel(atlas, 9, 12));
        assertEquals(SteveSkinLayout.EYE_PUPIL, texel(atlas, 10, 12));
        assertEquals(SteveSkinLayout.EYE_WHITE, texel(atlas, 13, 12));
        assertEquals(SteveSkinLayout.EYE_PUPIL, texel(atlas, 14, 12));
    }

    @Test
    public void theHairOverlayCoversTheHeadSecondLayerExceptTheFaceBand() {
        int[] atlas = paint();
        // Overlay head faces: crown and back are hair...
        assertEquals(SteveSkinLayout.HAIR, texel(atlas, 40, 0));  // overlay TOP
        assertEquals(SteveSkinLayout.HAIR, texel(atlas, 32, 8));  // overlay RIGHT
        assertEquals(SteveSkinLayout.HAIR, texel(atlas, 56, 8));  // overlay BACK
        // ...and the front overlay is a fringe only, leaving the eye band transparent so the face
        // underneath shows through.
        assertEquals(SteveSkinLayout.HAIR, texel(atlas, 40, 8));   // fringe
        assertEquals(0, texel(atlas, 40, 12));                     // over the eyes: clear
    }

    @Test
    public void theRightArmIsSleeveWithAnExposedHand() {
        int[] atlas = paint();
        // arm_r front strip is at u=44, v=20, 4x12 (see SkinModel).
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 44, 20));
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 44, 27));
        assertEquals(SteveSkinLayout.SKIN, texel(atlas, 44, 28));
        assertEquals(SteveSkinLayout.SKIN, texel(atlas, 44, 31));
    }

    @Test
    public void theLeftArmIsSleeveWithAnExposedHand() {
        int[] atlas = paint();
        // arm_l front strip is at u=36, v=52.
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 36, 52));
        assertEquals(SteveSkinLayout.SKIN, texel(atlas, 36, 60));
    }

    @Test
    public void theLegsAreTrousersWithBootsAtTheBottom() {
        int[] atlas = paint();
        // leg_r front strip is at u=4, v=20; leg_l at u=20, v=52.
        assertEquals(SteveSkinLayout.TROUSERS, texel(atlas, 4, 20));
        assertEquals(SteveSkinLayout.SHOES, texel(atlas, 4, 28));
        assertEquals(SteveSkinLayout.TROUSERS, texel(atlas, 20, 52));
        assertEquals(SteveSkinLayout.SHOES, texel(atlas, 20, 60));
    }

    @Test
    public void theBodyIsTheShirtAllRound() {
        int[] atlas = paint();
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 20, 20)); // FRONT
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 16, 20)); // RIGHT
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 28, 20)); // LEFT
        assertEquals(SteveSkinLayout.SHIRT, texel(atlas, 32, 20)); // BACK
    }

    /**
     * The limb regions must land in the UV boxes the model samples, not merely somewhere on the
     * atlas. A strip painted a few pixels off would render on the wrong face.
     */
    @Test
    public void theArmAndLegRegionsMatchTheModelsUvBoxes() {
        int[] atlas = paint();
        for (SkinModel.Box box : SkinModel.boxes()) {
            String id = box.id;
            if (!id.startsWith("arm_") && !id.startsWith("leg_")) continue;
            for (SkinModel.Face face : new SkinModel.Face[]{SkinModel.Face.FRONT}) {
                SkinModel.Uv uv = box.baseUv(face);
                // The model samples the top-left of the strip; it must be painted, not transparent.
                assertTrue(id + " " + face + " is painted",
                        texel(atlas, uv.u, uv.v) != 0);
                // And the very last row must be painted too, so no strip is short.
                assertTrue(id + " " + face + " bottom is painted",
                        texel(atlas, uv.u, uv.v + uv.h - 1) != 0);
            }
        }
    }
}
