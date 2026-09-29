package org.chimeramc.client.core.mods.inbuilt.model;

/**
 * Which modules cannot currently do their job, and therefore must not look usable.
 *
 * <p>A module that needs a per-frame game feed has no provider in this build — the native
 * signatures it would need are not derivable (see {@link ModIds#requiresGameData}). Listing such
 * a module as if it worked is the actual defect: a player toggles it, nothing happens, and the
 * module reads as broken rather than as "not available yet". The Mod Menu therefore badges it,
 * greys it out and refuses the toggle.
 *
 * <p>This is a pure predicate on purpose. It is the single source of truth for the badge, the
 * grey-out and the disabled toggle, so those three cannot drift apart, and it is unit-testable
 * without a device.
 */
public final class ModAvailability {

    /** The reason shown on a module that cannot run, or null when the module is usable. */
    public static String unavailableReason(String modId) {
        return ModIds.requiresGameData(modId) ? REASON_NO_GAME_DATA : null;
    }

    /**
     * True when the module must be presented as not working: badged, greyed and unclickable.
     */
    public static boolean isUnavailable(String modId) {
        return unavailableReason(modId) != null;
    }

    /** Whether a module's controls may be interacted with at all. */
    public static boolean isInteractive(String modId) {
        return !isUnavailable(modId);
    }

    /** A stable id for the reason, so a future cause can be told apart from this one. */
    public static final String REASON_NO_GAME_DATA = "no_game_data";

    private ModAvailability() {}
}
