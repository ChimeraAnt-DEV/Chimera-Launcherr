package org.chimeramc.client.core.mods.inbuilt.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The off-hand action rule: which game key(s) a mode sends. Pure, so no device is needed.
 */
public class OffhandActionTest {

    private static final int SWAP = 34; // KEYCODE_F
    private static final int USE = 50;  // KEYCODE_V

    @Test
    public void swapModeSendsOnlyTheSwapKey() {
        assertArrayEquals(new int[]{SWAP}, OffhandAction.keysFor(OffhandAction.MODE_SWAP, SWAP, USE));
    }

    @Test
    public void useModeSendsOnlyTheUseKey() {
        assertArrayEquals(new int[]{USE}, OffhandAction.keysFor(OffhandAction.MODE_USE, SWAP, USE));
    }

    @Test
    public void bothModeSwapsThenUsesInOrder() {
        assertArrayEquals(new int[]{SWAP, USE}, OffhandAction.keysFor(OffhandAction.MODE_BOTH, SWAP, USE));
    }

    @Test
    public void anUnknownModeSendsNothing() {
        assertEquals(0, OffhandAction.keysFor(99, SWAP, USE).length);
        assertEquals(0, OffhandAction.keysFor(-1, SWAP, USE).length);
    }

    @Test
    public void clampModeKeepsTheValueInRange() {
        assertEquals(OffhandAction.MODE_SWAP, OffhandAction.clampMode(-5));
        assertEquals(OffhandAction.MODE_SWAP, OffhandAction.clampMode(0));
        assertEquals(OffhandAction.MODE_BOTH, OffhandAction.clampMode(2));
        assertEquals(OffhandAction.MODE_BOTH, OffhandAction.clampMode(50));
    }
}
