package org.chimeramc.client.core.cosmetics.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Pins the {@code .geo.json} preview pipeline against the real assets shipped in
 * {@code resources/cosmetics/models}.
 *
 * <p>The parse is exercised on the actual Blockbench exports rather than a hand-written fixture:
 * those files carry the exact shapes that break a naive reader — a negative {@code uv_size} on the
 * mirrored faces, a cube {@code rotation} with a {@code pivot}, bone parents, and a
 * {@code format_version} that varies between exports. A fixture that only resembles them can pass
 * while the real file fails, which is the same lesson {@code McpedlSourceTest} records for the
 * installer parsers.
 */
public class BedrockGeometryPreviewTest {

    private static final File HAT_DIR = resolve("hats");
    private static final File PET_DIR = resolve("pets");

    /**
     * The test JVM runs from {@code app/}, so the shared asset tree is one directory up. Several
     * candidates are tried because the working directory differs between Gradle and an IDE run.
     */
    private static File resolve(String sub) {
        for (File candidate : new File[]{
                new File("../resources/cosmetics/models/" + sub),
                new File("resources/cosmetics/models/" + sub),
                new File("src/main/../../resources/cosmetics/models/" + sub)}) {
            if (candidate.isDirectory()) return candidate;
        }
        return new File("../resources/cosmetics/models/" + sub);
    }

    @Test
    public void parsesARealBlockbenchHat() throws IOException {
        BedrockGeometry geometry = BedrockGeometryParser.parse(read(new File(HAT_DIR, "vb_straw_hat.geo.json")));
        assertNotNull(geometry);
        assertEquals(1, geometry.models.size());

        BedrockGeometry.GeometryModel model = geometry.models.get(0);
        assertEquals("geometry.glow_hat_straw_hat", model.description.identifier);
        assertEquals(64, model.description.textureWidth);
        assertEquals(64, model.description.textureHeight);
        assertFalse("a hat must contribute cubes", model.bones.isEmpty());

        int cubes = 0;
        for (BedrockGeometry.Bone bone : model.bones) cubes += bone.cubes.size();
        assertTrue("the straw hat has several cubes", cubes >= 2);
    }

    @Test
    public void parsesAPetWithABoneHierarchy() throws IOException {
        BedrockGeometry geometry = BedrockGeometryParser.parse(read(new File(PET_DIR, "pet_owl.geo.json")));
        assertNotNull(geometry);
        BedrockGeometry.GeometryModel model = geometry.models.get(0);

        boolean hasParentedBone = false;
        for (BedrockGeometry.Bone bone : model.bones) {
            if (bone.parent != null && !bone.parent.isEmpty()) hasParentedBone = true;
        }
        assertTrue("a pet rig articulates through bone parents", hasParentedBone);
    }

    @Test
    public void everyShippedAssetParses() {
        int parsed = 0;
        for (File file : filesWithSuffix(HAT_DIR, ".geo.json")) {
            assertNotNull("failed to parse " + file.getName(), parseFile(file));
            parsed++;
        }
        for (File file : filesWithSuffix(PET_DIR, ".geo.json")) {
            assertNotNull("failed to parse " + file.getName(), parseFile(file));
            parsed++;
        }
        assertTrue("expected the shipped model set to be non-trivial", parsed >= 30);
    }

    @Test
    public void negativeUvSizeBecomesAMirrorFlag() throws IOException {
        // The straw hat's east face is authored with a negative uv_size, which mirrors the sample.
        BedrockGeometry geometry = parseFile(new File(HAT_DIR, "vb_straw_hat.geo.json"));
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        assertFalse(boxes.isEmpty());

        boolean sawMirror = false;
        for (PreviewMeshModel.Box box : boxes) {
            for (PreviewMeshModel.FaceUv uv : box.faceUv) {
                if (uv != null && (uv.flipH || uv.flipV)) sawMirror = true;
            }
        }
        assertTrue("a negative uv_size must be recorded as a mirror", sawMirror);
    }

