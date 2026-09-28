package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the reconnect schedule: it must start fast, grow to the ceiling, never exceed it, and be
 * jittered so clients do not retry in lockstep.
 */
public class RelayReconnectPolicyTest {

    @Test
    public void theFirstDelayIsTheBase() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(0f);
        assertEquals(RelayReconnectPolicy.BASE_DELAY_MS, policy.nextDelayMs(0.5));
    }

    @Test
    public void theDelayDoublesUntilTheCeiling() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(0f);
        assertEquals(1000, policy.nextDelayMs(0.5));
        assertEquals(2000, policy.nextDelayMs(0.5));
        assertEquals(4000, policy.nextDelayMs(0.5));
        assertEquals(8000, policy.nextDelayMs(0.5));
    }

    @Test
    public void theDelayNeverExceedsTheCeiling() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(0f);
        for (int i = 0; i < 50; i++) {
            long delay = policy.nextDelayMs(0.5);
            assertTrue("delay " + delay + " exceeded the ceiling", delay <= RelayReconnectPolicy.MAX_DELAY_MS);
        }
    }

    @Test
    public void jitterKeepsTheDelayWithinTheBand() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(0.2f);
        long base = RelayReconnectPolicy.BASE_DELAY_MS;
        // random = 0 should be the low edge of the band, random ~1 the high edge.
        long low = policy.nextDelayMs(0.0);
        policy.reset();
        long high = policy.nextDelayMs(0.9999);
        assertTrue("low edge " + low, low >= base * 0.8 - 1 && low <= base);
        assertTrue("high edge " + high, high >= base && high <= base * 1.2 + 1);
    }

    @Test
    public void differentRandomnessGivesDifferentDelays() {
        RelayReconnectPolicy a = new RelayReconnectPolicy(0.2f);
        RelayReconnectPolicy b = new RelayReconnectPolicy(0.2f);
        assertTrue("jitter must decorrelate two clients", a.nextDelayMs(0.1) != b.nextDelayMs(0.9));
    }

    @Test
    public void thePolicyReportsDegradedAfterRepeatedFailures() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(0f);
        for (int i = 0; i < RelayReconnectPolicy.ATTEMPTS_BEFORE_DEGRADED - 1; i++) {
            policy.nextDelayMs(0.5);
        }
        assertFalse(policy.isDegraded());
        policy.nextDelayMs(0.5);
        assertTrue(policy.isDegraded());
    }

    @Test
    public void resetReturnsToTheFastSchedule() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(0f);
        for (int i = 0; i < 6; i++) {
            policy.nextDelayMs(0.5);
        }
        assertTrue(policy.attempts() >= 6);
        policy.reset();
        assertEquals(0, policy.attempts());
        assertFalse(policy.isDegraded());
        assertEquals(RelayReconnectPolicy.BASE_DELAY_MS, policy.nextDelayMs(0.5));
    }

    @Test
    public void aJitterFractionAboveTheCapIsClamped() {
        RelayReconnectPolicy policy = new RelayReconnectPolicy(5f);
        long delay = policy.nextDelayMs(0.0);
        assertTrue("an absurd jitter fraction must be clamped", delay > 0);
    }
}
