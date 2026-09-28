package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the in-world nametag tags from the voice peers, whose world positions now
 * arrive over the voice protocol (v3 beacons/audio carry x/y/z).
 *
 * <p>This is the piece that makes {@link VoiceNametagMod.TagSource} real without any
 * native entity walk: the positions come from the same UDP packets the audio engine
 * already parses, and only the local view (camera) is read from the game.
 *
 * <p>Scope note: the protocol carries no world/server id, so "same world" is
 * approximated by "on a channel the listener can hear". On a LAN link that is the
 * same world in practice; a peer who wandered onto another server would project at a
 * stale position, which is why the module is opt-in.
 */
final class VoiceNametagTagFeed implements VoiceNametagMod.TagSource {

    /**
     * World height of a Bedrock nametag label above the player's feet, in blocks. The
     * projector adds the label's own baseline on top; this is the "over the head" anchor.
     */
    private static final float LABEL_HEIGHT = 2.0f;

    @Override
    public List<NametagIconProjector.Tag> read() {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module == null) return NametagIconProjector.emptyTags();
        List<VoicePeer> peers = module.audiblePeers();
        if (peers.isEmpty()) return NametagIconProjector.emptyTags();
        List<NametagIconProjector.Tag> tags = new ArrayList<>(peers.size());
        for (VoicePeer peer : peers) {
            if (peer == null) continue;
            tags.add(new NametagIconProjector.Tag(peer.id, peer.name,
                    peer.x, peer.y, peer.z, LABEL_HEIGHT, peer.channel));
        }
        return tags;
    }
}
