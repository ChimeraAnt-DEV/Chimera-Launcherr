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
    /**
     * Lean at a full {@code query.cape_flap_amount}, i.e. the swing vanilla itself would apply.
     *
     * <p>This is the primary driver. Vanilla's own {@code animation.player.cape} is
     * {@code math.lerp(0.0, -126.0, query.cape_flap_amount) - 6.0}, so a cape with a vanilla/Persona
     * cape equipped already has a per-frame flap signal the game computes; reading it makes the
     * Chimera cape track exactly the motion the player expects. {@code modified_move_speed} alone
     * is a poor driver because it is a small, game-scaled value (a sprint saturates near 1.0 only
     * at full tilt), so on its own the cloth barely moves at a walk. Deliberately short of
     * vanilla's 126° so the cape reads as cloth rather than a flag snapping horizontal.
     */
    public static final double FLAP_LEAN_DEG = 42.0;
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

    /**
     * Flutter phase lag per segment, in blocks of travel. Each segment runs its sine a little
     * behind the one above it, so the fold travels down the cloth instead of the whole chain
     * moving in lockstep. Across the full chain this is
     * {@code SEGMENT_COUNT * SEGMENT_PHASE_LAG_BLOCKS} of travel; at a sprint that is roughly one
     * to two frames, which is the lag the hem should visibly trail the shoulders by.
     */
    public static final double SEGMENT_PHASE_LAG_BLOCKS = 0.012;

    /** Sideways sway contributed by turning, per unit of the bounded body-yaw term. */
    public static final double TURN_SWAY_DEG = 4.0;

    private CapeAnimationCurve() {
    }

    /**
     * The fraction of the total cape rotation that a segment carries, 1-based.
     *
     * <p>A linear ramp from the shoulders to the hem, normalised so the shares sum to 1. That sum
     * is what keeps the chain honest: the total rotation across all segments equals the lean a
     * single bone would have had, so segmenting redistributes the bend along the cloth instead of
     * changing how far the cape leans. The hem carries the most and the shoulders the least, which
     * is what reads as a fold.
     */
    public static double segmentShare(int index, int total) {
        if (total <= 1) return 1.0;
        if (index < 1) index = 1;
        if (index > total) index = total;
        return (2.0 * index) / (double) (total * (total + 1));
    }

    /**
     * A segment's X rotation in degrees; negative leans back. The Java mirror of the per-segment
     * animation expression, used by the tests to prove the chain folds rather than snapping.
     */
    public static double segmentLeanDegrees(int index, int total, double moveSpeed,
                                            boolean jumping, double verticalSpeed,
                                            double distanceMoved) {
        return segmentLeanDegrees(index, total, moveSpeed, jumping, verticalSpeed, distanceMoved, 0.0);
    }

    /** As above, carrying the vanilla cape-flap term down the chain. */
    public static double segmentLeanDegrees(int index, int total, double moveSpeed,
                                            boolean jumping, double verticalSpeed,
                                            double distanceMoved, double capeFlap) {
        double share = segmentShare(index, total);
        double phase = (index - 1) * SEGMENT_PHASE_LAG_BLOCKS;
        return share * leanDegrees(moveSpeed, jumping, verticalSpeed, distanceMoved - phase, capeFlap);
    }

    /**
     * A segment's Z rotation in degrees. Uses the bounded body-yaw term so a turn ripples the
     * cloth, and the same per-segment phase lag so the ripple travels down the chain.
     */
    public static double segmentSwayDegrees(int index, int total, double moveSpeed,
                                            double distanceMoved, double bodyYawDegrees) {
        double share = segmentShare(index, total);
        double phase = (index - 1) * SEGMENT_PHASE_LAG_BLOCKS;
        return share * (swayDegrees(moveSpeed, distanceMoved - phase)
                + turnSwayDegrees(moveSpeed, bodyYawDegrees));
    }

    /**
     * The bounded turn-sway term. The body yaw wraps, so it is fed through a sine: the result is
     * always in {@code [-1, 1]} and cannot grow without bound as the player spins, while still
     * changing as the player turns so a turn visibly moves the cloth.
     */
    public static double turnSwayDegrees(double moveSpeed, double bodyYawDegrees) {
        double speed = clamp(moveSpeed, 0.0, MAX_MOVE_SPEED);
        double yaw = Double.isNaN(bodyYawDegrees) ? 0.0 : bodyYawDegrees;
        return Math.sin(Math.toRadians(yaw)) * TURN_SWAY_DEG * speed;
    }

    /** The cape bone's X rotation in degrees for the given movement state; negative leans back. */
    public static double leanDegrees(double moveSpeed, boolean jumping, double verticalSpeed,
                                     double distanceMoved) {
        return leanDegrees(moveSpeed, jumping, verticalSpeed, distanceMoved, 0.0);
    }

    /**
     * The cape bone's X rotation with vanilla's own {@code query.cape_flap_amount} as the primary
     * driver. {@code capeFlap} is the 0..1 signal the game already computes for a vanilla cape; when
     * the player has no cape equipped it is 0 and the {@code modified_move_speed} term still gives
     * the cloth motion, so the cape animates either way.
     */
    public static double leanDegrees(double moveSpeed, boolean jumping, double verticalSpeed,
                                     double distanceMoved, double capeFlap) {
        double speed = clamp(moveSpeed, 0.0, MAX_MOVE_SPEED);
        double flap = clamp(capeFlap, 0.0, 1.0);
        double vertical = clamp(verticalSpeed, -MAX_VERTICAL, MAX_VERTICAL);
        double lean = speed * WALK_LEAN_DEG + flap * FLAP_LEAN_DEG;
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
        return leanExpression(0.0);
    }

    /**
     * The X-rotation expression with a phase offset baked into the flutter's sine argument, so a
     * segment's fold travels slightly behind the one above it. The offset subtracts inside the
     * sine exactly as {@link #segmentLeanDegrees} does, so the JSON and the Java agree.
     */
    private static String leanExpression(double phaseOffsetBlocks) {
        return "-("
                + "math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")"
                + " * " + num(WALK_LEAN_DEG)
                + " + math.clamp(query.cape_flap_amount, 0.0, 1.0) * " + num(FLAP_LEAN_DEG)
                + " + (query.is_jumping ? " + num(JUMP_FLARE_DEG) + " : 0.0)"
                + " + math.clamp(query.vertical_speed, -" + num(MAX_VERTICAL)
                + ", " + num(MAX_VERTICAL) + ") * " + num(VERTICAL_LEAN_DEG)
                + " + math.sin((" + distanceWithOffset(phaseOffsetBlocks) + ") * "
                + num(FLUTTER_FREQUENCY) + ")"
                + " * " + num(FLUTTER_AMPLITUDE_DEG)
                + " * math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")"
                + ")";
    }

    /** The Bedrock expression for the Z sway, built from the same constants. */
    public static String swayExpression() {
        return swayExpression(0.0);
    }

    /** The Z-sway expression with a phase offset baked into its sine argument. */
    private static String swayExpression(double phaseOffsetBlocks) {
        return "math.sin((" + distanceWithOffset(phaseOffsetBlocks) + ") * " + num(SWAY_FREQUENCY) + ")"
                + " * " + num(SWAY_AMPLITUDE_DEG)
                + " * math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")";
    }

    /**
     * The bounded turn-sway Bedrock expression, mirroring {@link #turnSwayDegrees}. The body yaw
     * wraps, so it is fed through a sine; the result is always in {@code [-1, 1]}.
     */
    public static String turnSwayExpression() {
        return "math.sin(math.rad(query.body_y_rotation)) * " + num(TURN_SWAY_DEG)
                + " * math.clamp(query.modified_move_speed, 0.0, " + num(MAX_MOVE_SPEED) + ")";
    }

    /**
     * A segment's X-rotation Bedrock expression. {@code share} is the segment's fraction of the
     * total bend and the phase lag is baked into the sine argument, both derived from the same
     * constants {@link #segmentLeanDegrees} uses, so the JSON and the Java cannot describe a
     * different chain.
     */
    public static String segmentLeanExpression(int index, int total) {
        return "(" + num(segmentShare(index, total)) + ") * "
                + leanExpression((index - 1) * SEGMENT_PHASE_LAG_BLOCKS);
    }

    /**
     * A segment's Z-rotation Bedrock expression. Uses the bounded body-yaw term so a turn ripples
     * the cloth, and the same per-segment phase lag as the lean so the ripple travels down the
     * chain.
     */
    public static String segmentSwayExpression(int index, int total) {
        return "(" + num(segmentShare(index, total)) + ") * ("
                + swayExpression((index - 1) * SEGMENT_PHASE_LAG_BLOCKS)
                + " + " + turnSwayExpression() + ")";
    }

    /** {@code query.modified_distance_moved} minus a phase offset, or the bare query at zero. */
    private static String distanceWithOffset(double phaseOffsetBlocks) {
        if (phaseOffsetBlocks == 0.0) return "query.modified_distance_moved";
        return "query.modified_distance_moved - " + num(phaseOffsetBlocks);
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
