package org.chimeramc.client.core.cosmetics;

/**
 * Thematic particle effects attached to cosmetics, as pure Bedrock JSON.
 *
 * <p><b>Why this works and is safe.</b> A render controller emits particles by naming an effect in
 * its {@code particle_effects} list, and the effect is defined by a {@code particles/*.json} file
 * in the same resource pack. Both are standard, data-driven Bedrock content — no native code, no
 * hooking. A controller that names a particle file the pack does not ship is simply skipped by the
 * game, so a cosmetic with no effect writes no particle file and is unaffected.
 *
 * <p><b>Which cosmetics get one.</b> Only the ones with an obvious thematic tie, so the effect
 * reads as part of the piece rather than decoration: the Creeper palette carries a fuse spark, the
 * Ember palette rising embers, the Void palette slow dark motes, Gilded a gold sparkle, Lagoon
 * drifting bubbles. Everything else is intentionally effect-free.
 *
 * <p>Pure string building, so a unit test can parse each particle file and assert it is valid JSON
 * with a matching identifier, and that only the themed cosmetics resolve to an effect.
 */
public final class CosmeticEffects {

    /** Directory the particle files are written into. */
    public static final String PARTICLE_DIR = "particles/";

    /** One effect: an identifier plus the tint it emits. */
    public static final class Effect {
        public final String id;
        public final float r, g, b;
        public final float rate;
        public final float speed;

        Effect(String id, int color, float rate, float speed) {
            this.id = id;
            this.r = ((color >> 16) & 0xFF) / 255f;
            this.g = ((color >> 8) & 0xFF) / 255f;
            this.b = (color & 0xFF) / 255f;
            this.rate = rate;
            this.speed = speed;
        }
    }

    // Named effects, one per themed palette.
    static final Effect CREEPER_FUSE = new Effect("chimera:creeper_fuse", 0xFF8BC34A, 3f, 0.4f);
    static final Effect EMBER_RISE = new Effect("chimera:ember_rise", 0xFFFF8A5B, 5f, 0.7f);
    static final Effect VOID_MOTES = new Effect("chimera:void_motes", 0xFF8F979F, 2f, 0.25f);
    static final Effect GILDED_SPARKLE = new Effect("chimera:gilded_sparkle", 0xFFFFD86B, 2f, 0.35f);
    static final Effect LAGOON_BUBBLE = new Effect("chimera:lagoon_bubble", 0xFF57D9DB, 4f, 0.5f);

    private CosmeticEffects() {
    }

    /** The effect for a cape, or {@code null} when the cape has no thematic tie. */
    public static Effect forCape(CosmeticCatalog.Cape cape) {
        if (cape == null) return null;
        return byTheme(cape.id);
    }

    /** The effect for a worn accessory, or {@code null} when it has no thematic tie. */
    public static Effect forAccessory(CosmeticCatalog.Accessory accessory) {
        if (accessory == null) return null;
        return byTheme(accessory.id);
    }

    /**
     * Maps a cosmetic id to its effect by the palette stem the id begins with. Ids are
     * {@code <palette>_<shape>}, so the prefix is a stable key that survives new shapes being added
     * to a palette.
     */
    static Effect byTheme(String id) {
        if (id == null) return null;
        if (id.startsWith("creeper")) return CREEPER_FUSE;
        if (id.startsWith("ember")) return EMBER_RISE;
        if (id.startsWith("void")) return VOID_MOTES;
        if (id.startsWith("gilded")) return GILDED_SPARKLE;
        if (id.startsWith("lagoon")) return LAGOON_BUBBLE;
        return null;
    }

    /** The pack-relative path of an effect's particle file. */
    public static String pathFor(Effect effect) {
        String name = effect.id.contains(":") ? effect.id.substring(effect.id.indexOf(':') + 1)
                : effect.id;
        return PARTICLE_DIR + name + ".json";
    }

    /**
     * The particle definition for one effect.
     *
     * <p>A steady, looping emitter of small tinted motes that rise and fade, so a stationary player
     * still shows the effect without it becoming a fountain. The tint gradient fades alpha to zero
     * over the particle's life so motes vanish rather than pop.
     */
    public static String particleJson(Effect effect) {
        String c0 = colorArray(effect.r, effect.g, effect.b, 1f);
        String c1 = colorArray(effect.r * 0.6f, effect.g * 0.6f, effect.b * 0.6f, 0f);
        return "{\n"
                + "  \"format_version\": \"1.10.0\",\n"
                + "  \"particle_effect\": {\n"
                + "    \"description\": {\n"
                + "      \"identifier\": \"" + effect.id + "\",\n"
                + "      \"basic_render_parameters\": {\n"
                + "        \"material\": \"particles_alpha\",\n"
                + "        \"texture\": \"textures/particle/particles\"\n"
                + "      }\n"
                + "    },\n"
                + "    \"components\": {\n"
                + "      \"minecraft:emitter_local_space\": {\n"
                + "        \"position\": true,\n"
                + "        \"rotation\": false\n"
                + "      },\n"
                + "      \"minecraft:emitter_rate_steady\": {\n"
                + "        \"spawn_rate\": " + f(effect.rate) + ",\n"
                + "        \"max_particles\": 16\n"
                + "      },\n"
                + "      \"minecraft:emitter_lifetime_looping\": {\n"
                + "        \"active_time\": 1.0\n"
                + "      },\n"
                + "      \"minecraft:emitter_shape_sphere\": {\n"
                + "        \"radius\": 0.5,\n"
                + "        \"direction\": \"outwards\"\n"
                + "      },\n"
                + "      \"minecraft:particle_lifetime_expression\": {\n"
                + "        \"max_lifetime\": 1.2\n"
                + "      },\n"
                + "      \"minecraft:particle_initial_speed\": " + f(effect.speed) + ",\n"
                + "      \"minecraft:particle_motion_dynamic\": {\n"
                + "        \"linear_acceleration\": [0.0, 1.5, 0.0],\n"
                + "        \"linear_drag_coefficient\": 2.0\n"
                + "      },\n"
                + "      \"minecraft:particle_appearance_billboard\": {\n"
                + "        \"size\": [0.06, 0.06],\n"
                + "        \"facing_camera_mode\": \"rotate_xyz\",\n"
                + "        \"uv\": {\n"
                + "          \"texture_width\": 128,\n"
                + "          \"texture_height\": 128,\n"
                + "          \"uv\": [0, 0],\n"
                + "          \"uv_size\": [8, 8]\n"
                + "        }\n"
                + "      },\n"
                + "      \"minecraft:particle_appearance_tinting\": {\n"
                + "        \"color\": {\n"
                + "          \"gradient\": [" + c0 + ", " + c1 + "],\n"
                + "          \"interpolant\": \"variable.particle_age / variable.particle_lifetime\"\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    private static String colorArray(float r, float g, float b, float a) {
        return "[" + f(r) + ", " + f(g) + ", " + f(b) + ", " + f(a) + "]";
    }

    private static String f(float value) {
        String text = String.format(java.util.Locale.US, "%.3f", value);
        while (text.endsWith("0") && text.contains(".")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
