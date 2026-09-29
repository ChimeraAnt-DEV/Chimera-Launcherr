package org.chimeramc.client.launcher.controller;

import android.view.MotionEvent;

/**
 * Immutable response curve for one controller profile, precomputed for the input hot path.
 *
 * {@link ControllerInputProcessor} runs inside {@code dispatchKeyEvent} /
 * {@code dispatchGenericMotionEvent}, i.e. on every controller event before the game sees
 * it. Doing that work allocation-free and lock-free matters: any cost here is added
 * directly to input-to-photon latency.
 *
 * Everything the axis path needs is derived once, on profile change:
 * - {@code invRange} folds the {@code 1 / (1 - deadZone)} division into a multiply.
 * - {@code remapTable} is a flat array indexed by key code instead of a HashMap lookup,
 *   covering the key codes a gamepad can actually send.
 * - The stick response curves are baked into {@link #CURVE_STEPS}-entry lookup tables, so a
 *   fractional-power curve costs one array read (interpolated) and one multiply per axis per
 *   event instead of a {@code Math.pow} on the UI thread.
 *
 * Trigger axes take a separate path: they are deliberately <em>not</em> dead-zoned or scaled
 * like sticks (a trigger has no centre rest to drift around), but they do flow through the
 * profile's trigger curve, which is what makes a hair-trigger or a resting-trigger guard
 * possible.
 */
public final class ControllerResponse {

    /**
     * Key codes a gamepad can produce (KeyEvent.KEYCODE_BUTTON_A=96 .. KEYCODE_BUTTON_MODE=110,
     * plus the D-pad at 19-23 and the thumb buttons at 106/107). The table covers 0..110 so
     * every gamepad code is a direct array read.
     */
    static final int REMAP_TABLE_SIZE = 112;

    /**
     * Resolution of the precomputed curve tables. 256 entries over 0..1 bounds the maximum
     * quantisation error at about 0.002 output units, well below the drift epsilon and far
     * below anything a player can perceive, while keeping each table at 1 KB.
     */
    static final int CURVE_STEPS = 256;

    /** Smaller output than this is treated as centre, killing stick drift while idle. */
    private static final float DRIFT_EPSILON = 0.004f;

    /** Trigger floor applied when Low Input Delay is on, so a resting finger is not a press. */
    private static final float LOW_LATENCY_TRIGGER_DEAD_ZONE = 0.06f;

    private final float leftDeadZone;
    private final float rightDeadZone;
    private final float leftInvRange;
    private final float rightInvRange;
    private final float leftSensitivity;
    private final float rightSensitivity;
    private final int[] remapTable;
    /**
     * Held-modifier key for each alternate whole-map layer, and that layer's own flat table.
     *
     * Parallel arrays rather than a map so {@link #remapKey(int, int)} is a short linear scan over
     * a handful of ints — the layers are few and this runs inside {@code dispatchKeyEvent}, so it
     * must not allocate or hash. Empty when the profile has no layers, which is the common case.
     */
    private final int[] layerModifiers;
    private final int[][] layerRemapTables;
    private final float[] leftStickCurve;
    private final float[] rightStickCurve;
    private final TriggerCurve leftTrigger;
    private final TriggerCurve rightTrigger;
    private final boolean antiDriftEnabled;
    private final float leftDriftThreshold;
    private final float rightDriftThreshold;

