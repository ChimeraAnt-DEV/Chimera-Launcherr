package org.chimeramc.client.core.antegg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exercises the {@code type: "script"} path end to end: the archive is extracted and the entry
 * point is actually executed by the embedded Lua VM. This is the half of the format that used to
 * have no runtime, so a script that loads and reads its {@code mod} table is worth pinning.
 */
public class AntEggScriptLoadingTest {

    private static final String MANIFEST = "{"
            + "\"name\":\"Script Welcomer\","
            + "\"version\":\"2.0.1\","
            + "\"author\":\"Tester\","
            + "\"type\":\"script\","
            + "\"entry_point\":\"scripts/main.lua\","
            + "\"dependencies\":[]"
            + "}";

    private static File sandbox() throws Exception {
        File dir = Files.createTempDirectory("antegg-sandbox").toFile();
        dir.deleteOnExit();
        return dir;
    }

    private static File writePackage(String luaSource) throws Exception {
        File zip = File.createTempFile("antegg", ".antegg");
        zip.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("egg.json"));
            out.write(MANIFEST.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("scripts/main.lua"));
            out.write(luaSource.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    @Test
    public void extractsAndRunsAScriptEntryPoint() throws Exception {
        File root = sandbox();
        File pkg = writePackage("_G.loaded_marker = mod.name .. '|' .. mod.id");

        AntEggPackage.Result extracted = AntEggPackage.extract(pkg, root);
        assertTrue(extracted.error, extracted.isValid());
        File entry = new File(extracted.directory, extracted.manifest.entryPoint);
        assertTrue("entry point must be extracted", entry.isFile());

        AntEggScriptHost.Result run = AntEggScriptHost.run(extracted.directory, extracted.manifest);
        assertTrue(run.error, run.success);
    }

    @Test
    public void aSyntaxErrorIsReportedNotThrown() throws Exception {
        File root = sandbox();
        File pkg = writePackage("this is not lua ((");

        AntEggPackage.Result extracted = AntEggPackage.extract(pkg, root);
        assertTrue(extracted.isValid());
        AntEggScriptHost.Result run = AntEggScriptHost.run(extracted.directory, extracted.manifest);
        assertFalse(run.success);
        assertNotNull(run.error);
    }

    @Test
    public void aRuntimeErrorIsReportedNotThrown() throws Exception {
        File root = sandbox();
        File pkg = writePackage("error('boom')");

        AntEggPackage.Result extracted = AntEggPackage.extract(pkg, root);
        assertTrue(extracted.isValid());
        AntEggScriptHost.Result run = AntEggScriptHost.run(extracted.directory, extracted.manifest);
        assertFalse(run.success);
        assertTrue(run.error.contains("boom"));
    }

    @Test
    public void theModTableExposesIdentityAndPaths() throws Exception {
        // The script asserts on the table itself; a failure here means the host did not populate
        // mod.dir / mod.sandbox / mod.dependencies before running the chunk.
        File root = sandbox();
        File pkg = writePackage(
                "assert(mod.name == 'Script Welcomer', 'name')\n"
                        + "assert(mod.version == '2.0.1', 'version')\n"
                        + "assert(mod.author == 'Tester', 'author')\n"
                        + "assert(mod.id == 'script-welcomer', 'id')\n"
                        + "assert(type(mod.dir) == 'string' and #mod.dir > 0, 'dir')\n"
                        + "assert(type(mod.sandbox) == 'string' and #mod.sandbox > 0, 'sandbox')\n"
                        + "assert(#mod.dependencies == 0, 'deps')");

        AntEggPackage.Result extracted = AntEggPackage.extract(pkg, root);
        assertTrue(extracted.isValid());
        AntEggScriptHost.Result run = AntEggScriptHost.run(extracted.directory, extracted.manifest);
        assertTrue(run.error, run.success);
    }

    @Test
    public void aMissingEntryPointFailsBeforeRunning() throws Exception {
        File root = sandbox();
        AntEggManifest.Result parsed = AntEggManifest.parse(MANIFEST);
        assertTrue(parsed.isValid());
        AntEggScriptHost.Result run = AntEggScriptHost.run(root, parsed.manifest);
        assertFalse(run.success);
        assertTrue(run.error.contains("entry_point"));
    }
}
