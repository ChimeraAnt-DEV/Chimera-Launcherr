package org.chimeramc.client.launcher.controller;

import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

public class ControllerProfile {
    public static final int MAX_SLOTS = 5;
    /**
     * Default dead zone, as a radius of the stick's travel.
     *
     * This is a radial threshold, so it is deliberately lower than the per-axis 0.15 an earlier
     * build used: a per-axis test required 0.15 on <em>each</em> axis, i.e. about 0.21 combined
     * on a diagonal, which the player felt as having to shove the stick before the camera moved.
     * 0.08 on the radius is comfortably above the resting noise of a healthy pad while leaving
     * small deliberate movements responsive.
     */
    public static final float DEFAULT_DEAD_ZONE = 0.08f;
    public static final float DEFAULT_SENSITIVITY = 1.0f;
    public static final float MIN_SENSITIVITY = 0.25f;
    public static final float MAX_SENSITIVITY = 3.0f;

    private String name;
    private final Map<Integer, Integer> buttonRemaps = new HashMap();
    private float leftDeadZone = DEFAULT_DEAD_ZONE;
    private float rightDeadZone = DEFAULT_DEAD_ZONE;
    private float leftStickSensitivity = DEFAULT_SENSITIVITY;
    private float rightStickSensitivity = DEFAULT_SENSITIVITY;
    private boolean vibrationEnabled = true;

    // Curve shape is persisted as primitives rather than as the curve objects themselves.
    // Gson deserialises through Unsafe and does not run constructors, so a curve type whose
    // fields are final would be populated unpredictably, and a profile saved by an older
    // build has no curve fields at all — which must read back as the identity curve, not
    // as null.
    private String leftCurveKind = StickCurve.Kind.LINEAR.name();
    private float leftCurveExponent = 1f;
    private String rightCurveKind = StickCurve.Kind.LINEAR.name();
    private float rightCurveExponent = 1f;

    private float leftTriggerDeadZone = TriggerCurve.DEFAULT_DEAD_ZONE;
    private float leftTriggerExponent = TriggerCurve.DEFAULT_EXPONENT;
    private float rightTriggerDeadZone = TriggerCurve.DEFAULT_DEAD_ZONE;
    private float rightTriggerExponent = TriggerCurve.DEFAULT_EXPONENT;

    // Anti-drift is opt-in, so switching it on cannot silently change how an existing profile
    // feels. The noise floors are measured per stick by StickCalibration; 0 means "never
    // calibrated", which leaves the profile's own dead zone in charge.
    private boolean antiDriftEnabled = false;
    private float leftStickNoiseFloor = 0f;
    private float rightStickNoiseFloor = 0f;

    /**
     * Per-button click limits and hold-to-repeat rates, keyed by key code.
     *
     * Kept in the profile so they travel with it: a profile that exists to stop double-fire on a
     * worn pad should carry that with it, not need re-setting per install. An absent key means
     * off, which is why these are sparse maps rather than a fixed array.
     */
    private final Map<Integer, Integer> clickLimits = new HashMap<>();
    private final Map<Integer, Integer> repeatRates = new HashMap<>();

    /**
     * Strength of the pad's rumble, as a percentage.
     *
     * Stored on the profile rather than globally so a player who keeps one pad for racing and one
     * for Minecraft can set them differently. {@link #vibrationEnabled} is the on/off switch and
     * this is the magnitude; 0 reads as off even when the switch is on.
     */
    private int rumbleStrength = RumbleCurve.DEFAULT_STRENGTH;

    /**
     * Alternate whole-button maps, each in force only while its modifier key is held.
     *
     * The profile's own {@code buttonRemaps} is layer 0 and is always active; these are the extra
     * layers on top. An empty list (a profile saved before layers existed) reads back as "no
     * layers", which is the identity behaviour.
     */
    private final java.util.List<RemapLayer> remapLayers = new java.util.ArrayList<>();

    public ControllerProfile() {
        this("Profile");
    }

