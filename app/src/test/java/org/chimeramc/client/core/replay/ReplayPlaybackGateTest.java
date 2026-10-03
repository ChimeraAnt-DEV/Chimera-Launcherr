package org.chimeramc.client.core.replay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * Pins the playback/recording mutual-exclusion gate.
 *
 * <p>Two decode sessions at once is the one real resource risk in embedded playback, so the
 * counter rules are tested without a device: a single session latches, overlapping sessions stay
 * latched until the last release, and an unbalanced release cannot drive the count negative.
 */
public class ReplayPlaybackGateTest {

    @After
    public void tearDown() {
        ReplayPlaybackGate.reset();
    }

    @Test
    public void idleByDefault() {
        assertFalse(ReplayPlaybackGate.isPlaybackActive());
    }

    @Test
    public void aSingleSessionLatchesUntilReleased() {
        ReplayPlaybackGate.onPlaybackStarted();
        assertTrue(ReplayPlaybackGate.isPlaybackActive());
        ReplayPlaybackGate.onPlaybackEnded();
        assertFalse(ReplayPlaybackGate.isPlaybackActive());
    }

    @Test
    public void overlappingSessionsStayLatchedUntilTheLastRelease() {
        ReplayPlaybackGate.onPlaybackStarted();
        ReplayPlaybackGate.onPlaybackStarted();
        ReplayPlaybackGate.onPlaybackEnded();
        assertTrue(ReplayPlaybackGate.isPlaybackActive());
        ReplayPlaybackGate.onPlaybackEnded();
        assertFalse(ReplayPlaybackGate.isPlaybackActive());
    }

    @Test
    public void anUnbalancedReleaseDoesNotGoNegative() {
        ReplayPlaybackGate.onPlaybackEnded();
        ReplayPlaybackGate.onPlaybackEnded();
        assertFalse(ReplayPlaybackGate.isPlaybackActive());
        // Still functional after the stray releases.
        ReplayPlaybackGate.onPlaybackStarted();
        assertTrue(ReplayPlaybackGate.isPlaybackActive());
    }

    @Test
    public void resetClearsAnyLatch() {
        ReplayPlaybackGate.onPlaybackStarted();
        ReplayPlaybackGate.reset();
        assertFalse(ReplayPlaybackGate.isPlaybackActive());
    }
}
