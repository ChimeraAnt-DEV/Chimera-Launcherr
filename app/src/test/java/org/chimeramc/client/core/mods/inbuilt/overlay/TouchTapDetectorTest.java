package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Classification tests for the touch attack tap.
 *
 * <p>The detector exists because a vanilla touchscreen tap is the only attack path the game
 * consumes without going through a mouse-button call. Getting the rule wrong has two distinct
 * failure modes, and both are pinned here: a camera drag counted as an attack (forging swings),
 * and a real tap missed (making the Select Hit metronome silently under-count).
 */
public class TouchTapDetectorTest {

    private static final float SLOP = 12f;

    @Test
    public void aQuickTapIsAnAttack() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        assertTrue(d.onUp(0, 1000L + TouchTapDetector.MAX_TAP_MS - 20L));
    }

    @Test
    public void aDragIsNotAnAttack() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        d.onMove(0, 100f + SLOP * 3f, 100f, SLOP);
        // Moving the camera must not count, or every look gesture resets the timing window.
        assertFalse(d.onUp(0, 1050L));
    }

    @Test
    public void smallJitterWithinSlopIsStillATap() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        d.onMove(0, 100f + SLOP * 0.4f, 100f + SLOP * 0.4f, SLOP);
        assertTrue(d.onUp(0, 1050L));
    }

    @Test
    public void aLongHoldIsNotAnAttack() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        // Holding is mining or using, not a swing.
        assertFalse(d.onUp(0, 1000L + TouchTapDetector.MAX_TAP_MS + 200L));
    }

    @Test
    public void aCancelledGestureIsNotAnAttack() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        d.onCancel();
        assertFalse(d.onUp(0, 1010L));
        assertFalse(d.isTracking());
    }

    @Test
    public void aDifferentPointerLiftingDoesNotEndTheGesture() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        // A second finger lifting is not the end of the tracked tap.
        assertFalse(d.onUp(1, 1010L));
        assertTrue(d.isTracking());
    }

    @Test
    public void aGestureWithoutADownIsIgnored() {
        TouchTapDetector d = new TouchTapDetector();
        assertFalse(d.onUp(0, 1000L));
    }

    @Test
    public void movementAfterSlopIsStickyEvenIfTheFingerComesBack() {
        TouchTapDetector d = new TouchTapDetector();
        d.onDown(0, 100f, 100f, 1000L);
        d.onMove(0, 200f, 100f, SLOP);
        // Returning to the start does not un-drag: the gesture was a look, not a tap.
        d.onMove(0, 100f, 100f, SLOP);
        assertFalse(d.onUp(0, 1050L));
    }
}
