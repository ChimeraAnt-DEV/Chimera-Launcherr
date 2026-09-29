package org.chimeramc.client.core.content;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the in-game pack changer's list/merge logic against a real game-data directory, using
 * plain files and the same {@code manifest.json} shape the game writes. No mocks: the whole
 * point of the class is how it reads and rewrites the two files on disk.
 */
public class InGamePackChangerTest {

    private File tempDir() throws Exception {
        File dir = Files.createTempDirectory("pack-changer").toFile();
        dir.deleteOnExit();
        return dir;
    }

    private File writePack(File gameDataDir, String uuid, String name) throws Exception {
        File pack = new File(new File(gameDataDir, "resource_packs"), uuid);
        assertTrue(pack.mkdirs());
        try (FileWriter writer = new FileWriter(new File(pack, "manifest.json"))) {
            writer.write("{\"format_version\":2,\"header\":{\"name\":\"" + name
                    + "\",\"uuid\":\"" + uuid + "\",\"version\":[1,0,0]}}");
        }
        return pack;
    }

    private void writeGlobal(File gameDataDir, String json) throws Exception {
        File dir = new File(gameDataDir, "minecraftpe");
        assertTrue(dir.mkdirs());
        try (FileWriter writer = new FileWriter(new File(dir, "global_resource_packs.json"))) {
            writer.write(json);
        }
    }

    @Test
    public void listsInstalledPacksAndMarksActiveOnes() throws Exception {
        File root = tempDir();
        writePack(root, "aaaaaaaa-0000-0000-0000-000000000001", "Alpha");
        writePack(root, "bbbbbbbb-0000-0000-0000-000000000002", "Beta");
        writeGlobal(root, "[{\"pack_id\":\"aaaaaaaa-0000-0000-0000-000000000001\",\"version\":\"1.0.0\"}]");

        List<InGamePackChanger.PackEntry> packs = InGamePackChanger.listPacks(root);

        assertEquals(2, packs.size());
        // Sorted by name.
        assertEquals("Alpha", packs.get(0).name);
        assertTrue(packs.get(0).active);
        assertEquals("Beta", packs.get(1).name);
        assertFalse(packs.get(1).active);
    }

    @Test
    public void enablingWritesTheUuidAndKeepsOtherEntries() throws Exception {
        File root = tempDir();
        writePack(root, "bbbbbbbb-0000-0000-0000-000000000002", "Beta");
        writeGlobal(root, "[{\"pack_id\":\"aaaaaaaa-0000-0000-0000-000000000001\",\"version\":\"2.3.4\"}]");

        assertTrue(InGamePackChanger.setActive(root, "BBBBBBBB-0000-0000-0000-000000000002", "1.0.0", true));

        Set<String> active = InGamePackChanger.activeUuids(root);
        assertTrue(active.contains("aaaaaaaa-0000-0000-0000-000000000001"));
        assertTrue(active.contains("bbbbbbbb-0000-0000-0000-000000000002"));
        assertEquals(2, active.size());
    }

    @Test
    public void disablingRemovesOnlyThatPack() throws Exception {
        File root = tempDir();
        writeGlobal(root, "[{\"pack_id\":\"aaaaaaaa-0000-0000-0000-000000000001\",\"version\":\"1.0.0\"},"
                + "{\"pack_id\":\"bbbbbbbb-0000-0000-0000-000000000002\",\"version\":\"1.0.0\"}]");

        assertTrue(InGamePackChanger.setActive(root, "aaaaaaaa-0000-0000-0000-000000000001", "1.0.0", false));

        Set<String> active = InGamePackChanger.activeUuids(root);
        assertEquals(1, active.size());
        assertFalse(active.contains("aaaaaaaa-0000-0000-0000-000000000001"));
        assertTrue(active.contains("bbbbbbbb-0000-0000-0000-000000000002"));
    }

    @Test
    public void enablingTwiceDoesNotDuplicateTheEntry() throws Exception {
        File root = tempDir();
        assertTrue(InGamePackChanger.setActive(root, "cccccccc-0000-0000-0000-000000000003", "1.0.0", true));
        assertTrue(InGamePackChanger.setActive(root, "cccccccc-0000-0000-0000-000000000003", "1.0.0", true));

        assertEquals(1, InGamePackChanger.activeCount(root));
    }

