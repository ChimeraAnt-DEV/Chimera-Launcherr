package org.chimeramc.client.launcher.controller;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitRegistrationMod;
import org.chimeramc.client.settings.FeatureSettings;

/**
 * Applies the active controller profile to the real gameplay input pipeline.
 *
 * This class sits directly in {@code MinecraftActivity.dispatchKeyEvent} and
 * {@code dispatchGenericMotionEvent}, so it runs on every controller event, on the UI
 * thread, before the game processes the input. Any work done here is added straight to
 * input-to-photon latency, which is why:
 *
 * - No method on the read path is {@code synchronized}. The active response is published
 *   as one immutable, {@code volatile} reference, so readers never take a lock and never
 *   see a half-applied profile. The previous per-method locking contended with the UI
 *   thread on every single event.
 * - All profile-derived math (dead-zone range, sensitivity, remap table) is precomputed
 *   once in {@link ControllerResponse} instead of per event.
 *
 * With "Low Input Delay" enabled the dead zone is tightened and an ease-out response curve
 * is applied, so the same physical stick travel produces more look/turn output. That is a
 * real reduction in how far the stick must move before the game reacts.
 */
public final class ControllerInputProcessor {
    private static volatile ControllerResponse active;
    private static volatile ControllerType activeType;

    /**
     * Hysteresis gates for the two sticks, published together with the response.
     *
     * These hold per-stick run state and advance once per event, so they cannot live in the
     * immutable {@link ControllerResponse}. They are only read and written from the UI thread
     * inside {@code dispatchGenericMotionEvent}, which is why they are plain fields rather than
     * something synchronised: a lock here would be taken on every controller event on the
     * input-to-photon path.
     */
    private static final StickDriftGate leftDriftGate = new StickDriftGate();
    private static final StickDriftGate rightDriftGate = new StickDriftGate();

    /** Scratch for pair shaping, reused so the hot path does not allocate. One per stick. */
    private static final float[] leftPairScratch = new float[2];
    private static final float[] rightPairScratch = new float[2];

    /**
     * Analogue axes the dead zone must never swallow. Trigger and hat axes are the ones a pad
     * actually reports alongside the sticks; scroll is here because discarding a scroll event
     * would break overlay menu scrolling.
     */
    private static final int[] NON_STICK_AXES = {
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_VSCROLL,
            MotionEvent.AXIS_HSCROLL,
    };

    /**
     * Dead zone used when Low Input Delay is on. The profile's own dead zone is still the
     * floor; this only tightens the default so drifting sticks do not cancel it out.
     */
    private static final float LOW_LATENCY_DEAD_ZONE = 0.06f;

    private ControllerInputProcessor() {
    }

    public static void setActiveProfile(ControllerType type, ControllerProfile profile) {
        setActiveProfile(type, profile, isLowInputDelayEnabled());
    }

    public static void setActiveProfile(ControllerType type, ControllerProfile profile, boolean lowInputDelay) {
        activeType = type;
        // A partial run belongs to the profile that was active when it started. Carrying it
        // into a different profile could let a suppressed crossing count toward the new one.
        leftDriftGate.reset();
        rightDriftGate.reset();
        if (profile == null) {
            active = null;
            return;
        }
        ControllerProfile effective = lowInputDelay ? tightenDeadZone(profile) : profile;
        // Single volatile publication: the response is fully built before it is visible.
        active = new ControllerResponse(effective, lowInputDelay);
    }

    /**
     * Rebuilds the active response after the user flips a setting that affects it. Cheap:
     * it only runs on an explicit toggle/profile change, never per input event.
     */
    public static void refresh(ControllerProfile profile) {
        if (profile == null) return;
        setActiveProfile(activeType, profile, isLowInputDelayEnabled());
    }

    /**
     * Reloads the active profile for the current controller and rebuilds its response.
     * Call after toggling Low Input Delay or editing a profile so a running session picks
     * the change up without a restart.
     */
    public static void reload(Context context) {
        ControllerType type = activeType;
        if (type == null) {
            detectAndLoad(context);
            return;
        }
        ControllerProfile profile = new ControllerProfileManager(context).getActiveProfile(type);
        refresh(profile);
    }

