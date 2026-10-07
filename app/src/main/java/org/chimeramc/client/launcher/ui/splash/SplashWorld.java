package org.chimeramc.client.launcher.ui.splash;

import java.util.Random;

/**
 * The splash backdrop's Minecraft-inspired world, as pure arithmetic.
 *
 * <p>The old backdrop was a gradient with a few embers, which still reads as a "loading" screen
 * rather than a place. This models a small voxel world — a twinkling starfield, a blocky terrain
 * silhouette on two parallax layers, floating cubes that bob and turn, and the occasional shooting
 * star — so the splash looks like a Minecraft scene that happens to be warming up, not a spinner.
 *
 * <p>Everything animated is a function of {@link #time()} or advanced by {@link #update(float)},
 * and every accessor is a plain primitive read, so the whole scene is unit-testable on a JVM where
 * no {@code Canvas} exists. Storage is parallel primitive arrays: this runs every frame for the
 * whole splash, and allocating an object per star or cube would hand the collector a job on every
 * frame. {@link #update} allocates nothing.
 *
 * <p>Deterministic when seeded, so a motion change shows up as a failing number in
 * {@code SplashWorldTest} rather than a shrug on a phone.
 */
public final class SplashWorld {

    /** Star count across both parallax layers. */
    public static final int STAR_COUNT = 96;
    /** Floating voxel cubes. Few enough to stay cheap; enough to fill the frame with life. */
    public static final int BLOCK_COUNT = 11;
    /** Columns per terrain layer. The silhouette is coarse on purpose — it is a voxel world. */
    public static final int TERRAIN_COLUMNS = 40;

    /** Stars are seeded into the upper sky; the terrain owns the lower band. */
    public static final float STAR_BAND = 0.66f;

    /** How long a shooting star lives, and how often one is spawned. */
    public static final float SHOOTING_STAR_LIFE_MS = 900f;
    private static final float SHOOTING_STAR_MIN_GAP_MS = 2600f;
    private static final float SHOOTING_STAR_MAX_GAP_MS = 6200f;

    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starRadiusDp = new float[STAR_COUNT];
    private final float[] starPhase = new float[STAR_COUNT];
    private final float[] starSpeed = new float[STAR_COUNT];
    private final float[] starBaseAlpha = new float[STAR_COUNT];
    /** 0 = far (dim, slow parallax), 1 = near (bright, faster parallax). */
    private final int[] starLayer = new int[STAR_COUNT];

    /** Per-layer column heights, in view-height fractions measured up from the bottom edge. */
    private final float[][] terrainHeights = new float[2][TERRAIN_COLUMNS];
    /** Per-layer horizontal drift, in view-width fractions per second. */
    private final float[] terrainDrift = new float[2];

    private final float[] blockX = new float[BLOCK_COUNT];
    private final float[] blockY = new float[BLOCK_COUNT];
    private final float[] blockSizeDp = new float[BLOCK_COUNT];
    private final float[] blockBobPhase = new float[BLOCK_COUNT];
    private final float[] blockBobSpeed = new float[BLOCK_COUNT];
    private final float[] blockBobAmp = new float[BLOCK_COUNT];
    private final float[] blockRotPhase = new float[BLOCK_COUNT];
    private final float[] blockRotSpeed = new float[BLOCK_COUNT];
    private final float[] blockDriftX = new float[BLOCK_COUNT];
    /** 0 = back (smaller, dimmer), 1 = front (larger, brighter). */
    private final int[] blockLayer = new int[BLOCK_COUNT];

    private boolean shootingStarActive;
    private float shootingStarX;
    private float shootingStarY;
    private float shootingStarVx;
    private float shootingStarVy;
    private float shootingStarLifeMs;
    private float shootingStarGapMs;

    private float time;
    private float terrainScroll;
    private final Random random = new Random();

    public int starCount() {
        return STAR_COUNT;
    }

    public float starX(int i) {
        return starX[i];
    }

    public float starY(int i) {
        return starY[i];
    }

    public float starRadiusDp(int i) {
        return starRadiusDp[i];
    }

    public int starLayer(int i) {
        return starLayer[i];
    }

