package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.List;

/**
 * State and the game-data seam for the in-world Voice nametag microphone icon.
 *
 * <p>Draws a small animated microphone beside the nametag of each player who is in the same world
 * and on the same voice channel: an X over it when muted (by them or by you), a ring that fills
 * toward green with their live volume while they speak, and a neutral static glyph while idle.
 *
 * <p>Honest scope: the peer <em>audio level</em> is real -- it is the same smoothed RMS the audio
 * engine already measures, carried on the wire in the voice protocol. What this module cannot do
 * without a native feed is know where each player's nametag is on screen: that position lives in
 * {@code libminecraftpe.so}, exactly like the Hitboxes module's boxes. Until a provider is
 * installed, {@link #readTags()} returns an empty list and nothing is drawn -- never an icon at a
 * guessed position, which would sit over the wrong player.
 */
public final class VoiceNametagMod {

    private static volatile TagSource tagSource;
    private static volatile boolean active;
    private static volatile boolean enabled = true;
    private static volatile boolean animated = true;
    private static volatile int style = MicIconStyle.STYLE_CLASSIC;

    private VoiceNametagMod() {
    }

    /**
     * Supplies the in-world nametag tags each frame, or an empty list when it has none.
     *
     * <p>A provider that throws is treated as having no data, so a misbehaving feed degrades to
     * "draw nothing" rather than taking the overlay down mid-session.
     */
    public interface TagSource {
        List<NametagIconProjector.Tag> read();
    }

    public static void setTagSource(TagSource source) {
        tagSource = source;
    }

    public static void setEnabled(boolean isEnabled, InbuiltModManager manager) {
        active = isEnabled;
        if (manager != null) {
            enabled = manager.isVoiceNametagIconEnabled();
            animated = manager.isVoiceIconAnimated();
            style = manager.getVoiceIconStyle();
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        enabled = manager.isVoiceNametagIconEnabled();
        animated = manager.isVoiceIconAnimated();
        style = manager.getVoiceIconStyle();
    }

    public static boolean isActive() {
        return active && enabled;
    }

    public static boolean isAnimated() {
        return animated;
    }

    public static int getStyle() {
        return style;
    }

    /**
     * True when the module is on but no tag feed is installed.
     *
     * <p>Surfaced the same way as the combat modules' empty state: an enabled module that draws
     * nothing is indistinguishable from a broken one, so the absence of a feed is stated rather
     * than hidden. Callers that have no room for a line simply draw nothing.
     */
    public static boolean isAwaitingGameData() {
        return active && enabled && tagSource == null;
    }

    /** The tags to draw this frame; empty (never null) when there is nothing to show. */
    public static List<NametagIconProjector.Tag> readTags() {
        if (!isActive()) return NametagIconProjector.emptyTags();
        TagSource source = tagSource;
        if (source == null) return NametagIconProjector.emptyTags();
        try {
            List<NametagIconProjector.Tag> tags = source.read();
            return tags == null ? NametagIconProjector.emptyTags() : tags;
        } catch (Throwable t) {
            return NametagIconProjector.emptyTags();
        }
    }
}
