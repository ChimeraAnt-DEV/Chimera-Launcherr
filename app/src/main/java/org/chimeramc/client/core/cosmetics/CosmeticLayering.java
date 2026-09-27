package org.chimeramc.client.core.cosmetics;

/**
 * The depth order of the pieces the cosmetics preview draws, as pure arithmetic.
 *
 * <p>The preview is a painted 3D scene with a painter's-algorithm ordering rather than a depth
 * buffer, so "who covers whom" is decided entirely by the order of the draw calls. That order is
 * the part that goes wrong invisibly on a build machine: wings painted in the foreground pass
 * cover the torso and read as a flat sheet pasted on the chest, and a cape painted after the body
 * disappears inside it. Both were real defects.
 *
 * <p>The model uses Minecraft's convention that <b>−z is behind the player</b>. The body box spans
 * z ∈ [−2, 2], so the cape hangs just behind it and the wings sit behind the cape, letting them
 * fan out beyond the cape's silhouette while never crossing in front of the body. Encoding the
 * three planes here, and testing their order, is what keeps a future tweak from silently
 * reshuffling the layers.
 */
public final class CosmeticLayering {

    /** The body box's back face, in model pixels; anything less than this is behind the player. */
    public static final float BODY_BACK_Z = -2f;

    /** Where the cape hangs, in model pixels, matching the simulator's anchor plane. */
    public static final float CAPE_Z = -2.4f;

    /** Where the wings sit, in model pixels: behind the cape so they fan out from under it. */
    public static final float WINGS_Z = -2.9f;

    private CosmeticLayering() {
    }

    /**
     * Whether the wings are drawn behind the cape.
     *
     * <p>Since −z is behind, "behind" means a smaller z. This is the invariant the preview relies
     * on; if it ever inverts, the wings paint over the cape and the model looks like it is wearing
     * a flat board.
     */
    public static boolean wingsBehindCape() {
        return WINGS_Z < CAPE_Z;
    }

    /**
     * Whether the cape is drawn behind the body.
     *
     * <p>A cape in front of the body would cover the torso, which is the opposite of how a cape is
     * worn. Pinned so the cape's anchor cannot drift in front of the back face.
     */
    public static boolean capeBehindBody() {
        return CAPE_Z < BODY_BACK_Z;
    }
}
