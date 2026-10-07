package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The authored hats and pets: every shipped mesh is valid, retargeted, and reachable from a
 * catalogue entry. Runs against the real assets under {@code resources/cosmetics}.
 */
public class AuthoredCosmeticsTest {

    private static File dir(String sub) {
        for (File f : new File[]{
                new File("src/main/../../resources/cosmetics/models/" + sub),
                new File("../resources/cosmetics/models/" + sub),
                new File("resources/cosmetics/models/" + sub)}) {
            if (f.isDirectory()) return f;
        }
        return null;
    }

    private static JsonArray bones(String json) {
        return JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
    }

    private static int cubes(JsonArray bones) {
        int n = 0;
        for (int i = 0; i < bones.size(); i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            if (b.has("cubes")) n += b.getAsJsonArray("cubes").size();
        }
        return n;
    }

    private static String identifier(String json) {
        return JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonObject("description").get("identifier").getAsString();
    }

    @Test
    public void everyAuthoredHatIdHasACatalogueEntry() {
        Set<String> ids = new HashSet<>();
        for (CosmeticCatalog.Accessory a : CosmeticCatalog.accessories()) ids.add(a.id);
        for (String id : AuthoredHatModels.IDS) {
            assertTrue("no catalogue entry for authored hat " + id, ids.contains(id));
        }
    }

    @Test
    public void everyAuthoredPetIdHasACatalogueEntry() {
        Set<String> ids = new HashSet<>();
        for (CosmeticCatalog.Pet p : CosmeticCatalog.pets()) ids.add(p.id);
        for (String id : AuthoredPetModels.IDS) {
            assertTrue("no catalogue entry for authored pet " + id, ids.contains(id));
        }
    }

    @Test
    public void everyAuthoredHatIsValidRetargetedAndHasAnAccAttachBone() {
        File d = dir("hats");
        org.junit.Assume.assumeTrue("assets not found", d != null);
        for (String id : AuthoredHatModels.IDS) {
            String json = AuthoredHatModels.loadFromDir(d, id);
            assertNotNull(id + " should load", json);
            assertEquals(id + " identifier", AccessoryGeometry.GEOMETRY_ID, identifier(json));
            JsonArray bones = bones(json);
            boolean acc = false;
            for (int i = 0; i < bones.size(); i++) {
                if (AccBoneRetarget.ACC.equals(
                        bones.get(i).getAsJsonObject().get("name").getAsString())) acc = true;
            }
            assertTrue(id + " needs an acc attach bone so it follows the head", acc);
            assertTrue(id + " must keep its cubes", cubes(bones) > 0);
        }
    }

    @Test
    public void everyAuthoredPetIsValidRetargetedAndHasARootAndCubes() {
        File d = dir("pets");
        org.junit.Assume.assumeTrue("assets not found", d != null);
        for (String id : AuthoredPetModels.IDS) {
            String json = AuthoredPetModels.loadFromDir(d, id);
            assertNotNull(id + " should load", json);
            assertEquals(id + " identifier", PetGeometry.GEOMETRY_ID, identifier(json));
            JsonArray bones = bones(json);
            boolean root = false;
            for (int i = 0; i < bones.size(); i++) {
                if (PetBoneRetarget.ROOT.equals(
                        bones.get(i).getAsJsonObject().get("name").getAsString())) root = true;
            }
            assertTrue(id + " needs a pet root", root);
            assertTrue(id + " must keep its cubes", cubes(bones) > 0);
        }
    }

    /**
     * The cat ears/tail model is multi-bone: the ears must be the head attach point (so they turn
     * with the head) and the tail chain must survive so the accessory animation can wag it.
     */
    @Test
    public void theCatModelKeepsItsEarAndTailChains() {
        File d = dir("hats");
        org.junit.Assume.assumeTrue("assets not found", d != null);
        JsonArray bones = bones(AuthoredHatModels.loadFromDir(d, "orig_cat_ears_tail"));
        Set<String> names = new HashSet<>();
        for (int i = 0; i < bones.size(); i++) {
            names.add(bones.get(i).getAsJsonObject().get("name").getAsString());
        }
        assertTrue("ears must survive to animate", names.contains("ear_l"));
        assertTrue("ears must survive to animate", names.contains("ear_r"));
        assertTrue("tail chain must survive to wag", names.contains("tail_1"));
        assertTrue("tail chain must survive to wag", names.contains("tail_tip"));
        assertTrue("an acc attach bone is required", names.contains(AccBoneRetarget.ACC));
        // The acc bone must be the ears (the head-height bone), not the ground-level root, or the
        // whole cat would swing about the feet instead of the ears turning with the head. Its
        // pivot Y is the head top (~32), while the original root sits at 0.
        for (int i = 0; i < bones.size(); i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            if (AccBoneRetarget.ACC.equals(b.get("name").getAsString())) {
                double y = b.getAsJsonArray("pivot").get(1).getAsDouble();
                assertTrue("acc must attach at the head, not the ground (was y=" + y + ")", y > 20);
            }
        }
    }
}
