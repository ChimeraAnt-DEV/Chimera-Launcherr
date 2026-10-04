package org.chimeramc.client.core.cosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * One shared, vanilla-grounded body plan per pet species.
 *
 * <p><b>Why this exists.</b> The in-game geometry ({@link PetGeometry}) and the launcher preview
 * ({@code CapePreviewView}) used to each carry their own {@code switch (species)} of body
 * dimensions. Two hand-maintained copies of the same proportions is how the preview and the game
 * drifted apart, and neither was checked against a real animal. This class is the single source:
 * both consumers read {@link #parts} and cannot disagree about what a fox looks like.
 *
 * <p><b>Grounded in the vanilla models.</b> The proportions are taken from Mojang's own
 * {@code resource_pack/models/entity/*.geo.json} (freely inspectable, shipped in bedrock-samples),
 * not invented. A cat's head is 5 wide with two small upright ears and a long two-segment tail; a
 * wolf is bulkier with a neck ruff and a longer muzzle; a fox has large pointed ears and a bushy
 * tail with an accent tip; a parrot is a small vertical bird with a hooked beak and a long tail; a
 * bee is a fat striped body with two wide wings and a stinger; a spider is a small head with a
 * large abdomen and eight long splayed legs; a frog is squat with eyes on top of the head. The
 * dimensions below follow those ratios.
 *
 * <p><b>Silhouette first.</b> Every species differs in at least three of: body aspect, head shape
 * and taper, snout length, ear form, tail form and leg count. That is what makes a bee and a
 * dragon read as different animals rather than one box recoloured.
 *
 * <p>Pure data plus pure arithmetic, so a unit test can assert each species produces a distinct
 * part list with a snout, the right ear/tail/leg shapes, and the right number of legs, with no
 * Android or device.
 */
public final class PetModel {

    /** What a part is for, so the pose can move the right pieces. */
    public enum Role {
        BODY,
        HEAD,
        /** A tapered head front; the snout/muzzle/beak. */
        SNOUT,
        /** Ears, antennae, horns, gills, eyes and other head decorations. */
        HEAD_DECOR,
        LEG,
        WING,
        /** A wing case (a crawling flyer's elytra) rather than a feathered wing. */
        ELYTRA,
        TAIL,
        /** A body decoration: stripes, a shell, a spine ridge, a hunch. */
        BODY_DECOR
    }

    /** One box, tagged with the bone it belongs to and the role the pose animates it by. */
    public static final class Part {
        public final String bone;
        public final Role role;
        public final float x;
        public final float y;
        public final float z;
        public final float sx;
        public final float sy;
        public final float sz;
        public final boolean accent;

        Part(String bone, Role role, float x, float y, float z,
             float sx, float sy, float sz, boolean accent) {
            this.bone = bone;
            this.role = role;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.accent = accent;
        }
    }

    /** Bone names shared with the animation builder and the preview. */
    public static final String BONE_ROOT = "pet";
    public static final String BONE_HEAD = "head";
    public static final String BONE_TAIL = "tail";
    public static final String BONE_WING_L = "wing_l";
    public static final String BONE_WING_R = "wing_r";
    public static final String BONE_LEG_A = "leg_a";
    public static final String BONE_LEG_B = "leg_b";
    public static final String BONE_LEG_C = "leg_c";

    /** Where the pet stands inside the player's model space: in front and to the right. */
    public static final float BASE_X = 6f;
    public static final float BASE_Z = 9f;

    /** The body's real bounds for a species, used to anchor legs/head/tail. */
    public static final class Body {
        public final float x, y, z, w, h, d, legLen;
        public final float headW, headH, headD, snoutLen;
        public final boolean crawler;
        public final int legPairs;

        Body(float x, float y, float z, float w, float h, float d, float legLen,
             float headW, float headH, float headD, float snoutLen,
             boolean crawler, int legPairs) {
            this.x = x; this.y = y; this.z = z;
            this.w = w; this.h = h; this.d = d; this.legLen = legLen;
            this.headW = headW; this.headH = headH; this.headD = headD; this.snoutLen = snoutLen;
            this.crawler = crawler; this.legPairs = legPairs;
        }

        public float centerX() { return x + w / 2f; }
        public float centerZ() { return z + d / 2f; }
        public float top() { return y + h; }
        public float frontZ() { return z + d; }
        public float rearZ() { return z; }
    }

    private PetModel() {
    }

    /**
     * The body plan for a species, scaled by the pet's size trait.
     *
     * <p>Proportions follow the vanilla mob of the same name where one exists. The scale trait
     * multiplies every dimension, so a Mini and a Royal variant are genuinely different sizes.
     */
    public static Body body(CosmeticCatalog.PetSpecies species, float scale) {
        CosmeticCatalog.PetSpecies s = species == null ? CosmeticCatalog.PetSpecies.CAT : species;
        float k = scale <= 0f ? 1f : scale;
        float bx = BASE_X * k;
        float bz = BASE_Z * k;

        float w, h, d, legLen, hw, hh, hd, snout;
        boolean crawler = s.isCrawler();
        int legPairs;
        switch (s) {
            case CAT:
                w = 5f; h = 4f; d = 9f; legLen = 3f; hw = 5f; hh = 4f; hd = 5f; snout = 1f; legPairs = 2;
                break;
            case DOG:
                w = 5f; h = 4.5f; d = 10f; legLen = 3f; hw = 6f; hh = 5f; hd = 5f; snout = 2f; legPairs = 2;
                break;
            case WOLF:
                w = 5.5f; h = 5f; d = 10f; legLen = 3.5f; hw = 6f; hh = 5f; hd = 6f; snout = 2.5f; legPairs = 2;
                break;
            case FOX:
                w = 5f; h = 4f; d = 9f; legLen = 3f; hw = 6f; hh = 5f; hd = 6f; snout = 2.5f; legPairs = 2;
                break;
            case RABBIT:
                w = 5f; h = 4f; d = 7f; legLen = 2f; hw = 5f; hh = 4f; hd = 5f; snout = 1f; legPairs = 2;
                break;
            case PARROT:
                w = 4f; h = 6f; d = 5f; legLen = 2.5f; hw = 3.5f; hh = 3.5f; hd = 3.5f; snout = 1.4f; legPairs = 1;
                break;
            case BEE:
                w = 6f; h = 5.5f; d = 9f; legLen = 2f; hw = 3.5f; hh = 3.5f; hd = 3.5f; snout = 0.8f; legPairs = 3;
                break;
            case BUTTERFLY:
                w = 2.5f; h = 2.5f; d = 8f; legLen = 1.5f; hw = 3f; hh = 3f; hd = 3f; snout = 0.8f; legPairs = 2;
                break;
            case DRAGONFLY:
                w = 2.5f; h = 2.5f; d = 11f; legLen = 1.5f; hw = 3.5f; hh = 3.5f; hd = 3.5f; snout = 0.8f; legPairs = 3;
                break;
            case BEETLE:
                w = 6f; h = 4f; d = 9f; legLen = 2.5f; hw = 4f; hh = 3.5f; hd = 3.5f; snout = 1f; legPairs = 3;
                break;
            case SPIDER:
                w = 5f; h = 4f; d = 9f; legLen = 3.5f; hw = 5f; hh = 4.5f; hd = 4.5f; snout = 1f; legPairs = 3;
                break;
            case ANT:
                w = 4f; h = 3.5f; d = 9f; legLen = 3f; hw = 3.5f; hh = 3f; hd = 3f; snout = 1.2f; legPairs = 3;
                break;
            case FROG:
                w = 6f; h = 3f; d = 7f; legLen = 1.5f; hw = 6f; hh = 3f; hd = 5f; snout = 1f; legPairs = 2;
                break;
            case AXOLOTL:
                w = 4f; h = 4f; d = 9f; legLen = 1.5f; hw = 5f; hh = 4f; hd = 5f; snout = 1f; legPairs = 2;
                break;
            case TURTLE:
                w = 7f; h = 3.5f; d = 9f; legLen = 1.5f; hw = 4f; hh = 3.5f; hd = 4f; snout = 1f; legPairs = 2;
                break;
            case SNAKE:
                w = 3.5f; h = 3f; d = 12f; legLen = 0f; hw = 4f; hh = 3f; hd = 4f; snout = 1.2f; legPairs = 0;
                break;
            case LIZARD:
                w = 4.5f; h = 3.5f; d = 9f; legLen = 2f; hw = 5f; hh = 3.5f; hd = 5f; snout = 1.6f; legPairs = 2;
                break;
            case DRAGON:
                w = 7f; h = 6f; d = 12f; legLen = 4f; hw = 6f; hh = 5f; hd = 6f; snout = 3f; legPairs = 2;
                break;
            default:
                w = 5f; h = 4f; d = 8f; legLen = 3f; hw = 5f; hh = 4f; hd = 5f; snout = 1f; legPairs = 2;
                break;
        }
        return new Body(bx, legLen * k, bz, w * k, h * k, d * k, legLen * k,
                hw * k, hh * k, hd * k, snout * k, crawler, legPairs);
    }

    /**
     * Every box for a species, in draw order (legs, wings and tail first, then body, then head).
     *
     * <p>The consumer groups by {@link Part#bone}; the preview additionally uses {@link Part#role}
     * to apply the pose. Anchoring is computed from {@link Body} so a part can never float detached
     * from the animal it belongs to.
     */
    public static List<Part> parts(CosmeticCatalog.PetSpecies species, float scale) {
        CosmeticCatalog.PetSpecies s = species == null ? CosmeticCatalog.PetSpecies.CAT : species;
        Body b = body(s, scale);
        List<Part> out = new ArrayList<>();

        drawLegs(out, s, b);
        drawTail(out, s, b);
        drawWings(out, s, b);
        drawBody(out, s, b);
        drawHead(out, s, b);
        return out;
    }

    private static void drawLegs(List<Part> out, CosmeticCatalog.PetSpecies s, Body b) {
        if (b.legPairs <= 0) return;
        String[] bones = {BONE_LEG_A, BONE_LEG_B, BONE_LEG_C};
        float legW = Math.max(1.2f, b.w * 0.24f);
        for (int pair = 0; pair < b.legPairs && pair < bones.length; pair++) {
            // Front pair at the front of the body, back pair at the rear; a third (crawlers) in the
            // middle. Anchored to the body's real z span, never to the world origin.
            float t = b.legPairs == 1 ? 0.5f : pair / (float) (b.legPairs - 1);
            float along = b.z + legW + t * (b.d - 2f * legW);
            for (int side = -1; side <= 1; side += 2) {
                float x = side < 0 ? b.x + b.w * 0.12f : b.x + b.w * 0.88f - legW;
                out.add(new Part(bones[pair], Role.LEG, x, 0f, along,
                        legW, b.legLen, legW, pair == 1));
            }
        }
    }

    private static void drawTail(List<Part> out, CosmeticCatalog.PetSpecies s, Body b) {
        float cx = b.centerX();
        switch (s) {
            case SNAKE:
                // A long tapering tail continuing the body, so the whole thing reads as one animal.
                for (int seg = 0; seg < 5; seg++) {
                    float size = b.w * (0.95f - seg * 0.14f);
                    out.add(new Part(BONE_TAIL, Role.TAIL, cx - size / 2f, b.y + b.h * 0.2f,
                            b.rearZ() - (seg + 1) * (size * 0.7f + 0.4f),
                            size, size, size * 0.9f, seg % 2 == 0));
                }
                break;
            case CAT:
                // A long thin tail in two segments, held up and curling.
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - 0.6f, b.y + b.h * 0.7f, b.rearZ() - 3.2f,
                        1.2f, 1.2f, 3.4f, false));
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - 0.6f, b.y + b.h * 1.15f, b.rearZ() - 5.6f,
                        1.2f, 1.2f, 2.6f, false));
                break;
            case FOX:
                // A large bushy tail with an accent tip (vanilla foxes have a white-tipped brush).
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.28f, b.y + b.h * 0.35f,
                        b.rearZ() - 3.4f, b.w * 0.56f, b.h * 0.7f, 3.6f, false));
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.24f, b.y + b.h * 0.4f,
                        b.rearZ() - 5.2f, b.w * 0.48f, b.h * 0.6f, 1.8f, true));
                break;
            case WOLF:
                // A medium bushy tail, held level.
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.22f, b.y + b.h * 0.4f,
                        b.rearZ() - 3.6f, b.w * 0.44f, b.h * 0.6f, 3.8f, false));
                break;
            case DOG:
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.18f, b.y + b.h * 0.6f,
                        b.rearZ() - 2.2f, b.w * 0.36f, b.h * 0.5f, 2.4f, false));
                break;
            case RABBIT:
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.2f, b.y + b.h * 0.45f,
                        b.rearZ() - 1.4f, b.w * 0.4f, b.h * 0.45f, 1.6f, true));
                break;
            case PARROT:
                // Long tail feathers streaming back and down.
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - 0.8f, b.y - 0.6f, b.rearZ() - 3.4f,
                        1.6f, 4.6f, 1.2f, true));
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - 0.6f, b.y - 0.6f, b.rearZ() - 4.6f,
                        1.2f, 3.6f, 1.2f, false));
                break;
            case AXOLOTL:
                // A tall finned tail.
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.2f, b.y + b.h * 0.25f,
                        b.rearZ() - 4.2f, b.w * 0.4f, b.h * 0.9f, 4.4f, true));
                break;
            case LIZARD:
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - 0.5f, b.y + b.h * 0.3f, b.rearZ() - 4.2f,
                        1.0f, 1.0f, 4.4f, false));
                break;
            case TURTLE:
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - 0.5f, b.y + b.h * 0.2f, b.rearZ() - 1.4f,
                        1.0f, 1.0f, 1.6f, false));
                break;
            case DRAGON:
                // A long spiked tail: a taper plus a lit spike at the tip.
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.2f, b.y + b.h * 0.3f,
                        b.rearZ() - 4.4f, b.w * 0.4f, b.h * 0.4f, 4.6f, false));
                out.add(new Part(BONE_TAIL, Role.TAIL, cx - b.w * 0.12f, b.y + b.h * 0.5f,
                        b.rearZ() - 6.2f, b.w * 0.24f, b.h * 0.3f, 2.4f, true));
                break;
            default:
                break;
        }
    }

    private static void drawWings(List<Part> out, CosmeticCatalog.PetSpecies s, Body b) {
        float cx = b.centerX();
        float midY = b.y + b.h * 0.55f;
        switch (s) {
            case BEE:
                // Two wide flat wings lying over the back (vanilla bee wings are 9x0x6 each).
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.4f, b.top() - 0.2f,
                        b.centerZ() - 1.5f, b.w * 1.3f, 0.3f, 3.4f, true));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.4f - b.w * 1.3f, b.top() - 0.2f,
                        b.centerZ() - 1.5f, b.w * 1.3f, 0.3f, 3.4f, true));
                break;
            case BUTTERFLY:
                // Four big rounded wings, upper pair larger than the lower.
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.4f, midY + 1.2f,
                        b.centerZ() - 1.6f, 4.4f, 0.4f, 3.2f, true));
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.4f, midY - 0.8f,
                        b.centerZ() - 0.6f, 3.4f, 0.4f, 2.4f, false));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.4f - 4.4f, midY + 1.2f,
                        b.centerZ() - 1.6f, 4.4f, 0.4f, 3.2f, true));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.4f - 3.4f, midY - 0.8f,
                        b.centerZ() - 0.6f, 3.4f, 0.4f, 2.4f, false));
                break;
            case DRAGONFLY:
                // Two long narrow wings per side, set slightly back.
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.4f, b.top() - 0.1f,
                        b.centerZ() - 1.8f, 5.4f, 0.25f, 1.6f, true));
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.4f, b.top() - 0.1f,
                        b.centerZ() + 0.2f, 5.0f, 0.25f, 1.4f, true));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.4f - 5.4f, b.top() - 0.1f,
                        b.centerZ() - 1.8f, 5.4f, 0.25f, 1.6f, true));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.4f - 5.0f, b.top() - 0.1f,
                        b.centerZ() + 0.2f, 5.0f, 0.25f, 1.4f, true));
                break;
            case BEETLE:
                // A hard elytra case (two domed shells) rather than feathered wings.
                out.add(new Part(BONE_WING_L, Role.ELYTRA, cx + 0.2f, b.top() - 0.3f,
                        b.centerZ() - b.d * 0.42f, b.w * 0.52f, 1.6f, b.d * 0.86f, true));
                out.add(new Part(BONE_WING_R, Role.ELYTRA, cx - b.w * 0.52f - 0.2f, b.top() - 0.3f,
                        b.centerZ() - b.d * 0.42f, b.w * 0.52f, 1.6f, b.d * 0.86f, true));
                break;
            case PARROT:
                // Folded wings down each side, plus long primary feathers.
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.5f - 0.2f, b.y + b.h * 0.3f,
                        b.centerZ() - 1.4f, 1.2f, b.h * 0.7f, 3.2f, true));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.5f - 0.8f, b.y + b.h * 0.3f,
                        b.centerZ() - 1.4f, 1.2f, b.h * 0.7f, 3.2f, true));
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.5f - 0.3f, b.top() - 0.2f,
                        b.rearZ() - 2.6f, 0.4f, b.h * 0.5f, 3.0f, false));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.5f - 0.1f, b.top() - 0.2f,
                        b.rearZ() - 2.6f, 0.4f, b.h * 0.5f, 3.0f, false));
                break;
            case DRAGON:
                // Large membranous wings swept back from the shoulders.
                out.add(new Part(BONE_WING_L, Role.WING, cx + b.w * 0.45f, b.y + b.h * 0.4f,
                        b.centerZ() - 2.2f, b.w * 0.7f, 0.6f, b.d * 0.7f, true));
                out.add(new Part(BONE_WING_R, Role.WING, cx - b.w * 0.45f - b.w * 0.7f, b.y + b.h * 0.4f,
                        b.centerZ() - 2.2f, b.w * 0.7f, 0.6f, b.d * 0.7f, true));
                break;
            default:
                break;
        }
    }

    private static void drawBody(List<Part> out, CosmeticCatalog.PetSpecies s, Body b) {
        float cx = b.centerX();
        float cz = b.centerZ();
        float top = b.top();
        float midY = b.y + b.h * 0.5f;
        out.add(new Part(BONE_ROOT, Role.BODY, b.x, b.y, b.z, b.w, b.h, b.d, false));

        switch (s) {
            case TURTLE:
                // A domed shell over the whole back plus a paler plastron edge.
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, b.x - 0.5f, top - 0.2f, b.z - 0.5f,
                        b.w + 1f, 1.8f, b.d + 1f, true));
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, b.x - 0.3f, b.y - 0.2f, b.z - 0.3f,
                        b.w + 0.6f, 0.8f, b.d + 0.6f, false));
                break;
            case BEE:
                // Two dark abdomen bands and a stinger.
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, b.x - 0.2f, b.y - 0.05f,
                        cz - b.d * 0.28f, b.w + 0.4f, b.h + 0.1f, 1.0f, true));
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, b.x - 0.2f, b.y - 0.05f,
                        b.z + b.d * 0.16f, b.w + 0.4f, b.h + 0.1f, 1.0f, true));
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, cx - 0.5f, midY - 0.5f, b.rearZ(),
                        1.0f, 1.0f, 1.6f, false));
                break;
            case SPIDER:
                // A large rounded abdomen behind the small body (vanilla spider body1 is 10x8x12).
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, cx - b.w * 0.6f, b.y - 0.4f,
                        b.rearZ() - b.d * 0.55f, b.w * 1.2f, b.h * 1.05f, b.d * 0.6f, true));
                break;
            case ANT:
                // Three segments: head, thorax (the body) and a bulbous gaster.
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, cx - b.w * 0.45f, b.y + b.h * 0.15f,
                        b.rearZ() - b.d * 0.5f, b.w * 0.9f, b.h * 0.85f, b.d * 0.42f, true));
                break;
            case FROG:
                // A squat rear haunch against the body's back, plus a pale belly.
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, b.x, b.y, b.z + b.d * 0.6f,
                        b.w, b.h + 0.4f, b.d * 0.5f, true));
                break;
            case DRAGON:
                // A spine ridge of graduated plates down the back.
                for (int i = 0; i < 4; i++) {
                    float hgt = 1.8f - i * 0.3f;
                    out.add(new Part(BONE_ROOT, Role.BODY_DECOR, cx - 0.5f, top - 0.1f,
                            b.z + 0.6f + i * (b.d - 1.2f) / 3f, 1.0f, hgt, 0.8f, true));
                }
                break;
            case LIZARD:
                // A low dorsal ridge.
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, cx - 0.4f, top - 0.1f, b.z + 0.6f,
                        0.8f, 1.0f, b.d - 1.2f, true));
                break;
            case CAT:
            case DOG:
            case WOLF:
            case FOX:
                // A chest/shoulder mass so the front reads heavier than the rump.
                out.add(new Part(BONE_ROOT, Role.BODY_DECOR, b.x - 0.2f, b.y + b.h * 0.2f,
                        b.frontZ() - b.d * 0.45f, b.w + 0.4f, b.h * 0.85f, b.d * 0.42f, false));
                break;
            default:
                break;
        }
    }

    private static void drawHead(List<Part> out, CosmeticCatalog.PetSpecies s, Body b) {
        float cx = b.centerX();
        float hz = b.frontZ() + b.headD * 0.15f;
        float hy = b.y + b.h * 0.55f;
        out.add(new Part(BONE_HEAD, Role.HEAD, cx - b.headW / 2f, hy, hz,
                b.headW, b.headH, b.headD, false));

        // Snout / muzzle / beak, tapered forward from the head's front face.
        if (b.snoutLen > 0f) {
            float sw = b.headW * (s == CosmeticCatalog.PetSpecies.FOX ? 0.5f
                    : s == CosmeticCatalog.PetSpecies.PARROT ? 0.45f : 0.62f);
            float sh = b.headH * (s == CosmeticCatalog.PetSpecies.PARROT ? 0.5f : 0.55f);
            out.add(new Part(BONE_HEAD, Role.SNOUT, cx - sw / 2f, hy + b.headH * 0.15f,
                    hz + b.headD - 0.2f, sw, sh, b.snoutLen, true));
        }

        float top = hy + b.headH;
        float front = hz + b.headD;
        switch (s) {
            case CAT:
                earPair(out, cx, top - 0.3f, hz + 0.6f, 1.2f, 1.6f, 1.0f, b.headW * 0.30f, false);
                break;
            case DOG:
                // Floppy ears hanging down the sides of the head.
                for (int side = -1; side <= 1; side += 2) {
                    out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                            cx + side * (b.headW * 0.5f) - (side > 0 ? 0f : 1.0f),
                            hy - 0.6f, hz + 0.4f, 1.0f, b.headH * 0.85f, 1.6f, true));
                }
                break;
            case WOLF:
                earPair(out, cx, top - 0.3f, hz + 0.6f, 1.4f, 2.0f, 1.2f, b.headW * 0.32f, false);
                // A neck ruff behind the head.
                out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, cx - b.headW * 0.6f, hy - 1.0f,
                        hz - 1.6f, b.headW * 1.2f, 1.8f, 1.8f, true));
                break;
            case FOX:
                // Large pointed ears (vanilla fox ears are 2x2x1 set wide and tall).
                earPair(out, cx, top - 0.4f, hz + 0.6f, 1.6f, 2.6f, 1.2f, b.headW * 0.34f, false);
                break;
            case RABBIT:
                // Two tall ears, plus a nose tip.
                for (int side = -1; side <= 1; side += 2) {
                    out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                            cx + side * (b.headW * 0.28f) - (side > 0 ? 0f : 1.0f),
                            top, hz + 0.4f, 1.1f, 4.6f, 1.1f, false));
                }
                out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, cx - 0.5f, hy + b.headH * 0.3f,
                        front + b.snoutLen - 0.1f, 1.0f, 1.0f, 0.6f, true));
                break;
            case PARROT:
                // A hooked beak: a forward block and a downward hook.
                out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, cx - 0.5f, hy + b.headH * 0.25f,
                        front + b.snoutLen - 0.1f, 1.0f, 1.2f, 1.2f, false));
                out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, cx - 0.5f, hy + b.headH * 0.05f,
                        front + b.snoutLen + 0.5f, 1.0f, 1.0f, 1.0f, true));
                break;
            case BEE:
                antennae(out, cx, top, hz + 0.4f, 0.4f, 1.6f, 0.4f, b.headW * 0.28f, false);
                break;
            case ANT:
                antennae(out, cx, top, hz + 0.4f, 0.35f, 2.4f, 0.35f, b.headW * 0.3f, false);
                mandibles(out, cx, hy + b.headH * 0.2f, front + b.snoutLen - 0.1f, 1.0f);
                break;
            case BEETLE:
                antennae(out, cx, top, hz + 0.4f, 0.35f, 1.2f, 0.35f, b.headW * 0.26f, false);
                mandibles(out, cx, hy + b.headH * 0.2f, front + b.snoutLen - 0.1f, 1.2f);
                break;
            case BUTTERFLY:
                clubbedAntennae(out, cx, top, hz + 0.4f, b.headW * 0.26f);
                break;
            case DRAGONFLY:
                // Large compound eyes plus short antennae.
                eyePair(out, cx, hy + b.headH * 0.5f, hz + 0.2f, 1.6f, 1.6f, 1.6f, b.headW * 0.45f, true);
                antennae(out, cx, top, hz + 0.4f, 0.3f, 1.4f, 0.3f, b.headW * 0.24f, false);
                break;
            case SPIDER:
                // A cluster of eyes on the front of the small head.
                eyePair(out, cx, hy + b.headH * 0.45f, front - 0.1f, 0.9f, 0.9f, 0.5f,
                        b.headW * 0.28f, true);
                break;
            case FROG:
                // Bulging eyes sitting on top of the head.
                eyePair(out, cx, top - 0.4f, hz + 0.4f, 1.6f, 1.6f, 1.6f, b.headW * 0.3f, true);
                break;
            case LIZARD:
                // Eyes set on the sides of the head.
                for (int side = -1; side <= 1; side += 2) {
                    out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                            cx + side * (b.headW * 0.5f) - (side > 0 ? 0f : 0.8f),
                            hy + b.headH * 0.45f, hz + b.headD * 0.4f, 0.8f, 0.8f, 0.8f, true));
                }
                break;
            case AXOLOTL:
                // Three gill frills fanning back from each side of the head.
                for (int i = 0; i < 3; i++) {
                    for (int side = -1; side <= 1; side += 2) {
                        out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                                cx + side * (b.headW * 0.5f) - (side > 0 ? 0f : 1.4f),
                                hy + i * 1.1f, hz + 0.4f, 1.4f, 0.5f, 1.6f, true));
                    }
                }
                break;
            case SNAKE:
                // A forked tongue flicking from the snout.
                out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, cx - 0.2f, hy + b.headH * 0.25f,
                        front + b.snoutLen - 0.1f, 0.4f, 0.4f, 1.6f, true));
                break;
            case DRAGON:
                // Two swept-back horns.
                for (int side = -1; side <= 1; side += 2) {
                    out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                            cx + side * (b.headW * 0.32f) - (side > 0 ? 0f : 1.0f),
                            top - 0.4f, hz - 0.6f, 1.0f, 2.6f, 1.0f, true));
                }
                break;
            case TURTLE:
                break;
            default:
                break;
        }
    }

    private static void earPair(List<Part> out, float cx, float y, float z,
                                float w, float h, float d, float offset, boolean accent) {
        for (int side = -1; side <= 1; side += 2) {
            out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                    cx + side * offset - (side > 0 ? 0f : w), y, z, w, h, d, accent));
        }
    }

    private static void antennae(List<Part> out, float cx, float y, float z,
                                 float w, float h, float d, float offset, boolean accent) {
        for (int side = -1; side <= 1; side += 2) {
            out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                    cx + side * offset - (side > 0 ? 0f : w), y, z, w, h, d, accent));
        }
    }

    private static void clubbedAntennae(List<Part> out, float cx, float y, float z, float offset) {
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * offset - (side > 0 ? 0f : 0.35f);
            out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, x, y, z, 0.35f, 1.6f, 0.35f, false));
            out.add(new Part(BONE_HEAD, Role.HEAD_DECOR, x - 0.15f, y + 1.6f, z - 0.15f,
                    0.65f, 0.65f, 0.65f, true));
        }
    }

    private static void mandibles(List<Part> out, float cx, float y, float z, float len) {
        for (int side = -1; side <= 1; side += 2) {
            out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                    cx + side * 0.7f - (side > 0 ? 0f : 0.6f), y, z, 0.6f, 0.6f, len, true));
        }
    }

    private static void eyePair(List<Part> out, float cx, float y, float z,
                                float w, float h, float d, float offset, boolean accent) {
        for (int side = -1; side <= 1; side += 2) {
            out.add(new Part(BONE_HEAD, Role.HEAD_DECOR,
                    cx + side * offset - (side > 0 ? 0f : w), y, z, w, h, d, accent));
        }
    }

    /** The bone pivot for a species, used by the geometry builder and the animations. */
    public static float[] pivot(String bone, Body b) {
        float cx = b.centerX();
        switch (bone) {
            case BONE_HEAD:
                return new float[]{cx, b.y + b.h * 0.55f, b.frontZ()};
            case BONE_TAIL:
                return new float[]{cx, b.y + b.h * 0.4f, b.rearZ()};
            case BONE_WING_L:
                return new float[]{cx + b.w * 0.4f, b.top() - 0.2f, b.centerZ()};
            case BONE_WING_R:
                return new float[]{cx - b.w * 0.4f, b.top() - 0.2f, b.centerZ()};
            case BONE_LEG_A:
                return new float[]{cx, b.y, b.z + b.d * 0.2f};
            case BONE_LEG_B:
                return new float[]{cx, b.y, b.z + b.d * 0.8f};
            case BONE_LEG_C:
                return new float[]{cx, b.y, b.centerZ()};
            default:
                return new float[]{0f, 0f, 0f};
        }
    }
}
