package org.chimeramc.client.core.mods.inbuilt.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Pins the Mod Menu bind match.
 *
 * The match runs on the input-to-photon path for every key press while a session is live, so it
 * is pure and testable: an unbound side must never match (0 is not a key code), the keyboard and
 * controller binds must not cross-match, and a profile that remaps the bound button must still
 * open the menu via the raw code.
 */
public class ModMenuBindMatchTest {

    private static final int MENU_KEY = KeyEvent.KEYCODE_F6;
    private static final int CONTROLLER_KEY = KeyEvent.KEYCODE_BUTTON_L1;
    private static final int OTHER_KEY = KeyEvent.KEYCODE_F7;

    @Test
    public void unboundSidesNeverMatch() {
        assertFalse(InbuiltModManager.matchesModMenuBind(0, 0, MENU_KEY, MENU_KEY));
        assertFalse(InbuiltModManager.matchesModMenuBind(0, 0, 0, 0));
    }

    @Test
    public void keyboardBindMatchesItsKey() {
        assertTrue(InbuiltModManager.matchesModMenuBind(MENU_KEY, 0, MENU_KEY, MENU_KEY));
        assertFalse(InbuiltModManager.matchesModMenuBind(MENU_KEY, 0, OTHER_KEY, OTHER_KEY));
    }

    @Test
    public void controllerBindMatchesItsButton() {
        assertTrue(InbuiltModManager.matchesModMenuBind(0, CONTROLLER_KEY, CONTROLLER_KEY, CONTROLLER_KEY));
        assertFalse(InbuiltModManager.matchesModMenuBind(0, CONTROLLER_KEY, MENU_KEY, MENU_KEY));
    }

    @Test
    public void bindsDoNotCrossMatch() {
        // A keyboard key bound only on the keyboard side is not a controller bind and vice versa.
        assertFalse(InbuiltModManager.matchesModMenuBind(MENU_KEY, CONTROLLER_KEY, OTHER_KEY, OTHER_KEY));
        assertTrue(InbuiltModManager.matchesModMenuBind(MENU_KEY, CONTROLLER_KEY, CONTROLLER_KEY, CONTROLLER_KEY));
        assertTrue(InbuiltModManager.matchesModMenuBind(MENU_KEY, CONTROLLER_KEY, MENU_KEY, MENU_KEY));
    }

    @Test
    public void remappedPressStillMatchesViaTheRawCode() {
        // The profile remapped the bound button to something else; the player still pressed the
        // button they bound, which the raw code records.
        assertTrue(InbuiltModManager.matchesModMenuBind(CONTROLLER_KEY, 0, OTHER_KEY, CONTROLLER_KEY));
        // And the inverse: a remap that lands on the bound code opens it too.
        assertTrue(InbuiltModManager.matchesModMenuBind(CONTROLLER_KEY, 0, CONTROLLER_KEY, OTHER_KEY));
    }
}
