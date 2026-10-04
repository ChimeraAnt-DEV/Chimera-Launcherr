package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Pins the per-species pet animations and their controller.
 *
 * <p>The bug this guards against is a controller that names an animation the pack never writes, or
 * a species whose gait set is missing a state the controller can enter. Either one leaves the pet
 * frozen with no error, so the two files are checked against each other here.
 */
public class PetAnimationsTest {

    @Test
    public void everySpeciesEmitsTheFullGaitSet() {
        for (CosmeticCatalog.PetSpecies species : CosmeticCatalog.PetSpecies.values()) {
            JsonObject animations = JsonParser
                    .parseString(PetAnimations.animationsJson(pet(species)))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
            for (String id : new String[]{PetAnimations.IDLE, PetAnimations.WALK,
                    PetAnimations.RUN, PetAnimations.CROUCH, PetAnimations.FLY,
                    PetAnimations.SWIM}) {
                assertTrue(species + " must emit " + id, animations.has(id));
            }
        }
    }

    /**
     * The controller must only ever reference an animation the same species' animations file
     * actually writes. A dangling reference is a silent freeze.
     */
    @Test
    public void theControllerOnlyReferencesAnimationsThatExist() {
        for (CosmeticCatalog.PetSpecies species : CosmeticCatalog.PetSpecies.values()) {
            JsonObject animations = JsonParser
                    .parseString(PetAnimations.animationsJson(pet(species)))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
            JsonObject controller = JsonParser
                    .parseString(PetAnimations.controllerJson(species))
                    .getAsJsonObject()
                    .getAsJsonObject("animation_controllers")
                    .getAsJsonObject(PetAnimations.CONTROLLER_ID)
                    .getAsJsonObject("states");

            for (String state : controller.keySet()) {
                JsonArray list = controller.getAsJsonObject(state).getAsJsonArray("animations");
                for (int i = 0; i < list.size(); i++) {
                    String ref = list.get(i).getAsString();
                    assertTrue(species + " state " + state + " references missing " + ref,
                            animations.has(ref));
                }
            }
        }
    }

    @Test
    public void crawlingBugsGetTheContextualAnimationsAndOthersDoNot() {
        // A crawler rides the head: its run is the crawl, its crouch is look-around, its fly is the
        // blown-back cling and its swim is the water cling.
        JsonObject spider = JsonParser
                .parseString(PetAnimations.animationsJson(pet(CosmeticCatalog.PetSpecies.SPIDER)))
                .getAsJsonObject().getAsJsonObject("animations");
        assertTrue(spider.has(PetAnimations.CRAWL_HEAD));
        assertTrue(spider.has(PetAnimations.LOOK_AROUND));
        assertTrue(spider.has(PetAnimations.BLOWN_BACK));
        assertTrue(spider.has(PetAnimations.WATER_CLING));

        String spiderController = PetAnimations.controllerJson(CosmeticCatalog.PetSpecies.SPIDER);
        assertTrue("a crawler's run state crawls on the head",
                spiderController.contains(PetAnimations.CRAWL_HEAD));
        assertTrue("a crawler's crouch state looks around",
                spiderController.contains(PetAnimations.LOOK_AROUND));
        assertTrue("a crawler's fly state leans back",
                spiderController.contains(PetAnimations.BLOWN_BACK));
        assertTrue("a crawler's swim state clings to the water",
                spiderController.contains(PetAnimations.WATER_CLING));

        // A cat does not cling, so it must not carry the contextual animations at all.
        JsonObject cat = JsonParser
                .parseString(PetAnimations.animationsJson(pet(CosmeticCatalog.PetSpecies.CAT)))
                .getAsJsonObject().getAsJsonObject("animations");
        assertFalse(cat.has(PetAnimations.CRAWL_HEAD));
        assertFalse(cat.has(PetAnimations.BLOWN_BACK));
        String catController = PetAnimations.controllerJson(CosmeticCatalog.PetSpecies.CAT);
        assertFalse(catController.contains(PetAnimations.CRAWL_HEAD));
        assertFalse(catController.contains(PetAnimations.BLOWN_BACK));
    }

    @Test
    public void theControllerSwitchesOnThePlayersState() {
        String controller = PetAnimations.controllerJson(CosmeticCatalog.PetSpecies.CAT);
        // The transitions are what make one gait play at a time; without them every animation in
        // the file would blend together.
        assertTrue(controller.contains("query.is_sneaking"));
        assertTrue(controller.contains("query.is_swimming"));
        assertTrue(controller.contains("query.is_on_ground"));
        assertTrue(controller.contains("query.modified_move_speed"));
    }

    @Test
    public void speciesFallIntoDistinctMotionFamilies() {
        assertEquals(PetAnimations.Family.QUADRUPED,
                PetAnimations.familyOf(CosmeticCatalog.PetSpecies.CAT));
        assertEquals(PetAnimations.Family.BIRD,
                PetAnimations.familyOf(CosmeticCatalog.PetSpecies.PARROT));
        assertEquals(PetAnimations.Family.SERPENT,
                PetAnimations.familyOf(CosmeticCatalog.PetSpecies.SNAKE));
        assertEquals(PetAnimations.Family.CRAWLER,
                PetAnimations.familyOf(CosmeticCatalog.PetSpecies.SPIDER));
    }

    @Test
    public void aQuadrupedAndABirdDoNotShareAWalkCycle() {
        String cat = PetAnimations.animationsJson(pet(CosmeticCatalog.PetSpecies.CAT));
        String parrot = PetAnimations.animationsJson(pet(CosmeticCatalog.PetSpecies.PARROT));
        assertFalse("a bird must not reuse the quadruped walk cycle", cat.equals(parrot));
    }

    @Test
    public void theHeadOffsetBakesTheRealBodyPlan() {
        // The crawl-on-head offset must place the pet on the head's crown (y ~ 32), not at the
        // origin, and must move with the body plan rather than being a fixed literal.
        float[] small = PetAnimations.headOffset(pet(CosmeticCatalog.PetSpecies.SPIDER), 1f);
        float[] large = PetAnimations.headOffset(pet(CosmeticCatalog.PetSpecies.SPIDER), 1.5f);
        assertFalse(small[1] == large[1]);
        assertTrue("the pet is lifted to the head, not left at the feet", small[1] > 20f);
    }

    @Test
    public void everyAnimationParses() {
        for (CosmeticCatalog.PetSpecies species : CosmeticCatalog.PetSpecies.values()) {
            JsonObject animations = JsonParser
                    .parseString(PetAnimations.animationsJson(pet(species)))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
            Set<String> ids = new HashSet<>(animations.keySet());
            for (String id : ids) {
                JsonObject a = animations.getAsJsonObject(id);
                assertTrue(id + " needs a bones object", a.has("bones"));
            }
        }
    }

    private static CosmeticCatalog.Pet pet(CosmeticCatalog.PetSpecies species) {
        return new CosmeticCatalog.Pet(species.name().toLowerCase(), species.displayName,
                species, species.baseColor, species.accentColor, 1f);
    }
}
