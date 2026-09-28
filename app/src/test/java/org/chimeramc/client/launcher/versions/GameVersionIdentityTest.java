package org.chimeramc.client.launcher.versions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.versions.GameVersion;
import org.junit.Test;

import java.io.File;

/**
 * Pins {@link GameVersion}'s value identity.
 *
 * <p>The launcher compares versions that arrive by two routes — from {@code VersionManager}'s list
 * and re-materialised from an {@code Intent} extra — so reference equality is not enough. A delete
 * performed from the settings screen happened on the parceled copy; the manager holds a different
 * object for the same instance, so without this the selected version was never cleared and the
 * deleted instance kept showing as "last played".
 *
 * <p>No Android types are touched: {@link File} is a plain JVM class and the constructor only
 * stores its arguments.
 */
public class GameVersionIdentityTest {

    private static GameVersion version(String dir, String code, String name) {
        return new GameVersion(name, name, code, new File(dir), false, "com.mojang.minecraftpe", null);
    }

    @Test
    public void twoCopiesOfTheSameInstanceAreEqual() {
        GameVersion fromManager = version("/data/games/inst-a", "1.21.132", "Inst A");
        GameVersion fromIntent = version("/data/games/inst-a", "1.21.132", "Inst A");
        assertNotEquals("the two objects must be distinct references for this to prove anything",
                fromManager, null);
        assertTrue(fromManager != fromIntent);
        assertEquals(fromManager, fromIntent);
        assertEquals(fromManager.hashCode(), fromIntent.hashCode());
    }

    @Test
    public void differentInstancesAreNotEqual() {
        GameVersion a = version("/data/games/inst-a", "26.51", "A");
        GameVersion b = version("/data/games/inst-b", "26.51", "B");
        assertNotEquals(a, b);
    }

    @Test
    public void aRenameDoesNotChangeIdentity() {
        // The same directory, renamed display name and version code: still the same instance, which
        // is what a delete needs to match on.
        GameVersion before = version("/data/games/inst-a", "1.21.132", "Old Name");
        GameVersion after = version("/data/games/inst-a", "26.51", "New Name");
        assertEquals(before, after);
    }

    @Test
    public void fallsBackToDirectoryNameWhenThereIsNoDirectory() {
        GameVersion a = new GameVersion("inst-a", "A", "1.0", null, false, null, null);
        GameVersion b = new GameVersion("inst-a", "B", "2.0", null, false, null, null);
        assertEquals(a, b);
    }

    @Test
    public void nullIsNeverEqual() {
        assertFalse(version("/data/games/inst-a", "1", "A").equals(null));
    }
}
