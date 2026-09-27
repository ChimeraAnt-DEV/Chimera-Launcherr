package org.chimeramc.client.core.mods.inbuilt.model;

public final class ModIds {
    /** Group id for combat/PvP modules; the Mod Menu exposes it as the PvP tab. */
    public static final String GROUP_PVP = "pvp";

    /** Group id for social modules (proximity voice), so the menu can section them apart. */
    public static final String GROUP_VOICE = "voice";

    public static final String QUICK_DROP = "quick_drop";
    public static final String CAMERA_PERSPECTIVE = "camera_perspective";
    public static final String TOGGLE_HUD = "toggle_hud";
    public static final String AUTO_SPRINT = "auto_sprint";
    public static final String CHICK_PET = "chick_pet";
    public static final String ZOOM = "zoom";
    public static final String FPS_DISPLAY = "fps_display";
    public static final String CPS_DISPLAY = "cps_display";
    public static final String SNAPLOOK = "snaplook";
    public static final String VIRTUAL_CURSOR = "virtual_cursor";
    public static final String GYRO = "gyro";
    public static final String POJAV_CONTROLS = "pojav_controls";
    public static final String MORE_BUTTONS = "more_buttons";
    public static final String HOTBAR_SLOT = "hotbar_slot";
    public static final String AIM_SETTINGS = "aim_settings";
    public static final String MOD_MENU = "mod_menu";
    public static final String ARMOR_HUD = "armor_hud";
    public static final String CRYSTAL_OPTIMIZER = "crystal_optimizer";
    public static final String HIT_REGISTRATION = "hit_registration";
    public static final String HIT_TIMING = "hit_timing";
    public static final String HITBOX = "hitbox";
    public static final String VOICE_CHAT = "voice_chat";

    /**
     * Combat-oriented modules the Mod Menu files under its PvP tab. Kept here rather than
     * derived from the group string at the call site so the filter and the provider cannot
     * drift apart.
     */
    private static final java.util.Set<String> PVP_MODULES = java.util.Set.of(
            AIM_SETTINGS, CPS_DISPLAY, SNAPLOOK, CRYSTAL_OPTIMIZER, HIT_REGISTRATION,
            HIT_TIMING, HITBOX);

    public static boolean isPvpModule(String modId) {
        return modId != null && PVP_MODULES.contains(modId);
    }

    /**
     * Social modules the Mod Menu files under its Voice section. Same reasoning as
     * {@link #isPvpModule}: the section predicate and the provider's grouping must agree.
     */
    private static final java.util.Set<String> VOICE_MODULES = java.util.Set.of(VOICE_CHAT);

    public static boolean isVoiceModule(String modId) {
        return modId != null && VOICE_MODULES.contains(modId);
    }

    /**
     * True for modules that need a per-frame data feed from the game process (durability,
     * target entity, placement geometry, entity boxes). They render a "no game data"
     * placeholder rather than a stale or fabricated reading until a native provider publishes
     * values.
     */
    public static boolean requiresGameData(String modId) {
        return ARMOR_HUD.equals(modId) || CRYSTAL_OPTIMIZER.equals(modId) || HITBOX.equals(modId);
    }

    private ModIds() {}
}
