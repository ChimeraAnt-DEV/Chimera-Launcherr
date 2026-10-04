package org.chimeramc.client.core.minecraft;

/**
 * Defaults for the dynamic-render-distance governor.
 *
 * <p>These are the bounds the launcher pushes to the preloader; the hysteresis itself lives in the
 * preloader's {@code OptifineGovernor} so the step rule is unit-tested once.
 */
public final class RenderDistanceDefaults {

    /** Never step below this many chunks. */
    public static final int MIN_CHUNKS = 4;

    /** Start at this many chunks and never step above it. */
    public static final int MAX_CHUNKS = 12;

    /** Step down when the frame rate sits below this for the sustain window. */
    public static final int FPS_THRESHOLD = 45;

    private RenderDistanceDefaults() {
    }
}
