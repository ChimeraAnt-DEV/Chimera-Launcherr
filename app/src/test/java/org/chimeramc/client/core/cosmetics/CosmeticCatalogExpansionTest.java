package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the size and shape of the expanded cosmetics catalogue: each family is large enough to be
 * a real choice, every entry has a distinct id and a readable name, and the pet species advertise
 * the gaits and traits (crawler elytra, swimmer set) the renderer switches on.
 */
public class CosmeticCatalogExpansionTest {

    @Test
    public void everyFamilyHasEnoughStylesToBeARealChoice() {
        assertTrue("capes should offer 100+ styles, had " + CosmeticCatalog.capes().size(),
                CosmeticCatalog.capes().size() >= 100);
        assertTrue("accessories should offer 100+ styles, had " + CosmeticCatalog.accessories().size(),
                CosmeticCatalog.accessories().size() >= 100);
        // Pets are the distinct authored species now, not palette recolours of a shared mesh: one
        // entry per species, each its own rig and animation set. There are far more than a handful.
        assertTrue("pets should offer a dozen or more species, had " + CosmeticCatalog.pets().size(),
                CosmeticCatalog.pets().size() >= 12);
        assertEquals("one pet entry per species",
                CosmeticCatalog.PetSpecies.values().length, CosmeticCatalog.pets().size());
    }

    @Test
    public void everyCapeIdIsDistinctAndNamed() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (CosmeticCatalog.Cape c : CosmeticCatalog.capes()) {
            assertNotNull(c.id);
            assertNotNull(c.name);
            assertFalse(c.name.isEmpty());
            assertTrue("duplicate cape id " + c.id, ids.add(c.id));
            assertTrue("duplicate cape name " + c.name, names.add(c.name));
        }
    }

    /**
     * No two cosmetics anywhere may share a display name. The templated catalogue this replaced
     * built every accessory as "&lt;palette&gt; &lt;kind&gt;", so the list read as one word swapped
     * down a column; this pins that the names are genuinely distinct instead.
     */
    @Test
    public void noTwoCosmeticsShareADisplayName() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (CosmeticCatalog.Cape c : CosmeticCatalog.capes()) {
            assertTrue("duplicate cosmetic name " + c.name, names.add(c.name));
        }
        for (CosmeticCatalog.Accessory a : CosmeticCatalog.accessories()) {
            assertTrue("duplicate cosmetic name " + a.name, names.add(a.name));
        }
        for (CosmeticCatalog.Pet p : CosmeticCatalog.pets()) {
            assertTrue("duplicate cosmetic name " + p.name, names.add(p.name));
        }
    }

    @Test
    public void everyAccessoryIdIsDistinctAndNamed() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CosmeticCatalog.Accessory a : CosmeticCatalog.accessories()) {
            assertNotNull(a.id);
            assertNotNull(a.name);
            assertFalse(a.name.isEmpty());
            assertTrue("duplicate accessory id " + a.id, ids.add(a.id));
        }
    }

    @Test
    public void everyPetIdIsDistinctAndNamed() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CosmeticCatalog.Pet p : CosmeticCatalog.pets()) {
            assertNotNull(p.id);
            assertNotNull(p.name);
            assertFalse(p.name.isEmpty());
            assertTrue("duplicate pet id " + p.id, ids.add(p.id));
        }
    }

    @Test
    public void everySpeciesHasAtLeastOneGait() {
        for (CosmeticCatalog.PetSpecies s : CosmeticCatalog.PetSpecies.values()) {
            boolean any = false;
            for (CosmeticCatalog.PetLocomotion g : CosmeticCatalog.PetLocomotion.values()) {
                if (s.supports(g)) any = true;
            }
            assertTrue(s.name() + " must support at least one gait", any);
        }
    }

    @Test
    public void crawlersAndSwimmersAreDeclared() {
        assertTrue("a spider crawls", CosmeticCatalog.PetSpecies.SPIDER.isCrawler());
        assertTrue("an ant crawls", CosmeticCatalog.PetSpecies.ANT.isCrawler());
        assertTrue("a beetle crawls", CosmeticCatalog.PetSpecies.BEETLE.isCrawler());
        assertFalse("a cat does not crawl", CosmeticCatalog.PetSpecies.CAT.isCrawler());

        assertTrue("a cat can swim", CosmeticCatalog.PetSpecies.CAT.isSwimmer());
        assertTrue("an axolotl can swim", CosmeticCatalog.PetSpecies.AXOLOTL.isSwimmer());
        assertFalse("a bee does not swim", CosmeticCatalog.PetSpecies.BEE.isSwimmer());
    }

    @Test
    public void onlyFlyersSupportFlight() {
        assertTrue(CosmeticCatalog.PetSpecies.PARROT.supports(CosmeticCatalog.PetLocomotion.FLY));
        assertTrue(CosmeticCatalog.PetSpecies.DRAGON.supports(CosmeticCatalog.PetLocomotion.FLY));
        assertFalse(CosmeticCatalog.PetSpecies.CAT.supports(CosmeticCatalog.PetLocomotion.FLY));
    }

    @Test
    public void equippedPetHonoursNoneAndUnknown() {
        assertNull(CosmeticCatalog.equippedPet(CosmeticCatalog.NONE));
        assertNull(CosmeticCatalog.equippedPet(null));
        assertNull(CosmeticCatalog.equippedPet("not-a-real-pet"));
    }

    @Test
    public void aKnownPetIdResolves() {
        CosmeticCatalog.Pet first = CosmeticCatalog.pets().get(0);
        assertNotNull(CosmeticCatalog.pet(first.id));
    }

    @Test
    public void togglingAPetTakesItOff() {
        String id = CosmeticCatalog.pets().get(0).id;
        assertEquals(CosmeticCatalog.NONE, CosmeticCatalog.togglePet(id, id));
        assertEquals(id, CosmeticCatalog.togglePet(CosmeticCatalog.NONE, id));
    }

    @Test
    public void patternsActuallyVaryTheClothColour() {
        int base = 0xFF6236E8;
        int accent = 0xFFFFD86B;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (CosmeticCatalog.CapePattern pattern : CosmeticCatalog.CapePattern.values()) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    seen.add(CapePatterns.colorAt(pattern, i / 16f, j / 16f, base, accent));
                }
            }
        }
        // A weave that only ever returns the base colour would make every patterned cape identical.
        assertTrue("patterns must produce more than one colour", seen.size() > 5);
    }

    @Test
    public void aSolidPatternIsExactlyTheBaseColour() {
        int base = 0xFF123456;
        for (int i = 0; i <= 16; i++) {
            assertEquals(base, CapePatterns.colorAt(
                    CosmeticCatalog.CapePattern.SOLID, i / 16f, i / 16f, base, 0xFFFFFFFF));
        }
    }
}
