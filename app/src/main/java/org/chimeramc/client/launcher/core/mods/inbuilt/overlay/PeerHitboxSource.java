package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds hitbox geometry for other players from the proximity-voice feed.
 *
 * <p>This is the "no entity list" route to player hitboxes: positions arrive over the voice
 * protocol (peers advertise their world x/y/z on every beacon), so a box can be drawn for every
 * launcher user in the same world without touching {@code libminecraftpe.so}. It draws only what
 * the network actually told it -- a peer whose position is stale, or who never advertised one, is
 * skipped rather than drawn at a guessed spot.
 *
 * <p>Scope, stated plainly because it bounds what the feature can ever be: a box appears only for
 * a player running this launcher and on the same voice channel/world. Vanilla players, mobs,
 * items and projectiles are invisible to this route; those still need the native entity feed the
 * Hitboxes module's {@link HitboxMod.EntitySource} is for. A look direction is drawn when the peer
 * advertised one (protocol v5); a peer on an older build, or one that never sent a rotation,
 * carries the "unknown" sentinel and gets a box with no line rather than a fabricated heading.
 *
 * <p>Pure and Android-free apart from the module/peer types, so the geometry rules are unit
 * testable on the JVM like {@link HitboxProjector}.
 */
public final class PeerHitboxSource {

    /** The standard player hitbox, matching {@link HitboxProjector.Entity#player}. */
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float PLAYER_HEIGHT = 1.8f;

    /** The one entity class this feed can see. */
    public enum Kind {
        /** A player whose position arrived over the voice protocol. */
        PEER
    }

    /** One peer to draw: its box, and the heading it advertised (0/0 when unknown). */
    public static final class PeerHitbox {
        public final String peerId;
        public final String name;
        public final Kind kind;
        public final HitboxProjector.Entity entity;
        public final float yaw, pitch;
        /** True when the peer advertised a rotation, so a look line can be drawn. */
        public final boolean hasRotation;

        PeerHitbox(String peerId, String name, Kind kind, HitboxProjector.Entity entity,
                   float yaw, float pitch, boolean hasRotation) {
            this.peerId = peerId;
            this.name = name;
            this.kind = kind;
            this.entity = entity;
            this.yaw = yaw;
            this.pitch = pitch;
            this.hasRotation = hasRotation;
        }
    }

    /** Supplies the peers to draw; null when the voice module is not running. */
    public interface PeerSource {
        List<VoicePeer> read();
    }

    private PeerHitboxSource() {
    }

    /**
     * The peers to draw. Returns an empty list (never null) when there is nothing to show.
     */
    public static List<PeerHitbox> build(PeerSource source) {
        if (source == null) return Collections.emptyList();
        List<VoicePeer> peers;
        try {
            peers = source.read();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (peers == null || peers.isEmpty()) return Collections.emptyList();

        List<PeerHitbox> out = new ArrayList<>(peers.size());
        for (VoicePeer peer : peers) {
            PeerHitbox box = from(peer);
            if (box != null) out.add(box);
        }
        return out;
    }

    /**
     * One peer to a hitbox, or null when it cannot be placed.
     *
     * <p>Guards the position exactly as the audio path does: a NaN or infinite component is a torn
     * read, and a box projected from it would land at an undefined screen position.
     */
    public static PeerHitbox from(VoicePeer peer) {
        if (peer == null) return null;
        if (!isFinite(peer.x) || !isFinite(peer.y) || !isFinite(peer.z)) return null;

        HitboxProjector.Entity entity = HitboxProjector.Entity.player(peer.x, peer.y, peer.z);
        // 0/0 is the wire's "unknown" sentinel, so a peer that never sent a rotation is not drawn
        // as looking north. Both must be finite for the rotation to be trusted.
        boolean hasRotation = isFinite(peer.yaw) && isFinite(peer.pitch)
                && !(peer.yaw == 0f && peer.pitch == 0f);
        return new PeerHitbox(peer.id, peer.name, Kind.PEER, entity,
                peer.yaw, peer.pitch, hasRotation);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /**
     * The feed the overlay installs. Returns the currently audible/visible peers when the voice
     * module is up, otherwise an empty list -- so the overlay draws nothing rather than boxes for
     * peers it can no longer see.
     */
    public static List<VoicePeer> readPeers() {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module == null) return Collections.emptyList();
        try {
            return module.audiblePeers();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /**
     * True when the peer route exists at all (the voice module is running), independent of
     * whether anyone is currently audible.
     *
     * <p>This distinguishes "the module is working but nobody is in range" from "the module has
     * no data route", so the overlay can stay quiet in the first case instead of showing the
     * native "waiting for game data" notice as if the peer route were broken.
     */
    public static boolean hasFeed() {
        return VoiceChatModule.peek() != null;
    }
}

