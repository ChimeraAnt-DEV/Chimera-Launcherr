package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Pins the API mapping lookups and the prompt format rules.
 *
 * <p>The mapping is the contract the LLM is asked to write against, so a missing row or a wrong
 * target would be baked into every generated mod. The prompt parser is pinned because a model that
 * ignores the format must be a failed port, not a package containing the model's prose.
 */
public class JavaApiMappingAndPromptTest {

    @Test
    public void mapsTheSymbolsTheSpecNames() {
        assertEquals("AntEggItemDef", JavaApiMapping.targetFor("net.minecraft.item.Item.Settings"));
        assertEquals("AntEggRegistry.register", JavaApiMapping.targetFor("Registry.register"));
        assertEquals("ApexEvents.onPlayerTick",
                JavaApiMapping.targetFor("PlayerEntityTickCallback"));
    }

    @Test
    public void looksUpBySimpleNameAfterAnImport() {
        assertTrue(JavaApiMapping.isMapped("Registry.register"));
        assertTrue(JavaApiMapping.isMapped("CommandRegistrationCallback"));
        assertTrue(JavaApiMapping.isMapped("EntityType"));
    }

    @Test
    public void looksUpAFullyQualifiedNameThatEndsInAMappedSymbol() {
        assertNotNull(JavaApiMapping.lookup("net.minecraft.core.Registry.register"));
        assertNotNull(JavaApiMapping.lookup("net.fabricmc.fabric.api.event.lifecycle.v1"
                + ".ServerTickEvents"));
    }

    @Test
    public void unmappedSymbolsReturnNull() {
        assertNull(JavaApiMapping.lookup("com.example.MyHelper"));
        assertNull(JavaApiMapping.lookup(null));
        assertNull(JavaApiMapping.lookup(""));
        assertFalse(JavaApiMapping.isMapped("SomethingTotallyUnknown"));
    }

    @Test
    public void targetsAreDeduplicatedAndOrdered() {
        List<String> targets = JavaApiMapping.targetsFor(Arrays.asList(
                "Registry.register", "Registries.ITEM", "PlayerEntityTickCallback",
                "ServerTickEvents"));
        // Four distinct targets: register, ITEMS, onPlayerTick, onWorldTick.
        assertEquals(4, targets.size());
        assertTrue(targets.contains("AntEggRegistry.register"));
        assertTrue(targets.contains("AntEggRegistry.ITEMS"));
        assertTrue(targets.contains("ApexEvents.onPlayerTick"));
        assertTrue(targets.contains("ApexEvents.onWorldTick"));
    }

    @Test
    public void duplicateSymbolsCollapseToTheirTargets() {
        List<String> targets = JavaApiMapping.targetsFor(Arrays.asList(
                "net.minecraft.item.Item", "net.minecraft.item.Item.Settings"));
        // Both map to the same item-definition target.
        assertEquals(1, targets.size());
        assertEquals("AntEggItemDef", targets.get(0));
    }

    @Test
    public void everyMappingRowHasATargetAndATier() {
        for (JavaApiMapping.Entry entry : JavaApiMapping.entries()) {
            assertNotNull(entry.source);
            assertNotNull(entry.target);
            assertFalse(entry.target.isEmpty());
            assertTrue(entry.tier == PortabilityReport.TIER_1
                    || entry.tier == PortabilityReport.TIER_2);
        }
    }

    @Test
    public void everyTierOneAreaFromTheSpecIsCovered() {
        // Items, blocks, tick events and commands must each have at least one Tier-1 mapping.
        assertTrue(hasTier1("AntEggItemDef"));
        assertTrue(hasTier1("AntEggBlockDef"));
        assertTrue(hasTier1("ApexEvents.onPlayerTick"));
        assertTrue(hasTier1("ApexCommands.register"));
    }

    private static boolean hasTier1(String target) {
        for (JavaApiMapping.Entry entry : JavaApiMapping.entries()) {
            if (entry.target.equals(target) && entry.tier == PortabilityReport.TIER_1) return true;
        }
        return false;
    }

    // --- prompt -------------------------------------------------------------------------------

    @Test
    public void theSystemInstructionForbidsCheatsAndVerbatimCopying() {
        String system = PortPrompt.systemInstruction();
        assertTrue(system.contains("kill aura"));
        assertTrue(system.toLowerCase().contains("never copy decompiled"));
        assertTrue(system.contains("===CODE==="));
    }

    @Test
    public void parsesALuaAnswer() {
        PortPrompt.Answer answer = PortPrompt.parse(
                "LANG: lua\nSUMMARY: adds a command\n===CODE===\nprint('hi')\n");
        assertNotNull(answer);
        assertTrue(answer.isLua());
        assertEquals("print('hi')", answer.source);
        assertEquals("adds a command", answer.summary);
    }

    @Test
    public void parsesACppAnswer() {
        PortPrompt.Answer answer = PortPrompt.parse(
                "LANG: cpp\nSUMMARY: a tick effect\n===CODE===\n#include <x>\nvoid f(){}\n");
        assertNotNull(answer);
        assertTrue(answer.isCpp());
    }

    @Test
    public void rejectsAnAnswerWithNoCodeFence() {
        assertNull(PortPrompt.parse("LANG: lua\nSUMMARY: sorry"));
        assertNull(PortPrompt.parse(""));
        assertNull(PortPrompt.parse(null));
    }

    @Test
    public void acceptsASingleFencedBlockWithoutTheMarker() {
        PortPrompt.Answer answer = PortPrompt.parse("```lua\nprint(1)\n```");
        assertNotNull(answer);
        assertTrue(answer.isLua());
        assertEquals("print(1)", answer.source);
    }

    @Test
    public void guessesTheLanguageWhenTheHeaderOmitsIt() {
        PortPrompt.Answer cpp = PortPrompt.parse("===CODE===\n#include <a>\nint main(){}\n");
        assertNotNull(cpp);
        assertTrue(cpp.isCpp());
        PortPrompt.Answer lua = PortPrompt.parse("===CODE===\nprint(1)\n");
        assertNotNull(lua);
        assertTrue(lua.isLua());
    }

    @Test
    public void buildsEntryPointNamesPerLanguage() {
        assertEquals("coolmod.lua", PortPrompt.entryPointName(PortPrompt.LANG_LUA, "coolmod"));
        assertEquals("coolmod.so", PortPrompt.entryPointName(PortPrompt.LANG_CPP, "coolmod"));
    }

    @Test
    public void theUserPromptIncludesTheManifestAndTruncatesLongSource() {
        JavaModManifest manifest = JavaModManifest.parseFabric(
                "{\"id\":\"coolmod\",\"name\":\"Cool Mod\",\"description\":\"Adds things\"}")
                .manifest;
        String longSource = new String(new char[500]).replace('\0', 'x');
        String prompt = PortPrompt.userPrompt(longSource, manifest,
                Arrays.asList("AntEggItemDef"), 100);
        assertTrue(prompt.contains("coolmod"));
        assertTrue(prompt.contains("AntEggItemDef"));
        assertTrue(prompt.contains("truncated"));
        assertFalse(prompt.contains(longSource));
    }

    @Test
    public void theUserPromptKeepsShortSourceWhole() {
        String prompt = PortPrompt.userPrompt("class A {}", null, null, 1000);
        assertTrue(prompt.contains("class A {}"));
        assertFalse(prompt.contains("truncated"));
    }
}
