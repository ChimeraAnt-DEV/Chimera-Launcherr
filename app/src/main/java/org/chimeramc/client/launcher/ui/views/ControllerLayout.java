package org.chimeramc.client.ui.views;

import org.chimeramc.client.launcher.controller.ControllerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for the controller illustrations, kept out of the {@code View} so it can be
 * unit tested on the JVM.
 *
 * Everything here is centre-relative in <em>scale units</em>: a drawn point is
 * {@code (cx + dx * scale, cy + dy * scale)}. Region coordinates are separate and normalised
 * 0..1 instead, because they are authored against the same grid as the shell. Both
 * conventions are deliberate; the two must not be mixed.
 *
 * Correctness here is not cosmetic. A control drawn outside its shell, or two controls
 * overlapping, is the failure mode this illustration keeps regressing into, and neither can
 * be eyeballed on a build machine, so {@code ControllerLayoutTest} checks containment and
 * overlap numerically.
 */
public final class ControllerLayout {

    /** How a region is drawn. */
    public enum Shape {
        STICK, STICK_RING, DPAD, FACE_XBOX, FACE_DUAL, BUMPER, TRIGGER, CENTER_BUTTON,
        TOUCHPAD, PS_LOGO, GUIDE, MUTE
    }

    /** One addressable control: its centre, its size and how to draw it. */
    public static final class Spec {
        public final String id;
        public final float x;
        public final float y;
        public final float radius;
        public final String label;
        public final Shape shape;

        Spec(String id, float x, float y, float radius, String label, Shape shape) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.label = label;
            this.shape = shape;
        }

        /**
         * Half-extents of this region's drawn bounds, in scale units.
         *
         * Bumpers, triggers, the touchpad and the mute bar draw as wide rounded rectangles
         * or arcs rather than circles, so testing them as circles reports false containment
         * failures and hides real ones.
         */
        public float halfWidth() {
            float r = radius * 2f;
            switch (shape) {
                case BUMPER: return r * 2.3f;
                case TRIGGER: return r * 1.9f;
                case TOUCHPAD: return r * 2.15f;
                case MUTE: return r * 2.4f;
                default: return r;
            }
        }

