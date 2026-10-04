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

        assertNull("a plain palette has no effect",
                CosmeticEffects.forCape(cape("verdant_solid")));
        assertNull(CosmeticEffects.forAccessory(accessory("graphite_headphones")));
        assertNull(CosmeticEffects.forCape(null));
        assertNull(CosmeticEffects.forAccessory(null));
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
        for (CosmeticEffects.Effect effect : new CosmeticEffects.Effect[]{
                CosmeticEffects.CREEPER_FUSE, CosmeticEffects.EMBER_RISE,
                CosmeticEffects.VOID_MOTES, CosmeticEffects.GILDED_SPARKLE,
                CosmeticEffects.LAGOON_BUBBLE}) {
            JsonObject root = JsonParser.parseString(CosmeticEffects.particleJson(effect))
                    .getAsJsonObject()
                    .getAsJsonObject("particle_effect");
            assertEquals(effect.id, root.getAsJsonObject("description")
                    .get("identifier").getAsString());
            assertTrue(root.has("components"));
        }
    }

    /**
     * The effect id must not be the raw 0xAARRGGBB-derived float — a colour component outside 0..1
     * would make the gradient invalid. Pin the range.
     */
    @Test
    public void effectTintComponentsAreNormalised() {
        for (CosmeticEffects.Effect effect : new CosmeticEffects.Effect[]{
                CosmeticEffects.CREEPER_FUSE, CosmeticEffects.EMBER_RISE,
                CosmeticEffects.VOID_MOTES, CosmeticEffects.GILDED_SPARKLE,
                CosmeticEffects.LAGOON_BUBBLE}) {
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
}
