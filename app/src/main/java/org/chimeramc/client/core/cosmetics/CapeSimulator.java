package org.chimeramc.client.core.cosmetics;

/**
 * Verlet cloth simulation for the cape, sized to Minecraft's cape geometry.
 *
 * <p>A Minecraft cape is a 10x16 pixel quad: 0.625 blocks wide (10/16) and one block tall
 * (16/16), hanging from the shoulders. This simulates a grid of that shape so the cloth is the
 * right size on screen, not an arbitrary trapezoid.
 *
 * <p>The integration is the standard position-Verlet scheme with per-particle previous
 * positions. Verlet is chosen over a spring-mass solve because it is unconditionally cheap:
 * one add, one subtract and a couple of multiplies per particle, no matrix, no iteration
 * count that scales with stiffness. That is what keeps it lag-free — the cost is fixed per
 * frame regardless of how hard the cape is moving.
 *
 * <p>Everything is preallocated in the constructor and mutated in place. {@link #step} allocates
 * nothing, so a cape swinging every frame does not churn the heap and provoke a GC pause in the
 * middle of a fight.
 *
 * <p>Pure float maths with no Android types so the motion is unit-testable: a settled cape must
 * hang straight regardless of frame rate, and a cape that was pushed must come back to rest
 * rather than oscillating forever.
 */
public final class CapeSimulator {

    /** Grid resolution: enough columns that the hem curves, few enough to stay trivially cheap. */
    public static final int COLS = 5;
    public static final int ROWS = 7;

    /** Cape width in blocks, from Minecraft's 10-px cape texture (10/16). */
    public static final float WIDTH_BLOCKS = 10f / 16f;

    /** Cape height in blocks, from Minecraft's 16-px cape texture (16/16). */
    public static final float HEIGHT_BLOCKS = 1f;

    /**
     * Fixed timestep in seconds. Cloth blowing up is almost always a variable timestep feeding a
     * stiff integrator, so time is accumulated and consumed in fixed slices; a background frame
     * hitch cannot make the cape explode.
     */
    public static final float FIXED_DT = 1f / 60f;

    /** Cap on slices per call, so a long stall cannot spiral into a catch-up loop. */
    public static final int MAX_SUBSTEPS = 4;

    /** Gravity in blocks/s^2. Gentler than 9.8 so the cloth falls with a fabric's weight. */
    public static final float GRAVITY = 14f;

    /**
     * Velocity retained per second (air drag). Well below 1 so the cape settles quickly instead of
     * ringing: the old value of 0.86 left the cloth almost undamped, so a flick set it flapping for
     * seconds and a still character kept jittering — the "way too much physics" report. Fabric
     * loses energy fast, so the preview should too.
     */
    public static final float DAMPING = 0.22f;

    /**
     * How strongly a particle is pulled back to its anchor's rest offset, per second.
     *
     * <p>Raised with the damping so the cape returns to a clean hanging shape instead of drooping
     * and swinging. High stiffness alone would be stiff cloth; paired with strong drag it reads as
     * heavy fabric that moves a little and stops.
     */
    public static final float STIFFNESS = 52f;

    /** Maximum stretch of a structural link, as a fraction of its rest length. */
    public static final float MAX_STRETCH = 1.35f;

    private final float[] px = new float[COLS * ROWS];
    private final float[] py = new float[COLS * ROWS];
    private final float[] pz = new float[COLS * ROWS];
    private final float[] prevX = new float[COLS * ROWS];
    private final float[] prevY = new float[COLS * ROWS];
    private final float[] prevZ = new float[COLS * ROWS];
    private final float[] restX = new float[COLS * ROWS];
    private final float[] restY = new float[COLS * ROWS];
    private final float[] restZ = new float[COLS * ROWS];

    private float accumulator;
    private boolean initialised;

    /**
     * Anchor-space offset per particle: a point on the cape's rest quad relative to the top
     * centre (the shoulders). x spans the cape's width, y hangs negative downward.
     */
    private void computeRestOffsets() {
        for (int r = 0; r < ROWS; r++) {
            float v = r / (float) (ROWS - 1);
            for (int c = 0; c < COLS; c++) {
                float u = c / (float) (COLS - 1) - 0.5f;
                int i = r * COLS + c;
                restX[i] = u * WIDTH_BLOCKS;
                restY[i] = -v * HEIGHT_BLOCKS;
                restZ[i] = 0f;
            }
        }
    }

    /** Resets the cloth to its rest shape beneath the given anchor. */
    public void reset(float anchorX, float anchorY, float anchorZ) {
        computeRestOffsets();
        for (int i = 0; i < px.length; i++) {
            px[i] = anchorX + restX[i];
            py[i] = anchorY + restY[i];
            pz[i] = anchorZ + restZ[i];
            prevX[i] = px[i];
            prevY[i] = py[i];
            prevZ[i] = pz[i];
        }
        accumulator = 0f;
        initialised = true;
    }