        public float halfHeight() {
            float r = radius * 2f;
            switch (shape) {
                case BUMPER: return r * 0.52f;
                // The lever arc spans from slightly above its centre to well below; its reach
                // outward is what matters for placement.
                case TRIGGER: return r * 0.95f;
                case TOUCHPAD: return r * 0.78f;
                case MUTE: return r * 0.55f;
                default: return r;
            }
        }
    }

    private static final float XBOX_W = 0.60f;
    private static final float XBOX_TOP = -0.72f;
    private static final float XBOX_H = 1.46f;

    private static final float PS_W = 0.58f;
    private static final float PS_TOP = -0.70f;
    private static final float PS_H = 1.42f;

    private ControllerLayout() {
    }

    public static List<Spec> regions(ControllerType type) {
        List<Spec> out = new ArrayList<>();
        if (type == null) return out;
        switch (type) {
            case XBOX -> {
                out.add(new Spec("ls", 0.318f, 0.390f, 0.050f, "LS", Shape.STICK));
                out.add(new Spec("lsRing", 0.318f, 0.390f, 0.060f, "", Shape.STICK_RING));
                out.add(new Spec("rs", 0.655f, 0.620f, 0.050f, "RS", Shape.STICK));
                out.add(new Spec("rsRing", 0.655f, 0.620f, 0.060f, "", Shape.STICK_RING));
                out.add(new Spec("dp", 0.300f, 0.645f, 0.044f, "", Shape.DPAD));
                out.add(new Spec("a", 0.720f, 0.405f, 0.042f, "A", Shape.FACE_XBOX));
                out.add(new Spec("b", 0.775f, 0.335f, 0.042f, "B", Shape.FACE_XBOX));
                out.add(new Spec("x", 0.665f, 0.335f, 0.042f, "X", Shape.FACE_XBOX));
                out.add(new Spec("y", 0.720f, 0.265f, 0.042f, "Y", Shape.FACE_XBOX));
                // Bumpers sit below the triggers so they clear the shell's curved shoulder.
                out.add(new Spec("lb", 0.345f, 0.155f, 0.028f, "LB", Shape.BUMPER));
                out.add(new Spec("rb", 0.655f, 0.155f, 0.028f, "RB", Shape.BUMPER));
                // Triggers sit high enough that the lever crown clears the shell's shoulder.
                // At the old height the whole path sat behind the body and only an invisible
                // sliver poked out — the "back triggers don't show" report.
                out.add(new Spec("lt", 0.185f, 0.096f, 0.032f, "", Shape.TRIGGER));
                out.add(new Spec("rt", 0.815f, 0.096f, 0.032f, "", Shape.TRIGGER));
                out.add(new Spec("view", 0.440f, 0.355f, 0.020f, "", Shape.CENTER_BUTTON));
                out.add(new Spec("menu", 0.560f, 0.355f, 0.020f, "", Shape.CENTER_BUTTON));
                out.add(new Spec("guide", 0.500f, 0.265f, 0.026f, "", Shape.GUIDE));
            }
            case DS4, DUAL_SENSE -> {
                boolean dualSense = type == ControllerType.DUAL_SENSE;
                out.add(new Spec("ls", 0.380f, 0.660f, 0.048f, "LS", Shape.STICK));
                out.add(new Spec("lsRing", 0.380f, 0.660f, 0.058f, "", Shape.STICK_RING));
                out.add(new Spec("rs", 0.620f, 0.660f, 0.048f, "RS", Shape.STICK));
                out.add(new Spec("rsRing", 0.620f, 0.660f, 0.058f, "", Shape.STICK_RING));
                out.add(new Spec("dp", 0.320f, 0.392f, 0.043f, "", Shape.DPAD));
                out.add(new Spec("a", 0.685f, 0.443f, 0.040f, "", Shape.FACE_DUAL));
                out.add(new Spec("b", 0.743f, 0.385f, 0.040f, "", Shape.FACE_DUAL));
                out.add(new Spec("x", 0.627f, 0.385f, 0.040f, "", Shape.FACE_DUAL));
                out.add(new Spec("y", 0.685f, 0.327f, 0.040f, "", Shape.FACE_DUAL));
                out.add(new Spec("lb", 0.345f, 0.165f, 0.028f, "L1", Shape.BUMPER));
                out.add(new Spec("rb", 0.655f, 0.165f, 0.028f, "R1", Shape.BUMPER));
                out.add(new Spec("lt", 0.248f, 0.112f, 0.030f, "", Shape.TRIGGER));
                out.add(new Spec("rt", 0.752f, 0.112f, 0.030f, "", Shape.TRIGGER));
                out.add(new Spec("touch", 0.500f, 0.235f, dualSense ? 0.056f : 0.050f, "",
                        Shape.TOUCHPAD));
                out.add(new Spec("share", 0.300f, 0.255f, 0.019f, "", Shape.CENTER_BUTTON));
                out.add(new Spec("options", 0.700f, 0.255f, 0.019f, "", Shape.CENTER_BUTTON));
                out.add(new Spec("ps", 0.500f, 0.400f, 0.022f, "", Shape.PS_LOGO));
                if (dualSense) {
                    out.add(new Spec("mute", 0.500f, 0.472f, 0.015f, "", Shape.MUTE));
                }
            }
            default -> {
            }
        }
        return out;
    }

    /**
     * The shell outline as cubic Bezier segments, in centre-relative scale units.
     *
     * Each segment is eight floats: start, two control points, end. The list walks the
     * silhouette once and closes implicitly, so consecutive segments must share an endpoint
     * or the outline shows a seam.
     */
    public static float[] shellSegments(ControllerType type) {
        if (type == ControllerType.XBOX) {
            float w = XBOX_W, top = XBOX_TOP, h = XBOX_H;
            return new float[]{
                    -w * 0.86f, top, -w * 1.12f, top, -w * 1.26f, top + h * 0.10f, -w * 1.24f, top + h * 0.30f,
                    -w * 1.24f, top + h * 0.30f, -w * 1.22f, top + h * 0.52f, -w * 1.18f, top + h * 0.72f, -w * 1.02f, top + h * 0.92f,
                    -w * 1.02f, top + h * 0.92f, -w * 0.92f, top + h * 1.02f, -w * 0.74f, top + h * 1.03f, -w * 0.60f, top + h * 0.96f,
                    -w * 0.60f, top + h * 0.96f, -w * 0.50f, top + h * 0.90f, -w * 0.34f, top + h * 0.78f, -w * 0.26f, top + h * 0.70f,
                    -w * 0.26f, top + h * 0.70f, -w * 0.14f, top + h * 0.62f, w * 0.14f, top + h * 0.62f, w * 0.26f, top + h * 0.70f,
                    w * 0.26f, top + h * 0.70f, w * 0.34f, top + h * 0.78f, w * 0.50f, top + h * 0.90f, w * 0.60f, top + h * 0.96f,
                    w * 0.60f, top + h * 0.96f, w * 0.74f, top + h * 1.03f, w * 0.92f, top + h * 1.02f, w * 1.02f, top + h * 0.92f,
                    w * 1.02f, top + h * 0.92f, w * 1.18f, top + h * 0.72f, w * 1.22f, top + h * 0.52f, w * 1.24f, top + h * 0.30f,
                    w * 1.24f, top + h * 0.30f, w * 1.26f, top + h * 0.10f, w * 1.12f, top, w * 0.86f, top,
                    w * 0.86f, top, w * 0.40f, top - h * 0.045f, -w * 0.40f, top - h * 0.045f, -w * 0.86f, top,
            };
        }
        float w = PS_W, top = PS_TOP, h = PS_H;
        return new float[]{
                -w * 0.62f, top, -w * 0.95f, top, -w * 1.18f, top + h * 0.16f, -w * 1.20f, top + h * 0.42f,
                -w * 1.20f, top + h * 0.42f, -w * 1.22f, top + h * 0.62f, -w * 1.14f, top + h * 0.84f, -w * 0.92f, top + h * 0.98f,
                -w * 0.92f, top + h * 0.98f, -w * 0.74f, top + h * 1.04f, -w * 0.56f, top + h * 1.04f, -w * 0.42f, top + h * 0.96f,
                -w * 0.42f, top + h * 0.96f, -w * 0.32f, top + h * 0.90f, -w * 0.24f, top + h * 0.78f, -w * 0.16f, top + h * 0.66f,
                -w * 0.16f, top + h * 0.66f, -w * 0.07f, top + h * 0.58f, w * 0.07f, top + h * 0.58f, w * 0.16f, top + h * 0.66f,
                w * 0.16f, top + h * 0.66f, w * 0.24f, top + h * 0.78f, w * 0.32f, top + h * 0.90f, w * 0.42f, top + h * 0.96f,
                w * 0.42f, top + h * 0.96f, w * 0.56f, top + h * 1.04f, w * 0.74f, top + h * 1.04f, w * 0.92f, top + h * 0.98f,
                w * 0.92f, top + h * 0.98f, w * 1.14f, top + h * 0.84f, w * 1.22f, top + h * 0.62f, w * 1.20f, top + h * 0.42f,
                w * 1.20f, top + h * 0.42f, w * 1.18f, top + h * 0.16f, w * 0.95f, top, w * 0.62f, top,
                w * 0.62f, top, w * 0.30f, top - h * 0.05f, -w * 0.30f, top - h * 0.05f, -w * 0.62f, top,
        };
    }

    /**
     * Flatten the shell into a closed polygon of {@code x,y} pairs in centre-relative scale
     * units, for containment tests.
     */
    public static float[] shellPolygon(ControllerType type, int samplesPerSegment) {
        float[] segs = shellSegments(type);
        int n = samplesPerSegment <= 0 ? 16 : samplesPerSegment;
        int count = segs.length / 8;
        float[] out = new float[(count * n + 1) * 2];
        int k = 0;
        for (int s = 0; s < count; s++) {
            int b = s * 8;
            float x0 = segs[b], y0 = segs[b + 1];
            float x1 = segs[b + 2], y1 = segs[b + 3];
            float x2 = segs[b + 4], y2 = segs[b + 5];
            float x3 = segs[b + 6], y3 = segs[b + 7];
            for (int i = 0; i < n; i++) {
                float t = (float) i / n;
                float mt = 1f - t;
                float a = mt * mt * mt, c = 3f * mt * mt * t, d = 3f * mt * t * t, e = t * t * t;
                out[k++] = a * x0 + c * x1 + d * x2 + e * x3;
                out[k++] = a * y0 + c * y1 + d * y2 + e * y3;
            }
        }
        out[k++] = segs[segs.length - 2];
        out[k] = segs[segs.length - 1];
        return out;
    }

    /**
     * A region's centre in centre-relative scale units.
     *
     * Region coordinates are normalised 0..1, so 0.5 is centre and the span 0.0..1.0 maps to
     * twice the scale. The subtraction has to stay inside the parentheses, otherwise every
     * control lands at centre-minus-scale and the whole layout piles into the left half.
     */
    public static float regionDx(Spec spec) {
        return (spec.x - 0.5f) * 2f;
    }

    public static float regionDy(Spec spec) {
        return (spec.y - 0.5f) * 2f;
    }
}
