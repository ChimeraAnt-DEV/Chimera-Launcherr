package org.chimeramc.client.core.mods.inbuilt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.core.mods.inbuilt.overlay.InbuiltOverlayManager;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitTimingSolver;
import org.chimeramc.client.core.mods.inbuilt.overlay.MoreButtonsEditor;
import org.chimeramc.pojavcontrols.PojavControls;

import java.util.ArrayList;
import java.util.List;

public final class InbuiltModuleProvider {
    private static final String GROUP_ID = "inbuilt";
    private static final String GROUP_PVP_ID = ModIds.GROUP_PVP;
    private static final String MOD_ID = "inbuilt";

    private static final String CFG_OVERLAY_SIZE = "overlay_size";
    private static final String CFG_OVERLAY_OPACITY = "overlay_opacity";
    private static final String CFG_OVERLAY_LOCK = "overlay_lock";
    private static final String CFG_OVERLAY_SHOW_EVERYWHERE = "overlay_show_everywhere";
    private static final String CFG_AUTO_SPRINT_KEYBIND = "auto_sprint_keybind";
    private static final String CFG_CURSOR_SENSITIVITY = "cursor_sensitivity";
    private static final String CFG_ZOOM_LEVEL = "zoom_level";
    private static final String CFG_ZOOM_TRANSITION = "zoom_transition";
    private static final String CFG_ZOOM_KEYBIND = "zoom_keybind";
    private static final String CFG_GYRO_SENSITIVITY_X = "gyro_sensitivity_x";
    private static final String CFG_GYRO_SENSITIVITY_Y = "gyro_sensitivity_y";
    private static final String CFG_GYRO_INVERT_X = "gyro_invert_x";
    private static final String CFG_GYRO_INVERT_Y = "gyro_invert_y";
    private static final String CFG_GYRO_DEADZONE = "gyro_deadzone";
    private static final String CFG_HOTBAR_ITEM_ICONS = "hotbar_item_icons";
    private static final String CFG_HOTBAR_SLOT_PREFIX = "hotbar_slot_";
    private static final String CFG_HOTBAR_SLOT_ENABLED = "enabled";
    private static final String CFG_HOTBAR_SLOT_SIZE = "size";
    private static final String CFG_HOTBAR_SLOT_OPACITY = "opacity";
    private static final String CFG_AIM_SMOOTHING = "aim_smoothing";
    private static final String CFG_AIM_CROSSHAIR = "aim_crosshair";
    private static final String CFG_AIM_FLASH = "aim_flash";
    private static final String CFG_AIM_SENSITIVITY = "aim_sensitivity";
    private static final String CFG_AIM_CROSSHAIR_STYLE = "aim_crosshair_style";
    private static final String CFG_AIM_CROSSHAIR_COLOR = "aim_crosshair_color";
    private static final String CFG_ARMOR_HUD_SHOW_TARGET = "armor_hud_show_target";
    private static final String CFG_ARMOR_HUD_SHOW_ENCHANTS = "armor_hud_show_enchants";
    private static final String CFG_ARMOR_HUD_STACKED = "armor_hud_stacked";
    private static final String CFG_ARMOR_HUD_REFRESH_MS = "armor_hud_refresh_ms";
    private static final String CFG_CRYSTAL_MIN_SELF_HP = "crystal_min_self_hp";
    private static final String CFG_CRYSTAL_MAX_RANGE = "crystal_max_range";
    private static final String CFG_CRYSTAL_PLACEMENT_DELAY_MS = "crystal_placement_delay_ms";
    private static final String CFG_CRYSTAL_MANUAL_ASSIST = "crystal_manual_assist";
    private static final String CFG_CRYSTAL_KEYBIND = "crystal_keybind";
    private static final String CFG_HITREG_SENSITIVITY = "hitreg_sensitivity";
    private static final String CFG_HITREG_SMOOTHING = "hitreg_smoothing";
    private static final String CFG_HITREG_PREDICTION = "hitreg_prediction";
    private static final String CFG_HITREG_HAPTIC = "hitreg_haptic";
    private static final String CFG_HIT_TIMING_COOLDOWN_MS = "hit_timing_cooldown_ms";
    private static final String CFG_HIT_TIMING_SHOW_COMBO = "hit_timing_show_combo";
    private static final String CFG_HIT_TIMING_SHOW_BAR = "hit_timing_show_bar";
    private static final String CFG_HITBOX_SHOW_PLAYERS = "hitbox_show_players";
    private static final String CFG_HITBOX_SHOW_MOBS = "hitbox_show_mobs";
    private static final String CFG_HITBOX_SHOW_ITEMS = "hitbox_show_items";
    private static final String CFG_HITBOX_SHOW_PROJECTILES = "hitbox_show_projectiles";
    private static final String CFG_HITBOX_SHOW_THROWN_ITEMS = "hitbox_show_thrown_items";
    private static final String CFG_HITBOX_SHOW_LOOK_LINE = "hitbox_show_look_line";
    private static final String CFG_HITBOX_SHOW_CRIT_LINE = "hitbox_show_crit_line";
    private static final String CFG_HITBOX_SHOW_COMBO_BOX = "hitbox_show_combo_box";

    private InbuiltModuleProvider() {
    }

    public static List<UnifiedMod> load(Activity activity) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        String groupName = activity.getString(R.string.mod_menu_group_inbuilt);
        List<UnifiedMod> mods = new ArrayList<>();

