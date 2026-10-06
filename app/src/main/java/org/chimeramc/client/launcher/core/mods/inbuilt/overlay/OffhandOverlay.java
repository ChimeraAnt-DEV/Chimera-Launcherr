package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.ImageButton;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.core.mods.inbuilt.model.OffhandAction;

/**
 * The Offhand button: swaps the held item into the off hand and/or uses the off-hand item.
 *
 * <p>It sends the game's own off-hand key presses (F to swap, V to use) through the same
 * {@code dispatchKeyEvent} path a hardware keyboard uses, so it behaves identically in a local
 * world, a Realm and on a large server such as The Hive — nothing here is server- or world-specific.
 * Bedrock resolves the swap/use itself; the button only delivers the key.
 *
 * <p>The hardware keybind mirrors the button so a keyboard/controller player can trigger the same
 * action without the on-screen control.
 */
public class OffhandOverlay extends BaseOverlayButton {

    /**
     * Re-entrancy guard for the synthetic keys.
     *
     * <p>A synthetic key is dispatched back through the activity, which re-enters the overlay
     * manager's key handler. If the player bound the hardware key to the same code as the swap or
     * use key, that would match the bind again and recurse. The guard makes the injected press skip
     * the bind match and simply reach the game. Dispatch is synchronous on the UI thread, so a plain
     * static flag is enough.
     */
    private static boolean injecting;

    public OffhandOverlay(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.OFFHAND;
    }

    @Override
    protected int getIconResource() {
        return R.drawable.ic_offhand;
    }

    @Override
    protected void onButtonPressStart() {
        setPressedIcon(true);
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        performAction(activity, manager.getOffhandMode(),
                manager.getOffhandSwapKey(), manager.getOffhandUseKey());
    }

    @Override
    protected void onButtonPressEnd() {
        setPressedIcon(false);
    }

    @Override
    protected void onButtonClick() {
        // The press already fired the action; a tap is a press + release, so doing it here too
        // would double-fire.
    }

    /** True while a synthetic off-hand key is being delivered, so the bind match is skipped. */
    static boolean isInjecting() {
        return injecting;
    }

    /**
     * Sends the configured off-hand key(s) synchronously through the activity's key dispatch.
     *
     * <p>Mode 0 swaps, 1 uses, 2 does both in order, matching the config dialog's choice. Shared by
     * the on-screen button and the hardware keybind so both deliver exactly the same keys.
     */
    static void performAction(Activity activity, int mode, int swapKey, int useKey) {
        if (injecting) return;
        injecting = true;
        try {
            for (int keyCode : OffhandAction.keysFor(mode, swapKey, useKey)) {
                dispatchKey(activity, keyCode);
            }
        } finally {
            injecting = false;
        }
    }

    private static void dispatchKey(Activity activity, int keyCode) {
        long now = SystemClock.uptimeMillis();
        activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode,
                0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
        activity.dispatchKeyEvent(new KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode,
                0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
    }

    private void setPressedIcon(boolean pressed) {
        if (overlayView instanceof ImageButton) {
            ((ImageButton) overlayView).setImageResource(
                    pressed ? R.drawable.ic_offhand_pressed : R.drawable.ic_offhand);
        }
    }
}
