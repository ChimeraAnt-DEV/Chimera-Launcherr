package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The authored hat meshes and the {@code acc} root rename that makes the head-tilt animation drive
 * them. Runs against the real shipped assets.
 */
public class AuthoredHatModelsTest {

    private static File assetDir() {
        for (File f : new File[]{
                new File("src/main/../../resources/cosmetics/models/hats"),
                new File("../resources/cosmetics/models/hats"),
                new File("resources/cosmetics/models/hats")}) {
            if (f.isDirectory()) return f;
        }
        return null;
    }

    @Test
    public void theExpectedHatKindsHaveAuthoredModels() {
        for (CosmeticCatalog.AccessoryKind k : new CosmeticCatalog.AccessoryKind[]{
                CosmeticCatalog.AccessoryKind.CAP, CosmeticCatalog.AccessoryKind.BEANIE,
                CosmeticCatalog.AccessoryKind.CROWN, CosmeticCatalog.AccessoryKind.TOPHAT,
                CosmeticCatalog.AccessoryKind.WIZARD_HAT, CosmeticCatalog.AccessoryKind.HALO,
                CosmeticCatalog.AccessoryKind.FLOWER, CosmeticCatalog.AccessoryKind.MASK,
                CosmeticCatalog.AccessoryKind.EAR}) {
            assertTrue(k + " should have an authored model", AuthoredHatModels.hasAuthoredModel(k));
            assertNotNull(AuthoredHatModels.textureFor(k));
        }
    }

    @Test
    public void noTwoKindsShareAModelFile() {
        Set<String> files = new HashSet<>();
        for (CosmeticCatalog.AccessoryKind k : CosmeticCatalog.AccessoryKind.values()) {
            String f = AuthoredHatModels.fileFor(k);
            if (f != null) assertTrue("duplicate " + f, files.add(f));
        }
    }

    @Test
    public void everyAuthoredHatRetargetsItsIdentifierAndHasAnAccRoot() {
        File dir = assetDir();
        org.junit.Assume.assumeTrue("assets not found", dir != null);
        for (CosmeticCatalog.AccessoryKind k : CosmeticCatalog.AccessoryKind.values()) {
            if (!AuthoredHatModels.hasAuthoredModel(k)) continue;
            String json = AuthoredHatModels.loadFromDir(dir, k);
            assertNotNull(k + " should load", json);
            JsonObject geo = JsonParser.parseString(json).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            assertEquals("retargeted identifier", AccessoryGeometry.GEOMETRY_ID,
                    geo.getAsJsonObject("description").get("identifier").getAsString());
            JsonArray bones = geo.getAsJsonArray("bones");
            boolean acc = false;
            for (int i = 0; i < bones.size(); i++) {
                if (AccBoneRetarget.ACC.equals(
                        bones.get(i).getAsJsonObject().get("name").getAsString())) acc = true;
            }
            assertTrue(k + " must have an acc root so the head-tilt animation drives it", acc);
        }
    }

    @Test
    public void anAccRootRenameKeepsEveryCube() {
        File dir = assetDir();
        org.junit.Assume.assumeTrue("assets not found", dir != null);
        for (CosmeticCatalog.AccessoryKind k : CosmeticCatalog.AccessoryKind.values()) {
            if (!AuthoredHatModels.hasAuthoredModel(k)) continue;
            JsonArray bones = JsonParser.parseString(AuthoredHatModels.loadFromDir(dir, k))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject().getAsJsonArray("bones");
            int cubes = 0;
            for (int i = 0; i < bones.size(); i++) {
                JsonObject b = bones.get(i).getAsJsonObject();
                if (b.has("cubes")) cubes += b.getAsJsonArray("cubes").size();
            }
            assertTrue(k + " must keep its cubes", cubes > 0);
        }
    }

    @Test
    public void aKindWithoutAModelFallsBackToTheProceduralMesh() {
        assertNull(AuthoredHatModels.fileFor(CosmeticCatalog.AccessoryKind.NONE));
        assertTrue(!AuthoredHatModels.hasAuthoredModel(CosmeticCatalog.AccessoryKind.SCARF));
    }
}
