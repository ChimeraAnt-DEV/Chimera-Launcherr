package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * Pins the pet geometry: every species must produce a valid, non-empty and <em>distinct</em> mesh.
 *
 * <p>The bug this guards against is a pet that is one generic box for every species, so a bee and
 * a dragon differ only by colour. Each species has defining parts (a bee's stripes, a bird's
 * wings, a snake's tail, a turtle's shell), and a regression that collapses them fails here.
 */
public class PetGeometryTest {

    @Test
    public void noPetDrawsNothing() {
        assertNull(PetGeometry.geometryJson(null));
    }

    @Test
    public void everySpeciesIsValidJsonWithAtLeastOneCube() {
        for (CosmeticCatalog.PetSpecies species : CosmeticCatalog.PetSpecies.values()) {
            CosmeticCatalog.Pet pet = new CosmeticCatalog.Pet(
                    "test", "Test", species, 0xFF6236E8, 0xFFA88CFF, 1f);
            String json = PetGeometry.geometryJson(pet);
            assertTrue(species + " must produce geometry", json != null);

            JsonObject geometry = JsonParser.parseString(json)
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            assertEquals("identifier", PetGeometry.GEOMETRY_ID,
                    geometry.getAsJsonObject("description").get("identifier").getAsString());

            int cubes = geometry.getAsJsonArray("bones").get(0).getAsJsonObject()
                    .getAsJsonArray("cubes").size();
            assertTrue(species + " must have at least one cube", cubes >= 1);
        }
    }

    /** No two species may share a mesh, or the catalogue is selling palette variants as species. */
    @Test
    public void everySpeciesHasADistinctMesh() {
        CosmeticCatalog.PetSpecies[] species = CosmeticCatalog.PetSpecies.values();
        for (int i = 0; i < species.length; i++) {
            String a = PetGeometry.geometryJson(pet(species[i]));
            for (int j = i + 1; j < species.length; j++) {
                String b = PetGeometry.geometryJson(pet(species[j]));
                assertNotEquals("species " + species[i] + " and " + species[j]
                        + " share a mesh", a, b);
            }
        }
    }

    /** The pet stands on the ground and in front of the player, not at the player's origin. */
    @Test
    public void thePetStandsOnTheGroundInFrontOfThePlayer() {
        String json = PetGeometry.geometryJson(pet(CosmeticCatalog.PetSpecies.CAT));
        // CAT uses the default body (legs 3 tall, 8 deep), so the body sits at [6, 3, 9].
        assertTrue("pet body is placed in front (positive z)", json.contains("\"origin\": [6, 3, 9"));
        assertTrue("pet legs reach the ground (y=0)", json.contains(", 0, "));
    }

    /** Scale trait resizes the pet, so a small variant is genuinely smaller. */
    @Test
    public void theScaleTraitResizesThePet() {
        String small = PetGeometry.geometryJson(new CosmeticCatalog.Pet(
                "s", "Small", CosmeticCatalog.PetSpecies.CAT, 0, 0, 0.6f));
        String large = PetGeometry.geometryJson(new CosmeticCatalog.Pet(
                "l", "Large", CosmeticCatalog.PetSpecies.CAT, 0, 0, 1.6f));
        assertNotEquals("scale must change the mesh placement", small, large);
    }

    private static CosmeticCatalog.Pet pet(CosmeticCatalog.PetSpecies species) {
        return new CosmeticCatalog.Pet("test", "Test", species, 0xFF6236E8, 0xFFA88CFF, 1f);
    }
}
