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
 * <p>The rule is per <em>module</em> and per <em>option</em>. A module can be usable overall
 * while one of its outputs still needs the game: the Hitboxes module draws peers from the voice
 * feed, so it stays clickable, but its mob/item/projectile boxes and its crit/combo guides can
 * only come from the native entity list and are disabled individually
 * ({@link ModIds#outputRequiresGameData}).
 *
 * <p>This is a pure predicate on purpose. It is the single source of truth for the badge, the
 * grey-out, the disabled toggle and the disabled option, so those cannot drift apart, and it is
 * unit-testable without a device.
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

    /**
     * The reason shown beside a single config option that cannot produce output, or null when the
     * option can run. Mirrors {@link #unavailableReason} one level down, so a switch that can only
     * ever draw nothing is disabled with the same wording as a whole unavailable module.
     */
    public static String optionUnavailableReason(String modId, String configKey) {
        return ModIds.outputRequiresGameData(modId, configKey) ? REASON_NO_GAME_DATA : null;
    }

    /** Whether a single config option may be changed. */
    public static boolean isOptionInteractive(String modId, String configKey) {
        return optionUnavailableReason(modId, configKey) == null;
    }

    /** A stable id for the reason, so a future cause can be told apart from this one. */
    public static final String REASON_NO_GAME_DATA = "no_game_data";

    private ModAvailability() {}
}
