package org.chimeramc.client.ui.navigation;

import android.app.Activity;
import android.view.KeyEvent;

import org.chimeramc.client.ui.activities.InstallationsActivity;
import org.chimeramc.client.ui.activities.AboutActivity;
import org.chimeramc.client.ui.activities.CustomizeActivity;
import org.chimeramc.client.ui.activities.InstancesActivity;
import org.chimeramc.client.ui.activities.MainActivity;
import org.chimeramc.client.ui.activities.ModsFullscreenActivity;
import org.chimeramc.client.ui.activities.SettingsActivity;

/**
 * The launcher's top-level destinations.
 *
 * The launcher navigates by {@link Activity}, not by swapping fragments inside one screen,
 * so a tab is a label plus the activity it opens. Declaration order is the order shown in
 * the nav bar and the order controller bumpers cycle through, so the two cannot drift.
 *
 * Voice is deliberately <b>not</b> here. It is a mod, so its home is the in-game Mod Menu's
 * top bar (first entry, ahead of Modules); a launcher nav tab for it duplicated that home and
 * put a communication feature in the same row as navigation. {@code VoiceChatActivity} still
 * exists and is still reachable, but only from the Mod Menu.
 */
public enum LauncherTab {
    LAUNCH(MainActivity.class),
    VERSIONS(InstancesActivity.class),
    INSTALLATIONS(InstallationsActivity.class),
    MODS(ModsFullscreenActivity.class),
    CUSTOMIZE(CustomizeActivity.class),
    SETTINGS(SettingsActivity.class);

    private final Class<? extends Activity> activity;

    LauncherTab(Class<? extends Activity> activity) {
        this.activity = activity;
    }

    public Class<? extends Activity> activity() {
        return activity;
    }

    /** Index of this tab, used to position the selection indicator. */
    public int index() {
        return ordinal();
    }

    /**
     * The tab [steps] positions away, wrapping at both ends.
     *
     * Console dashboards cycle rather than stopping; wrapping keeps a right-bumper press
     * from dead-ending on the last tab with no feedback.
     */
    public LauncherTab offset(int steps) {
        LauncherTab[] tabs = values();
        int count = tabs.length;
        int next = ((ordinal() + steps) % count + count) % count;
        return tabs[next];
    }

    /** The tab whose activity is [activityClass], or null when it is not a tab. */
    public static LauncherTab forActivity(Class<?> activityClass) {
        for (LauncherTab tab : values()) {
            if (tab.activity == activityClass) return tab;
        }
        return null;
    }

    /**
     * Direction a key press moves through the tab bar, or 0 when the key is not tab
     * navigation.
     *
     * Both bumper pairs are accepted because L1/R1 (shoulders) and L2/R2 (triggers) report
     * as different key codes across controller types. D-pad left/right is included so a
     * controller with non-working bumpers is not locked to one tab.
     */
    public static int directionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return -1;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 1;
            default:
                return 0;
        }
    }

    /** True when [keyCode] should move the tab selection. */
    public static boolean isTabNavigationKey(int keyCode) {
        return directionForKey(keyCode) != 0;
    }

    /**
     * Whether a bumper press should switch tabs right now.
     *
     * Guarded on two conditions. The nav bar must actually be present, and no game session
     * may be running. The session check is defence in depth: gameplay runs in its own
     * activity which has no nav bar, but this makes it impossible for any future in-game
     * screen to have a bumper press navigate away from a running game.
     */
    public static boolean shouldHandleKey(int keyCode, boolean navBarPresent, boolean gameSessionActive) {
        return navBarPresent && !gameSessionActive && isTabNavigationKey(keyCode);
    }
}