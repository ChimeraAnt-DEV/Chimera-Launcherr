package org.chimeramc.client.core.cosmetics;

/**
 * Blocky geometry for the equipped pet, as pure JSON text, parented to the player.
 *
 * <p>This is the in-game half of the pet family. The preview draws a pet beside the character with
 * {@code CapePreviewView}, but a preview is not what a player sees in a world, so the equipped pet
 * is drawn on the player itself: a small companion standing just in front and to the right of the
 * player, driven by the same client-entity render controller route as the cape and hats.
 *
 * <p><b>No entity is spawned.</b> A real pet would need {@code /summon}, which only works in
 * single-player or on a LAN world the player hosts; on a server it is a cheat command that would
 * flag or ban the account. Rendering the pet as geometry on the player's own client entity is
 * client-side only — other players do not see it, exactly like the cape — and cannot trip a
 * server-side check. {@code cosmetics_scope_note} says so.
 *
 * <p><b>Body per species, tinted per palette.</b> Each {@link CosmeticCatalog.PetSpecies} builds
 * its own body, head, legs and the parts that make it read as that animal (a bee's striped
 * abdomen, a bird's wings, a snake's segmented tail, a turtle's shell, horns for a dragon, a
 * rabbit's tall ears). The palette then tints the mesh, and the species' scale trait resizes it.
 * A bee is genuinely small and a dragon genuinely long; a "Shadow" variant is the same shape
 * darker, which is what the catalogue's trait names mean.
 *
 * <p>Pure string building, so a unit test can parse the output and assert each species produces a
 * non-empty, distinct mesh without Android or a device.
 */
public final class PetGeometry {

    /** Identifier the player client entity binds the pet geometry to. */
    public static final String GEOMETRY_ID = "geometry.chimera_pet";

    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    /** UV origins for the two flat colour regions {@link FlatColorAtlas} paints. */
    static final int UV_BASE_X = 0;
    static final int UV_BASE_Y = 0;
    static final int UV_ACCENT_X = 0;
    static final int UV_ACCENT_Y = 32;

    private PetGeometry() {
    }

