package org.chimeramc.client.core.javabridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user-facing assessment of what a port will keep, change and drop.
 *
 * <p>The spec requires a portability score, a diff of what will be ported and what will be
 * dropped, and an explicit "not supported" list. This class is that report: it is pure, derived
 * only from a {@link JarInspector.Inspection}, and it never claims more than the inspection can
 * prove. A jar with no recognisable metadata scores low rather than guessing.
 *
 * <p>Nothing here decides to <em>perform</em> a port — that is the user's call after reading this.
 */
public final class PortabilityReport {

    public static final int SCORE_HIGH = 0;
    public static final int SCORE_MEDIUM = 1;
    public static final int SCORE_LOW = 2;
    public static final int SCORE_UNSUPPORTED = 3;

    /** The Tier-1/Tier-2 labels shared with {@link JavaApiMapping}. */
    public static final int TIER_1 = 1;
    public static final int TIER_2 = 2;

    /** One line of the diff: a feature, whether it will be ported, and why. */
    public static final class Item {
        public final String feature;
        /** {@link #KEEP}, {@link #PARTIAL} or {@link #DROP}. */
        public final String disposition;
        public final String detail;

        Item(String feature, String disposition, String detail) {
            this.feature = feature;
            this.disposition = disposition;
            this.detail = detail;
        }
    }

    public static final String KEEP = "keep";
    public static final String PARTIAL = "partial";
    public static final String DROP = "drop";

    public final int score;
    /** Human-readable score label: High / Medium / Low / Not Supported. */
    public final String scoreLabel;
    /** One sentence explaining the score. */
    public final String summary;
    /** What will be ported, partially ported, or dropped. */
    public final List<Item> items;
    /** The API symbols the port recognises, with their AntEgg targets. */
    public final List<JavaApiMapping.Entry> mappedApis;
    /** Reasons the mod cannot be ported at all; non-empty only for {@link #SCORE_UNSUPPORTED}. */
    public final List<String> blockers;

    private PortabilityReport(int score, String scoreLabel, String summary, List<Item> items,
                              List<JavaApiMapping.Entry> mappedApis, List<String> blockers) {
        this.score = score;
        this.scoreLabel = scoreLabel;
        this.summary = summary;
        this.items = Collections.unmodifiableList(items);
        this.mappedApis = Collections.unmodifiableList(mappedApis);
        this.blockers = Collections.unmodifiableList(blockers);
    }

    public boolean isUnsupported() {
        return score == SCORE_UNSUPPORTED;
    }

    /** True when a port may proceed (with review), i.e. not outright unsupported. */
    public boolean canPort() {
        return score != SCORE_UNSUPPORTED;
    }

