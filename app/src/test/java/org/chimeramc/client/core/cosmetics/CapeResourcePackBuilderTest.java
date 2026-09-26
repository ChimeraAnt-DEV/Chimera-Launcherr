package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.InflaterInputStream;

/**
 * Pins the generated cape resource pack: a pack that is subtly malformed imports but renders
 * nothing, which on a device is indistinguishable from "capes do not work".
 */
public class CapeResourcePackBuilderTest {

    @Test
    public void pngHeaderIsValidAndDimensionsMatch() {
        byte[] png = CapeTexturePainter.paint(0xFF6236E8, 0xFFA88CFF, true);

        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < signature.length; i++) {
            assertEquals("png signature byte " + i, signature[i], png[i]);
        }

        // IHDR data starts after the 8-byte signature, a 4-byte length and "IHDR".
        int width = readInt(png, 16);
        int height = readInt(png, 20);
        assertEquals(CapeTexturePainter.TEXTURE_WIDTH, width);
        assertEquals(CapeTexturePainter.TEXTURE_HEIGHT, height);
        assertEquals("bit depth", 8, png[24]);
        assertEquals("colour type (truecolour+alpha)", 6, png[25]);
    }

    @Test
    public void pngPixelsRoundTripThroughInflate() throws Exception {
        int[] source = new int[CapeTexturePainter.TEXTURE_WIDTH * CapeTexturePainter.TEXTURE_HEIGHT];
        for (int i = 0; i < source.length; i++) {
            source[i] = 0xFF000000 | (int) ((i * 2654435761L) % 0xFFFFFF);
        }
        byte[] png = PngWriter.encode(64, 32, source);

        int idatStart = findChunk(png, "IDAT");
        int length = readInt(png, idatStart - 8);
        byte[] deflated = new byte[length];
        System.arraycopy(png, idatStart, deflated, 0, length);

        byte[] raw;
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(deflated))) {
            raw = inflater.readAllBytes();
        }
        assertEquals(32 * (1 + 64 * 4), raw.length);

        // Each scanline starts with filter type 0, then RGBA in the order we wrote.
        for (int y = 0; y < 32; y++) {
            int rowStart = y * (1 + 64 * 4);
            assertEquals("filter byte on row " + y, 0, raw[rowStart]);
            for (int x = 0; x < 64; x++) {
                int pixel = source[y * 64 + x];
                int at = rowStart + 1 + x * 4;
                assertEquals("R at " + x + "," + y, (pixel >>> 16) & 0xFF, raw[at] & 0xFF);
                assertEquals("G at " + x + "," + y, (pixel >>> 8) & 0xFF, raw[at + 1] & 0xFF);
                assertEquals("B at " + x + "," + y, pixel & 0xFF, raw[at + 2] & 0xFF);
                assertEquals("A at " + x + "," + y, (pixel >>> 24) & 0xFF, raw[at + 3] & 0xFF);
            }
        }
    }

    @Test
    public void packWritesThePathsTheGameSamples() throws Exception {
        File dir = Files.createTempDirectory("cape-pack").toFile();
        try {
            CapeResourcePackBuilder.BuiltPack built =
                    CapeResourcePackBuilder.build(dir, CosmeticCatalog.cape("chimera"));

            assertTrue("manifest", new File(dir, "manifest.json").isFile());
            assertTrue("cape texture",
                    new File(dir, CapeResourcePackBuilder.CAPE_TEXTURE_PATH).isFile());
            assertTrue("elytra texture",
                    new File(dir, CapeResourcePackBuilder.ELYTRA_TEXTURE_PATH).isFile());
            assertTrue("pack icon", new File(dir, CapeResourcePackBuilder.PACK_ICON_PATH).isFile());

            assertNotNull(built);
            assertEquals(CapeResourcePackBuilder.PACK_UUID, built.uuid);
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void manifestDeclaresAResourcesModuleAndMatchingVersion() {
        String manifest = CapeResourcePackBuilder.manifestJson();

        assertTrue("pack uuid", manifest.contains(CapeResourcePackBuilder.PACK_UUID));
        assertTrue("module uuid", manifest.contains(CapeResourcePackBuilder.moduleUuid()));
        // A pack with no resources module imports but applies nothing.
        assertTrue("resources module", manifest.contains("\"type\": \"resources\""));
        assertTrue("header version", manifest.contains("\"version\": [1, 0, 0]"));
        assertTrue("uuid differs from the pack uuid",
                !CapeResourcePackBuilder.PACK_UUID.equals(CapeResourcePackBuilder.moduleUuid()));
    }

    @Test
    public void packUuidIsStableSoReapplyingReplacesInPlace() {
        // A random uuid per build would accumulate one pack per cape change in the instance.
        assertEquals("b7c1a5e2-3d4f-4a6b-9c8d-1e2f3a4b5c6d", CapeResourcePackBuilder.PACK_UUID);
        assertNotNull(CapeResourcePackBuilder.moduleUuid());
    }

    @Test
    public void aBlankPackIsFullyTransparent() {
        int[] pixels = decodeToPixels(CapeTexturePainter.paint(0x00000000, 0x00000000, false));
        for (int pixel : pixels) {
            assertEquals("blank cape must be transparent", 0, pixel >>> 24);
        }
    }

    @Test
    public void theClothRectIsPaintedOpaque() {
        int[] pixels = decodeToPixels(CapeTexturePainter.paint(0xFF6236E8, 0xFFA88CFF, true));

        // The visible cloth is the 10x16 rect at (12,1); every pixel of it must carry colour,
        // or the cape shows holes where the base texture shows through.
        for (int y = CapeTexturePainter.CLOTH_Y; y < CapeTexturePainter.CLOTH_Y + CapeTexturePainter.CLOTH_HEIGHT; y++) {
            for (int x = CapeTexturePainter.CLOTH_X; x < CapeTexturePainter.CLOTH_X + CapeTexturePainter.CLOTH_WIDTH; x++) {
                int pixel = pixels[y * CapeTexturePainter.TEXTURE_WIDTH + x];
                assertEquals("alpha at " + x + "," + y, 0xFF, pixel >>> 24);
            }
        }
    }

    @Test
    public void shadeDarkensWithoutTouchingAlpha() {
        int shaded = CapeTexturePainter.shade(0xFF808080, 0.5f);
        assertEquals(0xFF, shaded >>> 24);
        assertEquals(0x40, (shaded >>> 16) & 0xFF);
        assertEquals(0x40, (shaded >>> 8) & 0xFF);
        assertEquals(0x40, shaded & 0xFF);
    }

    @Test
    public void shadeClampsRatherThanWrapping() {
        // A factor above 1 must not overflow a channel into the next one.
        int bright = CapeTexturePainter.shade(0xFFF0F0F0, 4f);
        assertEquals(0xFF, (bright >>> 16) & 0xFF);
        assertEquals(0xFF, (bright >>> 8) & 0xFF);
        assertEquals(0xFF, bright & 0xFF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encodeRejectsAMismatchedBuffer() {
        PngWriter.encode(64, 32, new int[10]);
    }

    private static int[] decodeToPixels(byte[] png) {
        int idatStart = findChunk(png, "IDAT");
        int length = readInt(png, idatStart - 8);
        byte[] deflated = new byte[length];
        System.arraycopy(png, idatStart, deflated, 0, length);

        byte[] raw;
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(deflated))) {
            raw = inflater.readAllBytes();
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        int[] pixels = new int[CapeTexturePainter.TEXTURE_WIDTH * CapeTexturePainter.TEXTURE_HEIGHT];
        for (int y = 0; y < CapeTexturePainter.TEXTURE_HEIGHT; y++) {
            int rowStart = y * (1 + CapeTexturePainter.TEXTURE_WIDTH * 4);
            for (int x = 0; x < CapeTexturePainter.TEXTURE_WIDTH; x++) {
                int at = rowStart + 1 + x * 4;
                pixels[y * CapeTexturePainter.TEXTURE_WIDTH + x] =
                        ((raw[at + 3] & 0xFF) << 24) | ((raw[at] & 0xFF) << 16)
                                | ((raw[at + 1] & 0xFF) << 8) | (raw[at + 2] & 0xFF);
            }
        }
        return pixels;
    }

    /** Returns the offset just past the chunk type, i.e. where its data begins. */
    private static int findChunk(byte[] png, String type) {
        byte[] needle = type.getBytes(StandardCharsets.US_ASCII);
        for (int i = 8; i < png.length - 4; i++) {
            if (png[i] == needle[0] && png[i + 1] == needle[1]
                    && png[i + 2] == needle[2] && png[i + 3] == needle[3]) {
                return i + 4;
            }
        }
        throw new AssertionError("chunk not found: " + type);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
