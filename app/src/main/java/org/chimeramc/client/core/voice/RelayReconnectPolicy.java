package org.chimeramc.client.core.voice;

/**
 * The backoff schedule a relay client follows when the server is unreachable.
 *
 * <p>A phone that walks out of signal and back should reconnect quickly, but a server that is
 * down for an hour must not be hit every second by every client. The schedule doubles from a short
 * first delay up to a ceiling, and the ceiling is where a long outage sits. The small random
 * jitter spread across clients matters more than it looks: without it every client that dropped at
 * the same moment retries at the same moment, and the recovering server is hit by a synchronised
 * stampede.
 *
 * <p>Pure, with the randomness injected, so the schedule and the cap are unit-tested exactly
 * rather than sampled.
 */
public final class RelayReconnectPolicy {

    /** The first retry delay: fast enough that a brief blip is barely noticed. */
    public static final long BASE_DELAY_MS = 1000;
    /** The longest delay between retries, so a long outage is a slow poll and not a flood. */
    public static final long MAX_DELAY_MS = 30000;
    /** Retries before the client reports the link as unhealthy rather than merely connecting. */
    public static final int ATTEMPTS_BEFORE_DEGRADED = 3;

    private int attempt;
    private final float jitterFraction;

    /** @param jitterFraction the maximum ± fraction of jitter applied to each delay, e.g. 0.2 */
    public RelayReconnectPolicy(float jitterFraction) {
        this.jitterFraction = Math.max(0f, Math.min(0.9f, jitterFraction));
    }

    public static RelayReconnectPolicy defaults() {
        return new RelayReconnectPolicy(0.2f);
    }

    /**
     * The delay before the next attempt, advancing the schedule.
     *
     * @param random a value in {@code [0,1)}; injected so tests are deterministic
     */
    public long nextDelayMs(double random) {
        long base = BASE_DELAY_MS;
        for (int i = 0; i < attempt && base < MAX_DELAY_MS; i++) {
            base = Math.min(MAX_DELAY_MS, base * 2);
        }
        attempt++;
        if (jitterFraction <= 0f) return base;
        double spread = base * jitterFraction;
        // Map [0,1) to [-1,1) so the jitter is symmetric around the base delay.
        double offset = (random * 2.0 - 1.0) * spread;
        long delay = Math.round(base + offset);
        return Math.max(BASE_DELAY_MS / 2, Math.min(MAX_DELAY_MS, delay));
    }

    /** How many consecutive failures have been scheduled. */
    public int attempts() {
        return attempt;
    }

    /** Whether the link has failed often enough to be reported as degraded. */
    public boolean isDegraded() {
        return attempt >= ATTEMPTS_BEFORE_DEGRADED;
    }

    /** Resets after a successful connection, so the next outage starts fast again. */
    public void reset() {
        attempt = 0;
    }
}
