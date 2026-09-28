package org.chimeramc.client.core.minecraft;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.chimeramc.client.settings.LowLatencyNetworkManager;

/**
 * Per-instance play session tracking.
 *
 * A session starts when the Minecraft activity really begins (runtime started), and ends
 * when that activity is destroyed (normal exit, crash, or launcher restart). Elapsed
 * (wall-clock) time during the session is accumulated into a per-instance total stored in
 * SharedPreferences, following the same pattern as other per-instance launcher data.
 *
 * A lightweight heartbeat persists the running total every few seconds, so even if the
 * process is killed mid-session (game crash), at most a few seconds of playtime are lost
 * and the stale session markers are cleared on the next app start.
 */
public final class PlaytimeManager {
    private static final String PREFS = "playtime_tracker";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_ACTIVE_START = "active_start_elapsed";
    private static final String KEY_TOTAL_PREFIX = "total_ms_";
    public static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private static Context sAppContext;
    private static String sActiveProfileId;
    private static long sActiveBaseMs;
    private static long sActiveStartElapsed;

    private PlaytimeManager() {
    }

    public static void init(Context context) {
        sAppContext = context.getApplicationContext();
        cleanupInterruptedSession();
    }

    public static void startSession(String profileId) {
        if (sAppContext == null) return;
        if (profileId == null || profileId.isEmpty()) {
            // No instance identity (shouldn't happen in practice), but a session is still
            // running, so keep the network quiet zone active.
            if (sActiveProfileId == null) {
                LowLatencyNetworkManager.setGameSessionActive(true);
            }
            return;
        }
        if (profileId.equals(sActiveProfileId)) return;
        stopSession();
        sActiveProfileId = profileId;
        sActiveBaseMs = getTotalMs(profileId);
        sActiveStartElapsed = SystemClock.elapsedRealtime();
        prefs().edit()
                .putString(KEY_ACTIVE_PROFILE, profileId)
                .putLong(KEY_ACTIVE_START, sActiveStartElapsed)
                .apply();
        LowLatencyNetworkManager.setGameSessionActive(true);
        // A fresh session clears any memory-pressure reading left over from the last one, then
        // re-evaluates so the FPS optimizer's quiet window starts with the game.
        FpsOptimizationService.setLowMemory(false);
        FpsOptimizationService.apply();
    }

    public static void heartbeat() {
        if (sActiveProfileId == null) return;
        accumulateNow();
        // Thermal severity and battery saver change while the game runs, so the optimizer is
        // re-evaluated on the same heartbeat that keeps playtime fresh.
        FpsOptimizationService.apply();
    }

    public static void stopSession() {
        if (sAppContext == null) return;
        if (sActiveProfileId != null) {
            accumulateNow();
            prefs().edit()
                    .remove(KEY_ACTIVE_PROFILE)
                    .remove(KEY_ACTIVE_START)
                    .apply();
            sActiveProfileId = null;
        }
        LowLatencyNetworkManager.setGameSessionActive(false);
    }

    private static void accumulateNow() {
        if (sActiveProfileId == null) return;
        long now = SystemClock.elapsedRealtime();
        long delta = now - sActiveStartElapsed;
        if (delta <= 0) return;
        long total = sActiveBaseMs + delta;
        sActiveBaseMs = total;
        sActiveStartElapsed = now;
        prefs().edit().putLong(KEY_TOTAL_PREFIX + sActiveProfileId, total).apply();
    }

    public static long getTotalMs(String profileId) {
        if (profileId == null || sAppContext == null) return 0L;
        return prefs().getLong(KEY_TOTAL_PREFIX + profileId, 0L);
    }

    public static String formatPlaytime(long ms) {
        long totalSeconds = ms / 1000;
        if (totalSeconds < 60) return totalSeconds + "s";
        long minutes = totalSeconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        return hours + "h " + remMinutes + "m";
    }

    private static SharedPreferences prefs() {
        return sAppContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void cleanupInterruptedSession() {
        if (sAppContext == null) return;
        // A previous session that died without onDestroy is already accounted for up to its
        // last heartbeat; just drop the stale session markers.
        prefs().edit()
                .remove(KEY_ACTIVE_PROFILE)
                .remove(KEY_ACTIVE_START)
                .apply();
        LowLatencyNetworkManager.setGameSessionActive(false);
    }
}