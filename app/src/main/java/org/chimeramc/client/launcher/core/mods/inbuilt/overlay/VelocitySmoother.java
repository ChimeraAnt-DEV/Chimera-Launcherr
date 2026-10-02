package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Rolling position history for the Hit Prediction module.
 *
 * <p>Hit Prediction extrapolates a target's next position from its velocity, and the single
 * most important quality choice is <em>which</em> velocity: the last frame's delta jitters with
 * packet timing and produces a marker that visibly wobbles. This keeps the last few samples and
 * averages the velocity across them, so a one-frame hiccup moves the marker by a fraction of a
 * block instead of snapping it.
 *
 * <p>Pure and allocation-free after construction: a fixed ring of samples, updated in place. The
 * module runs it per peer per frame, so it must not build a list each tick.
 */
public final class VelocitySmoother {

    /** Number of frames averaged. Five is the spec's choice: enough to smooth, short enough to
     * still turn with a real direction change inside a swing. */
    public static final int WINDOW = 5;

    /** Velocity above which prediction is treated as unreliable, in blocks per second. */
    public static final float UNRELIABLE_SPEED = 9.0f;

    private final float[] xs = new float[WINDOW];
    private final float[] ys = new float[WINDOW];
    private final float[] zs = new float[WINDOW];
    private final long[] times = new long[WINDOW];
    private int count;
    private int head;

    /** Discards the history; call when a target is lost or a new one acquired. */
    public void reset() {
        count = 0;
        head = 0;
    }

    /** Adds a sample. Time is a monotonic millisecond stamp from the caller. */
    public void add(float x, float y, float z, long timeMs) {
        xs[head] = x;
        ys[head] = y;
        zs[head] = z;
        times[head] = timeMs;
        head = (head + 1) % WINDOW;
        if (count < WINDOW) count++;
    }

    public int sampleCount() {
        return count;
    }

    /** True once there is enough history for a smoothed velocity. */
    public boolean hasVelocity() {
        return count >= 2;
    }

    /**
     * The window-averaged velocity, in blocks per second, or null before two samples.
     *
     * <p>Averaged as total displacement over total elapsed time rather than a mean of per-frame
     * velocities: a frame that arrived late then weighs the same as one that arrived on time,
     * which is the point - a jittery arrival pattern must not become a jittery marker.
     */
    public float[] velocity() {
        if (count < 2) return null;
        int oldest = (head - count + WINDOW) % WINDOW;
        int newest = (head - 1 + WINDOW) % WINDOW;
        long dt = times[newest] - times[oldest];
        if (dt <= 0) return null;
        float seconds = dt / 1000f;
        return new float[]{
                (xs[newest] - xs[oldest]) / seconds,
                (ys[newest] - ys[oldest]) / seconds,
                (zs[newest] - zs[oldest]) / seconds
        };
    }

    /** The latest sample position, or null when empty. */
    public float[] latest() {
        if (count == 0) return null;
        int newest = (head - 1 + WINDOW) % WINDOW;
        return new float[]{xs[newest], ys[newest], zs[newest]};
    }

    /** Speed of the smoothed velocity in blocks per second, or 0 when unknown. */
    public float speed() {
        float[] v = velocity();
        if (v == null) return 0f;
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
