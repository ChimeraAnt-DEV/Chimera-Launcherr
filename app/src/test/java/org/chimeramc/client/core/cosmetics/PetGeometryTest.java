package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
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

    /**
     * The species-defining parts must be anchored to the body, not authored at absolute z=0. The
     * old code placed every shell, wing, stripe and tail at world z~0 while the body sat at z~9,
     * so the parts floated detached behind the animal and it did not read as that species on
     * device. Legs are excluded (their y-origin is 0) because they legitimately sit under the body.
     */
    @Test
    public void speciesPartsAreAnchoredToTheBodyNotFloatingBehindIt() {
        CosmeticCatalog.PetSpecies[] checked = {
                CosmeticCatalog.PetSpecies.TURTLE, CosmeticCatalog.PetSpecies.BEETLE,
                CosmeticCatalog.PetSpecies.SPIDER, CosmeticCatalog.PetSpecies.BUTTERFLY,
                CosmeticCatalog.PetSpecies.DRAGONFLY, CosmeticCatalog.PetSpecies.DRAGON,
                CosmeticCatalog.PetSpecies.PARROT, CosmeticCatalog.PetSpecies.BEE,
                CosmeticCatalog.PetSpecies.CAT, CosmeticCatalog.PetSpecies.DOG,
                CosmeticCatalog.PetSpecies.FOX, CosmeticCatalog.PetSpecies.WOLF,
                CosmeticCatalog.PetSpecies.RABBIT, CosmeticCatalog.PetSpecies.LIZARD,
                CosmeticCatalog.PetSpecies.AXOLOTL, CosmeticCatalog.PetSpecies.FROG,
        };
        for (CosmeticCatalog.PetSpecies species : checked) {
            JsonArray cubes = cubes(species);
            // The body is the largest-volume cube.
            double[] bodyZ = null;
            double bestVolume = -1;
            for (int i = 0; i < cubes.size(); i++) {
                JsonObject c = cubes.get(i).getAsJsonObject();
                double vol = volume(c);
                if (vol > bestVolume) {
                    bestVolume = vol;
                    bodyZ = zRange(c);
                }
            }
            boolean attached = false;
            for (int i = 0; i < cubes.size(); i++) {
                JsonObject c = cubes.get(i).getAsJsonObject();
                if (volume(c) == bestVolume) continue;
                if (origin(c)[1] == 0) continue; // a leg, legitimately under the body
                double[] z = zRange(c);
                if (z[0] < bodyZ[1] && z[1] > bodyZ[0]) {
                    attached = true;
                    break;
                }
            }
            assertTrue(species + " has no defining part anchored to its body", attached);
        }
    }

    private static JsonArray cubes(CosmeticCatalog.PetSpecies species) {
        // The mesh is multi-bone now (head, tail, wings, legs are separate), so gather every bone's
        // cubes; reading only the first bone would miss the species-defining parts.
        com.google.gson.JsonArray bones = JsonParser.parseString(PetGeometry.geometryJson(pet(species)))
                .getAsJsonObject()
                .getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject()
                .getAsJsonArray("bones");
        com.google.gson.JsonArray all = new com.google.gson.JsonArray();
        for (int b = 0; b < bones.size(); b++) {
            JsonArray cubes = bones.get(b).getAsJsonObject().getAsJsonArray("cubes");
            if (cubes == null) continue;
            for (int i = 0; i < cubes.size(); i++) {
                all.add(cubes.get(i));
            }
        }
        return all;
    }

    private static double[] origin(JsonObject cube) {
        JsonArray o = cube.getAsJsonArray("origin");
        return new double[]{o.get(0).getAsDouble(), o.get(1).getAsDouble(), o.get(2).getAsDouble()};
    }

    private static double[] zRange(JsonObject cube) {
        double z0 = origin(cube)[2];
        return new double[]{z0, z0 + cube.getAsJsonArray("size").get(2).getAsDouble()};
    }

    private static double volume(JsonObject cube) {
        JsonArray s = cube.getAsJsonArray("size");
        return s.get(0).getAsDouble() * s.get(1).getAsDouble() * s.get(2).getAsDouble();
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
