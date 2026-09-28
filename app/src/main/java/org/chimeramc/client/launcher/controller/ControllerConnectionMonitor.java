package org.chimeramc.client.launcher.controller;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;

/**
 * Watches for controllers being connected and disconnected anywhere in the app.
 *
 * <p>Device add/remove listening previously lived only inside {@code ControllerSettingsFragment},
 * so nothing reacted unless the Controller screen happened to be open. One listener registered
 * here instead covers every tab, and the same instance can be reached from the in-game overlay.
 *
 * <p>Holds no Activity, so it is safe to keep for the life of the process. A callback is
 * delivered to whatever {@link Listener} is currently attached.
 */
public final class ControllerConnectionMonitor {

    /** How long a reconnect for the same device id is ignored, to swallow flapping. */
    static final long DEBOUNCE_MS = 1500L;

    /** What the app wants to do when a pad arrives or leaves. */
    public interface Listener {
        /** Always called on the main thread. */
        void onControllerConnected(String name, ControllerType type, String profileName);

        /** Always called on the main thread. */
        void onControllerDisconnected(String name);
    }

    private final Context appContext;
    private final InputManager inputManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private boolean registered;

    private static volatile ControllerConnectionMonitor shared;

    /**
     * Returns the process-wide monitor, creating it on first use.
     *
     * One monitor serves every screen and the game; each visible Activity attaches its own
     * listener so only the front screen shows the pill, and the device listener itself is
     * registered exactly once for the life of the process.
     */
    public static ControllerConnectionMonitor get(Context context) {
        ControllerConnectionMonitor local = shared;
        if (local == null) {
            synchronized (ControllerConnectionMonitor.class) {
                local = shared;
                if (local == null) {
                    local = new ControllerConnectionMonitor(context.getApplicationContext());
                    shared = local;
                }
            }
        }
        return local;
    }

    /** Last known connection state per device id, used for debouncing. */
    private final java.util.Map<Integer, Long> lastEventAt = new java.util.HashMap<>();

    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            handleAdded(deviceId);
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            handleRemoved(deviceId);
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
        }
    };

    public ControllerConnectionMonitor(Context context) {
        this.appContext = context.getApplicationContext();
        this.inputManager = (InputManager) appContext.getSystemService(Context.INPUT_SERVICE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Registers the listener once; repeated calls are ignored. */
    public void start() {
        if (registered || inputManager == null) return;
        try {
            inputManager.registerInputDeviceListener(deviceListener, mainHandler);
            registered = true;
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        if (!registered || inputManager == null) return;
        try {
            inputManager.unregisterInputDeviceListener(deviceListener);
        } catch (Exception ignored) {
        }
        registered = false;
    }

    /**
     * Handles a newly-added input device.
     *
     * <p>Only gamepads count. A keyboard, the touchscreen and anything else report sources that
     * fail the {@link #isGamepad} test, and a pad we cannot classify is still announced as a
     * generic controller rather than dropped, because "connected and detected" is the useful
     * half of the message even without a vendor match.
     */
    private void handleAdded(int deviceId) {
        if (isDebounced(deviceId)) return;
        InputDevice device = InputDevice.getDevice(deviceId);
        if (device == null || !isGamepad(device)) return;

        ControllerType type = ControllerType.from(device);
        ControllerType effective = type != null ? type : ControllerType.XBOX;
        String name = displayNameOf(device, effective);

        // Load the pad's profile here rather than waiting for a screen to do it, so its
        // dead zones and remaps are active the moment the pad is.
        String profileName = null;
        try {
            ControllerProfileManager manager = new ControllerProfileManager(appContext);
            ControllerProfile profile = manager.getActiveProfile(effective);
            if (profile != null) {
                profileName = profile.getName();
                ControllerInputProcessor.setActiveProfile(effective, profile);
            }
        } catch (Exception ignored) {
            // A profile failure must not stop the notification from being shown.
        }

        final ControllerType reported = effective;
        final String reportedProfile = profileName;
        mainHandler.post(() -> {
            if (listener != null) listener.onControllerConnected(name, reported, reportedProfile);
        });
    }

    private void handleRemoved(int deviceId) {
        if (isDebounced(deviceId)) return;
        // The device is already gone, so its name cannot be read now; the last known id is all
        // we have. A generic label is used when nothing was cached for it.
        String name = lastKnownNames.remove(deviceId);
        if (name == null) name = "Controller";
        final String reported = name;
        mainHandler.post(() -> {
            if (listener != null) listener.onControllerDisconnected(reported);
        });
    }

    /** Names seen at connect time, so a disconnect can still name the pad that left. */
    private final java.util.Map<Integer, String> lastKnownNames = new java.util.HashMap<>();

    private boolean isDebounced(int deviceId) {
        long now = System.currentTimeMillis();
        Long last = lastEventAt.get(deviceId);
        lastEventAt.put(deviceId, now);
        return last != null && (now - last) < DEBOUNCE_MS;
    }

    /** True for a source that is a gamepad or joystick rather than a keyboard or touchscreen. */
    public static boolean isGamepad(InputDevice device) {
        if (device == null) return false;
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private String displayNameOf(InputDevice device, ControllerType type) {
        String name = device.getName();
        if (name == null || name.trim().isEmpty()) {
            name = type != null ? type.getDisplayName() : "Controller";
        }
        lastKnownNames.put(device.getId(), name);
        return name;
    }

    /** Formats the connected message; kept static so it is unit-testable without a device. */
    public static String connectedMessage(String name) {
        return name + " has been connected and detected! Settings loaded.";
    }

    /** Formats the disconnected message. */
    public static String disconnectedMessage(String name) {
        return name + " disconnected. Touch controls active.";
    }
}
