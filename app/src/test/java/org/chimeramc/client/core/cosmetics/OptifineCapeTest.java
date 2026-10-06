package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The built-in Optifine cape: black cloth with an "OF" monogram, applied by default when Optifine
 * Mode is on and the player has no cape equipped.
 */
public class OptifineCapeTest {

    @Test
    public void theModeSuppliesADefaultCapeWhenNothingIsEquipped() {
        CosmeticCatalog.Cape cape = CosmeticCatalog.resolveEquippedCape(CosmeticCatalog.NONE, true);
        assertNotNull("Optifine Mode must supply a default cape", cape);
        assertEquals(CosmeticCatalog.OPTIFINE_RED_ID, cape.id);
        assertEquals(CosmeticCatalog.CapePattern.OPTIFINE, cape.pattern);
    }

    @Test
    public void noModeAndNoSelectionMeansNoCape() {
        assertNull(CosmeticCatalog.resolveEquippedCape(CosmeticCatalog.NONE, false));
    }

    @Test
    public void anExplicitSelectionAlwaysWinsOverTheOptifineDefault() {
        CosmeticCatalog.Cape chosen = CosmeticCatalog.resolveEquippedCape("void_black", true);
        assertNotNull(chosen);
        assertEquals("void_black", chosen.id);
    }

    @Test
    public void theDefaultCapeIsAllBlackWithAColouredMonogram() {
        CosmeticCatalog.Cape cape = CosmeticCatalog.optifineDefaultCape();
        // Near-black cloth (the classic Optifine cape is black), with a coloured "OF".
        int r = (cape.color >> 16) & 0xFF, g = (cape.color >> 8) & 0xFF, b = cape.color & 0xFF;
        assertTrue("cloth must be near-black", r < 40 && g < 40 && b < 40);
        assertEquals("monogram colour is the accent", cape.accentColor, cape.trimColor);
        assertTrue("monogram must be red or blue",
                cape.accentColor == 0xFFD8202A || cape.accentColor == 0xFF2A6FD8);
    }

    @Test
    public void theOptifinePatternIsNotOfferedAsAGeneratedWeave() {
        // OPTIFINE is a monogram, not a palette weave: it must only appear on the two built-in
        // capes, never on a generated palette cape.
        int optifineCapes = 0;
        for (CosmeticCatalog.Cape c : CosmeticCatalog.capes()) {
            if (c.pattern == CosmeticCatalog.CapePattern.OPTIFINE) optifineCapes++;
        }
        assertEquals(2, optifineCapes);
    }

    @Test
    public void theMonogramRuleDrawsLettersAndLeavesTheRestClear() {
        // The centre of the glyph must be ink; a corner of the cloth must not be.
        assertTrue(CapePatterns.optifineMonogramAt(0.5f, 0.5f)
                || CapePatterns.optifineMonogramAt(0.18f, 0.5f));
        assertFalse("the cloth corner is not lettering", CapePatterns.optifineMonogramAt(0.02f, 0.05f));
    }

    @Test
    public void theTexturePainterWritesTheMonogramInTheAccentColour() throws Exception {
        CosmeticCatalog.Cape cape = CosmeticCatalog.optifineDefaultCape();
        byte[] png = CapeTexturePainter.paint(cape.color, cape.trimColor, cape.accentColor,
                cape.pattern, cape.branded);
        int[] pixels = decode(png);
        // The monogram is painted somewhere inside the visible cloth panel; at least one accent
        // texel must exist there.
        boolean found = false;
        for (int y = 1; y < 17 && !found; y++) {
            for (int x = 1; x < 11; x++) {
                // The PNG is RGBA on the wire, so the decoded ARGB must be compared on RGB only
                // (alpha is 255 for every painted texel, but a translucent one would not match).
                if ((pixels[y * 64 + x] & 0x00FFFFFF) == (cape.accentColor & 0x00FFFFFF)) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue("the OF monogram must be painted on the cloth", found);
    }

    /** Minimal PNG decode of our own encoder: 8-bit RGBA, filter 0 on every scanline. */
    private static int[] decode(byte[] png) throws Exception {
        int pos = 8; // skip signature
        int width = 0, height = 0;
        java.io.ByteArrayOutputStream idat = new java.io.ByteArrayOutputStream();
        while (pos < png.length) {
            int len = ((png[pos] & 0xFF) << 24) | ((png[pos + 1] & 0xFF) << 16)
                    | ((png[pos + 2] & 0xFF) << 8) | (png[pos + 3] & 0xFF);
            String type = new String(png, pos + 4, 4, "US-ASCII");
            int dataStart = pos + 8;
            if ("IHDR".equals(type)) {
                width = ((png[dataStart] & 0xFF) << 24) | ((png[dataStart + 1] & 0xFF) << 16)
                        | ((png[dataStart + 2] & 0xFF) << 8) | (png[dataStart + 3] & 0xFF);
                height = ((png[dataStart + 4] & 0xFF) << 24) | ((png[dataStart + 5] & 0xFF) << 16)
                        | ((png[dataStart + 6] & 0xFF) << 8) | (png[dataStart + 7] & 0xFF);
            } else if ("IDAT".equals(type)) {
                idat.write(png, dataStart, len);
            } else if ("IEND".equals(type)) {
                break;
            }
            pos = dataStart + len + 4;
        }
        byte[] raw = inflate(idat.toByteArray());
        int[] out = new int[width * height];
        int stride = width * 4 + 1;
        for (int y = 0; y < height; y++) {
            int rowStart = y * stride + 1; // filter byte at rowStart-1
            for (int x = 0; x < width; x++) {
                int i = rowStart + x * 4;
                // PNG stores RGBA; rebuild ARGB from it.
                int r = raw[i] & 0xFF, g = raw[i + 1] & 0xFF, b = raw[i + 2] & 0xFF;
                int a = raw[i + 3] & 0xFF;
                out[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static byte[] inflate(byte[] data) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(data);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!inflater.finished()) {
            int n = inflater.inflate(buf);
            if (n == 0) break;
            out.write(buf, 0, n);
        }
        inflater.end();
        return out.toByteArray();
    }
}
