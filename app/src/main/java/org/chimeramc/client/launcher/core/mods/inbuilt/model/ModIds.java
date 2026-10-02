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
    /** PvP Suite (V1.1): overlay-only visual aids, filed under the General tab. */
    public static final String REACH_INDICATOR = "reach_indicator";
    public static final String TRAJECTORY_PREDICTION = "trajectory_prediction";
    public static final String HIT_PREDICTION = "hit_prediction";
    public static final String KILL_EFFECTS = "kill_effects";

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
     * True for modules that are <em>entirely</em> dependent on a per-frame data feed from the
     * game process and have no other route to a result: durability/target reads (Armor HUD) and
     * placement geometry (Crystal Optimizer). With no native provider installed these cannot
     * produce anything, so the Mod Menu badges them and refuses the toggle.
     *
     * <p>The Hitboxes module is deliberately <em>not</em> here. It draws other players from the
     * proximity-voice feed, which needs no native hook at all, so it works today; only some of its
     * entity kinds need the native feed. Those are listed by {@link #outputRequiresGameData} and
     * are disabled per-option rather than taking the whole module down.
     */
    public static boolean requiresGameData(String modId) {
        return ARMOR_HUD.equals(modId) || CRYSTAL_OPTIMIZER.equals(modId);
    }

    /**
     * Hitbox outputs that can only be produced by the native entity feed.
     *
     * <p>A hitbox for a mob, a dropped item, a thrown item or a projectile needs the game's
     * entity list, which this build cannot read (see the native-feed notes). The player box, the
     * aim line and the chest-height target box are <em>not</em> here: peers advertise their own
     * position and view over the voice protocol, so those three work without the game process.
     * Keeping the distinction in one place is what stops the config dialog from offering a switch
     * that can never draw anything.
     */
    private static final java.util.Set<String> HITBOX_NATIVE_OUTPUTS = java.util.Set.of(
            "hitbox_show_mobs", "hitbox_show_items", "hitbox_show_projectiles",
            "hitbox_show_thrown_items", "hitbox_show_crit_line", "hitbox_show_combo_box");

    public static boolean outputRequiresGameData(String modId, String configKey) {
        return HITBOX.equals(modId) && configKey != null && HITBOX_NATIVE_OUTPUTS.contains(configKey);
    }

    private ModIds() {}
}
