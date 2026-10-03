package org.chimeramc.client.core.mods.inbuilt.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.core.mods.inbuilt.model.ModLoadoutStore;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class InbuiltModManager {
    private static final String PREFS_NAME = "inbuilt_mods_prefs";
    private static final String KEY_AUTOSPRINT_KEY = "autosprint_key";
    private static final String KEY_OVERLAY_BUTTON_SIZE_PREFIX = "overlay_button_size_";
    private static final String KEY_OVERLAY_OPACITY_PREFIX = "overlay_opacity_";
    private static final String KEY_MOD_MENU_ENABLED = "mod_menu_enabled";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_MOD_MENU_OPACITY = "mod_menu_opacity";
    private static final String KEY_MOD_MENU_BUTTON_OPACITY = "mod_menu_button_opacity";
    private static final String KEY_MOD_MENU_COMPACT = "mod_menu_compact";
    private static final String KEY_MOD_MENU_KEYBIND = "mod_menu_keybind";
    private static final String KEY_MOD_MENU_CONTROLLER_BIND = "mod_menu_controller_bind";
    private static final String KEY_PAUSE_MENU_ONLY = "pause_menu_only";
    private static final String KEY_FAVORITE_MOD_KEYS = "favorite_mod_keys";
    private static final String KEY_MOD_LOADOUTS = "mod_loadouts";
    private static final String KEY_INBUILT_MOD_ENABLED_PREFIX = "inbuilt_mod_enabled_";
    private static final String KEY_EXTERNAL_MODULE_ENABLED_PREFIX = "external_module_enabled_";
    private static final String KEY_ZOOM_LEVEL = "zoom_level";
    private static final String KEY_ZOOM_KEYBIND = "zoom_keybind";
    private static final String KEY_ZOOM_TRANSITION_DURATION = "zoom_transition_duration";
    private static final String KEY_CURSOR_SENSITIVITY = "cursor_sensitivity";
    private static final String KEY_GYRO_SENSITIVITY_X = "gyro_sensitivity_x";
    private static final String KEY_GYRO_SENSITIVITY_Y = "gyro_sensitivity_y";
    private static final String KEY_GYRO_INVERT_X = "gyro_invert_x";
    private static final String KEY_GYRO_INVERT_Y = "gyro_invert_y";
    private static final String KEY_GYRO_DEADZONE = "gyro_deadzone";
    private static final String KEY_OVERLAY_POSITION_X_PREFIX = "overlay_pos_x_";
    private static final String KEY_OVERLAY_POSITION_Y_PREFIX = "overlay_pos_y_";
    private static final String KEY_OVERLAY_LOCK_PREFIX = "overlay_lock_";
    private static final String KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX = "overlay_show_everywhere_";
    private static final String KEY_HOTBAR_ITEM_ICONS = "hotbar_item_icons";
    private static final String KEY_HOTBAR_SLOT_ENABLED_PREFIX = "hotbar_slot_enabled_";
    private static final String KEY_AIM_SMOOTHING = "aim_smoothing";
    private static final String KEY_AIM_CROSSHAIR = "aim_crosshair";
    private static final String KEY_AIM_FLASH = "aim_flash";
    private static final String KEY_AIM_SENSITIVITY = "aim_sensitivity";
    private static final String KEY_AIM_CROSSHAIR_STYLE = "aim_crosshair_style";
    private static final String KEY_AIM_CROSSHAIR_COLOR = "aim_crosshair_color";
    private static final String KEY_ARMOR_HUD_SHOW_TARGET = "armor_hud_show_target";
    private static final String KEY_ARMOR_HUD_SHOW_ENCHANTS = "armor_hud_show_enchants";
    private static final String KEY_ARMOR_HUD_STACKED = "armor_hud_stacked";
    private static final String KEY_ARMOR_HUD_REFRESH_MS = "armor_hud_refresh_ms";
    private static final String KEY_CRYSTAL_MIN_SELF_HP = "crystal_min_self_hp";
    private static final String KEY_CRYSTAL_MAX_RANGE = "crystal_max_range";
    private static final String KEY_CRYSTAL_PLACEMENT_DELAY_MS = "crystal_placement_delay_ms";
    private static final String KEY_CRYSTAL_MANUAL_ASSIST = "crystal_manual_assist";
    private static final String KEY_CRYSTAL_KEYBIND = "crystal_keybind";
    private static final String KEY_HITREG_SENSITIVITY = "hitreg_sensitivity";
    private static final String KEY_HITREG_SMOOTHING = "hitreg_smoothing";
    private static final String KEY_HITREG_PREDICTION = "hitreg_prediction";
    private static final String KEY_HITREG_HAPTIC = "hitreg_haptic";
    private static final String KEY_HIT_TIMING_COOLDOWN_MS = "hit_timing_cooldown_ms";
    private static final String KEY_HIT_TIMING_SHOW_COMBO = "hit_timing_show_combo";
    private static final String KEY_HIT_TIMING_SHOW_BAR = "hit_timing_show_bar";
    private static final String KEY_HITBOX_SHOW_PLAYERS = "hitbox_show_players";
    private static final String KEY_HITBOX_SHOW_MOBS = "hitbox_show_mobs";
    private static final String KEY_HITBOX_SHOW_ITEMS = "hitbox_show_items";
    private static final String KEY_HITBOX_SHOW_PROJECTILES = "hitbox_show_projectiles";
    private static final String KEY_HITBOX_SHOW_THROWN_ITEMS = "hitbox_show_thrown_items";
    private static final String KEY_HITBOX_SHOW_LOOK_LINE = "hitbox_show_look_line";
    private static final String KEY_HITBOX_SHOW_CRIT_LINE = "hitbox_show_crit_line";
    private static final String KEY_HITBOX_SHOW_COMBO_BOX = "hitbox_show_combo_box";
    /** Draws the chest-height target sub-box inside each peer box. */
    private static final String KEY_HITBOX_SHOW_TARGET_BOX = "hitbox_show_target_box";
    /** ARGB colour of the peer boxes; -1 means "use the default white". */
    private static final String KEY_HITBOX_PEER_COLOR = "hitbox_peer_color";
    // PvP Suite (V1.1). Every key here is a display preference only; nothing reads game state.
    private static final String KEY_REACH_POSITION = "reach_indicator_position";
    private static final String KEY_TRAJECTORY_COLOR = "trajectory_color";
    private static final String KEY_HIT_PREDICTION_LOOKAHEAD_MS = "hit_prediction_lookahead_ms";
    private static final String KEY_KILL_EFFECT_STYLE = "kill_effect_style";
    private static final String KEY_KILL_EFFECT_COLOR = "kill_effect_color";
    private static final String KEY_VOICE_RANGE = "voice_range_blocks";
    private static final String KEY_VOICE_VOLUME = "voice_volume_percent";
    private static final String KEY_VOICE_CHANNEL = "voice_channel";
    private static final String KEY_VOICE_CHANNEL_NAME = "voice_channel_name";
    private static final String KEY_VOICE_CHANNEL_PRIVATE = "voice_channel_private";
    private static final String KEY_VOICE_CAPACITY = "voice_channel_capacity";
    private static final String KEY_VOICE_MIC = "voice_mic_enabled";
    private static final String KEY_VOICE_MUTES = "voice_member_mutes";
    private static final String KEY_VOICE_ICON_STYLE = "voice_icon_style";
    private static final String KEY_VOICE_ICON_ANIMATE = "voice_icon_animate";
    private static final String KEY_VOICE_ICON_SHOW_NAMETAG = "voice_icon_show_nametag";
    private static final String KEY_VOICE_REFRESH_MS = "voice_chatter_refresh_ms";
    private static final String KEY_VOICE_RELAY_ENABLED = "voice_relay_enabled";
    private static final String KEY_VOICE_RELAY_ADDRESS = "voice_relay_address";
    private static final String KEY_VOICE_RELAY_PASSWORD = "voice_relay_password";
    private static final String KEY_VOICE_RELAY_TOKEN_SECRET = "voice_relay_token_secret";
    private static final String KEY_VOICE_DEVICE_ID = "voice_device_id";
    /** Whether the equipped cape/accessory/pet is advertised to other Chimera users in the world. */
    private static final String KEY_COSMETIC_SYNC_ENABLED = "cosmetic_sync_enabled";
    private static final String KEY_COSMETIC_MANUAL_PEER = "cosmetic_sync_manual_peer";
    private static final int DEFAULT_AIM_SMOOTHING = 40;
    private static final int DEFAULT_AIM_SENSITIVITY = 100;
    private static final int DEFAULT_AIM_CROSSHAIR_COLOR = 0xFF3DDC84;
    private static final int DEFAULT_ARMOR_HUD_REFRESH_MS = 100;
    private static final int DEFAULT_CRYSTAL_MIN_SELF_HP = 14;
    private static final int DEFAULT_CRYSTAL_MAX_RANGE = 4;
    private static final int DEFAULT_CRYSTAL_PLACEMENT_DELAY_MS = 50;
    private static final int DEFAULT_HITREG_SENSITIVITY = 100;
    private static final int DEFAULT_HITREG_SMOOTHING = 25;
    private static final int DEFAULT_HITREG_PREDICTION = 35;
    private static final int DEFAULT_OVERLAY_BUTTON_SIZE = 56;
    private static final int DEFAULT_OVERLAY_OPACITY = 100;
    private static final int MIN_MOD_MENU_OPACITY = 70;
    private static final int DEFAULT_ZOOM_LEVEL = 10;
    private static final int DEFAULT_ZOOM_TRANSITION_DURATION = 150;
    private static final int DEFAULT_CURSOR_SENSITIVITY = 120;
    private static final int DEFAULT_GYRO_SENSITIVITY = 100;
    private static final int DEFAULT_GYRO_DEADZONE = 5;
    private static final float DEFAULT_VOICE_RANGE = 24f;
    private static final float MIN_VOICE_RANGE = 4f;
    private static final float MAX_VOICE_RANGE = 64f;
    private static final int DEFAULT_VOICE_VOLUME = 100;
    private static final int DEFAULT_VOICE_REFRESH_MS = 100;

    private static volatile InbuiltModManager instance;
    private final SharedPreferences prefs;
    private final AtomicLong overlayVisibilityRevision = new AtomicLong(1L);
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private InbuiltModManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferenceListener = (sharedPreferences, key) -> {
            if (KEY_PAUSE_MENU_ONLY.equals(key) ||
                    (key != null && key.startsWith(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX))) {
                overlayVisibilityRevision.incrementAndGet();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    public static InbuiltModManager getInstance(Context context) {
        if (instance == null) {
            synchronized (InbuiltModManager.class) {
                if (instance == null) {
                    instance = new InbuiltModManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public int getAutoSprintKeybind() {
        return prefs.getInt(KEY_AUTOSPRINT_KEY, KeyEvent.KEYCODE_CTRL_LEFT);
    }

    public void setAutoSprintKeybind(int keyCode) {
        prefs.edit().putInt(KEY_AUTOSPRINT_KEY, keyCode).apply();
    }

    private String sharedOverlaySettingsId(String modId) {
        if (modId != null && modId.startsWith(ModIds.HOTBAR_SLOT + ":")) return ModIds.HOTBAR_SLOT;
        return modId;
    }

    public int getOverlayButtonSize(String modId) {
        String key = KEY_OVERLAY_BUTTON_SIZE_PREFIX + modId;
        if (prefs.contains(key)) return prefs.getInt(key, DEFAULT_OVERLAY_BUTTON_SIZE);
        String sharedId = sharedOverlaySettingsId(modId);
        if (sharedId != null && !sharedId.equals(modId)) {
            return prefs.getInt(KEY_OVERLAY_BUTTON_SIZE_PREFIX + sharedId, DEFAULT_OVERLAY_BUTTON_SIZE);
        }
        return DEFAULT_OVERLAY_BUTTON_SIZE;
    }

    public void setOverlayButtonSize(String modId, int sizeDp) {
        prefs.edit().putInt(KEY_OVERLAY_BUTTON_SIZE_PREFIX + modId, sizeDp).apply();
    }

    public int getOverlayOpacity(String modId) {
        String key = KEY_OVERLAY_OPACITY_PREFIX + modId;
        if (prefs.contains(key)) return prefs.getInt(key, DEFAULT_OVERLAY_OPACITY);
        String sharedId = sharedOverlaySettingsId(modId);
        if (sharedId != null && !sharedId.equals(modId)) {
            return prefs.getInt(KEY_OVERLAY_OPACITY_PREFIX + sharedId, DEFAULT_OVERLAY_OPACITY);
        }
        return DEFAULT_OVERLAY_OPACITY;
    }

    public void setOverlayOpacity(String modId, int opacity) {
        prefs.edit().putInt(KEY_OVERLAY_OPACITY_PREFIX + modId, Math.max(0, Math.min(100, opacity))).apply();
    }

    public boolean isModMenuEnabled() {
        return prefs.getBoolean(KEY_MOD_MENU_ENABLED, true);
    }

    public void setModMenuEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOD_MENU_ENABLED, enabled).apply();
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public int getModMenuOpacity() {
        return Math.max(MIN_MOD_MENU_OPACITY, prefs.getInt(KEY_MOD_MENU_OPACITY, DEFAULT_OVERLAY_OPACITY));
    }

    public void setModMenuOpacity(int opacity) {
        prefs.edit().putInt(KEY_MOD_MENU_OPACITY, Math.max(MIN_MOD_MENU_OPACITY, Math.min(100, opacity))).apply();
    }

    public int getModMenuButtonOpacity() {
        return prefs.getInt(KEY_MOD_MENU_BUTTON_OPACITY, DEFAULT_OVERLAY_OPACITY);
    }

    public void setModMenuButtonOpacity(int opacity) {
        prefs.edit().putInt(KEY_MOD_MENU_BUTTON_OPACITY, Math.max(0, Math.min(100, opacity))).apply();
    }

    public boolean isModMenuCompact() {
        return prefs.getBoolean(KEY_MOD_MENU_COMPACT, false);
    }

    public void setModMenuCompact(boolean compact) {
        prefs.edit().putBoolean(KEY_MOD_MENU_COMPACT, compact).apply();
    }

    /**
     * The keyboard key that opens the Mod Menu, or 0 for unbound.
     *
     * Stored as an Android key code so it travels with the app and can be shown without a
     * lookup table; the in-game dispatcher compares the raw code.
     */
    public int getModMenuKeybind() {
        return prefs.getInt(KEY_MOD_MENU_KEYBIND, 0);
    }

    public void setModMenuKeybind(int keyCode) {
        prefs.edit().putInt(KEY_MOD_MENU_KEYBIND, Math.max(0, keyCode)).apply();
    }

    /** The controller button that opens the Mod Menu, or 0 for unbound. */
    public int getModMenuControllerBind() {
        return prefs.getInt(KEY_MOD_MENU_CONTROLLER_BIND, 0);
    }

    public void setModMenuControllerBind(int keyCode) {
        prefs.edit().putInt(KEY_MOD_MENU_CONTROLLER_BIND, Math.max(0, keyCode)).apply();
    }

    /**
     * Whether a key press should open the Mod Menu.
     *
     * Pure so the match can be unit tested: the two binds are independent, an unbound (0) side
     * never matches, and a key matches on either its remapped or its raw code so a profile that
     * remaps the bound button still opens the menu.
     */
    public static boolean matchesModMenuBind(int menuBind, int controllerBind,
                                             int keyCode, int rawKeyCode) {
        if (menuBind != 0 && (keyCode == menuBind || rawKeyCode == menuBind)) return true;
        return controllerBind != 0 && (keyCode == controllerBind || rawKeyCode == controllerBind);
    }

    public boolean isPauseMenuOnly() {
        return prefs.getBoolean(KEY_PAUSE_MENU_ONLY, false);
    }

    public void setPauseMenuOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_PAUSE_MENU_ONLY, enabled).apply();
    }

    public Set<String> getFavoriteModKeys() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITE_MOD_KEYS, new HashSet<>()));
    }

    public void setModFavorite(String favoriteKey, boolean favorite) {
        if (favoriteKey == null || favoriteKey.isEmpty()) return;
        Set<String> favorites = getFavoriteModKeys();
        if (favorite) {
            favorites.add(favoriteKey);
        } else {
            favorites.remove(favoriteKey);
        }
        prefs.edit().putStringSet(KEY_FAVORITE_MOD_KEYS, favorites).apply();
    }

    public boolean resolveInbuiltModEnabled(String modId, boolean defaultEnabled) {
        if (modId == null || modId.isEmpty()) return defaultEnabled;
        return prefs.getBoolean(KEY_INBUILT_MOD_ENABLED_PREFIX + modId, defaultEnabled);
    }

    public void setInbuiltModEnabled(String modId, boolean enabled) {
        if (modId == null || modId.isEmpty()) return;
        prefs.edit().putBoolean(KEY_INBUILT_MOD_ENABLED_PREFIX + modId, enabled).apply();
    }

    public boolean resolveExternalModuleEnabled(String moduleId, boolean defaultEnabled) {
        if (moduleId == null || moduleId.isEmpty()) return defaultEnabled;
        String key = KEY_EXTERNAL_MODULE_ENABLED_PREFIX + moduleId;
        if (!prefs.contains(key)) {
            prefs.edit().putBoolean(key, defaultEnabled).apply();
            return defaultEnabled;
        }
        return prefs.getBoolean(key, defaultEnabled);
    }

    public void setExternalModuleEnabled(String moduleId, boolean enabled) {
        if (moduleId == null || moduleId.isEmpty()) return;
        prefs.edit().putBoolean(KEY_EXTERNAL_MODULE_ENABLED_PREFIX + moduleId, enabled).apply();
    }

    public int getZoomLevel() {
        try {
            return prefs.getInt(KEY_ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL);
        } catch (ClassCastException e) {
            prefs.edit().remove(KEY_ZOOM_LEVEL).apply();
            return DEFAULT_ZOOM_LEVEL;
        }
    }

    public void setZoomLevel(int level) {
        prefs.edit().putInt(KEY_ZOOM_LEVEL, Math.max(-20, Math.min(100, level))).apply();
    }

    public int getZoomKeybind() {
        return prefs.getInt(KEY_ZOOM_KEYBIND, KeyEvent.KEYCODE_C);
    }

    public void setZoomKeybind(int keyCode) {
        prefs.edit().putInt(KEY_ZOOM_KEYBIND, keyCode).apply();
    }

    public int getZoomTransitionDuration() {
        return prefs.getInt(KEY_ZOOM_TRANSITION_DURATION, DEFAULT_ZOOM_TRANSITION_DURATION);
    }

    public void setZoomTransitionDuration(int duration) {
        prefs.edit().putInt(KEY_ZOOM_TRANSITION_DURATION, Math.max(0, Math.min(1000, duration))).apply();
    }

    public int getCursorSensitivity() {
        return prefs.getInt(KEY_CURSOR_SENSITIVITY, DEFAULT_CURSOR_SENSITIVITY);
    }

    public void setCursorSensitivity(int sensitivity) {
        prefs.edit().putInt(KEY_CURSOR_SENSITIVITY, Math.max(10, Math.min(300, sensitivity))).apply();
    }

    public int getOverlayPositionX(String modId, int defaultX) {
        return prefs.getInt(KEY_OVERLAY_POSITION_X_PREFIX + modId, defaultX);
    }

    public int getOverlayPositionY(String modId, int defaultY) {
        return prefs.getInt(KEY_OVERLAY_POSITION_Y_PREFIX + modId, defaultY);
    }

    public void setOverlayPosition(String modId, int x, int y) {
        prefs.edit()
            .putInt(KEY_OVERLAY_POSITION_X_PREFIX + modId, x)
            .putInt(KEY_OVERLAY_POSITION_Y_PREFIX + modId, y)
            .apply();
    }

    public boolean isOverlayLocked(String modId) {
        return prefs.getBoolean(KEY_OVERLAY_LOCK_PREFIX + sharedOverlaySettingsId(modId), false);
    }

    public void setOverlayLocked(String modId, boolean locked) {
        prefs.edit().putBoolean(KEY_OVERLAY_LOCK_PREFIX + sharedOverlaySettingsId(modId), locked).apply();
    }

    public boolean isOverlayShowEverywhere(String modId) {
        return prefs.getBoolean(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX + sharedOverlaySettingsId(modId), false);
    }

    public void setOverlayShowEverywhere(String modId, boolean showEverywhere) {
        prefs.edit().putBoolean(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX + sharedOverlaySettingsId(modId), showEverywhere).apply();
    }

    public void clearOverlaySettings(String modId) {
        if (modId == null || modId.isEmpty()) return;
        prefs.edit()
                .remove(KEY_OVERLAY_BUTTON_SIZE_PREFIX + modId)
                .remove(KEY_OVERLAY_OPACITY_PREFIX + modId)
                .remove(KEY_OVERLAY_POSITION_X_PREFIX + modId)
                .remove(KEY_OVERLAY_POSITION_Y_PREFIX + modId)
                .remove(KEY_OVERLAY_LOCK_PREFIX + modId)
                .remove(KEY_OVERLAY_SHOW_EVERYWHERE_PREFIX + modId)
                .apply();
        overlayVisibilityRevision.incrementAndGet();
    }

    public long getOverlayVisibilityRevision() {
        return overlayVisibilityRevision.get();
    }

    public int getGyroSensitivityX() {
        return prefs.getInt(KEY_GYRO_SENSITIVITY_X, DEFAULT_GYRO_SENSITIVITY);
    }

    public void setGyroSensitivityX(int sensitivity) {
        prefs.edit().putInt(KEY_GYRO_SENSITIVITY_X, Math.max(10, Math.min(300, sensitivity))).apply();
    }

    public int getGyroSensitivityY() {
        return prefs.getInt(KEY_GYRO_SENSITIVITY_Y, DEFAULT_GYRO_SENSITIVITY);
    }

    public void setGyroSensitivityY(int sensitivity) {
        prefs.edit().putInt(KEY_GYRO_SENSITIVITY_Y, Math.max(10, Math.min(300, sensitivity))).apply();
    }

    public boolean isGyroInvertX() {
        return prefs.getBoolean(KEY_GYRO_INVERT_X, false);
    }

    public void setGyroInvertX(boolean invert) {
        prefs.edit().putBoolean(KEY_GYRO_INVERT_X, invert).apply();
    }

    public boolean isGyroInvertY() {
        return prefs.getBoolean(KEY_GYRO_INVERT_Y, false);
    }

    public void setGyroInvertY(boolean invert) {
        prefs.edit().putBoolean(KEY_GYRO_INVERT_Y, invert).apply();
    }

    public int getGyroDeadzone() {
        return prefs.getInt(KEY_GYRO_DEADZONE, DEFAULT_GYRO_DEADZONE);
    }

    public void setGyroDeadzone(int deadzone) {
        prefs.edit().putInt(KEY_GYRO_DEADZONE, Math.max(0, Math.min(50, deadzone))).apply();
    }
    public boolean isHotbarItemIconsEnabled() {
        return prefs.getBoolean(KEY_HOTBAR_ITEM_ICONS, false);
    }

    public void setHotbarItemIconsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HOTBAR_ITEM_ICONS, enabled).apply();
    }

    public boolean isHotbarSlotEnabled(int slot) {
        if (slot < 1 || slot > 9) return false;
        return prefs.getBoolean(KEY_HOTBAR_SLOT_ENABLED_PREFIX + slot, true);
    }

    public void setHotbarSlotEnabled(int slot, boolean enabled) {
        if (slot < 1 || slot > 9) return;
        prefs.edit().putBoolean(KEY_HOTBAR_SLOT_ENABLED_PREFIX + slot, enabled).apply();
    }

    public int getAimSmoothing() {
        return prefs.getInt(KEY_AIM_SMOOTHING, DEFAULT_AIM_SMOOTHING);
    }

    public void setAimSmoothing(int percent) {
        prefs.edit().putInt(KEY_AIM_SMOOTHING, Math.max(0, Math.min(95, percent))).apply();
    }

    public boolean isAimCrosshairEnabled() {
        return prefs.getBoolean(KEY_AIM_CROSSHAIR, true);
    }

    public void setAimCrosshairEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_CROSSHAIR, enabled).apply();
    }

    public boolean isAimFlashEnabled() {
        return prefs.getBoolean(KEY_AIM_FLASH, true);
    }

    public void setAimFlashEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AIM_FLASH, enabled).apply();
    }

    public int getAimSensitivity() {
        return prefs.getInt(KEY_AIM_SENSITIVITY, DEFAULT_AIM_SENSITIVITY);
    }

    public void setAimSensitivity(int percent) {
        prefs.edit().putInt(KEY_AIM_SENSITIVITY, Math.max(10, Math.min(300, percent))).apply();
    }

    public int getAimCrosshairStyle() {
        return prefs.getInt(KEY_AIM_CROSSHAIR_STYLE, 1);
    }

    public void setAimCrosshairStyle(int style) {
        prefs.edit().putInt(KEY_AIM_CROSSHAIR_STYLE, Math.max(0, Math.min(2, style))).apply();
    }

    public int getAimCrosshairColor() {
        return prefs.getInt(KEY_AIM_CROSSHAIR_COLOR, DEFAULT_AIM_CROSSHAIR_COLOR);
    }

    public void setAimCrosshairColor(int color) {
        prefs.edit().putInt(KEY_AIM_CROSSHAIR_COLOR, color).apply();
    }

    public boolean isArmorHudShowTarget() {
        return prefs.getBoolean(KEY_ARMOR_HUD_SHOW_TARGET, false);
    }

    public void setArmorHudShowTarget(boolean show) {
        prefs.edit().putBoolean(KEY_ARMOR_HUD_SHOW_TARGET, show).apply();
    }

    public boolean isArmorHudShowEnchants() {
        return prefs.getBoolean(KEY_ARMOR_HUD_SHOW_ENCHANTS, false);
    }

    public void setArmorHudShowEnchants(boolean show) {
        prefs.edit().putBoolean(KEY_ARMOR_HUD_SHOW_ENCHANTS, show).apply();
    }

    public boolean isArmorHudStacked() {
        return prefs.getBoolean(KEY_ARMOR_HUD_STACKED, true);
    }

    public void setArmorHudStacked(boolean stacked) {
        prefs.edit().putBoolean(KEY_ARMOR_HUD_STACKED, stacked).apply();
    }

    public int getArmorHudRefreshMs() {
        return prefs.getInt(KEY_ARMOR_HUD_REFRESH_MS, DEFAULT_ARMOR_HUD_REFRESH_MS);
    }

    public void setArmorHudRefreshMs(int ms) {
        prefs.edit().putInt(KEY_ARMOR_HUD_REFRESH_MS, Math.max(50, Math.min(1000, ms))).apply();
    }

    public int getCrystalMinSelfHp() {
        return prefs.getInt(KEY_CRYSTAL_MIN_SELF_HP, DEFAULT_CRYSTAL_MIN_SELF_HP);
    }

    public void setCrystalMinSelfHp(int hp) {
        prefs.edit().putInt(KEY_CRYSTAL_MIN_SELF_HP, Math.max(1, Math.min(20, hp))).apply();
    }

    public int getCrystalMaxRange() {
        return prefs.getInt(KEY_CRYSTAL_MAX_RANGE, DEFAULT_CRYSTAL_MAX_RANGE);
    }

    public void setCrystalMaxRange(int range) {
        prefs.edit().putInt(KEY_CRYSTAL_MAX_RANGE, Math.max(1, Math.min(8, range))).apply();
    }

    public int getCrystalPlacementDelayMs() {
        return prefs.getInt(KEY_CRYSTAL_PLACEMENT_DELAY_MS, DEFAULT_CRYSTAL_PLACEMENT_DELAY_MS);
    }

    public void setCrystalPlacementDelayMs(int ms) {
        prefs.edit().putInt(KEY_CRYSTAL_PLACEMENT_DELAY_MS, Math.max(0, Math.min(1000, ms))).apply();
    }

    /** When true the module only marks the ideal spot; it never issues place/break itself. */
    public boolean isCrystalManualAssist() {
        return prefs.getBoolean(KEY_CRYSTAL_MANUAL_ASSIST, true);
    }

    public void setCrystalManualAssist(boolean manualAssist) {
        prefs.edit().putBoolean(KEY_CRYSTAL_MANUAL_ASSIST, manualAssist).apply();
    }

    public int getCrystalKeybind() {
        return prefs.getInt(KEY_CRYSTAL_KEYBIND, 0);
    }

    public void setCrystalKeybind(int keyCode) {
        prefs.edit().putInt(KEY_CRYSTAL_KEYBIND, keyCode).apply();
    }

    public int getHitRegSensitivity() {
        return prefs.getInt(KEY_HITREG_SENSITIVITY, DEFAULT_HITREG_SENSITIVITY);
    }

    public void setHitRegSensitivity(int percent) {
        prefs.edit().putInt(KEY_HITREG_SENSITIVITY, Math.max(10, Math.min(300, percent))).apply();
    }

    public int getHitRegSmoothing() {
        return prefs.getInt(KEY_HITREG_SMOOTHING, DEFAULT_HITREG_SMOOTHING);
    }

    public void setHitRegSmoothing(int percent) {
        prefs.edit().putInt(KEY_HITREG_SMOOTHING, Math.max(0, Math.min(95, percent))).apply();
    }

    public int getHitRegPrediction() {
        return prefs.getInt(KEY_HITREG_PREDICTION, DEFAULT_HITREG_PREDICTION);
    }

    public void setHitRegPrediction(int percent) {
        prefs.edit().putInt(KEY_HITREG_PREDICTION, Math.max(0, Math.min(100, percent))).apply();
    }

    public boolean isHitRegHapticEnabled() {
        return prefs.getBoolean(KEY_HITREG_HAPTIC, true);
    }

    public void setHitRegHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HITREG_HAPTIC, enabled).apply();
    }

    public int getHitTimingCooldownMs() {
        return prefs.getInt(KEY_HIT_TIMING_COOLDOWN_MS,
                (int) org.chimeramc.client.core.mods.inbuilt.overlay.HitTimingSolver.DEFAULT_COOLDOWN_MS);
    }

    public void setHitTimingCooldownMs(int ms) {
        prefs.edit().putInt(KEY_HIT_TIMING_COOLDOWN_MS, Math.max(50, Math.min(2000, ms))).apply();
    }

    public boolean isHitTimingShowCombo() {
        return prefs.getBoolean(KEY_HIT_TIMING_SHOW_COMBO, true);
    }

    public void setHitTimingShowCombo(boolean show) {
        prefs.edit().putBoolean(KEY_HIT_TIMING_SHOW_COMBO, show).apply();
    }

    public boolean isHitTimingShowTimingBar() {
        return prefs.getBoolean(KEY_HIT_TIMING_SHOW_BAR, true);
    }

    public void setHitTimingShowTimingBar(boolean show) {
        prefs.edit().putBoolean(KEY_HIT_TIMING_SHOW_BAR, show).apply();
    }

    public boolean isHitboxShowPlayers() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_PLAYERS, true);
    }

    public void setHitboxShowPlayers(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_PLAYERS, show).apply();
    }

    public boolean isHitboxShowMobs() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_MOBS, true);
    }

    public void setHitboxShowMobs(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_MOBS, show).apply();
    }

    public boolean isHitboxShowItems() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_ITEMS, true);
    }

    public void setHitboxShowItems(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_ITEMS, show).apply();
    }

    public boolean isHitboxShowProjectiles() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_PROJECTILES, true);
    }

    public void setHitboxShowProjectiles(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_PROJECTILES, show).apply();
    }

    /** Thrown items (ender pearls, wind charges) are their own class from fired projectiles. */
    public boolean isHitboxShowThrownItems() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_THROWN_ITEMS, true);
    }

    public void setHitboxShowThrownItems(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_THROWN_ITEMS, show).apply();
    }

    public boolean isHitboxShowLookLine() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_LOOK_LINE, true);
    }

    public void setHitboxShowLookLine(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_LOOK_LINE, show).apply();
    }

    public boolean isHitboxShowCritLine() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_CRIT_LINE, true);
    }

    public void setHitboxShowCritLine(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_CRIT_LINE, show).apply();
    }

    public boolean isHitboxShowComboBox() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_COMBO_BOX, true);
    }

    public void setHitboxShowComboBox(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_COMBO_BOX, show).apply();
    }

    /** Whether the chest-height target sub-box is drawn. Default on. */
    public boolean isHitboxShowTargetBox() {
        return prefs.getBoolean(KEY_HITBOX_SHOW_TARGET_BOX, true);
    }

    /** Whether the chest-height target sub-box is drawn; alias used by the peer-feed overlay. */
    public boolean getHitboxShowTargetBox() {
        return isHitboxShowTargetBox();
    }

    public void setHitboxShowTargetBox(boolean show) {
        prefs.edit().putBoolean(KEY_HITBOX_SHOW_TARGET_BOX, show).apply();
    }

    /**
     * The peer box colour, or {@link Color#WHITE} when unset.
     *
     * <p>Default white rather than a themed accent: the box has to read against any world backdrop,
     * and the player's accent can be a low-contrast violet. Stored as an int so a picked colour
     * survives a restart without a separate colour-index scheme.
     */
    public int getHitboxPeerColor() {
        return prefs.getInt(KEY_HITBOX_PEER_COLOR, android.graphics.Color.WHITE);
    }

    public void setHitboxPeerColor(int color) {
        prefs.edit().putInt(KEY_HITBOX_PEER_COLOR, color).apply();
    }

    // --- Proximity voice ----------------------------------------------------------------

    public float getVoiceRangeBlocks() {
        return prefs.getFloat(KEY_VOICE_RANGE, DEFAULT_VOICE_RANGE);
    }

    public void setVoiceRangeBlocks(float blocks) {
        prefs.edit().putFloat(KEY_VOICE_RANGE,
                Math.max(MIN_VOICE_RANGE, Math.min(MAX_VOICE_RANGE, blocks))).apply();
    }

    public int getVoiceVolumePercent() {
        return prefs.getInt(KEY_VOICE_VOLUME, DEFAULT_VOICE_VOLUME);
    }

    public void setVoiceVolumePercent(int percent) {
        prefs.edit().putInt(KEY_VOICE_VOLUME, Math.max(0, Math.min(200, percent))).apply();
    }

    /** The listener's channel: who they hear and who hears them, beyond the open channel. */
    public String getVoiceChannel() {
        return org.chimeramc.client.core.voice.VoiceChannel.normalize(
                prefs.getString(KEY_VOICE_CHANNEL, org.chimeramc.client.core.voice.VoiceChannel.WORLD));
    }

    public void setVoiceChannel(String channel) {
        String normalized = org.chimeramc.client.core.voice.VoiceChannel.normalize(channel);
        // A typed code is a private, join-by-code channel; anything else is a public named room.
        // Deriving visibility from the id keeps the free-text channel field and the directory
        // from disagreeing about the same channel.
        boolean privateChannel = org.chimeramc.client.core.voice.VoiceChannel.isJoinCode(normalized);
        prefs.edit()
                .putString(KEY_VOICE_CHANNEL, normalized)
                .putBoolean(KEY_VOICE_CHANNEL_PRIVATE, privateChannel)
                .apply();
    }

    /**
     * Joins a channel by its id, recording the display name and visibility to advertise.
     *
     * <p>Joining by code is the same operation as picking a channel: the id is the join key, so
     * the only extra state is the name/visibility other peers need to build their directory.
     */
    public void joinVoiceChannel(String channel, String displayName, boolean isPrivate) {
        prefs.edit()
                .putString(KEY_VOICE_CHANNEL,
                        org.chimeramc.client.core.voice.VoiceChannel.normalize(channel))
                .putString(KEY_VOICE_CHANNEL_NAME, displayName == null ? "" : displayName.trim())
                .putBoolean(KEY_VOICE_CHANNEL_PRIVATE, isPrivate)
                .apply();
    }

    /** The display name for the current channel, or "" when on the open, unnamed channel. */
    public String getVoiceChannelName() {
        return prefs.getString(KEY_VOICE_CHANNEL_NAME, "");
    }

    /** Whether the current channel is private (join-by-code, never listed). */
    public boolean isVoiceChannelPrivate() {
        return prefs.getBoolean(KEY_VOICE_CHANNEL_PRIVATE, false);
    }

    /**
     * The current channel's advertised capacity, or {@code CAPACITY_NONE} for no cap.
     *
     * <p>Private channels never carry a capacity: the code is the cap. Reading it through this
     * method (rather than the raw pref) keeps that rule in one place, so a private channel left
     * over from a public one cannot advertise a stale number.
     */
    public int getVoiceChannelCapacity() {
        if (isVoiceChannelPrivate()) return org.chimeramc.client.core.voice.VoiceProtocol.CAPACITY_NONE;
        return org.chimeramc.client.core.voice.VoiceChannelCapacity.clampHostCapacity(
                prefs.getInt(KEY_VOICE_CAPACITY,
                        org.chimeramc.client.core.voice.VoiceProtocol.CAPACITY_NONE));
    }

    /** Sets the advertised capacity for a public channel; ignored for private channels. */
    public void setVoiceChannelCapacity(int capacity) {
        prefs.edit().putInt(KEY_VOICE_CAPACITY,
                org.chimeramc.client.core.voice.VoiceChannelCapacity.clampHostCapacity(capacity)).apply();
    }

    /** Restores the persisted local per-member mute set into {@code target}. */
    public void loadVoiceMutes(org.chimeramc.client.core.voice.VoiceMutes target) {
        if (target == null) return;
        String raw = prefs.getString(KEY_VOICE_MUTES, "");
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(",")) ids.add(part);
        }
        target.restoreFrom(ids);
    }

    /** Persists the local per-member mute set. Nothing here goes on the wire; it is viewer-only. */
    public void saveVoiceMutes(org.chimeramc.client.core.voice.VoiceMutes mutes) {
        prefs.edit().putString(KEY_VOICE_MUTES, mutes == null ? "" : mutes.serialize()).apply();
    }

    /** The mic-icon sprite style for the in-world nametag icon; see {@code MicIconStyle}. */
    public int getVoiceIconStyle() {
        return prefs.getInt(KEY_VOICE_ICON_STYLE,
                org.chimeramc.client.core.mods.inbuilt.overlay.MicIconStyle.STYLE_CLASSIC);
    }

    public void setVoiceIconStyle(int style) {
        prefs.edit().putInt(KEY_VOICE_ICON_STYLE,
                org.chimeramc.client.core.mods.inbuilt.overlay.MicIconStyle.clamp(style)).apply();
    }

    /** Whether the speaking state animates (rings/glow) or stays a static glyph. */
    public boolean isVoiceIconAnimated() {
        return prefs.getBoolean(KEY_VOICE_ICON_ANIMATE, true);
    }

    public void setVoiceIconAnimated(boolean animated) {
        prefs.edit().putBoolean(KEY_VOICE_ICON_ANIMATE, animated).apply();
    }

    /** Whether the in-world nametag mic icon is drawn at all. */
    public boolean isVoiceNametagIconEnabled() {
        return prefs.getBoolean(KEY_VOICE_ICON_SHOW_NAMETAG, true);
    }

    public void setVoiceNametagIconEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_ICON_SHOW_NAMETAG, enabled).apply();
    }

    /** The beacon visibility byte for the current channel. */
    public byte getVoiceChannelVisibility() {
        return isVoiceChannelPrivate()
                ? org.chimeramc.client.core.voice.VoiceProtocol.VISIBILITY_PRIVATE
                : org.chimeramc.client.core.voice.VoiceProtocol.VISIBILITY_PUBLIC;
    }

    /** Whether the player's own microphone is transmitted. Off means listen-only. */
    public boolean isVoiceMicEnabled() {
        return prefs.getBoolean(KEY_VOICE_MIC, true);
    }

    public void setVoiceMicEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_MIC, enabled).apply();
    }

    /**
     * Whether the equipped cape/accessory/pet is advertised to other Chimera users in the world.
     *
     * <p>On by default: the feature is client-side only and shows your cosmetics to the same
     * players who can already hear you on proximity voice, so the expected behaviour is that it
     * works. Turning it off stops both advertising and collecting.
     */
    public boolean isCosmeticSyncEnabled() {
        return prefs.getBoolean(KEY_COSMETIC_SYNC_ENABLED, true);
    }

    public void setCosmeticSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_COSMETIC_SYNC_ENABLED, enabled).apply();
    }

    /**
     * The manual unicast peer for cosmetic sync, as {@code host:port}, or "" when none is set.
     *
     * <p>This is the fallback route when no relay is configured: a direct peer address cannot be
     * discovered automatically, so the player pastes one. It is ignored while a relay is
     * configured, because the relay is the better route (it reconnects and reaches a whole
     * session).
     */
    public String getCosmeticManualPeer() {
        return prefs.getString(KEY_COSMETIC_MANUAL_PEER, "");
    }

    public void setCosmeticManualPeer(String peer) {
        prefs.edit().putString(KEY_COSMETIC_MANUAL_PEER,
                peer == null ? "" : peer.trim()).apply();
    }

    /** Resets the channel to the open one, used by the panel's "World" button. */
    public void useWorldVoiceChannel() {
        setVoiceChannel(org.chimeramc.client.core.voice.VoiceChannel.WORLD);
    }

    public int getVoiceChatterRefreshMs() {
        return prefs.getInt(KEY_VOICE_REFRESH_MS, DEFAULT_VOICE_REFRESH_MS);
    }

    public void setVoiceChatterRefreshMs(int ms) {
        prefs.edit().putInt(KEY_VOICE_REFRESH_MS, Math.max(50, Math.min(1000, ms))).apply();
    }

    // --- Relay transport ------------------------------------------------------------------

    /**
     * Whether the relay transport is enabled.
     *
     * <p>Off by default: LAN multicast is the zero-configuration path and stays the default, so a
     * player who has not set up a server is unaffected. Turning it on switches the session to the
     * relay address below.
     */
    public boolean isVoiceRelayEnabled() {
        return prefs.getBoolean(KEY_VOICE_RELAY_ENABLED, false);
    }

    public void setVoiceRelayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_RELAY_ENABLED, enabled).apply();
    }

    /** The configured relay address ({@code host} or {@code host:port}), or "" when unset. */
    public String getVoiceRelayAddress() {
        String configured = prefs.getString(KEY_VOICE_RELAY_ADDRESS, "");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        // Nothing saved: fall back to the address baked in at build time, so a release build can
        // point at the operator's server with no first-run setup. A saved value (including an
        // explicit clear) always wins, because an empty pref means "I set this".
        return org.chimeramc.client.BuildConfig.DEFAULT_VOICE_RELAY_ADDRESS;
    }

    public void setVoiceRelayAddress(String address) {
        prefs.edit().putString(KEY_VOICE_RELAY_ADDRESS,
                address == null ? "" : address.trim()).apply();
    }

    /**
     * Whether the relay address shown/in use comes from the build default rather than user input.
     * Used by the Voice screen to say where the default came from instead of presenting a value
     * the user never typed as if they had.
     */
    public boolean isVoiceRelayAddressFromBuildDefault() {
        String configured = prefs.getString(KEY_VOICE_RELAY_ADDRESS, "");
        return (configured == null || configured.trim().isEmpty())
                && !org.chimeramc.client.BuildConfig.DEFAULT_VOICE_RELAY_ADDRESS.isEmpty();
    }

    /**
     * A stable, non-identifying device id for the relay's token binding and device bans.
     *
     * <p>Deliberately not {@code ANDROID_ID} or any hardware id: it is a random value generated on
     * first use and kept in preferences, so it identifies this install to this relay for banning
     * and token binding without being a cross-app tracking identifier. Rotating it means a fresh
     * identity, which a user can do by clearing the pref; that is the intended escape hatch.
     */
    public String getVoiceDeviceId() {
        String existing = prefs.getString(KEY_VOICE_DEVICE_ID, "");
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        String generated = java.util.UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(KEY_VOICE_DEVICE_ID, generated).apply();
        return generated;
    }

    /**
     * The shared password for the relay, or "" when the server is open.
     *
     * <p>Stored as a plain preference, not encrypted: it is a shared room secret, not an account
     * credential, and the launcher has no keystore-backed store for a per-feature value. This is
     * the same trust level as the relay itself, which is documented as not being a secure channel.
     */
    public String getVoiceRelayPassword() {
        return prefs.getString(KEY_VOICE_RELAY_PASSWORD, "");
    }

    public void setVoiceRelayPassword(String password) {
        prefs.edit().putString(KEY_VOICE_RELAY_PASSWORD,
                password == null ? "" : password.trim()).apply();
    }

    /**
     * The shared token secret, if the relay uses signed join tokens instead of (or as well as) a
     * plain password. Empty means "password mode".
     *
     * <p>Same trust level as the password: a shared room secret, not an account credential. The
     * client never sends it; it signs a short-lived token with it (see
     * {@code org.chimeramc.client.core.voice.VoiceToken}), so a captured packet is useless once the
     * token expires.
     */
    public String getVoiceRelayTokenSecret() {
        return prefs.getString(KEY_VOICE_RELAY_TOKEN_SECRET, "");
    }

    public void setVoiceRelayTokenSecret(String secret) {
        prefs.edit().putString(KEY_VOICE_RELAY_TOKEN_SECRET,
                secret == null ? "" : secret.trim()).apply();
    }

    /**
     * The saved loadouts, parsed from the stored JSON.
     *
     * <p>A corrupt value reads as "no loadouts" rather than throwing, so a bad pref cannot take
     * the Mod Menu down.
     */
    public java.util.List<ModLoadoutStore.Loadout> getModLoadouts() {
        try {
            return ModLoadoutStore.fromJson(prefs.getString(KEY_MOD_LOADOUTS, "[]"));
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public void saveModLoadouts(java.util.List<ModLoadoutStore.Loadout> loadouts) {
        prefs.edit().putString(KEY_MOD_LOADOUTS, ModLoadoutStore.toJson(loadouts)).apply();
    }

    /** Saves the current on/off state of every id in {@code moduleIds} under {@code name}. */
    public void saveLoadout(String name, java.util.List<String> moduleIds) {
        ModLoadoutStore.Loadout loadout =
                ModLoadoutStore.capture(name, moduleIds, this::isInbuiltModEnabledOrFalse);
        saveModLoadouts(ModLoadoutStore.upsert(getModLoadouts(), loadout));
    }

    /** Applies a saved loadout, returning true when one by that name existed. */
    public boolean applyLoadout(String name) {
        ModLoadoutStore.Loadout loadout = ModLoadoutStore.find(getModLoadouts(), name);
        if (loadout == null) return false;
        ModLoadoutStore.apply(loadout, this::setInbuiltModEnabled);
        return true;
    }

    // ---- PvP Suite (V1.1) ----------------------------------------------------------------

    /** Where the Reach Indicator sits: {@code below_crosshair} (0) or {@code above_hotbar} (1). */
    public int getReachIndicatorPosition() {
        return prefs.getInt(KEY_REACH_POSITION, 0);
    }

    public void setReachIndicatorPosition(int position) {
        prefs.edit().putInt(KEY_REACH_POSITION, Math.max(0, Math.min(1, position))).apply();
    }

    /** ARGB colour of the predicted trajectory arc. Default a bright lime. */
    public int getTrajectoryColor() {
        return prefs.getInt(KEY_TRAJECTORY_COLOR, 0xFF7CFC5A);
    }

    public void setTrajectoryColor(int color) {
        prefs.edit().putInt(KEY_TRAJECTORY_COLOR, color).apply();
    }

    /** How far ahead the hit-prediction marker is drawn, in milliseconds. */
    public int getHitPredictionLookAheadMs() {
        return prefs.getInt(KEY_HIT_PREDICTION_LOOKAHEAD_MS, 1000);
    }

    public void setHitPredictionLookAheadMs(int ms) {
        prefs.edit().putInt(KEY_HIT_PREDICTION_LOOKAHEAD_MS, Math.max(500, Math.min(1500, ms))).apply();
    }

    /** Kill-effect particle style index: burst (0), column (1), ring (2). */
    public int getKillEffectStyle() {
        return prefs.getInt(KEY_KILL_EFFECT_STYLE, 0);
    }

    public void setKillEffectStyle(int style) {
        prefs.edit().putInt(KEY_KILL_EFFECT_STYLE, Math.max(0, Math.min(2, style))).apply();
    }

    /** ARGB colour of the kill-effect particles. Default a warm gold. */
    public int getKillEffectColor() {
        return prefs.getInt(KEY_KILL_EFFECT_COLOR, 0xFFFFC24B);
    }

    public void setKillEffectColor(int color) {
        prefs.edit().putInt(KEY_KILL_EFFECT_COLOR, color).apply();
    }

    public void deleteLoadout(String name) {
        saveModLoadouts(ModLoadoutStore.remove(getModLoadouts(), name));
    }

    private boolean isInbuiltModEnabledOrFalse(String modId) {
        return resolveInbuiltModEnabled(modId, false);
    }
}
