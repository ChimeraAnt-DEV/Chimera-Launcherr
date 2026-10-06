package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;

public class ModIconHelper {
    public static int getModIcon(String modId) {
        return switch (modId) {
            case ModIds.QUICK_DROP -> R.drawable.ic_quick_drop;
            case ModIds.CAMERA_PERSPECTIVE -> R.drawable.ic_camera;
            case ModIds.TOGGLE_HUD -> R.drawable.ic_toggle_hud_normal;
            case ModIds.AUTO_SPRINT -> R.drawable.ic_sprint_normal;
            case ModIds.CHICK_PET -> R.drawable.chick_idle_1;
            case ModIds.ZOOM -> R.drawable.ic_zoom_normal;
            case ModIds.FPS_DISPLAY -> R.drawable.ic_fps;
            case ModIds.CPS_DISPLAY -> R.drawable.ic_cps;
            case ModIds.SNAPLOOK -> R.drawable.ic_snaplook_normal;
            case ModIds.VIRTUAL_CURSOR -> R.drawable.ic_virtual_cursor;
            case ModIds.GYRO -> R.drawable.ic_gyro_normal;
            case ModIds.POJAV_CONTROLS -> R.drawable.ic_pojav_controls;
            case ModIds.MORE_BUTTONS -> R.drawable.ic_more_buttons_normal;
            case ModIds.HOTBAR_SLOT -> R.drawable.ic_hotbar_slot;
            case ModIds.OFFHAND -> R.drawable.ic_offhand;
            case ModIds.AIM_SETTINGS -> R.drawable.ic_crosshair;
            default -> R.drawable.ic_settings;
        };
    }
}
