package org.chimeramc.client.core.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Covers {@link SkinPackBuilder}: the validation rules, the legacy conversion and the pack
 * structure, all on the JVM (the builder is deliberately Android-free).
 */
public class SkinPackBuilderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static int[] solid(final int color, final int count) {
        int[] pixels = new int[count];
        Arrays.fill(pixels, color);
        return pixels;
    }

    private static SkinPackBuilder.SkinEntry entry(String name, int[] argb, int w, int h) {
        return new SkinPackBuilder.SkinEntry(name, argb, w, h);
    }

    @Test
    public void accepts64And128() {
        assertTrue(SkinPackBuilder.isAcceptableSize(64, 64));
        assertTrue(SkinPackBuilder.isAcceptableSize(128, 128));
        assertTrue(SkinPackBuilder.isAcceptableSize(64, 32));
        assertTrue(SkinPackBuilder.isAcceptableSize(128, 64));
        assertFalse(SkinPackBuilder.isAcceptableSize(32, 32));
        assertFalse(SkinPackBuilder.isAcceptableSize(64, 128));
    }

    @Test
    public void rejectsAnOddSizedImageWithTheUserFacingMessage() {
        try {
            SkinPackBuilder.validateAndNormalise(entry("odd", solid(0xFFFFFFFF, 32 * 32), 32, 32));
            org.junit.Assert.fail("expected InvalidSkinException");
        } catch (SkinPackBuilder.InvalidSkinException expected) {
            assertEquals("Skins must be 64x64 or 128x128 PNG", expected.getMessage());
        }
    }

    @Test
    public void a64SkinPassesThroughUnchanged() throws Exception {
        int[] skin = solid(0xFF112233, 64 * 64);
        int[] normalised = SkinPackBuilder.validateAndNormalise(entry("a", skin, 64, 64));
        assertEquals(64 * 64, normalised.length);
        assertEquals(0xFF112233, normalised[100]);
    }

    @Test
    public void anHdSkinIsDownsampledTo64() throws Exception {
        int[] hd = solid(0xFF445566, 128 * 128);
        int[] normalised = SkinPackBuilder.validateAndNormalise(entry("hd", hd, 128, 128));
        assertEquals(64 * 64, normalised.length);
        assertEquals(0xFF445566, normalised[0]);
    }

    /**
     * The legacy conversion tucks the modern left limbs into the bottom half by mirroring the
     * legacy right limbs. The bottom half of a legacy skin is all zeros, so any non-zero pixel
     * below row 32 proves the mirror ran.
     */
    @Test
    public void aLegacySkinGetsItsLimbsMirroredIntoTheBottomHalf() throws Exception {
        int[] legacy = new int[64 * 32];
        // Fill only the legacy right arm region (40..55, 16..31) with a known colour.
        for (int y = 16; y < 32; y++) {
            for (int x = 40; x < 56; x++) {
                legacy[y * 64 + x] = 0xFFAA0000;
            }
        }
        int[] normalised = SkinPackBuilder.validateAndNormalise(entry("legacy", legacy, 64, 32));
        assertEquals(64 * 64, normalised.length);
        // The source region ended up in the bottom half.
        assertEquals(0xFFAA0000, normalised[48 * 64 + 32]);
        assertEquals(0xFFAA0000, normalised[48 * 64 + 48]);
        // And the top half still has the original.
        assertEquals(0xFFAA0000, normalised[16 * 64 + 40]);
    }

    @Test
    public void buildsAValidPackWithManifestSkinsAndLang() throws Exception {
        File parent = folder.newFolder("skin_packs");
        int[] skin = solid(0xFF00FF00, 64 * 64);
        List<SkinPackBuilder.SkinEntry> entries = new ArrayList<>();
        entries.add(entry("Hero", skin, 64, 64));

        SkinPackBuilder.BuiltPack built = SkinPackBuilder.build(parent, "My Skin", entries, false);

        assertNotNull(built.directory);
        assertTrue(built.directory.isDirectory());
        assertEquals(1, built.skinCount);

        String manifest = read(new File(built.directory, "manifest.json"));
        assertTrue(manifest.contains("\"type\": \"skin_pack\""));
        assertTrue(manifest.contains(built.uuid));

        String skins = read(new File(built.directory, "skins.json"));
        assertTrue(skins.contains("\"type\": \"free\""));
        assertTrue(skins.contains("\"texture\": \"skin_0.png\""));
        assertTrue(skins.contains(SkinPackBuilder.GEOMETRY_CLASSIC));

        String lang = read(new File(built.directory, "texts/en_US.lang"));
        assertTrue(lang.contains("Hero"));

        assertTrue(new File(built.directory, "skin_0.png").exists());
        assertTrue(new File(built.directory, "pack_icon.png").exists());
    }

    @Test
    public void slimArmModelSelectsTheSlimGeometry() throws Exception {
        File parent = folder.newFolder("skin_packs_slim");
        List<SkinPackBuilder.SkinEntry> entries = new ArrayList<>();
        entries.add(entry("Alex", solid(0xFF123456, 64 * 64), 64, 64));

        SkinPackBuilder.BuiltPack built = SkinPackBuilder.build(parent, "Slim", entries, true);

        String skins = read(new File(built.directory, "skins.json"));
        assertTrue(skins.contains(SkinPackBuilder.GEOMETRY_SLIM));
        assertFalse(skins.contains("\"geometry\": \"" + SkinPackBuilder.GEOMETRY_CLASSIC + "\""));
    }

    @Test
    public void multipleImagesGoIntoOnePack() throws Exception {
        File parent = folder.newFolder("skin_packs_multi");
        List<SkinPackBuilder.SkinEntry> entries = new ArrayList<>();
        entries.add(entry("One", solid(0xFF000001, 64 * 64), 64, 64));
        entries.add(entry("Two", solid(0xFF000002, 64 * 64), 64, 64));

        SkinPackBuilder.BuiltPack built = SkinPackBuilder.build(parent, "Set", entries, false);

        assertEquals(2, built.skinCount);
        assertTrue(new File(built.directory, "skin_0.png").exists());
        assertTrue(new File(built.directory, "skin_1.png").exists());
        String skins = read(new File(built.directory, "skins.json"));
        assertTrue(skins.contains("\"localization_name\": \"Set0\""));
        assertTrue(skins.contains("\"localization_name\": \"Set1\""));
    }

    @Test
    public void localizationKeysStripSpacesAndPunctuation() {
        assertEquals("MySkin", SkinPackBuilder.sanitizeLocalization("My Skin!"));
        assertEquals("skin", SkinPackBuilder.sanitizeLocalization("!!!"));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
