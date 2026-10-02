package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pins the jar inspection against hand-built jars.
 *
 * <p>The jars are written by the test, so the structure is exact and no fixture has to be checked
 * into the tree. This is what proves the portability decisions are made from the archive's real
 * contents — the loader metadata, the class count, the native libraries, the mixin config — rather
 * than from a filename.
 */
public class JarInspectorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File writeJar(String name, String[][] entries) throws IOException {
        File jar = new File(folder.getRoot(), name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    private static String[][] fabricJar() {
        return new String[][]{
                {"fabric.mod.json", "{\"id\":\"coolmod\",\"name\":\"Cool Mod\",\"version\":\"1.0.0\","
                        + "\"authors\":[\"Alice\"],\"description\":\"Adds things\"}"},
                {"com/example/coolmod/CoolMod.class", "x"},
                {"com/example/coolmod/ItemRegistry.class", "x"},
                {"data/coolmod/recipes/sword.json", "{}"},
                {"assets/coolmod/textures/item/sword.png", "x"},
        };
    }

    @Test
    public void readsFabricMetadataAndCountsClasses() throws IOException {
        File jar = writeJar("coolmod.jar", fabricJar());
        JarInspector.Inspection inspection = JarInspector.inspect(jar);
        assertTrue(inspection.isValid());
        assertNotNull(inspection.manifest);
        assertEquals("coolmod", inspection.manifest.id);
        assertEquals(2, inspection.classCount);
        assertEquals(2, inspection.ownClassCount);
        assertFalse(inspection.hasMixins);
        assertTrue(inspection.hasDataFiles);
        assertTrue(inspection.hasClientAssets);
        assertTrue(inspection.nativeLibraries.isEmpty());
    }

    @Test
    public void findsTheModsOwnPackageRoots() throws IOException {
        File jar = writeJar("coolmod.jar", fabricJar());
        JarInspector.Inspection inspection = JarInspector.inspect(jar);
        assertTrue(inspection.packageRoots.contains("com/example"));
    }

    @Test
    public void detectsBundledNativeLibraries() throws IOException {
        File jar = writeJar("nat.jar", new String[][]{
                {"fabric.mod.json", "{\"id\":\"nat\",\"name\":\"Nat\"}"},
                {"com/example/Nat.class", "x"},
                {"lib/x86_64/libfoo.so", "x"},
                {"lib/arm64/libbar.dylib", "x"},
        });
        JarInspector.Inspection inspection = JarInspector.inspect(jar);
        assertEquals(2, inspection.nativeLibraries.size());
        assertTrue(inspection.nativeLibraries.contains("lib/x86_64/libfoo.so"));
    }

    @Test
    public void detectsMixinConfigs() throws IOException {
        File jar = writeJar("mix.jar", new String[][]{
                {"fabric.mod.json", "{\"id\":\"mix\",\"name\":\"Mix\"}"},
                {"mixmod.mixins.json", "{}"},
                {"com/example/Mix.class", "x"},
        });
        assertTrue(JarInspector.inspect(jar).hasMixins);
    }

    @Test
    public void detectsMixinClassesWithoutAConfig() throws IOException {
        File jar = writeJar("mix2.jar", new String[][]{
                {"fabric.mod.json", "{\"id\":\"mix2\",\"name\":\"Mix2\"}"},
                {"com/example/mixin/PlayerMixin.class", "x"},
        });
        assertTrue(JarInspector.inspect(jar).hasMixins);
    }

    @Test
    public void doesNotCountShadedLibraryClassesAsTheModsOwn() throws IOException {
        File jar = writeJar("shaded.jar", new String[][]{
                {"fabric.mod.json", "{\"id\":\"shaded\",\"name\":\"Shaded\"}"},
                {"com/example/Shaded.class", "x"},
                {"com/google/gson/Gson.class", "x"},
                {"kotlin/jvm/internal/Intrinsics.class", "x"},
                {"net/minecraft/item/Item.class", "x"},
        });
        JarInspector.Inspection inspection = JarInspector.inspect(jar);
        assertEquals(4, inspection.classCount);
        assertEquals(1, inspection.ownClassCount);
    }

    @Test
    public void readsForgeMetadataFromTheMetaInfFolder() throws IOException {
        File jar = writeJar("forge.jar", new String[][]{
                {"META-INF/mods.toml", "modLoader=\"javafml\"\n[[mods]]\nmodId=\"forgeMod\"\n"
                        + "displayName=\"Forge Mod\"\nversion=\"2.0.0\"\n"},
                {"com/example/ForgeMod.class", "x"},
        });
        JarInspector.Inspection inspection = JarInspector.inspect(jar);
        assertTrue(inspection.isValid());
        assertEquals("forgeMod", inspection.manifest.id);
        assertEquals(JavaModManifest.LOADER_FORGE, inspection.manifest.loader);
    }

    @Test
    public void aJarWithNoMetadataIsValidButHasNoManifest() throws IOException {
        File jar = writeJar("bare.jar", new String[][]{
                {"com/example/Bare.class", "x"},
        });
        JarInspector.Inspection inspection = JarInspector.inspect(jar);
        assertTrue(inspection.isValid());
        assertNull(inspection.manifest);
        assertEquals(1, inspection.ownClassCount);
    }

    @Test
    public void aMissingFileIsAnError() {
        JarInspector.Inspection inspection =
                JarInspector.inspect(new File(folder.getRoot(), "nope.jar"));
        assertFalse(inspection.isValid());
        assertNotNull(inspection.error);
    }

    @Test
    public void listsClassEntriesForTheDecompiler() throws IOException {
        File jar = writeJar("coolmod.jar", fabricJar());
        java.util.List<String> classes = JarInspector.classEntries(jar);
        assertEquals(2, classes.size());
        assertTrue(classes.contains("com/example/coolmod/CoolMod.class"));
    }

    @Test
    public void classEntryListIsEmptyForAMissingJar() {
        assertTrue(JarInspector.classEntries(new File(folder.getRoot(), "nope.jar")).isEmpty());
    }
}
