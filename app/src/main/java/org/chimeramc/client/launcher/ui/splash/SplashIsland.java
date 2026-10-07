package org.chimeramc.client.launcher.ui.splash;

import java.util.Random;

/**
 * A floating voxel island for the splash backdrop, as pure geometry.
 *
 * <p>The existing splash already draws a voxel sky, terrain and a few floating cubes, but a
 * handful of loose cubes still reads as decoration. A <em>floating island</em> — a grass-capped
 * disc of dirt tapering to a stone point, with a tree on top and a couple of broken-off rocks
 * drifting beside it — is the single most recognisable Minecraft silhouette there is, so it is the
 * backdrop's hero.
 *
 * <p>The model is grid-aligned integer blocks plus a block kind, so the view can project and paint
 * it with the same software 3D pass it already uses for the cubes. It is deliberately Android-free
 * and deterministic (seeded), so the shape — the grass disc, the tapered underside, the tree, the
 * drifting rocks — is a JVM test rather than something only a phone can show.
 */
public final class SplashIsland {

    /** Block materials, each with its own face colours in the view. */
    public static final int KIND_GRASS = 0;
    public static final int KIND_DIRT = 1;
    public static final int KIND_STONE = 2;
    public static final int KIND_LOG = 3;
    public static final int KIND_LEAVES = 4;

    /** How fast the island turns, in degrees per second. Slow enough to read as a slow orbit. */
    public static final float SPIN_DEG_PER_SEC = 7.5f;

    /** Upper bound on island blocks, so the view can size its scratch arrays once. */
    public static final int MAX_BLOCKS = 512;

    private final int[] x = new int[MAX_BLOCKS];
    private final int[] y = new int[MAX_BLOCKS];
    private final int[] z = new int[MAX_BLOCKS];
    private final int[] kind = new int[MAX_BLOCKS];
    private int count;

    public int blockCount() {
        return count;
    }

    public int x(int i) {
        return x[i];
    }

    public int y(int i) {
        return y[i];
    }

    public int z(int i) {
        return z[i];
    }

    public int kind(int i) {
        return kind[i];
    }

    /** The island's yaw at scene time {@code t} seconds, in degrees. */
    public float yaw(float t) {
        return (t * SPIN_DEG_PER_SEC) % 360f;
    }

    /**
     * Builds a fresh island. Deterministic for a given seed, so the silhouette is stable across
     * launches (a splash that reshapes itself every time reads as noise, not as a place).
     */
    public void seed(Random rng, long seed) {
        rng.setSeed(seed);
        count = 0;

        // Grass cap: a filled disc of radius 3, with a few edge blocks eroded so the rim is not a
        // perfect circle.
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) continue;
                if (d2 == radius * radius && rng.nextFloat() < 0.45f) continue; // eroded rim
                add(dx, 0, dz, KIND_GRASS);
            }
        }
        // Underside: dirt tapering in two steps, then a stone point, so the island has weight and
        // reads as torn out of the ground rather than a floating carpet.
        fillDisc(rng, 2, -1, KIND_DIRT);
        fillDisc(rng, 1, -2, KIND_STONE);
        add(0, -3, 0, KIND_STONE);
        add(0, -4, 0, KIND_STONE);

        // A tree: a three-block trunk with a small leaf canopy, the classic island landmark.
        add(0, 1, 0, KIND_LOG);
        add(0, 2, 0, KIND_LOG);
        add(0, 3, 0, KIND_LOG);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1) continue; // trim corners
                add(dx, 4, dz, KIND_LEAVES);
            }
        }
        add(0, 5, 0, KIND_LEAVES);
        add(0, 4, 0, KIND_LEAVES);

        // Two broken-off rocks drifting just off the rim, so the island is not alone in the sky.
        add(4, 1, -3, KIND_STONE);
        add(5, 1, -3, KIND_STONE);
        add(4, 2, -3, KIND_DIRT);
        add(-5, 2, 2, KIND_STONE);
        add(-4, 2, 2, KIND_STONE);
    }

    /** Fills a disc of the given radius at height {@code yy}, with a deterministic eroded rim. */
    private void fillDisc(Random rng, int radius, int yy, int material) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) continue;
                if (d2 == radius * radius && rng.nextFloat() < 0.4f) continue;
                add(dx, yy, dz, material);
            }
        }
    }

    private void add(int bx, int by, int bz, int material) {
        if (count >= MAX_BLOCKS) return;
        x[count] = bx;
        y[count] = by;
        z[count] = bz;
        kind[count] = material;
        count++;
    }
}
