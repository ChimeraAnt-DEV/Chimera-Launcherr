package org.chimeramc.client.core.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The catalogue of GlowberryClient cosmetics: capes, accessories and pets.
 *
 * <p>This class is pure data plus pure selection logic, so "which cape is equipped", "how many
 * pet styles exist" and "does this species fly" are unit-testable without an Android context or a
 * running overlay. The drawing lives in the preview view; the catalogue only says what exists.
 *
 * <p>The three families are generated from a small set of hand-authored palettes crossed with a
 * set of shapes/kinds, rather than listed by hand. That is what makes each family a hundred-plus
 * entries without a hundred near-duplicate literals: a new palette instantly adds one variant to
 * every shape, and every variant keeps a real, readable name.
 *
 * <p>Scope honesty: these are launcher-side cosmetics. Bedrock does not expose a general custom
 * cape slot, nor any way to spawn a client-side pet entity, to a third-party launcher, so the
 * previews show how the pieces <em>look</em>; only the cape can reach the game (through the
 * texture-override resource pack built elsewhere). The UI carries that note rather than implying
 * pets appear in the world.
 */
public final class CosmeticCatalog {

    /** How a cape's cloth is painted, independent of its palette. */
    public enum CapePattern {
        SOLID,
        VERTICAL_STRIPES,
        HORIZONTAL_STRIPES,
        GRADIENT,
        SPLIT,
        GRID,
        CHECKER,
        CHEVRON,
        HORIZON,
        WAVE,
        CAMO,
        STAR
    }

    /** The drawing family of an accessory; the preview switches on this, not on the id string. */
    public enum AccessoryKind {
        NONE,
        HEADPHONES,
        HALO,
        WINGS,
        CAP,
        BEANIE,
        CROWN,
        GLASSES,
        MASK,
        SCARF,
        BACKPACK,
        HORNS,
        FLOWER,
        BOWTIE,
        EAR,
        TOPHAT,
        WIZARD_HAT,
        TIARA,
        BEARD,
        // Added shapes: genuinely new silhouettes, not recolours of the above.
        VEIL,
        MONOCLE,
        ANTLERS,
        PLUME,
        TRICORN,
        MORTARBOARD
    }

    /** What a pet can do; a species advertises which of these it animates for. */
    public enum PetLocomotion {
        IDLE,
        WALK,
        RUN,
        CROUCH,
        FLY,
        SWIM
    }

    /** A cape the player can equip. */
    public static final class Cape {
        public final String id;
        public final String name;
        /** Base fill colour of the cape cloth. */
        public final int color;
        /** Trim colour around the cape and its collar. */
        public final int trimColor;
        /** Secondary colour used by patterned weaves. */
        public final int accentColor;
        public final CapePattern pattern;
        /** Whether the brand mark crawls across the cape. */
        public final boolean animated;
        /** Whether the cape carries the GlowberryClient brand mark at all. */
        public final boolean branded;

