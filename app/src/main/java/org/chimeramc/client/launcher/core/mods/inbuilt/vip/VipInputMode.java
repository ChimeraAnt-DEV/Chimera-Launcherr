package org.chimeramc.client.core.mods.inbuilt.vip;

/**
 * The input family the VIP Mod Menu tailors itself to.
 *
 * <p>The VIP screen is opened by the in-game bind and offers a Controller tab or a Keyboard tab
 * depending on what the player is holding. The decision is pure so it is a JVM test rather than
 * something guessed from a device: a pad present means the controller surface, otherwise the
 * keyboard surface. Touch is deliberately not modelled — the VIP screen is a bind-driven screen,
 * and the touch screen (Screen A) stays the launcher's Mod Menu.
 */
public final class VipInputMode {

    public enum Mode {
        CONTROLLER,
        KEYBOARD
    }

    private VipInputMode() {
    }

    /** Picks the surface for the VIP menu: a pad present means CONTROLLER, else KEYBOARD. */
    public static Mode forGamepad(boolean hasGamepad) {
        return hasGamepad ? Mode.CONTROLLER : Mode.KEYBOARD;
    }
}
