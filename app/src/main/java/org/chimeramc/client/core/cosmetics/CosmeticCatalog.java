package org.chimeramc.client.core.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The catalogue of GlowberryClient cosmetics: capes and accessories.
 *
 * This class is pure data plus pure selection logic, so "which cape is equipped" and "is the
 * first cape animated" are unit-testable without an Android context or a running overlay.
 * The drawing lives in the overlay's preview view; the catalogue only says what exists.
 *
 * Scope honesty: these are launcher-side cosmetics. Bedrock does not expose a general custom
 * cape slot to third-party clients, so nothing here claims a cape will appear on other
 * players' screens — the UI carries that note rather than implying it works.
 */
public final class CosmeticCatalog {

    /** A cape the player can equip. */
    public static final class Cape {
        public final String id;
        public final String name;
        /** Fill colour of the cape cloth. */
        public final int color;
        /** Trim colour around the cape and its collar. */
        public final int trimColor;
        /** Whether the brand mark crawls across the cape. */
        public final boolean animated;
        /** Whether the cape carries the GlowberryClient brand mark at all. */
        public final boolean branded;

        public Cape(String id, String name, int color, int trimColor, boolean animated, boolean branded) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.trimColor = trimColor;
            this.animated = animated;
            this.branded = branded;
        }
    }

    /** A worn accessory. */
    public static final class Accessory {
        public final String id;
        public final String name;
        public final int color;

        public Accessory(String id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    public static final String NONE = "none";

    private static final List<Cape> CAPES;
    private static final List<Accessory> ACCESSORIES;

    static {
        List<Cape> capes = new ArrayList<>();
        // The first cape is the animated GlowberryClient cape: the brand mark crawls across the
        // cloth, which is the one piece of motion this section is meant to show off.
        capes.add(new Cape("chimera", "Chimera Cape", 0xFF6236E8, 0xFFA88CFF, true, true));
        capes.add(new Cape("void_black", "Void Black", 0xFF141418, 0xFF3A3A44, false, false));
        capes.add(new Cape("magenta_flux", "Magenta Flux", 0xFFA82E9E, 0xFFE070C0, false, false));
        capes.add(new Cape("verdant", "Verdant", 0xFF1F7A4D, 0xFF63D69B, false, false));
        CAPES = Collections.unmodifiableList(capes);

        List<Accessory> accessories = new ArrayList<>();
        accessories.add(new Accessory("none", "None", 0x00000000));
        accessories.add(new Accessory("headphones", "Headphones", 0xFF2B2F36));
        accessories.add(new Accessory("halo", "Halo", 0xFFFFD86B));
        accessories.add(new Accessory("wings", "Wings", 0xFFA88CFF));
        ACCESSORIES = Collections.unmodifiableList(accessories);
    }

    private CosmeticCatalog() {
    }

    public static List<Cape> capes() {
        return CAPES;
    }

    public static List<Accessory> accessories() {
        return ACCESSORIES;
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

    /** Toggling a cape off is selecting it again; a cape id never has an "off" duplicate. */
    public static String toggleCape(String current, String clicked) {
        if (clicked == null) return NONE;
        return clicked.equals(current) ? NONE : clicked;
    }

    public static String toggleAccessory(String current, String clicked) {
        if (clicked == null || NONE.equals(clicked)) return NONE;
        return clicked.equals(current) ? NONE : clicked;
    }
}
