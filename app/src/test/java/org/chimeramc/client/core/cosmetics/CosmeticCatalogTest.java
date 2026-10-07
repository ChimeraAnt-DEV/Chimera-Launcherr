package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the cosmetics selection rules and the first cape's animated brand contract, so a
 * future edit cannot quietly make the flagship cape static.
 */
public class CosmeticCatalogTest {

    @Test
    public void theFirstCapeIsTheAnimatedBrandedOne() {
        CosmeticCatalog.Cape first = CosmeticCatalog.capes().get(0);
        assertTrue("the flagship cape must be animated", first.animated);
        assertTrue("the flagship cape must carry the brand mark", first.branded);
        assertTrue("the default cape is the flagship one",
                CosmeticCatalog.defaultCape().id.equals(first.id));
    }

    @Test
    public void everyCapeHasADistinctIdAndAName() {
        int count = 0;
        for (CosmeticCatalog.Cape c : CosmeticCatalog.capes()) {
            assertNotNull(c.id);
            assertFalse(c.id.isEmpty());
            assertNotNull(c.name);
            assertFalse(c.name.isEmpty());
            count++;
        }
        assertTrue("there should be more than one cape to choose from", count > 1);
    }

    @Test
    public void equippingNoneOrAnUnknownIdWearsNothing() {
        assertNull(CosmeticCatalog.equippedCape(CosmeticCatalog.NONE));
        assertNull(CosmeticCatalog.equippedCape(null));
        assertNull(CosmeticCatalog.equippedCape("does-not-exist"));
        assertNull(CosmeticCatalog.equippedAccessory("does-not-exist"));
    }

    @Test
    public void aKnownCapeIdResolves() {
        CosmeticCatalog.Cape cape = CosmeticCatalog.equippedCape("void_black");
        assertNotNull(cape);
        assertEquals("void_black", cape.id);
    }

    @Test
    public void togglingTheEquippedCapeTakesItOff() {
        assertEquals(CosmeticCatalog.NONE, CosmeticCatalog.toggleCape("chimera", "chimera"));
        assertEquals("magenta_flux", CosmeticCatalog.toggleCape("chimera", "magenta_flux"));
        assertEquals(CosmeticCatalog.NONE, CosmeticCatalog.toggleCape("chimera", null));
    }

    @Test
    public void togglingAccessoriesMirrorsCapeBehaviour() {
        assertEquals(CosmeticCatalog.NONE, CosmeticCatalog.toggleAccessory("halo", "halo"));
        assertEquals("halo", CosmeticCatalog.toggleAccessory("wings", "halo"));
        assertEquals(CosmeticCatalog.NONE, CosmeticCatalog.toggleAccessory("halo", CosmeticCatalog.NONE));
    }

    @Test
    public void accessoriesIncludeANoneChoice() {
        assertNotNull(CosmeticCatalog.accessory(CosmeticCatalog.NONE));
        assertEquals(CosmeticCatalog.NONE, CosmeticCatalog.accessories().get(0).id);
    }

    /**
     * The wings must be layered behind the cape, which must in turn be behind the body.
     *
     * <p>The preview has no depth buffer, so these plane values are the entire depth ordering.
     * Getting them wrong is the difference between wings peeking out from under the cape and
     * wings painted flat across the character's chest; the assertion is on the ordering rather
     * than the literal z values so the planes can be tuned without breaking the test.
     */
    @Test
    public void wingsAndCapeAreLayeredBehindTheBody() {
        assertTrue("wings must sit behind the cape", CosmeticLayering.wingsBehindCape());
        assertTrue("cape must sit behind the body", CosmeticLayering.capeBehindBody());
    }

    /**
     * A hat, glasses, a veil or antlers turns with the head; a scarf, bow tie, backpack, wings and
     * the beard do not. The preview and the in-game head-tilt animation both read this rule, so a
     * hat cannot track the head in the preview while sitting bolt-upright in the game.
     */
    @Test
    public void headWornAccessoriesFollowTheHeadAndNeckBackPiecesDoNot() {
        assertTrue(CosmeticCatalog.AccessoryKind.TOPHAT.followsHead());
        assertTrue(CosmeticCatalog.AccessoryKind.CROWN.followsHead());
        assertTrue(CosmeticCatalog.AccessoryKind.GLASSES.followsHead());
        assertTrue(CosmeticCatalog.AccessoryKind.VEIL.followsHead());
        assertTrue(CosmeticCatalog.AccessoryKind.ANTLERS.followsHead());

        assertFalse(CosmeticCatalog.AccessoryKind.SCARF.followsHead());
        assertFalse(CosmeticCatalog.AccessoryKind.BOWTIE.followsHead());
        assertFalse(CosmeticCatalog.AccessoryKind.BACKPACK.followsHead());
        assertFalse(CosmeticCatalog.AccessoryKind.WINGS.followsHead());
        // The beard hangs from the jaw, not the crown, so it does not turn with the head look.
        assertFalse(CosmeticCatalog.AccessoryKind.BEARD.followsHead());
        assertFalse(CosmeticCatalog.AccessoryKind.NONE.followsHead());
    }
}
