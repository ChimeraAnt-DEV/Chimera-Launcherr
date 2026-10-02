package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.Locale;

/**
 * Pure decision logic for the Reach Indicator module.
 *
 * <p>Answers one question: for the player's camera and a set of peer positions, which peer is
 * under the crosshair, and how far away is it? The overlay draws that distance as a small
 * caption under the crosshair, and hides the caption when nothing is targeted.
 *
 * <p>The target test reuses {@link HitboxProjector#crosshairHits}, so the number shown and the
 * box the Hitboxes module draws cannot disagree about what is being aimed at. Nearest-target
 * wins when the ray clips more than one box, because that is the one a hit would land on.
 *
 * <p>Honest scope: peers are the only entities this build can place (their positions arrive over
 * the proximity-voice protocol). A vanilla player or a mob under the crosshair produces no
 * reading, and the module draws nothing rather than a guessed distance - the same fail-hide rule
 * the rest of the suite follows.
 */
public final class ReachIndicator {

    private ReachIndicator() {}

    /** A target and its distance, in blocks. */
    public static final class Reading {
        public final String peerId;
        public final String name;
        public final float distanceBlocks;

        Reading(String peerId, String name, float distanceBlocks) {
            this.peerId = peerId;
            this.name = name;
            this.distanceBlocks = distanceBlocks;
        }

        /** One decimal, US locale so a comma cannot make "3,2 blocks" read as two numbers. */
        public String format() {
            return String.format(Locale.US, "%.1f blocks", distanceBlocks);
        }
    }

    /**
     * The nearest peer the crosshair is on, or null when it is on nobody.
     *
     * <p>Returns null (rather than a zero reading) whenever there is no camera or no peer feed,
     * so the overlay hides the caption. A "0.0 blocks" line would read as a real measurement of
     * an adjacent player.
     */
    public static Reading read(HitboxProjector.Camera camera, Iterable<VoicePeerPosition> peers) {
        if (camera == null || peers == null) return null;
        float[] forward = camera.forward();
        if (forward == null) return null;

        Reading best = null;
        for (VoicePeerPosition peer : peers) {
            if (peer == null) continue;
            if (!isFinite(peer.x) || !isFinite(peer.y) || !isFinite(peer.z)) continue;
            HitboxProjector.Entity entity =
                    HitboxProjector.Entity.player(peer.x, peer.y, peer.z);
            if (!HitboxProjector.crosshairHits(entity, camera, forward)) continue;

            // Distance to the peer's centre, which is what a player means by "how far away".
            float dy = (peer.y + entity.height / 2f) - camera.y;
            float distance = (float) Math.sqrt(
                    sq(peer.x - camera.x) + sq(dy) + sq(peer.z - camera.z));
            if (best == null || distance < best.distanceBlocks) {
                best = new Reading(peer.id, peer.name, distance);
            }
        }
        return best;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float sq(float value) {
        return value * value;
    }

    /**
     * The minimal peer shape this module needs, so it does not depend on the voice package.
     *
     * <p>Declared here rather than reusing {@code VoicePeer} directly so the reach rule stays
     * unit-testable without constructing a voice peer, and so the module's seam is explicit.
     */
    public static final class VoicePeerPosition {
        public final String id;
        public final String name;
        public final float x, y, z;

        public VoicePeerPosition(String id, String name, float x, float y, float z) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