        mods.add(create(activity, manager, overlayManager, ModIds.QUICK_DROP,
                R.string.inbuilt_mod_quick_drop, R.string.inbuilt_mod_quick_drop_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.CAMERA_PERSPECTIVE,
                R.string.inbuilt_mod_camera, R.string.inbuilt_mod_camera_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.TOGGLE_HUD,
                R.string.inbuilt_mod_hud, R.string.inbuilt_mod_hud_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.AUTO_SPRINT,
                R.string.inbuilt_mod_autosprint, R.string.inbuilt_mod_autosprint_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.CHICK_PET,
                R.string.inbuilt_mod_chick_pet, R.string.inbuilt_mod_chick_pet_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.ZOOM,
                R.string.inbuilt_mod_zoom, R.string.inbuilt_mod_zoom_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.FPS_DISPLAY,
                R.string.inbuilt_mod_fps_display, R.string.inbuilt_mod_fps_display_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.CPS_DISPLAY,
                R.string.inbuilt_mod_cps_display, R.string.inbuilt_mod_cps_display_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.SNAPLOOK,
                R.string.inbuilt_mod_snaplook, R.string.inbuilt_mod_snaplook_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.VIRTUAL_CURSOR,
                R.string.inbuilt_mod_virtual_cursor, R.string.inbuilt_mod_virtual_cursor_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.GYRO,
                R.string.inbuilt_mod_gyro, R.string.inbuilt_mod_gyro_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.POJAV_CONTROLS,
                R.string.inbuilt_mod_pojav_controls, R.string.inbuilt_mod_pojav_controls_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.MORE_BUTTONS,
                R.string.inbuilt_mod_more_buttons, R.string.inbuilt_mod_more_buttons_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.HOTBAR_SLOT,
                R.string.inbuilt_mod_hotbar_slot, R.string.inbuilt_mod_hotbar_slot_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.AIM_SETTINGS,
                R.string.inbuilt_mod_aim_settings, R.string.inbuilt_mod_aim_settings_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.ARMOR_HUD,
                R.string.inbuilt_mod_armor_hud, R.string.inbuilt_mod_armor_hud_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.CRYSTAL_OPTIMIZER,
                R.string.inbuilt_mod_crystal_optimizer, R.string.inbuilt_mod_crystal_optimizer_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.HIT_REGISTRATION,
                R.string.inbuilt_mod_hit_registration, R.string.inbuilt_mod_hit_registration_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.HIT_TIMING,
                R.string.inbuilt_mod_hit_timing, R.string.inbuilt_mod_hit_timing_desc,
                groupName));
        mods.add(create(activity, manager, overlayManager, ModIds.HITBOX,
                R.string.inbuilt_mod_hitbox, R.string.inbuilt_mod_hitbox_desc,
                groupName));

