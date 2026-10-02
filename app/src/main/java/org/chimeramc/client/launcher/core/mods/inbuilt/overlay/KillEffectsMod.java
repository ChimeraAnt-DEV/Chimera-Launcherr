package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * State holder for the Custom Kill Effects module, and its death seam.
 *
 * <p>Credit for a kill is inferred by {@link KillCreditRegistry}: a hit you landed within the last
 * two seconds followed by that player's death is credited to you. The registry is pure; this class
 * owns the seam that tells it when a player has died, and the on-screen particles the credit
 * triggers.
 *
 * <p>The particle state is intentionally tiny and self-contained: a short-lived list of bursts,
 * each with a birth time, a world position and a style. The overlay advances them by elapsed time
 * so a dropped frame cannot make a burst jump, and {@link #activeBursts} returns an empty array
 * when nothing is alive so an idle screen draws no particles at all.
 *
 * <p>Honest scope: this is a client-side visual. It does not damage anyone, and it cannot see a
 * server-confirmed kill - a player who dies to someone else, or to the world, is never credited.
 */
public final class KillEffectsMod {

    /** Particle styles, index-matched to the config's choice list. */
    public static final int STYLE_BURST = 0;
    public static final int STYLE_COLUMN = 1;
    public static final int STYLE_RING = 2;

    /** How long a burst lives, in milliseconds. */
    public static final long BURST_DURATION_MS = 1200L;

    /** The most bursts alive at once; a busy fight must not grow the list without bound. */
    public static final int MAX_BURSTS = 8;

    /** Supplies deaths; null when no game feed is installed. */
    public interface KillSource {
        /**
         * Returns the id of a player seen to die since the last call, or null when none did.
         *
         * <p>Polled once per frame. Returning a name rather than a full entity keeps the seam
         * minimal and lets the registry do the credit decision.
         */
        String pollDeath();
    }

    /** One live particle burst. */
    public static final class Burst {
        public final float x, y, z;
        public final long bornMs;
        public final int style;
        public final int color;

        Burst(float x, float y, float z, long bornMs, int style, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bornMs = bornMs;
            this.style = style;
            this.color = color;
        }

        /** 0..1 life fraction; 1 when fully spent. */
        public float progress(long nowMs) {
            float p = (nowMs - bornMs) / (float) BURST_DURATION_MS;
            return Math.max(0f, Math.min(1f, p));
        }
    }

    private static final java.util.List<Burst> BURSTS = new java.util.ArrayList<>();
    private static final KillCreditRegistry REGISTRY = new KillCreditRegistry();

    private static volatile KillSource killSource;
    private static volatile boolean active;
    private static volatile int style = STYLE_BURST;
    private static volatile int color = 0xFFFFC24B;

    private KillEffectsMod() {}

    public static void setKillSource(KillSource source) {
        killSource = source;
    }

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            style = manager.getKillEffectStyle();
            color = manager.getKillEffectColor();
        }
        REGISTRY.reset();
        BURSTS.clear();
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        style = manager.getKillEffectStyle();
        color = manager.getKillEffectColor();
    }

    public static boolean isActive() {
        return active;
    }

    public static int getStyle() {
        return style;
    }

    public static int getColor() {
        return color;
    }

    /** True when the module is on but no death feed exists, so no burst can ever fire. */
    public static boolean isAwaitingGameData() {
        return active && killSource == null;
    }

    /** Records a hit you landed, so a death within the window can be credited. */
    public static void onHit(String playerId, long nowMs) {
        if (!active) return;
        REGISTRY.recordHit(playerId, nowMs);
    }

    /**
     * Polls the death feed and spawns a burst for any death credited to you.
     *
     * <p>Called from the overlay manager's frame tick. When no feed is installed this is a no-op,
     * which is why the module shows "waiting for game data" rather than pretending to work.
     */
    public static void tick(long nowMs) {
        if (!active) return;
        KillSource source = killSource;
        if (source != null) {
            String dead;
            try {
                dead = source.pollDeath();
            } catch (Throwable t) {
                dead = null;
            }
            if (dead != null && REGISTRY.creditKill(dead, nowMs)) {
                spawn(dead, nowMs);
            }
        }
        // Retire spent bursts here so the list never grows past MAX_BURSTS even if nothing draws.
        for (java.util.Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            if (nowMs - it.next().bornMs >= BURST_DURATION_MS) it.remove();
        }
    }

    /**
     * Spawns a burst for a credited kill.
     *
     * <p>Positioned at the victim's last-known peer position, which is the only world position the
     * suite can obtain without the native entity feed. With no position the burst is skipped.
     */
    private static void spawn(String playerId, long nowMs) {
        ReachIndicator.VoicePeerPosition at = null;
        for (ReachIndicator.VoicePeerPosition peer : PeerPositions.read()) {
            if (peer.id.equals(playerId)) {
                at = peer;
                break;
            }
        }
        if (at == null) return;
        if (BURSTS.size() >= MAX_BURSTS) BURSTS.remove(0);
        BURSTS.add(new Burst(at.x, at.y, at.z, nowMs, style, color));
    }

    /** The live bursts, oldest first. Empty when nothing is alive. */
    public static java.util.List<Burst> activeBursts() {
        return java.util.Collections.unmodifiableList(BURSTS);
    }

    public static void reset() {
        REGISTRY.reset();
        BURSTS.clear();
    }
}