    /** Twinkling alpha in {@code [0,1]}, a function of scene time so it never drifts. */
    public float starAlpha(int i) {
        float twinkle = 0.55f + 0.45f * (float) Math.sin(starPhase[i] + time * starSpeed[i]);
        float value = starBaseAlpha[i] * twinkle;
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** The parallax offset for a star layer, in view-width fractions; wraps into {@code [-1,1]}. */
    public float starParallax(int layer) {
        float speed = layer == 0 ? 0.006f : 0.016f;
        return wrapSigned(time * speed);
    }

    public int blockCount() {
        return BLOCK_COUNT;
    }

    public float blockSizeDp(int i) {
        return blockSizeDp[i];
    }

    public int blockLayer(int i) {
        return blockLayer[i];
    }

    /** The cube's current x, in view-width fractions, including its slow drift and parallax. */
    public float blockX(int i) {
        return wrap01(blockX[i] + time * blockDriftX[i]
                + starParallax(blockLayer[i]) * (blockLayer[i] == 0 ? 0.5f : 1f));
    }

    /** The cube's current y, in view-height fractions, including its bob. */
    public float blockY(int i) {
        return blockY[i] + blockBobAmp[i]
                * (float) Math.sin(blockBobPhase[i] + time * blockBobSpeed[i]);
    }

    /** The cube's spin in degrees, a function of scene time. */
    public float blockRotation(int i) {
        return blockRotPhase[i] + time * blockRotSpeed[i];
    }

    public boolean shootingStarActive() {
        return shootingStarActive;
    }

    public float shootingStarX() {
        return shootingStarX;
    }

    public float shootingStarY() {
        return shootingStarY;
    }

    /** Fades in then out over the star's life, so it never pops on or off. */
    public float shootingStarAlpha() {
        if (!shootingStarActive) return 0f;
        float t = 1f - clamp01(shootingStarLifeMs / SHOOTING_STAR_LIFE_MS);
        return (float) Math.sin(t * Math.PI);
    }

    /** The trail length in view-width fractions, shrinking as the star burns out. */
    public float shootingStarTrail() {
        if (!shootingStarActive) return 0f;
        float t = 1f - clamp01(shootingStarLifeMs / SHOOTING_STAR_LIFE_MS);
        return 0.14f * (1f - 0.5f * t);
    }

    public float time() {
        return time;
    }

    /** The terrain layer's horizontal drift in view-width fractions; small, for parallax. */
    public float terrainOffset(int layer) {
        return terrainScroll * terrainDrift[layer];
    }

    /** A terrain column's height in view-height fractions, measured up from the bottom edge. */
    public float terrainHeight(int layer, int column) {
        if (layer < 0 || layer > 1) return 0f;
        int c = ((column % TERRAIN_COLUMNS) + TERRAIN_COLUMNS) % TERRAIN_COLUMNS;
        return terrainHeights[layer][c];
    }

    /**
     * Seeds a fresh world. Stars and cubes are spread over the frame so the scene is already
     * populated on the first frame instead of building up from empty.
     */
    public void seed(Random rng, long seed) {
        rng.setSeed(seed);
        random.setSeed(seed ^ 0x9E3779B97F4A7C15L);

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rng.nextFloat();
            starY[i] = rng.nextFloat() * STAR_BAND;
            starLayer[i] = i % 3 == 0 ? 1 : 0;
            starRadiusDp[i] = starLayer[i] == 1
                    ? 0.9f + rng.nextFloat() * 1.1f
                    : 0.6f + rng.nextFloat() * 0.7f;
            starPhase[i] = rng.nextFloat() * (float) (Math.PI * 2.0);
            starSpeed[i] = 0.6f + rng.nextFloat() * 2.2f;
            starBaseAlpha[i] = starLayer[i] == 1
                    ? 0.5f + rng.nextFloat() * 0.5f
                    : 0.2f + rng.nextFloat() * 0.4f;
        }

        // Two terrain layers: a low rolling far ridge and a taller near ridge, so the silhouette
        // reads as depth rather than one flat skyline.
        for (int layer = 0; layer < 2; layer++) {
            float base = layer == 0 ? 0.10f : 0.055f;
            float amp = layer == 0 ? 0.07f : 0.05f;
            float phase = layer == 0 ? 0f : 1.7f;
            for (int c = 0; c < TERRAIN_COLUMNS; c++) {
                float u = c / (float) TERRAIN_COLUMNS;
                float wave = (float) Math.sin(u * Math.PI * 4f + phase) * 0.5f
                        + (float) Math.sin(u * Math.PI * 9f + phase * 2f) * 0.5f;
                terrainHeights[layer][c] = clampRange(base + amp * (wave + 1f) * 0.5f, 0.02f, 0.30f);
            }
        }
        terrainDrift[0] = -0.010f;
        terrainDrift[1] = 0.016f;

        for (int i = 0; i < BLOCK_COUNT; i++) {
            blockLayer[i] = i % 3 == 0 ? 1 : 0;
            blockX[i] = rng.nextFloat();
            blockY[i] = 0.18f + rng.nextFloat() * 0.52f;
            blockSizeDp[i] = blockLayer[i] == 1
                    ? 16f + rng.nextFloat() * 14f
                    : 9f + rng.nextFloat() * 9f;
            blockBobPhase[i] = rng.nextFloat() * (float) (Math.PI * 2.0);
            blockBobSpeed[i] = 0.5f + rng.nextFloat() * 0.9f;
            blockBobAmp[i] = 0.012f + rng.nextFloat() * 0.026f;
            blockRotPhase[i] = rng.nextFloat() * 360f;
            blockRotSpeed[i] = (rng.nextFloat() - 0.5f) * 26f;
            blockDriftX[i] = (rng.nextFloat() - 0.5f) * 0.006f;
        }

        time = 0f;
        terrainScroll = 0f;
        shootingStarActive = false;
        shootingStarGapMs = 1400f + rng.nextFloat() * 1800f;
    }

