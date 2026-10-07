package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The native cosmetics bridge's pure parts: the key rule, the channel/geometry pairing, and the
 * fail-closed behaviour when the native library is absent (the JVM test classpath has no
 * libpreloader, so every call must degrade rather than throw).
 */
public class NativeCosmeticsTest {

    @Test
    public void theSameIdAlwaysYieldsTheSameKey() {
        assertEquals(NativeCosmeticsBridge.keyFor("player-uuid-1"),
                NativeCosmeticsBridge.keyFor("player-uuid-1"));
        assertFalse(NativeCosmeticsBridge.keyFor("player-a") == NativeCosmeticsBridge.keyFor("player-b"));
        assertEquals(0L, NativeCosmeticsBridge.keyFor(null));
    }

    @Test
    public void theKeyIsAStable64BitHash() {
        // Pinned so the native side's FNV-1a and this one cannot drift: both must agree that this
        // input hashes to this value, or a launcher and a hook would key on different players.
        assertEquals(-3750763034362895579L, NativeCosmeticsBridge.keyFor(""));
        assertEquals(NativeCosmeticsBridge.keyFor("chimera_cape"),
                NativeCosmeticsBridge.keyFor("chimera_cape"));
    }

    /** The native library is absent in a JVM test, so every call must be a safe no-op. */
    @Test
    public void theBridgeDegradesWithoutTheNativeLibrary() {
        assertFalse(NativeCosmeticsBridge.setCapeFor("p", null) && NativeCosmeticsBridge.isLive());
        assertFalse(NativeCosmeticsBridge.isLive());
        assertEquals(0, NativeCosmeticsBridge.stats() == null ? 0 : 1);
        NativeCosmeticsBridge.clear();
        NativeCosmeticsBridge.setRenderGeometry(new byte[]{1, 2, 3});
        assertFalse("no native library means no overrides reported",
                NativeCosmeticsBridge.hasOverrides());
    }
}