    @Test
    public void theVersionIsWrittenAsAnArrayNotAString() throws Exception {
        // The regression this guards: the game matches a pack-list entry against the manifest's
        // array version. A string version is not comparable, so the game dropped the pack and an
        // enabled pack never activated.
        File root = tempDir();
        assertTrue(InGamePackChanger.setActive(root, "eeee0000-0000-0000-0000-00000000000e", "2.3.4", true));

        String global = readFile(root, "minecraftpe/global_resource_packs.json");
        org.junit.Assert.assertTrue(global, global.contains("\"version\": ["));
        org.junit.Assert.assertTrue(global, global.contains("2"));
        // No string form of the version survives anywhere in the entry.
        org.junit.Assert.assertFalse(global, global.contains("\"2.3.4\""));
    }

    @Test
    public void aLegacyStringVersionIsNormalisedToAnArrayOnTheNextWrite() throws Exception {
        File root = tempDir();
        writeWorld(root, "World", "[{\"pack_id\":\"aaaa0000-0000-0000-0000-00000000000a\",\"version\":\"1.0.0\"}]");
        writeGlobal(root, "[{\"pack_id\":\"bbbb0000-0000-0000-0000-00000000000b\",\"version\":\"1.0.0\"}]");

        // Toggling a different pack rewrites both files and normalises the unrelated entries.
        assertTrue(InGamePackChanger.setActive(root, "cccc0000-0000-0000-0000-00000000000c", "1.0.0", true));

        assertFalse(readWorld(root, "World").contains("\"1.0.0\""));
        assertFalse(readFile(root, "minecraftpe/global_resource_packs.json").contains("\"1.0.0\""));
        assertTrue(readWorld(root, "World").contains("\"version\": ["));
    }

    private String readFile(File root, String relative) throws Exception {
        return new String(Files.readAllBytes(new File(root, relative).toPath()));
    }

    @Test
    public void corruptGlobalListIsReplacedNotPreserved() throws Exception {
        File root = tempDir();
        writeGlobal(root, "{ this is not an array");

        assertTrue(InGamePackChanger.setActive(root, "dddddddd-0000-0000-0000-000000000004", "1.0.0", true));
        assertEquals(1, InGamePackChanger.activeCount(root));
    }

    @Test
    public void missingGameDataRootYieldsEmptyAndNoCrash() throws Exception {
        File root = new File(tempDir(), "does-not-exist");
        assertTrue(InGamePackChanger.listPacks(root).isEmpty());
        assertEquals(0, InGamePackChanger.activeCount(root));
        assertFalse(InGamePackChanger.setActive(null, "x", "1.0.0", true));
    }

    @Test
    public void packWithoutManifestIsNotListed() throws Exception {
        File root = tempDir();
        // A pack folder with no manifest: the game would ignore it, and so does the panel.
        assertTrue(new File(new File(root, "resource_packs"), "broken").mkdirs());
        assertTrue(InGamePackChanger.listPacks(root).isEmpty());
    }

    private void writeWorld(File gameDataDir, String worldName, String json) throws Exception {
        File world = new File(new File(gameDataDir, "minecraftWorlds"), worldName);
        assertTrue(world.mkdirs());
        try (FileWriter writer = new FileWriter(new File(world, "world_resource_packs.json"))) {
            writer.write(json);
        }
    }

    private String readWorld(File gameDataDir, String worldName) throws Exception {
        File file = new File(new File(new File(gameDataDir, "minecraftWorlds"), worldName),
                "world_resource_packs.json");
        return new String(Files.readAllBytes(file.toPath()));
    }

    @Test
    public void enablingWritesTheRunningWorldsOwnListToo() throws Exception {
        // The regression this class now guards: the running world reads its own
        // world_resource_packs.json, not the global list, so toggling in-game did nothing until
        // the player left and re-entered. Both files must be written.
        File root = tempDir();
        writeWorld(root, "Survival", "[]");

        assertTrue(InGamePackChanger.setActive(root, "aaaa0000-0000-0000-0000-000000000001", "1.0.0", true));

        assertTrue(readWorld(root, "Survival").contains("aaaa0000-0000-0000-0000-000000000001"));
        Set<String> active = InGamePackChanger.activeUuids(root);
        assertTrue(active.contains("aaaa0000-0000-0000-0000-000000000001"));
    }

