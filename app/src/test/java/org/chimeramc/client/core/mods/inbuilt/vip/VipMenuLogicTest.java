package org.chimeramc.client.core.mods.inbuilt.vip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.ModMenuNavigation;
import org.junit.Test;

/**
 * The VIP Mod Menu's pure rules, with no device and no mocks.
 *
 * <p>These are the pieces that must not regress: the input→sub-mode decision, the tab order and
 * wraparound, the default tab, the keyboard layout's geometry invariants, and the selection maths
 * shared through {@code ModMenuNavigation}.
 */
public class VipMenuLogicTest {

    // ---- input mode ---------------------------------------------------------

    @Test
    public void aPadPicksTheControllerSurface() {
        assertEquals(VipInputMode.Mode.CONTROLLER, VipInputMode.forGamepad(true));
    }

    @Test
    public void noPadPicksTheKeyboardSurface() {
        assertEquals(VipInputMode.Mode.KEYBOARD, VipInputMode.forGamepad(false));
    }

    // ---- tab order ----------------------------------------------------------

    @Test
    public void declarationOrderIsTheTabOrder() {
        assertEquals(VipTab.MODULES, VipTab.values()[0]);
        assertEquals(VipTab.REPLAY, VipTab.values()[1]);
        assertEquals(VipTab.CONTROLLER, VipTab.values()[2]);
        assertEquals(VipTab.KEYBOARD, VipTab.values()[3]);
    }

    @Test
    public void shouldersWrapInBothDirections() {
        assertEquals(VipTab.REPLAY, VipTab.MODULES.next());
        assertEquals(VipTab.CONTROLLER, VipTab.REPLAY.next());
        assertEquals(VipTab.KEYBOARD, VipTab.CONTROLLER.next());
        assertEquals(VipTab.MODULES, VipTab.KEYBOARD.next());

        assertEquals(VipTab.KEYBOARD, VipTab.MODULES.previous());
        assertEquals(VipTab.REPLAY, VipTab.CONTROLLER.previous());
        assertEquals(VipTab.MODULES, VipTab.REPLAY.previous());
        assertEquals(VipTab.CONTROLLER, VipTab.KEYBOARD.previous());
    }

    @Test
    public void theDefaultTabMatchesTheInput() {
        assertEquals(VipTab.CONTROLLER, VipTab.defaultFor(VipInputMode.Mode.CONTROLLER));
        assertEquals(VipTab.KEYBOARD, VipTab.defaultFor(VipInputMode.Mode.KEYBOARD));
    }

    @Test
    public void everyTabCarriesATitleAndAnIcon() {
        for (VipTab tab : VipTab.values()) {
            assertTrue("title for " + tab, tab.getTitleRes() != 0);
            assertTrue("icon for " + tab, tab.getIconRes() != 0);
        }
    }

    // ---- keyboard layout ----------------------------------------------------

    @Test
    public void theBoardIsFiveRowsPlusTheArrowCluster() {
        assertEquals(7, new VipKeyLayout().rowCount());
    }

    @Test
    public void everyCapHasALegendAndAWidth() {
        VipKeyLayout layout = new VipKeyLayout();
        assertFalse(layout.keys().isEmpty());
        for (VipKeyLayout.Key key : layout.keys()) {
            assertNotNull("legend", key.legend);
            assertFalse("legend for " + key.code, key.legend.isEmpty());
            assertTrue("width for " + key.legend, key.width > 0f);
        }
    }

    @Test
    public void noTwoCapsOverlap() {
        // Two caps on the same row must not share any horizontal space; a layout bug here is
        // exactly what a drawn keyboard hides.
        VipKeyLayout layout = new VipKeyLayout();
        for (VipKeyLayout.Key a : layout.keys()) {
            for (VipKeyLayout.Key b : layout.keys()) {
                if (a == b || a.y != b.y) continue;
                boolean disjoint = a.x + a.width <= b.x || b.x + b.width <= a.x;
                assertTrue("overlap: " + a.legend + " / " + b.legend, disjoint);
            }
        }
    }

    @Test
    public void everyCapStaysInsideTheBoard() {
        VipKeyLayout layout = new VipKeyLayout();
        for (VipKeyLayout.Key key : layout.keys()) {
            assertTrue(key.legend + " starts left of the board", key.x >= 0f);
            assertTrue(key.legend + " runs past the board",
                    key.x + key.width <= layout.widthUnits());
        }
    }

    @Test
    public void eachLetterRowSumsToTheBoardWidth() {
        // Rows 0..3 are the main letter rows; they must fill the board exactly, which is what
        // makes the drawn rows line up rather than drift.
        VipKeyLayout layout = new VipKeyLayout();
        for (int row = 0; row <= 3; row++) {
            float sum = 0f;
            for (VipKeyLayout.Key key : layout.keys()) {
                if (key.y == row) sum += key.width;
            }
            assertEquals("row " + row, layout.widthUnits(), sum, 0.001f);
        }
    }

    @Test
    public void aKnownKeyRoundTrips() {
        VipKeyLayout layout = new VipKeyLayout();
        VipKeyLayout.Key a = layout.keyForCode(29); // KEYCODE_A
        assertNotNull(a);
        assertEquals("A", a.legend);
        VipKeyLayout.Key space = layout.keyForCode(62); // KEYCODE_SPACE
        assertNotNull(space);
        assertEquals("SPACE", space.legend);
        assertNull(layout.keyForCode(12345));
    }

    @Test
    public void modifiersAreMarkedSoTheyCanBeDrawnDifferently() {
        VipKeyLayout layout = new VipKeyLayout();
        assertTrue(layout.keyForCode(59).modifier);  // left shift
        assertTrue(layout.keyForCode(62).modifier);  // space
        assertFalse(layout.keyForCode(29).modifier); // A
    }

    // ---- shared selection maths ---------------------------------------------

    @Test
    public void aHeaderRowSpansTheWholeGrid() {
        // The VIP grid reuses ModMenuNavigation's layout rule, where a full-row flag means a
        // header: stepping down from the first card row must land on the next card row, not the
        // header that owns its own row.
        boolean[] fullRow = {true, false, false, false, true, false, false, false};
        boolean[] selectable = {false, true, true, true, false, true, true, true};

        int down = ModMenuNavigation.move(1, 3, fullRow, selectable,
                ModMenuNavigation.Direction.DOWN);
        assertEquals(5, down);

        int up = ModMenuNavigation.move(5, 3, fullRow, selectable,
                ModMenuNavigation.Direction.UP);
        assertEquals(1, up);
    }
}
