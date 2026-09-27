package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Geometry tests for the player model and its UV table.
 *
 * <p>The mistakes these pin are the ones that are invisible until they are rendered: a UV that
 * runs off the atlas, a texture region that was never assigned, and a visible-face test that
 * disagrees with the projection. Each is cheap to assert here and expensive to spot on screen.
 */
public class SkinModelTest {

    @Test
    public void everyFaceOfEveryBoxHasABaseUv() {
        for (SkinModel.Box box : SkinModel.boxes()) {
            for (SkinModel.Face face : SkinModel.Face.values()) {
                assertNotNull(box.id + " " + face + " has no base UV",
                        box.baseUv(face));
            }
        }
    }

    @Test
    public void everyUvStaysInsideTheAtlas() {
        for (SkinModel.Box box : SkinModel.boxes()) {
            for (SkinModel.Face face : SkinModel.Face.values()) {
                SkinModel.Uv uv = box.baseUv(face);
                assertTrue(box.id + " " + face + " UV out of atlas: "
                                + uv.u + "," + uv.v + " " + uv.w + "x" + uv.h,
                        SkinModel.uvWithinAtlas(uv));
                SkinModel.Uv over = box.overlayUv(face);
                if (over != null) {
                    assertTrue(box.id + " " + face + " overlay UV out of atlas",
                            SkinModel.uvWithinAtlas(over));
                }
            }
        }
    }

    @Test
    public void theHeadUsesTheStandardFaceRegion() {
        // If this ever moves, every skin in the preview shows the wrong part of the atlas.
        SkinModel.Box head = null;
        for (SkinModel.Box box : SkinModel.boxes()) {
            if ("head".equals(box.id)) head = box;
        }
        assertNotNull(head);
        SkinModel.Uv front = head.baseUv(SkinModel.Face.FRONT);
        assertEquals(8, front.u);
        assertEquals(8, front.v);
        assertEquals(8, front.w);
        assertEquals(8, front.h);
    }

    @Test
    public void modelIsThirtyTwoPixelsTall() {
        // 32 model pixels is 1.8 blocks, so the scale constant must follow.
        assertEquals(32f, SkinModel.heightPixels(), 1e-5f);
        assertEquals(1.8f / 32f, SkinModel.PIXELS_TO_BLOCKS, 1e-6f);
    }

    @Test
    public void theHeadSitsOnTopOfTheBody() {
        SkinModel.Box head = boxById("head");
        SkinModel.Box body = boxById("body");
        assertEquals("head must rest on the body", body.maxY(), head.minY(), 1e-4f);
    }

    @Test
    public void limbsHangBelowTheBody() {
        SkinModel.Box body = boxById("body");
        for (String id : new String[]{"arm_r", "arm_l", "leg_r", "leg_l"}) {
            SkinModel.Box limb = boxById(id);
            assertTrue(id + " must start at the top of the body", limb.maxY() <= body.maxY() + 1e-4f);
        }
    }

    @Test
    public void frontFaceIsVisibleFromTheDefaultView() {
        // The preview's default yaw looks at the front, so the face must not be culled.
        assertTrue(SkinModel.faceVisible(SkinModel.Face.FRONT, 0f, 0f));
        assertFalse(SkinModel.faceVisible(SkinModel.Face.BACK, 0f, 0f));
    }

    @Test
    public void turningTheCameraSwapsWhichFacesShow() {
        // Yaw is clockwise, so half a turn must reveal the back and hide the front.
        assertTrue(SkinModel.faceVisible(SkinModel.Face.BACK, 180f, 0f));
        assertFalse(SkinModel.faceVisible(SkinModel.Face.FRONT, 180f, 0f));
    }

    @Test
    public void pitchingUpRevealsTheTopAndHidesTheBottom() {
        assertTrue(SkinModel.faceVisible(SkinModel.Face.TOP, 0f, 30f));
        assertFalse(SkinModel.faceVisible(SkinModel.Face.BOTTOM, 0f, 30f));
    }

    @Test
    public void projectionKeepsThePointOnScreenAndMovesWithYaw() {
        float[] a = new float[3];
        float[] b = new float[3];
        SkinModel.project(0f, 0f, 10f, 0f, 0f, 1f, 100f, 100f, a);
        SkinModel.project(0f, 0f, 10f, 90f, 0f, 1f, 100f, 100f, b);
        // A point straight ahead maps to the origin; a quarter turn swings it sideways.
        assertEquals(100f, a[0], 1e-3f);
        assertEquals(100f, a[1], 1e-3f);
        assertTrue("yaw must move the projected point horizontally",
                Math.abs(b[0] - a[0]) > 1f);
    }

