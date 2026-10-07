package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The PNG → engine image → swap pipeline, and its fail-closed behaviour.
 *
 * <p>The JVM test classpath has no {@code libpreloader}, so every native call must degrade to a
 * safe no-op: no loader address, no image built, no swap. That is exactly the path a build whose
 * image-loader signature does not match takes, so this pins the graceful fallback rather than only
 * the happy path.
 */
public class CapeImagePipelineTest {

    private static byte[] png() {
        // A minimal, non-empty byte blob; the native side validates it through the engine, and in a
        // JVM test there is no engine at all, so only the guard path is exercised.
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    @Test
    public void withoutTheNativeLibraryThereIsNoImageLoader() {
        assertFalse(NativeCosmeticsBridge.canBuildImages());
        assertTrue(NativeCosmeticsBridge.imageLoaderAddress() == 0L);
    }

    @Test
    public void theEndToEndApplyFailsClosedWithNoEngine() {
        // A well-formed call still cannot proceed without the engine, and must not throw.
        assertFalse(NativeCosmeticsBridge.applyCapePng(0x1000L, png()));
        assertFalse(NativeCosmeticsBridge.applyCapePng(0L, png()));
        assertFalse(NativeCosmeticsBridge.applyCapePng(0x1000L, null));
        assertFalse(NativeCosmeticsBridge.applyCapePng(0x1000L, new byte[0]));
    }

    @Test
    public void anBuiltImageShorterThanTheStructIsRejected() {
        // swapCapeImage already refuses a short struct; applyCapePng must not bypass that.
        assertFalse(NativeCosmeticsBridge.swapCapeImage(0x1000L, new byte[0x10]));
        assertFalse(NativeCosmeticsBridge.swapCapeImage(0x1000L, new byte[0x30]));
    }
}
