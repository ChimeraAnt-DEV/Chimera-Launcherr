package org.chimeramc.client.core.mods.inbuilt.vip;

/**
 * Relay for a one-shot key capture running inside a menu.
 *
 * <p>The in-game activity dispatches keys through the preloader first, which can consume the very
 * press a bind picker is waiting for. The picker therefore registers here and the activity offers
 * each raw press before anything else may swallow it — the same mechanism the touch Mod Menu uses,
 * shared so the VIP screen and the touch screen cannot both hold a capture at once and silently
 * steal each other's presses.
 *
 * <p>Static because the object that receives the event is the Activity, not the overlay.
 */
public final class MenuBindRelay {

    /** Receives the next captured key. */
    public interface Capture {
        void onKey(int keyCode);
    }

    private static volatile Capture active;

    private MenuBindRelay() {
    }

    public static void set(Capture capture) {
        active = capture;
    }

    public static void clear() {
        active = null;
    }

    public static boolean isCapturing() {
        return active != null;
    }

    /** Offers a raw press to the current capture; true when it was consumed. */
    public static boolean deliver(int keyCode) {
        Capture capture = active;
        if (capture == null) return false;
        capture.onKey(keyCode);
        return true;
    }
}
