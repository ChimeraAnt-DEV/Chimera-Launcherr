package org.chimeramc.client.core.replay;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks whether a Replay clip is being decoded, so the recorder does not also run.
 *
 * <p>When a clip plays in the embedded player the game is still live behind the overlay, so two
 * decode sessions would run at once on the same hardware. The device also has one video
 * encoder/decoder path on many SoCs, and fighting over it mid-recording can stutter both. The
 * capture service checks this gate before starting and stops when playback begins.
 *
 * <p>A count rather than a boolean: two overlays (Screen A and Screen B) can in principle each
 * hold a player, and the gate must stay latched until the last one releases. Every release is
 * paired with a start, and {@link #reset()} is the safety net if a player is torn down without a
 * matching callback.
 */
public final class ReplayPlaybackGate {

    private static final AtomicInteger ACTIVE = new AtomicInteger(0);

    private ReplayPlaybackGate() {}

    public static void onPlaybackStarted() {
        ACTIVE.incrementAndGet();
    }

    public static void onPlaybackEnded() {
        ACTIVE.updateAndGet(value -> value > 0 ? value - 1 : 0);
    }

    /** True while any clip is decoding. */
    public static boolean isPlaybackActive() {
        return ACTIVE.get() > 0;
    }

    /** Clears the gate; used when a screen is destroyed without a clean stop. */
    public static void reset() {
        ACTIVE.set(0);
    }
}
