package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The mic indicator's artwork is static text, so its shape can be checked without a device.
 * This pins the grid so a stray edit cannot leave the sprite ragged or off-grid.
 */
public class MicIndicatorViewTest {

    @Test
    public void everySpriteRowIsExactlyTheGridWidth() {
        for (String row : MicIndicatorView.sprite()) {
            assertEquals(MicIndicatorView.GRID, row.length());
            for (char c : row.toCharArray()) {
                assertTrue("sprite uses only '#' and '.'", c == '#' || c == '.');
            }
        }
    }

    @Test
    public void theSpriteIsSquare() {
        assertEquals(MicIndicatorView.GRID, MicIndicatorView.sprite().length);
    }

    @Test
    public void theSpriteIsNotEmpty() {
        int filled = 0;
        for (String row : MicIndicatorView.sprite()) {
            for (char c : row.toCharArray()) {
                if (c == '#') filled++;
            }
        }
        assertTrue("the mic sprite must draw something", filled > 20);
    }
}
