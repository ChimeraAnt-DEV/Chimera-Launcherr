package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The alternate button-map layers: selection by held modifier, base-map fallback, and the
 * precomputed lookup the input hot path uses. No mocks — the whole thing is pure data.
 */
public class RemapLayerTest {

    private static final int MODIFIER = android.view.KeyEvent.KEYCODE_BUTTON_L1;
    private static final int A = android.view.KeyEvent.KEYCODE_BUTTON_A;
    private static final int B = android.view.KeyEvent.KEYCODE_BUTTON_B;
    private static final int X = android.view.KeyEvent.KEYCODE_BUTTON_X;

    @Test
    public void modifierSelectsTheLayerAndReleaseReturnsToTheBaseMap() {
        ControllerProfile profile = new ControllerProfile("P");
        profile.setRemap(A, B); // base: A becomes B
        RemapLayer layer = new RemapLayer("Swap");
        layer.setModifierKeyCode(MODIFIER);
        layer.setRemap(A, X); // while held: A becomes X
        profile.addRemapLayer(layer);

        ControllerResponse response = new ControllerResponse(profile, false);

        // Held: the layer wins.
        assertEquals(X, response.remapKey(A, MODIFIER));
        // Released: the base map is back.
        assertEquals(B, response.remapKey(A, 0));
    }

    @Test
    public void aButtonTheLayerDoesNotListFallsThroughToTheBaseMap() {
        ControllerProfile profile = new ControllerProfile("P");
        profile.setRemap(B, X); // base remaps B
        RemapLayer layer = new RemapLayer("L");
        layer.setModifierKeyCode(MODIFIER);
        layer.setRemap(A, B); // the layer only mentions A
        profile.addRemapLayer(layer);

        ControllerResponse response = new ControllerResponse(profile, false);

        // A is remapped by the layer...
        assertEquals(B, response.remapKey(A, MODIFIER));
        // ...while B, which the layer does not list, still uses the base remap rather than
        // silently becoming identity.
        assertEquals(X, response.remapKey(B, MODIFIER));
    }

    @Test
    public void holdingAnUnrelatedButtonDoesNotSwitchTheMap() {
        ControllerProfile profile = new ControllerProfile("P");
        profile.setRemap(A, B);
        RemapLayer layer = new RemapLayer("L");
        layer.setModifierKeyCode(MODIFIER);
        layer.setRemap(A, X);
        profile.addRemapLayer(layer);

        ControllerResponse response = new ControllerResponse(profile, false);
        assertEquals(B, response.remapKey(A, B));
    }

    @Test
    public void isLayerModifierOnlyMatchesTheConfiguredButtons() {
        ControllerProfile profile = new ControllerProfile("P");
        RemapLayer layer = new RemapLayer("L");
        layer.setModifierKeyCode(MODIFIER);
        profile.addRemapLayer(layer);
        ControllerResponse response = new ControllerResponse(profile, false);

        assertTrue(response.isLayerModifier(MODIFIER));
        assertFalse(response.isLayerModifier(A));
        assertFalse(response.isLayerModifier(0));
    }

    @Test
    public void aProfileWithNoLayersBehavesExactlyLikeTheBaseMap() {
        ControllerProfile profile = new ControllerProfile("P");
        profile.setRemap(A, B);
        ControllerResponse response = new ControllerResponse(profile, false);

        assertEquals(B, response.remapKey(A));
        assertEquals(B, response.remapKey(A, MODIFIER));
        assertFalse(response.isLayerModifier(MODIFIER));
    }

    @Test
    public void firstLayerWinsWhenTwoShareAModifier() {
        ControllerProfile profile = new ControllerProfile("P");
        RemapLayer first = new RemapLayer("First");
        first.setModifierKeyCode(MODIFIER);
        first.setRemap(A, B);
        RemapLayer second = new RemapLayer("Second");
        second.setModifierKeyCode(MODIFIER);
        second.setRemap(A, X);
        profile.addRemapLayer(first);
        profile.addRemapLayer(second);

        assertEquals(first, profile.activeLayer(MODIFIER));
        assertEquals(B, new ControllerResponse(profile, false).remapKey(A, MODIFIER));
    }

    @Test
    public void aModifierOfZeroIsNeverSelectedByHoldingAnything() {
        RemapLayer layer = new RemapLayer("L");
        layer.setModifierKeyCode(0);
        assertEquals(RemapLayer.NO_MODIFIER, layer.getModifierKeyCode());

        ControllerProfile profile = new ControllerProfile("P");
        profile.addRemapLayer(layer);
        assertNull(profile.activeLayer(0));
        assertNull(profile.activeLayer(MODIFIER));
    }

    @Test
    public void copyIsDeepForLayers() {
        ControllerProfile profile = new ControllerProfile("P");
        RemapLayer layer = new RemapLayer("L");
        layer.setModifierKeyCode(MODIFIER);
        layer.setRemap(A, B);
        profile.addRemapLayer(layer);

        ControllerProfile copy = profile.copy();
        copy.getRemapLayers().get(0).setRemap(A, X);

        // Mutating the copy's layer must not reach into the original.
        assertEquals(B, profile.getRemapLayers().get(0).remapKey(A));
    }

    @Test
    public void gamepadKeyCodesAreRecognisedAndKeyboardOnesAreNot() {
        assertTrue(ControllerResponse.isGamepadKeyCode(android.view.KeyEvent.KEYCODE_BUTTON_A));
        assertTrue(ControllerResponse.isGamepadKeyCode(android.view.KeyEvent.KEYCODE_BUTTON_R1));
        assertFalse(ControllerResponse.isGamepadKeyCode(android.view.KeyEvent.KEYCODE_Q));
        assertFalse(ControllerResponse.isGamepadKeyCode(android.view.KeyEvent.KEYCODE_SPACE));
        assertFalse(ControllerResponse.isGamepadKeyCode(0));
    }

    @Test
    public void anOutOfRangeModifierDoesNotSelectALayer() {
        ControllerProfile profile = new ControllerProfile("P");
        RemapLayer layer = new RemapLayer("L");
        layer.setModifierKeyCode(MODIFIER);
        profile.addRemapLayer(layer);
        ControllerResponse response = new ControllerResponse(profile, false);

        // A negative modifier reads as "none", not as a match for the 0 default.
        assertFalse(response.isLayerModifier(-5));
        assertNotNull(response.remapKey(A, -5));
    }
}
