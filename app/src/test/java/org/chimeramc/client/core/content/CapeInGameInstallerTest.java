package org.chimeramc.client.core.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pins the cape's install path against a real filesystem.
 *
 * <p>This exists because the failure it guards against is silent. The cape was installed into a
 * single hardcoded game data root while the game resolved its storage from the instance's
 * isolation setting and the player's internal/external choice, so the pack applied
 * "successfully" — every file present, the status reading installed — into a directory the
 * running game never loaded. On a device that is indistinguishable from capes being broken.
 *
 * <p>No mocks: the installer's contract is about where files end up, so the test writes real
 * files and reads them back.
 */
public class CapeInGameInstallerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private CosmeticCatalog.Cape cape() {
        return new CosmeticCatalog.Cape(
                "test_cape", "Test Cape", 0xFF6236E8, 0xFFA88CFF, 0xFFFFD86B,
                CosmeticCatalog.CapePattern.SOLID, true, true);
    }

    @Test
    public void installsToEveryCandidateRoot() throws Exception {
        File staging = temp.newFolder("staging");
        File rootA = temp.newFolder("root_a");
        File rootB = temp.newFolder("root_b");
        List<File> roots = Arrays.asList(rootA, rootB);

        SkinPackActivator.Result result =
                CapeInGameInstaller.install(staging, roots, cape());

        assertTrue("install should report success", result.success);
        // The point of the fix: a single-root install leaves the cape invisible whenever the
        // game resolved a different root, so every candidate must actually receive the pack.
        assertTrue("root A should have the cape pack",
                new File(rootA, "resource_packs").isDirectory());
        assertTrue("root B should have the cape pack",
                new File(rootB, "resource_packs").isDirectory());
        assertTrue(CapeInGameInstaller.isInstalled(rootA));
        assertTrue(CapeInGameInstaller.isInstalled(rootB));
        assertTrue(CapeInGameInstaller.isInstalled(roots));
    }

    @Test
    public void writesTheTextureTheGameSamples() throws Exception {
        File staging = temp.newFolder("staging2");
        File root = temp.newFolder("root_c");

        SkinPackActivator.Result result = CapeInGameInstaller.install(
                staging, Collections.singletonList(root), cape());
        assertTrue(result.success);

        // The pack name the game looks the installed pack up by is derived from the uuid, so
        // finding the directory by scanning is the only honest check.
        File resourcePacks = new File(root, "resource_packs");
        File[] packDirs = resourcePacks.listFiles(File::isDirectory);
        assertNotNull("resource_packs should exist", packDirs);
        assertEquals("exactly one pack should be installed", 1, packDirs.length);

        File capeTexture = new File(packDirs[0], "textures/entity/cape_invisible.png");
        assertTrue("cape texture must be written at the path the game samples",
                capeTexture.isFile());
        assertTrue("cape texture must not be empty", capeTexture.length() > 0);

        // A manifest that is missing or malformed imports but applies nothing.
        assertTrue("manifest must be present",
                new File(packDirs[0], "manifest.json").isFile());
    }

    @Test
    public void uninstallRemovesTheCapeFromEveryRoot() throws Exception {
        File staging = temp.newFolder("staging3");
        File rootA = temp.newFolder("root_d");
        File rootB = temp.newFolder("root_e");
        List<File> roots = Arrays.asList(rootA, rootB);

        assertTrue(CapeInGameInstaller.install(staging, roots, cape()).success);
        assertTrue(CapeInGameInstaller.uninstall(roots).success);

        assertFalse("root A should no longer report the cape",
                CapeInGameInstaller.isInstalled(rootA));
        assertFalse("root B should no longer report the cape",
                CapeInGameInstaller.isInstalled(rootB));
    }

    @Test
    public void noRootsIsAFailureNotASilentSuccess() {
        // Reporting success with nothing written is the exact bug this class exists to prevent.
        assertFalse(CapeInGameInstaller.uninstall(Collections.<File>emptyList()).success);
        assertFalse(CapeInGameInstaller.isInstalled(Collections.<File>emptyList()));
    }

    @Test
    public void theGlobalEntryVersionIsAnArraySoTheGameMatchesThePack() throws Exception {
        // The game matches a pack-list entry against the manifest's array version; a string
        // version is not comparable, so the pack is dropped and the cape never shows. This pins
        // the array form for the path the cape installer shares with the skin apply.
        File staging = temp.newFolder("staging_array");
        File root = temp.newFolder("root_array");

        assertTrue(CapeInGameInstaller.install(staging, Collections.singletonList(root), cape()).success);

        File global = new File(new File(root, "minecraftpe"), "global_resource_packs.json");
        assertTrue("global pack list must exist", global.isFile());
        String json = new String(java.nio.file.Files.readAllBytes(global.toPath()));
        assertTrue("version must be an array: " + json, json.contains("\"version\": ["));
        assertFalse("version must not be a string: " + json,
                json.contains("\"version\": \""));
    }

    @Test
    public void nullRootsAreSkippedRatherThanCrashing() throws Exception {
        File staging = temp.newFolder("staging4");
        File realRoot = temp.newFolder("root_f");

        SkinPackActivator.Result result = CapeInGameInstaller.install(
                staging, Arrays.asList(null, realRoot), cape());

        assertTrue("the real root should still be written", result.success);
        assertTrue(CapeInGameInstaller.isInstalled(realRoot));
    }
}
