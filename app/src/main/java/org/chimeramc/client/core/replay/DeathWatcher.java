package org.chimeramc.client.core.replay;

/**
 * Decides when the local player has died, from the game's own health reading.
 *
 * <p>Bedrock gives a client no death event, so the trigger is inferred from health: a reading that
 * was a plausible positive value and then reaches zero is a death. The watcher is deliberately
 * strict about what counts:
 *
 * <ul>
 *   <li><b>It must have seen a live reading first.</b> A source that is unavailable (this build's
 *       default) or that only ever reports zero never produces a death, so a missing feed cannot
 *       fire a highlight on its own.</li>
 *   <li><b>The reading must be plausible.</b> A health above {@link #MAX_HEALTH} is garbage from a
 *       wrong offset and is ignored, so a misread cannot look like a live player.</li>
 *   <li><b>One death fires once.</b> The death screen can keep reporting zero for seconds; the
 *       debounce collapses that into a single trigger.</li>
 * </ul>
 *
 * <p>Pure and clock-injected so the transition rules are unit-testable without a device.
 */
public final class DeathWatcher {

    /** A full health bar. A reading above this is not a real health value. */
    public static final float MAX_HEALTH = 20f;

    /** Two deaths closer than this are treated as one (the death screen keeps reporting zero). */
    public static final long DEBOUNCE_MS = 5_000L;

    private boolean sawLiveHealth;
    private long lastDeathMs = Long.MIN_VALUE;

    /**
     * Feeds one sample and reports whether it is a death.
     *
     * @param health          the current health reading
     * @param healthAvailable false when no feed is installed or the read was implausible
     * @param nowMs           a monotonic timestamp
     * @return true on the single frame a death is detected
     */
    public boolean onSample(float health, boolean healthAvailable, long nowMs) {
        if (!healthAvailable) return false;

        if (health > 0f && health <= MAX_HEALTH) {
            sawLiveHealth = true;
            return false;
        }
        if (health <= 0f && sawLiveHealth) {
            if (lastDeathMs != Long.MIN_VALUE && nowMs - lastDeathMs < DEBOUNCE_MS) {
                return false;
            }
            lastDeathMs = nowMs;
            // A respawn returns to positive health, which re-arms the next death.
            sawLiveHealth = false;
            return true;
        }
        return false;
    }

    public void reset() {
        sawLiveHealth = false;
        lastDeathMs = Long.MIN_VALUE;
    }
}
