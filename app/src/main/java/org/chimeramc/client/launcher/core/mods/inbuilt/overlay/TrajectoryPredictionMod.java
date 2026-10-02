package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.Collections;
import java.util.List;

/**
 * State holder for the Trajectory Prediction module, and its held-item seam.
 *
 * <p>The arc itself is pure ({@link TrajectorySolver}); this class owns the one thing that needs
 * the game: which projectile the player is holding, and how far a bow is drawn. That is the
 * {@link HeldItemSource} seam. With no provider, {@link #readArc} returns an empty list and the
 * overlay draws nothing - never an arc for an unknown item, which the player might trust.
 *
 * <p>Scope note: predicting a projectile's flight from the player's aim needs only the aim and the
 * item, both local. Reading a projectile already in flight would need the native entity feed, and
 * is deliberately out of scope.
 */
public final class TrajectoryPredictionMod {

    /** The held item and draw charge, as reported by the game. */
    public static final class HeldItem {
        /** Full item id, e.g. {@code minecraft:ender_pearl}; null when nothing is held. */
        public final String itemName;
        /** Bow draw, 0..1; ignored for projectiles with no charge bonus. */
        public final float charge;

        public HeldItem(String itemName, float charge) {
            this.itemName = itemName;
            this.charge = charge;
        }
    }

    /** Supplies the currently held item; null when no game feed is installed. */
    public interface HeldItemSource {
        HeldItem read();
    }

    private static volatile HeldItemSource heldItemSource;
    private static volatile boolean active;
    private static volatile int color = 0xFF7CFC5A;

    private TrajectoryPredictionMod() {}

    public static void setHeldItemSource(HeldItemSource source) {
        heldItemSource = source;
    }

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            color = manager.getTrajectoryColor();
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        color = manager.getTrajectoryColor();
    }

    public static boolean isActive() {
        return active;
    }

    public static int getColor() {
        return color;
    }

    /** True when the module is on but no held-item feed exists, so nothing can be drawn. */
    public static boolean isAwaitingGameData() {
        return active && heldItemSource == null;
    }

    /**
     * The predicted arc for the current aim, or an empty list when nothing can be predicted.
     *
     * <p>Empty rather than null so the overlay iterates unconditionally; empty is also the
     * "held item is not a projectile" answer, which is the common case and must draw nothing.
     */
    public static List<TrajectorySolver.Sample> readArc(HitboxProjector.Camera camera,
                                                        int maxSamples) {
        if (!active || camera == null) return Collections.emptyList();
        HeldItemSource source = heldItemSource;
        if (source == null) return Collections.emptyList();
        HeldItem held;
        try {
            held = source.read();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (held == null) return Collections.emptyList();
        TrajectorySolver.Projectile type = TrajectorySolver.forItem(held.itemName);
        if (type == null) return Collections.emptyList();
        return TrajectorySolver.predict(type, held.charge,
                camera.x, camera.y, camera.z, camera.yawDeg, camera.pitchDeg, maxSamples);
    }
}
