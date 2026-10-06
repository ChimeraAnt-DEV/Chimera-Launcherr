package org.chimeramc.client.core.mods.inbuilt.model;

/**
 * Which off-hand key(s) a button or keybind should send.
 *
 * <p>Kept Android-free and pure so the rule is unit-testable and so the on-screen button and the
 * hardware keybind share exactly one implementation — two copies is how they drift.
 */
public final class OffhandAction {

    /** Swap the held item into the off hand (Bedrock's F). */
    public static final int MODE_SWAP = 0;
    /** Use the off-hand item (Bedrock's V). */
    public static final int MODE_USE = 1;
    /** Swap, then use. */
    public static final int MODE_BOTH = 2;

    private OffhandAction() {
    }

    /**
     * The key codes to send for a mode, in order.
     *
     * @return an array of 1 or 2 key codes; empty for an unknown mode
     */
    public static int[] keysFor(int mode, int swapKey, int useKey) {
        switch (mode) {
            case MODE_SWAP:
                return new int[]{swapKey};
            case MODE_USE:
                return new int[]{useKey};
            case MODE_BOTH:
                return new int[]{swapKey, useKey};
            default:
                return new int[0];
        }
    }

    /** Clamps a stored mode to a valid value. */
    public static int clampMode(int mode) {
        return mode < MODE_SWAP ? MODE_SWAP : Math.min(mode, MODE_BOTH);
    }
}
