package org.chimeramc.client.core.cosmetics;

/**
 * Blocky geometry for the worn head/body accessories, as pure JSON text.
 *
 * <p>This is the in-game half of the accessory family. The preview draws an accessory with
 * {@code CapePreviewView}, but a preview is not what a player sees in a world, so each drawing
 * kind also has a real {@code minecraft:geometry} here, emitted into the same resource pack as
 * the cape and bound to the player by a render controller.
 *
 * <p><b>Why a separate geometry rather than the player's own bones.</b> A render controller
 * renders one geometry per controller, and the player's vanilla geometry is a compiled
 * {@code geometry.humanoid.custom}. Injecting new cubes into it is not something a resource pack
 * can do, so each accessory is its own small geometry drawn in entity space at the head's real
 * coordinates (the vanilla head box spans x -4..4, y 24..32, z -4..4). It follows the player's
 * position and facing; it does not inherit a head tilt, which for a hat is not visible anyway.
 *
 * <p><b>Varied meshes, not one mesh recoloured.</b> Each {@link CosmeticCatalog.AccessoryKind}
 * builds a different set of boxes — a cap is a crown plus a brim, a crown is a band plus five
 * points, a beanie has a pom, horns taper, a halo is a dense ring of thin slats. The palette then
 * tints those meshes, so "Amethyst Crown" and "Ember Crown" are the same shape in different
 * colours, while "Amethyst Crown" and "Amethyst Cap" are genuinely different geometry. That is
 * what the catalogue's 100+ accessory entries mean, and {@code accessory_style_note} says so.
 *
 * <p>Pure string building, so a unit test can parse the output and assert each kind produces a
 * non-empty, distinct mesh without Android or a device.
 */
public final class AccessoryGeometry {

    /** Identifier the player client entity binds the accessory geometry to. */
    public static final String GEOMETRY_ID = "geometry.chimera_hat";

    /**
     * Halo slat count. A low count reads as a faceted polygon; 24 overlaps at this radius so the
     * ring profile looks continuous. Kept public so the test can assert the ring is dense enough.
     */
    public static final int HALO_SEGMENTS = 24;

    /** Halo ring radius, in entity units (the head is 8 wide, so 5 sits just clear of it). */
    public static final float HALO_RADIUS = 5f;

    /** Texture atlas the geometry's UVs address. */
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    /**
     * UV origins for the two flat colour regions {@link CosmeticTexturePainter} paints.
     *
     * <p>A box UV needs {@code 2*(width+depth)} by {@code height+depth} pixels. Both regions are
     * a full 64x32, so the largest box here (a 9x3x9 crown needs 36x12) fits with room to spare
     * and a cube can never sample across into the other region's colour.
     */
    static final int UV_BASE_X = 0;
    static final int UV_BASE_Y = 0;
    static final int UV_ACCENT_X = 0;
    static final int UV_ACCENT_Y = 32;

    private AccessoryGeometry() {
    }

