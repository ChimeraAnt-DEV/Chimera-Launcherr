package org.chimeramc.client.core.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.launcher.core.content.CosmeticsDiagnostics;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;

/**
 * Covers {@link CosmeticsDiagnostics}: each link of the cape chain is detected from a real file
 * tree, and a missing link is reported as the specific failure rather than a generic one.
 */
public class CosmeticsDiagnosticsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final String UUID = "b7c1a5e2-3d4f-4a6b-9c8d-1e2f3a4b5c6d";

    private File writtenPack(File root, String minEngine) throws Exception {
        return writtenPack(root, minEngine, true);
    }

    private File writtenPack(File root, String minEngine, boolean withEntity) throws Exception {
        File pack = new File(new File(root, "resource_packs"), UUID);
        assertTrue(pack.mkdirs());
        try (FileWriter w = new FileWriter(new File(pack, "manifest.json"))) {
            w.write("{\"header\":{\"min_engine_version\":[" + minEngine + "]}}");
        }
        if (withEntity) {
            File entity = new File(pack, "entity/player.entity.json");
            assertTrue(entity.getParentFile().mkdirs());
            try (FileWriter w = new FileWriter(entity)) {
                w.write("{}");
            }
            File controller = new File(pack, "render_controllers/chimera_cape.render_controllers.json");
            assertTrue(controller.getParentFile().mkdirs());
            try (FileWriter w = new FileWriter(controller)) {
                w.write("{}");
            }
        }
        return pack;
    }

    private void activate(File root) throws Exception {
        File pe = new File(root, "minecraftpe");
        assertTrue(pe.mkdirs());
        try (FileWriter w = new FileWriter(new File(pe, "global_resource_packs.json"))) {
            w.write("[{\"pack_id\":\"" + UUID + "\",\"version\":[1,0,0]}]");
        }
    }

    private static CosmeticsDiagnostics.Check find(List<CosmeticsDiagnostics.Check> checks,
                                                   String labelPart) {
        for (CosmeticsDiagnostics.Check c : checks) {
            if (c.label.toLowerCase().contains(labelPart.toLowerCase())) return c;
        }
        throw new AssertionError("no check containing " + labelPart);
    }

    @Test
    public void aFullyInstalledPackPassesTheTwoFileChecks() throws Exception {
        File root = folder.newFolder("root");
        writtenPack(root, "1, 20, 0");
        activate(root);
        File staging = folder.newFolder("staging");
        File staged = new File(staging, "cape_pack");
        assertTrue(staged.mkdirs());
        try (FileWriter w = new FileWriter(new File(staged, "manifest.json"))) {
            w.write("{}");
        }

        List<CosmeticsDiagnostics.Check> checks = CosmeticsDiagnostics.run(
                Arrays.asList(root), staging, "1.26.60.28");

        assertEquals(CosmeticsDiagnostics.Status.OK,
                find(checks, "written").status);
        assertEquals(CosmeticsDiagnostics.Status.OK,
                find(checks, "global_resource_packs").status);
        assertEquals(CosmeticsDiagnostics.Status.OK,
                find(checks, "entity override").status);
        assertEquals(CosmeticsDiagnostics.Status.OK,
                find(checks, "Manifest accepts").status);
    }

    @Test
    public void aPackWithoutTheEntityOverrideFailsTheRendererCheck() throws Exception {
        // A pack that is written and active but has no player entity override cannot draw a cape;
        // the diagnostics must say so rather than reporting everything green.
        File root = folder.newFolder("root");
        writtenPack(root, "1, 20, 0", false);
        activate(root);

        List<CosmeticsDiagnostics.Check> checks = CosmeticsDiagnostics.run(
                Arrays.asList(root), null, "1.26.60.28");

        assertEquals(CosmeticsDiagnostics.Status.FAIL,
                find(checks, "entity override").status);
    }

    @Test
    public void aPackWrittenButNotActivatedFailsTheActiveCheck() throws Exception {
        File root = folder.newFolder("root");
        writtenPack(root, "1, 20, 0");
        // deliberately no global_resource_packs.json

        List<CosmeticsDiagnostics.Check> checks = CosmeticsDiagnostics.run(
                Arrays.asList(root), null, "1.26.60.28");

        assertEquals(CosmeticsDiagnostics.Status.OK, find(checks, "written").status);
        assertEquals(CosmeticsDiagnostics.Status.FAIL,
                find(checks, "global_resource_packs").status);
    }

    @Test
    public void nothingInstalledFailsTheWrittenCheck() throws Exception {
        File root = folder.newFolder("root");

        List<CosmeticsDiagnostics.Check> checks = CosmeticsDiagnostics.run(
                Arrays.asList(root), null, "1.26.60.28");

        assertEquals(CosmeticsDiagnostics.Status.FAIL, find(checks, "written").status);
    }

    @Test
    public void aManifestTooNewForTheGameFails() throws Exception {
        File root = folder.newFolder("root");
        writtenPack(root, "1, 99, 0");
        activate(root);

        List<CosmeticsDiagnostics.Check> checks = CosmeticsDiagnostics.run(
                Arrays.asList(root), null, "1.26.60.28");

        assertEquals(CosmeticsDiagnostics.Status.FAIL, find(checks, "Manifest accepts").status);
    }

    @Test
    public void unknownRenderStateIsManualNotInvented() throws Exception {
        File root = folder.newFolder("root");
        List<CosmeticsDiagnostics.Check> checks = CosmeticsDiagnostics.run(
                Arrays.asList(root), null, "1.26.60.28");

        assertEquals(CosmeticsDiagnostics.Status.MANUAL,
                find(checks, "entity override").status);
        assertEquals(CosmeticsDiagnostics.Status.MANUAL,
                find(checks, "Renderer draws").status);
    }

    @Test
    public void versionParsingHandlesTheRealFormats() {
        assertTrue(CosmeticsDiagnostics.compare(
                CosmeticsDiagnostics.parseVersion("1.26.60.28"),
                CosmeticsDiagnostics.parseVersion("1.20.0")) > 0);
        assertTrue(CosmeticsDiagnostics.compare(
                CosmeticsDiagnostics.parseVersion("26.51"),
                CosmeticsDiagnostics.parseVersion("1.20.0")) > 0);
        assertEquals(0, CosmeticsDiagnostics.compare(
                CosmeticsDiagnostics.parseVersion("1.20"),
                CosmeticsDiagnostics.parseVersion("1.20.0.0")));
        assertFalse(CosmeticsDiagnostics.isActiveIn(
                rootOfMissingDir(), UUID));
    }

    private File rootOfMissingDir() {
        try {
            return folder.newFolder("empty");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