    public ControllerResponse(ControllerProfile profile, boolean snappy) {
        this.leftDeadZone = clamp(profile == null ? ControllerProfile.DEFAULT_DEAD_ZONE : profile.getLeftDeadZone());
        this.rightDeadZone = clamp(profile == null ? ControllerProfile.DEFAULT_DEAD_ZONE : profile.getRightDeadZone());
        // Guard the divisor: a dead zone of 1.0 would make the range zero.
        this.leftInvRange = 1f / Math.max(0.01f, 1f - this.leftDeadZone);
        this.rightInvRange = 1f / Math.max(0.01f, 1f - this.rightDeadZone);
        this.leftSensitivity = profile == null ? ControllerProfile.DEFAULT_SENSITIVITY : profile.getLeftStickSensitivity();
        this.rightSensitivity = profile == null ? ControllerProfile.DEFAULT_SENSITIVITY : profile.getRightStickSensitivity();
        this.remapTable = buildRemapTable(profile);
        java.util.List<RemapLayer> layers = profile == null ? null : profile.getRemapLayers();
        int layerCount = layers == null ? 0 : layers.size();
        this.layerModifiers = new int[layerCount];
        this.layerRemapTables = new int[layerCount][];
        for (int i = 0; i < layerCount; i++) {
            RemapLayer layer = layers.get(i);
            this.layerModifiers[i] = layer.getModifierKeyCode();
            this.layerRemapTables[i] = buildLayerRemapTable(layer);
        }
        this.antiDriftEnabled = profile != null && profile.isAntiDriftEnabled();
        // Widen the dead zone to this pad's measured resting noise. Only when the toggle is on:
        // with it off the profile's own dead zone is exactly what the user chose.
        this.leftDriftThreshold = antiDriftEnabled
                ? StickCalibration.effectiveDeadZone(this.leftDeadZone,
                        profile.getLeftStickNoiseFloor())
                : this.leftDeadZone;
        this.rightDriftThreshold = antiDriftEnabled
                ? StickCalibration.effectiveDeadZone(this.rightDeadZone,
                        profile.getRightStickNoiseFloor())
                : this.rightDeadZone;

        StickCurve leftCurve = profile == null ? null : profile.getLeftCurve();
        StickCurve rightCurve = profile == null ? null : profile.getRightCurve();
        if (snappy) {
            // Low Input Delay used to hard-code an ease-out curve. A profile that already
            // selects its own curve must not have it silently replaced by the toggle, so only
            // supply the default while the profile is still on the identity curve.
            leftCurve = withLowLatencyCurve(leftCurve);
            rightCurve = withLowLatencyCurve(rightCurve);
        }
        this.leftStickCurve = buildCurveTable(leftCurve);
        this.rightStickCurve = buildCurveTable(rightCurve);

        this.leftTrigger = withLowLatencyTriggerFloor(profile == null ? null : profile.getLeftTriggerCurve(), snappy);
        this.rightTrigger = withLowLatencyTriggerFloor(profile == null ? null : profile.getRightTriggerCurve(), snappy);
    }

    private static StickCurve withLowLatencyCurve(StickCurve curve) {
        if (curve == null || curve.isIdentity()) {
            return StickCurve.Preset.BALANCED.toCurve();
        }
        return curve;
    }

    private static TriggerCurve withLowLatencyTriggerFloor(TriggerCurve curve, boolean snappy) {
        TriggerCurve effective = curve == null ? new TriggerCurve() : curve;
        if (!snappy) return effective;
        return new TriggerCurve(Math.max(effective.getDeadZone(), LOW_LATENCY_TRIGGER_DEAD_ZONE),
                effective.getExponent());
    }

    private static float[] buildCurveTable(StickCurve curve) {
        StickCurve effective = curve == null ? new StickCurve() : curve;
        float[] table = new float[CURVE_STEPS + 1];
        for (int i = 0; i <= CURVE_STEPS; i++) {
            table[i] = effective.apply(i / (float) CURVE_STEPS);
        }
        // Pin the ends so quantisation can never leave full deflection short of 1.0.
        table[0] = 0f;
        table[CURVE_STEPS] = 1f;
        return table;
    }

    private static int[] buildRemapTable(ControllerProfile profile) {
        int[] table = new int[REMAP_TABLE_SIZE];
        for (int code = 0; code < REMAP_TABLE_SIZE; code++) {
            // Identity by default; codes outside the table fall through to the identity path.
            table[code] = code;
        }
        if (profile != null) {
            for (java.util.Map.Entry<Integer, Integer> entry : profile.getButtonRemaps().entrySet()) {
                Integer from = entry.getKey();
                Integer to = entry.getValue();
                if (from == null || to == null) continue;
                if (from < 0 || from >= REMAP_TABLE_SIZE) continue;
                table[from] = to;
            }
        }
        return table;
    }

