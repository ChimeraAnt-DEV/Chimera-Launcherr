package org.chimeramc.client.core.voice;

import java.util.Locale;
import java.util.Random;

/**
 * Decides which speakers a listener can hear and at what gain.
 *
 * <p>Proximity voice means exactly that: a speaker becomes audible only when the listener's
 * position is inside the speaker's range, and within that range the volume falls off with
 * distance. This class is the whole rule, as pure float maths, so "does an 8-block away player
 * fade out at a 12-block range" is unit-testable without a socket, a microphone or a game.
 *
 * <p><b>Channels.</b> A talker and a listener each carry a channel id. Two players talk only when
 * their channels agree, which is what makes multi-channel support real: a team can sit on
 * channel "team" and hear each other from across the map while still not hearing a stranger
 * standing next to them on the default channel. Channel {@link #WORLD} is the open channel
 * everyone is on unless they pick another; a listener hears someone on {@link #WORLD} even while
 * the listener is on a private channel, so switching to a team channel never cuts you off from a
 * player standing in front of you. That last rule is a deliberate choice, stated here because
 * the alternative — a hard filter — is what makes a channel switch feel like the mod broke.
 *
 * <p>This is a launcher-side peer link. It is honest about its scope: it carries voice between
 * launcher users in the same world, not into the game's own audio mixer, and it cannot give a
 * vanilla client without the launcher a voice channel.
 */
public final class VoiceChannel {

    /** The open channel: audible to everyone within range, whichever channel they are on. */
    public static final String WORLD = "world";

    /** Prefix of a generated private-channel join code. */
    public static final String CODE_PREFIX = "CHIMERA-";

    /**
     * Code alphabet. Every character is a digit or an unambiguously-shaped uppercase letter, so a
     * code read aloud or copied off a screen cannot be mistyped as {@code O}/{@code 0} or
     * {@code I}/{@code 1}/{@code L}.
     */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 4;

    /** A listener hears a talker at or inside this fraction of the range at full gain. */
    public static final float FULL_GAIN_FRACTION = 0.35f;

    private VoiceChannel() {
    }

    /**
     * Generates a short, human-readable join code: {@code CHIMERA-XXXX}.
     *
     * <p>A private channel's id <em>is</em> its join code, so "invite a friend" is nothing more
     * than sharing this string -- typing it in selects the same channel id and the existing
     * {@link #canHear} match takes over. No new networking is involved.
     */
    public static String generateCode() {
        return generateCode(new Random());
    }

    /** Generates a code from a supplied source, so tests can pin the alphabet and shape. */
    public static String generateCode(Random random) {
        StringBuilder code = new StringBuilder(CODE_PREFIX.length() + CODE_LENGTH);
        code.append(CODE_PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    /** Whether an id is a generated private-channel code. */
    public static boolean isJoinCode(String channel) {
        if (channel == null) return false;
        String trimmed = channel.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.startsWith(CODE_PREFIX) || trimmed.length() != CODE_PREFIX.length() + CODE_LENGTH) {
            return false;
        }
        for (int i = CODE_PREFIX.length(); i < trimmed.length(); i++) {
            if (indexOf(CODE_ALPHABET, trimmed.charAt(i)) < 0) return false;
        }
        return true;
    }

    private static int indexOf(char[] alphabet, char value) {
        for (int i = 0; i < alphabet.length; i++) {
            if (alphabet[i] == value) return i;
        }
        return -1;
    }

    /** Normalises a channel id, treating null/blank as {@link #WORLD}. */
    public static String normalize(String channel) {
        if (channel == null) return WORLD;
        String trimmed = channel.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? WORLD : trimmed;
    }

    /**
     * Whether a listener on {@code listenerChannel} can hear a talker on {@code talkerChannel}.
     *
     * <p>Same channel always hears. {@link #WORLD} is hearable across channels in both
     * directions: a talker broadcasting on the open channel reaches a listener on any channel,
     * and a listener on the open channel hears every talker. Two private channels that differ
     * do not hear each other.
     */
    public static boolean canHear(String listenerChannel, String talkerChannel) {
        String listener = normalize(listenerChannel);
        String talker = normalize(talkerChannel);
        if (listener.equals(talker)) return true;
        return WORLD.equals(listener) || WORLD.equals(talker);
    }

    /**
     * The gain a listener receives from a talker, in {@code [0,1]}, or 0 when out of range or
     * on a channel that does not reach.
     *
     * <p>The falloff is linear from full gain at the inner radius to silence at the range edge.
     * Linear is chosen over an inverse-square curve because proximity chat is judged by ear, not
     * by physics: a quadratic falloff makes a player two blocks behind you barely audible, which
     * reads as a bug rather than as distance.
     *
     * @param distance       listener-to-talker distance in blocks; negative is treated as 0
     * @param rangeBlocks    the talker's audible range; a non-positive range means silence
     * @param listenerChannel the listener's channel
     * @param talkerChannel   the talker's channel
     */
    public static float gain(float distance, float rangeBlocks,
                            String listenerChannel, String talkerChannel) {
        if (rangeBlocks <= 0f) return 0f;
        if (!canHear(listenerChannel, talkerChannel)) return 0f;

        float d = distance < 0f ? 0f : distance;
        if (d >= rangeBlocks) return 0f;

        float inner = rangeBlocks * FULL_GAIN_FRACTION;
        if (d <= inner) return 1f;
        return (rangeBlocks - d) / (rangeBlocks - inner);
    }

    /** Convenience overload using squared distance, to avoid a sqrt on the audio path. */
    public static float gainFromSquared(float distanceSquared, float rangeBlocks,
                                        String listenerChannel, String talkerChannel) {
        if (rangeBlocks <= 0f || distanceSquared < 0f) return 0f;
        return gain((float) Math.sqrt(distanceSquared), rangeBlocks,
                listenerChannel, talkerChannel);
    }

    /**
     * Whether a talker should be streamed to a listener at all.
     *
     * <p>Separate from {@link #gain} so a transport can stop sending audio the moment a listener
     * walks out of range, rather than paying to encode packets that mix to zero.
     */
    public static boolean inRange(float distance, float rangeBlocks) {
        return rangeBlocks > 0f && distance >= 0f && distance < rangeBlocks;
    }
}
