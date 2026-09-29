package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;

import org.chimeramc.client.core.mods.inbuilt.overlay.ModMenuNavigation;
import org.junit.Test;

/**
 * Controller navigation over the Mod Menu grid. Pure, so every edge — group headers that own a
 * whole row, moving off the end, an all-unavailable menu — is pinned here rather than found on a
 * pad mid-game.
 */
public class ModMenuNavigationTest {

    // A three-wide grid: index 0 and index 4 are full-row group headers, the rest are cards.
    private static final boolean[] FULL_ROW = {true, false, false, false, true, false, false};
    private static final boolean[] SELECTABLE = {false, true, true, true, false, true, true};

    private static int move(int current, ModMenuNavigation.Direction direction) {
        return ModMenuNavigation.move(current, 3, FULL_ROW, SELECTABLE, direction);
    }

    @Test
    public void theFirstPressSelectsTheFirstSelectableItem() {
        assertEquals(1, move(ModMenuNavigation.NONE, ModMenuNavigation.Direction.DOWN));
    }

    @Test
    public void leftAndRightStepOneSelectableItemAtATime() {
        assertEquals(2, move(1, ModMenuNavigation.Direction.RIGHT));
        assertEquals(1, move(2, ModMenuNavigation.Direction.LEFT));
    }

    @Test
    public void rightAtTheEndOfTheListStaysPut() {
        assertEquals(6, move(6, ModMenuNavigation.Direction.RIGHT));
    }

    @Test
    public void downPastAGroupHeaderLandsOnARealModule() {
        // Index 2 is the middle of the first card row; the next row starts with the index-4 group
        // header, so the move must settle on the nearest card in the following row, not the header.
        assertEquals(6, move(2, ModMenuNavigation.Direction.DOWN));
    }

    @Test
    public void aRowOfOnlyHeadersDoesNotSwallowTheSelection() {
        boolean[] fullRow = {true, false, false, false, false};
        boolean[] selectable = {true, false, false, false, false};
        assertEquals(0, ModMenuNavigation.move(0, 4, fullRow, selectable,
                ModMenuNavigation.Direction.DOWN));
    }

    @Test
    public void anEmptyMenuReportsNothingToSelect() {
        assertEquals(ModMenuNavigation.NONE, ModMenuNavigation.move(ModMenuNavigation.NONE, 4,
                new boolean[0], new boolean[0], ModMenuNavigation.Direction.DOWN));
    }

    @Test
    public void anAllUnavailableMenuReportsNothingToSelect() {
        boolean[] none = {false, false, false};
        assertEquals(ModMenuNavigation.NONE, ModMenuNavigation.move(ModMenuNavigation.NONE, 3,
                none, none, ModMenuNavigation.Direction.DOWN));
    }

    @Test
    public void aSingleColumnListStepsByWholeRows() {
        boolean[] fullRow = {false, false, false, false};
        boolean[] selectable = {false, true, true, true};
        assertEquals(2, ModMenuNavigation.move(1, 1, fullRow, selectable,
                ModMenuNavigation.Direction.DOWN));
        assertEquals(1, ModMenuNavigation.move(2, 1, fullRow, selectable,
                ModMenuNavigation.Direction.UP));
    }
}
