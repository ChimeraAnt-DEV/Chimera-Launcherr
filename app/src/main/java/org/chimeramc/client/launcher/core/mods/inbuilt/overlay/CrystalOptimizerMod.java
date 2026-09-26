package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Crystal Optimizer state and its game-data seam.
 *
 * <p>Two distinct halves, deliberately separable:
 *
 * <ul>
 *   <li><b>Decision</b> — {@link CrystalPlacementSolver} ranks candidate placement spots. Pure
 *       geometry, fully unit tested, no game access.</li>
 *   <li><b>Data</b> — the player's position/health, the target's position, and the nearby
 *       obsidian/bedrock blocks come from a {@link WorldSource}. Reading those, and actually
 *       placing and detonating a crystal, need the game process and are not assumed here.</li>
 * </ul>
 *
 * <p>{@link #isManualAssist()} defaults to true and is the safe mode: the module highlights the
 * best spot and never issues an action itself. Automatic placement exists only as an explicit
 * opt-in, because a client that places and breaks blocks for the player is exactly the kind of
 * automation servers restrict or ban.
 */
public final class CrystalOptimizerMod {
    private static volatile WorldSource worldSource;
    private static volatile boolean active;
    private static volatile boolean manualAssist = true;
    private static volatile int minSelfHp = 14;
    private static volatile int maxRange = 4;
    private static volatile int placementDelayMs = 50;

    private CrystalOptimizerMod() {}

    /** Supplies the geometry the solver needs, or {@code null} when it cannot be read. */
    public interface WorldSource {
        /**
         * @return the current world snapshot, or {@code null} if unavailable.
         */
        World read();
    }

    /** Player + target + candidate blocks at one instant. */
    public static final class World {
        public final float playerX;
        public final float playerY;
        public final float playerZ;
        public final float selfHp;
        public final float targetX;
        public final float targetY;
        public final float targetZ;
        public final java.util.List<CrystalPlacementSolver.BaseBlock> blocks;

        public World(float playerX, float playerY, float playerZ, float selfHp,
                     float targetX, float targetY, float targetZ,
                     java.util.List<CrystalPlacementSolver.BaseBlock> blocks) {
            this.playerX = playerX;
            this.playerY = playerY;
            this.playerZ = playerZ;
            this.selfHp = selfHp;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.blocks = blocks;
        }
    }

    public static void setWorldSource(WorldSource source) {
        worldSource = source;
    }

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            manualAssist = manager.isCrystalManualAssist();
            minSelfHp = manager.getCrystalMinSelfHp();
            maxRange = manager.getCrystalMaxRange();
            placementDelayMs = manager.getCrystalPlacementDelayMs();
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        manualAssist = manager.isCrystalManualAssist();
        minSelfHp = manager.getCrystalMinSelfHp();
        maxRange = manager.getCrystalMaxRange();
        placementDelayMs = manager.getCrystalPlacementDelayMs();
    }

    public static boolean isActive() {
        return active;
    }

    /** True while the module will only mark the spot and never act on its own. */
    public static boolean isManualAssist() {
        return manualAssist;
    }

    public static int getMinSelfHp() {
        return minSelfHp;
    }

    public static int getMaxRange() {
        return maxRange;
    }

    public static int getPlacementDelayMs() {
        return placementDelayMs;
    }

    /**
     * True when the module is on but no world feed is installed, so no spot can ever be found.
     *
     * <p>Without this the readout said "no safe spot", which blames the aimed position for a
     * state that is really "the data source does not exist yet". Those are different failures,
     * and only one of them is the player's to fix.
     */
    public static boolean isAwaitingGameData() {
        return active && worldSource == null;
    }

    /**
     * Evaluates the current world and returns the best placement, or {@code null} when there is
     * no usable spot (no data, no target, out of range, or the trade would cost too much health).
     */
    public static CrystalPlacementSolver.Candidate evaluate() {
        WorldSource source = worldSource;
        if (!active || source == null) return null;
        World world;
        try {
            world = source.read();
        } catch (Throwable t) {
            return null;
        }
        if (world == null) return null;

        CrystalPlacementSolver.Candidate candidate = CrystalPlacementSolver.findBest(
                world.playerX, world.playerY, world.playerZ, world.selfHp,
                world.targetX, world.targetY, world.targetZ,
                minSelfHp, maxRange, world.blocks);
        if (candidate == null) return null;
        if (!CrystalPlacementSolver.isSafeStandoff(candidate.distanceToSelf)) return null;
        return candidate;
    }
}
