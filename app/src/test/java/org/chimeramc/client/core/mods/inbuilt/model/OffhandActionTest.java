package org.chimeramc.client.core.mods.inbuilt.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The off-hand action rule: which game key(s) a mode sends. Pure, so no device is needed.
 *
 * <p>Bedrock has no swap-to-off-hand key, so the primary mode opens the inventory instead; the test
 * names say "inventory" to match the behaviour rather than the old "swap" spelling.
 */
public class OffhandActionTest {

    private static final int INVENTORY = 33; // KEYCODE_E
    private static final int USE = 50;       // KEYCODE_V

    @Test
    public void inventoryModeSendsOnlyTheInventoryKey() {
        assertArrayEquals(new int[]{INVENTORY},
                OffhandAction.keysFor(OffhandAction.MODE_INVENTORY, INVENTORY, USE));
    }

    @Test
    public void useModeSendsOnlyTheUseKey() {
        assertArrayEquals(new int[]{USE}, OffhandAction.keysFor(OffhandAction.MODE_USE, INVENTORY, USE));
    }

    @Test
    public void bothModeOpensTheInventoryThenUsesInOrder() {
        assertArrayEquals(new int[]{INVENTORY, USE},
                OffhandAction.keysFor(OffhandAction.MODE_BOTH, INVENTORY, USE));
    }

    @Test
    public void anUnknownModeSendsNothing() {
        assertEquals(0, OffhandAction.keysFor(99, INVENTORY, USE).length);
        assertEquals(0, OffhandAction.keysFor(-1, INVENTORY, USE).length);
    }

    @Test
    public void clampModeKeepsTheValueInRange() {
        assertEquals(OffhandAction.MODE_INVENTORY, OffhandAction.clampMode(-5));
        assertEquals(OffhandAction.MODE_INVENTORY, OffhandAction.clampMode(0));
        assertEquals(OffhandAction.MODE_BOTH, OffhandAction.clampMode(2));
        assertEquals(OffhandAction.MODE_BOTH, OffhandAction.clampMode(50));
    }
}
