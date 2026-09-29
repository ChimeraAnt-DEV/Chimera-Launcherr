package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.preloader.PreloaderInput;

/**
 * The in-process feed that fills the two voice-nametag seams from the game.
 *
 * <p>The in-world icon needs exactly two things the voice protocol cannot carry: the
 * listener's own world position and the listener's own view rotation. Both are read
 * off the local player through {@link PreloaderInput}, which captures the live
 * {@code ClientInstance} by hooking the client's own local-player accessor -- no
 * entity walking, no nametag lookups, no renderer access.
 *
 * <p>It installs both seams and nothing else: {@link VoiceChatModule.PositionSource}
 * so beacons carry a real position (which also lets the distance rule apply), and
 * {@link VoiceNametagOverlay.CameraSource} so the icons can be projected. With the
 * native read unavailable every method returns {@code null} and the callers fall back
 * to their honest no-data states rather than drawing a guessed position.
 */
public final class LocalPlayerFeed {

    /**
     * The field of view the projection assumes. Bedrock's default is 70 degrees; the
     * camera seam carries the value rather than the projector hard-coding it, so a
     * future feed can report the player's actual setting.
     */
    private static final float DEFAULT_FOV_DEG = 70f;

    /**
     * Minecraft reports entity pitch as positive looking <em>down</em>, while
     * {@link HitboxProjector.Camera} treats positive pitch as looking up (see its
     * {@code forward()}). Yaw already agrees (0 toward +Z, increasing clockwise), so
     * only pitch is flipped. An inverted vertical offset on device means this sign.
     */
    private static final float PITCH_SIGN = -1f;

    private LocalPlayerFeed() {
    }

    /** Installs both seams. Safe to call repeatedly; the last install wins. */
    public static void install() {
        VoiceChatModule.setPositionSource(new LocalPlayerFeed.Feed());
        VoiceNametagOverlay.setCameraSource(LocalPlayerFeed::readCamera);
    }

    /**
     * The feed handed to the voice module: position plus view rotation.
     *
     * <p>It implements the rotation extension so beacons advertise which way the player is looking,
     * which is what lets another launcher user draw a look-direction line for this player. The two
     * values are read from the same native snapshot in the same call, so a position can never be
     * paired with a rotation from a different frame.
     */
    static final class Feed implements VoiceChatModule.PositionSource, VoiceChatModule.RotationSource {
        @Override
        public float[] read() {
            return readPosition();
        }

        @Override
        public float[] readRotation() {
            return PreloaderInput.readLocalPlayerRotation();
        }
    }

    /** The local player's position, or null when the native read is unavailable. */
    static float[] readPosition() {
        return PreloaderInput.readLocalPlayerPosition();
    }

    /** The local player's camera, or null when any input is missing. */
    static HitboxProjector.Camera readCamera(int screenWidth, int screenHeight) {
        return cameraFrom(PreloaderInput.readLocalPlayerPosition(),
                PreloaderInput.readLocalPlayerRotation(), screenWidth, screenHeight);
    }

    /**
     * Builds the camera from a raw position/rotation pair, or null when any input is
     * missing. Pure so the yaw/pitch mapping and the null handling are unit tests.
     */
    static HitboxProjector.Camera cameraFrom(float[] position, float[] rotation,
                                             int screenWidth, int screenHeight) {
        if (position == null || position.length < 3
                || rotation == null || rotation.length < 2
                || screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        return new HitboxProjector.Camera(position[0], position[1], position[2],
                rotation[0], PITCH_SIGN * rotation[1], DEFAULT_FOV_DEG,
                screenWidth, screenHeight);
    }
}
