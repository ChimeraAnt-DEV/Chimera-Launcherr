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
}
