package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
 * Pins the portability score, the keep/partial/drop diff and the unsupported list.
 *
 * <p>The score is the user's first decision point, so the tests assert the two things that matter:
 * a hard blocker (native libraries, no metadata) is "Not Supported" rather than a low score, and
 * the diff names every category the spec calls out.
 */
public class PortabilityReportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

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

    @Test
    public void aSmallDataDrivenModScoresHigh() throws IOException {
        File file = jar("small.jar", new String[][]{
                {"fabric.mod.json", FABRIC},
                {"com/example/CoolMod.class", "x"},
                {"data/coolmod/recipes/sword.json", "{}"},
        });
        PortabilityReport report = PortabilityReport.of(JarInspector.inspect(file));
        assertEquals(PortabilityReport.SCORE_HIGH, report.score);
        assertEquals("High", report.scoreLabel);
        assertTrue(report.canPort());
    }

    @Test
    public void aModWithNativeLibrariesIsNotSupported() throws IOException {
        File file = jar("native.jar", new String[][]{
                {"fabric.mod.json", FABRIC},
                {"com/example/CoolMod.class", "x"},
                {"lib/x86_64/libfoo.so", "x"},
        });
        PortabilityReport report = PortabilityReport.of(JarInspector.inspect(file));
        assertTrue(report.isUnsupported());
        assertEquals("Not Supported", report.scoreLabel);
        assertFalse(report.canPort());
        assertFalse(report.blockers.isEmpty());
    }

    @Test
    public void aJarWithNoMetadataIsNotSupported() throws IOException {
        File file = jar("bare.jar", new String[][]{
                {"com/example/Thing.class", "x"},
        });
        PortabilityReport report = PortabilityReport.of(JarInspector.inspect(file));
        assertTrue(report.isUnsupported());
        assertTrue(report.blockers.get(0).contains("metadata"));
    }

    @Test
    public void aMixinHeavyModScoresLow() throws IOException {
        String[][] entries = new String[30][];
        entries[0] = new String[]{"fabric.mod.json", FABRIC};
        entries[1] = new String[]{"mixmod.mixins.json", "{}"};
        for (int i = 2; i < 30; i++) {
            entries[i] = new String[]{"com/example/Class" + i + ".class", "x"};
        }
        File file = jar("mix.jar", entries);
        PortabilityReport report = PortabilityReport.of(JarInspector.inspect(file));
        assertEquals(PortabilityReport.SCORE_LOW, report.score);
        assertEquals("Low", report.scoreLabel);
        assertTrue(report.canPort());
    }

    @Test
    public void theDiffNamesEveryCategoryTheSpecRequires() throws IOException {
        File file = jar("cool.jar", new String[][]{
                {"fabric.mod.json", FABRIC},
                {"com/example/CoolMod.class", "x"},
                {"data/coolmod/recipes/sword.json", "{}"},
                {"assets/coolmod/textures/item/sword.png", "x"},
        });
        PortabilityReport report = PortabilityReport.of(JarInspector.inspect(file));
        assertTrue(hasFeature(report, "Custom items / blocks"));
        assertTrue(hasFeature(report, "Tick events"));
        assertTrue(hasFeature(report, "Commands"));
        assertTrue(hasFeature(report, "Data-driven recipes / loot tables"));
        assertTrue(hasFeature(report, "Client textures / models"));
    }

    @Test
    public void mixinsAreReportedAsDropped() throws IOException {
        File file = jar("mix.jar", new String[][]{
                {"fabric.mod.json", FABRIC},
                {"mixmod.mixins.json", "{}"},
                {"com/example/CoolMod.class", "x"},
        });
        PortabilityReport report = PortabilityReport.of(JarInspector.inspect(file));
        boolean droppedMixin = false;
        for (PortabilityReport.Item item : report.droppedItems()) {
            if (item.feature.contains("Mixin")) droppedMixin = true;
        }
        assertTrue(droppedMixin);
    }

    @Test
    public void anUnsupportedReportStillExplainsItself() {
        PortabilityReport report = PortabilityReport.of(null);
        assertTrue(report.isUnsupported());
        assertFalse(report.items.isEmpty());
        assertFalse(report.summary.isEmpty());
    }

    @Test
    public void aNullInspectionDoesNotThrow() {
        assertNotNull(PortabilityReport.of(null).blockers);
    }

    @Test
    public void labelsMatchTheScoreConstants() {
        assertEquals("High", PortabilityReport.labelFor(PortabilityReport.SCORE_HIGH));
        assertEquals("Medium", PortabilityReport.labelFor(PortabilityReport.SCORE_MEDIUM));
        assertEquals("Low", PortabilityReport.labelFor(PortabilityReport.SCORE_LOW));
        assertEquals("Not Supported",
                PortabilityReport.labelFor(PortabilityReport.SCORE_UNSUPPORTED));
    }

    @Test
    public void theReportListsTheApiMapping() {
        PortabilityReport report = PortabilityReport.of(null);
        assertFalse(report.mappedApis.isEmpty());
    }

    private static boolean hasFeature(PortabilityReport report, String feature) {
        for (PortabilityReport.Item item : report.items) {
            if (item.feature.equals(feature)) return true;
        }
        return false;
    }
}
