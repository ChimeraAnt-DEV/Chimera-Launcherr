package org.chimeramc.client.core.mods.inbuilt.model;

/**
 * Which off-hand key(s) a button or keybind should send.
 *
 * <p>Kept Android-free and pure so the rule is unit-testable and so the on-screen button and the
 * hardware keybind share exactly one implementation — two copies is how they drift.
 *
 * <p><b>Why the primary action opens the inventory.</b> Bedrock Edition has no "swap to off hand"
 * keybinding at all — there is no {@code key.offhand} in the shipped input map (Java's {@code F}
 * swap simply does not exist here). The off-hand slot is filled from the inventory screen, and only
 * a handful of items are accepted there: shields, arrows (including tipped), firework rockets,
 * totems of undying, filled/explorer maps and nautilus shells. So the honest, working action is to
 * open the inventory ({@code key.inventory}, {@code E}) and let the player place an allowed item in
 * the slot, rather than inject a swap key that the game ignores.
 */
public final class OffhandAction {

    /** Open the inventory, where the off-hand slot lives (Bedrock's {@code E}). */
    public static final int MODE_INVENTORY = 0;
    /** Use whatever is in the off hand (Bedrock's {@code key.use}). */
    public static final int MODE_USE = 1;
    /** Open the inventory, then use the off-hand item. */
    public static final int MODE_BOTH = 2;

    private OffhandAction() {
    }

    /**
     * The key codes to send for a mode, in order.
     *
     * @return an array of 1 or 2 key codes; empty for an unknown mode
     */
    public static int[] keysFor(int mode, int inventoryKey, int useKey) {
        switch (mode) {
            case MODE_INVENTORY:
                return new int[]{inventoryKey};
            case MODE_USE:
                return new int[]{useKey};
            case MODE_BOTH:
                return new int[]{inventoryKey, useKey};
            default:
                return new int[0];
        }
    }

    /** Clamps a stored mode to a valid value. */
    public static int clampMode(int mode) {
        return mode < MODE_INVENTORY ? MODE_INVENTORY : Math.min(mode, MODE_BOTH);
    }
}
