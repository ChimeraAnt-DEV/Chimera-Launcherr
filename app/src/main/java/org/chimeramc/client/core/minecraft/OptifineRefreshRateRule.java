package org.chimeramc.client.core.minecraft;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import org.chimeramc.client.util.DisplayModePreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the refresh rate Bedrock Optifine Mode should request.
 *
 * <p>Mirrors the preloader's {@code SelectTargetRefreshRate}: the highest supported rate strictly
 * above the one in use, or 0 when there is nothing to unlock (a 60Hz-only panel, an unknown mode
 * list, or the game already at the top rate). The launcher owns the request because only it holds
 * the {@code Window}; the preloader reports the outcome.
 *
 * <p>{@link #resolveTargetHz} reads the display; {@link #selectTargetHz} is pure and unit-tested.
 */
public final class OptifineRefreshRateRule {

    private OptifineRefreshRateRule() {
    }

    /**
     * @param supportedHz every refresh rate the panel advertises.
     * @param currentHz   the rate currently in use, or 0 if unknown.
     * @return the highest supported rate above {@code currentHz}, or 0 for "nothing to unlock".
     */
    public static int selectTargetHz(int[] supportedHz, int currentHz) {
        if (supportedHz == null || supportedHz.length == 0) {
            return 0;
        }
        int highest = 0;
        for (int hz : supportedHz) {
            if (hz > highest) {
                highest = hz;
            }
        }
        if (highest <= currentHz) {
            return 0;
        }
        return highest;
    }

    /**
     * Reads the display and returns the rate to request, or {@link OptifineModeManager#NO_REFRESH_TARGET}.
     *
     * <p>Only same-resolution modes are considered, for the same reason
     * {@link DisplayModePreference} exists: on many panels the fast modes are lower resolution,
     * and "unlocking 120Hz" would silently trade sharpness for frames.
     */
    public static int resolveTargetHz(Context context) {
        if (context == null) {
            return OptifineModeManager.NO_REFRESH_TARGET;
        }
        try {
            WindowManager windowManager =
                    (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return OptifineModeManager.NO_REFRESH_TARGET;
            }
            Display display = windowManager.getDefaultDisplay();
            if (display == null) {
                return OptifineModeManager.NO_REFRESH_TARGET;
            }
            Display.Mode current = display.getMode();
            if (current == null) {
                return OptifineModeManager.NO_REFRESH_TARGET;
            }

            List<Integer> sameResolution = new ArrayList<>();
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()) {
                    sameResolution.add(Math.round(mode.getRefreshRate()));
                }
            }
            int[] rates = new int[sameResolution.size()];
            for (int i = 0; i < rates.length; i++) {
                rates[i] = sameResolution.get(i);
            }
            return selectTargetHz(rates, Math.round(current.getRefreshRate()));
        } catch (Throwable throwable) {
            return OptifineModeManager.NO_REFRESH_TARGET;
        }
    }
}
