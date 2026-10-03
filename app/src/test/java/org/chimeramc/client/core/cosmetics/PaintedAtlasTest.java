package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

/**
 * Pins the shaded atlas painter. The bug this guards is the flat blob: a single solid colour per
 * region gives a pet or hat no form and no material, which is what made them read as flat shapes.
 * These tests assert there is real shading (a top row brighter than a bottom row) and real surface
 * texture, without Android or a device.
 */
public class PaintedAtlasTest {

    private static final int W = 64;
    private static final int H = 64;
    private static final int ACCENT_START = 32;

    private static int[] decodeToPixels(byte[] png) {
        int idatStart = findChunk(png, "IDAT");
        int length = readInt(png, idatStart - 8);
        byte[] deflated = new byte[length];
        System.arraycopy(png, idatStart, deflated, 0, length);

        byte[] raw;
        try (InflaterInputStream inflater =
                     new InflaterInputStream(new ByteArrayInputStream(deflated))) {
            raw = inflater.readAllBytes();
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        int[] pixels = new int[W * H];
        for (int y = 0; y < H; y++) {
            int rowStart = y * (1 + W * 4);
            for (int x = 0; x < W; x++) {
                int at = rowStart + 1 + x * 4;
                pixels[y * W + x] = ((raw[at + 3] & 0xFF) << 24) | ((raw[at] & 0xFF) << 16)
                        | ((raw[at + 1] & 0xFF) << 8) | (raw[at + 2] & 0xFF);
            }
        }
        return pixels;
    }

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

    private static int luma(int argb) {
        return (int) (0.299 * ((argb >> 16) & 0xFF)
                + 0.587 * ((argb >> 8) & 0xFF)
                + 0.114 * (argb & 0xFF));
    }

    /** The whole point: the top of a region is brighter than its bottom, so a face has form. */
    @Test
    public void eachRegionIsShadedTopToBottom() {
        int[] pixels = decodeToPixels(PaintedAtlas.paint(
                0xFF6236E8, 0xFFA88CFF, W, H, ACCENT_START, 7));

        int baseTop = averageRow(pixels, 2);
        int baseBottom = averageRow(pixels, ACCENT_START - 3);
        assertTrue("base region top must be brighter than its bottom: "
                + baseTop + " vs " + baseBottom, baseTop > baseBottom);

        int accentTop = averageRow(pixels, ACCENT_START + 2);
        int accentBottom = averageRow(pixels, H - 3);
        assertTrue("accent region top must be brighter than its bottom: "
                + accentTop + " vs " + accentBottom, accentTop > accentBottom);
    }

    /** A region must not be one flat colour: neighbouring pixels must vary (surface texture). */
    @Test
    public void theSurfaceHasVisibleTexture() {
        int[] pixels = decodeToPixels(PaintedAtlas.paint(
                0xFF6236E8, 0xFFA88CFF, W, H, ACCENT_START, 7));
        // Sample one row well inside the base region; its pixels must not all be identical.
        int row = 8;
        int first = pixels[row * W];
        boolean varied = false;
        for (int x = 1; x < W; x++) {
            if (pixels[row * W + x] != first) {
                varied = true;
                break;
            }
        }
        assertTrue("a shaded atlas must not be a flat fill", varied);
    }

    /** The accent region must actually differ from the base, or the two-tone parts vanish. */
    @Test
    public void baseAndAccentRegionsDiffer() {
        int[] pixels = decodeToPixels(PaintedAtlas.paint(
                0xFF6236E8, 0xFFA88CFF, W, H, ACCENT_START, 7));
        assertNotEquals(pixels[4 * W + 4], pixels[(ACCENT_START + 4) * W + 4]);
    }

    /** Two different seeds must not produce the same grain, or every species looks alike. */
    @Test
    public void differentSeedsProduceDifferentGrain() {
        byte[] a = PaintedAtlas.paint(0xFF6236E8, 0xFFA88CFF, W, H, ACCENT_START, 1);
        byte[] b = PaintedAtlas.paint(0xFF6236E8, 0xFFA88CFF, W, H, ACCENT_START, 2);
        boolean differ = false;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) { differ = true; break; }
        }
        assertTrue("different seeds should change the grain", differ);
    }

    /** Alpha stays opaque; the geometry samples a solid region, never a translucent one. */
    @Test
    public void everyPixelIsOpaque() {
        int[] pixels = decodeToPixels(PaintedAtlas.paint(
                0xFF6236E8, 0xFFA88CFF, W, H, ACCENT_START, 7));
        for (int p : pixels) {
            assertEquals(0xFF, (p >>> 24) & 0xFF);
        }
    }

    private static int averageRow(int[] pixels, int y) {
        long sum = 0;
        for (int x = 0; x < W; x++) sum += luma(pixels[y * W + x]);
        return (int) (sum / W);
    }
}