    private static int[] buildLayerRemapTable(RemapLayer layer) {
        int[] table = new int[REMAP_TABLE_SIZE];
        for (int code = 0; code < REMAP_TABLE_SIZE; code++) {
            table[code] = code;
        }
        if (layer != null) {
            for (java.util.Map.Entry<Integer, Integer> entry : layer.getRemaps().entrySet()) {
                Integer from = entry.getKey();
                Integer to = entry.getValue();
                if (from == null || to == null) continue;
                if (from < 0 || from >= REMAP_TABLE_SIZE) continue;
                table[from] = to;
            }
        }
        return table;
    }

    private static float clamp(float zone) {
        if (Float.isNaN(zone)) return ControllerProfile.DEFAULT_DEAD_ZONE;
        return Math.max(0f, Math.min(0.99f, zone));
    }

    /**
     * Whether a key code is a gamepad button rather than a keyboard key.
     *
     * <p>The joystick buttons are <em>not</em> one contiguous range: the lettered faces sit at
     * 96..110 while {@code KEYCODE_BUTTON_1..16} sit at 188..203, so a min/max test is impossible
     * and a modifier is only meaningful on a pad — a keyboard key that happens to share a numeric
     * code must not select a layer.
     */
    public static boolean isGamepadKeyCode(int keyCode) {
        return GAMEPAD_KEY_CODES.contains(keyCode);
    }

    private static final java.util.Set<Integer> GAMEPAD_KEY_CODES = buildGamepadKeyCodes();

