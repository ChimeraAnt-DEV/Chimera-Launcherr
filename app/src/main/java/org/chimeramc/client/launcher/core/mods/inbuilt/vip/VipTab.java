package org.chimeramc.client.core.mods.inbuilt.vip;

import org.chimeramc.client.R;

/**
 * The sections of the VIP Mod Menu (Screen B).
 *
 * <p>Declaration order is the tab order and the bumper/shoulder cycle order, exactly like the
 * launcher's {@code LauncherTab}. The first section is the shared module list; the two input
 * sections are the screen's sub-modes and the tab that opens by default follows whatever input
 * the player is holding, so a pad user lands on the Controller surface and a KBM user on the
 * Keyboard surface without a manual switch.
 *
 * <p>Pure and Android-free apart from the resource ids it carries, so ordering, wraparound and
 * default selection are JVM tests.
 */
public enum VipTab {
    MODULES(R.string.vip_tab_modules, R.drawable.ic_modules),
    CONTROLLER(R.string.vip_tab_controller, R.drawable.ic_controller),
    KEYBOARD(R.string.vip_tab_keyboard, R.drawable.ic_keyboard);

    private final int titleRes;
    private final int iconRes;

    VipTab(int titleRes, int iconRes) {
        this.titleRes = titleRes;
        this.iconRes = iconRes;
    }

    public int getTitleRes() {
        return titleRes;
    }

    public int getIconRes() {
        return iconRes;
    }

    /** The tab that opens for the given input family; modules stay one press away. */
    public static VipTab defaultFor(VipInputMode.Mode mode) {
        return mode == VipInputMode.Mode.CONTROLLER ? CONTROLLER : KEYBOARD;
    }

    /** The next section, wrapping. Used by the shoulder/bumper cycle. */
    public VipTab next() {
        VipTab[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** The previous section, wrapping. */
    public VipTab previous() {
        VipTab[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
