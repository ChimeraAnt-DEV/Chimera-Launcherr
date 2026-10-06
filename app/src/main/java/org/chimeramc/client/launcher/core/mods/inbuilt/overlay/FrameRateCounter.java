package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Pure rolling frame-rate counter: a fixed window of frame timestamps reduced to a whole-number
 * rate. Android-free so the arithmetic is unit-testable without a device.
 *
 * <p>Frames arrive as monotonic nanosecond stamps. {@link #fpsAt} counts how many stamps fall
 * within the last second. A duplicate stamp (some stacks deliver one) is ignored so it cannot
 * inflate the rate.
 */
public final class FrameRateCounter {

    /** Frame timestamps retained; enough for a second at any plausible refresh rate. */
    public static final int WINDOW = 240;

    /** Sentinel so a legitimate first frame at t=0 is not mistaken for a duplicate. */
    private static final long NO_STAMP = Long.MIN_VALUE;

    private final long[] stamps = new long[WINDOW];
    private int cursor;
    private int count;
    private long lastStampNanos = NO_STAMP;

    /** Records one frame timestamp. A repeated timestamp is ignored. */
    public void record(long frameTimeNanos) {
        if (frameTimeNanos == lastStampNanos) return;
        lastStampNanos = frameTimeNanos;
        stamps[cursor] = frameTimeNanos;
        cursor = (cursor + 1) % WINDOW;
        if (count < WINDOW) count++;
    }

    /** Frames within the last second of {@code nowNanos}, as a whole number. */
    public int fpsAt(long nowNanos) {
        if (count < 2) return 0;
        long cutoff = nowNanos - 1_000_000_000L;
        int frames = 0;
        // Walk newest-first and stop at the first stamp older than the window.
        for (int i = 1; i <= count; i++) {
            int index = (cursor - i + WINDOW * 2) % WINDOW;
            if (stamps[index] < cutoff) break;
            frames++;
        }
        return frames;
    }

    /** The rate at the most recent stamp; 0 before two frames are known. */
    public int fps() {
        return fpsAt(lastStampNanos);
    }

    public void reset() {
        cursor = 0;
        count = 0;
        lastStampNanos = NO_STAMP;
    }
}
