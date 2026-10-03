package org.chimeramc.client.core.cosmetics;

/**
 * The cloth motion the in-game cape plays, as a pure function of Bedrock's movement queries.
 *
 * <p>The in-game cape is a static box unless a data-driven animation moves it, which is why a
 * cape built with only a geometry renders stiff and rigid. This class owns the motion the
 * generated animation applies to the cape bone, so the shape is unit-testable without a device and
 * the amplitude constants are the single source of truth the animation JSON is built from — a
 * literal drifting in one but not the other is exactly how "the cape animates in the preview but
 * not in the game" happens.
 *
 * <p><b>Axis convention.</b> The cape bone is turned 180 degrees about Y so its face points away
 * from the player, so the swing that reads as "the cape trails behind me" is a rotation about X,
 * which is the same axis the vanilla {@code animation.player.cape} drives. Negative X leans the
 * cloth back. Sway about Z is a small secondary flutter so a run does not look like a rigid plank
 * hinging on one axis.
 *
 * <p>The amplitudes are deliberately modest. A cape that snaps to full extension at a walk reads
 * as a flag rather than cloth; the lean ramps with speed and the flutter is a sine over distance
 * moved so the fold pattern travels down the cloth instead of pulsing in place.
 */
public final class CapeAnimationCurve {

    /** Lean at a full sprint. */
    public static final double WALK_LEAN_DEG = 28.0;
    /** Extra flare while airborne. */
    public static final double JUMP_FLARE_DEG = 12.0;
    /** Lean added per unit of vertical speed, so rising and falling both stream the cloth. */
    public static final double VERTICAL_LEAN_DEG = 10.0;
    /** Amplitude of the travelling flutter. */
    public static final double FLUTTER_AMPLITUDE_DEG = 7.0;
    /** Spatial frequency of the flutter, per block moved. */
    public static final double FLUTTER_FREQUENCY = 55.0;
    /** Amplitude of the secondary sideways sway. */
    public static final double SWAY_AMPLITUDE_DEG = 5.0;
    /** Spatial frequency of the sway, per block moved. */
    public static final double SWAY_FREQUENCY = 41.0;

    /** Movement speed is treated as saturated at this value; a sprint reads as full extension. */
    public static final double MAX_MOVE_SPEED = 1.0;
    /** Vertical speed is clamped to this magnitude so a long fall cannot over-rotate the cape. */
    public static final double MAX_VERTICAL = 1.5;

    private CapeAnimationCurve() {
    }

    /** The cape bone's X rotation in degrees for the given movement state; negative leans back. */
    public static double leanDegrees(double moveSpeed, boolean jumping, double verticalSpeed,
                                     double distanceMoved) {
        double speed = clamp(moveSpeed, 0.0, MAX_MOVE_SPEED);
        double vertical = clamp(verticalSpeed, -MAX_VERTICAL, MAX_VERTICAL);
        double lean = speed * WALK_LEAN_DEG;
        if (jumping) lean += JUMP_FLARE_DEG;
        lean += vertical * VERTICAL_LEAN_DEG;
        lean += Math.sin(distanceMoved * FLUTTER_FREQUENCY) * FLUTTER_AMPLITUDE_DEG * speed;
        return -lean;
    }

    /** The cape bone's Z rotation in degrees; the secondary sideways flutter while moving. */
    public static double swayDegrees(double moveSpeed, double distanceMoved) {
        double speed = clamp(moveSpeed, 0.0, MAX_MOVE_SPEED);
        return Math.sin(distanceMoved * SWAY_FREQUENCY) * SWAY_AMPLITUDE_DEG * speed;
    }

    /**
     * The Bedrock expression for the X rotation.
     *
     * <p>Built from the constants above so the JSON and {@link #leanDegrees} cannot disagree about
     * an amplitude. Clamped exactly as the Java does: speed to {@code [0,1]}, vertical speed to
     * {@code [-1.5,1.5]}.
     */
    public static String leanExpression() {
        return "-("
                + "math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")"
                + " * " + num(WALK_LEAN_DEG)
                + " + (query.is_jumping ? " + num(JUMP_FLARE_DEG) + " : 0.0)"
                + " + math.clamp(query.vertical_speed, -" + num(MAX_VERTICAL)
                + ", " + num(MAX_VERTICAL) + ") * " + num(VERTICAL_LEAN_DEG)
                + " + math.sin(query.modified_distance_moved * " + num(FLUTTER_FREQUENCY) + ")"
                + " * " + num(FLUTTER_AMPLITUDE_DEG)
                + " * math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")"
                + ")";
    }

    /** The Bedrock expression for the Z sway, built from the same constants. */
    public static String swayExpression() {
        return "math.sin(query.modified_distance_moved * " + num(SWAY_FREQUENCY) + ")"
                + " * " + num(SWAY_AMPLITUDE_DEG)
                + " * math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")";
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return value < min ? min : Math.min(value, max);
    }

    /** Renders a constant the way the animation JSON wants it: always with a decimal point. */
    private static String num(double value) {
        String text = String.valueOf(value);
        return text.contains(".") ? text : text + ".0";
    }
}