    @Test
    public void aPackActiveOnlyInAWorldCountsAsActive() throws Exception {
        File root = tempDir();
        // Nothing in the global list; the world lists it. The panel must not show "off".
        writeWorld(root, "Creative", "[{\"pack_id\":\"bbbb0000-0000-0000-0000-000000000002\"}]");

        Set<String> active = InGamePackChanger.activeUuids(root);
        assertTrue(active.contains("bbbb0000-0000-0000-0000-000000000002"));
    }

    @Test
    public void disablingRemovesItFromEveryWorldsList() throws Exception {
        File root = tempDir();
        writeWorld(root, "One", "[{\"pack_id\":\"cccc0000-0000-0000-0000-000000000003\"}]");
        writeWorld(root, "Two", "[{\"pack_id\":\"cccc0000-0000-0000-0000-000000000003\"},"
                + "{\"pack_id\":\"dddd0000-0000-0000-0000-000000000004\"}]");

        assertTrue(InGamePackChanger.setActive(root, "cccc0000-0000-0000-0000-000000000003", "1.0.0", false));

        assertFalse(readWorld(root, "One").contains("cccc0000"));
        assertFalse(readWorld(root, "Two").contains("cccc0000"));
        // The unrelated entry survives.
        assertTrue(readWorld(root, "Two").contains("dddd0000-0000-0000-0000-000000000004"));
    }

    @Test
    public void aWorldWithoutAListIsNotCreatedJustToRemoveAPack() throws Exception {
        File root = tempDir();
        writeWorld(root, "Fresh", "[]");
        File untouched = new File(new File(new File(root, "minecraftWorlds"), "Fresh"),
                "world_resource_packs.json");

        InGamePackChanger.setActive(root, "eeee0000-0000-0000-0000-000000000005", "1.0.0", false);

        // Removing a pack that was never there leaves the file as-is; an empty file the game
        // would then parse is a needless side effect.
        assertEquals("[]", new String(Files.readAllBytes(untouched.toPath())).trim());
    }

    @Test
    public void listPacksMergesAcrossCandidateRoots() throws Exception {
        File first = tempDir();
        File second = tempDir();
        File alpha = writePack(first, "aaaa0000-0000-0000-0000-00000000000a", "Alpha");
        writePack(second, "bbbb0000-0000-0000-0000-00000000000b", "Beta");
        writeGlobal(second, "[{\"pack_id\":\"bbbb0000-0000-0000-0000-00000000000b\"}]");

        List<InGamePackChanger.PackEntry> packs =
                InGamePackChanger.listPacks(java.util.Arrays.asList(first, second));

        assertEquals(2, packs.size());
        assertEquals("Alpha", packs.get(0).name);
        assertFalse(packs.get(0).active);
        assertEquals("Beta", packs.get(1).name);
        assertTrue(packs.get(1).active);
        // The listing did not move the pack between roots.
        assertTrue(alpha.isDirectory());
    }

    @Test
    public void requestReloadReportsFalseWhenNoReloaderIsInstalled() {
        InGamePackChanger.setReloader(null);
        assertFalse(InGamePackChanger.requestReload());
    }

    @Test
    public void requestReloadForwardsToTheInstalledReloader() {
        InGamePackChanger.setReloader(() -> true);
        try {
            assertTrue(InGamePackChanger.requestReload());
        } finally {
            InGamePackChanger.setReloader(null);
        }
    }

    @Test
    public void aThrowingReloaderIsTreatedAsNoLiveReload() {
        InGamePackChanger.setReloader(() -> {
            throw new IllegalStateException("game gone");
        });
        try {
            assertFalse(InGamePackChanger.requestReload());
        } finally {
            InGamePackChanger.setReloader(null);
        }
    }

    @Test
    public void worldPackFilesFindsEveryWorldDirectory() throws Exception {
        File root = tempDir();
        writeWorld(root, "One", "[]");
        writeWorld(root, "Two", "[]");
        assertEquals(2, InGamePackChanger.worldPackFiles(root).size());
        assertTrue(InGamePackChanger.worldPackFiles(null).isEmpty());
    }
}
