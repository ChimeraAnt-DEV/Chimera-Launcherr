package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Resolves the visual state of one in-world nametag microphone icon.
 *
 * <p>Three states, in this priority:
 * <ul>
 *   <li>{@link State#MUTED} -- the peer muted themselves, or <em>you</em> muted them locally. Both
 *       draw the same X: the icon is about whether you can hear them right now, and either way you
 *       cannot. The peer is never told they were muted by you (the mute is local), so the icon is
 *       the only place the distinction would matter and it deliberately does not draw one.</li>
 *   <li>{@link State#SPEAKING} -- not muted and their live level is above the audibility floor.
 *       The fill grows toward full green with the volume.</li>
 *   <li>{@link State#IDLE} -- not muted and quiet: a neutral, static glyph, no animation.</li>
 * </ul>
 *
 * <p>Pure and Android-free so the whole three-way decision, including the "mute beats speaking"
 * precedence, is a unit test rather than something only visible on a device.
 */
public final class NametagMicState {

    public enum State {
        MUTED,
        SPEAKING,
        IDLE
    }

    private NametagMicState() {
    }

    /**
     * Resolves the state for one peer.
     *
     * @param level      the peer's live, smoothed mic level in {@code [0,1]}
     * @param selfMuted  the peer muted their own microphone
     * @param mutedByYou the local listener muted this peer (client-side only)
     */
    public static State resolve(float level, boolean selfMuted, boolean mutedByYou) {
        if (selfMuted || mutedByYou) return State.MUTED;
        return MicIconStyle.speakingFill(level) > 0f ? State.SPEAKING : State.IDLE;
    }

    /** Whether the state should animate at all; idle is deliberately static. */
    public static boolean animates(State state) {
        return state == State.SPEAKING;
    }
}
