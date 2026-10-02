package org.chimeramc.client.core.replay;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The Replay tab's persisted settings.
 *
 * <p>Its own preferences file rather than {@code inbuilt_mods_prefs}: the replay library is a
 * feature the launcher owns, and keeping its keys separate means a future rename of the mod store
 * cannot strand a player's storage cap. Everything the spec calls configurable lives here — the
 * clip-length limit, the storage cap, the highlight triggers and the burn-in fields — so the UI
 * reads one source of truth.
 */
public final class ReplaySettings {

    private static final String PREFS_NAME = "replay_settings";

    private static final String KEY_CLIP_LIMIT_MINUTES = "clip_limit_minutes";
    private static final String KEY_STORAGE_CAP_BYTES = "storage_cap_bytes";
    private static final String KEY_FORCE_LOW_QUALITY = "force_low_quality";
    private static final String KEY_BURN_METADATA = "burn_metadata";
    private static final String KEY_WATERMARK = "watermark";
    private static final String KEY_TRIGGER_DEATH = "trigger_death";
    private static final String KEY_TRIGGER_STREAK = "trigger_streak";
    private static final String KEY_STREAK_THRESHOLD = "streak_threshold";
    private static final String KEY_TRIGGER_COMBO = "trigger_combo";
    private static final String KEY_COMBO_THRESHOLD = "combo_threshold";
    private static final String KEY_SORT = "sort";

    /** Default clip length, per the spec. */
    public static final int DEFAULT_CLIP_LIMIT_MINUTES = 5;
    public static final int MAX_CLIP_LIMIT_MINUTES = 30;

    private static volatile ReplaySettings instance;

    private final SharedPreferences prefs;

    private ReplaySettings(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static ReplaySettings get(Context context) {
        ReplaySettings local = instance;
        if (local == null) {
            synchronized (ReplaySettings.class) {
                local = instance;
                if (local == null) {
                    local = new ReplaySettings(
                            context.getApplicationContext()
                                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
                    instance = local;
                }
            }
        }
        return local;
    }

    /** The clip-length limit in minutes, clamped to 1..{@link #MAX_CLIP_LIMIT_MINUTES}. */
    public int clipLimitMinutes() {
        int value = prefs.getInt(KEY_CLIP_LIMIT_MINUTES, DEFAULT_CLIP_LIMIT_MINUTES);
        return Math.max(1, Math.min(MAX_CLIP_LIMIT_MINUTES, value));
    }

    public void setClipLimitMinutes(int minutes) {
        prefs.edit().putInt(KEY_CLIP_LIMIT_MINUTES,
                Math.max(1, Math.min(MAX_CLIP_LIMIT_MINUTES, minutes))).apply();
    }

    public long storageCapBytes() {
        return ReplayStoragePolicy.clampCapBytes(
                prefs.getLong(KEY_STORAGE_CAP_BYTES, ReplayStoragePolicy.DEFAULT_CAP_BYTES));
    }

    public void setStorageCapBytes(long bytes) {
        prefs.edit().putLong(KEY_STORAGE_CAP_BYTES, ReplayStoragePolicy.clampCapBytes(bytes))
                .apply();
    }

    public boolean forceLowQuality() {
        return prefs.getBoolean(KEY_FORCE_LOW_QUALITY, false);
    }

    public void setForceLowQuality(boolean force) {
        prefs.edit().putBoolean(KEY_FORCE_LOW_QUALITY, force).apply();
    }

    public boolean burnMetadata() {
        return prefs.getBoolean(KEY_BURN_METADATA, true);
    }

    public void setBurnMetadata(boolean burn) {
        prefs.edit().putBoolean(KEY_BURN_METADATA, burn).apply();
    }

    public boolean watermark() {
        return prefs.getBoolean(KEY_WATERMARK, false);
    }

    public void setWatermark(boolean watermark) {
        prefs.edit().putBoolean(KEY_WATERMARK, watermark).apply();
    }

    public boolean triggerDeath() {
        return prefs.getBoolean(KEY_TRIGGER_DEATH, false);
    }

    public boolean triggerKillStreak() {
        return prefs.getBoolean(KEY_TRIGGER_STREAK, false);
    }

    public int streakThreshold() {
        return Math.max(1, prefs.getInt(KEY_STREAK_THRESHOLD, 3));
    }

    public boolean triggerCombo() {
        return prefs.getBoolean(KEY_TRIGGER_COMBO, false);
    }

    public int comboThreshold() {
        return Math.max(1, prefs.getInt(KEY_COMBO_THRESHOLD, 10));
    }

    public void setTriggerDeath(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_DEATH, enabled).apply();
    }

    public void setTriggerKillStreak(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_STREAK, enabled).apply();
    }

    public void setStreakThreshold(int threshold) {
        prefs.edit().putInt(KEY_STREAK_THRESHOLD, Math.max(1, threshold)).apply();
    }

    public void setTriggerCombo(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRIGGER_COMBO, enabled).apply();
    }

    public void setComboThreshold(int threshold) {
        prefs.edit().putInt(KEY_COMBO_THRESHOLD, Math.max(1, threshold)).apply();
    }

    /** A fresh trigger built from the current settings. */
    public ReplayHighlightTrigger newTrigger() {
        return new ReplayHighlightTrigger(triggerDeath(), triggerKillStreak(), streakThreshold(),
                triggerCombo(), comboThreshold());
    }

    public ReplayLibrary.Sort sort() {
        String raw = prefs.getString(KEY_SORT, ReplayLibrary.Sort.DATE_NEWEST.name());
        try {
            return ReplayLibrary.Sort.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ReplayLibrary.Sort.DATE_NEWEST;
        }
    }

    public void setSort(ReplayLibrary.Sort sort) {
        prefs.edit().putString(KEY_SORT, (sort == null
                ? ReplayLibrary.Sort.DATE_NEWEST : sort).name()).apply();
    }
}
