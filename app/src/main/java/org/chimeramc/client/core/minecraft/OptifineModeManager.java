package org.chimeramc.client.core.minecraft;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.chimeramc.client.preloader.PreloaderInput;
import org.chimeramc.client.settings.FeatureSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates Bedrock Optifine Mode between the Settings screen, the persisted feature settings
 * and the preloader.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Build and push the configuration blob before a game launch.</li>
 *   <li>Track which items were active when a launch started, so a launch that dies before a
 *       world loads can be attributed and a repeatedly-failing item auto-disabled.</li>
 *   <li>Read the preloader's per-item report for the Settings screen.</li>
 * </ul>
 *
 * <p>The crash-loop guard is the important part. A Tier-2 hook that resolves to the wrong address
 * can kill the process before any UI appears; without attribution the user would have to clear
 * app data. Instead the pending set is written before launch, cleared once a world is reached,
 * and a pending set found at the next start (or a reported launch failure) increments each
 * item's counter. At {@code CRASH_LOOP_THRESHOLD} the item is switched off and the counter reset,
 * so the next launch is clean.
 */
public final class OptifineModeManager {

    private static final String TAG = "OptifineMode";
    private static final String PREFS = "optifine_mode";
    private static final String KEY_PENDING = "pending_items";

    /** Consecutive pre-world failures after which an item is switched off automatically. */
    public static final int CRASH_LOOP_THRESHOLD = 3;

    /** Sentinel for "the governor did not resolve a higher refresh rate". */
    public static final int NO_REFRESH_TARGET = 0;

    private OptifineModeManager() {
    }

    /** One item's reported state, parsed from the preloader's flat string array. */
    public static final class ItemState {
        public final String id;
        public final boolean enabled;
        public final boolean tier2;
        /** 0 = not attempted, 1 = active, 2 = skipped, 3 = failed. */
        public final int status;
        public final String detail;
        public final boolean needsRestart;

        ItemState(String id, boolean enabled, boolean tier2, int status, String detail,
                  boolean needsRestart) {
            this.id = id;
            this.enabled = enabled;
            this.tier2 = tier2;
            this.status = status;
            this.detail = detail;
            this.needsRestart = needsRestart;
        }
    }

    /**
     * Pushes the current settings to the preloader and records the active set for crash
     * attribution.
     *
     * <p>Safe to call repeatedly. When the master switch is off, a fully-disabled blob is sent so
     * nothing the preloader installed keeps running, while the per-item toggles are preserved in
     * settings for the next time it is on.
     */
    public static void apply(Context context) {
        FeatureSettings settings = FeatureSettings.getInstance();
        boolean preloaderLoaded = org.chimeramc.client.core.mods.ModManager.ensurePreloaderLoaded();

        List<String> ids = OptifineItemIds.ALL;
        String blob;
        if (!settings.isOptifineModeEnabled()) {
            blob = OptifineConfigBlob.buildDisabled(ids);
        } else {
            List<Boolean> enabled = new ArrayList<>(ids.size());
            List<Integer> crashes = new ArrayList<>(ids.size());
            for (String id : ids) {
                enabled.add(settings.isOptifineItemEnabled(id));
                crashes.add(settings.getOptifineCrashCount(id));
            }
            blob = OptifineConfigBlob.build(true, ids, enabled, crashes);
        }

        if (preloaderLoaded) {
            PreloaderInput.configureOptifineMode(blob);
            PreloaderInput.setOptifineRefreshTarget(OptifineRefreshRateRule.resolveTargetHz(context));
            PreloaderInput.configureRenderDistance(
                    RenderDistanceDefaults.MIN_CHUNKS,
                    RenderDistanceDefaults.MAX_CHUNKS,
                    RenderDistanceDefaults.FPS_THRESHOLD);
        } else {
            Log.i(TAG, "Preloader unavailable; optifine configuration not pushed");
        }

        // Record which items were enabled at this launch so a crash can be attributed to them.
        if (context != null) {
            List<String> pending = new ArrayList<>();
            if (settings.isOptifineModeEnabled()) {
                for (String id : ids) {
                    if (settings.isOptifineItemEnabled(id)) {
                        pending.add(id);
                    }
                }
            }
            prefs(context).edit().putString(KEY_PENDING, String.join("\n", pending)).apply();
        }
    }

    /**
     * Clears the pending set because a session reached a world.
     *
     * <p>Called once the game reports a live session; also clears the crash counters, since a
     * launch that reached a world is proof the active set works on this device.
     */
    public static void onSessionReachedWorld(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        String pending = prefs.getString(KEY_PENDING, "");
        if (pending != null && !pending.isEmpty()) {
            FeatureSettings.getInstance().clearOptifineCrashCounts();
        }
        prefs.edit().remove(KEY_PENDING).apply();
    }

    /**
     * Attributes a launch failure to the items that were active.
     *
     * <p>Called from the launch-failure path. Each pending item's counter is incremented and, at
     * the threshold, the item is switched off so the next launch does not repeat the failure.
     */
    public static void onLaunchFailed(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        String pending = prefs.getString(KEY_PENDING, "");
        if (pending == null || pending.isEmpty()) {
            return;
        }
        FeatureSettings settings = FeatureSettings.getInstance();
        for (String id : pending.split("\n")) {
            if (id.isEmpty()) {
                continue;
            }
            int next = settings.getOptifineCrashCount(id) + 1;
            if (next >= CRASH_LOOP_THRESHOLD) {
                settings.setOptifineItemEnabled(id, false);
                settings.setOptifineCrashCount(id, 0);
                Log.w(TAG, "Disabled optifine item after repeated launch failures: " + id);
            } else {
                settings.setOptifineCrashCount(id, next);
            }
        }
        prefs.edit().remove(KEY_PENDING).apply();
    }

    /**
     * Handles a pending set left by a process that died before reporting anything.
     *
     * <p>Called at application start. A pending set means the last launch never reached a world
     * and never reported a failure (the process was killed), which is exactly the crash-loop case
     * the guard exists for.
     */
    public static void reconcileOnStartup(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        String pending = prefs.getString(KEY_PENDING, "");
        if (pending == null || pending.isEmpty()) {
            return;
        }
        FeatureSettings settings = FeatureSettings.getInstance();
        for (String id : pending.split("\n")) {
            if (id.isEmpty()) {
                continue;
            }
            int next = settings.getOptifineCrashCount(id) + 1;
            if (next >= CRASH_LOOP_THRESHOLD) {
                settings.setOptifineItemEnabled(id, false);
                settings.setOptifineCrashCount(id, 0);
                Log.w(TAG, "Disabled optifine item after repeated crashes: " + id);
            } else {
                settings.setOptifineCrashCount(id, next);
            }
        }
        prefs.edit().remove(KEY_PENDING).apply();
    }

    /** The preloader's per-item report, or an empty list when the native library is absent. */
    public static List<ItemState> readState() {
        String[] flat = PreloaderInput.readOptifineState();
        List<ItemState> result = new ArrayList<>();
        if (flat == null) {
            return result;
        }
        for (int i = 0; i + 5 < flat.length; i += 6) {
            try {
                result.add(new ItemState(
                        flat[i],
                        "1".equals(flat[i + 1]),
                        "1".equals(flat[i + 2]),
                        Integer.parseInt(flat[i + 3]),
                        flat[i + 4],
                        "1".equals(flat[i + 5])));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Malformed optifine state entry: " + flat[i]);
            }
        }
        return result;
    }

    /** True when the preloader is loaded and the master switch is on with an active item. */
    public static boolean isActive() {
        return PreloaderInput.isOptifineModeActive();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
