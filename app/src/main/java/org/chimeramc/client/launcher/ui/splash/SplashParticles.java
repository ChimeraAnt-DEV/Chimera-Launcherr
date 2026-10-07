package org.chimeramc.client.launcher.ui.splash;

import java.util.Random;

/**
 * The splash scene's rising ember field, as pure arithmetic.
 *
 * <p>The splash backdrop is a live scene rather than a static gradient: soft embers drift up from
 * the lower edge with a slow sway and a flicker, so the screen never looks frozen while the
 * launcher warms up. All of the motion is computed here from a time delta and the view bounds, so
 * the rules -- embers rise, they stay inside the box, they recycle off the top, alpha stays in
 * range -- are unit-testable on a JVM where no {@code Canvas} exists.
 *
 * <p>Storage is parallel primitive arrays rather than an object per ember: this runs every frame
 * for the whole splash, and allocating a particle object per ember would hand the collector a job
 * on every spawn. {@link #update} allocates nothing.
 *
 * <p>Deterministic when seeded: the same seed and the same deltas produce the same field, which is
 * what lets the test pin the behaviour without a device.
 */
public final class SplashParticles {

    /** Default ember count. Enough to fill the frame, few enough to stay cheap on a weak GPU. */
    public static final int DEFAULT_COUNT = 42;

    /** Ember radius range, in dp, before the density scale is applied. */
    public static final float MIN_RADIUS_DP = 1.6f;
    public static final float MAX_RADIUS_DP = 5.4f;

    /** Rise speed range, in view-heights per second. Slow, so the field reads as ambient drift. */
    public static final float MIN_RISE = 0.035f;
    public static final float MAX_RISE = 0.12f;

    /** Horizontal sway amplitude range, in view-width fractions. */
    public static final float MIN_SWAY = 0.008f;
    public static final float MAX_SWAY = 0.03f;

    private final float[] x;
    private final float[] y;
    private final float[] radiusDp;
    private final float[] rise;
    private final float[] swayAmplitude;
    private final float[] swayPhase;
    private final float[] swaySpeed;
    private final float[] flickerPhase;
    private final float[] flickerSpeed;
    private final float[] baseAlpha;

    /** Elapsed scene time in seconds; the sway/flicker are functions of this, so they never drift. */
    private float time;

    public SplashParticles() {
        this(DEFAULT_COUNT);
    }

    public SplashParticles(int count) {
        int n = Math.max(1, count);
        x = new float[n];
        y = new float[n];
        radiusDp = new float[n];
        rise = new float[n];
        swayAmplitude = new float[n];
        swayPhase = new float[n];
        swaySpeed = new float[n];
        flickerPhase = new float[n];
        flickerSpeed = new float[n];
        baseAlpha = new float[n];
    }

    public int count() {
        return x.length;
    }

    public float x(int i) {
        return x[i];
    }

    /** The ember's y position, in view-height fractions; smaller is nearer the top. */
    public float y(int i) {
        return y[i];
    }

    public float radiusDp(int i) {
        return radiusDp[i];
    }

    public float alpha(int i) {
        float flicker = 0.65f + 0.35f * (float) Math.sin(flickerPhase[i] + time * flickerSpeed[i]);
        float value = baseAlpha[i] * flicker;
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** The ember's horizontal offset from its column, in view-width fractions. */
    public float swayOffset(int i) {
        return swayAmplitude[i] * (float) Math.sin(swayPhase[i] + time * swaySpeed[i]);
    }

    /**
     * Seeds a fresh field. Positions start spread over the whole height so the scene is already
     * populated on the first frame instead of building up from an empty screen.
     */
    public void seed(Random random, long seed) {
        random.setSeed(seed);
        for (int i = 0; i < x.length; i++) {
            x[i] = random.nextFloat();
            y[i] = random.nextFloat();
            radiusDp[i] = MIN_RADIUS_DP + random.nextFloat() * (MAX_RADIUS_DP - MIN_RADIUS_DP);
            rise[i] = MIN_RISE + random.nextFloat() * (MAX_RISE - MIN_RISE);
            swayAmplitude[i] = MIN_SWAY + random.nextFloat() * (MAX_SWAY - MIN_SWAY);
            swayPhase[i] = random.nextFloat() * (float) (Math.PI * 2.0);
            swaySpeed[i] = 0.3f + random.nextFloat() * 0.8f;
            flickerPhase[i] = random.nextFloat() * (float) (Math.PI * 2.0);
            flickerSpeed[i] = 0.8f + random.nextFloat() * 2.4f;
            baseAlpha[i] = 0.22f + random.nextFloat() * 0.5f;
        }
        time = 0f;
    }

    /**
     * Advances the field by {@code dtMs}. Embers rise, recycle off the top with a fresh column, and
     * wrap their horizontal position so a sway that carries one off an edge slides back in rather
     * than popping. Allocates nothing.
     */
    public void update(float dtMs) {
        if (dtMs <= 0f) return;
        float dt = dtMs / 1000f;
        time += dt;
        for (int i = 0; i < x.length; i++) {
            y[i] -= rise[i] * dt;
            if (y[i] < -0.06f) {
                // Recycled to the bottom. x is nudged by a golden-ratio step so a recycled ember
                // does not retrace the column it just left, which would read as a visible loop.
                y[i] = 1.06f;
                x[i] = wrap01(x[i] + 0.618034f);
            }
        }
    }

    /** The scene clock in seconds, exposed so a renderer can phase-lock any extra flourish. */
    public float time() {
        return time;
    }

    private static float wrap01(float value) {
        float v = value % 1f;
        return v < 0f ? v + 1f : v;
    }
}