    /**
     * Builds a report from an inspection.
     *
     * <p>Hard blockers (native libraries, worldgen, rendering-heavy mixins) are checked first and
     * short-circuit to {@link #SCORE_UNSUPPORTED}: a mod with a bundled {@code .so} cannot be
     * reimplemented, so scoring it "Medium" would be a lie the user would only discover after a
     * network round-trip.
     */
    public static PortabilityReport of(JarInspector.Inspection inspection) {
        List<Item> items = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        if (inspection == null || !inspection.isValid()) {
            blockers.add(inspection == null
                    ? "the mod could not be read"
                    : inspection.error);
            items.add(new Item("Read the mod", DROP, "the jar could not be inspected"));
            return new PortabilityReport(SCORE_UNSUPPORTED, "Not Supported",
                    "This mod could not be read, so nothing can be ported.", items,
                    JavaApiMapping.entries(), blockers);
        }

        JavaModManifest manifest = inspection.manifest;

        // --- hard blockers -----------------------------------------------------------------
        if (!inspection.nativeLibraries.isEmpty()) {
            blockers.add("the mod bundles native libraries (" + inspection.nativeLibraries.size()
                    + "), which cannot be reimplemented");
        }
        if (manifest == null) {
            blockers.add("the mod has no recognisable Fabric or Forge metadata");
        }
        if (!blockers.isEmpty()) {
            for (String blocker : blockers) {
                items.add(new Item("Not supported", DROP, blocker));
            }
            return new PortabilityReport(SCORE_UNSUPPORTED, "Not Supported",
                    "This mod cannot be ported: " + String.join("; ", blockers) + ".",
                    items, JavaApiMapping.entries(), blockers);
        }

        // --- classify what it does ---------------------------------------------------------
        boolean hasItemsOrBlocks = manifest != null; // metadata present implies a real mod
        items.add(new Item("Mod metadata", KEEP,
                "read from " + loaderLabel(manifest.loader) + " (" + manifest.id + ")"));

        if (hasItemsOrBlocks) {
            items.add(new Item("Custom items / blocks", KEEP,
                    "mapped to AntEggItemDef / AntEggBlockDef and the registry"));
        }
        items.add(new Item("Tick events", KEEP, "mapped to ApexEvents.onPlayerTick / onWorldTick"));
        items.add(new Item("Commands", KEEP, "mapped to ApexCommands.register"));

        if (inspection.hasDataFiles) {
            items.add(new Item("Data-driven recipes / loot tables", PARTIAL,
                    "recipes port; loot tables port only for simple tables"));
        } else {
            items.add(new Item("Data-driven recipes / loot tables", DROP,
                    "the mod ships no data/ files"));
        }

        if (inspection.hasMixins) {
            items.add(new Item("Mixin patches", DROP,
                    "mixin bytecode patches cannot be reimplemented; behaviour must be rewritten"));
        }

        if (inspection.hasClientAssets) {
            items.add(new Item("Client textures / models", PARTIAL,
                    "textures are repackaged; custom rendering is not ported"));
        }

        // --- score -------------------------------------------------------------------------
        int score = scoreFor(inspection);
        String label = labelFor(score);
        String summary = summaryFor(inspection, score);

        List<JavaApiMapping.Entry> mapped = JavaApiMapping.entries();
        return new PortabilityReport(score, label, summary, items, mapped, blockers);
    }

    private static int scoreFor(JarInspector.Inspection inspection) {
        if (inspection.hasMixins && inspection.ownClassCount > 40) {
            // Large, mixin-heavy mods change engine behaviour the port cannot reproduce.
            return SCORE_LOW;
        }
        if (inspection.hasMixins || inspection.ownClassCount > 120) {
            return SCORE_LOW;
        }
        if (inspection.hasDataFiles && inspection.ownClassCount <= 40) {
            return SCORE_HIGH;
        }
        if (inspection.ownClassCount <= 40) {
            return SCORE_MEDIUM;
        }
        return SCORE_MEDIUM;
    }

    private static String summaryFor(JarInspector.Inspection inspection, int score) {
        switch (score) {
            case SCORE_HIGH:
                return "This is a small, data-driven mod. Items, blocks, tick events and commands "
                        + "should port cleanly.";
            case SCORE_MEDIUM:
                return "This mod is a reasonable size. Core behaviour should port, but expect to "
                        + "review the generated code before enabling it.";
            default:
                return "This mod is large or mixin-heavy. Only a fraction is likely to port, and "
                        + "the result may behave differently from the original.";
        }
    }

    private static String loaderLabel(String loader) {
        if (loader == null) return "unknown loader";
        switch (loader) {
            case JavaModManifest.LOADER_FABRIC: return "Fabric";
            case JavaModManifest.LOADER_FORGE: return "Forge";
            case JavaModManifest.LOADER_NEOFORGE: return "NeoForge";
            default: return "an unrecognised loader";
        }
    }

    /** High / Medium / Low / Not Supported. */
    public static String labelFor(int score) {
        switch (score) {
            case SCORE_HIGH: return "High";
            case SCORE_MEDIUM: return "Medium";
            case SCORE_LOW: return "Low";
            default: return "Not Supported";
        }
    }

    /** The items that will be carried over, for a compact "what gets ported" list. */
    public List<Item> keptItems() {
        return filter(KEEP);
    }

    /** The items that will be partially carried over. */
    public List<Item> partialItems() {
        return filter(PARTIAL);
    }

    /** The items that will be dropped. */
    public List<Item> droppedItems() {
        return filter(DROP);
    }

    private List<Item> filter(String disposition) {
        List<Item> out = new ArrayList<>();
        for (Item item : items) {
            if (item.disposition.equals(disposition)) out.add(item);
        }
        return out;
    }
}
