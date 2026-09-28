package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The pure part of the local-player feed: turning a raw position/rotation pair into a
 * camera, and refusing to when anything is missing.
 *
 * <p>No mocks: the mapping is a plain function, so the sign convention and the
 * fail-closed null handling are asserted directly.
 */
public class LocalPlayerFeedTest {

    @Test
    public void mapsPositionAndYawStraightThrough() {
        HitboxProjector.Camera camera = LocalPlayerFeed.cameraFrom(
                new float[]{10f, 64f, -30f}, new float[]{90f, 0f}, 1920, 1080);
        assertNotNull(camera);
        assertEquals(10f, camera.x, 0.0001f);
        assertEquals(64f, camera.y, 0.0001f);
        assertEquals(-30f, camera.z, 0.0001f);
        assertEquals(90f, camera.yawDeg, 0.0001f);
        assertEquals(1920, camera.screenWidth);
        assertEquals(1080, camera.screenHeight);
    }

    /**
     * Minecraft reports pitch as positive looking down; the camera treats positive pitch as
     * looking up. Looking down (game +30) must become camera -30, or every projected icon
     * sits at the wrong height.
     */
    @Test
    public void negatesPitchBecauseTheConventionsDiffer() {
        HitboxProjector.Camera lookingDown = LocalPlayerFeed.cameraFrom(
                new float[]{0f, 0f, 0f}, new float[]{0f, 30f}, 800, 600);
        assertNotNull(lookingDown);
        assertEquals(-30f, lookingDown.pitchDeg, 0.0001f);

        HitboxProjector.Camera lookingUp = LocalPlayerFeed.cameraFrom(
                new float[]{0f, 0f, 0f}, new float[]{0f, -30f}, 800, 600);
        assertNotNull(lookingUp);
        assertEquals(30f, lookingUp.pitchDeg, 0.0001f);
    }

    @Test
    public void refusesWhenPositionIsMissing() {
        assertNull(LocalPlayerFeed.cameraFrom(null, new float[]{0f, 0f}, 800, 600));
        assertNull(LocalPlayerFeed.cameraFrom(new float[]{1f, 2f}, new float[]{0f, 0f}, 800, 600));
    }

    @Test
    public void refusesWhenRotationIsMissing() {
        assertNull(LocalPlayerFeed.cameraFrom(new float[]{1f, 2f, 3f}, null, 800, 600));
        assertNull(LocalPlayerFeed.cameraFrom(new float[]{1f, 2f, 3f}, new float[]{0f}, 800, 600));
    }

    @Test
    public void refusesWithoutADrawableSurface() {
        assertNull(LocalPlayerFeed.cameraFrom(new float[]{1f, 2f, 3f}, new float[]{0f, 0f}, 0, 600));
        assertNull(LocalPlayerFeed.cameraFrom(new float[]{1f, 2f, 3f}, new float[]{0f, 0f}, 800, 0));
    }
}
