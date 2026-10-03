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
        BEARD
    }

    /** What a pet can do; a species advertises which of these it animates for. */
    public enum PetLocomotion {
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
        CAT("Cat", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH, PetLocomotion.SWIM),
        DOG("Dog", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH, PetLocomotion.SWIM),
        FOX("Fox", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        WOLF("Wolf", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        RABBIT("Rabbit", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        PARROT("Parrot", PetLocomotion.WALK, PetLocomotion.FLY),
        BEE("Bee", PetLocomotion.WALK, PetLocomotion.FLY),
        BUTTERFLY("Butterfly", PetLocomotion.WALK, PetLocomotion.FLY),
        DRAGONFLY("Dragonfly", PetLocomotion.WALK, PetLocomotion.FLY),
        BEETLE("Beetle", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH,
                PetLocomotion.FLY),
        SPIDER("Spider", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        ANT("Ant", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        FROG("Frog", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.SWIM),
        AXOLOTL("Axolotl", PetLocomotion.WALK, PetLocomotion.SWIM),
        TURTLE("Turtle", PetLocomotion.WALK, PetLocomotion.CROUCH, PetLocomotion.SWIM),
        SNAKE("Snake", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH,
                PetLocomotion.SWIM),
        LIZARD("Lizard", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH),
        DRAGON("Dragon", PetLocomotion.WALK, PetLocomotion.RUN, PetLocomotion.CROUCH,
                PetLocomotion.FLY, PetLocomotion.SWIM);

        public final String displayName;
        private final PetLocomotion[] gaits;

        PetSpecies(String displayName, PetLocomotion... gaits) {
            this.displayName = displayName;
            this.gaits = gaits;
        }

        public boolean supports(PetLocomotion locomotion) {
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

    static {
        List<Cape> capes = new ArrayList<>();

        // The flagship cape keeps its exact id and stays first, animated and branded, so the
        // section opens on the one piece of motion it is meant to show off.
        capes.add(new Cape("chimera", "Chimera Cape", 0xFF6236E8, 0xFFA88CFF, 0xFFFFD86B,
                CapePattern.SOLID, true, true));

        // The classic solid ids are preserved verbatim so existing selections keep resolving.
        capes.add(new Cape("void_black", "Void Black", 0xFF141418, 0xFF3A3A44, 0xFF8F979F,
                CapePattern.SOLID, false, false));
        capes.add(new Cape("magenta_flux", "Magenta Flux", 0xFFA82E9E, 0xFFE070C0, 0xFFFFE1F4,
                CapePattern.GRADIENT, false, false));
        capes.add(new Cape("verdant", "Verdant", 0xFF1F7A4D, 0xFF63D69B, 0xFFE9FFF3,
                CapePattern.SOLID, false, false));

        // Generated families: every palette crossed with every pattern. The brand mark rides on
        // the "Chimera" palette's Split weave only, so the flagship stays special instead of every
        // cape being branded.
        for (int p = 0; p < PALETTES.length; p++) {
            int[] pal = PALETTES[p];
            String stem = PALETTE_NAMES[p];
            for (CapePattern pattern : CapePattern.values()) {
                String id = slug(stem) + "_" + pattern.name().toLowerCase();
                if (isHandAuthored(id)) continue;
                boolean branded = "Chimera".equals(stem) && pattern == CapePattern.SPLIT;
                capes.add(new Cape(id, stem + " " + patternName(pattern),
                        pal[0], pal[1], pal[2], pattern, false, branded));
            }
        }
        CAPES = Collections.unmodifiableList(capes);

        List<Accessory> accessories = new ArrayList<>();
        accessories.add(new Accessory("none", "None", AccessoryKind.NONE, 0x00000000, 0x00000000));
        // Preserved classic ids so a stored selection keeps working.
        accessories.add(new Accessory("headphones", "Headphones", AccessoryKind.HEADPHONES,
                0xFF2B2F36, 0xFF6C757D));
        accessories.add(new Accessory("halo", "Halo", AccessoryKind.HALO, 0xFFFFD86B, 0xFFFFF3C4));
        accessories.add(new Accessory("wings", "Wings", AccessoryKind.WINGS, 0xFFA88CFF, 0xFF6236E8));

        // Generated accessories: every drawing kind crossed with a palette.
        AccessoryKind[] kinds = {
                AccessoryKind.CAP, AccessoryKind.BEANIE, AccessoryKind.CROWN,
                AccessoryKind.GLASSES, AccessoryKind.MASK, AccessoryKind.SCARF,
                AccessoryKind.BACKPACK, AccessoryKind.HORNS, AccessoryKind.FLOWER,
                AccessoryKind.BOWTIE, AccessoryKind.EAR, AccessoryKind.TOPHAT,
                AccessoryKind.WIZARD_HAT, AccessoryKind.TIARA, AccessoryKind.BEARD
        };
        String[] kindNames = {
                "Cap", "Beanie", "Crown", "Glasses", "Mask", "Scarf",
                "Backpack", "Horns", "Flower", "Bowtie", "Ear", "Top Hat",
                "Wizard Hat", "Tiara", "Beard"
        };
        for (int p = 0; p < PALETTES.length; p++) {
            int[] pal = PALETTES[p];
            String stem = PALETTE_NAMES[p];
            for (int k = 0; k < kinds.length; k++) {
                String id = slug(stem) + "_" + kinds[k].name().toLowerCase();
                if (isHandAuthoredAccessory(id)) continue;
                accessories.add(new Accessory(id, stem + " " + kindNames[k], kinds[k],
                        pal[0], pal[1]));
            }
        }
        ACCESSORIES = Collections.unmodifiableList(accessories);

        List<Pet> pets = new ArrayList<>();
        // A pet family: species crossed with palettes and a few size traits, so there are many
        // distinct looks per species while each one still reads as its species.
        String[] traitNames = {"", "Mini", "Royal", "Shadow"};
        for (PetSpecies species : PetSpecies.values()) {
            for (int p = 0; p < PALETTES.length; p++) {
                int[] pal = PALETTES[p];
                String stem = PALETTE_NAMES[p];
                for (int t = 0; t < traitNames.length; t++) {
                    float scale = t == 1 ? 0.75f : (t == 2 ? 1.18f : 1f);
                    int body = t == 3 ? darken(pal[0]) : pal[0];
                    String trait = traitNames[t].isEmpty() ? "" : traitNames[t] + " ";
                    String id = slug(species.displayName) + "_" + slug(stem)
                            + (trait.isEmpty() ? "" : "_" + t);
                    pets.add(new Pet(id, trait + stem + " " + species.displayName,
                            species, body, pal[1], scale));
                }
            }
        }
        PETS = Collections.unmodifiableList(pets);
    }

    private static boolean isHandAuthored(String id) {
        return id.equals("chimera_solid") || id.equals("void_solid")
                || id.equals("flux_gradient") || id.equals("verdant_solid");
    }

    private static boolean isHandAuthoredAccessory(String id) {
        return id.equals("graphite_headphones") || id.equals("gilded_halo")
                || id.equals("amethyst_wings");
    }

    private static String patternName(CapePattern pattern) {
        switch (pattern) {
            case SOLID: return "Solid";
            case VERTICAL_STRIPES: return "Stripes";
            case HORIZONTAL_STRIPES: return "Bands";
            case GRADIENT: return "Gradient";
            case SPLIT: return "Split";
            case GRID: return "Grid";
            case CHECKER: return "Checker";
            case CHEVRON: return "Chevron";
            case HORIZON: return "Horizon";
            case WAVE: return "Wave";
            case CAMO: return "Camo";
            case STAR: return "Star";
            default: return "Solid";
        }
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
