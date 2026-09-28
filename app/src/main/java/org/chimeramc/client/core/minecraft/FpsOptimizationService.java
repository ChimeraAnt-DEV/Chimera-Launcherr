package org.chimeramc.client.core.minecraft;

import android.content.Context;
import android.os.PowerManager;

import org.chimeramc.client.settings.FeatureSettings;
import org.chimeramc.client.settings.LowLatencyNetworkManager;
import org.chimeramc.client.settings.ThermalGovernor;

/**
 * Applies {@link FpsOptimizer}'s decision to the launcher's own subsystems.
 *
 * <p>This is the only place the module touches the host, and every effect it has is one the
 * launcher already owned: the network quiet zone ({@code LowLatencyNetworkManager}) and the
 * thermal/battery background-work gates. Nothing here reaches Minecraft's renderer, so the
 * module cannot change a frame the player sees. That is the design constraint, not a
 * limitation to fix later.
 */
public final class FpsOptimizationService {

    private static volatile Context sAppContext;
    private static volatile boolean sLowMemory;

    private FpsOptimizationService() {}

    public static void init(Context context) {
        if (context != null) sAppContext = context.getApplicationContext();
    }

    /**
     * Records that the OS reported a low-memory condition.
     *
     * <p>Owned by the host ({@code Application.onTrimMemory}); the optimizer reads it as one of
     * the pressure signals.
     */
    public static void setLowMemory(boolean lowMemory) {
        sLowMemory = lowMemory;
    }

    /** The action the optimizer would take for the current device state. */
    public static FpsOptimizer.Action currentAction() {
        FeatureSettings settings = FeatureSettings.getInstance();
        boolean enabled = settings.isFpsOptimizerEnabled();
        boolean sessionActive = LowLatencyNetworkManager.isGameSessionActive();
        boolean batterySaver = isBatterySaverOn();
        return FpsOptimizer.decide(enabled, sessionActive, ThermalGovernor.severity(),
                batterySaver, sLowMemory);
    }

    /**
     * Re-evaluates and applies the decision, returning it for display.
     *
     * <p>Only ever <em>reinforces</em> the existing session state; it never clears it. The
     * quiet zone is owned by {@code PlaytimeManager}, and a module that is switched off (or a
     * session that has no instance identity) must not turn off a flag some other part of the
     * launcher set. When the action is anything but {@code NONE}, the session is by definition
     * already active, so re-asserting it is a no-op that keeps the intent explicit.
     */
    public static FpsOptimizer.Action apply() {
        FpsOptimizer.Action action = currentAction();
        if (action != FpsOptimizer.Action.NONE) {
            LowLatencyNetworkManager.setGameSessionActive(true);
        }
        return action;
    }

    private static boolean isBatterySaverOn() {
        Context context = sAppContext;
        if (context == null) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
