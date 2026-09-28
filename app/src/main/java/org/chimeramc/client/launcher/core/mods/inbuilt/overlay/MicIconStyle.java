package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.voice.VoiceProtocol;

/**
 * The available looks for the in-world nametag microphone icon.
 *
 * <p>Kept as pure integers so the appearance setting is a value the manager can persist and the
 * renderer can switch on without a Context. This is the one piece of the voice feature whose
 * "appearance/behaviour" the module settings still own, because it is a global look rather than
 * a per-channel control.
 */
public final class MicIconStyle {

    /** The blocky pixel-art microphone, matching the in-game HUD indicator. */
    public static final int STYLE_CLASSIC = 0;
    /** A smooth line-art microphone, for players who prefer a less pixelated mark. */
    public static final int STYLE_SMOOTH = 1;

    /**
     * Below this level a peer is not considered to be speaking.
     *
     * <p>The raw level is the smoothed microphone RMS (see the audio engine). Speech sits low on a
     * linear scale, so the fill is only lifted to full green near the top of the range while
     * background noise stays neutral.
     */
    public static final float AUDIBLE_FLOOR = 0.06f;

    private MicIconStyle() {
    }

    /** The number of styles, for a settings slider's range. */
    public static int count() {
        return 2;
    }

    /** Clamps any stored/hostile value into the known styles. */
    public static int clamp(int style) {
        if (style < 0) return STYLE_CLASSIC;
        return Math.min(style, count() - 1);
    }

    /** A short label for the settings row. */
    public static String describe(int style) {
        return clamp(style) == STYLE_SMOOTH ? "Smooth" : "Classic";
    }

    /**
     * The grow-toward-green fill for a speaking peer, in {@code [0,1]}.
     *
     * <p>A level at or below {@link #AUDIBLE_FLOOR} reads as the neutral idle state (0), not a
     * faint ring; louder peers approach full green and reach it by full scale.
     */
    public static float speakingFill(float level) {
        float clamped = VoiceProtocol.clampLevel(level);
        if (clamped <= AUDIBLE_FLOOR) return 0f;
        return Math.min(1f, (clamped - AUDIBLE_FLOOR) / (1f - AUDIBLE_FLOOR));
    }
}
