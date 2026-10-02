package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one place the PvP Suite reads other players' positions.
 *
 * <p>Every module in the suite that needs a target - the Reach Indicator, Hit Prediction and the
 * kill-credit registry - needs the same thing: the peers the proximity-voice link can see, with
 * their positions guarded. Routing all three through this helper means they cannot disagree about
 * who is present, and the fail-closed rules live in one place:
 *
 * <ul>
 *   <li>A missing voice module yields an empty list, never a null the caller has to guard.</li>
 *   <li>A throwing feed degrades to empty rather than taking an overlay down mid-fight.</li>
 *   <li>A non-finite position is skipped: a torn read projected into the world would place a
 *       marker at an undefined spot, which the player might aim at.</li>
 * </ul>
 *
 * <p>This is the suite's only target source. Vanilla players, mobs and items are invisible to it;
 * reaching those needs the native entity feed the Hitboxes seam documents, and the suite fails
 * closed (draws nothing) rather than guessing.
 */
public final class PeerPositions {

    private PeerPositions() {}

    /** The peers the voice link can currently see, converted for the suite. Empty when none. */
    public static List<ReachIndicator.VoicePeerPosition> read() {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module == null) return Collections.emptyList();
        List<VoicePeer> peers;
        try {
            peers = module.audiblePeers();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (peers == null || peers.isEmpty()) return Collections.emptyList();

        List<ReachIndicator.VoicePeerPosition> out = new ArrayList<>(peers.size());
        for (VoicePeer peer : peers) {
            if (peer == null) continue;
            if (!isFinite(peer.x) || !isFinite(peer.y) || !isFinite(peer.z)) continue;
            out.add(new ReachIndicator.VoicePeerPosition(peer.id, peer.name,
                    peer.x, peer.y, peer.z));
        }
        return out;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
