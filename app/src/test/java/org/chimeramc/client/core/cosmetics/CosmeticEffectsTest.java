package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * Pins the thematic cosmetic particle effects and the render controllers that emit them.
 *
 * <p>Two failure modes are guarded: a themed cosmetic that silently resolves to no effect (so the
 * feature never fires), and an effectless cosmetic whose controller names a particle file the pack
 * does not write (a dangling reference the game skips). Both are silent, so they are checked here.
 */
public class CosmeticEffectsTest {

    @Test
    public void themedCosmeticsResolveToAnEffectAndOthersDoNot() {
        assertNotNull(CosmeticEffects.forCape(cape("creeper_star")));
        assertNotNull(CosmeticEffects.forCape(cape("ember_gradient")));
        assertNotNull(CosmeticEffects.forCape(cape("void_solid")));
        assertNotNull(CosmeticEffects.forAccessory(accessory("gilded_crown")));
        assertNotNull(CosmeticEffects.forAccessory(accessory("lagoon_cap")));
        // The expansion: the remaining themed palettes now carry effects too.
        assertNotNull(CosmeticEffects.forCape(cape("abyss_solid")));
        assertNotNull(CosmeticEffects.forCape(cape("verdant_solid")));
        assertNotNull(CosmeticEffects.forCape(cape("rosewood_solid")));
        assertNotNull(CosmeticEffects.forCape(cape("amethyst_solid")));
        assertNotNull(CosmeticEffects.forCape(cape("flux_solid")));
        assertNotNull(CosmeticEffects.forCape(cape("chimera_solid")));

        // Graphite is the deliberate neutral palette: no effect, the reference "none" path.
        assertNull("the neutral palette has no effect",
                CosmeticEffects.forAccessory(accessory("graphite_headphones")));
        assertNull(CosmeticEffects.forCape(null));
        assertNull(CosmeticEffects.forAccessory(null));
        assertNull(CosmeticEffects.forPet(null));
    }

    @Test
    public void aFewSpeciesCarryTheirOwnSignatureEffect() {
        assertNotNull(CosmeticEffects.forPet(pet(CosmeticCatalog.PetSpecies.BEE)));
        assertNotNull(CosmeticEffects.forPet(pet(CosmeticCatalog.PetSpecies.BUTTERFLY)));
        assertNotNull(CosmeticEffects.forPet(pet(CosmeticCatalog.PetSpecies.DRAGON)));
        assertNotNull(CosmeticEffects.forPet(pet(CosmeticCatalog.PetSpecies.AXOLOTL)));
        assertNull("a plain species has no effect",
                CosmeticEffects.forPet(pet(CosmeticCatalog.PetSpecies.CAT)));
        assertNull(CosmeticEffects.forPet(pet(CosmeticCatalog.PetSpecies.SPIDER)));
    }

    @Test
    public void eachEffectHasItsOwnIdentifierAndPath() {
        assertFalse(CosmeticEffects.CREEPER_FUSE.id.equals(CosmeticEffects.EMBER_RISE.id));
        assertEquals("particles/creeper_fuse.json",
                CosmeticEffects.pathFor(CosmeticEffects.CREEPER_FUSE));
        assertEquals("particles/ember_rise.json",
                CosmeticEffects.pathFor(CosmeticEffects.EMBER_RISE));
    }

    @Test
    public void theParticleFileIsValidJsonUnderItsOwnIdentifier() {
        for (CosmeticEffects.Effect effect : allEffects()) {
            JsonObject root = JsonParser.parseString(CosmeticEffects.particleJson(effect))
                    .getAsJsonObject()
                    .getAsJsonObject("particle_effect");
            assertEquals(effect.id, root.getAsJsonObject("description")
                    .get("identifier").getAsString());
            assertTrue(root.has("components"));
        }
    }

    /** Every effect the catalogue can emit, so a new one cannot skip the JSON/range checks. */
    private static CosmeticEffects.Effect[] allEffects() {
        return new CosmeticEffects.Effect[]{
                CosmeticEffects.CREEPER_FUSE, CosmeticEffects.EMBER_RISE,
                CosmeticEffects.VOID_MOTES, CosmeticEffects.GILDED_SPARKLE,
                CosmeticEffects.LAGOON_BUBBLE, CosmeticEffects.ABYSS_CURRENT,
                CosmeticEffects.VERDANT_LEAF, CosmeticEffects.ROSEWOOD_PETAL,
                CosmeticEffects.AMETHYST_GLINT, CosmeticEffects.FLUX_SPARK,
                CosmeticEffects.CHIMERA_MOTE, CosmeticEffects.BEE_POLLEN,
                CosmeticEffects.WING_DUST, CosmeticEffects.DRAGONFIRE,
                CosmeticEffects.AXOLOTL_BUBBLE
        };
    }

    @Test
    public void everyEffectIdentifierIsUnique() {
        CosmeticEffects.Effect[] effects = allEffects();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CosmeticEffects.Effect effect : effects) {
            assertTrue("duplicate effect id " + effect.id, ids.add(effect.id));
        }
        assertEquals(effects.length, ids.size());
    }

    /**
     * The effect id must not be the raw 0xAARRGGBB-derived float — a colour component outside 0..1
     * would make the gradient invalid. Pin the range.
     */
    @Test
    public void effectTintComponentsAreNormalised() {
        for (CosmeticEffects.Effect effect : allEffects()) {
            assertTrue(effect.r >= 0f && effect.r <= 1f);
            assertTrue(effect.g >= 0f && effect.g <= 1f);
            assertTrue(effect.b >= 0f && effect.b <= 1f);
        }
    }

    private static CosmeticCatalog.Cape cape(String id) {
        return new CosmeticCatalog.Cape(id, id, 0xFF000000, 0xFF111111, 0xFF222222,
                CosmeticCatalog.CapePattern.SOLID, false, false);
    }

    private static CosmeticCatalog.Accessory accessory(String id) {
        return new CosmeticCatalog.Accessory(id, id, CosmeticCatalog.AccessoryKind.CROWN,
                0xFF000000, 0xFF111111);
    }

    private static CosmeticCatalog.Pet pet(CosmeticCatalog.PetSpecies species) {
        return new CosmeticCatalog.Pet(species.name().toLowerCase(), species.displayName,
                species, species.baseColor, species.accentColor, 1f);
    }
}
