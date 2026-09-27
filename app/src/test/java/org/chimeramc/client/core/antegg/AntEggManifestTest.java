package org.chimeramc.client.core.antegg;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the .AntEgg manifest grammar and the archive extractor against real files on disk.
 *
 * <p>Both are the trust boundary of an import format, so the tests exercise the cases that decide
 * whether a hostile or broken package is accepted: type mismatches, a missing entry point, a ZIP
 * entry that tries to escape the sandbox, and a missing dependency.
 */
public class AntEggManifestTest {

    private static final String VALID = "{"
            + "\"name\":\"Cool Mod\","
            + "\"version\":\"1.2.3\","
            + "\"author\":\"Someone\","
            + "\"type\":\"native\","
            + "\"entry_point\":\"bin/libcool.so\","
            + "\"dependencies\":[\"Core Lib\"]"
            + "}";

    @Test
    public void parsesAValidManifest() {
        AntEggManifest.Result result = AntEggManifest.parse(VALID);
        assertTrue(result.error, result.isValid());
        AntEggManifest manifest = result.manifest;
        assertEquals("Cool Mod", manifest.name);
        assertEquals("cool-mod", manifest.id());
        assertEquals("1.2.3", manifest.version);
        assertEquals("Someone", manifest.author);
        assertTrue(manifest.isNative());
        assertFalse(manifest.isScript());
        assertEquals("bin/libcool.so", manifest.entryPoint);
        assertEquals(1, manifest.dependencies.size());
        assertEquals("Core Lib", manifest.dependencies.get(0));
    }

    @Test
    public void scriptTypeRequiresALuaEntryPoint() {
        String json = VALID.replace("\"native\"", "\"script\"")
                .replace("bin/libcool.so", "scripts/main.lua");
        assertTrue(AntEggManifest.parse(json).isValid());

        String wrong = json.replace("scripts/main.lua", "bin/mod.so");
        assertFalse(AntEggManifest.parse(wrong).isValid());
    }

    @Test
    public void nativeTypeRequiresASoEntryPoint() {
        String wrong = VALID.replace("bin/libcool.so", "scripts/main.lua");
        assertFalse(AntEggManifest.parse(wrong).isValid());
    }

    @Test
    public void rejectsMissingFields() {
        assertFalse(AntEggManifest.parse("{}").isValid());
        assertFalse(AntEggManifest.parse("{\"name\":\"x\"}").isValid());
        String noEntry = VALID.replace("\"entry_point\":\"bin/libcool.so\",", "");
        assertFalse(AntEggManifest.parse(noEntry).isValid());
    }

    @Test
    public void rejectsNonSemanticVersion() {
        assertFalse(AntEggManifest.parse(VALID.replace("1.2.3", "v1.2")).isValid());
        assertFalse(AntEggManifest.parse(VALID.replace("1.2.3", "one.two.three")).isValid());
        // Pre-release suffixes are allowed.
        assertTrue(AntEggManifest.parse(VALID.replace("1.2.3", "1.2.3-beta.1")).isValid());
    }

    @Test
    public void rejectsAnEntryPointThatEscapesThePackage() {
        assertFalse(AntEggManifest.parse(VALID.replace("bin/libcool.so", "../evil.so")).isValid());
        assertFalse(AntEggManifest.parse(VALID.replace("bin/libcool.so", "/bin/evil.so")).isValid());
    }

    @Test
    public void rejectsMalformedJson() {
        assertFalse(AntEggManifest.parse("not json").isValid());
        assertFalse(AntEggManifest.parse("[1,2,3]").isValid());
        assertFalse(AntEggManifest.parse("{\"name\": \"x\" ").isValid());
    }

    @Test
    public void rejectsNonStringDependencies() {
        String json = VALID.replace("[\"Core Lib\"]", "[1]");
        assertFalse(AntEggManifest.parse(json).isValid());
    }

    // --- package extraction ----------------------------------------------------------------

    private File zipWith(String entryName, String content) throws Exception {
        File zip = File.createTempFile("antegg", ".antegg");
        zip.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    private File sandbox() throws Exception {
        File dir = Files.createTempDirectory("antegg-sandbox").toFile();
        dir.deleteOnExit();
        return dir;
    }

    @Test
    public void extractsAValidPackageIntoTheSandbox() throws Exception {
        File zip = File.createTempFile("antegg", ".antegg");
        zip.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("egg.json"));
            out.write(VALID.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("bin/libcool.so"));
            out.write(new byte[]{1, 2, 3, 4});
            out.closeEntry();
        }

        File root = sandbox();
        AntEggPackage.Result result = AntEggPackage.extract(zip, root);

        assertTrue(result.error, result.isValid());
        assertNotNull(result.directory);
        assertEquals("cool-mod", result.directory.getName());
        assertTrue(new File(result.directory, "bin/libcool.so").isFile());
    }

    @Test
    public void rejectsAnEntryThatEscapesTheSandbox() throws Exception {
        File zip = File.createTempFile("antegg", ".antegg");
        zip.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("egg.json"));
            out.write(VALID.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("../escaped.so"));
            out.write(new byte[]{1});
            out.closeEntry();
            out.putNextEntry(new ZipEntry("bin/libcool.so"));
            out.write(new byte[]{1});
            out.closeEntry();
        }

        File root = sandbox();
        AntEggPackage.Result result = AntEggPackage.extract(zip, root);
        assertFalse(result.isValid());
        assertNull(result.manifest);
        // The hostile entry must not exist anywhere near the sandbox.
        assertFalse(new File(root.getParentFile(), "escaped.so").exists());
    }

    @Test
    public void rejectsAPackageMissingItsEntryPoint() throws Exception {
        File zip = File.createTempFile("antegg", ".antegg");
        zip.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("egg.json"));
            out.write(VALID.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        AntEggPackage.Result result = AntEggPackage.extract(zip, sandbox());
        assertFalse(result.isValid());
        assertTrue(result.error.contains("entry_point"));
    }

    @Test
    public void inspectDoesNotWriteAnything() throws Exception {
        File zip = zipWith("egg.json", VALID);
        File root = sandbox();
        AntEggPackage.Result result = AntEggPackage.inspect(zip);
        assertTrue(result.isValid());
        assertEquals(0, root.listFiles().length);
    }

    @Test
    public void missingDependenciesAreReported() throws Exception {
        File root = sandbox();
        AntEggManifest.Result parsed = AntEggManifest.parse(VALID);
        assertNotNull(AntEggLoader.missingDependencies(root, parsed.manifest));

        // With the dependency installed (same slug), the check passes.
        assertTrue(new File(root, "core-lib").mkdirs());
        assertNull(AntEggLoader.missingDependencies(root, parsed.manifest));
    }
}
