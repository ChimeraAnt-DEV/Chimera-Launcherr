package org.chimeramc.client.ui.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.chimeramc.client.ui.activities.InstallationsActivity;
import org.chimeramc.client.ui.activities.CustomizeActivity;
import org.chimeramc.client.ui.activities.InstancesActivity;
import org.chimeramc.client.ui.activities.MainActivity;
import org.chimeramc.client.ui.activities.ModsFullscreenActivity;
import org.junit.Test;

public class LauncherTabTest {

    @Test
    public void tabOrderMatchesConsoleLayout() {
        LauncherTab[] tabs = LauncherTab.values();
        assertSame(LauncherTab.LAUNCH, tabs[0]);
        assertSame(LauncherTab.VERSIONS, tabs[1]);
        assertSame(LauncherTab.INSTALLATIONS, tabs[2]);
        assertSame(LauncherTab.MODS, tabs[3]);
        assertSame(LauncherTab.CUSTOMIZE, tabs[4]);
        assertSame(LauncherTab.SETTINGS, tabs[5]);
        // Six tabs fit the HorizontalScrollView. About lives inside Settings,
        // Controller/Skins inside Customize, and Voice is an in-game Mod Menu section
        // rather than a launcher destination, so it is deliberately not here.
        assertEquals(6, tabs.length);
    }

    @Test
    public void tabsMapToTheirActivities() {
        assertSame(MainActivity.class, LauncherTab.LAUNCH.activity());
        assertSame(InstancesActivity.class, LauncherTab.VERSIONS.activity());
        assertSame(InstallationsActivity.class, LauncherTab.INSTALLATIONS.activity());
        assertSame(ModsFullscreenActivity.class, LauncherTab.MODS.activity());
        assertSame(CustomizeActivity.class, LauncherTab.CUSTOMIZE.activity());
    }

    @Test
    public void forActivityResolvesKnownTabs() {
        assertSame(LauncherTab.LAUNCH, LauncherTab.forActivity(MainActivity.class));
        assertSame(LauncherTab.VERSIONS, LauncherTab.forActivity(InstancesActivity.class));
        assertSame(LauncherTab.INSTALLATIONS, LauncherTab.forActivity(InstallationsActivity.class));
        assertNull(LauncherTab.forActivity(String.class));
    }

    @Test
    public void offsetWrapsAtBothEnds() {
        LauncherTab first = LauncherTab.values()[0];
        LauncherTab last = LauncherTab.values()[LauncherTab.values().length - 1];

        // A wrap keeps a right-bumper press from dead-ending on the last tab.
        assertSame(first, last.offset(1));
        assertSame(last, first.offset(-1));
    }

    @Test
    public void offsetMovesWithinBounds() {
        assertSame(LauncherTab.VERSIONS, LauncherTab.LAUNCH.offset(1));
        assertSame(LauncherTab.LAUNCH, LauncherTab.VERSIONS.offset(-1));
        assertSame(LauncherTab.LAUNCH, LauncherTab.LAUNCH.offset(0));
    }

    @Test
    public void bumpersAndDpadDriveNavigation() {
        assertEquals(1, LauncherTab.directionForKey(KeyEvent.KEYCODE_BUTTON_R1));
        assertEquals(1, LauncherTab.directionForKey(KeyEvent.KEYCODE_BUTTON_R2));
        assertEquals(1, LauncherTab.directionForKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(-1, LauncherTab.directionForKey(KeyEvent.KEYCODE_BUTTON_L1));
        assertEquals(-1, LauncherTab.directionForKey(KeyEvent.KEYCODE_BUTTON_L2));
        assertEquals(-1, LauncherTab.directionForKey(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(0, LauncherTab.directionForKey(KeyEvent.KEYCODE_BUTTON_A));
        assertEquals(0, LauncherTab.directionForKey(KeyEvent.KEYCODE_DPAD_UP));
    }

    /** A bumper press must never navigate away from a running game. */

    @Test
    public void gameSessionSuppressesTabSwitching() {
        assertFalse(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_BUTTON_R1, true, true));
        assertFalse(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_BUTTON_L1, true, true));
    }

    @Test
    public void switchingOnlyWhenNavBarIsPresent() {
        assertFalse(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_BUTTON_R1, false, false));
        assertTrue(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_BUTTON_R1, true, false));
    }

    @Test
    public void nonNavigationKeysAreIgnored() {
        assertFalse(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_BUTTON_A, true, false));
        assertFalse(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_DPAD_UP, true, false));
        assertFalse(LauncherTab.shouldHandleKey(KeyEvent.KEYCODE_BACK, true, false));
    }
}