package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.antegg.AntEggPackage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pins the port orchestration with hand-written test doubles for the two external seams.
 *
 * <p>The doubles are real implementations of {@link Decompiler} and {@link LlmClient} that return
 * fixed values; they are not mocks of the code under test. The point of this test is the ordering
 * the spec implies: a cheat or an unsupported mod must be refused <em>before</em> the decompiler or
 * the LLM is touched, so the doubles record whether they were called.
 */
public class JavaPorterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** A decompiler double that returns fixed source and records that it ran. */
    private static final class FakeDecompiler implements Decompiler {
        boolean called;
        Result next = Result.ok("class CoolMod {}", 1);

        @Override
        public Result decompile(File jar, List<String> classEntries, int maxClasses) {
            called = true;
            return next;
        }
    }

    /** An LLM double that returns a fixed reply and records that it ran. */
    private static final class FakeLlm implements LlmClient {
        boolean called;
        Result next = Result.ok("LANG: lua\nSUMMARY: done\n===CODE===\nprint('ported')\n");

        @Override
        public Result complete(String systemInstruction, String userPrompt, boolean userInitiated) {
            called = true;
            if (!userInitiated) return Result.fail("not user initiated");
            return next;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }

    private File jar(String name, String[][] entries) throws IOException {
        File file = new File(folder.getRoot(), name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String[] entry : entries) {
                zip.putNextEntry(new ZipEntry(entry[0]));
                zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    private static final String FABRIC = "{\"id\":\"coolmod\",\"name\":\"Cool Mod\","
            + "\"version\":\"1.0.0\",\"authors\":[\"Alice\"]}";

    private static String[][] smallMod() {
        return new String[][]{
                {"fabric.mod.json", FABRIC},
                {"com/example/CoolMod.class", "x"},
                {"data/coolmod/recipes/sword.json", "{}"},
        };
    }

    @Test
    public void aCheatModIsRefusedBeforeDecompilingOrCallingTheLlm() throws IOException {
        File file = jar("cheat.jar", new String[][]{
                {"fabric.mod.json", "{\"id\":\"killaura\",\"name\":\"Kill Aura\"}"},
                {"com/example/Cheat.class", "x"},
        });
        FakeDecompiler decompiler = new FakeDecompiler();
        FakeLlm llm = new FakeLlm();
        JavaPorter porter = new JavaPorter(decompiler, llm);

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertFalse(result.success);
        assertTrue(result.wasRefusedAsCheat());
        assertEquals("kill aura", result.refusedCheat);
        assertFalse("the decompiler must not run for a refused mod", decompiler.called);
        assertFalse("the LLM must not be called for a refused mod", llm.called);
    }

    @Test
    public void anUnsupportedModIsRefusedBeforeDecompiling() throws IOException {
        File file = jar("native.jar", new String[][]{
                {"fabric.mod.json", FABRIC},
                {"com/example/CoolMod.class", "x"},
                {"lib/x86_64/libfoo.so", "x"},
        });
        FakeDecompiler decompiler = new FakeDecompiler();
        FakeLlm llm = new FakeLlm();
        JavaPorter porter = new JavaPorter(decompiler, llm);

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertFalse(result.success);
        assertTrue(result.report.isUnsupported());
        assertFalse(decompiler.called);
        assertFalse(llm.called);
    }

    @Test
    public void aPortableModIsDecompiledPortedAndPackaged() throws IOException {
        File file = jar("cool.jar", smallMod());
        FakeDecompiler decompiler = new FakeDecompiler();
        FakeLlm llm = new FakeLlm();
        JavaPorter porter = new JavaPorter(decompiler, llm);

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertTrue(result.error, result.success);
        assertTrue(decompiler.called);
        assertTrue(llm.called);
        assertNotNull(result.packageFile);
        assertTrue(result.packageFile.isFile());
        assertTrue(result.packageFile.getName().endsWith(".antegg"));
        assertEquals("print('ported')", result.generatedSource);
    }

    @Test
    public void theGeneratedPackageLoadsThroughTheNormalReader() throws IOException {
        File file = jar("cool.jar", smallMod());
        JavaPorter porter = new JavaPorter(new FakeDecompiler(), new FakeLlm());

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertTrue(result.success);
        AntEggPackage.Result inspected = AntEggPackage.inspect(result.packageFile);
        assertTrue(inspected.isValid());
        assertTrue("a ported mod must be marked aiPorted", inspected.manifest.aiPorted);
        assertEquals("JavaBridge (AI port)", inspected.manifest.author);
        assertEquals("coolmod 1.0.0", inspected.manifest.originMod);
    }

    @Test
    public void aMalformedLlmReplyIsAFailureNotAPackage() throws IOException {
        File file = jar("cool.jar", smallMod());
        FakeLlm llm = new FakeLlm();
        llm.next = LlmClient.Result.ok("I could not port this, sorry.");
        JavaPorter porter = new JavaPorter(new FakeDecompiler(), llm);

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertFalse(result.success);
        assertNotNull(result.error);
        assertNull(result.packageFile);
    }

    @Test
    public void anLlmNetworkFailureIsReported() throws IOException {
        File file = jar("cool.jar", smallMod());
        FakeLlm llm = new FakeLlm();
        llm.next = LlmClient.Result.fail("could not reach the LLM provider");
        JavaPorter porter = new JavaPorter(new FakeDecompiler(), llm);

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertFalse(result.success);
        assertTrue(result.error.contains("could not reach"));
    }

    @Test
    public void aDecompilerFailureIsReported() throws IOException {
        File file = jar("cool.jar", smallMod());
        FakeDecompiler decompiler = new FakeDecompiler();
        decompiler.next = Decompiler.Result.fail("decompilation failed");
        FakeLlm llm = new FakeLlm();
        JavaPorter porter = new JavaPorter(decompiler, llm);

        JavaPorter.Result result = porter.port(file, folder.newFolder("out"));
        assertFalse(result.success);
        assertFalse("the LLM must not run when decompilation failed", llm.called);
    }

    @Test
    public void assessDoesNoNetworkOrDecompilation() throws IOException {
        File file = jar("cool.jar", smallMod());
        FakeDecompiler decompiler = new FakeDecompiler();
        FakeLlm llm = new FakeLlm();
        JavaPorter porter = new JavaPorter(decompiler, llm);

        JavaPorter.Assessment assessment = porter.assess(file);
        assertNotNull(assessment.report);
        assertTrue(assessment.canPort());
        assertFalse(decompiler.called);
        assertFalse(llm.called);
    }

    @Test
    public void sanitizeVersionMakesAnUnpaddedVersionSemantic() {
        assertEquals("1.2.0", JavaPorter.sanitizeVersion("1.2"));
        assertEquals("1.0.0", JavaPorter.sanitizeVersion("v1"));
        assertEquals("1.2.3", JavaPorter.sanitizeVersion("1.2.3"));
        assertEquals("1.0.0", JavaPorter.sanitizeVersion("snapshot"));
    }

    @Test
    public void collectSymbolsFindsMappedApisInSource() {
        List<String> symbols = JavaPorter.collectSymbols(
                "Registry.register(Registries.ITEM, new Item.Settings());");
        assertTrue(symbols.contains("Registry.register"));
        assertTrue(symbols.contains("Registries.ITEM"));
    }
}
