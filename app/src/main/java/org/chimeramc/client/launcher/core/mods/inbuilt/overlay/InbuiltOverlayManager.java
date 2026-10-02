package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.view.MotionEvent;

import org.chimeramc.client.core.mods.inbuilt.ExternalModBridge;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.manager.MoreButtonsManager;
import org.chimeramc.client.core.mods.inbuilt.model.MoreButtonConfig;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.levimc.launcher.core.mods.inbuilt.nativemod.PojavControlsMod;
import org.chimeramc.pojavcontrols.PojavControls;
import org.chimeramc.pojavcontrols.PojavControlsHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InbuiltOverlayManager {
    public interface HudEditorSelectionListener {
        void onHudEditorSelectionChanged(int currentSizeDp);
    }

    private static volatile InbuiltOverlayManager instance;
    private final Activity activity;
    private final List<BaseOverlayButton> overlays = new ArrayList<>();
    private final Map<String, Boolean> modActiveStates = new HashMap<>();
    private final Map<String, BaseOverlayButton> modOverlayMap = new HashMap<>();
    private final Map<String, ExternalButtonOverlay> externalButtonOverlayMap = new HashMap<>();
    private final Map<String, MoreButtonOverlay> moreButtonOverlayMap = new HashMap<>();
    private final Map<Integer, HotbarSlotOverlay> hotbarSlotOverlayMap = new HashMap<>();
    private boolean moreButtonsEditorOpen;
    private final Map<String, Integer> modPositionMap = new HashMap<>();
    private ChickPetOverlay chickPetOverlay;
    private ZoomOverlay zoomOverlay;
    private SnaplookOverlay snaplookOverlay;
    private GyroOverlay gyroOverlay;
    private FpsDisplayOverlay fpsDisplayOverlay;
    private CpsDisplayOverlay cpsDisplayOverlay;
    private AimSettingsOverlay aimSettingsOverlay;
    private ArmorHudOverlay armorHudOverlay;
    private CrystalOptimizerOverlay crystalOptimizerOverlay;
    private HitTimingOverlay hitTimingOverlay;
    private HitboxOverlay hitboxOverlay;
    private ReachIndicatorOverlay reachIndicatorOverlay;
    private TrajectoryPredictionOverlay trajectoryPredictionOverlay;
    private HitPredictionOverlay hitPredictionOverlay;
    private KillEffectsOverlay killEffectsOverlay;
    private VoiceChatOverlay voiceChatOverlay;
    private VoiceNametagOverlay voiceNametagOverlay;
    private ModMenuButton modMenuButton;
    private HudOverlay hudOverlay;
    private BaseOverlayButton selectedHudEditorOverlay;
    private String selectedDisplayModId;
    private HudEditorSelectionListener hudEditorSelectionListener;
    private boolean hudEditorMode = false;
    private int baseY = 150;
    private static final int SPACING = 70;
    private static final int START_X = 50;
    private long lastVisibilityStateHash = Long.MIN_VALUE;
    /**
     * Latches the first time the preloader reports the game HUD screen is up.
     *
     * Until that happens the native hook may simply not be installed (its signature rules did
     * not resolve), and an unproven "false" must not be read as "no game". See
     * {@link OverlayVisibility} for why that distinction keeps a mod's UI from disappearing.
     */
    private boolean gameWorldSeen = false;

    /** Result code for the microphone request issued when proximity voice starts. */
    private static final int REQUEST_VOICE_MIC = 0x7C01;

    public InbuiltOverlayManager(Activity activity) {
        this.activity = activity;
        instance = this;
    }

    public static InbuiltOverlayManager getInstance() {
        return instance;
    }

    public void showEnabledOverlays() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        if (!manager.isModMenuEnabled()) return;

        // The in-game pack changer must be able to nudge the live session whether or not voice
        // chat happens to be running, so the reloader is installed here, not as a side effect of
        // starting the voice link. Without it a pack toggle writes the files but the loaded world
        // keeps its cached stack until the next load.
        org.chimeramc.client.core.content.InGamePackChanger.setReloader(
                org.chimeramc.client.preloader.PreloaderInput::reloadResourcePacks);

        if (hudOverlay == null) {
            hudOverlay = new HudOverlay(activity);
        }
        hudOverlay.show();

        int nextY = baseY;

        modActiveStates.put(ModIds.QUICK_DROP, false);
        modActiveStates.put(ModIds.CAMERA_PERSPECTIVE, false);
        modActiveStates.put(ModIds.TOGGLE_HUD, false);
        modActiveStates.put(ModIds.AUTO_SPRINT, false);
        modActiveStates.put(ModIds.CHICK_PET, false);
        modActiveStates.put(ModIds.ZOOM, false);
        modActiveStates.put(ModIds.FPS_DISPLAY, false);
        modActiveStates.put(ModIds.CPS_DISPLAY, false);
        modActiveStates.put(ModIds.SNAPLOOK, false);
        modActiveStates.put(ModIds.VIRTUAL_CURSOR, false);
        modActiveStates.put(ModIds.GYRO, false);
        modActiveStates.put(ModIds.POJAV_CONTROLS, false);
        modActiveStates.put(ModIds.MORE_BUTTONS, false);
        modActiveStates.put(ModIds.HOTBAR_SLOT, false);
        modActiveStates.put(ModIds.AIM_SETTINGS, false);
        modActiveStates.put(ModIds.ARMOR_HUD, false);
        modActiveStates.put(ModIds.CRYSTAL_OPTIMIZER, false);
        modActiveStates.put(ModIds.HIT_REGISTRATION, false);
        modActiveStates.put(ModIds.HIT_TIMING, false);
        modActiveStates.put(ModIds.HITBOX, false);
        modActiveStates.put(ModIds.REACH_INDICATOR, false);
        modActiveStates.put(ModIds.TRAJECTORY_PREDICTION, false);
        modActiveStates.put(ModIds.HIT_PREDICTION, false);
        modActiveStates.put(ModIds.KILL_EFFECTS, false);
        modActiveStates.put(ModIds.VOICE_CHAT, false);

        modPositionMap.put(ModIds.QUICK_DROP, nextY + SPACING);
        modPositionMap.put(ModIds.CAMERA_PERSPECTIVE, nextY + SPACING * 2);
        modPositionMap.put(ModIds.TOGGLE_HUD, nextY + SPACING * 3);
        modPositionMap.put(ModIds.AUTO_SPRINT, nextY + SPACING * 4);
        modPositionMap.put(ModIds.ZOOM, nextY + SPACING * 5);
        modPositionMap.put(ModIds.FPS_DISPLAY, nextY + SPACING * 6);
        modPositionMap.put(ModIds.CPS_DISPLAY, nextY + SPACING * 7);
        modPositionMap.put(ModIds.SNAPLOOK, nextY + SPACING * 8);
        modPositionMap.put(ModIds.VIRTUAL_CURSOR, nextY + SPACING * 9);
        modPositionMap.put(ModIds.GYRO, nextY + SPACING * 10);

        if (zoomOverlay == null) {
            zoomOverlay = new ZoomOverlay(activity);
            zoomOverlay.initializeForKeyboard();
        }

        if (snaplookOverlay == null) {
            snaplookOverlay = new SnaplookOverlay(activity);
            snaplookOverlay.initializeForKeyboard();
        }

        restorePersistedInbuiltModState(manager, ModIds.QUICK_DROP);
        restorePersistedInbuiltModState(manager, ModIds.CAMERA_PERSPECTIVE);
        restorePersistedInbuiltModState(manager, ModIds.TOGGLE_HUD);
        restorePersistedInbuiltModState(manager, ModIds.AUTO_SPRINT);
        restorePersistedInbuiltModState(manager, ModIds.CHICK_PET);
        restorePersistedInbuiltModState(manager, ModIds.ZOOM);
        restorePersistedInbuiltModState(manager, ModIds.FPS_DISPLAY);
        restorePersistedInbuiltModState(manager, ModIds.CPS_DISPLAY);
        restorePersistedInbuiltModState(manager, ModIds.SNAPLOOK);
        restorePersistedInbuiltModState(manager, ModIds.VIRTUAL_CURSOR);
        restorePersistedInbuiltModState(manager, ModIds.GYRO);
        restorePersistedInbuiltModState(manager, ModIds.POJAV_CONTROLS);
        restorePersistedInbuiltModState(manager, ModIds.MORE_BUTTONS);
        restorePersistedInbuiltModState(manager, ModIds.HOTBAR_SLOT);
        restorePersistedInbuiltModState(manager, ModIds.AIM_SETTINGS);
        restorePersistedInbuiltModState(manager, ModIds.ARMOR_HUD);
        restorePersistedInbuiltModState(manager, ModIds.CRYSTAL_OPTIMIZER);
        restorePersistedInbuiltModState(manager, ModIds.HIT_REGISTRATION);
        restorePersistedInbuiltModState(manager, ModIds.HIT_TIMING);
        restorePersistedInbuiltModState(manager, ModIds.HITBOX);
        restorePersistedInbuiltModState(manager, ModIds.REACH_INDICATOR);
        restorePersistedInbuiltModState(manager, ModIds.TRAJECTORY_PREDICTION);
        restorePersistedInbuiltModState(manager, ModIds.HIT_PREDICTION);
        restorePersistedInbuiltModState(manager, ModIds.KILL_EFFECTS);
        restorePersistedInbuiltModState(manager, ModIds.VOICE_CHAT);

        modMenuButton = new ModMenuButton(activity);
        modMenuButton.show(START_X, nextY);
        refreshExternalButtons();
        refreshRuntimeVisibility();
    }

    private void restorePersistedInbuiltModState(InbuiltModManager manager, String modId) {
        if (manager.resolveInbuiltModEnabled(modId, false)) {
            handleModToggle(modId, true);
        }
    }

    public void flashAimFeedback() {
        if (aimSettingsOverlay != null && AimSettingsMod.isFlashEnabled()) {
            aimSettingsOverlay.flash();
        }
    }

    public void handleModToggle(String modId, boolean enabled) {
        boolean wasEnabled = modActiveStates.getOrDefault(modId, false);
        modActiveStates.put(modId, enabled);
        
        if (enabled && !wasEnabled) {
            showModOverlay(modId);
        } else if (!enabled && wasEnabled) {
            hideModOverlay(modId);
        }
    }

    private void showModOverlay(String modId) {
        if (modOverlayMap.containsKey(modId)) {
            return;
        }

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int centerX = metrics.widthPixels / 2 - (int)(26 * metrics.density);
        int centerY = metrics.heightPixels / 2 - (int)(26 * metrics.density);

        int savedX = manager.getOverlayPositionX(modId, centerX);
        int savedY = manager.getOverlayPositionY(modId, centerY);

        switch (modId) {
            case ModIds.QUICK_DROP:
                QuickDropOverlay quickDrop = new QuickDropOverlay(activity);
                quickDrop.show(savedX, savedY);
                overlays.add(quickDrop);
                modOverlayMap.put(modId, quickDrop);
                break;
            case ModIds.CAMERA_PERSPECTIVE:
                CameraPerspectiveOverlay camera = new CameraPerspectiveOverlay(activity);
                camera.show(savedX, savedY);
                overlays.add(camera);
                modOverlayMap.put(modId, camera);
                break;
            case ModIds.TOGGLE_HUD:
                ToggleHudOverlay hud = new ToggleHudOverlay(activity);
                hud.show(savedX, savedY);
                overlays.add(hud);
                modOverlayMap.put(modId, hud);
                break;
            case ModIds.AUTO_SPRINT:
                AutoSprintOverlay sprint = new AutoSprintOverlay(activity);
                sprint.show(savedX, savedY);
                overlays.add(sprint);
                modOverlayMap.put(modId, sprint);
                break;
            case ModIds.CHICK_PET:
                if (chickPetOverlay == null) {
                    chickPetOverlay = new ChickPetOverlay(activity);
                    chickPetOverlay.show();
                }
                break;
            case ModIds.ZOOM:
                if (zoomOverlay == null) {
                    zoomOverlay = new ZoomOverlay(activity);
                }
                zoomOverlay.show(savedX, savedY);
                overlays.add(zoomOverlay);
                modOverlayMap.put(modId, zoomOverlay);
                break;
            case ModIds.FPS_DISPLAY:
                if (fpsDisplayOverlay == null) {
                    fpsDisplayOverlay = new FpsDisplayOverlay(activity);
                    fpsDisplayOverlay.show(savedX, savedY);
                }
                break;
            case ModIds.CPS_DISPLAY:
                if (cpsDisplayOverlay == null) {
                    cpsDisplayOverlay = new CpsDisplayOverlay(activity);
                    cpsDisplayOverlay.show(savedX, savedY);
                }
                break;
            case ModIds.SNAPLOOK:
                if (snaplookOverlay == null) {
                    snaplookOverlay = new SnaplookOverlay(activity);
                }
                snaplookOverlay.show(savedX, savedY);
                overlays.add(snaplookOverlay);
                modOverlayMap.put(modId, snaplookOverlay);
                break;
            case ModIds.VIRTUAL_CURSOR:
                VirtualCursorOverlay cursorOverlay = new VirtualCursorOverlay(activity);
                cursorOverlay.show(savedX, savedY);
                overlays.add(cursorOverlay);
                modOverlayMap.put(modId, cursorOverlay);
                break;
            case ModIds.GYRO:
                if (gyroOverlay == null) {
                    gyroOverlay = new GyroOverlay(activity);
                }
                gyroOverlay.show(savedX, savedY);
                overlays.add(gyroOverlay);
                modOverlayMap.put(modId, gyroOverlay);
                break;
            case ModIds.MORE_BUTTONS:
                refreshMoreButtons();
                break;
            case ModIds.HOTBAR_SLOT:
                refreshHotbarSlots();
                break;
            case ModIds.POJAV_CONTROLS:
                if (activity instanceof PojavControlsHost && PojavControlsMod.setEnabled(true)) {
                    PojavControls.setEnabled(activity, (PojavControlsHost) activity, true);
                }
                PojavControls.setLowLatencyMode(
                        org.chimeramc.client.settings.FeatureSettings.getInstance().isLowInputDelayEnabled());
                break;
            case ModIds.AIM_SETTINGS:
                if (aimSettingsOverlay == null) {
                    aimSettingsOverlay = new AimSettingsOverlay(activity);
                }
                aimSettingsOverlay.show();
                AimSettingsMod.setEnabled(true, InbuiltModManager.getInstance(activity));
                break;
            case ModIds.ARMOR_HUD:
                if (armorHudOverlay == null) {
                    armorHudOverlay = new ArmorHudOverlay(activity);
                }
                armorHudOverlay.show(savedX, savedY);
                ArmorHudMod.setEnabled(true, manager);
                break;
            case ModIds.CRYSTAL_OPTIMIZER:
                if (crystalOptimizerOverlay == null) {
                    crystalOptimizerOverlay = new CrystalOptimizerOverlay(activity);
                }
                crystalOptimizerOverlay.show();
                CrystalOptimizerMod.setEnabled(true, manager);
                break;
            case ModIds.HIT_REGISTRATION:
                HitRegistrationMod.setEnabled(true, manager);
                break;
            case ModIds.HIT_TIMING: {
                if (hitTimingOverlay == null) {
                    hitTimingOverlay = new HitTimingOverlay(activity);
                }
                // The indicator belongs at the top centre, clear of the crosshair and hotbar.
                int topX = metrics.widthPixels / 2 - (int) (48 * metrics.density);
                int topY = (int) (12 * metrics.density);
                hitTimingOverlay.show(
                        manager.getOverlayPositionX(ModIds.HIT_TIMING, topX),
                        manager.getOverlayPositionY(ModIds.HIT_TIMING, topY));
                HitTimingMod.setEnabled(true, manager);
                break;
            }
            case ModIds.HITBOX:
                if (hitboxOverlay == null) {
                    hitboxOverlay = new HitboxOverlay(activity);
                }
                hitboxOverlay.show();
                HitboxMod.setEnabled(true, manager);
                break;
            case ModIds.REACH_INDICATOR:
                if (reachIndicatorOverlay == null) {
                    reachIndicatorOverlay = new ReachIndicatorOverlay(activity);
                }
                reachIndicatorOverlay.show();
                ReachIndicatorMod.setEnabled(true, manager);
                break;
            case ModIds.TRAJECTORY_PREDICTION:
                if (trajectoryPredictionOverlay == null) {
                    trajectoryPredictionOverlay = new TrajectoryPredictionOverlay(activity);
                }
                trajectoryPredictionOverlay.show();
                TrajectoryPredictionMod.setEnabled(true, manager);
                break;
            case ModIds.HIT_PREDICTION:
                if (hitPredictionOverlay == null) {
                    hitPredictionOverlay = new HitPredictionOverlay(activity);
                }
                hitPredictionOverlay.show();
                HitPredictionMod.setEnabled(true, manager);
                break;
            case ModIds.KILL_EFFECTS:
                if (killEffectsOverlay == null) {
                    killEffectsOverlay = new KillEffectsOverlay(activity);
                }
                killEffectsOverlay.show();
                KillEffectsMod.setEnabled(true, manager);
                break;
            case ModIds.VOICE_CHAT: {
                if (voiceChatOverlay == null) {
                    voiceChatOverlay = new VoiceChatOverlay(activity);
                }
                // The mic indicator lives bottom-right, above the hotbar's right edge; a caller
                // that never dragged it keeps that home instead of the generic centre default.
                int voiceX = manager.getOverlayPositionX(ModIds.VOICE_CHAT,
                        metrics.widthPixels - (int) (44 * metrics.density));
                int voiceY = manager.getOverlayPositionY(ModIds.VOICE_CHAT,
                        metrics.heightPixels - (int) (120 * metrics.density));
                voiceChatOverlay.show(voiceX, voiceY);
                // In-world nametag icons are part of the voice feature: they only have anything to
                // show while the link is running, so they come and go with it.
                if (voiceNametagOverlay == null) {
                    voiceNametagOverlay = new VoiceNametagOverlay(activity);
                }
                voiceNametagOverlay.show();
                VoiceNametagMod.setEnabled(true, manager);
                startVoiceChat(manager);
                break;
            }
        }
    }

    private void hideModOverlay(String modId) {
        if (modId.equals(ModIds.ARMOR_HUD)) {
            if (armorHudOverlay != null) {
                armorHudOverlay.hide();
                armorHudOverlay = null;
            }
            ArmorHudMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.CRYSTAL_OPTIMIZER)) {
            if (crystalOptimizerOverlay != null) {
                crystalOptimizerOverlay.hide();
                crystalOptimizerOverlay = null;
            }
            CrystalOptimizerMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.HIT_REGISTRATION)) {
            HitRegistrationMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.HIT_TIMING)) {
            if (hitTimingOverlay != null) {
                hitTimingOverlay.hide();
                hitTimingOverlay = null;
            }
            HitTimingMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.HITBOX)) {
            if (hitboxOverlay != null) {
                hitboxOverlay.hide();
                hitboxOverlay = null;
            }
            HitboxMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.REACH_INDICATOR)) {
            if (reachIndicatorOverlay != null) {
                reachIndicatorOverlay.hide();
                reachIndicatorOverlay = null;
            }
            ReachIndicatorMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.TRAJECTORY_PREDICTION)) {
            if (trajectoryPredictionOverlay != null) {
                trajectoryPredictionOverlay.hide();
                trajectoryPredictionOverlay = null;
            }
            TrajectoryPredictionMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.HIT_PREDICTION)) {
            if (hitPredictionOverlay != null) {
                hitPredictionOverlay.hide();
                hitPredictionOverlay = null;
            }
            HitPredictionMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.KILL_EFFECTS)) {
            if (killEffectsOverlay != null) {
                killEffectsOverlay.hide();
                killEffectsOverlay = null;
            }
            KillEffectsMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.VOICE_CHAT)) {
            stopVoiceChat();
            if (voiceChatOverlay != null) {
                voiceChatOverlay.hide();
                voiceChatOverlay = null;
            }
            if (voiceNametagOverlay != null) {
                voiceNametagOverlay.hide();
                voiceNametagOverlay = null;
            }
            VoiceNametagMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.AIM_SETTINGS)) {
            if (aimSettingsOverlay != null) aimSettingsOverlay.hide();
            AimSettingsMod.setEnabled(false, null);
            return;
        }
        if (modId.equals(ModIds.HOTBAR_SLOT)) {
            hideHotbarSlots();
            return;
        }
        if (modId.equals(ModIds.MORE_BUTTONS)) {
            hideMoreButtons();
            return;
        }
        if (modId.equals(ModIds.POJAV_CONTROLS)) {
            PojavControls.setEnabled(activity,
                    activity instanceof PojavControlsHost ? (PojavControlsHost) activity : null,
                    false);
            PojavControlsMod.setEnabled(false);
            return;
        }
        if (modId.equals(ModIds.CHICK_PET)) {
            if (chickPetOverlay != null) {
                chickPetOverlay.hide();
                chickPetOverlay = null;
            }
            return;
        }
        
        if (modId.equals(ModIds.ZOOM)) {
            if (zoomOverlay != null) {
                if (zoomOverlay == selectedHudEditorOverlay) {
                    selectHudEditorOverlay(null);
                }
                zoomOverlay.hide();
                overlays.remove(zoomOverlay);
                modOverlayMap.remove(modId);
            }
            return;
        }

        if (modId.equals(ModIds.FPS_DISPLAY)) {
            if (fpsDisplayOverlay != null) {
                fpsDisplayOverlay.hide();
                fpsDisplayOverlay = null;
            }
            return;
        }

        if (modId.equals(ModIds.CPS_DISPLAY)) {
            if (cpsDisplayOverlay != null) {
                cpsDisplayOverlay.hide();
                cpsDisplayOverlay = null;
            }
            return;
        }

        if (modId.equals(ModIds.SNAPLOOK)) {
            if (snaplookOverlay != null) {
                if (snaplookOverlay == selectedHudEditorOverlay) {
                    selectHudEditorOverlay(null);
                }
                snaplookOverlay.hide();
                overlays.remove(snaplookOverlay);
                modOverlayMap.remove(modId);
            }
            return;
        }

        if (modId.equals(ModIds.GYRO)) {
            if (gyroOverlay != null) {
                if (gyroOverlay == selectedHudEditorOverlay) {
                    selectHudEditorOverlay(null);
                }
                gyroOverlay.hide();
                overlays.remove(gyroOverlay);
                modOverlayMap.remove(modId);
            }
            return;
        }
        
        BaseOverlayButton overlay = modOverlayMap.get(modId);
        if (overlay != null) {
            if (overlay == selectedHudEditorOverlay) {
                selectHudEditorOverlay(null);
            }
            overlay.hide();
            overlays.remove(overlay);
            modOverlayMap.remove(modId);
        }
    }

    public void refreshMoreButtons() {
        if (moreButtonsEditorOpen || !modActiveStates.getOrDefault(ModIds.MORE_BUTTONS, false)) return;

        MoreButtonsManager buttonsManager = MoreButtonsManager.getInstance(activity);
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        java.util.List<MoreButtonConfig> configs = buttonsManager.getButtons();
        java.util.Set<String> validIds = new java.util.HashSet<>();
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int centerX = metrics.widthPixels / 2 - (int)(28 * metrics.density);
        int centerY = metrics.heightPixels / 2 - (int)(28 * metrics.density);
        int visibleIndex = 0;

        for (MoreButtonConfig config : configs) {
            validIds.add(config.id);
            MoreButtonOverlay existing = moreButtonOverlayMap.get(config.id);
            if (!config.visible) {
                if (existing != null) removeMoreButtonOverlay(existing);
                continue;
            }

            if (existing != null) {
                existing.applyConfigurationChanges();
                continue;
            }

            int defaultX = centerX + (int)((visibleIndex % 4) * 64 * metrics.density);
            int defaultY = centerY + (int)((visibleIndex / 4) * 64 * metrics.density);
            int savedX = manager.getOverlayPositionX(config.overlayKey(), defaultX);
            int savedY = manager.getOverlayPositionY(config.overlayKey(), defaultY);
            MoreButtonOverlay overlay = new MoreButtonOverlay(activity, config);
            overlay.show(savedX, savedY);
            overlays.add(overlay);
            moreButtonOverlayMap.put(config.id, overlay);
            modOverlayMap.put(config.overlayKey(), overlay);
            visibleIndex++;
        }

        java.util.List<MoreButtonOverlay> stale = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, MoreButtonOverlay> entry : moreButtonOverlayMap.entrySet()) {
            if (!validIds.contains(entry.getKey())) stale.add(entry.getValue());
        }
        for (MoreButtonOverlay overlay : stale) removeMoreButtonOverlay(overlay);
    }

    public void setMoreButtonsEditorOpen(boolean open) {
        if (moreButtonsEditorOpen == open) return;
        moreButtonsEditorOpen = open;
        if (open) hideMoreButtons();
        else refreshMoreButtons();
    }

    private void hideMoreButtons() {
        java.util.List<MoreButtonOverlay> copy = new java.util.ArrayList<>(moreButtonOverlayMap.values());
        for (MoreButtonOverlay overlay : copy) removeMoreButtonOverlay(overlay);
    }

    private void removeMoreButtonOverlay(MoreButtonOverlay overlay) {
        if (overlay == null) return;
        if (overlay == selectedHudEditorOverlay) selectHudEditorOverlay(null);
        overlay.hide();
        overlays.remove(overlay);
        moreButtonOverlayMap.remove(overlay.getButtonId());
        modOverlayMap.remove(overlay.getOverlayConfigKey());
    }

    public void refreshHotbarSlots() {
        if (!modActiveStates.getOrDefault(ModIds.HOTBAR_SLOT, false)) return;
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int gap = (int)(4 * metrics.density);
        int enabledCount = 0;
        int total = 0;
        for (int slot = 1; slot <= 9; slot++) {
            if (!manager.isHotbarSlotEnabled(slot)) continue;
            total += getHotbarSlotButtonSizePx(manager, metrics, slot);
            enabledCount++;
        }
        if (enabledCount > 1) total += gap * (enabledCount - 1);
        int startX = Math.max(0, (metrics.widthPixels - total) / 2);
        int defaultY = Math.max(0, metrics.heightPixels - (int)(120 * metrics.density));
        int nextX = startX;
        for (int slot = 1; slot <= 9; slot++) {
            HotbarSlotOverlay existing = hotbarSlotOverlayMap.get(slot);
            if (!manager.isHotbarSlotEnabled(slot)) {
                removeHotbarSlotOverlay(existing);
                continue;
            }
            int button = getHotbarSlotButtonSizePx(manager, metrics, slot);
            if (existing != null) {
                existing.applyConfigurationChanges();
            } else {
                String key = ModIds.HOTBAR_SLOT + ":" + slot;
                int savedX = manager.getOverlayPositionX(key, nextX);
                int savedY = manager.getOverlayPositionY(key, defaultY);
                HotbarSlotOverlay overlay = new HotbarSlotOverlay(activity, slot);
                overlay.show(savedX, savedY);
                overlays.add(overlay);
                hotbarSlotOverlayMap.put(slot, overlay);
                modOverlayMap.put(key, overlay);
            }
            nextX += button + gap;
        }
    }

    private int getHotbarSlotButtonSizePx(InbuiltModManager manager,
                                           android.util.DisplayMetrics metrics, int slot) {
        String key = ModIds.HOTBAR_SLOT + ":" + slot;
        return (int)(manager.getOverlayButtonSize(key) * metrics.density);
    }

    private void removeHotbarSlotOverlay(HotbarSlotOverlay overlay) {
        if (overlay == null) return;
        if (overlay == selectedHudEditorOverlay) selectHudEditorOverlay(null);
        overlay.hide();
        overlays.remove(overlay);
        hotbarSlotOverlayMap.remove(overlay.getSlot());
        modOverlayMap.remove(ModIds.HOTBAR_SLOT + ":" + overlay.getSlot());
    }

    private void hideHotbarSlots() {
        java.util.List<HotbarSlotOverlay> copy = new java.util.ArrayList<>(hotbarSlotOverlayMap.values());
        for (HotbarSlotOverlay overlay : copy) removeHotbarSlotOverlay(overlay);
        hotbarSlotOverlayMap.clear();
    }

    public void handleExternalModuleToggle(String moduleId, boolean enabled) {
        if (enabled) {
            showExternalButtonsForModule(moduleId);
        } else {
            hideExternalButtonsForModule(moduleId);
        }
    }

    private void refreshExternalButtons() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        java.util.Set<String> enabledModules = new java.util.HashSet<>();
        int extCount = ExternalModBridge.getExternalModCount();
        for (int i = 0; i < extCount; i++) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(ExternalModBridge.getExternalModInfo(i));
                String moduleId = obj.optString("module_id", "");
                if (moduleId.isEmpty()) continue;

                boolean nativeEnabled = obj.optBoolean("enabled", false);
                boolean enabled = manager.resolveExternalModuleEnabled(moduleId, nativeEnabled);
                if (enabled != nativeEnabled) {
                    ExternalModBridge.toggleExternalMod(moduleId, enabled);
                }
                if (enabled) {
                    enabledModules.add(moduleId);
                }
            } catch (Exception ignored) {}
        }

        for (String moduleId : enabledModules) {
            showExternalButtonsForModule(moduleId);
        }
        java.util.List<ExternalButtonOverlay> stale = new java.util.ArrayList<>();
        for (ExternalButtonOverlay overlay : externalButtonOverlayMap.values()) {
            if (!enabledModules.contains(overlay.getModuleId())) {
                stale.add(overlay);
            }
        }
        for (ExternalButtonOverlay overlay : stale) {
            if (overlay == selectedHudEditorOverlay) {
                selectHudEditorOverlay(null);
            }
            overlay.hide();
            overlays.remove(overlay);
            externalButtonOverlayMap.remove(overlay.getButtonId());
            modOverlayMap.remove(overlay.getModId());
        }
    }

    private void showExternalButtonsForModule(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) return;

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int centerX = metrics.widthPixels / 2 - (int)(26 * metrics.density);
        int centerY = metrics.heightPixels / 2 - (int)(26 * metrics.density);

        int buttonCount = ExternalModBridge.getExternalButtonCount();
        for (int i = 0; i < buttonCount; i++) {
            ExternalModBridge.ExternalButton button = ExternalModBridge.getExternalButton(i);
            if (button == null || !moduleId.equals(button.moduleId)) continue;
            if (!button.defaultVisible || !button.moduleEnabled) continue;
            if (externalButtonOverlayMap.containsKey(button.buttonId)) continue;

            int savedX = manager.getOverlayPositionX(button.positionKey(), centerX);
            int savedY = manager.getOverlayPositionY(button.positionKey(), centerY);
            ExternalButtonOverlay overlay = new ExternalButtonOverlay(activity, button);
            overlay.show(savedX, savedY);
            overlays.add(overlay);
            externalButtonOverlayMap.put(button.buttonId, overlay);
            modOverlayMap.put(button.positionKey(), overlay);
        }
    }

    private void hideExternalButtonsForModule(String moduleId) {
        java.util.List<ExternalButtonOverlay> toHide = new java.util.ArrayList<>();
        for (ExternalButtonOverlay overlay : externalButtonOverlayMap.values()) {
            if (moduleId.equals(overlay.getModuleId())) {
                toHide.add(overlay);
            }
        }
        for (ExternalButtonOverlay overlay : toHide) {
            if (overlay == selectedHudEditorOverlay) {
                selectHudEditorOverlay(null);
            }
            overlay.hide();
            overlays.remove(overlay);
            externalButtonOverlayMap.remove(overlay.getButtonId());
            modOverlayMap.remove(overlay.getModId());
        }
    }

    public boolean isModActive(String modId) {
        return modActiveStates.getOrDefault(modId, false);
    }


    public void hideAllOverlays() {
        PojavControls.setEnabled(activity,
                activity instanceof PojavControlsHost ? (PojavControlsHost) activity : null,
                false);
        PojavControlsMod.setEnabled(false);
        selectHudEditorOverlay(null);
        for (BaseOverlayButton overlay : overlays) {
            overlay.hide();
        }
        overlays.clear();
        modOverlayMap.clear();
        externalButtonOverlayMap.clear();
        moreButtonOverlayMap.clear();
        hotbarSlotOverlayMap.clear();
        modActiveStates.clear();
        modPositionMap.clear();
        if (chickPetOverlay != null) {
            chickPetOverlay.hide();
            chickPetOverlay = null;
        }
        if (zoomOverlay != null) {
            zoomOverlay.hide();
            zoomOverlay = null;
        }
        if (fpsDisplayOverlay != null) {
            fpsDisplayOverlay.hide();
            fpsDisplayOverlay = null;
        }
        if (cpsDisplayOverlay != null) {
            cpsDisplayOverlay.hide();
            cpsDisplayOverlay = null;
        }
        if (snaplookOverlay != null) {
            snaplookOverlay.hide();
            snaplookOverlay = null;
        }
        if (gyroOverlay != null) {
            gyroOverlay.hide();
            gyroOverlay = null;
        }
        if (aimSettingsOverlay != null) {
            aimSettingsOverlay.hide();
            aimSettingsOverlay = null;
        }
        if (armorHudOverlay != null) {
            armorHudOverlay.hide();
            armorHudOverlay = null;
        }
        if (crystalOptimizerOverlay != null) {
            crystalOptimizerOverlay.hide();
            crystalOptimizerOverlay = null;
        }
        if (hitTimingOverlay != null) {
            hitTimingOverlay.hide();
            hitTimingOverlay = null;
        }
        if (hitboxOverlay != null) {
            hitboxOverlay.hide();
            hitboxOverlay = null;
        }
        if (reachIndicatorOverlay != null) {
            reachIndicatorOverlay.hide();
            reachIndicatorOverlay = null;
        }
        if (trajectoryPredictionOverlay != null) {
            trajectoryPredictionOverlay.hide();
            trajectoryPredictionOverlay = null;
        }
        if (hitPredictionOverlay != null) {
            hitPredictionOverlay.hide();
            hitPredictionOverlay = null;
        }
        if (killEffectsOverlay != null) {
            killEffectsOverlay.hide();
            killEffectsOverlay = null;
        }
        ReachIndicatorMod.setEnabled(false, null);
        TrajectoryPredictionMod.setEnabled(false, null);
        HitPredictionMod.setEnabled(false, null);
        KillEffectsMod.setEnabled(false, null);
        stopVoiceChat();
        if (voiceChatOverlay != null) {
            voiceChatOverlay.hide();
            voiceChatOverlay = null;
        }
        if (voiceNametagOverlay != null) {
            voiceNametagOverlay.hide();
            voiceNametagOverlay = null;
        }
        VoiceNametagMod.setEnabled(false, null);
        if (modMenuButton != null) {
            modMenuButton.hide();
            modMenuButton = null;
        }
        if (hudOverlay != null) {
            hudOverlay.hide();
            hudOverlay = null;
        }
        instance = null;
    }

    /**
     * Starts the proximity voice link, seeded with the module's stored config.
     *
     * <p>The module owns the socket and the audio device for the whole session, so it is started
     * once here and only reconfigured on a settings change — not per event.
     */
    private void startVoiceChat(InbuiltModManager manager) {
        try {
            if (manager.isVoiceMicEnabled()
                    && activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Ask now; the module already handles a missing mic by starting listen-only, so
                // if this is denied the feature still works and the pill reports listen-only.
                activity.requestPermissions(
                        new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_MIC);
            }
            org.chimeramc.client.core.voice.VoiceChatModule module =
                    org.chimeramc.client.core.voice.VoiceChatModule.get(activity, deviceName());
            // Fill the position/camera seams from the local player so beacons carry a real
            // position and the in-world nametag icons can be projected. Fail-closed: with no
            // native read the module stays in channel mode and the icons draw nothing.
            LocalPlayerFeed.install();
            VoiceNametagMod.setTagSource(new VoiceNametagTagFeed());
            module.start(manager);
        } catch (Throwable t) {
            android.util.Log.w("InbuiltOverlayManager", "Could not start voice chat", t);
        }
    }

    private void stopVoiceChat() {
        org.chimeramc.client.core.voice.VoiceChatModule module =
                org.chimeramc.client.core.voice.VoiceChatModule.peek();
        if (module != null) module.stop();
    }

    /**
     * Starts or stops the whole voice link for a screen that is not the in-game overlay.
     *
     * <p>The standalone Voice screen can be reached from the launcher at any time; it drives the
     * same module the in-game overlay uses, so turning the feature on there and in the game is one
     * piece of state, not two. Persisting the enable flag keeps the in-game restore path honest.
     */
    public boolean setVoiceChatEnabled(boolean enabled) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        manager.setInbuiltModEnabled(ModIds.VOICE_CHAT, enabled);
        handleModToggle(ModIds.VOICE_CHAT, enabled);
        return isModActive(ModIds.VOICE_CHAT);
    }

    /** Whether the voice link is currently running. */
    public boolean isVoiceChatActive() {
        return isModActive(ModIds.VOICE_CHAT);
    }

    /** Applies a channel or mic change to a running session without restarting it. */
    public void applyVoiceConfig() {
        org.chimeramc.client.core.voice.VoiceChatModule module =
                org.chimeramc.client.core.voice.VoiceChatModule.peek();
        if (module != null && module.isRunning()) {
            module.applyConfig(InbuiltModManager.getInstance(activity));
            module.announceNow();
        }
    }

    private String deviceName() {
        try {
            String name = android.os.Build.MODEL;
            return name == null || name.trim().isEmpty() ? "Chimera" : name.trim();
        } catch (Throwable t) {
            return "Chimera";
        }
    }

    public boolean handleKeyEvent(int keyCode, int action) {
        return handleKeyEvent(keyCode, action, keyCode);
    }

    /**
     * @param keyCode the key after profile remapping, used by the mods' own binds
     * @param rawKeyCode the hardware key before remapping
     *
     * <p>The Mod Menu bind is checked against both, so a pad button a profile remapped still
     * opens the menu when it is the one the player captured.
     */
    public boolean handleKeyEvent(int keyCode, int action, int rawKeyCode) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);

        // The open Mod Menu takes controller navigation before anything else. Its root is
        // unfocusable by design (so the platform stops painting a focus wash), so the menu cannot
        // be driven by the framework's focus system and is driven here instead.
        if (handleMenuControllerKey(rawKeyCode, action)) return true;

        // Mod Menu open bind. Honoured for both the keyboard and controller codes, and only on
        // the press so a held button does not flap the panel open and shut.
        if (action == android.view.KeyEvent.ACTION_DOWN && modMenuButton != null) {
            if (InbuiltModManager.matchesModMenuBind(
                    manager.getModMenuKeybind(), manager.getModMenuControllerBind(),
                    keyCode, rawKeyCode)) {
                modMenuButton.toggleMenuFromBind();
                return true;
            }
        }

        boolean zoomEnabled = modActiveStates.getOrDefault(ModIds.ZOOM, false);
        
        int zoomKeybind = manager.getZoomKeybind();
        if (zoomEnabled && keyCode == zoomKeybind) {
            if (zoomOverlay != null) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    zoomOverlay.onKeyDown();
                    return true;
                } else if (action == android.view.KeyEvent.ACTION_UP) {
                    zoomOverlay.onKeyUp();
                    return true;
                }
            }
        }

        boolean snaplookEnabled = modActiveStates.getOrDefault(ModIds.SNAPLOOK, false);

        if (snaplookEnabled && keyCode == android.view.KeyEvent.KEYCODE_X) {
            if (snaplookOverlay != null) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    snaplookOverlay.onKeyDown();
                    return true;
                } else if (action == android.view.KeyEvent.ACTION_UP) {
                    snaplookOverlay.onKeyUp();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Hands one controller key to the open Mod Menu, if there is one.
     *
     * <p>Public because the game activity calls it at the very top of {@code dispatchKeyEvent},
     * before the preloader can swallow the press: a pad button the menu wants to use for navigation
     * would otherwise be consumed by the game first.
     */
    public boolean handleMenuControllerKey(int rawKeyCode, int action) {
        if (modMenuButton == null) return false;
        // The VIP Mod Menu (Screen B) owns controller input while it is open; Screen A (the touch
        // menu) only sees keys when it is the one showing. The two are never open at once.
        org.chimeramc.client.core.mods.inbuilt.vip.VipModMenuOverlay vip =
                modMenuButton.getVipMenuOverlay();
        if (vip != null && vip.isShowing()) {
            return vip.handleControllerKey(rawKeyCode,
                    action == android.view.KeyEvent.ACTION_DOWN);
        }
        ModMenuOverlay menu = modMenuButton.getMenuOverlay();
        if (menu == null || !menu.isShowing()) return false;
        return menu.handleControllerKey(rawKeyCode,
                action == android.view.KeyEvent.ACTION_DOWN);
    }

    /** Hands analogue stick movement to the open Mod Menu, so the stick can move the selection. */
    public boolean handleMenuControllerMotion(android.view.MotionEvent event) {
        if (modMenuButton == null) return false;
        org.chimeramc.client.core.mods.inbuilt.vip.VipModMenuOverlay vip =
                modMenuButton.getVipMenuOverlay();
        if (vip != null && vip.isShowing() && vip.handleControllerMotion(event)) {
            return true;
        }
        ModMenuOverlay menu = modMenuButton.getMenuOverlay();
        return menu != null && menu.isShowing() && menu.handleControllerMotion(event);
    }

    public boolean handleScrollEvent(float scrollDelta) {
        for (ExternalButtonOverlay overlay : externalButtonOverlayMap.values()) {
            if (overlay.onScroll(scrollDelta)) {
                return true;
            }
        }
        if (zoomOverlay != null && zoomOverlay.isZooming()) {
            zoomOverlay.onScroll(scrollDelta);
            return true;
        }
        return false;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        if (cpsDisplayOverlay != null) {
            return cpsDisplayOverlay.handleTouchEvent(event);
        }
        return false;
    }

    public boolean handleMouseEvent(MotionEvent event) {
        if (cpsDisplayOverlay != null) {
            return cpsDisplayOverlay.handleMouseEvent(event);
        }
        return false;
    }

    /**
     * Records one attack the player made, for the Select Hit module.
     *
     * <p>Called from the paths that actually send an attack to the game — a mouse or controller
     * primary button, and a touch attack — so the timing is measured against the same input the
     * game receives rather than against a UI event that may never reach it.
     */
    public void notifyAttack() {
        HitTimingMod.onAttack(android.os.SystemClock.uptimeMillis());
    }

    private final TouchTapDetector touchTapDetector = new TouchTapDetector();

    /**
     * Observes a raw touch event for the Select Hit metronome.
     *
     * <p>With the on-screen controls disabled, a tap on the world is an attack and there is no
     * other funnel to observe it through, so the gesture is classified here. A drag is the look
     * gesture and must not count, otherwise moving the camera would forge a swing and reset the
     * window the module is trying to teach.
     */
    public void notifyTouchForAttack(MotionEvent event, int slopPx) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchTapDetector.onDown(event.getPointerId(0), event.getX(), event.getY(),
                        android.os.SystemClock.uptimeMillis());
                break;
            case MotionEvent.ACTION_MOVE:
                touchTapDetector.onMove(event.getPointerId(0), event.getX(), event.getY(), slopPx);
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int index = event.getActionIndex();
                if (event.getPointerId(index) == 0) {
                    if (touchTapDetector.onUp(0, android.os.SystemClock.uptimeMillis())) notifyAttack();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                if (touchTapDetector.onUp(event.getPointerId(0),
                        android.os.SystemClock.uptimeMillis())) {
                    notifyAttack();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                touchTapDetector.onCancel();
                break;
            default:
                break;
        }
    }

    public void applyConfigurationChanges(String modId) {
        BaseOverlayButton overlay = modOverlayMap.get(modId);
        if (overlay != null) {
            overlay.applyConfigurationChanges();
        }

        if (modId.equals(ModIds.ZOOM) && zoomOverlay != null) {
            zoomOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.SNAPLOOK) && snaplookOverlay != null) {
            snaplookOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.GYRO) && gyroOverlay != null) {
            gyroOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.FPS_DISPLAY) && fpsDisplayOverlay != null) {
            fpsDisplayOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.CPS_DISPLAY) && cpsDisplayOverlay != null) {
            cpsDisplayOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.ARMOR_HUD) && armorHudOverlay != null) {
            armorHudOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.HIT_TIMING) && hitTimingOverlay != null) {
            hitTimingOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.HITBOX) && hitboxOverlay != null) {
            hitboxOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.REACH_INDICATOR) && reachIndicatorOverlay != null) {
            reachIndicatorOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.TRAJECTORY_PREDICTION) && trajectoryPredictionOverlay != null) {
            trajectoryPredictionOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.HIT_PREDICTION) && hitPredictionOverlay != null) {
            hitPredictionOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.KILL_EFFECTS) && killEffectsOverlay != null) {
            killEffectsOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.VOICE_CHAT) && voiceChatOverlay != null) {
            voiceChatOverlay.applyConfigurationChanges();
            VoiceNametagMod.onConfigChanged(InbuiltModManager.getInstance(activity));
            if (voiceNametagOverlay != null) voiceNametagOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.MORE_BUTTONS)) {
            refreshMoreButtons();
        }
        if (modId.equals(ModIds.HOTBAR_SLOT)) {
            refreshHotbarSlots();
        }
    }

    public void setHudEditorMode(boolean active) {
        hudEditorMode = active;
        for (BaseOverlayButton overlay : overlays) {
            overlay.setHudEditorMode(active);
        }
        if (fpsDisplayOverlay != null) {
            fpsDisplayOverlay.setHudEditorMode(active);
        }
        if (cpsDisplayOverlay != null) {
            cpsDisplayOverlay.setHudEditorMode(active);
        }
        if (armorHudOverlay != null) {
            armorHudOverlay.setHudEditorMode(active);
        }
        if (hitTimingOverlay != null) {
            hitTimingOverlay.setHudEditorMode(active);
        }
        if (hitboxOverlay != null) {
            hitboxOverlay.setHudEditorMode(active);
        }
        if (reachIndicatorOverlay != null) {
            reachIndicatorOverlay.setHudEditorMode(active);
        }
        if (trajectoryPredictionOverlay != null) {
            trajectoryPredictionOverlay.setHudEditorMode(active);
        }
        if (hitPredictionOverlay != null) {
            hitPredictionOverlay.setHudEditorMode(active);
        }
        if (killEffectsOverlay != null) {
            killEffectsOverlay.setHudEditorMode(active);
        }
        if (voiceChatOverlay != null) {
            voiceChatOverlay.setHudEditorMode(active);
        }
        if (voiceNametagOverlay != null) {
            voiceNametagOverlay.setHudEditorMode(active);
        }
        if (hudOverlay != null) {
            hudOverlay.setHudEditorMode(active);
        }

        if (modMenuButton != null) {
            if (active) {
                modMenuButton.setVisibility(android.view.View.GONE);
            } else {
                int savedX = InbuiltModManager.getInstance(activity).getOverlayPositionX(ModIds.MOD_MENU, START_X);
                int savedY = InbuiltModManager.getInstance(activity).getOverlayPositionY(ModIds.MOD_MENU, baseY);
                modMenuButton.show(savedX, savedY);
            }
        }
        refreshRuntimeVisibility();

        if (active) {
            selectFirstHudEditorOverlay();
        } else {
            selectHudEditorOverlay(null);
        }
    }

    public void setHudEditorSelectionListener(HudEditorSelectionListener listener) {
        hudEditorSelectionListener = listener;
        if (listener != null) {
            listener.onHudEditorSelectionChanged(getSelectedHudEditorButtonSize());
        }
    }

    public void selectHudEditorOverlay(BaseOverlayButton overlay) {
        if (!hudEditorMode && overlay != null) {
            return;
        }
        selectedDisplayModId = null;
        if (selectedHudEditorOverlay == overlay) {
            notifySelectionListener();
            return;
        }
        if (selectedHudEditorOverlay != null) {
            selectedHudEditorOverlay.setHudEditorSelected(false);
        }
        selectedHudEditorOverlay = overlay;
        if (selectedHudEditorOverlay != null) {
            selectedHudEditorOverlay.setHudEditorSelected(true);
        }
        notifySelectionListener();
    }

    public void selectHudEditorDisplay(String modId) {
        if (!hudEditorMode || modId == null) return;
        if (selectedHudEditorOverlay != null) {
            selectedHudEditorOverlay.setHudEditorSelected(false);
            selectedHudEditorOverlay = null;
        }
        selectedDisplayModId = modId;
        notifySelectionListener();
    }

    private void notifySelectionListener() {
        if (hudEditorSelectionListener != null) {
            hudEditorSelectionListener.onHudEditorSelectionChanged(getSelectedHudEditorButtonSize());
        }
    }

    public int getSelectedHudEditorButtonSize() {
        if (selectedHudEditorOverlay != null) {
            return selectedHudEditorOverlay.getCurrentButtonSizeDp();
        }
        if (selectedDisplayModId != null) {
            return InbuiltModManager.getInstance(activity).getOverlayButtonSize(selectedDisplayModId);
        }
        return 0;
    }

    public void setSelectedHudEditorButtonSize(int sizeDp) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        if (selectedHudEditorOverlay != null) {
            String configKey = selectedHudEditorOverlay.getOverlayConfigKey();
            manager.setOverlayButtonSize(configKey, sizeDp);
            selectedHudEditorOverlay.applyConfigurationChanges();
        } else if (selectedDisplayModId != null) {
            manager.setOverlayButtonSize(selectedDisplayModId, sizeDp);
            if (selectedDisplayModId.equals(ModIds.FPS_DISPLAY) && fpsDisplayOverlay != null) {
                fpsDisplayOverlay.applyConfigurationChanges();
            } else if (selectedDisplayModId.equals(ModIds.CPS_DISPLAY) && cpsDisplayOverlay != null) {
                cpsDisplayOverlay.applyConfigurationChanges();
            }
        }
    }

    private void selectFirstHudEditorOverlay() {
        if (selectedHudEditorOverlay != null) {
            selectedHudEditorOverlay.setHudEditorSelected(true);
            notifySelectionListener();
            return;
        }
        if (selectedDisplayModId != null) {
            notifySelectionListener();
            return;
        }
        if (!overlays.isEmpty()) {
            selectHudEditorOverlay(overlays.get(0));
        } else {
            selectHudEditorOverlay(null);
        }
    }

    public void resetAllPositionsToCenter() {
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int centerX = metrics.widthPixels / 2 - (int)(26 * metrics.density);
        int centerY = metrics.heightPixels / 2 - (int)(26 * metrics.density);

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        
        for (java.util.Map.Entry<String, BaseOverlayButton> entry : modOverlayMap.entrySet()) {
            if (entry.getKey().startsWith(ModIds.HOTBAR_SLOT + ":")) continue;
            manager.setOverlayPosition(entry.getKey(), centerX, centerY);
            entry.getValue().updatePosition(centerX, centerY);
        }

        if (!hotbarSlotOverlayMap.isEmpty()) {
            int gap = (int)(4 * metrics.density);
            int total = 0;
            int enabledCount = 0;
            for (int slot = 1; slot <= 9; slot++) {
                if (!hotbarSlotOverlayMap.containsKey(slot)) continue;
                total += getHotbarSlotButtonSizePx(manager, metrics, slot);
                enabledCount++;
            }
            if (enabledCount > 1) total += gap * (enabledCount - 1);
            int startX = Math.max(0, (metrics.widthPixels - total) / 2);
            int defaultY = Math.max(0, metrics.heightPixels - (int)(120 * metrics.density));
            int nextX = startX;
            for (int slot = 1; slot <= 9; slot++) {
                HotbarSlotOverlay overlay = hotbarSlotOverlayMap.get(slot);
                if (overlay == null) continue;
                String key = ModIds.HOTBAR_SLOT + ":" + slot;
                manager.setOverlayPosition(key, nextX, defaultY);
                overlay.updatePosition(nextX, defaultY);
                nextX += getHotbarSlotButtonSizePx(manager, metrics, slot) + gap;
            }
        }
        
        if (fpsDisplayOverlay != null) {
            manager.setOverlayPosition(ModIds.FPS_DISPLAY, centerX, centerY);
            fpsDisplayOverlay.updatePosition(centerX, centerY);
        }
        if (armorHudOverlay != null) {
            manager.setOverlayPosition(ModIds.ARMOR_HUD, centerX, centerY);
            armorHudOverlay.updatePosition(centerX, centerY);
        }
        if (hitTimingOverlay != null) {
            int topX = metrics.widthPixels / 2 - (int) (48 * metrics.density);
            int topY = (int) (12 * metrics.density);
            manager.setOverlayPosition(ModIds.HIT_TIMING, topX, topY);
            hitTimingOverlay.updatePosition(topX, topY);
        }
        if (cpsDisplayOverlay != null) {
            manager.setOverlayPosition(ModIds.CPS_DISPLAY, centerX, centerY);
            cpsDisplayOverlay.updatePosition(centerX, centerY);
        }
        
        java.util.Set<String> explicitHudModules = new java.util.HashSet<>();
        ExternalModBridge.HudEditorElement[] hudElements = ExternalModBridge.getHudEditorElements();
        if (hudElements != null) {
            for (ExternalModBridge.HudEditorElement element : hudElements) {
                if (element == null || element.moduleId == null || element.positionKeyX == null || element.positionKeyY == null) continue;
                explicitHudModules.add(element.moduleId);
                float elementX = metrics.widthPixels * 0.5f - Math.max(1f, element.width) * 0.5f;
                float elementY = metrics.heightPixels * 0.5f - Math.max(1f, element.height) * 0.5f;
                ExternalModBridge.setExternalModConfig(element.moduleId, element.positionKeyX, String.valueOf(elementX));
                ExternalModBridge.setExternalModConfig(element.moduleId, element.positionKeyY, String.valueOf(elementY));
            }
        }

        ExternalModBridge.DrawCommand[] cmds = ExternalModBridge.getDrawCommands();
        if (cmds != null) {
            java.util.Set<String> processed = new java.util.HashSet<>();
            for (ExternalModBridge.DrawCommand cmd : cmds) {
                if (cmd.moduleId != null && !explicitHudModules.contains(cmd.moduleId) && processed.add(cmd.moduleId)) {
                    ExternalModBridge.setExternalModConfig(cmd.moduleId, "hudPosX", String.valueOf(centerX));
                    ExternalModBridge.setExternalModConfig(cmd.moduleId, "hudPosY", String.valueOf(centerY));
                }
            }
        }
    }

    public void refreshRuntimeVisibility() {
        lastVisibilityStateHash = Long.MIN_VALUE;
        tick();
    }

    public void tick() {
        // Hold-to-repeat runs off the game's own frame tick, so a held button injects at a
        // steady rate rather than in bursts when some other timer happened to fire.
        long frameNow = android.os.SystemClock.uptimeMillis();
        org.chimeramc.client.launcher.controller.ControllerInputProcessor
                .tickCpsRepeats(frameNow);
        // PvP Suite run state advances on the game's own frame tick, like the CPS repeats:
        // Hit Prediction smooths peer velocity and Kill Effects ages its bursts and polls the
        // death feed. Both are no-ops when their module is off.
        HitPredictionMod.tick(frameNow);
        KillEffectsMod.tick(frameNow);
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        boolean isPauseOnly = manager.isPauseMenuOnly();
        boolean isPauseOpen = org.chimeramc.client.preloader.PreloaderInput.isPauseMenuOpen();
        boolean isHudScreenOpen = org.chimeramc.client.preloader.PreloaderInput.isHudScreenOpen();
        boolean isShowingMenu = org.chimeramc.client.preloader.PreloaderInput.isShowingMenu();
        if (isHudScreenOpen) {
            gameWorldSeen = true;
        }
        // A resumed game session is the fallback signal when the preloader's HUD hook never
        // installs; without it the native "false" hid every HUD overlay once the Mod Menu
        // closed. See OverlayVisibility for the full reasoning.
        boolean sessionActive = org.chimeramc.client.core.minecraft.MinecraftActivityState.isRunning()
                || org.chimeramc.client.core.minecraft.MinecraftActivityState.isResumed()
                || org.chimeramc.client.settings.LowLatencyNetworkManager.isGameSessionActive();
        boolean showGameOverlays = OverlayVisibility.showGameOverlays(
                isHudScreenOpen, isPauseOpen, isShowingMenu, hudEditorMode, gameWorldSeen, sessionActive);
        boolean inbuiltVisible = hudEditorMode || showGameOverlays;
        boolean hotbarVisible = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.HOTBAR_SLOT);

        for (BaseOverlayButton overlay : overlays) {
            if (inbuiltVisible || manager.isOverlayShowEverywhere(overlay.getOverlayConfigKey())) {
                overlay.tick();
            }
        }
        for (HotbarSlotOverlay hotbar : hotbarSlotOverlayMap.values()) {
            hotbar.setRenderVisible(hotbarVisible);
        }

        long stateHash = manager.getOverlayVisibilityRevision();
        stateHash = 31L * stateHash + (isPauseOnly ? 1L : 0L);
        stateHash = 31L * stateHash + (isPauseOpen ? 1L : 0L);
        stateHash = 31L * stateHash + (isHudScreenOpen ? 1L : 0L);
        stateHash = 31L * stateHash + (isShowingMenu ? 1L : 0L);
        stateHash = 31L * stateHash + (hudEditorMode ? 1L : 0L);
        // The fallback signals must be part of the hash too: a session starting or the HUD
        // hook finally reporting "open" changes what should be visible even when every native
        // flag is unchanged, and a stale hash would leave the overlays hidden.
        stateHash = 31L * stateHash + (gameWorldSeen ? 1L : 0L);
        stateHash = 31L * stateHash + (sessionActive ? 1L : 0L);
        stateHash = 31L * stateHash + overlays.size();
        for (BaseOverlayButton overlay : overlays) {
            stateHash = 31L * stateHash + System.identityHashCode(overlay);
        }
        stateHash = 31L * stateHash + (fpsDisplayOverlay == null ? 0L : System.identityHashCode(fpsDisplayOverlay));
        stateHash = 31L * stateHash + (cpsDisplayOverlay == null ? 0L : System.identityHashCode(cpsDisplayOverlay));
        stateHash = 31L * stateHash + (chickPetOverlay == null ? 0L : System.identityHashCode(chickPetOverlay));
        stateHash = 31L * stateHash + (modMenuButton == null ? 0L : System.identityHashCode(modMenuButton));
        stateHash = 31L * stateHash + (hudOverlay == null ? 0L : System.identityHashCode(hudOverlay));
        if (stateHash == lastVisibilityStateHash) return;
        lastVisibilityStateHash = stateHash;

        activity.runOnUiThread(() -> {
            if (modMenuButton != null) {
                int visibility = !hudEditorMode && (!isPauseOnly || (isPauseOpen && isShowingMenu))
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                modMenuButton.setVisibility(visibility);
                if (!hudEditorMode && visibility == android.view.View.GONE && modMenuButton.isMenuShowing()) {
                    modMenuButton.hideMenu();
                }
            }

            if (hudOverlay != null) {
                int visibility = hudOverlay.isHudEditorMode() || showGameOverlays
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                if (hudOverlay.getVisibility() != visibility) {
                    hudOverlay.setVisibility(visibility);
                }
            }

            for (BaseOverlayButton overlay : overlays) {
                if (overlay.overlayView != null) {
                    int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(overlay.getOverlayConfigKey())
                            ? android.view.View.VISIBLE
                            : android.view.View.GONE;
                    if (overlay.overlayView.getVisibility() != visibility) {
                        overlay.overlayView.setVisibility(visibility);
                    }
                }
            }

            if (chickPetOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.CHICK_PET)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                chickPetOverlay.setVisibility(visibility);
            }

            if (fpsDisplayOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.FPS_DISPLAY)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                fpsDisplayOverlay.setVisibility(visibility);
            }

            if (cpsDisplayOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.CPS_DISPLAY)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                cpsDisplayOverlay.setVisibility(visibility);
            }

            if (armorHudOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.ARMOR_HUD)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                armorHudOverlay.setOverlayVisibility(visibility);
            }

            if (crystalOptimizerOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.CRYSTAL_OPTIMIZER)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                crystalOptimizerOverlay.setOverlayVisibility(visibility);
            }

            if (hitTimingOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.HIT_TIMING)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                hitTimingOverlay.setOverlayVisibility(visibility);
            }

            if (hitboxOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.HITBOX)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                hitboxOverlay.setOverlayVisibility(visibility);
            }

            if (reachIndicatorOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.REACH_INDICATOR)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                reachIndicatorOverlay.setOverlayVisibility(visibility);
            }

            if (trajectoryPredictionOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.TRAJECTORY_PREDICTION)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                trajectoryPredictionOverlay.setOverlayVisibility(visibility);
            }

            if (hitPredictionOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.HIT_PREDICTION)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                hitPredictionOverlay.setOverlayVisibility(visibility);
            }

            if (killEffectsOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.KILL_EFFECTS)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                killEffectsOverlay.setOverlayVisibility(visibility);
            }

            if (voiceChatOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.VOICE_CHAT)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                voiceChatOverlay.setOverlayVisibility(visibility);
            }

            if (voiceNametagOverlay != null) {
                int visibility = inbuiltVisible || manager.isOverlayShowEverywhere(ModIds.VOICE_CHAT)
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE;
                voiceNametagOverlay.setOverlayVisibility(visibility);
            }
        });
    }
}
