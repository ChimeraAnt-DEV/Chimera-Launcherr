package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The Blockbench drop-in seam: an authored {@code .geo.json} replaces the procedural mesh, and its
 * absence or malformation falls back to the procedural one.
 *
 * <p>The point of the seam is that the catalogue's meshes can be authored in Blockbench without any
 * further code, so these pin the two properties that make that safe: the authored identifier is
 * retargeted to the id the render controller looks for (while the bones/cubes are preserved), and a
 * bad or missing asset never produces a broken pack.
 */
public class AuthoredGeometryTest {

    private static final String BLOCKBENCH_MODEL = "{\n"
            + "  \"format_version\": \"1.12.0\",\n"
            + "  \"minecraft:geometry\": [\n"
            + "    {\n"
            + "      \"description\": {\n"
            + "        \"identifier\": \"geometry.my_hat\",\n"
            + "        \"texture_width\": 64,\n"
            + "        \"texture_height\": 64\n"
            + "      },\n"
            + "      \"bones\": [\n"
            + "        {\n"
            + "          \"name\": \"acc\",\n"
            + "          \"pivot\": [0.0, 24.0, 0.0],\n"
            + "          \"cubes\": [\n"
            + "            { \"origin\": [-4, 32, -4], \"size\": [8, 2, 8], \"uv\": [0, 0] }\n"
            + "          ]\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";

    @Test
    public void theIdentifierIsRetargetedButTheMeshIsPreserved() {
        String out = AuthoredGeometry.retargetIdentifier(BLOCKBENCH_MODEL,
                AccessoryGeometry.GEOMETRY_ID);
        assertNotNull(out);
        assertTrue("target identifier", out.contains("\"identifier\": \"" + AccessoryGeometry.GEOMETRY_ID + "\""));
        assertTrue("authored identifier is gone", !out.contains("geometry.my_hat"));
        // The bones and cubes must survive untouched, or the retarget would deform the model.
        assertTrue("bone kept", out.contains("\"name\": \"acc\""));
        assertTrue("cube kept", out.contains("\"origin\": [-4, 32, -4]"));
        assertTrue("uv kept", out.contains("\"uv\": [0, 0]"));
    }

    @Test
    public void anAbsentModelFallsBackRatherThanThrowing() {
        AuthoredGeometry.AssetOpener empty = path -> {
            throw new java.io.FileNotFoundException(path);
        };
        assertNull(AuthoredGeometry.loadFromAssets(empty, AuthoredGeometry.ASSET_DIR,
                AuthoredGeometry.HAT_FILE, AccessoryGeometry.GEOMETRY_ID));
        assertNull(AuthoredGeometry.loadFromFile(new File("/does/not/exist.geo.json"),
                AccessoryGeometry.GEOMETRY_ID));
        assertNull(AuthoredGeometry.loadFromFile(null, AccessoryGeometry.GEOMETRY_ID));
    }

    @Test
    public void aMalformedModelFallsBackRatherThanEmittingABrokenPack() {
        // No identifier field at all: retarget cannot produce a usable model, so it must be null
        // and the caller falls back to the procedural geometry.
        assertNull(AuthoredGeometry.retargetIdentifier("{ \"bones\": [] }",
                AccessoryGeometry.GEOMETRY_ID));
        assertNull(AuthoredGeometry.retargetIdentifier(null, AccessoryGeometry.GEOMETRY_ID));
    }

    @Test
    public void anAuthoredModelIsUsedWhenPresent() {
        Map<String, String> assets = new HashMap<>();
        assets.put(AuthoredGeometry.ASSET_DIR + "/" + AuthoredGeometry.HAT_FILE, BLOCKBENCH_MODEL);
        AuthoredGeometry.AssetOpener opener = path -> {
            String content = assets.get(path);
            if (content == null) throw new java.io.FileNotFoundException(path);
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        };
        String loaded = AuthoredGeometry.loadFromAssets(opener, AuthoredGeometry.ASSET_DIR,
                AuthoredGeometry.HAT_FILE, AccessoryGeometry.GEOMETRY_ID);
        assertNotNull(loaded);
        assertTrue(loaded.contains(AccessoryGeometry.GEOMETRY_ID));
    }

    @Test
    public void theAssetDirectoryIsTheDocumentedDropInPath() {
        assertEquals("cosmetics/models", AuthoredGeometry.ASSET_DIR);
        assertEquals("chimera_hat.geo.json", AuthoredGeometry.HAT_FILE);
        assertEquals("chimera_cape.geo.json", AuthoredGeometry.CAPE_FILE);
        assertEquals("chimera_pet.geo.json", AuthoredGeometry.PET_FILE);
    }
}