    /**
     * The geometry JSON for a kind, or {@code null} for a kind that draws nothing
     * ({@link CosmeticCatalog.AccessoryKind#NONE}).
     */
    public static String geometryJson(CosmeticCatalog.AccessoryKind kind) {
        if (kind == null || kind == CosmeticCatalog.AccessoryKind.NONE) return null;

        StringBuilder cubes = new StringBuilder();
        switch (kind) {
            case CAP:
                cube(cubes, -4.5f, 32f, -4.5f, 9f, 2f, 9f, false);
                cube(cubes, -3f, 31.4f, 4f, 6f, 1f, 3f, true);
                break;
            case BEANIE:
                cube(cubes, -4.5f, 32f, -4.5f, 9f, 3f, 9f, false);
                cube(cubes, -4.6f, 31.4f, -4.6f, 9.2f, 1.2f, 9.2f, true);
                cube(cubes, -1f, 35f, -1f, 2f, 2f, 2f, false);
                break;
            case CROWN:
                cube(cubes, -4.4f, 32f, -4.4f, 8.8f, 1.6f, 8.8f, false);
                for (int i = -2; i <= 2; i++) {
                    float h = (i == 0) ? 2.6f : 1.8f;
                    cube(cubes, i * 1.7f - 0.5f, 33.6f, -4.4f, 1f, h, 1f, true);
                }
                break;
            case HORNS:
                cube(cubes, -3.4f, 32f, -0.8f, 1.6f, 4f, 1.6f, false);
                cube(cubes, 1.8f, 32f, -0.8f, 1.6f, 4f, 1.6f, false);
                cube(cubes, -3.6f, 36f, -1f, 1f, 1.6f, 1f, true);
                cube(cubes, 2.6f, 36f, -1f, 1f, 1.6f, 1f, true);
                break;
            case HALO:
                // A ring needs many segments: 8 slats read as a faceted octagon, which is exactly
                // the "blocky spaced-out circle" complaint. 24 slats at radius 5 overlap (arc
                // spacing ~1.31 vs a 1.5-wide cube) so the profile reads as a continuous circle.
                for (int i = 0; i < HALO_SEGMENTS; i++) {
                    double a = i / (double) HALO_SEGMENTS * Math.PI * 2.0;
                    float x = (float) (Math.cos(a) * HALO_RADIUS);
                    float z = (float) (Math.sin(a) * HALO_RADIUS);
                    cube(cubes, x - 0.75f, 35f, z - 0.75f, 1.5f, 0.4f, 1.5f, true);
                }
                break;
            case FLOWER:
                cube(cubes, 3.4f, 32.6f, 0.6f, 2.4f, 1.2f, 2.4f, false);
                cube(cubes, 2.6f, 31.4f, 0.6f, 1.2f, 1.2f, 1.2f, true);
                cube(cubes, 4.2f, 31.4f, 0.6f, 1.2f, 1.2f, 1.2f, true);
                cube(cubes, 3.4f, 31.4f, -0.6f, 1.2f, 1.2f, 1.2f, true);
                break;
            case EAR:
                cube(cubes, -3.4f, 32f, -1f, 2f, 3.4f, 2f, false);
                cube(cubes, 1.4f, 32f, -1f, 2f, 3.4f, 2f, false);
                cube(cubes, -3f, 32.4f, -0.4f, 1.2f, 2.2f, 0.6f, true);
                cube(cubes, 1.8f, 32.4f, -0.4f, 1.2f, 2.2f, 0.6f, true);
                break;
            case HEADPHONES:
                // Five boxes arcing over the crown, then one cup proud of each ear.
                for (int i = 0; i < 5; i++) {
                    float t = i / 4f;
                    float x = -4.6f + t * 9.2f;
                    float y = 32f + (float) Math.sin(t * Math.PI) * 1.4f;
                    cube(cubes, x - 0.6f, y, -0.6f, 1.2f, 1.2f, 1.2f, false);
                }
                cube(cubes, -6.2f, 27.6f, -2.2f, 1.6f, 4.4f, 4.4f, true);
                cube(cubes, 4.6f, 27.6f, -2.2f, 1.6f, 4.4f, 4.4f, true);
                break;
            case GLASSES:
                cube(cubes, -3.4f, 28.6f, 4.2f, 3.2f, 2f, 0.6f, false);
                cube(cubes, 0.2f, 28.6f, 4.2f, 3.2f, 2f, 0.6f, false);
                cube(cubes, -0.6f, 29.2f, 4.2f, 1.2f, 0.6f, 0.6f, true);
                break;
            case MASK:
                cube(cubes, -4f, 25.6f, 4.2f, 8f, 4.4f, 0.8f, false);
                cube(cubes, 4f, 26.6f, 2.6f, 1.4f, 1.4f, 1.4f, true);
                break;
            case SCARF:
                cube(cubes, -4.2f, 22.8f, -2.2f, 8.4f, 2f, 4.4f, false);
                cube(cubes, -4.3f, 21.6f, -2.3f, 8.6f, 0.8f, 4.6f, true);
                break;
            case BACKPACK:
                cube(cubes, -4f, 12f, -5.6f, 8f, 9f, 2.6f, false);
                cube(cubes, -3.2f, 18f, -2.4f, 1.2f, 7f, 0.8f, true);
                cube(cubes, 2f, 18f, -2.4f, 1.2f, 7f, 0.8f, true);
                break;
            case BOWTIE:
                cube(cubes, -2.6f, 21.4f, 2.4f, 2.4f, 2f, 1.2f, false);
                cube(cubes, 0.2f, 21.4f, 2.4f, 2.4f, 2f, 1.2f, false);
                cube(cubes, -0.6f, 21.4f, 2.6f, 1.2f, 1.4f, 1f, true);
                break;
            case TOPHAT:
                // A wide brim under a tall, narrow crown — the silhouette no stacked-cap shape has.
                cube(cubes, -5.5f, 32f, -5.5f, 11f, 1f, 11f, false);
                cube(cubes, -4f, 33f, -4f, 8f, 6f, 8f, false);
                cube(cubes, -4.1f, 33f, -4.1f, 8.2f, 1f, 8.2f, true);
                break;
            case WIZARD_HAT:
                // A cone approximated by graduated cubes, on a wide brim: a real curve, not a box.
                cube(cubes, -6f, 32f, -6f, 12f, 1f, 12f, false);
                cube(cubes, -4f, 33f, -4f, 8f, 2f, 8f, false);
                cube(cubes, -3f, 35f, -3f, 6f, 2f, 6f, false);
                cube(cubes, -2f, 37f, -2f, 4f, 2f, 4f, false);
                cube(cubes, -1f, 39f, -1f, 2f, 2f, 2f, false);
                cube(cubes, -0.4f, 41f, -0.4f, 0.8f, 1f, 0.8f, true);
                break;
            case TIARA:
                // A low band with a rising centre stone and two side stones, unlike the tall crown.
                cube(cubes, -4.2f, 32f, -4.2f, 8.4f, 1f, 8.4f, false);
                cube(cubes, -0.8f, 33f, 3.6f, 1.6f, 2f, 0.8f, true);
                cube(cubes, -3.4f, 33f, 3.6f, 1.2f, 1.4f, 0.8f, true);
                cube(cubes, 2.2f, 33f, 3.6f, 1.2f, 1.4f, 0.8f, true);
                break;
            case BEARD:
                // A beard that narrows in graduated steps, so it rounds under the chin.
                cube(cubes, -3.6f, 24f, 3.4f, 7.2f, 2f, 1.2f, false);
                cube(cubes, -3f, 22.4f, 3.4f, 6f, 2f, 1.2f, false);
                cube(cubes, -2f, 20.8f, 3.4f, 4f, 2f, 1.2f, false);
                cube(cubes, -1f, 19.4f, 3.4f, 2f, 1.8f, 1.2f, true);
                break;
            case WINGS:
                // Two swept slabs behind the shoulders; a static approximation of the preview's
                // animated wings, because an animated accessory would need its own controller.
                cube(cubes, -9.5f, 20f, -3.4f, 6f, 9f, 1f, false);
                cube(cubes, 3.5f, 20f, -3.4f, 6f, 9f, 1f, false);
                break;
            default:
                return null;
        }

        return "{\n"
                + "  \"format_version\": \"1.12.0\",\n"
                + "  \"minecraft:geometry\": [\n"
                + "    {\n"
                + "      \"description\": {\n"
                + "        \"identifier\": \"" + GEOMETRY_ID + "\",\n"
                + "        \"texture_width\": " + TEXTURE_WIDTH + ",\n"
                + "        \"texture_height\": " + TEXTURE_HEIGHT + ",\n"
                + "        \"visible_bounds_width\": 2,\n"
                + "        \"visible_bounds_height\": 4,\n"
                + "        \"visible_bounds_offset\": [0, 1, 0]\n"
                + "      },\n"
                + "      \"bones\": [\n"
                + "        {\n"
                + "          \"name\": \"acc\",\n"
                + "          \"pivot\": [0.0, 24.0, 0.0],\n"
                + "          \"cubes\": [" + cubes + "]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    /**
     * A geometry that resolves but draws nothing.
     *
     * <p>Written when no accessory is equipped. The player entity always names the hat geometry, so
     * the identifier must always resolve — a client entity that references a missing geometry can
     * fail to load entirely, which would take the cape down with it. An empty bone list keeps the
     * reference valid and draws nothing.
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
                + "          \"name\": \"acc\",\n"
                + "          \"pivot\": [0.0, 24.0, 0.0],\n"
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

    /** Trims a float to a short decimal so the emitted JSON stays readable. */
    static String f(float value) {
        if (value == Math.round(value)) return Integer.toString(Math.round(value));
        String text = String.format(java.util.Locale.US, "%.2f", value);
        // Trim trailing zeros so "-4.50" reads as "-4.5"; a whole number already returned above.
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
