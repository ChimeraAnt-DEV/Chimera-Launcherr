package org.chimeramc.client.launcher.controller;

import org.chimeramc.client.ui.views.ControllerLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the flat, normalised region table into a perspective projection of a gamepad body, so
 * the illustration can show depth and, critically, the shoulder triggers that a pure top-down
 * view hides behind the shell.
 *
 * <p>This is deliberately Android-free: it produces plain {@code x,y} screen coordinates and
 * paint hints, and the view only draws what it returns. That keeps the geometry unit-testable,
 * which matters because a projection bug looks plausible on screen — the 2D layout had several
 * that only the layout test caught.
 *
 * <p>The model is a shallow box: the face is at {@code z = 0}, the region table supplies {@code
 * x,y} in centre-relative scale units, and a small camera pitch tilts the whole thing so the top
 * edge recedes. Shoulder controls are lifted toward the camera (negative depth) so the tilt does
 * not bury them behind the body; the body then overlaps their lower edge, which is what makes the
 * bumpers and triggers read as attached to the top rather than pasted on the face.
 */
public final class Controller3DProjection {

    /** A region with its projected screen position. */
    public static final class Projected {
        public final ControllerLayout.Spec spec;
        /** Screen position of the region's centre, already scaled and translated. */
        public final float x;
        public final float y;
        /** Screen radius, scaled by {@code scale} and the perspective factor at its depth. */
        public final float radius;
        /** Depth of the region's plane in scale units; negative is behind the face. */
        public final float z;
        /** True when the region is a shoulder control, drawn in the raised layer. */
        public final boolean shoulder;

        /** Perspective factor at this region's depth; greater than 1 when in front of the face. */
        public final float perspective;

        Projected(ControllerLayout.Spec spec, float x, float y, float radius, float perspective,
                  float z, boolean shoulder) {
            this.spec = spec;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.perspective = perspective;
            this.z = z;
            this.shoulder = shoulder;
        }
    }

    /**
     * How far the top of the pad tips away from the camera, per scale unit of y.
     *
     * A small value is enough to read as depth without the perspective becoming a fisheye; the
     * whole point is that the triggers become visible, not that the pad looks like a floor.
     */
    private static final float PITCH = 0.34f;

    /**
     * Distance from the camera to the face plane, in scale units. Larger flattens the
     * perspective; the default keeps the near edge noticeably larger than the far one.
     */
    private static final float CAMERA_DISTANCE = 3.1f;

    /** Depth lift applied to shoulder controls; negative brings them toward the camera. */
    private static final float SHOULDER_Z = -0.55f;

    private Controller3DProjection() {
    }

    /** True for the shapes that belong on the shoulder layer. */
    public static boolean isShoulder(ControllerLayout.Shape shape) {
        return shape == ControllerLayout.Shape.BUMPER || shape == ControllerLayout.Shape.TRIGGER;
    }

    /**
     * Rotates a face-local point by the model pitch about the horizontal axis through the origin.
     *
     * <p>Positive {@code y} is toward the top of the pad (the region table uses screen-down
     * positive, so callers negate it). The top tips away from the camera, so a point above the
     * centre gains positive depth and is drawn slightly smaller.
     */
    static float[] tilt(float x, float y, float z) {
        float cos = (float) Math.cos(PITCH);
        float sin = (float) Math.sin(PITCH);
        // Rotating about x: y' = y*cos - z*sin, z' = y*sin + z*cos
        return new float[]{x, y * cos - z * sin, y * sin + z * cos};
    }

    /**
     * Projects a point in model space onto the screen.
     *
     * @param scale    pixels per scale unit
     * @param centerX  screen x of the pad's centre
     * @param centerY  screen y of the pad's centre
     */
    static float[] project(float x, float y, float z, float scale, float centerX, float centerY) {
        float[] rotated = tilt(x, y, z);
        float depth = CAMERA_DISTANCE + rotated[2];
        if (depth < 0.05f) depth = 0.05f;
        float perspective = CAMERA_DISTANCE / depth;
        // Screen y grows downward, and the model's y grows upward.
        return new float[]{
                centerX + rotated[0] * scale * perspective,
                centerY - rotated[1] * scale * perspective,
                perspective,
        };
    }

    /**
     * Projects every region of {@code type} in draw order: far first, so a nearer control
     * paints over one behind it.
     *
     * <p>The region's {@code y} is taken as screen-down (matching the 2D table) and negated into
     * the model's up-positive space here, so callers do not have to remember the convention.
     */
    public static List<Projected> project(ControllerType type, float scale,
                                          float centerX, float centerY) {
        List<Projected> out = new ArrayList<>();
        for (ControllerLayout.Spec spec : ControllerLayout.regions(type)) {
            boolean shoulder = isShoulder(spec.shape);
            float z = shoulder ? SHOULDER_Z : 0f;
            float[] p = project(ControllerLayout.regionDx(spec), -ControllerLayout.regionDy(spec), z,
                    scale, centerX, centerY);
            out.add(new Projected(spec, p[0], p[1], spec.radius * 2f * scale * p[2], p[2], z,
                    shoulder));
        }
        out.sort((a, b) -> Float.compare(a.z, b.z));
        return out;
    }

    /**
     * The projected extent of the whole illustration, as {@code minX, minY, maxX, maxY} in scale
     * units at {@code scale = 1} and the origin as the centre.
     *
     * <p>This exists because the view cannot fit the pad from the shell alone. The triggers are
     * lifted toward the camera and sit above the shell's shoulder, so the drawn shape is
     * asymmetric about the face: fitting {@code height / 2} around the face centre left the
     * triggers poking off the top edge, where the view clipped them. Measuring the real extent
     * (shell plus every control, including each control's own radius) lets the view fit and centre
     * the whole thing, so the shoulder controls are always on screen.
     */
    public static float[] unitBounds(ControllerType type, int samplesPerSegment) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        float[] shell = projectShell(type, samplesPerSegment, 1f, 0f, 0f);
        for (int i = 0; i < shell.length; i += 2) {
            minX = Math.min(minX, shell[i]);
            maxX = Math.max(maxX, shell[i]);
            minY = Math.min(minY, shell[i + 1]);
            maxY = Math.max(maxY, shell[i + 1]);
        }

        for (Projected p : project(type, 1f, 0f, 0f)) {
            // Use the real draw half-extents: bumpers, triggers and the touchpad are wide rounded
            // rectangles, so a circle radius understates them and the fit clips their ends.
            float hw = p.spec.halfWidth() * p.perspective;
            float hh = p.spec.halfHeight() * p.perspective;
            minX = Math.min(minX, p.x - hw);
            maxX = Math.max(maxX, p.x + hw);
            minY = Math.min(minY, p.y - hh);
            maxY = Math.max(maxY, p.y + hh);
        }

        if (minX > maxX) return new float[]{-1f, -1f, 1f, 1f};
        return new float[]{minX, minY, maxX, maxY};
    }

    /**
     * Projects the shell outline into a closed polygon of {@code x,y} pairs, for filling the body.
     *
     * @param samplesPerSegment bezier samples per segment, matching
     *                          {@link ControllerLayout#shellPolygon}
     */
    public static float[] projectShell(ControllerType type, int samplesPerSegment, float scale,
                                       float centerX, float centerY) {
        float[] flat = ControllerLayout.shellPolygon(type, samplesPerSegment);
        float[] out = new float[flat.length];
        for (int i = 0; i < flat.length; i += 2) {
            float[] p = project(flat[i], -flat[i + 1], 0f, scale, centerX, centerY);
            out[i] = p[0];
            out[i + 1] = p[1];
        }
        return out;
    }
}