    /**
     * Advances the cloth.
     *
     * @param dtSeconds      real elapsed time; consumed in fixed slices
     * @param anchorX/Y/Z    the shoulder point this frame, already moved by the body
     * @param forwardZ       the body's facing direction on the world Z axis
     * @param forwardX       the body's facing direction on the world X axis
     * @param speedBlocks    the body's speed, used to billow the cape behind it
     */
    public void step(float dtSeconds, float anchorX, float anchorY, float anchorZ,
                     float forwardX, float forwardZ, float speedBlocks) {
        if (!initialised) {
            reset(anchorX, anchorY, anchorZ);
            return;
        }
        // Clamp so a stall cannot bank more work than a few slices.
        accumulator += Math.min(dtSeconds, FIXED_DT * MAX_SUBSTEPS);
        int steps = 0;
        while (accumulator >= FIXED_DT && steps < MAX_SUBSTEPS) {
            integrate(FIXED_DT, anchorX, anchorY, anchorZ, forwardX, forwardZ, speedBlocks);
            accumulator -= FIXED_DT;
            steps++;
        }
        if (steps == MAX_SUBSTEPS) {
            accumulator = 0f;
        }
    }

    private void integrate(float dt, float anchorX, float anchorY, float anchorZ,
                           float forwardX, float forwardZ, float speedBlocks) {
        // Air drag as a per-step factor derived from the per-second damping, so the cloth's
        // settling time does not change with the timestep.
        float dampingPerStep = (float) Math.pow(DAMPING, dt);
        // The cape trails behind the direction of travel: the wind pushes opposite to forward.
        float windX = -forwardX * speedBlocks * 0.11f;
        float windZ = -forwardZ * speedBlocks * 0.11f;

        for (int i = 0; i < px.length; i++) {
            if (isAnchored(i)) {
                px[i] = anchorX + restX[i];
                py[i] = anchorY + restY[i];
                pz[i] = anchorZ + restZ[i];
                prevX[i] = px[i];
                prevY[i] = py[i];
                prevZ[i] = pz[i];
                continue;
            }

            float vx = (px[i] - prevX[i]) * dampingPerStep;
            float vy = (py[i] - prevY[i]) * dampingPerStep;
            float vz = (pz[i] - prevZ[i]) * dampingPerStep;

            prevX[i] = px[i];
            prevY[i] = py[i];
            prevZ[i] = pz[i];

            // Restoring pull toward the anchor-space rest pose plus gravity and wind.
            float pullX = (anchorX + restX[i] - px[i]) * STIFFNESS * dt;
            float pullY = (anchorY + restY[i] - py[i]) * STIFFNESS * dt;
            float pullZ = (anchorZ + restZ[i] - pz[i]) * STIFFNESS * dt;

            px[i] += vx + pullX + windX * dt;
            py[i] += vy + pullY - GRAVITY * dt * dt;
            pz[i] += vz + pullZ + windZ * dt;
        }

        // One relaxation pass over the structural links. A single pass is enough to stop the
        // cloth shearing apart and costs O(n), which is the point.
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = r * COLS + c;
                if (c + 1 < COLS) link(i, i + 1, MAX_STRETCH);
                if (r + 1 < ROWS) link(i, i + COLS, MAX_STRETCH);
            }
        }
    }

    /** The top row is pinned to the shoulders; everything below hangs. */
    private boolean isAnchored(int i) {
        return i < COLS;
    }

    /**
     * Stops a link stretching past {@code maxStretch} of its rest length by splitting the
     * correction between the two ends, but never moving an anchored particle.
     */
    private void link(int a, int b, float maxStretch) {
        float dx = px[b] - px[a];
        float dy = py[b] - py[a];
        float dz = pz[b] - pz[a];
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float restLen = restLen(a, b);
        float limit = restLen * maxStretch;
        if (dist <= limit || dist < 1e-5f) return;

        float correction = (dist - limit) / dist;
        boolean anchorA = isAnchored(a);
        boolean anchorB = isAnchored(b);
        if (anchorA && anchorB) return;
        float shareA = anchorA ? 0f : (anchorB ? 1f : 0.5f);
        float shareB = anchorB ? 0f : (anchorA ? 1f : 0.5f);
        px[a] += dx * correction * shareA;
        py[a] += dy * correction * shareA;
        pz[a] += dz * correction * shareA;
        px[b] -= dx * correction * shareB;
        py[b] -= dy * correction * shareB;
        pz[b] -= dz * correction * shareB;
    }

    private float restLen(int a, int b) {
        float dx = restX[b] - restX[a];
        float dy = restY[b] - restY[a];
        float dz = restZ[b] - restZ[a];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public int size() {
        return px.length;
    }

    public float x(int i) { return px[i]; }
    public float y(int i) { return py[i]; }
    public float z(int i) { return pz[i]; }

    /** Row index of a particle, for drawing the mesh in order. */
    public int rowOf(int i) { return i / COLS; }
    public int colOf(int i) { return i % COLS; }
}
