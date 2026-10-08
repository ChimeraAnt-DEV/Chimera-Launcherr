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
 * The segmented cape chain, verified without a device.
 *
 * <p>A wrong pivot or a bad parent in a 16-bone chain folds the cape into a knot that is only
 * visible on a real tablet, so the invariants are pinned here: the chain is contiguous, each
 * segment hangs from the one above it, the segments together span the same 10x16 box the single
 * bone did, and no two segments occupy the same space. The JSON is parsed, not string-matched, so
 * the geometry the game would load is what is checked.
 */
public class CapeGeometryTest {

    private static JsonArray bones() {
        JsonObject model = JsonParser.parseString(
                CapeGeometry.modelJson(CapeGeometry.GEOMETRY_ID)).getAsJsonObject();
        return model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
    }

    private static JsonObject bone(String name) {
        for (int i = 0; i < bones().size(); i++) {
            JsonObject bone = bones().get(i).getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) return bone;
        }
        throw new AssertionError("no bone " + name);
    }

    private static double[] pivot(String name) {
        JsonArray p = bone(name).getAsJsonArray("pivot");
        return new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()};
    }

    private static double[] origin(String name) {
        JsonArray cube = bone(name).getAsJsonArray("cubes").get(0).getAsJsonObject()
                .getAsJsonArray("origin");
        return new double[]{cube.get(0).getAsDouble(), cube.get(1).getAsDouble(),
                cube.get(2).getAsDouble()};
    }

    @Test
    public void theChainHasTheConfiguredNumberOfSegments() {
        // 16 segments is the requested chain; the fallback is the documented reduced value.
        assertEquals(16, CapeGeometry.SEGMENT_COUNT);
        assertEquals(12, CapeGeometry.FALLBACK_SEGMENT_COUNT);
        assertTrue("fallback must be fewer bones than the full chain",
                CapeGeometry.FALLBACK_SEGMENT_COUNT < CapeGeometry.SEGMENT_COUNT);
    }

    @Test
    public void everySegmentIsParentedToTheOneAboveIt() {
        // The first hangs from the body; each later one from its predecessor. A segment parented
        // to the body would move independently instead of folding with the chain.
        assertEquals("body", CapeGeometry.chainParent(1));
        for (int i = 2; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            assertEquals(CapeGeometry.boneName(i - 1), CapeGeometry.chainParent(i));
        }
        // And the JSON agrees with the rule.
        for (int i = 1; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            assertEquals(CapeGeometry.chainParent(i),
                    bone(CapeGeometry.boneName(i)).get("parent").getAsString());
        }
    }

    @Test
    public void theChainSpansTheSameBoxAsTheSingleBoneCape() {
        // The top segment's pivot is the shoulder point the single bone used...
        assertEquals(CapeGeometry.CAPE_TOP_Y, pivot(CapeGeometry.boneName(1))[1], 1e-9);
        // ...and the bottom segment's cube reaches the hem the single 16px box reached.
        double bottomOrigin = origin(CapeGeometry.boneName(CapeGeometry.SEGMENT_COUNT))[1];
        assertEquals(CapeGeometry.CAPE_BOTTOM_Y, bottomOrigin, 1e-9);
    }

    @Test
    public void segmentsAreContiguousAndDoNotOverlap() {
        // In Bedrock geometry a cube's `origin` is its minimum corner, so a segment spans
        // [originY, originY + height] and its pivot is the top edge. Each segment must hinge on
        // the bottom edge of the one above it, and no two may share a row.
        Set<Double> bottoms = new HashSet<>();
        double previousBottom = Double.NaN;
        for (int i = 1; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            String name = CapeGeometry.boneName(i);
            double pivotY = pivot(name)[1];
            double originY = origin(name)[1];
            double top = originY + CapeGeometry.segmentHeight(CapeGeometry.SEGMENT_COUNT);
            if (i > 1) {
                assertEquals("segment " + i + " must hinge on the bottom of the one above",
                        previousBottom, pivotY, 1e-9);
            }
            assertEquals("pivot sits on the segment's top edge", top, pivotY, 1e-9);
            assertTrue("no two segments share a row", bottoms.add(originY));
            previousBottom = originY;
        }
    }

    @Test
    public void eachSegmentSamplesItsOwnTextureRow() {
        // Sixteen distinct rows of the cloth panel, top to bottom; a repeated row would stretch one
        // slice of the artwork down the whole cape.
        Set<Double> rows = new HashSet<>();
        for (int i = 1; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            JsonObject cube = bone(CapeGeometry.boneName(i)).getAsJsonArray("cubes").get(0)
                    .getAsJsonObject();
            double uvV = cube.getAsJsonArray("uv").get(1).getAsDouble();
            assertEquals(i - 1, uvV, 1e-9);
            assertTrue("each segment has a distinct texture row", rows.add(uvV));
        }
        assertEquals(CapeGeometry.SEGMENT_COUNT, rows.size());
    }

    @Test
    public void everySegmentFacesOutward() {
        // The 180-degree Y turn, in both rotation and bind_pose_rotation, is what puts the artwork
        // on the side the player sees. A segment missing it would face into the player's back.
        for (int i = 1; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            JsonObject segment = bone(CapeGeometry.boneName(i));
            for (String key : new String[]{"rotation", "bind_pose_rotation"}) {
                JsonArray r = segment.getAsJsonArray(key);
                assertEquals("segment " + i + " " + key + " yaw",
                        180.0, r.get(1).getAsDouble(), 1e-9);
            }
        }
    }

    @Test
    public void theBodyAndWaistBonesAreKept() {
        // The chain hangs off the same skeleton the vanilla cape did.
        assertEquals(24.0, pivot("body")[1], 1e-9);
        assertEquals("waist", bone("body").get("parent").getAsString());
        assertEquals(12.0, pivot("waist")[1], 1e-9);
    }

    @Test
    public void aChangedSegmentCountReshapesTheWholeChainConsistently() {
        // The chain is built from SEGMENT_COUNT alone, so the reduced count must produce a shorter
        // chain that still reaches the hem. This is the FPS fallback path.
        int total = CapeGeometry.FALLBACK_SEGMENT_COUNT;
        JsonObject model = JsonParser.parseString(
                CapeGeometry.modelJson(CapeGeometry.GEOMETRY_ID, total))
                .getAsJsonObject();
        JsonArray bones = model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
        assertEquals(total + 2, bones.size());

        double lastOrigin = Double.NaN;
        for (int i = 1; i <= total; i++) {
            JsonObject segment = findBone(bones, CapeGeometry.boneName(i));
            double pivotY = segment.getAsJsonArray("pivot").get(1).getAsDouble();
            lastOrigin = segment.getAsJsonArray("cubes").get(0).getAsJsonObject()
                    .getAsJsonArray("origin").get(1).getAsDouble();
            double height = CapeGeometry.segmentHeight(total);
            assertEquals("pivot sits on the segment's top edge",
                    lastOrigin + height, pivotY, 1e-9);
        }
        assertEquals("reduced chain still reaches the hem",
                CapeGeometry.CAPE_BOTTOM_Y, lastOrigin, 1e-9);
    }

    private static JsonObject findBone(JsonArray bones, String name) {
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) return bone;
        }
        throw new AssertionError("no bone " + name);
    }

    @Test
    public void theGeometryIsValidJsonWithTheRequestedIdentifier() {
        JsonObject model = JsonParser.parseString(
                CapeGeometry.modelJson(CapeGeometry.GEOMETRY_ID)).getAsJsonObject();
        JsonObject geo = model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        assertEquals(CapeGeometry.GEOMETRY_ID,
                geo.getAsJsonObject("description").get("identifier").getAsString());
        // 2 skeleton bones + the segments.
        assertEquals(CapeGeometry.SEGMENT_COUNT + 2, bones().size());
        assertFalse(bones().size() == 0);
    }
}