        public Cape(String id, String name, int color, int trimColor, int accentColor,
                    CapePattern pattern, boolean animated, boolean branded) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.trimColor = trimColor;
            this.accentColor = accentColor;
            this.pattern = pattern == null ? CapePattern.SOLID : pattern;
            this.animated = animated;
            this.branded = branded;
        }
    }

    /** A worn accessory. */
    public static final class Accessory {
        public final String id;
        public final String name;
        public final AccessoryKind kind;
        public final int color;
        /** Secondary colour for two-tone pieces (cups, wings, straps). */
        public final int accentColor;

        public Accessory(String id, String name, AccessoryKind kind, int color, int accentColor) {
            this.id = id;
            this.name = name;
            this.kind = kind == null ? AccessoryKind.NONE : kind;
            this.color = color;
            this.accentColor = accentColor;
        }
    }

    /** A companion that trots, flies, crawls or swims beside the character. */
    public static final class Pet {
        public final String id;
        public final String name;
        public final PetSpecies species;
        public final int color;
        public final int accentColor;
        /** Body scale relative to the default pet, so a "giant" variant reads as bigger. */
        public final float scale;

        public Pet(String id, String name, PetSpecies species, int color, int accentColor,
                   float scale) {
            this.id = id;
            this.name = name;
            this.species = species == null ? PetSpecies.CAT : species;
            this.color = color;
            this.accentColor = accentColor;
            this.scale = scale;
        }

        /** Whether this pet animates for the given locomotion. */
        public boolean supports(PetLocomotion locomotion) {
            return species.supports(locomotion);
        }
    }

    /**
     * A pet species: a blocky Minecraft-style body with a name and the locomotion set it animates.
     *
     * <p>Only the species knows how to build itself and which gaits it has, so "a crawling pet has
     * its own elytra for flying" and "a swimmer gets a swim set" are properties of the species
     * rather than special cases scattered through the renderer.
     */
    public enum PetSpecies {
        CAT("Cat", 0xFF6B6B6B, 0xFFEDE7DC, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH, PetLocomotion.SWIM),
        DOG("Dog", 0xFFB58B5A, 0xFFE8D9C0, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH, PetLocomotion.SWIM),
        FOX("Fox", 0xFFD2703A, 0xFFF3E2D0, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        WOLF("Wolf", 0xFF8C9199, 0xFFE7EAEE, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        RABBIT("Rabbit", 0xFFD8CFC2, 0xFFFFF6EE, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        PARROT("Parrot", 0xFFD64545, 0xFF3FB6D8, PetLocomotion.WALK, PetLocomotion.FLY),
        BEE("Bee", 0xFFE8B93A, 0xFF2E2A22, PetLocomotion.WALK, PetLocomotion.FLY),
        BUTTERFLY("Butterfly", 0xFF7B5BD6, 0xFFFFC94D, PetLocomotion.WALK, PetLocomotion.FLY),
        DRAGONFLY("Dragonfly", 0xFF3FB6A8, 0xFF9CE8E0, PetLocomotion.WALK, PetLocomotion.FLY),
        BEETLE("Beetle", 0xFF4A3B6B, 0xFF8E7BC4, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH,
                PetLocomotion.FLY),
        SPIDER("Spider", 0xFF3A2E2A, 0xFFB0452F, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        ANT("Ant", 0xFF5A3520, 0xFF8A5A34, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        FROG("Frog", 0xFF4E9B3E, 0xFFB7E36B, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.SWIM),
        AXOLOTL("Axolotl", 0xFFE68AA8, 0xFFFFD1E0, PetLocomotion.WALK, PetLocomotion.SWIM),
        TURTLE("Turtle", 0xFF4E7A3E, 0xFF8FBF6A, PetLocomotion.WALK, PetLocomotion.CROUCH, PetLocomotion.SWIM),
        SNAKE("Snake", 0xFF4C7A2E, 0xFFC7D96B, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH,
                PetLocomotion.SWIM),
        LIZARD("Lizard", 0xFF7A8A4E, 0xFFD6C46A, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        DRAGON("Dragon", 0xFF5A2E8C, 0xFFD6A84A, PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH,
                PetLocomotion.FLY, PetLocomotion.SWIM);

        public final String displayName;
        /** The species' natural body colour, used as the default pet tint. */
        public final int baseColor;
        /** The species' natural accent (belly, wings, spots), used as the default accent. */
        public final int accentColor;
        private final PetLocomotion[] gaits;

        PetSpecies(String displayName, int baseColor, int accentColor, PetLocomotion... gaits) {
            this.displayName = displayName;
            this.baseColor = baseColor;
            this.accentColor = accentColor;
            this.gaits = gaits;
        }

        public boolean supports(PetLocomotion locomotion) {
            // Idle is the universal resting state: every pet can stand still, so it is supported
            // even though no species lists it in its gait set.
            if (locomotion == PetLocomotion.IDLE) return true;
            for (PetLocomotion g : gaits) {
                if (g == locomotion) return true;
            }
            return false;
        }

        /** True when this species crawls, i.e. it gets a custom elytra when it flies. */
        public boolean isCrawler() {
            return this == SPIDER || this == ANT || this == BEETLE;
        }

        /**
         * True when this species clings to the player in the air rather than flying alongside.
         *
         * <p>A crawling bug that has no flight of its own (spider, ant) hitches a ride and leans
         * back into the wind. A bug that is genuinely a flyer (beetle) flies on its own instead, so
         * it must not be given the blown-back cling. This is the one property that separates the
         * two, and both the in-game controller and the preview read it so they cannot disagree.
         */
        public boolean clingsInAir() {
            return isCrawler() && !supports(PetLocomotion.FLY);
        }

        /** True when this species swims, i.e. it gets a dedicated swim set. */
        public boolean isSwimmer() {
            return supports(PetLocomotion.SWIM);
        }
    }

    public static final String NONE = "none";

    private static final List<Cape> CAPES;
    private static final List<Accessory> ACCESSORIES;
    private static final List<Pet> PETS;

    /** One palette: a base, a trim and a pattern accent. */
    private static final int[][] PALETTES = {
            {0xFF6236E8, 0xFFA88CFF, 0xFFFFD86B}, // Chimera violet
            {0xFF141418, 0xFF3A3A44, 0xFF8F979F}, // Void
            {0xFFA82E9E, 0xFFE070C0, 0xFFFFE1F4}, // Magenta flux
            {0xFF1F7A4D, 0xFF63D69B, 0xFFE9FFF3}, // Verdant
            {0xFFB5442E, 0xFFFF8A5B, 0xFFFFE0C2}, // Ember
            {0xFF1E5FA8, 0xFF5FB4FF, 0xFFD6ECFF}, // Deep sea
            {0xFFC9A227, 0xFFFFD86B, 0xFF7A5C12}, // Gilded
            {0xFF2B2F36, 0xFF6C757D, 0xFFB9C2CC}, // Graphite
            {0xFF7A1F3D, 0xFFD65C82, 0xFFFFD9E4}, // Rosewood
            {0xFF0F8B8D, 0xFF57D9DB, 0xFFE2FFFF}, // Lagoon
            {0xFF5A2E8C, 0xFFB07CE8, 0xFFF0DFFF}, // Amethyst
            {0xFF3B6B23, 0xFF8BC34A, 0xFFEFFFDC}  // Creeper
    };

    private static final String[] PALETTE_NAMES = {
            "Chimera", "Void", "Flux", "Verdant", "Ember", "Abyss",
            "Gilded", "Graphite", "Rosewood", "Lagoon", "Amethyst", "Creeper"
    };

    /**
     * Per-palette cape names, one per {@link CapePattern} (index-aligned with
     * {@link CapePattern#ordinal()}). A full table rather than one shared set of pattern words:
     * the earlier version composed every cape as "&lt;palette&gt; Sovereign/Tapestry/..." so a
     * column of capes was one word swapped, which is exactly the templated look this replaces.
     * Every name here is distinct, and the second word varies by palette as well as by pattern, so
     * "Emberdusk" and "Rosewood" do not share a pattern's nickname.
     */
    private static final String[][] CAPE_PATTERN_NAMES = {
            // SOLID              VERTICAL              HORIZONTAL           GRADIENT            SPLIT                GRID                   CHECKER               CHEVRON              HORIZON              WAVE                  CAMO                STAR
            {"Chimera Cape", "Violet Tapestry", "Amethyst Ribbon", "Twilight Aurora", "Starburst Regalia", "Arcane Lattice", "Chequered Sigil", "Violet Chevron", "Eventide Horizon", "Violet Riptide", "Wilds of Chimera", "Celestial Chimera"},
            {"Umbral Sovereign", "Nightfall Tapestry", "Onyx Ribbon", "Voidlight Aurora", "Eclipse Starburst", "Obsidian Lattice", "Shadow Chequer", "Nightfall Chevron", "Dusk Horizon", "Umbral Riptide", "Hollow Wilds", "Starless Celestial"},
            {"Fluxweave Cape", "Magenta Tapestry", "Neon Ribbon", "Pulse Aurora", "Static Starburst", "Circuit Lattice", "Overload Chequer", "Neon Chevron", "Signal Horizon", "Magenta Riptide", "Charged Wilds", "Neon Celestial"},
            {"Wildwood Cape", "Thicket Tapestry", "Vine Ribbon", "Dawnleaf Aurora", "Canopy Starburst", "Bramble Lattice", "Mossy Chequer", "Thicket Chevron", "Greenwood Horizon", "Riverbed Riptide", "Deep Wilds", "Starlit Canopy"},
            {"Emberdusk Cape", "Cinder Tapestry", "Scarlet Ribbon", "Sunset Aurora", "Bonfire Starburst", "Coalbed Lattice", "Cinder Chequer", "Scarlet Chevron", "Smoke Horizon", "Lavafall Riptide", "Scorched Wilds", "Ember Celestial"},
            {"Abyssal Cape", "Trench Tapestry", "Current Ribbon", "Depth Aurora", "Kraken Starburst", "Coral Lattice", "Undertow Chequer", "Current Chevron", "Deepwater Horizon", "Undertow Riptide", "Drowned Wilds", "Abyssal Celestial"},
            {"Gilded Cape", "Goldleaf Tapestry", "Bullion Ribbon", "Sunburst Aurora", "Midas Starburst", "Filigree Lattice", "Gilded Chequer", "Goldleaf Chevron", "Golden Horizon", "Bullion Riptide", "Auric Wilds", "Gilded Celestial"},
            {"Obsidian Cape", "Slate Tapestry", "Steel Ribbon", "Ashfall Aurora", "Iron Starburst", "Graphite Lattice", "Slate Chequer", "Steel Chevron", "Ashen Horizon", "Coldiron Riptide", "Bleak Wilds", "Obsidian Celestial"},
            {"Rosewood Cape", "Petal Tapestry", "Blossom Ribbon", "Rosegold Aurora", "Camellia Starburst", "Petalwork Lattice", "Rose Chequer", "Blossom Chevron", "Roseate Horizon", "Petal Riptide", "Blooming Wilds", "Rosewood Celestial"},
            {"Tidal Cape", "Reef Tapestry", "Foam Ribbon", "Lagoon Aurora", "Seafoam Starburst", "Coralweave Lattice", "Tidal Chequer", "Reef Chevron", "Seabound Horizon", "Lagoon Riptide", "Shoreline Wilds", "Tidal Celestial"},
            {"Amethyst Cape", "Crystal Tapestry", "Geode Ribbon", "Dusk Aurora", "Prism Starburst", "Crystalline Lattice", "Amethyst Chequer", "Geode Chevron", "Violet Horizon", "Prism Riptide", "Cavern Wilds", "Amethyst Celestial"},
            {"Creeping Cape", "Vine Tapestry", "Spore Ribbon", "Fuse Aurora", "Creeper Starburst", "Mossy Lattice", "Spore Chequer", "Vine Chevron", "Blast Horizon", "Sporefall Riptide", "Overgrown Wilds", "Creeping Celestial"}
    };

    /**
     * Per-palette accessory names, one per {@link #ACCESSORY_KINDS} entry. Hand-written rather than
     * composed so an accessory reads like a themed item rather than a swatch name. Every name is
     * distinct across the whole table: the earlier version composed "&lt;palette&gt; Cap/Crown/..."
     * so the list was one word swapped, which is the templated look this replaces. Index-aligned
     * with {@link #PALETTES} and {@link #ACCESSORY_KINDS}.
     */
    private static final String[][] ACCESSORY_NAMES = {
            //        CAP                 BEANIE              CROWN                GLASSES             MASK               SCARF               BACKPACK             HORNS              FLOWER              BOWTIE             EAR               TOPHAT              WIZARD_HAT          TIARA              BEARD              VEIL               MONOCLE            ANTLERS            PLUME              TRICORN            MORTARBOARD
            {"Chimera Crest", "Chimera Knit", "Chimera Circlet", "Chimera Visor", "Chimera Veil", "Chimera Stole", "Chimera Satchel", "Chimera Horns", "Chimera Bloom", "Chimera Knot", "Chimera Ears", "Chimera Stovepipe", "Chimera Arcanist", "Chimera Diadem", "Chimera Mane", "Chimera Shroud", "Chimera Lens", "Chimera Antlers", "Chimera Plume", "Chimera Corsair", "Chimera Scholar"},
            {"Gravekeeper Cap", "Ashen Watchcap", "Void Diadem", "Umbral Shades", "Shroud of Night", "Grave Wrap", "The Nightpack", "Voidgrasp Spikes", "Nightshade Bloom", "Gloom Cravat", "Vesper Bats", "Hollow Chimney", "Occultist's Hood", "Hollow Tiara", "Bramblebeard", "Sable Shroud", "Shadow Monocle", "Briar Antlers", "Raven Quill", "Dread Freebooter", "Dean of Shadows"},
            {"Static Beret", "Neon Beanie", "Pulse Circlet", "Overclock Goggles", "Glitch Mask", "Static Scarf", "Signal Rucksack", "Stormhorn Spikes", "Neon Blossom", "Flux Bow", "Fizz Ears", "Overload Top Hat", "Hexweaver Hat", "Flux Tiara", "Voltage Beard", "Screen Veil", "Circuit Lens", "Voltaic Antlers", "Ion Feather", "Pulse Captain", "Graduate of Flux"},
            {"Mossbank Cap", "Fern Beanie", "Thornwood Crown", "Leafcut Specs", "Verdant Mask", "Rootwork Scarf", "Forager's Pack", "Bramble Horns", "Daisy Crown", "Green Knot", "Faun Ears", "Great Tall Hat", "Druid's Crown", "Garland Tiara", "Mossbeard", "Petal Veil", "Oak Monocle", "Greenman Antlers", "Fern Frond", "Pathfinder's Tricorn", "Verdant Scholar"},
            {"Cinder Cap", "Coalbeanie", "Ember Circlet", "Furnace Goggles", "Ashen Mask", "Scorch Scarf", "Pyre Pack", "Flarehorn Spikes", "Fire Lily", "Cinder Bow", "Salamander Ears", "Smokestack Hat", "Pyromancer's Hood", "Ember Tiara", "Sootbeard", "Smoke Veil", "Cinder Lens", "Flare Antlers", "Fire Plume", "Scorch Corsair", "Ember Mortarboard"},
            {"Trench Cap", "Kelp Watchcap", "Drowned Crown", "Deepfathom Lenses", "Angler Mask", "Tide Scarf", "Salvage Pack", "Kraken Horns", "Deep Coral", "Tide Knot", "Siren Fins", "Deep Top Hat", "Tidecaller's Hood", "Abyssal Tiara", "Barnacle Beard", "Deep Veil", "Angler Monocle", "Kraken Antlers", "Storm Plume", "Drowned Captain", "Scholar of the Deep"},
            {"Bullion Cap", "Filigree Beanie", "Crown of Midas", "Goldsmith Monocle", "Gilded Mask", "Goldthread Scarf", "The Coffer", "Gilt Horns", "Golden Rose", "Bullion Bow", "Fae Ears", "Gilded Stovepipe", "Archmage's Hat", "Gold Diadem", "Golden Mane", "Veil of Gold", "Assayer's Lens", "Gilt Antlers", "Plume of Gold", "Gold Corsair", "Gilded Graduate"},
            {"Slate Cap", "Coldiron Watchcap", "Iron Crown", "Ashfall Shades", "Grey Mask", "Steel Scarf", "The Ironpack", "Coldiron Horns", "Ashen Bloom", "Grey Bow", "Slate Ears", "Chimney Stack", "Grey Occultist", "Iron Tiara", "Slatebeard", "Ash Veil", "Steel Monocle", "Iron Antlers", "Ash Quill", "Slate Freebooter", "Grey Dean"},
            {"Petal Beret", "Rosebud Beanie", "Rosegold Circlet", "Rosewater Specs", "Rose Mask", "Petal Scarf", "Gardener's Pack", "Rosewood Horns", "Camellia Crown", "Rose Knot", "Fawn Ears", "Rosewood Top Hat", "Enchanter's Hat", "Petal Tiara", "Rosewood Beard", "Blossom Veil", "Rosewater Monocle", "Rosewood Antlers", "Rose Plume", "Rosewood Corsair", "Rosewood Scholar"},
            {"Seafoam Cap", "Reef Beanie", "Lagoon Crown", "Coral Goggles", "Tidepool Mask", "Foam Scarf", "Beachcomber's Pack", "Coral Horns", "Lagoon Lotus", "Foam Bow", "Reef Fins", "Seafoam Stovepipe", "Tidecaller's Hat", "Lagoon Tiara", "Coral Beard", "Sea Veil", "Coral Lens", "Reef Antlers", "Foam Plume", "Lagoon Captain", "Tidal Graduate"},
            {"Geode Beret", "Crystal Beanie", "Prism Crown", "Amethyst Lenses", "Crystal Mask", "Geode Scarf", "Prospector's Pack", "Prism Horns", "Amethyst Blossom", "Prism Bow", "Crystal Ears", "Amethyst Top Hat", "Arcanist's Crown", "Amethyst Diadem", "Geode Beard", "Crystal Veil", "Amethyst Monocle", "Crystal Antlers", "Prism Plume", "Amethyst Corsair", "Amethyst Scholar"},
            {"Spore Cap", "Moss Beanie", "Vine Crown", "Fuse Goggles", "Creeper Mask", "Spore Scarf", "Overgrowth Pack", "Thornhorn Spikes", "Spore Bloom", "Creeper Bow", "Grasshopper Ears", "Moss Stovepipe", "Hexer of Vines", "Creeper Tiara", "Sporebeard", "Moss Veil", "Fuse Monocle", "Thorn Antlers", "Spore Plume", "Creeper Corsair", "Creeper Scholar"}
    };

    static {
        List<Cape> capes = new ArrayList<>();

        // The flagship cape keeps its exact id and stays first, animated and branded, so the
        // section opens on the one piece of motion it is meant to show off.
        capes.add(new Cape("chimera", "Chimera Prime", 0xFF6236E8, 0xFFA88CFF, 0xFFFFD86B,
                CapePattern.SOLID, true, true));

        // The classic ids are preserved verbatim so existing selections keep resolving. Their
        // display names are upgraded to the designed naming the rest of the catalogue uses.
        capes.add(new Cape("void_black", "Sovereign of the Void", 0xFF141418, 0xFF3A3A44, 0xFF8F979F,
                CapePattern.SOLID, false, false));
        capes.add(new Cape("magenta_flux", "Fluxweave Aurora", 0xFFA82E9E, 0xFFE070C0, 0xFFFFE1F4,
                CapePattern.GRADIENT, false, false));
        capes.add(new Cape("verdant", "Wildwood Sovereign", 0xFF1F7A4D, 0xFF63D69B, 0xFFE9FFF3,
                CapePattern.SOLID, false, false));

        // Generated families: every palette crossed with every pattern. The brand mark rides on
        // the "Chimera" palette's Split weave only, so the flagship stays special instead of every
        // cape being branded. Each palette has its own row of names (see CAPE_PATTERN_NAMES), so a
        // cape is a genuinely named piece rather than "<palette> <shared pattern word>".
        for (int p = 0; p < PALETTES.length; p++) {
            int[] pal = PALETTES[p];
            String stem = PALETTE_NAMES[p];
            for (CapePattern pattern : CapePattern.values()) {
                String id = slug(stem) + "_" + pattern.name().toLowerCase();
                if (isHandAuthored(id)) continue;
                boolean branded = "Chimera".equals(stem) && pattern == CapePattern.SPLIT;
                capes.add(new Cape(id, capePatternName(p, pattern),
                        pal[0], pal[1], pal[2], pattern, false, branded));
            }
        }
        CAPES = Collections.unmodifiableList(capes);

        List<Accessory> accessories = new ArrayList<>();
        accessories.add(new Accessory("none", "None", AccessoryKind.NONE, 0x00000000, 0x00000000));
        // Preserved classic ids so a stored selection keeps working.
        accessories.add(new Accessory("headphones", "Graphite Headphones", AccessoryKind.HEADPHONES,
                0xFF2B2F36, 0xFF6C757D));
        accessories.add(new Accessory("halo", "Gilded Halo", AccessoryKind.HALO, 0xFFFFD86B, 0xFFFFF3C4));
        accessories.add(new Accessory("wings", "Amethyst Wings", AccessoryKind.WINGS, 0xFFA88CFF, 0xFF6236E8));

        // Generated accessories: every drawing kind crossed with a palette, using the hand-written
        // per-palette names so each is a themed item rather than a swatch.
        AccessoryKind[] accessoryKinds = {
                AccessoryKind.CAP, AccessoryKind.BEANIE, AccessoryKind.CROWN,
                AccessoryKind.GLASSES, AccessoryKind.MASK, AccessoryKind.SCARF,
                AccessoryKind.BACKPACK, AccessoryKind.HORNS, AccessoryKind.FLOWER,
                AccessoryKind.BOWTIE, AccessoryKind.EAR, AccessoryKind.TOPHAT,
                AccessoryKind.WIZARD_HAT, AccessoryKind.TIARA, AccessoryKind.BEARD,
                AccessoryKind.VEIL, AccessoryKind.MONOCLE, AccessoryKind.ANTLERS,
                AccessoryKind.PLUME, AccessoryKind.TRICORN, AccessoryKind.MORTARBOARD
        };
        for (int p = 0; p < PALETTES.length; p++) {
            int[] pal = PALETTES[p];
            String stem = PALETTE_NAMES[p];
            for (int k = 0; k < accessoryKinds.length; k++) {
                String id = slug(stem) + "_" + accessoryKinds[k].name().toLowerCase();
                if (isHandAuthoredAccessory(id)) continue;
                accessories.add(new Accessory(id, ACCESSORY_NAMES[p][k], accessoryKinds[k],
                        pal[0], pal[1]));
            }
        }
        ACCESSORIES = Collections.unmodifiableList(accessories);

        // Pets are the distinct species only. Each species is a separate authored model with its
        // own rig and animations, so a pet's variety comes from the species itself rather than a
        // palette recolour of a shared mesh — the earlier "Mini/Royal/Shadow" size recolours read
        // as the same animal tinted, which is not what a cosmetics catalogue should be.
        List<Pet> pets = new ArrayList<>();
        for (PetSpecies species : PetSpecies.values()) {
            pets.add(new Pet(slug(species.displayName), species.displayName,
                    species, species.baseColor, species.accentColor, 1f));
        }
        PETS = Collections.unmodifiableList(pets);
    }

    /** The accessory silhouettes the catalogue generates, index-aligned with {@link #ACCESSORY_NAMES}. */
    static final AccessoryKind[] ACCESSORY_KINDS = {
            AccessoryKind.CAP, AccessoryKind.BEANIE, AccessoryKind.CROWN,
            AccessoryKind.GLASSES, AccessoryKind.MASK, AccessoryKind.SCARF,
            AccessoryKind.BACKPACK, AccessoryKind.HORNS, AccessoryKind.FLOWER,
            AccessoryKind.BOWTIE, AccessoryKind.EAR, AccessoryKind.TOPHAT,
            AccessoryKind.WIZARD_HAT, AccessoryKind.TIARA, AccessoryKind.BEARD,
            AccessoryKind.VEIL, AccessoryKind.MONOCLE, AccessoryKind.ANTLERS,
            AccessoryKind.PLUME, AccessoryKind.TRICORN, AccessoryKind.MORTARBOARD
    };

    private static boolean isHandAuthored(String id) {
        return id.equals("chimera_solid") || id.equals("void_solid")
                || id.equals("flux_gradient") || id.equals("verdant_solid");
    }

    private static boolean isHandAuthoredAccessory(String id) {
        return id.equals("graphite_headphones") || id.equals("gilded_halo")
                || id.equals("amethyst_wings");
    }

    /** The designed name for a cape, from the palette's row and the pattern's column. */
    private static String capePatternName(int palette, CapePattern pattern) {
        int p = palette;
        int i = pattern == null ? 0 : pattern.ordinal();
        if (p < 0 || p >= CAPE_PATTERN_NAMES.length) p = 0;
        String[] row = CAPE_PATTERN_NAMES[p];
        return i >= 0 && i < row.length ? row[i] : "Cape";
    }

    private static String slug(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString();
    }

    /** Pure ARGB darkening; {@code android.graphics.Color} is unavailable in JVM tests. */
    private static int darken(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.6f);
        int g = (int) (((color >> 8) & 0xFF) * 0.6f);
        int b = (int) ((color & 0xFF) * 0.6f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private CosmeticCatalog() {
    }

    public static List<Cape> capes() {
        return CAPES;
    }

    public static List<Accessory> accessories() {
        return ACCESSORIES;
    }

    public static List<Pet> pets() {
        return PETS;
    }

    /** The default cape, which is the animated branded one so the section opens on motion. */
    public static Cape defaultCape() {
        return CAPES.get(0);
    }

    public static Cape cape(String id) {
        for (Cape c : CAPES) {
            if (c.id.equals(id)) return c;
        }
        return defaultCape();
    }

    /**
     * Resolves a stored cape id, treating an unknown or absent id as "no cape".
     *
     * Wearing a cape is a user choice, so a missing selection is honoured as none rather than
     * silently equipping the default.
     */
    public static Cape equippedCape(String id) {
        if (id == null || id.isEmpty() || NONE.equals(id)) return null;
        for (Cape c : CAPES) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public static Accessory accessory(String id) {
        for (Accessory a : ACCESSORIES) {
            if (a.id.equals(id)) return a;
        }
        return ACCESSORIES.get(0);
    }

    public static Accessory equippedAccessory(String id) {
        if (id == null || id.isEmpty() || NONE.equals(id)) return null;
        for (Accessory a : ACCESSORIES) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }

    public static Pet pet(String id) {
        for (Pet p : PETS) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    /** Resolves a stored pet id, treating an unknown or absent id as "no pet". */
    public static Pet equippedPet(String id) {
        if (id == null || id.isEmpty() || NONE.equals(id)) return null;
        return pet(id);
    }

    /** Toggling a cape off is selecting it again; a cape id never has an "off" duplicate. */
    public static String toggleCape(String current, String clicked) {
        if (clicked == null) return NONE;
        return clicked.equals(current) ? NONE : clicked;
    }

    public static String toggleAccessory(String current, String clicked) {
        if (clicked == null || NONE.equals(clicked)) return NONE;
        return clicked.equals(current) ? NONE : clicked;
    }

    /** A pet is worn the same way as a cape: selecting it again takes it off. */
    public static String togglePet(String current, String clicked) {
        if (clicked == null) return NONE;
        return clicked.equals(current) ? NONE : clicked;
    }
}
