package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.Locale;

/**
 * Pure geometry for the Hit Prediction module.
 *
 * <p>Given a target's smoothed velocity and a look-ahead time, returns the ghost marker's world
 * position and whether the prediction is trustworthy. Two rules matter and both are the reason
 * this is a separate class rather than inline maths in the overlay:
 *
 * <ul>
 *   <li><b>Smoothed velocity, never the last frame.</b> The velocity handed in comes from
 *       {@link VelocitySmoother}'s five-frame window; using a single frame makes the marker
 *       wobble with packet jitter.</li>
 *   <li><b>Fail-hide when the target is moving fast.</b> Above {@link #UNRELIABLE_SPEED} the
 *       target is dashing or airborne and a straight-line extrapolation is worse than no
 *       marker, because the player would aim at it. The prediction returns null there.</li>
 * </ul>
 *
 * <p>Honest scope: this is a ghost box at a predicted position, not a hit registration. It
 * cannot make a hit land - the server decides that - and with no peer feed it draws nothing.
 */
public final class HitPredictor {

    /** Blocks per second above which a straight-line guess is not trustworthy. */
    public static final float UNRELIABLE_SPEED = VelocitySmoother.UNRELIABLE_SPEED;

    /** Bounds on the configurable look-ahead, in milliseconds. */
    public static final int MIN_LOOK_AHEAD_MS = 500;
    public static final int MAX_LOOK_AHEAD_MS = 1500;
    public static final int DEFAULT_LOOK_AHEAD_MS = 1000;

    /** A predicted marker position, plus the distance the target is expected to have moved. */
    public static final class Prediction {
        public final String peerId;
        public final float x, y, z;
        /** How far ahead of the current position the marker sits, in blocks. */
        public final float leadBlocks;

        Prediction(String peerId, float x, float y, float z, float leadBlocks) {
            this.peerId = peerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.leadBlocks = leadBlocks;
        }
    }

    private HitPredictor() {}

    /** Clamps a look-ahead into the supported range; a bad pref reads as the default. */
    public static int clampLookAheadMs(int ms) {
        if (ms <= 0) return DEFAULT_LOOK_AHEAD_MS;
        return Math.max(MIN_LOOK_AHEAD_MS, Math.min(MAX_LOOK_AHEAD_MS, ms));
    }

    /**
     * Predicts the marker for one target.
     *
     * @param velocity    the smoothed velocity in blocks per second, from {@link VelocitySmoother}
     * @param x/y/z       the target's current position
     * @param lookAheadMs how far ahead to project
     * @return the marker, or null when the target is too fast or the velocity is unknown
     */
    public static Prediction predict(String peerId, float[] velocity,
                                     float x, float y, float z, int lookAheadMs) {
        if (velocity == null || velocity.length < 3) return null;
        if (!isFinite(velocity[0]) || !isFinite(velocity[1]) || !isFinite(velocity[2])) return null;

        float speed = (float) Math.sqrt(
                velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2]);
        if (speed > UNRELIABLE_SPEED) return null;

        float seconds = clampLookAheadMs(lookAheadMs) / 1000f;
        float px = x + velocity[0] * seconds;
        float py = y + velocity[1] * seconds;
        float pz = z + velocity[2] * seconds;
        return new Prediction(peerId, px, py, pz, speed * seconds);
    }

    /** One decimal, US locale, matching the reach readout's number style. */
    public static String formatLead(float blocks) {
        return String.format(Locale.US, "%.1f", blocks);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
