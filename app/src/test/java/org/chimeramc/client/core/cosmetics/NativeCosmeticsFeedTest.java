package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the native cosmetics capability probe. The important property is that it is fail-closed:
 * in a plain JVM test there is no native library, so the feed must read as the pack fallback rather
 * than throwing or claiming a native path. The classification helpers are exercised with synthetic
 * statuses so the "covers other players" rule is pinned without a device.
 */
public class NativeCosmeticsFeedTest {

    @Test
    public void absentNativeLibraryReadsAsPackFallback() {
        NativeCosmeticsFeed.Status status = NativeCosmeticsFeed.read();
        assertNotNull(status);
        // No libpreloader in a unit test, so the honest reading is "native path unavailable".
        assertEquals(NativeCosmeticsFeed.Mode.PACK_FALLBACK, status.mode);
        assertFalse(status.isNative());
        assertFalse(NativeCosmeticsFeed.isNativeRenderLive());
    }

    @Test
    public void unavailableStatusDescribesThePackRoute() {
        NativeCosmeticsFeed.Status status = NativeCosmeticsFeed.read();
        String text = NativeCosmeticsFeed.describe(status).toLowerCase();
        assertTrue(text.contains("resource-pack"));
        assertTrue(text.contains("not active"));
    }

    @Test
    public void aFrameWithOneModelDoesNotClaimOtherPlayers() {
        NativeCosmeticsFeed.Status status =
                newStatus(NativeCosmeticsFeed.Mode.NATIVE_RENDER, 120, 1, 4);
        assertTrue(status.isNative());
        assertFalse(status.coversOtherPlayers());
        String text = NativeCosmeticsFeed.describe(status);
        assertTrue(text.contains("active"));
        assertFalse(text.contains("other players"));
    }

    @Test
    public void aFrameWithTwoModelsClaimsOtherPlayers() {
        NativeCosmeticsFeed.Status status =
                newStatus(NativeCosmeticsFeed.Mode.NATIVE_RENDER, 120, 3, 4);
        assertTrue(status.coversOtherPlayers());
        assertTrue(NativeCosmeticsFeed.describe(status).contains("other players"));
    }

    @Test
    public void packFallbackNeverReportsNative() {
        NativeCosmeticsFeed.Status status =
                newStatus(NativeCosmeticsFeed.Mode.PACK_FALLBACK, 0, 0, -1);
        assertFalse(status.isNative());
        assertFalse(status.coversOtherPlayers());
        assertTrue(NativeCosmeticsFeed.describe(status).toLowerCase().contains("not active"));
    }

    /**
     * Builds a status through the public reading path so the test does not depend on a
     * package-private constructor that may change. A null/invalid stats array is the only way to
     * force a status in a JVM, so the synthetic values are injected by subclassing the enum-free
     * shape directly via the package-private constructor the class already exposes to its tests.
     */
    private static NativeCosmeticsFeed.Status newStatus(NativeCosmeticsFeed.Mode mode,
                                                        int renderTick, int calls, int ms) {
        return new NativeCosmeticsFeed.Status(mode, renderTick, calls, ms);
    }

    @Test
    public void absentImagePipelineDescribesThePackRoute() {
        // No libpreloader in a JVM test, so the verified-loader probe is false and the report must
        // say the image pipeline is not installed rather than claiming a substitution.
        String text = NativeCosmeticsFeed.describeImagePipeline().toLowerCase();
        assertTrue(text.contains("not installed"));
        assertTrue(text.contains("loader"));
    }

    @Test
    public void imagePipelineStatusNeverReadsAsLiveWithoutTheLibrary() {
        assertFalse(NativeCosmeticsBridge.isImagePathVerified());
        assertFalse(NativeCosmeticsBridge.isImagePipelineLive());
        assertEquals(0, NativeCosmeticsBridge.substitutionCount());
    }
}