    @Test
    public void projectionRaisesThePointOnScreenWhenItRisesInTheWorld() {
        float[] low = new float[3];
        float[] high = new float[3];
        SkinModel.project(0f, 0f, 10f, 0f, 0f, 1f, 100f, 200f, low);
        SkinModel.project(0f, 5f, 10f, 0f, 0f, 1f, 100f, 200f, high);
        // Screen Y grows downward, so a higher world point must produce a smaller screen Y.
        assertTrue("a higher point must draw higher on screen", high[1] < low[1]);
    }

    @Test
    public void everyBoxHasPositiveVolume() {
        for (SkinModel.Box box : SkinModel.boxes()) {
            assertTrue(box.id + " width", box.w > 0f);
            assertTrue(box.id + " height", box.h > 0f);
            assertTrue(box.id + " depth", box.d > 0f);
        }
    }

    /**
     * The left limb regions must match the standard player-skin layout exactly.
     *
     * <p>These are the regions that were wrong: the left arm's base left/back strips and its
     * whole overlay row, and the left leg's base left/back strips all pointed at the neighbouring
     * strip, so the preview sampled the wrong part of the atlas on one side of the body. The
     * values are the documented 64x64 layout; a wrong one does not throw, it just renders the
     * wrong clothing.
     */
    @Test
    public void leftArmMatchesTheStandardSkinLayout() {
        SkinModel.Box arm = boxById("arm_l");
        assertSideUv(arm.baseUv(SkinModel.Face.RIGHT), 32, 52);
        assertSideUv(arm.baseUv(SkinModel.Face.FRONT), 36, 52);
        assertSideUv(arm.baseUv(SkinModel.Face.LEFT), 40, 52);
        assertSideUv(arm.baseUv(SkinModel.Face.BACK), 44, 52);
        assertCapUv(arm.baseUv(SkinModel.Face.TOP), 36, 48);
        assertCapUv(arm.baseUv(SkinModel.Face.BOTTOM), 40, 48);
        assertSideUv(arm.overlayUv(SkinModel.Face.RIGHT), 48, 52);
        assertSideUv(arm.overlayUv(SkinModel.Face.FRONT), 52, 52);
        assertSideUv(arm.overlayUv(SkinModel.Face.LEFT), 56, 52);
        assertSideUv(arm.overlayUv(SkinModel.Face.BACK), 60, 52);
    }

    @Test
    public void leftLegMatchesTheStandardSkinLayout() {
        SkinModel.Box leg = boxById("leg_l");
        assertSideUv(leg.baseUv(SkinModel.Face.RIGHT), 16, 52);
        assertSideUv(leg.baseUv(SkinModel.Face.FRONT), 20, 52);
        assertSideUv(leg.baseUv(SkinModel.Face.LEFT), 24, 52);
        assertSideUv(leg.baseUv(SkinModel.Face.BACK), 28, 52);
        assertCapUv(leg.baseUv(SkinModel.Face.TOP), 20, 48);
        assertCapUv(leg.baseUv(SkinModel.Face.BOTTOM), 24, 48);
    }

    /**
     * The side strips around a limb must advance left-to-right in the documented order
     * (right/front/left/back), each by one strip width. A transposed pair is the classic silent
     * mistake — it swaps two faces' clothing and nothing else complains.
     */
    @Test
    public void limbSideStripsAdvanceInDocumentedOrder() {
        for (String id : new String[]{"arm_r", "arm_l", "leg_r", "leg_l"}) {
            SkinModel.Box box = boxById(id);
            int uRight = box.baseUv(SkinModel.Face.RIGHT).u;
            int uFront = box.baseUv(SkinModel.Face.FRONT).u;
            int uLeft = box.baseUv(SkinModel.Face.LEFT).u;
            int uBack = box.baseUv(SkinModel.Face.BACK).u;
            int step = (int) box.w;
            assertEquals(id + " front must follow right", uRight + step, uFront);
            assertEquals(id + " left must follow front", uFront + step, uLeft);
            assertEquals(id + " back must follow left", uLeft + step, uBack);
            assertEquals("all strips share a row", box.baseUv(SkinModel.Face.RIGHT).v,
                    box.baseUv(SkinModel.Face.BACK).v);
        }
    }

    private static void assertSideUv(SkinModel.Uv uv, int u, int v) {
        assertNotNull(uv);
        assertEquals("u", u, uv.u);
        assertEquals("v", v, uv.v);
        assertEquals(4, uv.w);
        assertEquals(12, uv.h);
    }

    private static void assertCapUv(SkinModel.Uv uv, int u, int v) {
        assertNotNull(uv);
        assertEquals("u", u, uv.u);
        assertEquals("v", v, uv.v);
        assertEquals(4, uv.w);
        assertEquals(4, uv.h);
    }

    private static SkinModel.Box boxById(String id) {
        for (SkinModel.Box box : SkinModel.boxes()) {
            if (box.id.equals(id)) return box;
        }
        throw new AssertionError("no box " + id);
    }
}
