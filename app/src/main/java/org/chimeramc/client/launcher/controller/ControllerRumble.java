package org.chimeramc.client.launcher.controller;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.InputDevice;

/**
 * Sends a rumble to the connected controller.
 *
 * <p>Android only exposes direct controller vibration from API 31 ({@link InputDevice#vibrate}).
 * Below that there is no supported path to the pad's actuators, so the test button falls back to
 * the phone's own vibrator — the player still gets a confirmation the tap landed, and the caller
 * is told which happened rather than being left thinking the pad rumbled.
 */
public final class ControllerRumble {

    private ControllerRumble() {
    }

    /**
     * Vibrates the connected pad for {@code durationMs} at {@code amplitude} (1..255).
     *
     * @return true when the controller itself vibrated; false when it could not (unsupported API
     *         level, no pad, or the pad rejected the call) so the caller can fall back.
     */
    public static boolean play(Context context, ControllerType type, int amplitude) {
        if (context == null || amplitude <= 0) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        InputDevice device = findPad(context, type);
        if (device == null) return false;
        try {
            return Api31.vibrate(device, amplitude);
        } catch (Throwable t) {
            // A pad that advertises vibration but rejects the call must not take the caller down.
            return false;
        }
    }

    private static InputDevice findPad(Context context, ControllerType type) {
        InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (im == null) return null;
        InputDevice fallback = null;
        for (int id : im.getInputDeviceIds()) {
            InputDevice device = im.getInputDevice(id);
            if (!ControllerConnectionMonitor.isGamepad(device)) continue;
            if (fallback == null) fallback = device;
            if (type != null && type.matches(device)) return device;
        }
        return fallback;
    }

    /**
     * The API 31+ vibration call, kept in a nested holder.
     *
     * {@code InputDevice.getVibrator} does not exist below API 31, so referencing it from a method
     * a 28 device can reach would throw {@code NoClassDefFoundError} at class-init time — the same
     * trap {@code ThermalGovernor}'s nested holder exists to avoid. The {@code SDK_INT} guard in
     * {@link #play} runs first, and this holder is only ever loaded behind it. A pad that has no
     * actuator returns a null Vibrator, which must be a false result rather than a crash.
     */
    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    private static final class Api31 {
        static boolean vibrate(InputDevice device, int amplitude) {
            android.os.Vibrator vibrator = device.getVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) return false;
            android.os.VibrationEffect effect =
                    android.os.VibrationEffect.createOneShot(120L, amplitude);
            vibrator.vibrate(effect);
            return true;
        }
    }
}
