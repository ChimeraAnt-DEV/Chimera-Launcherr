package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the Fabric/Forge metadata extraction against realistic fixture text.
 *
 * <p>These fixtures mirror the shapes the real loaders emit — a Fabric manifest with an author
 * array and a {@code depends} object, a Forge {@code mods.toml} with a {@code [[mods]]} table — so a
 * parser that only resembles them cannot pass while the live formats are read wrongly.
 */
public class JavaModManifestTest {

    private static final String FABRIC_JSON = "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"coolmod\",\n"
            + "  \"version\": \"1.2.0\",\n"
            + "  \"name\": \"Cool Mod\",\n"
            + "  \"description\": \"Adds cool things\",\n"
            + "  \"authors\": [\"Alice\", { \"name\": \"Bob\" }],\n"
            + "  \"license\": \"MIT\",\n"
            + "  \"depends\": {\n"
            + "    \"fabricloader\": \">=0.14.0\",\n"
            + "    \"minecraft\": \">=1.20.1\",\n"
            + "    \"java\": \">=17\",\n"
            + "    \"otherlib\": \"*\"\n"
            + "  }\n"
            + "}";

    @Test
    public void parsesFabricMetadata() {
        JavaModManifest.Result result = JavaModManifest.parseFabric(FABRIC_JSON);
        assertTrue(result.isValid());
        JavaModManifest manifest = result.manifest;
        assertEquals("coolmod", manifest.id);
        assertEquals("Cool Mod", manifest.name);
        assertEquals("1.2.0", manifest.version);
        assertEquals("MIT", manifest.license);
        assertEquals(JavaModManifest.LOADER_FABRIC, manifest.loader);
        assertEquals("Adds cool things", manifest.description);
    }

    @Test
    public void joinsFabricAuthorsFromStringsAndObjects() {
        JavaModManifest manifest = JavaModManifest.parseFabric(FABRIC_JSON).manifest;
        assertEquals("Alice, Bob", manifest.author);
    }

    @Test
    public void separatesMinecraftConstraintFromRealDependencies() {
        JavaModManifest manifest = JavaModManifest.parseFabric(FABRIC_JSON).manifest;
        // fabricloader/fabric/java/minecraft are not port dependencies; otherlib is.
        assertEquals(java.util.Collections.singletonList("otherlib"), manifest.dependencies);
        assertEquals(java.util.Collections.singletonList(">=1.20.1"), manifest.minecraftVersions);
    }

    @Test
    public void derivesAnIdFromTheNameWhenTheIdIsMissing() {
        JavaModManifest.Result result = JavaModManifest.parseFabric(
                "{\"name\": \"My Fancy Mod\"}");
        assertTrue(result.isValid());
        assertEquals("my-fancy-mod", result.manifest.id);
    }

    @Test
    public void rejectsAnEmptyFabricManifest() {
        assertFalse(JavaModManifest.parseFabric("").isValid());
        assertFalse(JavaModManifest.parseFabric("   ").isValid());
        assertFalse(JavaModManifest.parseFabric(null).isValid());
    }

    @Test
    public void rejectsMalformedFabricJson() {
        assertFalse(JavaModManifest.parseFabric("{ not json").isValid());
        assertFalse(JavaModManifest.parseFabric("[]").isValid());
    }

    @Test
    public void rejectsFabricJsonWithNeitherIdNorName() {
        assertFalse(JavaModManifest.parseFabric("{\"version\": \"1.0.0\"}").isValid());
    }

    private static final String FORGE_TOML = "modLoader=\"javafml\"\n"
            + "loaderVersion=\"[47,)\"\n"
            + "license=\"MIT\"\n"
            + "\n"
            + "[[mods]]\n"
            + "modId=\"coolmod\"\n"
            + "version=\"1.2.0\"\n"
            + "displayName=\"Cool Mod\"\n"
            + "authors=\"Alice, Bob\"\n"
            + "description='''Adds cool things'''\n"
            + "\n"
            + "[[dependencies.coolmod]]\n"
            + "modId=\"forge\"\n"
            + "mandatory=true\n";

    @Test
    public void parsesForgeModsToml() {
        JavaModManifest.Result result = JavaModManifest.parseForge(FORGE_TOML);
        assertTrue(result.isValid());
        JavaModManifest manifest = result.manifest;
        assertEquals("coolmod", manifest.id);
        assertEquals("Cool Mod", manifest.name);
        assertEquals("1.2.0", manifest.version);
        assertEquals(JavaModManifest.LOADER_FORGE, manifest.loader);
        assertEquals("Alice, Bob", manifest.author);
    }

    @Test
    public void readsOnlyTheFirstModsTable() {
        String toml = "[[mods]]\nmodId=\"first\"\ndisplayName=\"First\"\n"
                + "[[mods]]\nmodId=\"second\"\ndisplayName=\"Second\"\n";
        JavaModManifest manifest = JavaModManifest.parseForge(toml).manifest;
        assertEquals("first", manifest.id);
        assertEquals("First", manifest.name);
    }

    @Test
    public void detectsNeoForgeInTheLoaderLine() {
        String toml = "modLoader=\"net.neoforged.bus.api.IEventBus\"\n"
                + "[[mods]]\nmodId=\"neo\"\ndisplayName=\"Neo\"\n";
        assertEquals(JavaModManifest.LOADER_NEOFORGE,
                JavaModManifest.parseForge(toml).manifest.loader);
    }

    @Test
    public void parsesLegacyMcmodInfo() {
        String json = "[{\"modid\":\"legacy\",\"name\":\"Legacy\",\"version\":\"1.0\","
                + "\"authorList\":[\"Alice\"],\"description\":\"old\"}]";
        JavaModManifest.Result result = JavaModManifest.parseForge(json);
        assertTrue(result.isValid());
        assertEquals("legacy", result.manifest.id);
        assertEquals("Legacy", result.manifest.name);
    }

    @Test
    public void rejectsTomlWithNoModsTable() {
        assertFalse(JavaModManifest.parseForge("modLoader=\"javafml\"\n").isValid());
    }

    @Test
    public void slugHandlesPunctuationAndSpaces() {
        assertEquals("my-fancy-mod", JavaModManifest.slug("My Fancy Mod"));
        assertEquals("a-b-c", JavaModManifest.slug("a---b___c"));
        assertEquals("mod", JavaModManifest.slug("!!!"));
        assertEquals("mod", JavaModManifest.slug(null));
    }

    @Test
    public void missingAuthorIsEmptyNotNull() {
        JavaModManifest manifest = JavaModManifest.parseFabric("{\"id\":\"x\"}").manifest;
        assertNotNull(manifest);
        assertEquals("", manifest.author);
        assertTrue(manifest.dependencies.isEmpty());
    }

    @Test
    public void fabricDependencyValueMayBeAnArray() {
        String json = "{\"id\":\"x\",\"depends\":{\"minecraft\":[\"1.20\",\"1.21\"]}}";
        JavaModManifest manifest = JavaModManifest.parseFabric(json).manifest;
        assertEquals(2, manifest.minecraftVersions.size());
        assertTrue(manifest.dependencies.isEmpty());
    }

    @Test
    public void resultCarriesAnErrorWhenInvalid() {
        JavaModManifest.Result result = JavaModManifest.parseFabric("");
        assertNull(result.manifest);
        assertNotNull(result.error);
        assertFalse(result.error.isEmpty());
    }
}
