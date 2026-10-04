package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * Pins the accessory geometry: every drawing kind must produce a valid, non-empty and
 * <em>distinct</em> mesh.
 *
 * <p>The bug this guards against is a hat that reuses one box for every style, so "Cap" and
 * "Crown" differ only by colour. The catalogue advertises varied styles, so the meshes have to
 * actually vary; a regression that collapses them back to a single box fails here.
 */
public class AccessoryGeometryTest {

    /** Every kind that draws something. {@code NONE} deliberately draws nothing. */
    private static final CosmeticCatalog.AccessoryKind[] DRAWABLE = {
            CosmeticCatalog.AccessoryKind.CAP,
            CosmeticCatalog.AccessoryKind.BEANIE,
            CosmeticCatalog.AccessoryKind.CROWN,
            CosmeticCatalog.AccessoryKind.HORNS,
            CosmeticCatalog.AccessoryKind.HALO,
            CosmeticCatalog.AccessoryKind.FLOWER,
            CosmeticCatalog.AccessoryKind.EAR,
            CosmeticCatalog.AccessoryKind.HEADPHONES,
            CosmeticCatalog.AccessoryKind.GLASSES,
            CosmeticCatalog.AccessoryKind.MASK,
            CosmeticCatalog.AccessoryKind.SCARF,
            CosmeticCatalog.AccessoryKind.BACKPACK,
            CosmeticCatalog.AccessoryKind.BOWTIE,
            CosmeticCatalog.AccessoryKind.WINGS,
            CosmeticCatalog.AccessoryKind.TOPHAT,
            CosmeticCatalog.AccessoryKind.WIZARD_HAT,
            CosmeticCatalog.AccessoryKind.TIARA,
            CosmeticCatalog.AccessoryKind.BEARD,
            CosmeticCatalog.AccessoryKind.VEIL,
            CosmeticCatalog.AccessoryKind.MONOCLE,
            CosmeticCatalog.AccessoryKind.ANTLERS,
            CosmeticCatalog.AccessoryKind.PLUME,
            CosmeticCatalog.AccessoryKind.TRICORN,
            CosmeticCatalog.AccessoryKind.MORTARBOARD
    };

    @Test
    public void noneDrawsNothing() {
        assertNull(AccessoryGeometry.geometryJson(CosmeticCatalog.AccessoryKind.NONE));
        assertNull(AccessoryGeometry.geometryJson(null));
    }

    @Test
    public void everyDrawableKindIsValidJsonWithAtLeastOneCube() {
        for (CosmeticCatalog.AccessoryKind kind : DRAWABLE) {
            String json = AccessoryGeometry.geometryJson(kind);
            assertTrue(kind + " must produce geometry", json != null);

            JsonObject geometry = JsonParser.parseString(json)
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            assertEquals("identifier", AccessoryGeometry.GEOMETRY_ID,
                    geometry.getAsJsonObject("description").get("identifier").getAsString());

            int cubes = geometry.getAsJsonArray("bones").get(0).getAsJsonObject()
                    .getAsJsonArray("cubes").size();
            assertTrue(kind + " must have at least one cube", cubes >= 1);
        }
    }

    /**
     * The anti-regression: no two drawable kinds may share a mesh. If they do, the catalogue is
     * selling palette variants of one shape as distinct styles.
     */
    @Test
    public void everyDrawableKindHasADistinctMesh() {
        for (int i = 0; i < DRAWABLE.length; i++) {
            String a = AccessoryGeometry.geometryJson(DRAWABLE[i]);
            for (int j = i + 1; j < DRAWABLE.length; j++) {
                String b = AccessoryGeometry.geometryJson(DRAWABLE[j]);
                assertNotEquals("kinds " + DRAWABLE[i] + " and " + DRAWABLE[j]
                        + " share a mesh", a, b);
            }
        }
    }

    /** A hat must sit above the head; its cubes must reach up past the crown (y >= 32). */
    @Test
    public void headwearSitsAboveTheHead() {
        for (CosmeticCatalog.AccessoryKind kind : new CosmeticCatalog.AccessoryKind[]{
                CosmeticCatalog.AccessoryKind.CAP,
                CosmeticCatalog.AccessoryKind.BEANIE,
                CosmeticCatalog.AccessoryKind.CROWN}) {
            String json = AccessoryGeometry.geometryJson(kind);
            assertTrue(kind + " must reach above the head crown",
                    json.contains("\"origin\": [-4.5, 32") || json.contains("\"origin\": [-4.4, 32"));
        }
    }

    /** A crown's points must be taller in the middle, or it reads as a plain band. */
    @Test
    public void theCrownHasATallCentrePoint() {
        String json = AccessoryGeometry.geometryJson(CosmeticCatalog.AccessoryKind.CROWN);
        assertTrue("crown centre point is taller", json.contains("\"size\": [1, 2.6, 1]"));
        assertTrue("crown side points are shorter", json.contains("\"size\": [1, 1.8, 1]"));
    }

    /**
     * A halo made of too few slats reads as a faceted polygon, not a ring. Pin a dense count so a
     * future edit cannot quietly drop it back to an octagon.
     */
    @Test
    public void theHaloIsADenseRingNotAFacetedPolygon() {
        assertTrue("a halo needs many slats to read as a circle",
                AccessoryGeometry.HALO_SEGMENTS >= 16);
        String json = AccessoryGeometry.geometryJson(CosmeticCatalog.AccessoryKind.HALO);
        JsonObject geometry = JsonParser.parseString(json)
                .getAsJsonObject()
                .getAsJsonArray("minecraft:geometry")
                .get(0)
                .getAsJsonObject();
        int cubes = geometry.getAsJsonArray("bones").get(0).getAsJsonObject()
                .getAsJsonArray("cubes").size();
        assertEquals(AccessoryGeometry.HALO_SEGMENTS, cubes);

        // Neighbouring slats must overlap, or the ring shows gaps between them.
        double arc = 2.0 * Math.PI * AccessoryGeometry.HALO_RADIUS / AccessoryGeometry.HALO_SEGMENTS;
        assertTrue("halo slats overlap (arc " + arc + " <= cube width 1.5)", arc <= 1.5);
    }

    @Test
    public void floatFormattingIsCompact() {
        assertEquals("4", AccessoryGeometry.f(4f));
        assertEquals("-4.5", AccessoryGeometry.f(-4.5f));
        assertEquals("0.25", AccessoryGeometry.f(0.25f));
    }
}
