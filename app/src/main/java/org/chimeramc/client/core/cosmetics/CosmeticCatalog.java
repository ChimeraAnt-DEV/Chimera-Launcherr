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
     * Evocative name stems per palette, used to compose a cape's name from its palette and pattern
     * so a generated cape reads as a designed piece ("Emberdusk Aurora") rather than a catalogue
     * entry ("Ember Gradient"). Index-aligned with {@link #PALETTES}.
     */
    private static final String[] CAPE_STEMS = {
            "Chimera", "Umbral", "Fluxweave", "Wildwood", "Emberdusk", "Abyssal",
            "Gilded", "Obsidian", "Rosewood", "Tidal", "Amethyst", "Creeping"
    };

    /** Evocative names per cape pattern, used with {@link #CAPE_STEMS}. Index-aligned with
     *  {@link CapePattern}; a shorter array falls back to "Weave". */
    private static final String[] CAPE_PATTERN_NAMES = {
            "Sovereign", "Tapestry", "Ribbon", "Aurora", "Starburst", "Lattice",
            "Chequered", "Chevron", "Horizon", "Riptide", "Wilds", "Celestial"
    };

    /**
     * Per-palette accessory names, one per {@link #ACCESSORY_KINDS} entry. Hand-written rather than
     * composed so an accessory reads like a themed item ("Ember Circlet") instead of a swatch name
     * ("Ember Tiara"). Index-aligned with {@link #PALETTES}.
     */
    private static final String[][] ACCESSORY_NAMES = {
            //        CAP            BEANIE        CROWN         GLASSES      MASK          SCARF        BACKPACK     HORNS        FLOWER       BOWTIE       EAR          TOPHAT       WIZARD_HAT   TIARA        BEARD        VEIL         MONOCLE      ANTLERS      PLUME        TRICORN      MORTARBOARD
            {"Chimera Crest", "Chimera Knit", "Chimera Circlet", "Chimera Visor", "Chimera Veil", "Chimera Stole", "Chimera Satchel", "Chimera Horns", "Chimera Bloom", "Chimera Knot", "Chimera Ears", "Chimera Stovepipe", "Chimera Arcanist", "Chimera Diadem", "Chimera Mane", "Chimera Shroud", "Chimera Lens", "Chimera Antlers", "Chimera Plume", "Chimera Corsair", "Chimera Scholar"},
            {"Void Cap", "Void Watchcap", "Void Diadem", "Void Shades", "Void Cowl", "Void Wrap", "Void Pack", "Void Spikes", "Void Nightshade", "Void Cravat", "Void Bat Ears", "Void Chimney", "Void Occultist", "Void Tiara", "Void Growth", "Void Shroud", "Void Monocle", "Void Bramble", "Void Quill", "Void Freebooter", "Void Dean"},
            {"Flux Beret", "Flux Beanie", "Flux Crown", "Flux Goggles", "Flux Mask", "Flux Scarf", "Flux Rucksack", "Flux Horns", "Flux Blossom", "Flux Bow", "Flux Ears", "Flux Top Hat", "Flux Hexer", "Flux Tiara", "Flux Beard", "Flux Veil", "Flux Lens", "Flux Antlers", "Flux Feather", "Flux Captain", "Flux Graduate"},
            {"Verdant Cap", "Verdant Beanie", "Verdant Crown", "Verdant Specs", "Verdant Mask", "Verdant Scarf", "Verdant Pack", "Verdant Bramble", "Verdant Daisy", "Verdant Knot", "Verdant Ears", "Verdant Tall Hat", "Verdant Druid", "Verdant Tiara", "Verdant Mossbeard", "Verdant Veil", "Verdant Monocle", "Verdant Antlers", "Verdant Frond", "Verdant Explorer", "Verdant Scholar"},
            {"Ember Cap", "Ember Beanie", "Ember Circlet", "Ember Goggles", "Ember Mask", "Ember Scarf", "Ember Pack", "Ember Horns", "Ember Lily", "Ember Bow", "Ember Ears", "Ember Stovepipe", "Ember Pyromancer", "Ember Tiara", "Ember Beard", "Ember Veil", "Ember Lens", "Ember Antlers", "Ember Plume", "Ember Corsair", "Ember Mortarboard"},
            {"Abyss Cap", "Abyss Watchcap", "Abyss Crown", "Abyss Lenses", "Abyss Mask", "Abyss Scarf", "Abyss Pack", "Abyss Horns", "Abyss Coral", "Abyss Knot", "Abyss Fins", "Abyss Top Hat", "Abyss Tidecaller", "Abyss Tiara", "Abyss Beard", "Abyss Veil", "Abyss Monocle", "Abyss Antlers", "Abyss Plume", "Abyss Captain", "Abyss Scholar"},
            {"Gilded Cap", "Gilded Beanie", "Gilded Crown", "Gilded Monocle", "Gilded Mask", "Gilded Scarf", "Gilded Coffer", "Gilded Horns", "Gilded Rose", "Gilded Bow", "Gilded Ears", "Gilded Stovepipe", "Gilded Archmage", "Gilded Diadem", "Gilded Mane", "Gilded Veil", "Gilded Lens", "Gilded Antlers", "Gilded Plume", "Gilded Corsair", "Gilded Graduate"},
            {"Graphite Cap", "Graphite Watchcap", "Graphite Crown", "Graphite Shades", "Graphite Mask", "Graphite Scarf", "Graphite Pack", "Graphite Horns", "Graphite Bloom", "Graphite Bow", "Graphite Ears", "Graphite Chimney", "Graphite Occultist", "Graphite Tiara", "Graphite Beard", "Graphite Veil", "Graphite Monocle", "Graphite Antlers", "Graphite Quill", "Graphite Freebooter", "Graphite Dean"},
            {"Rosewood Beret", "Rosewood Beanie", "Rosewood Circlet", "Rosewood Specs", "Rosewood Mask", "Rosewood Scarf", "Rosewood Pack", "Rosewood Horns", "Rosewood Camellia", "Rosewood Bow", "Rosewood Ears", "Rosewood Top Hat", "Rosewood Enchanter", "Rosewood Tiara", "Rosewood Beard", "Rosewood Veil", "Rosewood Monocle", "Rosewood Antlers", "Rosewood Plume", "Rosewood Corsair", "Rosewood Scholar"},
            {"Tidal Cap", "Tidal Beanie", "Tidal Crown", "Tidal Goggles", "Tidal Mask", "Tidal Scarf", "Tidal Pack", "Tidal Horns", "Tidal Lotus", "Tidal Bow", "Tidal Fins", "Tidal Stovepipe", "Tidal Tidecaller", "Tidal Tiara", "Tidal Beard", "Tidal Veil", "Tidal Lens", "Tidal Antlers", "Tidal Plume", "Tidal Captain", "Tidal Graduate"},
            {"Amethyst Beret", "Amethyst Beanie", "Amethyst Crown", "Amethyst Lenses", "Amethyst Mask", "Amethyst Scarf", "Amethyst Pack", "Amethyst Horns", "Amethyst Blossom", "Amethyst Bow", "Amethyst Ears", "Amethyst Top Hat", "Amethyst Arcanist", "Amethyst Diadem", "Amethyst Beard", "Amethyst Veil", "Amethyst Monocle", "Amethyst Antlers", "Amethyst Plume", "Amethyst Corsair", "Amethyst Scholar"},
            {"Creeper Cap", "Creeper Beanie", "Creeper Crown", "Creeper Goggles", "Creeper Mask", "Creeper Scarf", "Creeper Pack", "Creeper Horns", "Creeper Bloom", "Creeper Bow", "Creeper Ears", "Creeper Stovepipe", "Creeper Hexer", "Creeper Tiara", "Creeper Beard", "Creeper Veil", "Creeper Monocle", "Creeper Antlers", "Creeper Plume", "Creeper Corsair", "Creeper Scholar"}
    };

    static {
        List<Cape> capes = new ArrayList<>();

        // The flagship cape keeps its exact id and stays first, animated and branded, so the
        // section opens on the one piece of motion it is meant to show off.
        capes.add(new Cape("chimera", "Chimera Cape", 0xFF6236E8, 0xFFA88CFF, 0xFFFFD86B,
                CapePattern.SOLID, true, true));

        // The classic ids are preserved verbatim so existing selections keep resolving. Their
        // display names are upgraded to the designed naming the rest of the catalogue uses.
        capes.add(new Cape("void_black", "Umbral Sovereign", 0xFF141418, 0xFF3A3A44, 0xFF8F979F,
                CapePattern.SOLID, false, false));
        capes.add(new Cape("magenta_flux", "Fluxweave Aurora", 0xFFA82E9E, 0xFFE070C0, 0xFFFFE1F4,
                CapePattern.GRADIENT, false, false));
        capes.add(new Cape("verdant", "Wildwood Sovereign", 0xFF1F7A4D, 0xFF63D69B, 0xFFE9FFF3,
                CapePattern.SOLID, false, false));

        // Generated families: every palette crossed with every pattern. The brand mark rides on
        // the "Chimera" palette's Split weave only, so the flagship stays special instead of every
        // cape being branded. Names are composed from the palette's evocative stem and the
        // pattern's own name so a cape reads as a designed piece.
        for (int p = 0; p < PALETTES.length; p++) {
            int[] pal = PALETTES[p];
            String stem = PALETTE_NAMES[p];
            String nameStem = CAPE_STEMS[p];
            for (CapePattern pattern : CapePattern.values()) {
                String id = slug(stem) + "_" + pattern.name().toLowerCase();
                if (isHandAuthored(id)) continue;
                boolean branded = "Chimera".equals(stem) && pattern == CapePattern.SPLIT;
                capes.add(new Cape(id, nameStem + " " + capePatternName(pattern),
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

    /** The evocative name for a cape pattern, used with {@link #CAPE_STEMS}. */
    private static String capePatternName(CapePattern pattern) {
        int i = pattern.ordinal();
        return i >= 0 && i < CAPE_PATTERN_NAMES.length ? CAPE_PATTERN_NAMES[i] : "Weave";
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
