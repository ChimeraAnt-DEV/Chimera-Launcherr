package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Hitbox visualisation state and its game-data seam.
 *
 * <p>The module draws the bounding boxes of players, mobs, dropped items and projectiles, plus
 * the two combat guides: the line where a critical hit lands and the inner box that is the best
 * target for chaining combos. An entity turns blue while the crosshair is on it.
 *
 * <p>Honest scope: reading entity positions out of the running game is a native concern (the
 * preloader's pattern-scanned game hooks), and this module deliberately does not assume it.
 * Until a provider is installed, {@link #readScene()} returns null and the overlay draws
 * nothing — never a fabricated or stale box, which would be worse than no box at all because
 * the player would aim at it.
 */
public final class HitboxMod {

    private static volatile EntitySource entitySource;
    private static volatile boolean active;
    private static volatile boolean showPlayers = true;
    private static volatile boolean showMobs = true;
    private static volatile boolean showItems = true;
    private static volatile boolean showProjectiles = true;
    private static volatile boolean showThrownItems = true;
    private static volatile boolean showLookLine = true;
    private static volatile boolean showCritLine = true;
    private static volatile boolean showComboBox = true;
    private static volatile boolean showTargetBox = true;
    private static volatile int peerColor = 0xFFFFFFFF;

    private HitboxMod() {}

    /**
     * Supplies the current scene each frame, or null when it has none.
     *
     * <p>A provider that throws is treated as having no data, so a misbehaving feed degrades to
     * "draw nothing" rather than taking the overlay down mid-fight.
     */
    public interface EntitySource {
        HitboxProjector.Scene read();
    }

    public static void setEntitySource(EntitySource source) {
        entitySource = source;
    }

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            applyConfig(manager.isHitboxShowPlayers(), manager.isHitboxShowMobs(),
                    manager.isHitboxShowItems(), manager.isHitboxShowProjectiles(),
                    manager.isHitboxShowThrownItems(),
                    manager.isHitboxShowLookLine(), manager.isHitboxShowCritLine(),
                    manager.isHitboxShowComboBox(),
                    manager.getHitboxShowTargetBox(),
                    manager.getHitboxPeerColor());
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        applyConfig(manager.isHitboxShowPlayers(), manager.isHitboxShowMobs(),
                manager.isHitboxShowItems(), manager.isHitboxShowProjectiles(),
                manager.isHitboxShowThrownItems(),
                manager.isHitboxShowLookLine(), manager.isHitboxShowCritLine(),
                manager.isHitboxShowComboBox(),
                manager.getHitboxShowTargetBox(), manager.getHitboxPeerColor());
    }

    /** Separate from the manager overload so the filters are testable without a Context. */
    public static void applyConfig(boolean players, boolean mobs, boolean items,
                                   boolean projectiles, boolean thrownItems,
                                   boolean lookLine, boolean critLine, boolean comboBox) {
        applyConfig(players, mobs, items, projectiles, thrownItems, lookLine, critLine, comboBox,
                showTargetBox, peerColor);
    }

    /** Full form, including the peer-feed toggle and colour. */
    public static void applyConfig(boolean players, boolean mobs, boolean items,
                                   boolean projectiles, boolean thrownItems,
                                   boolean lookLine, boolean critLine, boolean comboBox,
                                   boolean targetBox, int color) {
        showPlayers = players;
        showMobs = mobs;
        showItems = items;
        showProjectiles = projectiles;
        showThrownItems = thrownItems;
        showLookLine = lookLine;
        showCritLine = critLine;
        showComboBox = comboBox;
        showTargetBox = targetBox;
        peerColor = color;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isShowLookLine() {
        return active && showLookLine;
    }

    public static boolean isShowCritLine() {
        return active && showCritLine;
    }

    public static boolean isShowComboBox() {
        return active && showComboBox;
    }

    /** Whether the chest-height target sub-box should be drawn. */
    public static boolean isShowTargetBox() {
        return active && showTargetBox;
    }

    /** The configured peer box colour, defaulting to white. */
    public static int peerColor() {
        return peerColor;
    }

    /**
     * True when the module is on and the peer data route exists (the voice link is running).
     *
     * <p>Different from {@link #isAwaitingGameData()}, which is about the native entity feed: the
     * peer feed needs no native hook, so the route is present whenever the voice module is
     * running. Note this is the route's existence, not whether anyone is currently in range -- the
     * overlay uses it to tell "working, nobody nearby" apart from "no route at all", so it does
     * not show the native "waiting for game data" notice while the peer route is fine.
     */
    public static boolean hasPeerFeed() {
        return active && PeerHitboxSource.hasFeed();
    }

    public static boolean isKindEnabled(HitboxProjector.Kind kind) {
        if (kind == null) return false;
        switch (kind) {
            case PLAYER:
                return showPlayers;
            case MOB:
                return showMobs;
            case DROPPED_ITEM:
                return showItems;
            case THROWN_ITEM:
                return showThrownItems;
            case PROJECTILE:
                return showProjectiles;
            default:
                return false;
        }
    }

    /**
     * True when the module is on but no entity feed is installed, so nothing can be drawn.
     *
     * <p>The overlay shows that as an explicit "waiting for game data" line. A module that is
     * enabled and silently draws nothing is indistinguishable from a broken one, and the whole
     * reason this seam exists is that the absence of a feed is a real, explainable state rather
     * than a failure — so it is surfaced instead of hidden.
     */
    public static boolean isAwaitingGameData() {
        return active && entitySource == null;
    }

    /** The projected frame to draw, or null when there is nothing to show. */
    public static HitboxProjector.Frame readFrame() {
        if (!active) return null;
        EntitySource source = entitySource;
        if (source == null) return null;
        HitboxProjector.Scene scene;
        try {
            scene = source.read();
        } catch (Throwable t) {
            return null;
        }
        if (scene == null) return null;

        HitboxProjector.Frame frame = HitboxProjector.project(scene);
        java.util.List<HitboxProjector.Projected> visible = new java.util.ArrayList<>();
        for (HitboxProjector.Projected projected : frame.entities) {
            if (isKindEnabled(projected.kind)) visible.add(projected);
        }
        return new HitboxProjector.Frame(frame.lookLine, visible);
    }
}
