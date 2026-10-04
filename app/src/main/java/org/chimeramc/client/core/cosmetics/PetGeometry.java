package org.chimeramc.client.core.cosmetics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p><b>The mesh comes from {@link PetModel}, not from a switch in this file.</b> The body plan
 * (proportions taken from the vanilla mob models) lives in one place and is shared with the
 * preview, so the two cannot drift. This class only turns that plan into Bedrock JSON, grouping the
 * parts by bone so the animations in {@link PetAnimations} can move the head, tail, wings and legs
 * independently instead of rocking one rigid block.
 *
 * <p>Pure string building, so a unit test can parse the output and assert each species produces a
 * non-empty, distinct, multi-bone mesh without Android or a device.
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
     * feet are at y=0, so the pet is built up from y=0 and placed at z ~ +9 (in front) and x ~ +6
     * (to the right) inside the player's own model space.
     */
    public static String geometryJson(CosmeticCatalog.Pet pet) {
        if (pet == null) return null;

        PetModel.Body body = PetModel.body(pet.species, pet.scale);
        List<PetModel.Part> parts = PetModel.parts(pet.species, pet.scale);

        // Group by bone in a stable order, so the emitted JSON is deterministic and a test can
        // diff two species.
        Map<String, StringBuilder> byBone = new LinkedHashMap<>();
        byBone.put(PetModel.BONE_ROOT, new StringBuilder());
        byBone.put(PetModel.BONE_HEAD, new StringBuilder());
        byBone.put(PetModel.BONE_TAIL, new StringBuilder());
        byBone.put(PetModel.BONE_WING_L, new StringBuilder());
        byBone.put(PetModel.BONE_WING_R, new StringBuilder());
        byBone.put(PetModel.BONE_LEG_A, new StringBuilder());
        byBone.put(PetModel.BONE_LEG_B, new StringBuilder());
        byBone.put(PetModel.BONE_LEG_C, new StringBuilder());
        for (PetModel.Part p : parts) {
            StringBuilder sb = byBone.get(p.bone);
            if (sb == null) sb = byBone.get(PetModel.BONE_ROOT);
            cube(sb, p.x, p.y, p.z, p.sx, p.sy, p.sz, p.accent);
        }

        StringBuilder bones = new StringBuilder();
        boolean[] firstBone = {true};
        appendBone(bones, firstBone, PetModel.BONE_ROOT, null, body, byBone.get(PetModel.BONE_ROOT));
        appendBone(bones, firstBone, PetModel.BONE_HEAD, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_HEAD));
        appendBone(bones, firstBone, PetModel.BONE_TAIL, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_TAIL));
        appendBone(bones, firstBone, PetModel.BONE_WING_L, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_WING_L));
        appendBone(bones, firstBone, PetModel.BONE_WING_R, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_WING_R));
        appendBone(bones, firstBone, PetModel.BONE_LEG_A, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_LEG_A));
        appendBone(bones, firstBone, PetModel.BONE_LEG_B, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_LEG_B));
        appendBone(bones, firstBone, PetModel.BONE_LEG_C, PetModel.BONE_ROOT, body, byBone.get(PetModel.BONE_LEG_C));

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
                + bones
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    private static void appendBone(StringBuilder out, boolean[] first, String name, String parent,
                                   PetModel.Body body, StringBuilder cubes) {
        if (cubes == null || cubes.length() == 0) return;
        float[] pivot = PetModel.pivot(name, body);
        if (!first[0]) out.append(",\n");
        first[0] = false;
        out.append("        {\n");
        out.append("          \"name\": \"").append(name).append("\",\n");
        if (parent != null) {
            out.append("          \"parent\": \"").append(parent).append("\",\n");
        }
        out.append("          \"pivot\": [").append(f(pivot[0])).append(", ")
                .append(f(pivot[1])).append(", ").append(f(pivot[2])).append("],\n");
        out.append("          \"cubes\": [").append(cubes).append("]\n");
        out.append("        }");
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
                + "          \"name\": \"" + PetModel.BONE_ROOT + "\",\n"
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

    static String f(float value) {
        if (value == Math.round(value)) return Integer.toString(Math.round(value));
        String text = String.format(java.util.Locale.US, "%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
