package org.chimeramc.client.core.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The default Steve character's texture regions, as a pure table.
 *
 * <p>Painted from the same UV table the renderer samples ({@link SkinModel}), so the default
 * character cannot drift from the model: the head, body, arms and legs use the base-layer regions,
 * and the second layer carries Steve's hair. Keeping the table pure means a coordinate typo — the
 * kind that silently paints a limb in the wrong place — is caught by a JVM test rather than only
 * being visible on a device.
 *
 * <p>Colours are the classic Steve palette. The preview's {@link PreviewLighting} pass adds the
 * shading, exactly as it does for an imported skin, so these are flat base colours.
 */
public final class SteveSkinLayout {

    /** One filled rectangle in the 64x64 atlas, with the ARGB colour to fill it. */
    public static final class Region {
        public final int u, v, w, h, color;

        Region(int u, int v, int w, int h, int color) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.color = color;
        }
    }

    // The palette is sampled from the project's own Steve model
    // (GlowberryClientPets/steve-minecraft), so the default character in the preview is that
    // exact skin rather than a generic approximation: teal shirt, blue trousers, tan skin and
    // the model's near-black hair. All opaque — the preview never draws a translucent default.
    public static final int SKIN = 0xFFAB7D66;
    public static final int HAIR = 0xFF281B0B;
    public static final int SHIRT = 0xFF00AEAD;
    public static final int SHIRT_DARK = 0xFF009A9A;
    public static final int TROUSERS = 0xFF4639A6;
    public static final int TROUSERS_DARK = 0xFF352C80;
    public static final int SHOES = 0xFF302872;
    public static final int MOUTH = 0xFF6E4B3A;
    public static final int EYE_WHITE = 0xFFF2F2F2;
    public static final int EYE_PUPIL = 0xFF3B3B8B;

    private static final List<Region> REGIONS;

    static {
        List<Region> r = new ArrayList<>();

        // Head: hair on top and back, face front, skin elsewhere.
        add(r, 8, 0, 8, 8, HAIR);    // top
        add(r, 16, 0, 8, 8, SKIN);   // bottom (neck)
        add(r, 0, 8, 8, 8, SKIN);    // right
        add(r, 8, 8, 8, 8, SKIN);    // front
        add(r, 16, 8, 8, 8, SKIN);   // left
        add(r, 24, 8, 8, 8, HAIR);   // back
        // Face: two eyes and a mouth so it reads as a face at preview size.
        add(r, 9, 12, 2, 1, EYE_WHITE);
        add(r, 10, 12, 1, 1, EYE_PUPIL);
        add(r, 13, 12, 2, 1, EYE_WHITE);
        add(r, 14, 12, 1, 1, EYE_PUPIL);
        add(r, 11, 14, 2, 1, MOUTH);

        // Hair second layer: crown, sides and back, plus a fringe that leaves a band over the eyes
        // open so the face shows through.
        add(r, 40, 0, 8, 8, HAIR);   // overlay top
        add(r, 32, 8, 8, 8, HAIR);   // overlay right
        add(r, 40, 8, 8, 3, HAIR);   // overlay front: fringe only
        add(r, 48, 8, 8, 8, HAIR);   // overlay left
        add(r, 56, 8, 8, 8, HAIR);   // overlay back

        // Body: the shirt, with the side and back strips a step darker so the torso reads as a
        // lit surface rather than one flat colour (the reference model is shaded this way).
        add(r, 20, 16, 8, 4, SHIRT);       // top
        add(r, 28, 16, 8, 4, SHIRT_DARK);  // bottom
        add(r, 16, 20, 4, 12, SHIRT_DARK); // right
        add(r, 20, 20, 8, 12, SHIRT);      // front
        add(r, 28, 20, 4, 12, SHIRT_DARK); // left
        add(r, 32, 20, 8, 12, SHIRT_DARK); // back

        // Arms: teal sleeves (front/top lit, sides/back shaded) with the bare hand at the bottom.
        addArm(r, 44, 16, 40, 20);
        addArm(r, 36, 48, 32, 52);

        // Legs: blue trousers with darker boots at the bottom of each side strip.
        addLeg(r, 4, 16, 0, 20);
        addLeg(r, 20, 48, 16, 52);

        REGIONS = Collections.unmodifiableList(r);
    }

    private SteveSkinLayout() {
    }

    /** Every region to paint, in paint order. */
    public static List<Region> regions() {
        return REGIONS;
    }

    /**
     * An arm: a sleeve over the upper 8px and the bare hand at the lowest 4px of each side strip.
     * The front strip (i==1) is the lit shade and the other three a step darker, so the limb reads
     * as a rounded surface. The 4 side strips are the standard right/front/left/back order.
     */
    private static void addArm(List<Region> r, int topU, int topV, int sideU, int sideV) {
        add(r, topU, topV, 4, 4, SHIRT);       // top
        add(r, topU + 4, topV, 4, 4, SHIRT_DARK); // bottom
        for (int i = 0; i < 4; i++) {
            int sleeve = i == 1 ? SHIRT : SHIRT_DARK;
            add(r, sideU + i * 4, sideV, 4, 8, sleeve);
            add(r, sideU + i * 4, sideV + 8, 4, 4, SKIN);
        }
    }

    /**
     * A leg: trousers over the upper 8px and the boot at the lowest 4px of each side strip. The
     * front strip is the lit shade, the rest darker, matching the arm treatment.
     */
    private static void addLeg(List<Region> r, int topU, int topV, int sideU, int sideV) {
        add(r, topU, topV, 4, 4, TROUSERS);
        add(r, topU + 4, topV, 4, 4, SHOES);      // sole
        for (int i = 0; i < 4; i++) {
            int cloth = i == 1 ? TROUSERS : TROUSERS_DARK; // front lit, sides/back darker
            add(r, sideU + i * 4, sideV, 4, 8, cloth);
            add(r, sideU + i * 4, sideV + 8, 4, 4, SHOES);
        }
    }

    private static void add(List<Region> r, int u, int v, int w, int h, int color) {
        r.add(new Region(u, v, w, h, color));
    }
}
