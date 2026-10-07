package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * The authored pet meshes: which species have a real Blockbench model, that each is retargeted to
 * the identifier the render controller looks for, and that the shipped assets are well-formed.
 *
 * <p>These read the real files from {@code resources/cosmetics/models/pets}, so a broken export or a
 * mapping that points two species at one file fails here rather than only on a device.
 */
public class AuthoredPetModelsTest {

    private static File assetDir() {
        // Gradle runs unit tests with the app module as the working directory.
        File[] candidates = {
                new File("src/main/../../resources/cosmetics/models/pets"),
                new File("../resources/cosmetics/models/pets"),
                new File("resources/cosmetics/models/pets"),
        };
        for (File f : candidates) {
            if (f.isDirectory()) return f;
        }
        return null;
    }

    @Test
    public void theExpectedSpeciesHaveAuthoredModels() {
        for (CosmeticCatalog.PetSpecies s : new CosmeticCatalog.PetSpecies[]{
                CosmeticCatalog.PetSpecies.DRAGON, CosmeticCatalog.PetSpecies.PARROT,
                CosmeticCatalog.PetSpecies.DRAGONFLY, CosmeticCatalog.PetSpecies.AXOLOTL,
                CosmeticCatalog.PetSpecies.WOLF}) {
            assertTrue(s + " should have an authored model", AuthoredPetModels.hasAuthoredModel(s));
            assertNotNull(AuthoredPetModels.fileFor(s));
            assertNotNull(AuthoredPetModels.textureFor(s));
        }
    }

    @Test
    public void speciesWithoutAModelFallBackToTheProceduralMesh() {
        assertFalse(AuthoredPetModels.hasAuthoredModel(CosmeticCatalog.PetSpecies.CAT));
        assertNull(AuthoredPetModels.fileFor(CosmeticCatalog.PetSpecies.CAT));
    }

    @Test
    public void noTwoSpeciesShareAModelFile() {
        Set<String> files = new HashSet<>();
        for (CosmeticCatalog.PetSpecies s : CosmeticCatalog.PetSpecies.values()) {
            String f = AuthoredPetModels.fileFor(s);
            if (f != null) assertTrue("duplicate model file " + f, files.add(f));
        }
    }

    @Test
    public void everyAuthoredModelIsValidJsonAndRetargetsToThePetGeometryId() {
        File dir = assetDir();
        org.junit.Assume.assumeTrue("assets not found from the test working directory", dir != null);
        for (CosmeticCatalog.PetSpecies s : CosmeticCatalog.PetSpecies.values()) {
            if (!AuthoredPetModels.hasAuthoredModel(s)) continue;
            String json = AuthoredPetModels.loadFromDir(dir, s);
            assertNotNull(s + " model should load", json);
            JsonObject model = JsonParser.parseString(json).getAsJsonObject();
            JsonObject geo = model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            assertEquals("retargeted identifier",
                    PetGeometry.GEOMETRY_ID,
                    geo.getAsJsonObject("description").get("identifier").getAsString());
            JsonArray bones = geo.getAsJsonArray("bones");
            assertTrue(s + " must keep its bones", bones.size() > 0);
        }
    }

    @Test
    public void everyAuthoredModelKeepsItsCubesAndItsRootBone() {
        File dir = assetDir();
        org.junit.Assume.assumeTrue("assets not found from the test working directory", dir != null);
        for (CosmeticCatalog.PetSpecies s : CosmeticCatalog.PetSpecies.values()) {
            if (!AuthoredPetModels.hasAuthoredModel(s)) continue;
            JsonObject model = JsonParser.parseString(
                    AuthoredPetModels.loadFromDir(dir, s)).getAsJsonObject();
            JsonArray bones = model.getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject().getAsJsonArray("bones");
            boolean hasRoot = false;
            int cubes = 0;
            for (int i = 0; i < bones.size(); i++) {
                JsonObject bone = bones.get(i).getAsJsonObject();
                if ("pet".equals(bone.get("name").getAsString())) hasRoot = true;
                if (bone.has("cubes")) cubes += bone.getAsJsonArray("cubes").size();
            }
            assertTrue(s + " must have a pet root bone so the animation controller can drive it",
                    hasRoot);
            assertTrue(s + " must keep its cubes", cubes > 0);
        }
    }
}
