package org.chimeramc.client.core.javabridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A curated mapping from common Fabric/Forge API symbols to their Bedrock/AntEgg equivalents.
 *
 * <p>This table is what makes the port a <em>reimplementation</em> rather than a translation: the
 * LLM is told which target API to write against, and the report can say which of a mod's API uses
 * have a known equivalent and which do not. It is a plain data table with a pure lookup, so the
 * mapping and its coverage rules are unit-testable without a device or a network call.
 *
 * <p>The target names are the .AntEgg runtime surface ({@code mod.*} for Lua script mods, and the
 * {@code pl} preloader bridge — referred to in the spec as "ApexAntLamina" — for native mods).
 */
public final class JavaApiMapping {

    /** One mapping row: a source symbol, its target, the tier it enables and a short note. */
    public static final class Entry {
        public final String source;
        public final String target;
        /** {@link PortabilityReport#TIER_1} or {@link PortabilityReport#TIER_2}. */
        public final int tier;
        public final String note;

        Entry(String source, String target, int tier, String note) {
            this.source = source;
            this.target = target;
            this.tier = tier;
            this.note = note;
        }
    }

    /** The curated table. Order is stable so a report's "mapped APIs" list reads consistently. */
    private static final List<Entry> ENTRIES = buildEntries();

    private JavaApiMapping() {}

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        // --- items / blocks (Tier 1) -------------------------------------------------------
        add(entries, "net.minecraft.item.Item.Settings", "AntEggItemDef", 1,
                "item definition");
        add(entries, "net.minecraft.item.Item", "AntEggItemDef", 1, "item definition");
        add(entries, "Registry.register", "AntEggRegistry.register", 1, "registry insert");
        add(entries, "Registries.ITEM", "AntEggRegistry.ITEMS", 1, "item registry");
        add(entries, "Registries.BLOCK", "AntEggRegistry.BLOCKS", 1, "block registry");
        add(entries, "net.minecraft.block.Block", "AntEggBlockDef", 1, "block definition");
        add(entries, "Block.Settings", "AntEggBlockDef", 1, "block definition");
        add(entries, "ItemGroup", "AntEggCreativeTab", 1, "creative tab");
        add(entries, "RecipeManager", "AntEggRecipes", 1, "crafting recipe");
        add(entries, "CraftingRecipe", "AntEggRecipes", 1, "crafting recipe");
        add(entries, "ShapedRecipe", "AntEggRecipes.shaped", 1, "shaped recipe");
        add(entries, "ShapelessRecipe", "AntEggRecipes.shapeless", 1, "shapeless recipe");
        // --- tick events (Tier 1) ----------------------------------------------------------
        add(entries, "PlayerEntityTickCallback", "ApexEvents.onPlayerTick", 1, "player tick");
        add(entries, "ServerTickEvents", "ApexEvents.onWorldTick", 1, "world tick");
        add(entries, "ServerTickEvents.END_SERVER_TICK", "ApexEvents.onWorldTick", 1, "world tick");
        add(entries, "ClientTickEvents", "ApexEvents.onClientTick", 1, "client tick");
        add(entries, "ServerLifecycleEvents", "ApexEvents.onServerLifecycle", 1, "server lifecycle");
        add(entries, "PlayerBlockBreakEvents", "ApexEvents.onBlockBreak", 1, "block break");
        add(entries, "PlayerBlockPlaceEvents", "ApexEvents.onBlockPlace", 1, "block place");
        add(entries, "UseBlockCallback", "ApexEvents.onBlockUse", 1, "block use");
        add(entries, "UseItemCallback", "ApexEvents.onItemUse", 1, "item use");
        add(entries, "AttackEntityCallback", "ApexEvents.onEntityAttack", 1, "entity attack");
        // --- commands (Tier 1) -------------------------------------------------------------
        add(entries, "CommandRegistrationCallback", "ApexEvents.onRegisterCommands", 1, "commands");
        add(entries, "CommandManager", "ApexCommands.register", 1, "command registration");
        add(entries, "LiteralArgumentBuilder", "ApexCommands.literal", 1, "command literal");
        add(entries, "CommandManager.argument", "ApexCommands.argument", 1, "command argument");
        // --- data driven (Tier 2) ----------------------------------------------------------
        add(entries, "LootTable", "AntEggLoot", 2, "loot table");
        add(entries, "LootTableLoadingCallback", "ApexEvents.onLootTable", 2, "loot table");
        add(entries, "ItemStack", "AntEggItemStack", 2, "item stack");
        add(entries, "Identifier", "AntEggId", 2, "resource id");
        // --- mobs / entities (Tier 2) ------------------------------------------------------
        add(entries, "EntityType", "AntEggEntityDef", 2, "entity type");
        add(entries, "EntityType.Builder", "AntEggEntityDef", 2, "entity type");
        add(entries, "Registries.ENTITY_TYPE", "AntEggRegistry.ENTITIES", 2, "entity registry");
        add(entries, "MobEntity", "AntEggMobBehaviour", 2, "mob behaviour");
        add(entries, "PathAwareEntity", "AntEggMobBehaviour", 2, "mob behaviour");
        add(entries, "Goal", "AntEggMobGoal", 2, "mob goal");
        add(entries, "SpawnRestriction", "AntEggMobSpawn", 2, "spawn restriction");
        // --- gui (Tier 2) ------------------------------------------------------------------
        add(entries, "HandledScreen", "AntEggGuiScreen", 2, "gui screen");
        add(entries, "ScreenHandler", "AntEggGuiContainer", 2, "gui container");
        add(entries, "ScreenHandlerType", "AntEggGuiContainer", 2, "gui container");
        add(entries, "ScreenRegistry", "AntEggGui.register", 2, "gui registration");
        return Collections.unmodifiableList(entries);
    }

    private static void add(List<Entry> entries, String source, String target, int tier,
                            String note) {
        entries.add(new Entry(source, target, tier, note));
    }

    /** The full table, for display in the portability report. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * Finds the mapping for a symbol seen in decompiled source, or null when there is none.
     *
     * <p>Matching is by fully-qualified prefix or by bare simple name: decompiled source contains
     * both {@code Registry.register} (after an import) and
     * {@code net.minecraft.core.Registry.register}. A symbol with no mapping is not a failure by
     * itself — it just means the LLM has to reason about it — but an unmapped <em>API class</em> is
     * what drives the score down.
     */
    public static Entry lookup(String symbol) {
        if (symbol == null || symbol.isEmpty()) return null;
        String needle = symbol.trim();
        // Exact match first, then a fully-qualified form ending in the simple name.
        for (Entry entry : ENTRIES) {
            if (entry.source.equals(needle)) return entry;
        }
        for (Entry entry : ENTRIES) {
            if (entry.source.endsWith("." + needle) || needle.endsWith("." + entry.source)) {
                return entry;
            }
        }
        return null;
    }

    /** The target for a symbol, or null when unmapped. */
    public static String targetFor(String symbol) {
        Entry entry = lookup(symbol);
        return entry == null ? null : entry.target;
    }

    /** True when the symbol has a known Bedrock equivalent. */
    public static boolean isMapped(String symbol) {
        return lookup(symbol) != null;
    }

    /**
     * The distinct targets a set of source symbols maps onto.
     *
     * <p>Used to tell the LLM which target APIs to write against, and to show the user a short
     * "what will be ported" list rather than every raw symbol.
     */
    public static List<String> targetsFor(List<String> symbols) {
        List<String> targets = new ArrayList<>();
        if (symbols == null) return targets;
        for (String symbol : symbols) {
            Entry entry = lookup(symbol);
            if (entry != null && !targets.contains(entry.target)) {
                targets.add(entry.target);
            }
        }
        return targets;
    }

    /** Case-insensitive containment test used when scanning source text for a known symbol. */
    static boolean sourceContains(String text, String symbol) {
        return text != null && symbol != null
                && text.toLowerCase(Locale.ROOT).contains(symbol.toLowerCase(Locale.ROOT));
    }
}