    private static java.util.Set<Integer> buildGamepadKeyCodes() {
        java.util.HashSet<Integer> codes = new java.util.HashSet<>();
        // Lettered faces, shoulders, triggers, sticks and the menu pair (96..110).
        for (int code = android.view.KeyEvent.KEYCODE_BUTTON_A;
                code <= android.view.KeyEvent.KEYCODE_BUTTON_MODE; code++) {
            codes.add(code);
        }
        // Numbered face buttons (188..203).
        for (int code = android.view.KeyEvent.KEYCODE_BUTTON_1;
                code <= android.view.KeyEvent.KEYCODE_BUTTON_16; code++) {
            codes.add(code);
        }
        // The d-pad arrives as these key codes when a pad reports it as buttons.
        codes.add(android.view.KeyEvent.KEYCODE_DPAD_UP);
        codes.add(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        codes.add(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
        codes.add(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
        codes.add(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
        return java.util.Collections.unmodifiableSet(codes);
    }

    /** Whether a button is a modifier for one of this profile's layers. */
    public boolean isLayerModifier(int keyCode) {
        if (keyCode <= 0) return false;
        for (int modifier : layerModifiers) {
            if (modifier == keyCode) return true;
        }
        return false;
    }

    public int remapKey(int keyCode) {
        return remapKey(keyCode, 0);
    }

    /**
     * Remaps a key through the layer whose modifier is held, else through the base map.
     *
     * @param heldModifierKeyCode the key code of the modifier button currently held, or 0 for
     *        none. The base map's own remaps still apply to the layer's input, so a layer only
     *        needs to list the buttons whose meaning changes while the modifier is down.
     */
    public int remapKey(int keyCode, int heldModifierKeyCode) {
        if (keyCode <= 0) return keyCode;
        if (heldModifierKeyCode > 0) {
            for (int i = 0; i < layerModifiers.length; i++) {
                if (layerModifiers[i] != heldModifierKeyCode) continue;
                int[] table = layerRemapTables[i];
                if (keyCode < REMAP_TABLE_SIZE) {
                    int mapped = table[keyCode];
                    return mapped == keyCode ? remapTable[keyCode] : mapped;
                }
                return keyCode;
            }
        }
        if (keyCode < REMAP_TABLE_SIZE) {
            return remapTable[keyCode];
        }
        // Rare code outside the table: fall back to the map so remaps still apply.
        return keyCode;
    }

    /**
     * Applies dead zone, response curve and sensitivity to one stick axis.
     *
     * The curve exists because the previous linear mapping felt sluggish: after removing
     * the dead zone, small physical movement produced a proportionally small look/turn
     * rate, so aiming near centre was mushy. {@code snappy} applies an ease-out curve
     * that gives more output for the same physical travel while still reaching 1.0 at
     * full deflection, so it stays fully controllable at the edges.
     */
    public float adjustAxis(int axis, float value) {
        final boolean left = axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y;
        final boolean right = axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ;
        if (!left && !right) return value;

        final float deadZone = left ? leftDeadZone : rightDeadZone;
        final float invRange = left ? leftInvRange : rightInvRange;
        final float sensitivity = left ? leftSensitivity : rightSensitivity;
        final float[] curve = left ? leftStickCurve : rightStickCurve;

        final float magnitude = value < 0f ? -value : value;
        if (magnitude <= deadZone) return 0f;

        // Normalise past the dead zone into 0..1 without a division.
        float normalised = (magnitude - deadZone) * invRange;
        if (normalised > 1f) normalised = 1f;

        normalised = evaluate(curve, normalised);

        float output = normalised * sensitivity;
        if (output > 1f) output = 1f;

        if (output < DRIFT_EPSILON) return 0f;
        return value < 0f ? -output : output;
    }

    /** Table lookup with linear interpolation between the two nearest samples. */
    static float evaluate(float[] table, float normalised) {
        if (normalised <= 0f) return 0f;
        if (normalised >= 1f) return table[CURVE_STEPS];
        float scaled = normalised * CURVE_STEPS;
        int index = (int) scaled;
        if (index >= CURVE_STEPS) return table[CURVE_STEPS];
        float frac = scaled - index;
        float low = table[index];
        float high = table[index + 1];
        return low + (high - low) * frac;
    }

    /**
     * Applies dead zone, response curve and sensitivity to a stick pair at once.
     *
     * This is the radial (combined-magnitude) path, and it is what the anti-drift toggle
     * relies on. The single-axis {@link #adjustAxis} test compares each axis against the dead
     * zone independently, which cannot see a stick that rests slightly off-centre on one axis:
     * that axis alone sits just above the threshold while the other is at zero, so the pair
     * reads as a small constant diagonal push — ghost drift the player sees as a camera that
     * creeps on its own.
     *
     * Measuring {@code sqrt(x*x + y*y)} against the threshold and rescaling both axes together
     * fixes that, because a stick merely resting off-centre has a small magnitude no matter
     * which axes the offset lands on. It also makes the dead zone a circle rather than a
     * square, so diagonal travel is no longer flattened more aggressively than axial travel.
     *
     * The axes are returned through {@code out} to keep this allocation-free on the input path.
     *
     * @param left  true for the left stick, false for the right
     * @param x     x-axis value (AXIS_X for left, AXIS_Z for right)
     * @param y     y-axis value (AXIS_Y for left, AXIS_RZ for right)
     * @param out   receives the shaped x at index 0 and y at index 1
     * @return true when either axis changed
     */
    public boolean adjustStickPair(boolean left, float x, float y, float[] out) {
        return adjustStickPair(left, x, y, (float) Math.sqrt(x * x + y * y), out);
    }

    /**
     * Pair shaping with the magnitude already computed.
     *
     * The caller needs the magnitude anyway to consult its drift gate, so taking it here keeps
     * the hot path to one {@code sqrt} per stick per event instead of two.
     */
    public boolean adjustStickPair(boolean left, float x, float y, float magnitude, float[] out) {
        final float threshold = left ? leftDriftThreshold : rightDriftThreshold;
        final float invRange = left ? leftInvRange : rightInvRange;
        final float sensitivity = left ? leftSensitivity : rightSensitivity;
        final float[] curve = left ? leftStickCurve : rightStickCurve;

        if (magnitude <= threshold) {
            out[0] = 0f;
            out[1] = 0f;
            return x != 0f || y != 0f;
        }

        float normalised = (magnitude - threshold) * invRange;
        if (normalised > 1f) normalised = 1f;
        normalised = evaluate(curve, normalised);

        float shaped = normalised * sensitivity;
        if (shaped > 1f) shaped = 1f;
        if (shaped < DRIFT_EPSILON) {
            out[0] = 0f;
            out[1] = 0f;
            return x != 0f || y != 0f;
        }

        // Scale the original vector by the shaped-to-raw ratio so the direction the player is
        // pushing is preserved exactly; only the length changes. Dividing by the magnitude
        // would do the same arithmetic with more operations and a division.
        float scale = shaped / magnitude;
        float outX = x * scale;
        float outY = y * scale;
        out[0] = outX;
        out[1] = outY;
        return outX != x || outY != y;
    }

    /** The anti-drift threshold in force for one stick, as a magnitude. */
    public float driftThreshold(boolean left) {
        return left ? leftDriftThreshold : rightDriftThreshold;
    }

    /** Whether the anti-drift filter is on for this response. */
    public boolean isAntiDriftEnabled() {
        return antiDriftEnabled;
    }

    /**
     * Applies the profile's trigger curve to one analogue trigger axis.
     *
     * Unlike the stick path this never redistributes through a dead-zone divisor, and a value
     * inside the trigger dead zone becomes exactly zero, which is what stops a resting finger
     * from being read as a press.
     */
    public float adjustTrigger(int axis, float value) {
        final boolean left = axis == MotionEvent.AXIS_LTRIGGER || axis == MotionEvent.AXIS_BRAKE;
        final boolean right = axis == MotionEvent.AXIS_RTRIGGER || axis == MotionEvent.AXIS_GAS;
        if (!left && !right) return value;
        final TriggerCurve curve = left ? leftTrigger : rightTrigger;
        return curve.apply(value);
    }

    /** True for any axis this response is responsible for rewriting. */
    public boolean handlesAxis(int axis) {
        return axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y
                || axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ
                || axis == MotionEvent.AXIS_LTRIGGER || axis == MotionEvent.AXIS_RTRIGGER
                || axis == MotionEvent.AXIS_BRAKE || axis == MotionEvent.AXIS_GAS;
    }

    /**
     * Routes one axis through the stick curve or the trigger curve as appropriate, so the
     * event-rewriting path can share a single loop over the axes it owns.
     */
    public float adjustAxisOrTrigger(int axis, float value) {
        switch (axis) {
            case MotionEvent.AXIS_X:
            case MotionEvent.AXIS_Y:
            case MotionEvent.AXIS_Z:
            case MotionEvent.AXIS_RZ:
                return adjustAxis(axis, value);
            case MotionEvent.AXIS_LTRIGGER:
            case MotionEvent.AXIS_RTRIGGER:
            case MotionEvent.AXIS_BRAKE:
            case MotionEvent.AXIS_GAS:
                return adjustTrigger(axis, value);
            default:
                return value;
        }
    }

    /** True when the axis is pushed outside the dead zone. Non-stick axes have no dead zone. */
    public boolean isOutsideDeadZone(int axis, float value) {
        final boolean left = axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y;
        final boolean right = axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ;
        if (!left && !right) return true;
        final float deadZone = left ? leftDeadZone : rightDeadZone;
        final float magnitude = value < 0f ? -value : value;
        return magnitude > deadZone;
    }

    /**
     * Radial counterpart to {@link #isOutsideDeadZone}: true when the stick pair's combined
     * magnitude clears the threshold.
     *
     * This is the test that catches diagonal rest drift. Checking one axis at a time reports
     * "outside" for an axis sitting just over the threshold even when the pair's magnitude
     * says the stick is at rest, which is why the per-axis form alone cannot gate the anti-drift
     * path.
     */
    public boolean isPairOutsideThreshold(boolean left, float x, float y) {
        final float threshold = left ? leftDriftThreshold : rightDriftThreshold;
        return (float) Math.sqrt(x * x + y * y) > threshold;
    }
}