    @Test
    public void resolvesBoneParentRotationsIntoWorldCorners() {
        // A cube on a child bone must be carried by the parent's rotation, not left where it was
        // authored. The parent rotates 90 degrees about z about the origin, so a child cube sitting
        // straight above the pivot must swing to the side.
        String json = "{"
                + "\"format_version\": \"1.12.0\","
                + "\"minecraft:geometry\": [{"
                + "  \"description\": {\"identifier\": \"geometry.test\", \"texture_width\": 64, \"texture_height\": 64},"
                + "  \"bones\": ["
                + "    {\"name\": \"root\", \"pivot\": [0, 0, 0], \"rotation\": [0, 0, 90]},"
                + "    {\"name\": \"child\", \"parent\": \"root\", \"pivot\": [0, 0, 0],"
                + "     \"cubes\": [{\"origin\": [-1, 4, -1], \"size\": [2, 2, 2],"
                + "       \"uv\": {\"up\": {\"uv\": [0, 0], \"uv_size\": [2, 2]}}}]}"
                + "  ]"
                + "}]}";

        BedrockGeometry geometry = BedrockGeometryParser.parse(json);
        assertNotNull(geometry);
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        assertEquals(1, boxes.size());

        PreviewMeshModel.Box box = boxes.get(0);
        // The cube centre is (0,5,0); a +90 degree z rotation about the origin maps (0,5,0) to
        // (-5,0,0). Its y must therefore collapse toward 0 and its x must grow negative.
        float centreX = 0f, centreY = 0f;
        for (float[] corner : box.corners) {
            centreX += corner[0] / 8f;
            centreY += corner[1] / 8f;
        }
        assertTrue("child bone must be carried by the parent rotation (x)", centreX < -3.5f);
        assertTrue("child bone must be carried by the parent rotation (y)", Math.abs(centreY) < 1.5f);
    }

    @Test
    public void cubeLocalRotationTurnsAboutItsOwnPivot() {
        String json = "{"
                + "\"format_version\": \"1.12.0\","
                + "\"minecraft:geometry\": [{"
                + "  \"description\": {\"identifier\": \"geometry.test\"},"
                + "  \"bones\": [{\"name\": \"b\", \"pivot\": [0, 0, 0], \"cubes\": ["
                + "    {\"origin\": [0, 0, -1], \"size\": [2, 2, 2], \"pivot\": [0, 0, 0],"
                + "     \"rotation\": [0, 0, 90],"
                + "     \"uv\": {\"up\": {\"uv\": [0, 0], \"uv_size\": [2, 2]}}}]}]"
                + "}]}";
        BedrockGeometry geometry = BedrockGeometryParser.parse(json);
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        assertEquals(1, boxes.size());
        float maxY = -Float.MAX_VALUE;
        for (float[] corner : boxes.get(0).corners) maxY = Math.max(maxY, corner[1]);
        // Rotating the [-1..1] cube 90 degrees about z swaps its x and y extents, so it reaches y=2.
        assertTrue("local rotation must swap the cube's extents", maxY > 1.5f);
    }

    @Test
    public void boxUvUnwrapProducesDistinctFaceRects() {
        String json = "{"
                + "\"format_version\": \"1.12.0\","
                + "\"minecraft:geometry\": [{"
                + "  \"description\": {\"identifier\": \"geometry.test\"},"
                + "  \"bones\": [{\"name\": \"b\", \"cubes\": ["
                + "    {\"origin\": [0, 0, 0], \"size\": [4, 6, 2], \"uv\": [0, 0], \"uv_size\": [4, 6]}]}]"
                + "}]}";
        BedrockGeometry geometry = BedrockGeometryParser.parse(json);
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        assertEquals(1, boxes.size());
        for (PreviewMeshModel.FaceUv uv : boxes.get(0).faceUv) {
            assertNotNull(uv);
            assertTrue(uv.w > 0 && uv.h > 0);
        }
        // The top and bottom must not sit on the same atlas rectangle.
        PreviewMeshModel.FaceUv top = boxes.get(0).faceUv[0];
        PreviewMeshModel.FaceUv bottom = boxes.get(0).faceUv[1];
        assertTrue(top.u != bottom.u || top.v != bottom.v);
    }

    @Test
    public void malformedInputIsRejectedWithoutThrowing() {
        assertNull(BedrockGeometryParser.parse(null));
        assertNull(BedrockGeometryParser.parse(""));
        assertNull(BedrockGeometryParser.parse("not json at all"));
        assertNull(BedrockGeometryParser.parse("{\"minecraft:geometry\": \"nope\"}"));
        assertNull(BedrockGeometryParser.parse("{}"));
    }

    @Test
    public void singleNumberSizeIsBroadcast() {
        String json = "{"
                + "\"format_version\": \"1.8.0\","
                + "\"minecraft:geometry\": [{"
                + "  \"description\": {\"identifier\": \"geometry.test\"},"
                + "  \"bones\": [{\"name\": \"b\", \"cubes\": ["
                + "    {\"origin\": [0, 0, 0], \"size\": 4, \"uv\": [0, 0], \"uv_size\": [4, 4]}]}]"
                + "}]}";
        BedrockGeometry geometry = BedrockGeometryParser.parse(json);
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        assertEquals(1, boxes.size());
        float maxX = -Float.MAX_VALUE;
        for (float[] corner : boxes.get(0).corners) maxX = Math.max(maxX, corner[0]);
        assertEquals(4f, maxX, 0.001f);
    }

    private static BedrockGeometry parseFile(File file) {
        try {
            return BedrockGeometryParser.parse(read(file));
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File[] filesWithSuffix(File dir, String suffix) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(suffix));
        return files == null ? new File[0] : files;
    }
}
