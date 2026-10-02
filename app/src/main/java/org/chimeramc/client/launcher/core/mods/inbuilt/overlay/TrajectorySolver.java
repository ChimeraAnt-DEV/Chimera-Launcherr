package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure ballistic prediction for the Trajectory Prediction module.
 *
 * <p>Given a launch point, a heading and the projectile being held, integrates the same
 * per-tick model the game uses (velocity in blocks per tick, gravity per tick, a drag factor
 * applied each tick) and returns the sampled arc. The overlay draws the samples as a dotted
 * line; nothing here touches Android or the game, so the flight rules are unit-testable.
 *
 * <p><b>Per-projectile physics, not one curve for all three.</b> An arrow drops fastest and
 * flies furthest at full draw; an ender pearl travels nearly straight with only a slight drop;
 * a snowball sits between the two. Modelling them identically was the thing to avoid - a
 * single gravity would put the pearl's arc visibly under the ground and the arrow's above it.
 *
 * <p>Honest scope: this predicts where a projectile <em>would</em> go from the player's own
 * aim. It does not read a projectile already in flight (that would need the native entity feed
 * the Hitboxes module's seam is for), and with no held-projectile reading the overlay draws
 * nothing rather than a guessed arc.
 */
public final class TrajectorySolver {

    /** The game's tick rate; the integrator steps in ticks so the model matches the server. */
    public static final float TICKS_PER_SECOND = 20f;

    /** How far the arc is followed before it is abandoned, in blocks. */
    public static final float MAX_RANGE = 96f;

    /** Distance below the world floor at which the arc is treated as landed. */
    private static final float GROUND_Y = 0f;

    /** A held item the module knows how to predict. */
    public enum Projectile {
        /** Bow/crossbow arrow. Speed scales with draw; drops fastest. */
        ARROW(3.0f, 0.05f, 0.99f, 0.6f),
        /** Ender pearl: nearly straight, only a slight drop. */
        ENDER_PEARL(1.5f, 0.03f, 0.99f, 0f),
        /** Snowball / egg: a medium arc between the two. */
        SNOWBALL(1.5f, 0.045f, 0.99f, 0f);

        /** Launch speed in blocks per tick at full charge. */
        public final float speed;
        /** Downward acceleration in blocks per tick, per tick. */
        public final float gravity;
        /** Velocity retained each tick. */
        public final float drag;
        /** Extra speed the item gains across a 0..1 charge; 0 when charge does not apply. */
        public final float chargeBonus;

        Projectile(float speed, float gravity, float drag, float chargeBonus) {
            this.speed = speed;
            this.gravity = gravity;
            this.drag = drag;
            this.chargeBonus = chargeBonus;
        }
    }

    /** One point on the predicted arc, in world coordinates. */
    public static final class Sample {
        public final float x, y, z;
        /** Seconds since launch, for spacing the dotted line. */
        public final float timeSeconds;

        Sample(float x, float y, float z, float timeSeconds) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timeSeconds = timeSeconds;
        }
    }

    private TrajectorySolver() {}

    /**
     * The projectile a held item name maps to, or null when the module cannot predict it.
     *
     * <p>Names are matched loosely (the game reports a full item id such as
     * {@code minecraft:ender_pearl}) so a caller can pass whatever the seam gives it.
     */
    public static Projectile forItem(String itemName) {
        if (itemName == null) return null;
        String name = itemName.toLowerCase(Locale.US);
        if (name.contains("arrow")) return Projectile.ARROW;
        if (name.contains("ender_pearl") || name.contains("pearl")) return Projectile.ENDER_PEARL;
        if (name.contains("snowball") || name.contains("egg")) return Projectile.SNOWBALL;
        return null;
    }

    /**
     * Integrates the arc.
     *
     * @param type      the held projectile; null yields an empty arc
     * @param charge    0..1 draw, applied only to projectiles with a charge bonus
     * @param originX/Y/Z the launch point (the player's eye)
     * @param yawDeg    the player's heading, Minecraft convention (clockwise from +Z)
     * @param pitchDeg  the player's pitch, positive looking up
     * @param maxSamples upper bound on the returned points
     */
    public static List<Sample> predict(Projectile type, float charge,
                                       float originX, float originY, float originZ,
                                       float yawDeg, float pitchDeg, int maxSamples) {
        if (type == null || maxSamples <= 0) return Collections.emptyList();
        float clampedCharge = Math.max(0f, Math.min(1f, charge));
        float speed = type.speed + type.chargeBonus * clampedCharge;

        float[] forward = HitboxProjector.forwardFrom(yawDeg, pitchDeg);
        // Velocity is held in blocks per tick, matching the game's integrator.
        float vx = forward[0] * speed;
        float vy = forward[1] * speed;
        float vz = forward[2] * speed;

        float x = originX;
        float y = originY;
        float z = originZ;

        List<Sample> samples = new ArrayList<>(Math.min(maxSamples, 64));
        samples.add(new Sample(x, y, z, 0f));

        float travelled = 0f;
        int ticks = 0;
        int maxTicks = maxSamples * 2;
        while (samples.size() < maxSamples && ticks < maxTicks) {
            float prevX = x, prevY = y, prevZ = z;
            x += vx;
            y += vy;
            z += vz;
            vy -= type.gravity;
            vx *= type.drag;
            vy *= type.drag;
            vz *= type.drag;
            ticks++;

            travelled += distance(prevX, prevY, prevZ, x, y, z);
            // Sample every other tick so the dotted line is evenly spaced without being dense.
            if (ticks % 2 == 0) {
                samples.add(new Sample(x, y, z, ticks / TICKS_PER_SECOND));
            }
            if (y <= GROUND_Y || travelled > MAX_RANGE) break;
        }
        return samples;
    }

    private static float distance(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * One decimal place, locale-stable.
     *
     * <p>US locale is fixed on purpose: a comma decimal separator would render "3,2 blocks",
     * which reads as a list of two numbers.
     */
    public static String formatDistance(float blocks) {
        return String.format(Locale.US, "%.1f blocks", blocks);
    }
}