    /**
     * Advances the world by {@code dtMs}. Stars and cubes are functions of the clock, so only the
     * clock, the terrain scroll and the shooting star advance here. Allocates nothing.
     */
    public void update(float dtMs) {
        if (dtMs <= 0f) return;
        float dt = dtMs / 1000f;
        time += dt;
        terrainScroll += dt * 0.02f;

        if (shootingStarActive) {
            shootingStarLifeMs -= dtMs;
            shootingStarX += shootingStarVx * dt;
            shootingStarY += shootingStarVy * dt;
            if (shootingStarLifeMs <= 0f
                    || shootingStarX < -0.2f || shootingStarX > 1.2f
                    || shootingStarY < -0.1f || shootingStarY > 1.1f) {
                shootingStarActive = false;
                shootingStarGapMs = SHOOTING_STAR_MIN_GAP_MS
                        + random.nextFloat() * (SHOOTING_STAR_MAX_GAP_MS - SHOOTING_STAR_MIN_GAP_MS);
            }
        } else {
            shootingStarGapMs -= dtMs;
            if (shootingStarGapMs <= 0f) spawnShootingStar();
        }
    }

    private void spawnShootingStar() {
        shootingStarActive = true;
        shootingStarLifeMs = SHOOTING_STAR_LIFE_MS;
        // Enters from the upper area and streaks down-right at a shallow angle, the way a meteor
        // reads on screen; the speed is chosen so it crosses the visible sky within its lifetime.
        shootingStarX = 0.05f + random.nextFloat() * 0.45f;
        shootingStarY = 0.04f + random.nextFloat() * 0.18f;
        float speed = 0.55f + random.nextFloat() * 0.35f;
        float angle = (float) Math.toRadians(16f + random.nextFloat() * 12f);
        shootingStarVx = (float) (Math.cos(angle) * speed);
        shootingStarVy = (float) (Math.sin(angle) * speed);
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        return value > 1f ? 1f : value;
    }

    private static float clampRange(float value, float min, float max) {
        if (value < min) return min;
        return value > max ? max : value;
    }

    private static float wrap01(float value) {
        float v = value % 1f;
        return v < 0f ? v + 1f : v;
    }

    private static float wrapSigned(float value) {
        float v = wrap01(value);
        return v > 0.5f ? v - 1f : v;
    }
}
