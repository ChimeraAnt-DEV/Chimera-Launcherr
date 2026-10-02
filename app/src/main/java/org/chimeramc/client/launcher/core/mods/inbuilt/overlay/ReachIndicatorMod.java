package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.List;

/**
 * State holder for the Reach Indicator module.
 *
 * <p>Reports the distance to the player under the crosshair, using the peer feed
 * ({@link PeerPositions}) as its target source and the same crosshair test the Hitboxes module
 * uses, so the number and the box agree. With no feed, or with the crosshair on nobody, the
 * reading is null and the overlay draws nothing.
 */
public final class ReachIndicatorMod {

    /** Placement of the caption. */
    public static final int POSITION_BELOW_CROSSHAIR = 0;
    public static final int POSITION_ABOVE_HOTBAR = 1;

    private static volatile boolean active;
    private static volatile int position = POSITION_BELOW_CROSSHAIR;

    private ReachIndicatorMod() {}

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            position = manager.getReachIndicatorPosition();
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        position = manager.getReachIndicatorPosition();
    }

    /** Separate from the manager overload so the placement is testable without a Context. */
    public static void applyConfig(int placement) {
        position = placement;
    }

    public static boolean isActive() {
        return active;
    }

    public static int getPosition() {
        return position;
    }

    /** True when the caption is anchored above the hotbar rather than under the crosshair. */
    public static boolean isAboveHotbar() {
        return position == POSITION_ABOVE_HOTBAR;
    }

    /**
     * The current reading, or null when there is nothing to show.
     *
     * <p>Null covers every no-data case (module off, no camera, no peer under the crosshair) so
     * the overlay has a single "draw nothing" path and cannot render a stale distance.
     */
    public static ReachIndicator.Reading read(HitboxProjector.Camera camera) {
        if (!active || camera == null) return null;
        List<ReachIndicator.VoicePeerPosition> peers = PeerPositions.read();
        if (peers.isEmpty()) return null;
        return ReachIndicator.read(camera, peers);
    }
}
