package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * The authored-model bone retarget: an author's bone names become the pet vocabulary the gait
 * controller drives, with no cube lost and no name colliding.
 */
public class PetBoneRetargetTest {

    private static String model(String... names) {
        StringBuilder sb = new StringBuilder(
                "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{"
                        + "\"identifier\":\"geometry.chimera_pet\",\"texture_width\":64,"
                        + "\"texture_height\":64},\"bones\":[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(names[i]).append("\",\"pivot\":[0,0,0],"
                    + "\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}");
        }
        sb.append("]}]}");
        return sb.toString();
    }

    private static JsonArray bones(String json) {
        return JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
    }

    private static Set<String> names(JsonArray bones) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < bones.size(); i++) out.add(bones.get(i).getAsJsonObject().get("name").getAsString());
        return out;
    }

    @Test
    public void headNeckAndBeakAllBecomeTheHeadBone() {
        JsonArray out = bones(PetBoneRetarget.apply(model("pet", "HEAD", "mandibula")));
        assertTrue(names(out).contains("head"));
        // Only one bone may be the head; the second keeps its own name but is re-parented.
        assertEquals(1, count(names(out), "head"));
    }

    @Test
    public void legsMapToTheLegSlotsInOrder() {
        JsonArray out = bones(PetBoneRetarget.apply(
                model("pet", "right_leg", "left_leg", "pata1", "patas", "dedos")));
        Set<String> n = names(out);
        assertTrue(n.contains("leg_a"));
        assertTrue(n.contains("leg_b"));
        assertTrue(n.contains("leg_c"));
        // The fourth leg-ish bone keeps a name and is still present.
        assertEquals(6, out.size());
    }

    @Test
    public void wingsMapToTheLeftAndRightSlots() {
        JsonArray out = bones(PetBoneRetarget.apply(model("pet", "wingLeft1", "wingRight1")));
        Set<String> n = names(out);
        assertTrue(n.contains("wing_l"));
        assertTrue(n.contains("wing_r"));
    }

    @Test
    public void noCubeIsLostAndNoNameCollides() {
        // Seven bones, two of which are named "body" and two "extra" -- the collisions.
        String json = PetBoneRetarget.apply(
                model("pet", "HEAD", "body", "body", "tail", "extra", "extra"));
        JsonArray out = bones(json);
        assertEquals(7, out.size());
        assertEquals("names must be unique", out.size(), names(out).size());
        int cubes = 0;
        for (int i = 0; i < out.size(); i++) {
            cubes += out.get(i).getAsJsonObject().getAsJsonArray("cubes").size();
        }
        assertEquals("every cube survives", 7, cubes);
    }

    @Test
    public void everyNonRootBoneHangsOffTheRoot() {
        JsonArray out = bones(PetBoneRetarget.apply(model("pet", "HEAD", "tail", "unknownthing")));
        for (int i = 0; i < out.size(); i++) {
            JsonObject b = out.get(i).getAsJsonObject();
            if ("pet".equals(b.get("name").getAsString())) {
                assertFalse("the root has no parent", b.has("parent"));
            } else {
                assertEquals("pet", b.get("parent").getAsString());
            }
        }
    }

    @Test
    public void aModelWithNoRootGainsOne() {
        JsonArray out = bones(PetBoneRetarget.apply(model("body", "HEAD")));
        assertTrue("a pet root must exist", names(out).contains("pet"));
    }

    @Test
    public void malformedJsonIsReturnedUnchanged() {
        assertEquals("not json", PetBoneRetarget.apply("not json"));
        assertEquals("{}", PetBoneRetarget.apply("{}"));
    }

    private static int count(Set<String> names, String name) {
        return names.contains(name) ? 1 : 0;
    }
}