    private static boolean isLowInputDelayEnabled() {
        FeatureSettings settings = FeatureSettings.getInstance();
        return settings != null && settings.isLowInputDelayEnabled();
    }

    private static ControllerProfile tightenDeadZone(ControllerProfile profile) {
        ControllerProfile effective = profile.copy();
        effective.setLeftDeadZone(Math.min(profile.getLeftDeadZone(), LOW_LATENCY_DEAD_ZONE));
        effective.setRightDeadZone(Math.min(profile.getRightDeadZone(), LOW_LATENCY_DEAD_ZONE));
        return effective;
    }

    public static ControllerType getActiveType() {
        return activeType;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static void detectAndLoad(Context context) {
        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                ControllerType type = ControllerType.from(device);
                if (type != null) {
                    ControllerProfile profile = new ControllerProfileManager(context).getActiveProfile(type);
                    setActiveProfile(type, profile);
                    return;
                }
            }
        }
    }

    /**
     * Applies a per-instance controller profile binding, if the instance has one.
     *
     * Called when a session starts so an instance can bring its own controller setup. The
     * bound controller type wins over whatever is currently connected: a player who binds a
     * profile to an instance expects that instance to use it, and the alternative (silently
     * ignoring the binding when a different pad is plugged in) makes the binding look broken.
     *
     * @return true when a binding was applied
     */
    public static boolean applyInstanceBinding(Context context, String profileId) {
        if (context == null || profileId == null || profileId.trim().isEmpty()) return false;
        ControllerProfileManager manager = new ControllerProfileManager(context);
        ControllerProfileManager.Binding binding = manager.getBinding(profileId);
        if (binding == null) return false;
        ControllerProfile profile = manager.getProfile(binding.type, binding.slot);
        if (profile == null) return false;
        setActiveProfile(binding.type, profile);
        return true;
    }

    /**
     * Applies instance bindings for any instance id that matches, used when the bound
     * instance is already known at selection time rather than at launch.
     */
    public static void reloadForInstance(Context context, String profileId) {
        if (context == null) return;
        if (!applyInstanceBinding(context, profileId)) {
            detectAndLoad(context);
        }
    }

    public static int remapKey(int keyCode) {
        ControllerResponse response = active;
        if (response == null || keyCode <= 0) {
            return keyCode;
        }
        return response.remapKey(keyCode);
    }

    public static float adjustAxisValue(int axis, float value) {
        ControllerResponse response = active;
        if (response == null) {
            return value;
        }
        return response.adjustAxis(axis, value);
    }

    /**
     * Rewrites a motion event's stick and trigger axes through the active profile and returns
     * the event the game should see.
     *
     * This is what makes the response-curve and trigger-curve editors observable in gameplay.
     * Swallowing in-dead-zone events (see {@link #isWithinDeadZone}) is not enough on its own:
     * the game reads the axis values off the event, so without rewriting them a curve would
     * change nothing the player can feel.
     *
     * Allocates only when an axis actually changes, so an all-default profile (and every
     * non-joystick event) stays on the zero-allocation path. When it does allocate, the caller
     * owns the returned event; the original is returned untouched otherwise, so callers must
     * compare identity before recycling.
     */
    public static MotionEvent transformMotionEvent(MotionEvent event) {
        ControllerResponse response = active;
        if (response == null || event == null) {
            return event;
        }
        int sources = event.getSource();
        if ((sources & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                && (sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            return event;
        }

        int pointerCount = event.getPointerCount();
        if (pointerCount <= 0) return event;

        // The stick axes are always shaped as two pairs so the dead-zone decision uses the
        // combined magnitude. The radial test is the *correct* dead zone on its own: a per-axis
        // test cannot see a stick resting off-centre on one axis, and it demands more travel on
        // a diagonal than on an axis (each axis must clear the threshold separately), which the
        // player feels as having to shove the stick before the camera moves.
        //
        // The hysteresis gate is the separate, opt-in anti-drift feature: it needs the magnitude
        // too, so the pair shaping is shared and only the gate is conditional.
        boolean pairShaping = pointerCount == 1;
        boolean antiDrift = response.isAntiDriftEnabled() && pairShaping;
        float[] leftPair = null;
        float[] rightPair = null;
        if (pairShaping) {
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            float y = event.getAxisValue(MotionEvent.AXIS_Y);
            float z = event.getAxisValue(MotionEvent.AXIS_Z);
            float rz = event.getAxisValue(MotionEvent.AXIS_RZ);
            long time = event.getEventTime();
            leftPair = shapePair(response, leftDriftGate, true, x, y, time, antiDrift, leftPairScratch);
            rightPair = shapePair(response, rightDriftGate, false, z, rz, time, antiDrift, rightPairScratch);
        }

        // Read through PointerCoords rather than MotionEvent.getAxisValue(axis, pointerIndex),
        // which only exists from API 29; this project supports API 28.
        //
        // The scratch arrays are cached rather than allocated: this runs on every controller
        // motion event, so a per-event allocation is a per-event cost on the input-to-photon
        // path. MotionEvent.obtain copies their contents, so reuse after the call is safe. Only
        // touched from the UI thread, hence plain fields.
        if (pointerCount > MAX_POOLED_POINTERS) {
            return transformWithAllocation(event, response, pairShaping, leftPair, rightPair, pointerCount);
        }
        if (rewrittenScratch == null) {
            rewrittenScratch = new float[MAX_POOLED_POINTERS][TRANSFORM_AXES.length];
            pooledCoords = new MotionEvent.PointerCoords[MAX_POOLED_POINTERS];
            pooledProperties = new MotionEvent.PointerProperties[MAX_POOLED_POINTERS];
            pooledReadCoords = new MotionEvent.PointerCoords();
            for (int i = 0; i < MAX_POOLED_POINTERS; i++) {
                pooledCoords[i] = new MotionEvent.PointerCoords();
                pooledProperties[i] = new MotionEvent.PointerProperties();
            }
        }

        boolean changed = false;
        for (int p = 0; p < pointerCount; p++) {
            event.getPointerCoords(p, pooledReadCoords);
            for (int a = 0; a < TRANSFORM_AXES.length; a++) {
                int axis = TRANSFORM_AXES[a];
                float original = pooledReadCoords.getAxisValue(axis);
                float updated = rewrittenAxis(response, pairShaping, leftPair, rightPair, axis, original);
                rewrittenScratch[p][a] = updated;
                if (updated != original) {
                    changed = true;
                }
            }
        }
        if (!changed) return event;

        for (int p = 0; p < pointerCount; p++) {
            event.getPointerProperties(p, pooledProperties[p]);
            event.getPointerCoords(p, pooledCoords[p]);
            for (int a = 0; a < TRANSFORM_AXES.length; a++) {
                pooledCoords[p].setAxisValue(TRANSFORM_AXES[a], rewrittenScratch[p][a]);
            }
        }

        return MotionEvent.obtain(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                pointerCount,
                pooledProperties,
                pooledCoords,
                event.getMetaState(),
                event.getButtonState(),
                event.getXPrecision(),
                event.getYPrecision(),
                event.getDeviceId(),
                event.getEdgeFlags(),
                event.getSource(),
                event.getFlags());
    }

    /** Resolves one axis through the profile, honouring the pair result when there is one. */
    private static float rewrittenAxis(ControllerResponse response, boolean pairShaping,
                                       float[] leftPair, float[] rightPair, int axis, float original) {
        if (pairShaping && leftPair != null) {
            if (axis == MotionEvent.AXIS_X) return leftPair[0];
            if (axis == MotionEvent.AXIS_Y) return leftPair[1];
            if (axis == MotionEvent.AXIS_Z) return rightPair[0];
            if (axis == MotionEvent.AXIS_RZ) return rightPair[1];
        }
        return response.adjustAxisOrTrigger(axis, original);
    }

    /** Rare path for pads reporting more pointers than the pool holds; allocates as before. */
    private static MotionEvent transformWithAllocation(
            MotionEvent event, ControllerResponse response, boolean pairShaping,
            float[] leftPair, float[] rightPair, int pointerCount) {
        boolean changed = false;
        float[][] rewritten = new float[pointerCount][TRANSFORM_AXES.length];
        MotionEvent.PointerCoords scratch = new MotionEvent.PointerCoords();
        for (int p = 0; p < pointerCount; p++) {
            event.getPointerCoords(p, scratch);
            for (int a = 0; a < TRANSFORM_AXES.length; a++) {
                int axis = TRANSFORM_AXES[a];
                float original = scratch.getAxisValue(axis);
                float updated = rewrittenAxis(response, pairShaping, leftPair, rightPair, axis, original);
                rewritten[p][a] = updated;
                if (updated != original) {
                    changed = true;
                }
            }
        }
        if (!changed) return event;

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        for (int p = 0; p < pointerCount; p++) {
            properties[p] = new MotionEvent.PointerProperties();
            event.getPointerProperties(p, properties[p]);
            coords[p] = new MotionEvent.PointerCoords();
            event.getPointerCoords(p, coords[p]);
            for (int a = 0; a < TRANSFORM_AXES.length; a++) {
                coords[p].setAxisValue(TRANSFORM_AXES[a], rewritten[p][a]);
            }
        }

        return MotionEvent.obtain(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                pointerCount,
                properties,
                coords,
                event.getMetaState(),
                event.getButtonState(),
                event.getXPrecision(),
                event.getYPrecision(),
                event.getDeviceId(),
                event.getEdgeFlags(),
                event.getSource(),
                event.getFlags());
    }

    /** Max simultaneous pointers the scratch pool covers; gamepads report one. */
    private static final int MAX_POOLED_POINTERS = 8;
    private static float[][] rewrittenScratch;
    private static MotionEvent.PointerCoords[] pooledCoords;
    private static MotionEvent.PointerProperties[] pooledProperties;
    private static MotionEvent.PointerCoords pooledReadCoords;

    /** Axes the profile rewrites on the way to the game. */
    private static final int[] TRANSFORM_AXES = {
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS,
    };

    public static float adjustTriggerValue(int axis, float value) {
        ControllerResponse response = active;
        if (response == null) {
            return value;
        }
        return response.adjustTrigger(axis, value);
    }

    /**
     * Whether the event's stick axes are all inside the dead zone, meaning the game should
     * not see the event at all.
     *
     * With anti-drift off this stays on the precomputed per-axis dead zone, which is cheap and
     * is the historical behaviour. With it on the decision moves to the combined magnitude
     * plus the hysteresis gate, because a per-axis test cannot see a stick resting off-centre
     * on one axis — that axis alone sits just over the threshold and the pair reads as a small
     * constant push.
     *
     * Only ever reports true when a stick axis actually carries movement. Returning true for an
     * all-zero event would swallow controller button presses, which also arrive through
     * {@code dispatchGenericMotionEvent} with no stick deflection.
     */
    public static boolean isWithinDeadZone(MotionEvent event) {
        ControllerResponse response = active;
        if (response == null || event == null) {
            return false;
        }
        int sources = event.getSource();
        if ((sources & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                && (sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            return false;
        }
        // Only an event whose *entire* content is suppressed stick movement may be swallowed.
        // A pad reports every axis in one event, so a constant drift offset would otherwise ride
        // along with an analogue trigger pull and the swallow would discard the pull — breaking
        // triggers on exactly the drifting pads this filter is meant to help.
        if (hasNonStickContent(event)) {
            return false;
        }
        if (response.isAntiDriftEnabled() && event.getPointerCount() == 1) {
            return isDriftSuppressed(response, event);
        }
        // The radial test, matching the shaping path. Judging each axis separately here would
        // swallow an event whose diagonal push the radial dead zone legitimately lets through,
        // so the check and the rewrite would disagree about the same event.
        if (event.getPointerCount() == 1) {
            return isPairFlattened(response, event);
        }
        return isFlattened(response, event, MotionEvent.AXIS_X)
                || isFlattened(response, event, MotionEvent.AXIS_Y)
                || isFlattened(response, event, MotionEvent.AXIS_Z)
                || isFlattened(response, event, MotionEvent.AXIS_RZ);
    }

    /** True when both stick pairs sit inside the radial dead zone, i.e. the event is all drift. */
    private static boolean isPairFlattened(ControllerResponse response, MotionEvent event) {
        return isPairInsideDeadZone(response, true, event.getAxisValue(MotionEvent.AXIS_X),
                event.getAxisValue(MotionEvent.AXIS_Y))
                && isPairInsideDeadZone(response, false, event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ));
    }

    private static boolean isPairInsideDeadZone(ControllerResponse response, boolean left,
                                                float x, float y) {
        if (x == 0f && y == 0f) return true;
        return !response.isPairOutsideThreshold(left, x, y);
    }

    /**
     * True when the event carries input the dead zone has no business discarding: triggers, the
     * D-pad hat, scroll, or any other analogue control a pad might expose.
     */
    private static boolean hasNonStickContent(MotionEvent event) {
        for (int i = 0; i < NON_STICK_AXES.length; i++) {
            if (event.getAxisValue(NON_STICK_AXES[i]) != 0f) {
                return true;
            }
        }
        return false;
    }

    /**
     * The anti-drift verdict for one event: true when every deflected stick is still being held
     * back, so the event carries nothing the game should act on.
     *
     * The gates are advanced here rather than in the transform step because this is the first
     * thing the dispatch path calls, and a crossing that gets swallowed must still count toward
     * the run that eventually lets movement through.
     */
    private static boolean isDriftSuppressed(ControllerResponse response, MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);
        float z = event.getAxisValue(MotionEvent.AXIS_Z);
        float rz = event.getAxisValue(MotionEvent.AXIS_RZ);
        if (x == 0f && y == 0f && z == 0f && rz == 0f) {
            return false;
        }
        long time = event.getEventTime();
        boolean leftAllowed = allowed(leftDriftGate, response, true, x, y, time);
        boolean rightAllowed = allowed(rightDriftGate, response, false, z, rz, time);
        return !leftAllowed && !rightAllowed;
    }

    private static boolean allowed(StickDriftGate gate, ControllerResponse response,
                                   boolean left, float x, float y, long time) {
        if (x == 0f && y == 0f) {
            // Nothing deflected on this stick: no run to keep, and nothing to let through.
            gate.reset();
            return false;
        }
        float magnitude = (float) Math.sqrt(x * x + y * y);
        return gate.allow(magnitude, response.driftThreshold(left), time);
    }

    private static boolean isFlattened(ControllerResponse response, MotionEvent event, int axis) {
        float value = event.getAxisValue(axis);
        return value != 0f && !response.isOutsideDeadZone(axis, value);
    }

    /**
     * Shapes one stick pair, honouring the hysteresis gate when anti-drift is on.
     *
     * The gate is opt-in: with it off the stick is shaped purely by the radial dead zone, which
     * is the behaviour the player expects from the profile's own setting. A stick the gate is
     * still holding back is reported as centred rather than shaped, so a wobbling stick cannot
     * leak a small push just because it nominally cleared the dead zone. The gate is consulted
     * at the event's own timestamp, which is what lets the dead-zone check and this rewrite
     * agree about a single event without double-counting it.
     */
    private static float[] shapePair(ControllerResponse response, StickDriftGate gate,
                                     boolean left, float x, float y, long time, boolean antiDrift,
                                     float[] out) {
        if (x == 0f && y == 0f) {
            gate.reset();
            out[0] = 0f;
            out[1] = 0f;
            return out;
        }
        float magnitude = (float) Math.sqrt(x * x + y * y);
        if (antiDrift && !gate.allow(magnitude, response.driftThreshold(left), time)) {
            out[0] = 0f;
            out[1] = 0f;
            return out;
        }
        if (!antiDrift) gate.reset();
        response.adjustStickPair(left, x, y, magnitude, out);
        // The right stick is the look stick, so hit-registration shaping applies there and not
        // to movement. Reuses the caller's buffer so the hot path still allocates nothing.
        if (!left && HitRegistrationMod.isActive()) {
            HitRegistrationMod.shapeLookDelta(out[0], out[1], out);
        }
        return out;
    }

    public static int processKeyEvent(int keyCode) {
        ControllerResponse response = active;
        if (response == null || keyCode <= 0) {
            return keyCode;
        }
        return response.remapKey(keyCode);
    }
}