    public ControllerProfile(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Integer, Integer> getButtonRemaps() {
        return buttonRemaps;
    }

    public int remapKey(int keyCode) {
        if (keyCode <=  0) {
            return keyCode;
        }
        Integer mapped = buttonRemaps.get(keyCode);
        return mapped != null ? mapped : keyCode;
    }

    public void setRemap(int fromKeyCode, int toKeyCode) {
        if (fromKeyCode <=  0) {
            return;
        }
        if (toKeyCode <=  0 || toKeyCode == fromKeyCode) {
            buttonRemaps.remove(fromKeyCode);
        } else {
            buttonRemaps.put(fromKeyCode, toKeyCode);
        }
    }

    public float getLeftDeadZone() {
        return leftDeadZone;

    }

    public float getRightDeadZone() {
        return rightDeadZone;

    }

    public float getLeftStickSensitivity() {
        return leftStickSensitivity;

    }

    public float getRightStickSensitivity() {
        return rightStickSensitivity;

    }

    public void setLeftDeadZone(float zone) {
        leftDeadZone = clampDeadZone(zone);
    }

    public void setRightDeadZone(float zone) {
        rightDeadZone = clampDeadZone(zone);
    }

    public void setLeftStickSensitivity(float sensitivity) {
        leftStickSensitivity = clampSensitivity(sensitivity);
    }

    public void setRightStickSensitivity(float sensitivity) {
        rightStickSensitivity = clampSensitivity(sensitivity);
    }

    public boolean isVibrationEnabled() {
        return vibrationEnabled;

    }

    public void setVibrationEnabled(boolean enabled) {
        vibrationEnabled = enabled;

    }

    public StickCurve getLeftCurve() {
        return StickCurve.parse(leftCurveKind, leftCurveExponent);
    }

    public void setLeftCurve(StickCurve curve) {
        applyCurve(curve, true);
    }

    public StickCurve getRightCurve() {
        return StickCurve.parse(rightCurveKind, rightCurveExponent);
    }

    public void setRightCurve(StickCurve curve) {
        applyCurve(curve, false);
    }

    private void applyCurve(StickCurve curve, boolean left) {
        StickCurve effective = curve == null ? new StickCurve() : curve;
        if (left) {
            leftCurveKind = effective.getKind().name();
            leftCurveExponent = effective.getExponent();
        } else {
            rightCurveKind = effective.getKind().name();
            rightCurveExponent = effective.getExponent();
        }
    }

    public TriggerCurve getLeftTriggerCurve() {
        return new TriggerCurve(leftTriggerDeadZone, leftTriggerExponent);
    }

    public void setLeftTriggerCurve(TriggerCurve curve) {
        applyTriggerCurve(curve, true);
    }

    public TriggerCurve getRightTriggerCurve() {
        return new TriggerCurve(rightTriggerDeadZone, rightTriggerExponent);
    }

    public void setRightTriggerCurve(TriggerCurve curve) {
        applyTriggerCurve(curve, false);
    }

    private void applyTriggerCurve(TriggerCurve curve, boolean left) {
        TriggerCurve effective = curve == null ? new TriggerCurve() : curve;
        if (left) {
            leftTriggerDeadZone = effective.getDeadZone();
            leftTriggerExponent = effective.getExponent();
        } else {
            rightTriggerDeadZone = effective.getDeadZone();
            rightTriggerExponent = effective.getExponent();
        }
    }

    private static float clampDeadZone(float zone) {
        if (Float.isNaN(zone)) return DEFAULT_DEAD_ZONE;
        return Math.max(0f, Math.min(0.9f, zone));
    }

    public boolean isAntiDriftEnabled() {
        return antiDriftEnabled;
    }

    public void setAntiDriftEnabled(boolean enabled) {
        antiDriftEnabled = enabled;
    }

    /** Measured resting magnitude of the left stick, or 0 when it was never calibrated. */
    public float getLeftStickNoiseFloor() {
        return leftStickNoiseFloor;
    }

    public float getRightStickNoiseFloor() {
        return rightStickNoiseFloor;
    }

    public void setLeftStickNoiseFloor(float floor) {
        leftStickNoiseFloor = clampNoiseFloor(floor);
    }

    public void setRightStickNoiseFloor(float floor) {
        rightStickNoiseFloor = clampNoiseFloor(floor);
    }

    private static float clampNoiseFloor(float floor) {
        if (Float.isNaN(floor) || floor <= 0f) return 0f;
        return Math.min(1f, floor);
    }

    private static float clampSensitivity(float sensitivity) {
        if (Float.isNaN(sensitivity)) return DEFAULT_SENSITIVITY;
        return Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, sensitivity));
    }

    /** The clicks-per-second cap for a button, or 0 when unlimited. */
    public int getClickLimit(int keyCode) {
        Integer value = clickLimits.get(keyCode);
        return value == null ? CpsLimiter.OFF : value;
    }

    public void setClickLimit(int keyCode, int cps) {
        if (cps <= CpsLimiter.OFF) {
            clickLimits.remove(keyCode);
        } else {
            clickLimits.put(keyCode, Math.min(cps, CpsLimiter.MAX_CPS));
        }
    }

    /** The hold-to-repeat rate for a button, or 0 when off. */
    public int getRepeatRate(int keyCode) {
        Integer value = repeatRates.get(keyCode);
        return value == null ? CpsLimiter.OFF : value;
    }

    public void setRepeatRate(int keyCode, int cps) {
        if (cps <= CpsLimiter.OFF) {
            repeatRates.remove(keyCode);
        } else {
            repeatRates.put(keyCode, Math.min(cps, CpsLimiter.MAX_CPS));
        }
    }

    /** Buttons with a repeat configured, for the settings UI and the warning. */
    public Map<Integer, Integer> getRepeatRates() {
        return new HashMap<>(repeatRates);
    }

    /** Rumble magnitude as a percentage; 0 means off even when vibration is enabled. */
    public int getRumbleStrength() {
        return rumbleStrength;
    }

    public void setRumbleStrength(int percent) {
        rumbleStrength = RumbleCurve.clampStrength(percent);
    }

    /** The extra whole-map layers, in hold order. The returned list is the live one. */
    public java.util.List<RemapLayer> getRemapLayers() {
        return remapLayers;
    }

    public void addRemapLayer(RemapLayer layer) {
        if (layer != null) remapLayers.add(layer);
    }

    public void removeRemapLayer(int index) {
        if (index >= 0 && index < remapLayers.size()) remapLayers.remove(index);
    }

    /**
     * The layer whose modifier is currently held, or null for the base map.
     *
     * First match in list order wins, so the layer declared first takes priority when two layers
     * share a modifier.
     */
    public RemapLayer activeLayer(int heldKeyCode) {
        if (heldKeyCode <= 0) return null;
        for (RemapLayer layer : remapLayers) {
            if (layer.getModifierKeyCode() == heldKeyCode) return layer;
        }
        return null;
    }

    public ControllerProfile copy() {
        ControllerProfile copy = new ControllerProfile(name);
        copy.leftDeadZone = leftDeadZone;

        copy.rightDeadZone = rightDeadZone;

        copy.leftStickSensitivity = leftStickSensitivity;

        copy.rightStickSensitivity = rightStickSensitivity;

        copy.vibrationEnabled = vibrationEnabled;

        copy.leftCurveKind = leftCurveKind;
        copy.leftCurveExponent = leftCurveExponent;
        copy.rightCurveKind = rightCurveKind;
        copy.rightCurveExponent = rightCurveExponent;
        copy.leftTriggerDeadZone = leftTriggerDeadZone;
        copy.leftTriggerExponent = leftTriggerExponent;
        copy.rightTriggerDeadZone = rightTriggerDeadZone;
        copy.rightTriggerExponent = rightTriggerExponent;

        copy.antiDriftEnabled = antiDriftEnabled;
        copy.leftStickNoiseFloor = leftStickNoiseFloor;
        copy.rightStickNoiseFloor = rightStickNoiseFloor;

        copy.buttonRemaps.clear();
        copy.buttonRemaps.putAll(buttonRemaps);
        copy.clickLimits.clear();
        copy.clickLimits.putAll(clickLimits);
        copy.repeatRates.clear();
        copy.repeatRates.putAll(repeatRates);
        copy.rumbleStrength = rumbleStrength;
        copy.remapLayers.clear();
        for (RemapLayer layer : remapLayers) {
            copy.remapLayers.add(layer.copy());
        }
        return copy;

    }

    public static boolean isAxisStick(int axis) {
        return axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y
                || axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ;
    }
}