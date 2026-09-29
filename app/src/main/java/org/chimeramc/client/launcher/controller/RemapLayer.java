package org.chimeramc.client.launcher.controller;

import java.util.HashMap;
import java.util.Map;

/**
 * A whole alternate button map on top of a profile's base remaps.
 *
 * <p>Layer 0 of a profile is always the base map (the profile's own {@code buttonRemaps}); every
 * {@code RemapLayer} beyond it is one extra map that is only in force while its {@code modifierKey}
 * is held. That is the "hold a button to switch the whole map" behaviour: while the modifier is
 * down {@link #remapKey} answers from this layer, and the moment it is released the base map is
 * back. A modifier of 0 means the layer is never selected by holding anything and is only usable
 * as a named map the player picks explicitly.
 *
 * <p>Pure and Android-free apart from key codes: {@link ControllerResponse} precomputes each
 * layer's flat lookup array once when the profile changes, so the hot path still does an array
 * index and never a map lookup.
 */
public final class RemapLayer {

    /** A modifier of zero means the layer has no hold trigger. */
    public static final int NO_MODIFIER = 0;

    private String name;
    private int modifierKeyCode = NO_MODIFIER;
    private final Map<Integer, Integer> remaps = new HashMap<>();

    public RemapLayer() {
        this("Layer");
    }

    public RemapLayer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getModifierKeyCode() {
        return modifierKeyCode;
    }

    public void setModifierKeyCode(int keyCode) {
        modifierKeyCode = keyCode <= 0 ? NO_MODIFIER : keyCode;
    }

    public Map<Integer, Integer> getRemaps() {
        return remaps;
    }

    public int remapKey(int keyCode) {
        if (keyCode <= 0) return keyCode;
        Integer mapped = remaps.get(keyCode);
        return mapped != null ? mapped : keyCode;
    }

    public void setRemap(int fromKeyCode, int toKeyCode) {
        if (fromKeyCode <= 0) return;
        if (toKeyCode <= 0 || toKeyCode == fromKeyCode) {
            remaps.remove(fromKeyCode);
        } else {
            remaps.put(fromKeyCode, toKeyCode);
        }
    }

    public RemapLayer copy() {
        RemapLayer copy = new RemapLayer(name);
        copy.modifierKeyCode = modifierKeyCode;
        copy.remaps.putAll(remaps);
        return copy;
    }
}