    /**
     * The geometry JSON for a pet, or {@code null} for {@code null}.
     *
     * <p>The pet stands in front of the player and to their right, on the ground: the player's
     * feet are at y=0, so the pet is built up from y=0 and placed at z ~ +10 (in front) and x ~ +7
     * (to the right) inside the player's own model space.
     */
    public static String geometryJson(CosmeticCatalog.Pet pet) {
        if (pet == null) return null;

        CosmeticCatalog.PetSpecies species = pet.species;
        float s = pet.scale <= 0f ? 1f : pet.scale;

        // The pet is authored small and scaled into place. A blocky pet reads best at roughly a
        // third of the player's height, so these are model pixels in the player's 1/16-block unit.
        float baseX = 6f * s;
        float baseZ = 9f * s;

        // Per-species body, mirroring CapePreviewView's preview dimensions so the preview and the
        // in-game pet are recognisably the same animal.
        float bodyW, bodyH, bodyD, legLen;
        switch (species) {
            case BEE:
            case BUTTERFLY:
            case DRAGONFLY:
                bodyW = 4f; bodyH = 3f; bodyD = 6f; legLen = 1.5f;
                break;
            case SPIDER:
            case ANT:
                bodyW = 5f; bodyH = 2.5f; bodyD = 7f; legLen = 2.5f;
                break;
            case FROG:
            case AXOLOTL:
            case LIZARD:
                bodyW = 5f; bodyH = 3f; bodyD = 7f; legLen = 2f;
                break;
            case SNAKE:
                bodyW = 3.5f; bodyH = 3f; bodyD = 10f; legLen = 0.5f;
                break;
            case DRAGON:
                bodyW = 7f; bodyH = 5f; bodyD = 11f; legLen = 3.5f;
                break;
            case PARROT:
                bodyW = 4f; bodyH = 5f; bodyD = 5f; legLen = 2.5f;
                break;
            case TURTLE:
                bodyW = 7f; bodyH = 3.5f; bodyD = 9f; legLen = 1.5f;
                break;
            case RABBIT:
                bodyW = 4f; bodyH = 4f; bodyD = 6f; legLen = 2f;
                break;
            default:
                bodyW = 5f; bodyH = 4f; bodyD = 8f; legLen = 3f;
                break;
        }

        float bodyY = legLen;
        float headSize = Math.min(bodyH, 5f) + 1f;
        float headZ = baseZ + bodyD / 2f + headSize * 0.3f;
        float headY = bodyY + bodyH + headSize * 0.3f;

        StringBuilder cubes = new StringBuilder();

        // Legs (behind the body). Crawlers get six, the rest four. The along-axis is measured from
        // the body's centre, not its front face, or every leg sits a body-length ahead of the
        // animal it is supposed to hold up.
        if (species != CosmeticCatalog.PetSpecies.SNAKE) {
            int legs = (species == CosmeticCatalog.PetSpecies.SPIDER
                    || species == CosmeticCatalog.PetSpecies.ANT) ? 6 : 4;
            for (int i = 0; i < legs; i++) {
                float side = (i % 2 == 0) ? -1f : 1f;
                float along = (i / 2 - 0.5f) * bodyD * 0.5f;
                cube(cubes, baseX + side * bodyW * 0.42f, 0f,
                        baseZ + bodyD / 2f + along, 1.6f, legLen, 1.6f, false);
            }
        } else {
            // A snake's tail: tapered segments trailing behind the body, the first sitting
            // against the body's rear face so the tail reads as one animal rather than a line of
            // blocks floating in space.
            float tailStartZ = baseZ - 1.6f;
            for (int seg = 1; seg <= 4; seg++) {
                float size = 2.4f - seg * 0.3f;
                cube(cubes, baseX + bodyW / 2f - size / 2f, bodyY + bodyH / 2f,
                        tailStartZ - (seg - 1) * 1.6f, size, size, 1.6f, seg % 2 == 0);
            }
        }

        // Body.
        cube(cubes, baseX, bodyY, baseZ, bodyW, bodyH, bodyD, false);

        // Head.
        cube(cubes, baseX, headY, headZ, headSize, headSize, headSize, false);

        // Species-defining parts.
        drawHeadgear(cubes, species, baseX, headY, headZ, headSize);
        drawSpeciesBody(cubes, species, baseX, bodyY, bodyH, bodyW, bodyD, baseZ);

        return "{\n"
                + "  \"format_version\": \"1.12.0\",\n"
                + "  \"minecraft:geometry\": [\n"
                + "    {\n"
                + "      \"description\": {\n"
                + "        \"identifier\": \"" + GEOMETRY_ID + "\",\n"
                + "        \"texture_width\": " + TEXTURE_WIDTH + ",\n"
                + "        \"texture_height\": " + TEXTURE_HEIGHT + ",\n"
                + "        \"visible_bounds_width\": 3,\n"
                + "        \"visible_bounds_height\": 3,\n"
                + "        \"visible_bounds_offset\": [0, 1, 0]\n"
                + "      },\n"
                + "      \"bones\": [\n"
                + "        {\n"
                + "          \"name\": \"pet\",\n"
                + "          \"pivot\": [0.0, 0.0, 0.0],\n"
                + "          \"cubes\": [" + cubes + "]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    /** Ears, antennae, horns and the like, so a species reads from the head shape alone. */
    private static void drawHeadgear(StringBuilder out, CosmeticCatalog.PetSpecies species,
                                     float x, float headY, float headZ, float headSize) {
        float top = headY + headSize;
        switch (species) {
            case RABBIT:
                // Two tall ears.
                cube(out, x - 1.2f, top, headZ, 0.9f, 3.2f, 0.9f, false);
                cube(out, x + 0.3f, top, headZ, 0.9f, 3.2f, 0.9f, false);
                break;
            case CAT:
                // Small upright pointed ears.
                cube(out, x - 2f, top - 0.2f, headZ - 0.4f, 1f, 1.4f, 1f, false);
                cube(out, x + 1f, top - 0.2f, headZ - 0.4f, 1f, 1.4f, 1f, false);
                break;
            case DOG:
                // Floppy ears hanging down the sides of the head.
                cube(out, x - 2.4f, headY, headZ - 0.4f, 1f, 2.6f, 1.4f, true);
                cube(out, x + 1.4f, headY, headZ - 0.4f, 1f, 2.6f, 1.4f, true);
                break;
            case WOLF:
                // Upright ears plus a neck ruff.
                cube(out, x - 2.2f, top - 0.2f, headZ - 0.4f, 1.2f, 1.8f, 1.2f, false);
                cube(out, x + 1f, top - 0.2f, headZ - 0.4f, 1.2f, 1.8f, 1.2f, false);
                cube(out, x - 1f, headY - 1.4f, headZ - 1.6f, 2f, 1.4f, 2f, true);
                break;
            case FOX:
                // Large pointed ears.
                cube(out, x - 2.4f, top - 0.4f, headZ - 0.4f, 1.4f, 2.4f, 1.2f, false);
                cube(out, x + 1f, top - 0.4f, headZ - 0.4f, 1.4f, 2.4f, 1.2f, false);
                break;
            case PARROT:
                cube(out, x - 0.4f, headY, headZ + headSize * 0.5f, 0.8f, 0.8f, 1.6f, true);
                break;
            case BEE:
                cube(out, x - 1.2f, top, headZ, 0.4f, 1.4f, 0.4f, true);
                cube(out, x + 0.8f, top, headZ, 0.4f, 1.4f, 0.4f, true);
                break;
            case ANT:
                // Longer, thinner antennae than a bee's.
                cube(out, x - 1.4f, top, headZ, 0.3f, 2f, 0.3f, true);
                cube(out, x + 1.1f, top, headZ, 0.3f, 2f, 0.3f, true);
                break;
            case BEETLE:
                // Short antennae plus a pair of small mandible horns.
                cube(out, x - 1f, top, headZ, 0.3f, 1f, 0.3f, true);
                cube(out, x + 0.7f, top, headZ, 0.3f, 1f, 0.3f, true);
                cube(out, x - 1.4f, headY, headZ + headSize * 0.4f, 0.6f, 0.6f, 1.4f, true);
                cube(out, x + 0.8f, headY, headZ + headSize * 0.4f, 0.6f, 0.6f, 1.4f, true);
                break;
            case DRAGON:
                cube(out, x - 1.6f, top, headZ - 0.6f, 1f, 2.4f, 1f, true);
                cube(out, x + 0.6f, top, headZ - 0.6f, 1f, 2.4f, 1f, true);
                break;
            case BUTTERFLY:
                // Clubbed antennae: a thin stalk with a knob at the tip.
                cube(out, x - 1f, top, headZ, 0.3f, 1.4f, 0.3f, true);
                cube(out, x - 1.15f, top + 1.4f, headZ - 0.15f, 0.6f, 0.6f, 0.6f, true);
                cube(out, x + 0.7f, top, headZ, 0.3f, 1.4f, 0.3f, true);
                cube(out, x + 0.55f, top + 1.4f, headZ - 0.15f, 0.6f, 0.6f, 0.6f, true);
                break;
            case DRAGONFLY:
                // Straight, shorter antennae.
                cube(out, x - 0.9f, top, headZ, 0.3f, 1.2f, 0.3f, true);
                cube(out, x + 0.6f, top, headZ, 0.3f, 1.2f, 0.3f, true);
                break;
            case FROG:
                // Bulging eyes sitting on top of the head.
                cube(out, x - 1.4f, top - 0.4f, headZ, 1.2f, 1.2f, 1.2f, true);
                cube(out, x + 0.2f, top - 0.4f, headZ, 1.2f, 1.2f, 1.2f, true);
                break;
            case LIZARD:
                // Eyes set on the sides of the head.
                cube(out, x - 2.2f, headY + headSize * 0.4f, headZ, 0.8f, 0.8f, 0.8f, true);
                cube(out, x + 1.4f, headY + headSize * 0.4f, headZ, 0.8f, 0.8f, 0.8f, true);
                break;
            case AXOLOTL:
                // Gill frills, three per side, fanning back from the head.
                for (int i = 0; i < 3; i++) {
                    cube(out, x - 2.2f, headY + i * 0.8f, headZ - 0.4f, 0.5f, 0.5f, 1.6f, true);
                    cube(out, x + 1.7f, headY + i * 0.8f, headZ - 0.4f, 0.5f, 0.5f, 1.6f, true);
                }
                break;
            default:
                break;
        }
    }

    /** Shells, wings, stripes and tails: the parts that distinguish body shapes. */
    private static void drawSpeciesBody(StringBuilder out, CosmeticCatalog.PetSpecies species,
                                        float x, float bodyY, float bodyH, float bodyW,
                                        float bodyD, float baseZ) {
        // Every part is anchored to the body it belongs to. The body occupies x..x+bodyW and
        // baseZ..baseZ+bodyD, so a part placed at absolute z=0 (the old code) floated detached in
        // empty space several units behind the animal and never read as its shell, wing or tail.
        float cx = x + bodyW / 2f;
        float cz = baseZ + bodyD / 2f;
        float rearZ = baseZ;
        float top = bodyY + bodyH;
        float midY = bodyY + bodyH * 0.5f;
        switch (species) {
            case TURTLE:
                // A domed shell half-sunk into the back.
                cubeC(out, cx, top + 0.3f, cz, bodyW + 1f, 1.4f, bodyD + 1f, true);
                break;
            case PARROT:
                // Wings folded along the sides, plus a tail streaming behind.
                cubeC(out, cx - (bodyW / 2f + 1.5f), midY, cz, 3f, 0.6f, 4f, true);
                cubeC(out, cx + (bodyW / 2f + 1.5f), midY, cz, 3f, 0.6f, 4f, true);
                cubeC(out, cx, bodyY + bodyH * 0.6f, rearZ - 1f, 0.8f, 3f, 2f, false);
                break;
            case BUTTERFLY:
                // Large, rounded wings out to each side.
                cubeC(out, cx - (bodyW / 2f + 2f), midY, cz, 4f, 0.5f, 5f, true);
                cubeC(out, cx + (bodyW / 2f + 2f), midY, cz, 4f, 0.5f, 5f, true);
                break;
            case DRAGONFLY:
                // Long, narrow wings set a little back.
                cubeC(out, cx - (bodyW / 2f + 2.5f), midY, cz + 1f, 5f, 0.3f, 2f, true);
                cubeC(out, cx + (bodyW / 2f + 2.5f), midY, cz + 1f, 5f, 0.3f, 2f, true);
                break;
            case BEE:
                // Two dark bands wrapping the abdomen.
                cubeC(out, cx, midY, cz - bodyD * 0.25f, bodyW + 0.6f, 0.6f, bodyD * 0.5f, true);
                cubeC(out, cx, midY, rearZ + bodyD * 0.15f, bodyW + 0.4f, 0.6f, 1f, true);
                break;
            case SPIDER:
                // A rounded abdomen on the back half.
                cubeC(out, cx, top + 0.2f, rearZ + bodyD * 0.35f, bodyW, 1.6f, 2.6f, true);
                break;
            case BEETLE:
                // A hard elytra shell over the whole back.
                cubeC(out, cx, top + 0.3f, cz - bodyD * 0.05f, bodyW + 0.6f, 1.4f,
                        bodyD * 0.9f, true);
                break;
            case DRAGON:
                // A spine ridge, plus two wings swept back from the shoulders.
                cubeC(out, cx, top + 0.6f, cz, 1f, 2f, bodyD * 0.8f, true);
                cubeC(out, cx - (bodyW / 2f + 1.75f), midY, cz, 3.5f, 0.6f, 5f, true);
                cubeC(out, cx + (bodyW / 2f + 1.75f), midY, cz, 3.5f, 0.6f, 5f, true);
                break;
            case CAT:
                // A long, thin tail with a curl at the tip.
                cubeC(out, cx, midY, rearZ - 1.5f, 0.9f, 0.9f, 3f, false);
                cubeC(out, cx, bodyY + bodyH * 0.9f, rearZ - 3.4f, 0.9f, 0.9f, 1.4f, false);
                break;
            case DOG:
                // A short stub tail.
                cubeC(out, cx, bodyY + bodyH * 0.7f, rearZ - 0.8f, 1.4f, 1.4f, 1.6f, false);
                break;
            case WOLF:
                // A medium bushy tail.
                cubeC(out, cx, bodyY + bodyH * 0.55f, rearZ - 1.5f, 1.8f, 1.8f, 3f, false);
                break;
            case FOX:
                // A large bushy tail with an accent tip.
                cubeC(out, cx, bodyY + bodyH * 0.5f, rearZ - 1.7f, 2.2f, 2.2f, 3.4f, false);
                cubeC(out, cx, bodyY + bodyH * 0.5f, rearZ - 4.0f, 2.2f, 2.2f, 1.2f, true);
                break;
            case RABBIT:
                // A round puff tail.
                cubeC(out, cx, bodyY + bodyH * 0.6f, rearZ - 0.7f, 1.4f, 1.4f, 1.4f, true);
                break;
            case LIZARD:
                // A thin, long tail.
                cubeC(out, cx, bodyY + bodyH * 0.4f, rearZ - 1.7f, 0.9f, 0.9f, 3.4f, false);
                break;
            case FROG:
                // A squat rear haunch sitting on the ground against the body's rear.
                cubeC(out, cx, (bodyH + 0.6f) / 2f, rearZ + 1f, bodyW + 0.4f, bodyH + 0.6f,
                        2f, false);
                break;
            case AXOLOTL:
                // A finned tail.
                cubeC(out, cx, bodyY + bodyH * 0.6f, rearZ - 1.7f, 1.2f, 2f, 3.4f, true);
                break;
            default:
                break;
        }
    }

    /**
     * A geometry that resolves but draws nothing, written when no pet is equipped so the player
     * entity's reference to the pet geometry always resolves. See {@link #geometryJson}.
     */
    public static String emptyGeometryJson() {
        return "{\n"
                + "  \"format_version\": \"1.12.0\",\n"
                + "  \"minecraft:geometry\": [\n"
                + "    {\n"
                + "      \"description\": {\n"
                + "        \"identifier\": \"" + GEOMETRY_ID + "\",\n"
                + "        \"texture_width\": " + TEXTURE_WIDTH + ",\n"
                + "        \"texture_height\": " + TEXTURE_HEIGHT + "\n"
                + "      },\n"
                + "      \"bones\": [\n"
                + "        {\n"
                + "          \"name\": \"pet\",\n"
                + "          \"pivot\": [0.0, 0.0, 0.0],\n"
                + "          \"cubes\": []\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    private static void cube(StringBuilder out, float x, float y, float z,
                             float sx, float sy, float sz, boolean accent) {
        if (out.length() > 0) out.append(",");
        int uvx = accent ? UV_ACCENT_X : UV_BASE_X;
        int uvy = accent ? UV_ACCENT_Y : UV_BASE_Y;
        out.append("{\"origin\": [").append(f(x)).append(", ").append(f(y)).append(", ")
                .append(f(z)).append("], \"size\": [").append(f(sx)).append(", ")
                .append(f(sy)).append(", ").append(f(sz)).append("], \"uv\": [")
                .append(uvx).append(", ").append(uvy).append("]}");
    }

    /**
     * A cube placed by its centre, for the species parts. Bedrock's {@code origin} is the minimum
     * corner, so a part authored from the body's centre needs the half-size subtracted; doing that
     * by hand at every call site is how a shell ends up half a body off the animal.
     */
    private static void cubeC(StringBuilder out, float cx, float cy, float cz,
                              float sx, float sy, float sz, boolean accent) {
        cube(out, cx - sx / 2f, cy - sy / 2f, cz - sz / 2f, sx, sy, sz, accent);
    }

    static String f(float value) {
        if (value == Math.round(value)) return Integer.toString(Math.round(value));
        String text = String.format(java.util.Locale.US, "%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
