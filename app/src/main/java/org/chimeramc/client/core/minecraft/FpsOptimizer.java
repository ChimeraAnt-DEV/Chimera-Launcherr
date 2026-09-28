package org.chimeramc.client.core.minecraft;

/**
 * Decides how hard the launcher may work on the host while a game session runs.
 *
 * <p>The rule that makes this module honest: <b>it never touches Minecraft's rendering</b>.
 * There is no code here that lowers the game's resolution, trims its render distance, disables
 * its particles, or changes a single draw call. A Bedrock client's visuals are set by the
 * player and the server, and a launcher that quietly altered them would be a different, worse
 * product. What this class can actually recover is only the host-side cost the launcher itself
 * adds: news/update polling, speculative DNS and background refreshes competing for the same
 * CPU, network and battery the game is using.
 *
 * <p>It is a pure function of the inputs so the rule, not a belief about it, is what the unit
 * tests pin. The caller reads the device state and this returns the actions to take.
 */
public final class FpsOptimizer {

    /** What the launcher should do to the host right now. */
    public enum Action {
        /** Session is not the foreground concern; leave the launcher's normal behaviour alone. */
        NONE,
        /** A session is running: serve cached data and pause speculative work. */
        QUIET_HOST,
        /** The host is under real pressure: quiet plus suspending non-essential refresh work. */
        SHED_BACKGROUND
    }

    private FpsOptimizer() {}

    /**
     * The action to take for a device state.
     *
     * @param enabled        whether the user turned the FPS optimization module on
     * @param sessionActive  whether a game session is running (the module is a no-op otherwise)
     * @param thermalSeverity the platform thermal reading; {@code >= 3} is throttling territory
     * @param batterySaver   whether the OS battery saver is on
     * @param lowMemory      whether the device reports a critically low memory state
     */
    public static Action decide(boolean enabled, boolean sessionActive, int thermalSeverity,
                                boolean batterySaver, boolean lowMemory) {
        if (!enabled || !sessionActive) return Action.NONE;

        // A hot or squeezed device is the case the module exists for: the game is the thing
        // that must keep its share of the CPU, so the launcher steps back further than merely
        // serving caches.
        if (thermalSeverity >= 3 || batterySaver || lowMemory) return Action.SHED_BACKGROUND;

        return Action.QUIET_HOST;
    }

    /**
     * Whether this action asks the launcher to stop speculative/background work.
     *
     * <p>Expressed here rather than at the call sites so the mapping cannot drift between them.
     */
    public static boolean pausesSpeculativeWork(Action action) {
        return action == Action.SHED_BACKGROUND;
    }

    /**
     * Whether this action asks the launcher to serve cached data instead of polling.
     *
     * <p>True for every active state: a running session is the reason the module exists.
     */
    public static boolean servesCachedData(Action action) {
        return action == Action.QUIET_HOST || action == Action.SHED_BACKGROUND;
    }

    /**
     * A short description safe to show the user.
     *
     * <p>Deliberately does <em>not</em> claim an FPS number: the module cannot measure the
     * game's frame rate from here, and promising one would be the same dishonesty as the
     * "reduce network latency" label promising a ping.
     */
    public static String describe(Action action) {
        switch (action) {
            case SHED_BACKGROUND:
                return "Host under load: launcher paused background work";
            case QUIET_HOST:
                return "Session running: launcher serving cached data";
            case NONE:
            default:
                return "Inactive";
        }
    }
}
