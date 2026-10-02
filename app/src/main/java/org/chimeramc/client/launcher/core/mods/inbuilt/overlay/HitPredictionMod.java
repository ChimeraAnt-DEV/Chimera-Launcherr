package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * State holder for the Hit Prediction module.
 *
 * <p>Keeps one {@link VelocitySmoother} per peer and, each frame, turns the smoothed velocity
 * into a ghost marker via the pure {@link HitPredictor}. The per-peer state is the reason this is
 * a stateful holder rather than a pure call: velocity needs history, and history is per target.
 *
 * <p>Run state only - nothing here is persisted. Targets that stop being seen have their
 * smoother dropped, so a player who leaves and rejoins does not inherit a stale velocity.
 *
 * <p>Honest scope: the marker is a ghost box at a predicted position, never a hit. It cannot make
 * a hit land, and with no peer feed it draws nothing.
 */
public final class HitPredictionMod {

    private static final Map<String, VelocitySmoother> SMOOTHERS = new HashMap<>();

    private static volatile boolean active;
    private static volatile int lookAheadMs = HitPredictor.DEFAULT_LOOK_AHEAD_MS;

    private HitPredictionMod() {}

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            lookAheadMs = manager.getHitPredictionLookAheadMs();
        }
        SMOOTHERS.clear();
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        lookAheadMs = manager.getHitPredictionLookAheadMs();
    }

    public static boolean isActive() {
        return active;
    }

    public static int getLookAheadMs() {
        return lookAheadMs;
    }

    /**
     * Feeds the current peer positions into the per-peer smoothers.
     *
     * <p>Called once per frame from the overlay manager's tick, which is the only cadence that
     * matches how often the positions actually change. Smoothers for peers no longer visible are
     * dropped here.
     */
    public static void tick(long nowMs) {
        if (!active) {
            if (!SMOOTHERS.isEmpty()) SMOOTHERS.clear();
            return;
        }
        List<ReachIndicator.VoicePeerPosition> peers = PeerPositions.read();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ReachIndicator.VoicePeerPosition peer : peers) {
            seen.add(peer.id);
            VelocitySmoother smoother = SMOOTHERS.get(peer.id);
            if (smoother == null) {
                smoother = new VelocitySmoother();
                SMOOTHERS.put(peer.id, smoother);
            }
            smoother.add(peer.x, peer.y, peer.z, nowMs);
        }
        for (Iterator<Map.Entry<String, VelocitySmoother>> it = SMOOTHERS.entrySet().iterator();
             it.hasNext(); ) {
            if (!seen.contains(it.next().getKey())) it.remove();
        }
    }

    /**
     * The ghost markers to draw, or an empty list.
     *
     * <p>A peer with too little history, or moving faster than {@link HitPredictor}'s limit,
     * produces no marker rather than a guessed one.
     */
    public static List<HitPredictor.Prediction> readMarkers() {
        if (!active || SMOOTHERS.isEmpty()) return Collections.emptyList();
        List<HitPredictor.Prediction> out = new ArrayList<>();
        for (Map.Entry<String, VelocitySmoother> entry : SMOOTHERS.entrySet()) {
            VelocitySmoother smoother = entry.getValue();
            float[] latest = smoother.latest();
            if (latest == null) continue;
            HitPredictor.Prediction prediction = HitPredictor.predict(
                    entry.getKey(), smoother.velocity(),
                    latest[0], latest[1], latest[2], lookAheadMs);
            if (prediction != null) out.add(prediction);
        }
        return out;
    }

    /** Clears per-peer history; called when a session ends. */
    public static void reset() {
        SMOOTHERS.clear();
    }
}