        return groupPvpLast(mods);
    }

    /**
     * Moves PvP modules to the end, preserving the declared order within each bucket. The menu
     * draws one section header per contiguous group, and the combat modules are declared apart
     * from each other, so without this the PvP section would render twice.
     */
    static List<UnifiedMod> groupPvpLast(List<UnifiedMod> mods) {
        List<UnifiedMod> ordered = new ArrayList<>(mods.size());
        for (UnifiedMod mod : mods) {
            if (!ModIds.isPvpModule(mod.getId())) ordered.add(mod);
        }
        for (UnifiedMod mod : mods) {
            if (ModIds.isPvpModule(mod.getId())) ordered.add(mod);
        }
        return ordered;
    }

    private static UnifiedMod create(Activity activity, InbuiltModManager manager,
                                     InbuiltOverlayManager overlayManager, String id,
                                     int nameRes, int descRes, String groupName) {
        boolean active = overlayManager != null
                ? overlayManager.isModActive(id)
                : manager.resolveInbuiltModEnabled(id, false);
        boolean customConfig = ModIds.POJAV_CONTROLS.equals(id) || ModIds.MORE_BUTTONS.equals(id)
                || ModIds.ARMOR_HUD.equals(id) || ModIds.CRYSTAL_OPTIMIZER.equals(id)
                || ModIds.HIT_REGISTRATION.equals(id) || ModIds.HIT_TIMING.equals(id)
                || ModIds.HITBOX.equals(id);
        // Combat modules get their own PvP section so the tab is a real destination, not just a
        // filter over the inbuilt list. They remain inbuilt modules, so the Inbuilt filter and
        // the "Inbuilt" grouping still find them.
        boolean pvpModule = ModIds.isPvpModule(id);
        UnifiedMod result = new UnifiedMod(
                id,
                activity.getString(nameRes),
                activity.getString(descRes),
                MOD_ID,
                UnifiedMod.Source.INBUILT,
                active,
                createConfigs(activity, manager, id),
                customConfig,
                pvpModule ? GROUP_PVP_ID : GROUP_ID,
                pvpModule ? activity.getString(R.string.mod_menu_group_pvp) : groupName,
                (mod, enabled) -> setEnabled(manager, mod, enabled),
                (mod, config, value) -> setConfig(manager, mod, config, value),
                ModIds.POJAV_CONTROLS.equals(id)
                        ? mod -> PojavControls.launchEditor(activity)
                        : (ModIds.MORE_BUTTONS.equals(id) ? mod -> MoreButtonsEditor.show(activity) : null)
        );
        result.setLocalConfigSchema(createLocalConfigSchema(activity, result));
        return result;
    }

    private static RuntimeConfigSchema createLocalConfigSchema(Context context, UnifiedMod mod) {
        String modId = mod.getId();
        if (ModIds.ARMOR_HUD.equals(modId) || ModIds.CRYSTAL_OPTIMIZER.equals(modId)
                || ModIds.HIT_REGISTRATION.equals(modId) || ModIds.HIT_TIMING.equals(modId)
                || ModIds.HITBOX.equals(modId)) {
            return createCombatConfigSchema(context, mod);
        }
        boolean hotbar = ModIds.HOTBAR_SLOT.equals(mod.getId());
        if (!hotbar && !ModIds.GYRO.equals(mod.getId())) return null;
        try {
            JSONArray categories = new JSONArray();
            JSONArray nodes = new JSONArray();
            if (hotbar) {
                categories.put(configCategory(context, "slots", R.string.mod_config_category_slots));
                categories.put(configCategory(context, "appearance", R.string.mod_config_category_appearance));
                categories.put(configCategory(context, "behavior", R.string.mod_config_category_behavior));
                nodes.put(configNode(context, mod, CFG_HOTBAR_ITEM_ICONS, "slots"));
                JSONArray slots = new JSONArray();
                for (int slot = 1; slot <= 9; slot++) {
                    String key = hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_ENABLED);
                    slots.put(new JSONObject().put("key", key).put("value", key)
                            .put("label", mod.findConfigEntry(key).displayName));
                    String section = "slot_" + slot;
                    nodes.put(new JSONObject().put("id", section).put("type", "section")
                            .put("category", "appearance").put("collapsible", true)
                            .put("title", context.getString(R.string.mod_config_hotbar_slot_section, slot)));
                    nodes.put(configNode(context, mod, hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_SIZE), "appearance")
                            .put("section", section));
                    nodes.put(configNode(context, mod, hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_OPACITY), "appearance")
                            .put("section", section));
                }
                nodes.put(new JSONObject().put("id", "visible_slots").put("type", "toggle_group")
                        .put("category", "slots").put("title", context.getString(R.string.mod_config_visible_slots))
                        .put("options", slots));
                nodes.put(configNode(context, mod, CFG_OVERLAY_LOCK, "behavior"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "behavior"));
            } else {
                categories.put(configCategory(context, "motion", R.string.mod_config_category_motion));
                categories.put(configCategory(context, "button", R.string.mod_config_category_button));
                nodes.put(configNode(context, mod, CFG_GYRO_SENSITIVITY_X, "motion"));
                nodes.put(configNode(context, mod, CFG_GYRO_SENSITIVITY_Y, "motion"));
                nodes.put(configNode(context, mod, CFG_GYRO_INVERT_X, "motion"));
                nodes.put(configNode(context, mod, CFG_GYRO_INVERT_Y, "motion"));
                nodes.put(configNode(context, mod, CFG_GYRO_DEADZONE, "motion"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SIZE, "button"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_OPACITY, "button"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_LOCK, "button"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "button"));
            }
            return RuntimeConfigSchema.parse(new JSONObject().put("version", 2)
                    .put("default_category", hotbar ? "slots" : "motion")
                    .put("categories", categories).put("nodes", nodes).toString());
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to build inbuilt config schema", e);
        }
    }

    /**
     * Category layout for the three combat modules. They each get an explicit schema so the
     * dialog groups related settings rather than listing one flat column.
     */
    private static RuntimeConfigSchema createCombatConfigSchema(Context context, UnifiedMod mod) {
        String modId = mod.getId();
        try {
            JSONArray categories = new JSONArray();
            JSONArray nodes = new JSONArray();
            String defaultCategory;
            if (ModIds.ARMOR_HUD.equals(modId)) {
                categories.put(configCategory(context, "display", R.string.mod_config_category_appearance));
                categories.put(configCategory(context, "data", R.string.mod_config_category_behavior));
                defaultCategory = "display";
                nodes.put(configNode(context, mod, CFG_ARMOR_HUD_SHOW_TARGET, "display"));
                nodes.put(configNode(context, mod, CFG_ARMOR_HUD_SHOW_ENCHANTS, "display"));
                nodes.put(configNode(context, mod, CFG_ARMOR_HUD_STACKED, "display"));
                nodes.put(configNode(context, mod, CFG_ARMOR_HUD_REFRESH_MS, "data"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SIZE, "display"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_OPACITY, "display"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_LOCK, "data"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "data"));
            } else if (ModIds.CRYSTAL_OPTIMIZER.equals(modId)) {
                categories.put(configCategory(context, "safety", R.string.mod_config_category_behavior));
                categories.put(configCategory(context, "tuning", R.string.mod_config_category_motion));
                defaultCategory = "safety";
                nodes.put(configNode(context, mod, CFG_CRYSTAL_MANUAL_ASSIST, "safety"));
                nodes.put(configNode(context, mod, CFG_CRYSTAL_MIN_SELF_HP, "safety"));
                nodes.put(configNode(context, mod, CFG_CRYSTAL_MAX_RANGE, "tuning"));
                nodes.put(configNode(context, mod, CFG_CRYSTAL_PLACEMENT_DELAY_MS, "tuning"));
                nodes.put(configNode(context, mod, CFG_CRYSTAL_KEYBIND, "tuning"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SIZE, "tuning"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_OPACITY, "tuning"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_LOCK, "safety"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "safety"));
            } else if (ModIds.HIT_TIMING.equals(modId)) {
                categories.put(configCategory(context, "timing", R.string.mod_config_category_motion));
                categories.put(configCategory(context, "display", R.string.mod_config_category_appearance));
                defaultCategory = "timing";
                nodes.put(configNode(context, mod, CFG_HIT_TIMING_COOLDOWN_MS, "timing"));
                nodes.put(configNode(context, mod, CFG_HIT_TIMING_SHOW_COMBO, "display"));
                nodes.put(configNode(context, mod, CFG_HIT_TIMING_SHOW_BAR, "display"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_LOCK, "display"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "display"));
            } else if (ModIds.HITBOX.equals(modId)) {
                categories.put(configCategory(context, "entities", R.string.mod_config_category_slots));
                categories.put(configCategory(context, "guides", R.string.mod_config_category_appearance));
                defaultCategory = "entities";
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_PLAYERS, "entities"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_MOBS, "entities"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_ITEMS, "entities"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_PROJECTILES, "entities"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_THROWN_ITEMS, "entities"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_LOOK_LINE, "guides"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_CRIT_LINE, "guides"));
                nodes.put(configNode(context, mod, CFG_HITBOX_SHOW_COMBO_BOX, "guides"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "guides"));
            } else {
                categories.put(configCategory(context, "aim", R.string.mod_config_category_motion));
                categories.put(configCategory(context, "feedback", R.string.mod_config_category_button));
                defaultCategory = "aim";
                nodes.put(configNode(context, mod, CFG_HITREG_SENSITIVITY, "aim"));
                nodes.put(configNode(context, mod, CFG_HITREG_SMOOTHING, "aim"));
                nodes.put(configNode(context, mod, CFG_HITREG_PREDICTION, "aim"));
                nodes.put(configNode(context, mod, CFG_HITREG_HAPTIC, "feedback"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_LOCK, "feedback"));
                nodes.put(configNode(context, mod, CFG_OVERLAY_SHOW_EVERYWHERE, "feedback"));
            }
            int noteRes = scopeNoteRes(modId);
            if (noteRes != 0) {
                nodes.put(scopeNoteNode(context, mod, noteRes, defaultCategory));
            }
            return RuntimeConfigSchema.parse(new JSONObject().put("version", 2)
                    .put("default_category", defaultCategory)
                    .put("categories", categories).put("nodes", nodes).toString());
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to build combat config schema", e);
        }
    }

    private static JSONObject configCategory(Context context, String id, int titleRes) throws JSONException {
        return new JSONObject().put("id", id).put("title", context.getString(titleRes));
    }

    private static JSONObject configNode(Context context, UnifiedMod mod, String key, String category) throws JSONException {
        UnifiedMod.ConfigEntry config = mod.findConfigEntry(key);
        JSONObject node = new JSONObject().put("id", key).put("key", key)
                .put("category", category).put("title", config.displayName)
                .put("type", schemaTypeFor(config.type))
                .put("default_value", config.defaultValue)
                .put("min_value", config.minValue).put("max_value", config.maxValue);
        int descRes = configDescriptionRes(key);
        if (descRes != 0) {
            node.put("description", context.getString(descRes));
        }
        if (!config.dependsOn.isEmpty()) {
            node.put("enabled_when", new JSONArray().put(new JSONObject()
                    .put("key", config.dependsOn).put("op", "truthy")));
        }
        return node;
    }

    /**
     * The `_desc` string for a config key, or 0 when there is none.
     *
     * <p>Resolved through an explicit table rather than a name lookup so a renamed key cannot
     * silently start matching an unrelated string.
     */
    static int configDescriptionRes(String key) {
        switch (key) {
            case CFG_HITREG_SENSITIVITY: return R.string.mod_config_hitreg_sensitivity_desc;
            case CFG_HITREG_SMOOTHING: return R.string.mod_config_hitreg_smoothing_desc;
            case CFG_HITREG_PREDICTION: return R.string.mod_config_hitreg_prediction_desc;
            case CFG_HITREG_HAPTIC: return R.string.mod_config_hitreg_haptic_desc;
            case CFG_CRYSTAL_PLACEMENT_DELAY_MS: return R.string.mod_config_crystal_placement_delay_ms_desc;
            case CFG_CRYSTAL_MANUAL_ASSIST: return R.string.mod_config_crystal_manual_assist_desc;
            case CFG_CRYSTAL_MIN_SELF_HP: return R.string.mod_config_crystal_min_self_hp_desc;
            case CFG_CRYSTAL_MAX_RANGE: return R.string.mod_config_crystal_max_range_desc;
            case CFG_CRYSTAL_KEYBIND: return R.string.mod_config_crystal_keybind_desc;
            case CFG_HIT_TIMING_COOLDOWN_MS: return R.string.mod_config_hit_timing_cooldown_ms_desc;
            case CFG_HIT_TIMING_SHOW_COMBO: return R.string.mod_config_hit_timing_show_combo_desc;
            case CFG_HIT_TIMING_SHOW_BAR: return R.string.mod_config_hit_timing_show_bar_desc;
            case CFG_HITBOX_SHOW_PLAYERS: return R.string.mod_config_hitbox_show_players_desc;
            case CFG_HITBOX_SHOW_MOBS: return R.string.mod_config_hitbox_show_mobs_desc;
            case CFG_HITBOX_SHOW_ITEMS: return R.string.mod_config_hitbox_show_items_desc;
            case CFG_HITBOX_SHOW_PROJECTILES: return R.string.mod_config_hitbox_show_projectiles_desc;
            case CFG_HITBOX_SHOW_THROWN_ITEMS: return R.string.mod_config_hitbox_show_thrown_items_desc;
            case CFG_HITBOX_SHOW_LOOK_LINE: return R.string.mod_config_hitbox_show_look_line_desc;
            case CFG_HITBOX_SHOW_CRIT_LINE: return R.string.mod_config_hitbox_show_crit_line_desc;
            case CFG_HITBOX_SHOW_COMBO_BOX: return R.string.mod_config_hitbox_show_combo_box_desc;
            default: return 0;
        }
    }

    /**
     * An informational node carrying the module's scope note, or null when it has none.
     *
     * <p>These modules are named after an outcome ("Hit Registration") but deliberately stop
     * short of it — the server decides whether a hit lands, and the box feed may not exist yet.
     * Saying so in the dialog is what stops the honest limit reading as a broken feature.
     */
    private static JSONObject scopeNoteNode(Context context, UnifiedMod mod,
                                            int noteRes, String category) throws JSONException {
        return new JSONObject()
                .put("id", mod.getId() + "_scope_note")
                .put("type", "info")
                .put("category", category)
                .put("title", context.getString(R.string.mod_config_scope_note_title))
                .put("description", context.getString(noteRes));
    }

    private static int scopeNoteRes(String modId) {
        if (ModIds.HIT_REGISTRATION.equals(modId)) return R.string.hitreg_scope_note;
        if (ModIds.CRYSTAL_OPTIMIZER.equals(modId)) return R.string.crystal_optimizer_fairness_note;
        if (ModIds.HIT_TIMING.equals(modId)) return R.string.hit_timing_scope_note;
        if (ModIds.HITBOX.equals(modId)) return R.string.hitbox_scope_note;
        if (ModIds.ARMOR_HUD.equals(modId)) return R.string.armor_hud_no_data;
        return 0;
    }

    /**
     * Maps a config entry's type to the schema node type the dialog renders.
     *
     * This must be the real type, not a toggle/slider default: the dialog builds its control
     * from the node type alone, so a KEYBIND node declared as a slider would show a 0..100
     * range and write a small integer over the stored key code, and a RADIO node would lose
     * its options. Anything the schema cannot express (the legacy colour picker) still falls
     * back to a slider rather than silently dropping the setting.
     */
    static String schemaTypeFor(UnifiedMod.ConfigType type) {
        switch (type) {
            case TOGGLE:
                return "toggle";
            case SLIDER_INT:
                return "slider_int";
            case SLIDER_FLOAT:
                return "slider_float";
            case RADIO:
                return "choice";
            case KEYBIND:
                return "keybind";
            case TEXT:
                return "text";
            case BUTTON:
                return "button";
            case COLOR:
            default:
                return "slider_int";
        }
    }

    private static List<UnifiedMod.ConfigEntry> createConfigs(Context context,
                                                              InbuiltModManager manager,
                                                              String modId) {
        List<UnifiedMod.ConfigEntry> configs = new ArrayList<>();
        if (ModIds.POJAV_CONTROLS.equals(modId) || ModIds.MORE_BUTTONS.equals(modId)) return configs;
        if (!ModIds.CHICK_PET.equals(modId)) {
            if (!ModIds.HOTBAR_SLOT.equals(modId)) {
                configs.add(config(CFG_OVERLAY_SIZE,
                        context.getString(R.string.mod_config_overlay_button_size_dp),
                        UnifiedMod.ConfigType.SLIDER_INT,
                        "56", "20", "100",
                        String.valueOf(manager.getOverlayButtonSize(modId))));
                configs.add(config(CFG_OVERLAY_OPACITY,
                        context.getString(R.string.mod_config_overlay_opacity_percent),
                        UnifiedMod.ConfigType.SLIDER_INT,
                        "100", "0", "100",
                        String.valueOf(manager.getOverlayOpacity(modId))));
            }
            configs.add(config(CFG_OVERLAY_LOCK,
                    context.getString(R.string.overlay_button_lock),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isOverlayLocked(modId))));
            configs.add(config(CFG_OVERLAY_SHOW_EVERYWHERE,
                    context.getString(R.string.mod_config_overlay_show_everywhere),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isOverlayShowEverywhere(modId))));
        }

        if (ModIds.HOTBAR_SLOT.equals(modId)) {
            configs.add(config(CFG_HOTBAR_ITEM_ICONS,
                    context.getString(R.string.mod_config_hotbar_item_icons),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isHotbarItemIconsEnabled())));
            for (int slot = 1; slot <= 9; slot++) {
                String enabledKey = hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_ENABLED);
                String overlayKey = ModIds.HOTBAR_SLOT + ":" + slot;
                configs.add(config(enabledKey,
                        context.getString(R.string.mod_config_hotbar_slot_enabled, slot),
                        UnifiedMod.ConfigType.TOGGLE,
                        "true", "", "",
                        String.valueOf(manager.isHotbarSlotEnabled(slot))));
                configs.add(config(hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_SIZE),
                        context.getString(R.string.mod_config_hotbar_slot_size, slot),
                        UnifiedMod.ConfigType.SLIDER_INT,
                        "56", "20", "100",
                        String.valueOf(manager.getOverlayButtonSize(overlayKey)), enabledKey));
                configs.add(config(hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_OPACITY),
                        context.getString(R.string.mod_config_hotbar_slot_opacity, slot),
                        UnifiedMod.ConfigType.SLIDER_INT,
                        "100", "0", "100",
                        String.valueOf(manager.getOverlayOpacity(overlayKey)), enabledKey));
            }
        }

        if (ModIds.AUTO_SPRINT.equals(modId)) {
            configs.add(config(CFG_AUTO_SPRINT_KEYBIND,
                    context.getString(R.string.mod_config_auto_sprint_keybind),
                    UnifiedMod.ConfigType.KEYBIND,
                    "", "", "",
                    String.valueOf(manager.getAutoSprintKeybind())));
        } else if (ModIds.VIRTUAL_CURSOR.equals(modId)) {
            configs.add(config(CFG_CURSOR_SENSITIVITY,
                    context.getString(R.string.mod_config_cursor_sensitivity_percent),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "120", "10", "200",
                    String.valueOf(manager.getCursorSensitivity())));
        } else if (ModIds.ZOOM.equals(modId)) {
            configs.add(config(CFG_ZOOM_LEVEL,
                    context.getString(R.string.mod_config_zoom_level_percent),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "10", "-20", "100",
                    String.valueOf(manager.getZoomLevel())));
            configs.add(config(CFG_ZOOM_TRANSITION,
                    context.getString(R.string.mod_config_zoom_transition),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "150", "0", "1000",
                    String.valueOf(manager.getZoomTransitionDuration())));
            configs.add(config(CFG_ZOOM_KEYBIND,
                    context.getString(R.string.mod_config_zoom_keybind),
                    UnifiedMod.ConfigType.KEYBIND,
                    "", "", "",
                    String.valueOf(manager.getZoomKeybind())));
        } else if (ModIds.GYRO.equals(modId)) {
            configs.add(config(CFG_GYRO_SENSITIVITY_X,
                    context.getString(R.string.mod_config_gyro_sensitivity_x),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "300",
                    String.valueOf(manager.getGyroSensitivityX())));
            configs.add(config(CFG_GYRO_SENSITIVITY_Y,
                    context.getString(R.string.mod_config_gyro_sensitivity_y),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "300",
                    String.valueOf(manager.getGyroSensitivityY())));
            configs.add(config(CFG_GYRO_INVERT_X,
                    context.getString(R.string.mod_config_gyro_invert_x),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isGyroInvertX())));
            configs.add(config(CFG_GYRO_INVERT_Y,
                    context.getString(R.string.mod_config_gyro_invert_y),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isGyroInvertY())));
            configs.add(config(CFG_GYRO_DEADZONE,
                    context.getString(R.string.mod_config_gyro_deadzone),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "5", "0", "50",
                    String.valueOf(manager.getGyroDeadzone())));
        } else if (ModIds.AIM_SETTINGS.equals(modId)) {
            configs.add(config(CFG_AIM_SENSITIVITY,
                    context.getString(R.string.mod_config_aim_sensitivity),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "300",
                    String.valueOf(manager.getAimSensitivity())));
            configs.add(config(CFG_AIM_SMOOTHING,
                    context.getString(R.string.mod_config_aim_smoothing),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "40", "0", "95",
                    String.valueOf(manager.getAimSmoothing())));
            configs.add(config(CFG_AIM_CROSSHAIR,
                    context.getString(R.string.mod_config_aim_crosshair),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimCrosshairEnabled())));
            configs.add(config(CFG_AIM_CROSSHAIR_STYLE,
                    context.getString(R.string.aim_crosshair_style),
                    UnifiedMod.ConfigType.RADIO,
                    "1",
                    context.getString(R.string.aim_crosshair_style_dot) + ","
                            + context.getString(R.string.aim_crosshair_style_cross) + ","
                            + context.getString(R.string.aim_crosshair_style_circle),
                    "",
                    String.valueOf(manager.getAimCrosshairStyle())));
            configs.add(config(CFG_AIM_CROSSHAIR_COLOR,
                    context.getString(R.string.aim_crosshair_color),
                    UnifiedMod.ConfigType.COLOR,
                    "#FF3DDC84", "", "",
                    colorToHex(manager.getAimCrosshairColor())));
            configs.add(config(CFG_AIM_FLASH,
                    context.getString(R.string.mod_config_aim_flash),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isAimFlashEnabled())));
        } else if (ModIds.ARMOR_HUD.equals(modId)) {
            configs.add(config(CFG_ARMOR_HUD_SHOW_TARGET,
                    context.getString(R.string.mod_config_armor_hud_show_target),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isArmorHudShowTarget())));
            configs.add(config(CFG_ARMOR_HUD_SHOW_ENCHANTS,
                    context.getString(R.string.mod_config_armor_hud_show_enchants),
                    UnifiedMod.ConfigType.TOGGLE,
                    "false", "", "",
                    String.valueOf(manager.isArmorHudShowEnchants())));
            configs.add(config(CFG_ARMOR_HUD_STACKED,
                    context.getString(R.string.mod_config_armor_hud_stacked),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isArmorHudStacked())));
            configs.add(config(CFG_ARMOR_HUD_REFRESH_MS,
                    context.getString(R.string.mod_config_armor_hud_refresh_ms),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "50", "1000",
                    String.valueOf(manager.getArmorHudRefreshMs())));
        } else if (ModIds.CRYSTAL_OPTIMIZER.equals(modId)) {
            // Manual assist is listed first and defaults on: it is the variant that does not
            // automate a player action, so it must be the one a fresh profile lands on.
            configs.add(config(CFG_CRYSTAL_MANUAL_ASSIST,
                    context.getString(R.string.mod_config_crystal_manual_assist),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isCrystalManualAssist())));
            configs.add(config(CFG_CRYSTAL_MIN_SELF_HP,
                    context.getString(R.string.mod_config_crystal_min_self_hp),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "14", "1", "20",
                    String.valueOf(manager.getCrystalMinSelfHp())));
            configs.add(config(CFG_CRYSTAL_MAX_RANGE,
                    context.getString(R.string.mod_config_crystal_max_range),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "4", "1", "8",
                    String.valueOf(manager.getCrystalMaxRange())));
            configs.add(config(CFG_CRYSTAL_PLACEMENT_DELAY_MS,
                    context.getString(R.string.mod_config_crystal_placement_delay_ms),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "50", "0", "1000",
                    String.valueOf(manager.getCrystalPlacementDelayMs())));
            configs.add(config(CFG_CRYSTAL_KEYBIND,
                    context.getString(R.string.mod_config_crystal_keybind),
                    UnifiedMod.ConfigType.KEYBIND,
                    "", "", "",
                    String.valueOf(manager.getCrystalKeybind())));
        } else if (ModIds.HIT_REGISTRATION.equals(modId)) {
            configs.add(config(CFG_HITREG_SENSITIVITY,
                    context.getString(R.string.mod_config_hitreg_sensitivity),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "100", "10", "300",
                    String.valueOf(manager.getHitRegSensitivity())));
            configs.add(config(CFG_HITREG_SMOOTHING,
                    context.getString(R.string.mod_config_hitreg_smoothing),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "25", "0", "95",
                    String.valueOf(manager.getHitRegSmoothing())));
            configs.add(config(CFG_HITREG_PREDICTION,
                    context.getString(R.string.mod_config_hitreg_prediction),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    "35", "0", "100",
                    String.valueOf(manager.getHitRegPrediction())));
            configs.add(config(CFG_HITREG_HAPTIC,
                    context.getString(R.string.mod_config_hitreg_haptic),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitRegHapticEnabled())));
        } else if (ModIds.HIT_TIMING.equals(modId)) {
            configs.add(config(CFG_HIT_TIMING_COOLDOWN_MS,
                    context.getString(R.string.mod_config_hit_timing_cooldown_ms),
                    UnifiedMod.ConfigType.SLIDER_INT,
                    String.valueOf(HitTimingSolver.DEFAULT_COOLDOWN_MS), "50", "2000",
                    String.valueOf(manager.getHitTimingCooldownMs())));
            configs.add(config(CFG_HIT_TIMING_SHOW_COMBO,
                    context.getString(R.string.mod_config_hit_timing_show_combo),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitTimingShowCombo())));
            configs.add(config(CFG_HIT_TIMING_SHOW_BAR,
                    context.getString(R.string.mod_config_hit_timing_show_bar),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitTimingShowTimingBar())));
        } else if (ModIds.HITBOX.equals(modId)) {
            configs.add(config(CFG_HITBOX_SHOW_PLAYERS,
                    context.getString(R.string.mod_config_hitbox_show_players),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowPlayers())));
            configs.add(config(CFG_HITBOX_SHOW_MOBS,
                    context.getString(R.string.mod_config_hitbox_show_mobs),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowMobs())));
            configs.add(config(CFG_HITBOX_SHOW_ITEMS,
                    context.getString(R.string.mod_config_hitbox_show_items),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowItems())));
            configs.add(config(CFG_HITBOX_SHOW_PROJECTILES,
                    context.getString(R.string.mod_config_hitbox_show_projectiles),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowProjectiles())));
            configs.add(config(CFG_HITBOX_SHOW_THROWN_ITEMS,
                    context.getString(R.string.mod_config_hitbox_show_thrown_items),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowThrownItems())));
            configs.add(config(CFG_HITBOX_SHOW_LOOK_LINE,
                    context.getString(R.string.mod_config_hitbox_show_look_line),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowLookLine())));
            configs.add(config(CFG_HITBOX_SHOW_CRIT_LINE,
                    context.getString(R.string.mod_config_hitbox_show_crit_line),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowCritLine())));
            configs.add(config(CFG_HITBOX_SHOW_COMBO_BOX,
                    context.getString(R.string.mod_config_hitbox_show_combo_box),
                    UnifiedMod.ConfigType.TOGGLE,
                    "true", "", "",
                    String.valueOf(manager.isHitboxShowComboBox())));
        }
        return configs;
    }

    private static UnifiedMod.ConfigEntry config(String key, String displayName,
                                                 UnifiedMod.ConfigType type,
                                                 String defaultValue, String minValue,
                                                 String maxValue, String currentValue) {
        return config(key, displayName, type, defaultValue, minValue, maxValue, currentValue, "");
    }

    private static UnifiedMod.ConfigEntry config(String key, String displayName,
                                                 UnifiedMod.ConfigType type,
                                                 String defaultValue, String minValue,
                                                 String maxValue, String currentValue,
                                                 String dependsOn) {
        return new UnifiedMod.ConfigEntry(
                key, displayName, type, defaultValue, minValue, maxValue,
                currentValue, dependsOn
        );
    }

    private static String hotbarSlotConfigKey(int slot, String setting) {
        return CFG_HOTBAR_SLOT_PREFIX + slot + "_" + setting;
    }

    private static void setEnabled(InbuiltModManager manager, UnifiedMod mod, boolean enabled) {
        manager.setInbuiltModEnabled(mod.getId(), enabled);
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.handleModToggle(mod.getId(), enabled);
        }
    }

    private static void setConfig(InbuiltModManager manager, UnifiedMod mod, UnifiedMod.ConfigEntry config,
                                  String value) {
        if (ModIds.HOTBAR_SLOT.equals(mod.getId()) && setHotbarSlotConfig(manager, config.key, value)) return;
        switch (config.key) {
            case CFG_OVERLAY_SIZE:
                manager.setOverlayButtonSize(mod.getId(), parseInt(value, manager.getOverlayButtonSize(mod.getId())));
                break;
            case CFG_OVERLAY_OPACITY:
                manager.setOverlayOpacity(mod.getId(), parseInt(value, manager.getOverlayOpacity(mod.getId())));
                break;
            case CFG_OVERLAY_LOCK:
                manager.setOverlayLocked(mod.getId(), parseBoolean(value));
                break;
            case CFG_OVERLAY_SHOW_EVERYWHERE:
                manager.setOverlayShowEverywhere(mod.getId(), parseBoolean(value));
                break;
            case CFG_AUTO_SPRINT_KEYBIND:
                manager.setAutoSprintKeybind(parseInt(value, manager.getAutoSprintKeybind()));
                break;
            case CFG_CURSOR_SENSITIVITY:
                manager.setCursorSensitivity(parseInt(value, manager.getCursorSensitivity()));
                break;
            case CFG_ZOOM_LEVEL:
                manager.setZoomLevel(parseInt(value, manager.getZoomLevel()));
                break;
            case CFG_ZOOM_TRANSITION:
                manager.setZoomTransitionDuration(parseInt(value, manager.getZoomTransitionDuration()));
                break;
            case CFG_ZOOM_KEYBIND:
                manager.setZoomKeybind(parseInt(value, manager.getZoomKeybind()));
                break;
            case CFG_GYRO_SENSITIVITY_X:
                manager.setGyroSensitivityX(parseInt(value, manager.getGyroSensitivityX()));
                break;
            case CFG_GYRO_SENSITIVITY_Y:
                manager.setGyroSensitivityY(parseInt(value, manager.getGyroSensitivityY()));
                break;
            case CFG_GYRO_INVERT_X:
                manager.setGyroInvertX(parseBoolean(value));
                break;
            case CFG_GYRO_INVERT_Y:
                manager.setGyroInvertY(parseBoolean(value));
                break;
            case CFG_GYRO_DEADZONE:
                manager.setGyroDeadzone(parseInt(value, manager.getGyroDeadzone()));
                break;
            case CFG_HOTBAR_ITEM_ICONS:
                manager.setHotbarItemIconsEnabled(parseBoolean(value));
                break;
            case CFG_AIM_SMOOTHING:
                manager.setAimSmoothing(parseInt(value, manager.getAimSmoothing()));
                break;
            case CFG_AIM_SENSITIVITY:
                manager.setAimSensitivity(parseInt(value, manager.getAimSensitivity()));
                break;
            case CFG_AIM_CROSSHAIR:
                manager.setAimCrosshairEnabled(parseBoolean(value));
                break;
            case CFG_AIM_CROSSHAIR_STYLE:
                manager.setAimCrosshairStyle(parseInt(value, manager.getAimCrosshairStyle()));
                break;
            case CFG_AIM_CROSSHAIR_COLOR: {
                try {
                    manager.setAimCrosshairColor(Color.parseColor(value));
                } catch (Exception ignored) {
                }
                break;
            }
            case CFG_AIM_FLASH:
                manager.setAimFlashEnabled(parseBoolean(value));
                break;
            case CFG_ARMOR_HUD_SHOW_TARGET:
                manager.setArmorHudShowTarget(parseBoolean(value));
                break;
            case CFG_ARMOR_HUD_SHOW_ENCHANTS:
                manager.setArmorHudShowEnchants(parseBoolean(value));
                break;
            case CFG_ARMOR_HUD_STACKED:
                manager.setArmorHudStacked(parseBoolean(value));
                break;
            case CFG_ARMOR_HUD_REFRESH_MS:
                manager.setArmorHudRefreshMs(parseInt(value, manager.getArmorHudRefreshMs()));
                break;
            case CFG_CRYSTAL_MANUAL_ASSIST:
                manager.setCrystalManualAssist(parseBoolean(value));
                break;
            case CFG_CRYSTAL_MIN_SELF_HP:
                manager.setCrystalMinSelfHp(parseInt(value, manager.getCrystalMinSelfHp()));
                break;
            case CFG_CRYSTAL_MAX_RANGE:
                manager.setCrystalMaxRange(parseInt(value, manager.getCrystalMaxRange()));
                break;
            case CFG_CRYSTAL_PLACEMENT_DELAY_MS:
                manager.setCrystalPlacementDelayMs(parseInt(value, manager.getCrystalPlacementDelayMs()));
                break;
            case CFG_CRYSTAL_KEYBIND:
                manager.setCrystalKeybind(parseInt(value, manager.getCrystalKeybind()));
                break;
            case CFG_HITREG_SENSITIVITY:
                manager.setHitRegSensitivity(parseInt(value, manager.getHitRegSensitivity()));
                break;
            case CFG_HITREG_SMOOTHING:
                manager.setHitRegSmoothing(parseInt(value, manager.getHitRegSmoothing()));
                break;
            case CFG_HITREG_PREDICTION:
                manager.setHitRegPrediction(parseInt(value, manager.getHitRegPrediction()));
                break;
            case CFG_HITREG_HAPTIC:
                manager.setHitRegHapticEnabled(parseBoolean(value));
                break;
            case CFG_HIT_TIMING_COOLDOWN_MS:
                manager.setHitTimingCooldownMs(parseInt(value, manager.getHitTimingCooldownMs()));
                break;
            case CFG_HIT_TIMING_SHOW_COMBO:
                manager.setHitTimingShowCombo(parseBoolean(value));
                break;
            case CFG_HIT_TIMING_SHOW_BAR:
                manager.setHitTimingShowTimingBar(parseBoolean(value));
                break;
            case CFG_HITBOX_SHOW_PLAYERS:
                manager.setHitboxShowPlayers(parseBoolean(value));
                break;
            case CFG_HITBOX_SHOW_MOBS:
                manager.setHitboxShowMobs(parseBoolean(value));
                break;
            case CFG_HITBOX_SHOW_ITEMS:
                manager.setHitboxShowItems(parseBoolean(value));
                break;
            case CFG_HITBOX_SHOW_PROJECTILES:
                manager.setHitboxShowProjectiles(parseBoolean(value));
                org.chimeramc.client.core.mods.inbuilt.overlay.HitboxMod.onConfigChanged(manager);
                break;
            case CFG_HITBOX_SHOW_THROWN_ITEMS:
                manager.setHitboxShowThrownItems(parseBoolean(value));
                org.chimeramc.client.core.mods.inbuilt.overlay.HitboxMod.onConfigChanged(manager);
                break;
            case CFG_HITBOX_SHOW_LOOK_LINE:
                manager.setHitboxShowLookLine(parseBoolean(value));
                break;
            case CFG_HITBOX_SHOW_CRIT_LINE:
                manager.setHitboxShowCritLine(parseBoolean(value));
                break;
            case CFG_HITBOX_SHOW_COMBO_BOX:
                manager.setHitboxShowComboBox(parseBoolean(value));
                break;
            default:
                break;
        }
        if (ModIds.AIM_SETTINGS.equals(mod.getId())) {
            org.chimeramc.client.core.mods.inbuilt.overlay.AimSettingsMod.onConfigChanged(manager);
        } else if (ModIds.ARMOR_HUD.equals(mod.getId())) {
            org.chimeramc.client.core.mods.inbuilt.overlay.ArmorHudMod.onConfigChanged(manager);
        } else if (ModIds.CRYSTAL_OPTIMIZER.equals(mod.getId())) {
            org.chimeramc.client.core.mods.inbuilt.overlay.CrystalOptimizerMod.onConfigChanged(manager);
        } else if (ModIds.HIT_REGISTRATION.equals(mod.getId())) {
            org.chimeramc.client.core.mods.inbuilt.overlay.HitRegistrationMod.onConfigChanged(manager);
        } else if (ModIds.HIT_TIMING.equals(mod.getId())) {
            org.chimeramc.client.core.mods.inbuilt.overlay.HitTimingMod.onConfigChanged(manager);
        } else if (ModIds.HITBOX.equals(mod.getId())) {
            org.chimeramc.client.core.mods.inbuilt.overlay.HitboxMod.onConfigChanged(manager);
        }
    }

    private static boolean setHotbarSlotConfig(InbuiltModManager manager, String key, String value) {
        for (int slot = 1; slot <= 9; slot++) {
            String overlayKey = ModIds.HOTBAR_SLOT + ":" + slot;
            if (hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_ENABLED).equals(key)) {
                manager.setHotbarSlotEnabled(slot, parseBoolean(value));
                return true;
            }
            if (hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_SIZE).equals(key)) {
                manager.setOverlayButtonSize(overlayKey, parseInt(value, manager.getOverlayButtonSize(overlayKey)));
                return true;
            }
            if (hotbarSlotConfigKey(slot, CFG_HOTBAR_SLOT_OPACITY).equals(key)) {
                manager.setOverlayOpacity(overlayKey, parseInt(value, manager.getOverlayOpacity(overlayKey)));
                return true;
            }
        }
        return false;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String colorToHex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }
}
