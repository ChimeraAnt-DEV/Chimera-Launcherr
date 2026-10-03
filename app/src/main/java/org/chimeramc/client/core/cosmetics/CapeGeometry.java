package org.chimeramc.client.core.cosmetics;

/**
 * The segmented cape geometry: a chain of thin bones parented in sequence.
 *
 * <p>A single box swings as one rigid plank — every part of the cloth reaches its lean at the same
 * instant — so the cape reads as a hinged board rather than fabric. This builds a chain of
 * {@link #SEGMENT_COUNT} bones, each hinged at the bottom edge of the one above, so the cloth folds
 * and ripples down its length. The segments together span the same 10x16 box the single-bone cape
 * used, and each segment samples its own slice of the cape texture, so the artwork is not stretched
 * across the chain.
 *
 * <p>Every dimension is derived from the segment count, so changing the count (the FPS fallback)
 * produces a chain that still spans the box: at 16 segments each is one pixel tall, at 12 each is
 * {@code 16/12} pixels. The UV offset is proportional for the same reason — a fixed per-segment UV
 * would leave the bottom rows unsampled on a shorter chain.
 *
 * <p>Pure string building with no Android types, so the chain is unit-testable: the bone count, the
 * parenting order, the joint positions and that no two segments overlap are all checked without a
 * device. A coordinate typo that would silently fold the cape into a knot on a real device fails
 * here instead.
 *
 * <p><b>Performance.</b> Bone count is a per-frame CPU cost on low-end hardware.
 * {@link #FALLBACK_SEGMENT_COUNT} is the reduced chain to switch to if a device measures a
 * frame-rate drop. It is one constant: the geometry, the animation and the render controller all
 * derive from {@link #SEGMENT_COUNT}, so the switch cannot leave one of them describing a different
 * chain than the others.
 */
public final class CapeGeometry {

    /**
     * Bones in the chain. Sixteen gives a smooth fold; the cape is 16px tall, so one bone per pixel
     * is the natural resolution and a smaller count visibly segments the cloth.
     */
    public static final int SEGMENT_COUNT = 16;

    /**
     * The reduced chain for a device where 16 bones measurably cost frame rate. Changing
     * {@link #SEGMENT_COUNT} to this value is the whole switch; every other dimension derives from
     * the count, so the shorter chain still spans the cape box.
     */
    public static final int FALLBACK_SEGMENT_COUNT = 12;

    /** Top of the cape, at the shoulders. */
    public static final double CAPE_TOP_Y = 24.0;
    /** Bottom of the cape, at the hem. */
    public static final double CAPE_BOTTOM_Y = 8.0;
    /** Height of the cape box in model pixels. */
    public static final double CAPE_HEIGHT_PX = CAPE_TOP_Y - CAPE_BOTTOM_Y;

    /** Texture rows the cape's cloth panel occupies. */
    public static final int TEXTURE_ROWS = 16;

    /** The 180-degree turn that points each segment's face away from the player. */
    private static final double FACING_YAW = 180.0;

    private CapeGeometry() {
    }

    /** Bone name for a 1-based segment index, e.g. {@code cape_1}. */
    public static String boneName(int index) {
        return "cape_" + index;
    }

    /**
     * The bone this segment hangs from. The first segment hangs from the body; every later segment
     * hangs from the one above it, which is what makes the chain fold cumulatively.
     */
    public static String chainParent(int index) {
        return index <= 1 ? "body" : boneName(index - 1);
    }

    /** Height of one segment in model pixels, so {@code count} segments span the cape box. */
    public static double segmentHeight(int count) {
        return CAPE_HEIGHT_PX / count;
    }

    /** The joint (pivot) Y of a segment: the top edge of the segment, at the shoulders. */
    public static double segmentPivotY(int index, int count) {
        return CAPE_TOP_Y - (index - 1) * segmentHeight(count);
    }

    /** The cube origin Y of a segment; in Bedrock {@code origin} is the minimum corner. */
    public static double segmentOriginY(int index, int count) {
        return segmentPivotY(index, count) - segmentHeight(count);
    }

    /** The texture row a segment's UV starts at, proportional to the chain length. */
    public static double segmentUvV(int index, int count) {
        return (index - 1) * (double) TEXTURE_ROWS / count;
    }

    /**
     * The full geometry JSON for the chain.
     *
     * <p>{@code body}/{@code waist} are kept so the chain hangs off the same skeleton the vanilla
     * cape did. Every segment carries the 180-degree Y turn in both {@code rotation} and
     * {@code bind_pose_rotation}: the animation replaces {@code rotation} with its own
     * {@code [lean, 180, sway]} triple, and the bind pose keeps the correct facing if the animation
     * ever fails to load.
     */
    public static String modelJson(String identifier) {
        return modelJson(identifier, SEGMENT_COUNT);
    }

    /** The chain geometry for an explicit segment count, so the fallback is testable. */
    public static String modelJson(String identifier, int count) {
        double height = segmentHeight(count);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format_version\": \"1.12.0\",\n");
        sb.append("  \"minecraft:geometry\": [\n");
        sb.append("    {\n");
        sb.append("      \"description\": {\n");
        sb.append("        \"identifier\": \"").append(identifier).append("\",\n");
        sb.append("        \"texture_width\": 64,\n");
        sb.append("        \"texture_height\": 32,\n");
        sb.append("        \"visible_bounds_width\": 2,\n");
        sb.append("        \"visible_bounds_height\": 3,\n");
        sb.append("        \"visible_bounds_offset\": [0, 1, 0]\n");
        sb.append("      },\n");
        sb.append("      \"bones\": [\n");
        sb.append("        {\n");
        sb.append("          \"name\": \"body\",\n");
        sb.append("          \"pivot\": [0.0, 24.0, 0.0],\n");
        sb.append("          \"parent\": \"waist\"\n");
        sb.append("        },\n");
        sb.append("        {\n");
        sb.append("          \"name\": \"waist\",\n");
        sb.append("          \"pivot\": [0.0, 12.0, 0.0]\n");
        sb.append("        },\n");
        for (int i = 1; i <= count; i++) {
            sb.append("        {\n");
            sb.append("          \"name\": \"").append(boneName(i)).append("\",\n");
            sb.append("          \"parent\": \"").append(chainParent(i)).append("\",\n");
            sb.append("          \"pivot\": [0.0, ").append(num(segmentPivotY(i, count)))
                    .append(", 3.0],\n");
            sb.append("          \"bind_pose_rotation\": [0.0, ").append(num(FACING_YAW))
                    .append(", 0.0],\n");
            sb.append("          \"rotation\": [0.0, ").append(num(FACING_YAW)).append(", 0.0],\n");
            sb.append("          \"cubes\": [\n");
            sb.append("            {\n");
            sb.append("              \"origin\": [-5.0, ").append(num(segmentOriginY(i, count)))
                    .append(", 3.0],\n");
            sb.append("              \"size\": [10, ").append(num(height)).append(", 1],\n");
            sb.append("              \"uv\": [0, ").append(num(segmentUvV(i, count))).append("]\n");
            sb.append("            }\n");
            sb.append("          ]\n");
            sb.append("        }");
            sb.append(i < count ? ",\n" : "\n");
        }
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Renders a constant the way the geometry JSON wants it: always with a decimal point. */
    private static String num(double value) {
        String text = String.valueOf(value);
        return text.contains(".") ? text : text + ".0";
    }
}
