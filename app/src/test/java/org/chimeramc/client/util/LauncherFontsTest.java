package org.chimeramc.client.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the font catalogue's contract without a device: 20+ entries, a stable default, and
 * index/key lookups that never throw on a stale persisted key.
 */
public class LauncherFontsTest {

    @Test
    public void catalogueOffersMoreThanTwentyFonts() {
        assertTrue("the request was for 20+ fonts, got " + LauncherFonts.entries().size(),
                LauncherFonts.entries().size() >= 20);
    }

    @Test
    public void everyKeyIsUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (LauncherFonts.Entry e : LauncherFonts.entries()) {
            assertTrue("duplicate font key " + e.key, seen.add(e.key));
        }
    }

    @Test
    public void defaultKeyIsFirstEntry() {
        assertEquals(LauncherFonts.DEFAULT_KEY, LauncherFonts.keyAt(0));
        assertEquals(0, LauncherFonts.indexOf(LauncherFonts.DEFAULT_KEY));
    }

    @Test
    public void unknownKeyFallsBackToDefaultInsteadOfThrowing() {
        assertEquals(0, LauncherFonts.indexOf("definitely-not-a-font"));
        assertEquals(LauncherFonts.DEFAULT_KEY, LauncherFonts.keyAt(-1));
        assertEquals(LauncherFonts.DEFAULT_KEY, LauncherFonts.keyAt(9999));
    }

    @Test
    public void roundTripsEveryKeyThroughItsIndex() {
        for (int i = 0; i < LauncherFonts.entries().size(); i++) {
            String key = LauncherFonts.keyAt(i);
            assertEquals(i, LauncherFonts.indexOf(key));
            assertFalse(key.isEmpty());
        }
    }
}
