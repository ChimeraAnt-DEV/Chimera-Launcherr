package org.chimeramc.client.core.cosmetics;

/**
 * The head-bone look rotation, applied to a worn accessory so a hat tracks the head in the preview
 * the way the in-game {@code acc} bone tracks it in the pack.
 *
 * <p>In the pack the worn accessory is a separate render-controller geometry, so it cannot be
 * parented to the player's head bone; {@code animation.chimera_hat_tilt} instead turns the
 * accessory's {@code acc} bone by {@code query.target_x_rotation} / {@code query.target_y_rotation}
 * — the same queries the vanilla head bone uses — about the neck pivot {@code (0, 24, 0)}. The
 * preview draws the accessory's authored mesh directly, so it must apply the same rotation itself or
 * a hat would sit bolt-upright while the head turns, and a head-tracking hat would look broken.
 *
 * <p>This is the preview half of that rule, kept Android-free so the pivot and the rotation order
 * are unit-testable. The rotation is pitch about X then yaw about Y, around the neck pivot; the same
 * order the pack's animation expresses.
 */
public final class HeadLookTransform {

    /** The vanilla head bone's pivot, which the accessory's {@code acc} bone also uses. */
    public static final float PIVOT_X = 0f;
    public static final float PIVOT_Y = 24f;
    public static final float PIVOT_Z = 0f;

    /**
     * Rotates {@code point} in place about the neck pivot by the head's pitch (X) and yaw (Y).
     *
     * @param pitchDeg head pitch in degrees ({@code query.target_x_rotation})
     * @param yawDeg   head yaw in degrees ({@code query.target_y_rotation})
     * @param point    {@code [x, y, z]} in model space, mutated in place
     */
    public static void apply(float pitchDeg, float yawDeg, float[] point) {
        if (pitchDeg == 0f && yawDeg == 0f) return;
        float x = point[0] - PIVOT_X;
        float y = point[1] - PIVOT_Y;
        float z = point[2] - PIVOT_Z;

        // Pitch about X.
        double pitch = Math.toRadians(pitchDeg);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        double y1 = y * cosP - z * sinP;
        double z1 = y * sinP + z * cosP;

        // Yaw about Y.
        double yaw = Math.toRadians(yawDeg);
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double x2 = x * cosY + z1 * sinY;
        double z2 = -x * sinY + z1 * cosY;

        point[0] = (float) x2 + PIVOT_X;
        point[1] = (float) y1 + PIVOT_Y;
        point[2] = (float) z2 + PIVOT_Z;
    }

    /**
     * Rotates a direction vector (no pivot translation) by the head's pitch (X) then yaw (Y).
     *
     * <p>Used to keep a rotated face's cull normal consistent with its rotated corners, so a head
     * turned by the look does not cull the face it is drawing.
     */
    public static void applyVector(float pitchDeg, float yawDeg, float[] v) {
        if (pitchDeg == 0f && yawDeg == 0f) return;
        double pitch = Math.toRadians(pitchDeg);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        double y1 = v[1] * cosP - v[2] * sinP;
        double z1 = v[1] * sinP + v[2] * cosP;

        double yaw = Math.toRadians(yawDeg);
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double x2 = v[0] * cosY + z1 * sinY;
        double z2 = -v[0] * sinY + z1 * cosY;

        v[0] = (float) x2;
        v[1] = (float) y1;
        v[2] = (float) z2;
    }

    /**
     * A gentle idle head look, in degrees, from elapsed milliseconds.
     *
     * <p>The launcher preview has no live look input, so the head sways on a slow, bounded sine —
     * enough to show the hat tracking the head without the character appearing to glance around.
     * {@code out[0]} is pitch, {@code out[1]} is yaw.
     */
    public static void idleLook(long elapsedMs, float[] out) {
        double t = elapsedMs / 1000.0;
        out[0] = (float) (Math.sin(t * 0.6) * 6.0);
        out[1] = (float) (Math.sin(t * 0.4 + 0.9) * 14.0);
    }

    private HeadLookTransform() {
    }
}
