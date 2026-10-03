package org.chimeramc.client.core.cosmetics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds the resource pack that makes the equipped cape appear on the player in-game.
 *
 * <p><b>Why not a texture override.</b> The previous implementation replaced the player's
 * {@code textures/entity/cape_invisible.png}. That file is the <em>vanilla</em> cape slot, but a
 * cape the player equipped through Microsoft's Persona cosmetics system is not rendered from it:
 * its texture is fetched per-account, so the override was never sampled for the case that matters
 * and nothing showed. Overriding the file was also a no-op for a player with no vanilla cape
 * equipped.
 *
 * <p><b>What this does instead.</b> It renders the cape as first-party JSON content: a client-entity
 * render controller plus a geometry and a texture. The pack overrides
 * {@code entity/player.entity.json} and adds
 * <ul>
 *   <li>a {@code chimera_cape} texture/geometry/material shortname on the player,</li>
 *   <li>a {@code controller.render.chimera_cape} render controller that draws a cape box on the
 *       player's back in third person (and the paperdoll), and</li>
 *   <li>the geometry ({@code models/entity/chimera_cape.geo.json}) and texture
 *       ({@code textures/entity/chimera_cape.png}).</li>
 * </ul>
 * Because the cape is our own geometry drawn unconditionally, it does not depend on a vanilla cape
 * being equipped and it never touches the Persona texture path. No native code, no hooking, no
 * reverse engineering — a plain resource pack.
 *
 * <p><b>On attachables.</b> A {@code minecraft:attachable} is bound to an <em>item</em> slot
 * (main hand, off hand, armour). A cape is not an item the player holds, so there is no item for an
 * attachable to bind to; the client-entity render controller is the supported data-driven route for
 * a cape, and it is the one the working community cape packs use. The pack is still pure JSON
 * content, no native code.
 *
 * <p><b>Why {@code min_engine_version} stays low.</b> A player client-entity file whose
 * {@code min_engine_version} is above 1.13.0 disables the Character Creator (Persona skins and
 * capes). The entity file declares {@code 1.8.0}, the value the working community packs use, so
 * custom and Persona skins keep rendering and only the cape is added.
 *
 * <p>The pack uuid is fixed rather than random so re-applying an edit updates the existing pack in
 * place instead of accumulating one pack per cape change.
 */
public final class CapeResourcePackBuilder {

    /** Stable pack identity; changing it strands an already-applied pack. */
    public static final String PACK_UUID = "b7c1a5e2-3d4f-4a6b-9c8d-1e2f3a4b5c6d";
    public static final String PACK_VERSION = "1.0.0";
    public static final String PACK_NAME = "Chimera Cape";

    /** Identifier of the cape geometry, referenced by the entity and the render controller. */
    public static final String CAPE_GEOMETRY_ID = "geometry.chimera_cape";
    /** Render controller that draws the cape; listed by the player client entity. */
    public static final String CAPE_CONTROLLER_ID = "controller.render.chimera_cape";
    /** The animation that gives the cape its cloth motion; played by the {@code cape} key. */
    public static final String CAPE_ANIMATION_ID = "animation.chimera_cape";

    /** Paths the pack writes. Public so diagnostics and tests can verify the pack layout. */
    public static final String PLAYER_ENTITY_PATH = "entity/player.entity.json";
    public static final String CAPE_MODEL_PATH = "models/entity/chimera_cape.geo.json";
    public static final String CAPE_RENDER_CONTROLLER_PATH =
            "render_controllers/chimera_cape.render_controllers.json";
    public static final String CAPE_ANIMATION_PATH = "animations/chimera_cape.animation.json";
    public static final String CAPE_TEXTURE_PATH = "textures/entity/chimera_cape.png";
    static final String PACK_ICON_PATH = "pack_icon.png";

    /**
     * Bedrock refuses packs whose format version it does not recognise, and a manifest that is
     * too new fails to load on older clients. The rules the launcher ships cover 1.26.x, so the
     * manifest declares a long-supported format.
     */
    private static final int FORMAT_VERSION = 2;

    private CapeResourcePackBuilder() {
    }

    /** Result of writing a pack, carrying the files that were produced. */
    public static final class BuiltPack {
        public final File directory;
        public final String uuid;
        public final String version;

        BuiltPack(File directory, String uuid, String version) {
            this.directory = directory;
            this.uuid = uuid;
            this.version = version;
        }
    }

    /**
     * Writes a cape resource pack into {@code targetDir}.
     *
     * @param targetDir   directory to create the pack in; must not already exist as a file
     * @param cape        the cape to render, or {@code null} for a blank/no-cape pack
     * @throws IOException when a file cannot be written
     */
    public static BuiltPack build(File targetDir, CosmeticCatalog.Cape cape) throws IOException {
        if (targetDir == null) throw new IOException("no target directory");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("cannot create " + targetDir);
        }
        if (!targetDir.isDirectory()) {
            throw new IOException("not a directory: " + targetDir);
        }

        writeFile(new File(targetDir, "manifest.json"),
                manifestJson().getBytes(StandardCharsets.UTF_8));

        byte[] texture;
        if (cape == null) {
            texture = CapeTexturePainter.paint(0x00000000, 0x00000000, false);
        } else {
            texture = CapeTexturePainter.paint(cape.color, cape.trimColor, cape.accentColor,
                    cape.pattern, cape.branded);
        }

        writeAt(targetDir, PLAYER_ENTITY_PATH,
                playerEntityJson().getBytes(StandardCharsets.UTF_8));
        writeAt(targetDir, CAPE_MODEL_PATH,
                capeModelJson().getBytes(StandardCharsets.UTF_8));
        writeAt(targetDir, CAPE_RENDER_CONTROLLER_PATH,
                capeRenderControllerJson().getBytes(StandardCharsets.UTF_8));
        writeAt(targetDir, CAPE_ANIMATION_PATH,
                capeAnimationJson().getBytes(StandardCharsets.UTF_8));
        writeAt(targetDir, CAPE_TEXTURE_PATH, texture);
        writeAt(targetDir, PACK_ICON_PATH, texture);

        return new BuiltPack(targetDir, PACK_UUID, PACK_VERSION);
    }

    private static void writeAt(File root, String relativePath, byte[] data) throws IOException {
        File file = new File(root, relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        writeFile(file, data);
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(data);
        }
    }

    /**
     * The pack manifest.
     *
     * <p>{@code resources} declares a single {@code resources} module, which is what tells the
     * game this pack supplies client-side assets. A missing module type makes the pack import but
     * apply nothing.
     */
    static String manifestJson() {
        return "{\n"
                + "  \"format_version\": " + FORMAT_VERSION + ",\n"
                + "  \"header\": {\n"
                + "    \"name\": \"" + PACK_NAME + "\",\n"
                + "    \"description\": \"GlowberryClient cape (client-entity render controller).\",\n"
                + "    \"uuid\": \"" + PACK_UUID + "\",\n"
                + "    \"version\": [" + versionArray(PACK_VERSION) + "],\n"
                + "    \"min_engine_version\": [1, 20, 0]\n"
                + "  },\n"
                + "  \"modules\": [\n"
                + "    {\n"
                + "      \"type\": \"resources\",\n"
                + "      \"uuid\": \"" + moduleUuid() + "\",\n"
                + "      \"version\": [" + versionArray(PACK_VERSION) + "]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    /**
     * The player client-entity override.
     *
     * <p>Adds the cape shortnames and the render controller to the vanilla player description.
     * Everything else is the vanilla animation set so custom skins, Persona skins and every
     * existing player animation keep working; only the cape is new.
     */
    static String playerEntityJson() {
        return "{\n"
                + "  \"format_version\": \"1.10.0\",\n"
                + "  \"minecraft:client_entity\": {\n"
                + "    \"description\": {\n"
                + "      \"identifier\": \"minecraft:player\",\n"
                + "      \"min_engine_version\": \"1.8.0\",\n"
                + "      \"materials\": {\n"
                + "        \"default\": \"entity_alphatest\",\n"
                + "        \"cape\": \"entity_alphatest\",\n"
                + "        \"animated\": \"player_animated\",\n"
                + "        \"spectator\": \"player_spectator\",\n"
                + "        \"chimera_cape\": \"entity_alphatest\"\n"
                + "      },\n"
                + "      \"textures\": {\n"
                + "        \"default\": \"textures/entity/steve\",\n"
                + "        \"cape\": \"textures/entity/cape_invisible\",\n"
                + "        \"chimera_cape\": \"" + CAPE_TEXTURE_PATH + "\"\n"
                + "      },\n"
                + "      \"geometry\": {\n"
                + "        \"default\": \"geometry.humanoid.custom\",\n"
                + "        \"cape\": \"geometry.cape\",\n"
                + "        \"chimera_cape\": \"" + CAPE_GEOMETRY_ID + "\"\n"
                + "      },\n"
                + "      \"scripts\": {\n"
                + "        \"scale\": \"0.9375\",\n"
                + "        \"initialize\": [\n"
                + "          \"variable.is_holding_right = 0.0;\",\n"
                + "          \"variable.is_blinking = 0.0;\",\n"
                + "          \"variable.last_blink_time = 0.0;\",\n"
                + "          \"variable.hand_bob = 0.0;\"\n"
                + "        ],\n"
                + "        \"pre_animation\": [\n"
                + "          \"variable.helmet_layer_visible = !query.has_head_gear;\",\n"
                + "          \"variable.leg_layer_visible = 1.0;\",\n"
                + "          \"variable.boot_layer_visible = 1.0;\",\n"
                + "          \"variable.chest_layer_visible = 1.0;\",\n"
                + "          \"variable.attack_body_rot_y = Math.sin(360*Math.sqrt(variable.attack_time)) * 5.0;\",\n"
                + "          \"variable.tcos0 = (math.cos(query.modified_distance_moved * 38.17) * query.modified_move_speed / variable.gliding_speed_value) * 57.3;\",\n"
                + "          \"variable.first_person_rotation_factor = math.sin((1 - variable.attack_time) * 180.0);\",\n"
                + "          \"variable.hand_bob = query.life_time < 0.01 ? 0.0 : variable.hand_bob + ((query.is_on_ground && query.is_alive ? math.clamp(math.sqrt(math.pow(query.position_delta(0), 2.0) + math.pow(query.position_delta(2), 2.0)), 0.0, 0.1) : 0.0) - variable.hand_bob) * 0.02;\",\n"
                + "          \"variable.map_angle = math.clamp(1 - variable.player_x_rotation / 45.1, 0.0, 1.0);\",\n"
                + "          \"variable.item_use_normalized = query.main_hand_item_use_duration / query.main_hand_item_max_duration;\",\n"
                + "          \"variable.riding_y_offset = query.is_riding_any_entity_of_type('minecraft:minecart', 'minecraft:boat', 'minecraft:chest_boat', 'minecraft:strider') ? -3.0 : 0.0;\"\n"
                + "        ],\n"
                + "        \"animate\": [\n"
                + "          \"root\"\n"
                + "        ],\n"
                + "        \"variables\": {\n"
                + "          \"variable.fp_melee_spear_use_attachable_rotation_z\": \"public\",\n"
                + "          \"variable.tp_melee_spear_use_attachable_rotation_z\": \"public\",\n"
                + "          \"variable.fp_melee_spear_attack_attachable_rotation_z\": \"public\",\n"
                + "          \"variable.tp_melee_spear_attack_attachable_position_z\": \"public\",\n"
                + "          \"variable.attack_time\": \"public\",\n"
                + "          \"variable.item_use_normalized\": \"public\"\n"
                + "        },\n"
                + "        \"should_update_effects_offscreen\": \"1.0\"\n"
                + "      },\n"
                + "      \"animations\": {\n"
                + "        \"root\": \"controller.animation.player.root\",\n"
                + "        \"base_controller\": \"controller.animation.player.base\",\n"
                + "        \"hudplayer\": \"controller.animation.player.hudplayer\",\n"
                + "        \"humanoid_base_pose\": \"animation.humanoid.base_pose\",\n"
                + "        \"look_at_target\": \"controller.animation.humanoid.look_at_target\",\n"
                + "        \"look_at_target_ui\": \"animation.player.look_at_target.ui\",\n"
                + "        \"look_at_target_default\": \"animation.humanoid.look_at_target.default\",\n"
                + "        \"look_at_target_gliding\": \"animation.humanoid.look_at_target.gliding\",\n"
                + "        \"look_at_target_swimming\": \"animation.humanoid.look_at_target.swimming\",\n"
                + "        \"look_at_target_inverted\": \"animation.player.look_at_target.inverted\",\n"
                + "        \"cape\": \"" + CAPE_ANIMATION_ID + "\",\n"
                + "        \"move.arms\": \"animation.player.move.arms\",\n"
                + "        \"move.legs\": \"animation.player.move.legs\",\n"
                + "        \"swimming\": \"animation.player.swim\",\n"
                + "        \"swimming.legs\": \"animation.player.swim.legs\",\n"
                + "        \"riding.arms\": \"animation.player.riding.arms\",\n"
                + "        \"riding.legs\": \"animation.player.riding.legs\",\n"
                + "        \"holding\": \"animation.player.holding\",\n"
                + "        \"brandish_spear\": \"animation.humanoid.brandish_spear\",\n"
                + "        \"charging\": \"animation.humanoid.charging\",\n"
                + "        \"attack.positions\": \"animation.player.attack.positions\",\n"
                + "        \"attack.rotations\": \"animation.player.attack.rotations\",\n"
                + "        \"sneaking\": \"animation.player.sneaking\",\n"
                + "        \"bob\": \"animation.player.bob\",\n"
                + "        \"damage_nearby_mobs\": \"animation.humanoid.damage_nearby_mobs\",\n"
                + "        \"bow_and_arrow\": \"animation.humanoid.bow_and_arrow\",\n"
                + "        \"use_item_progress\": \"animation.humanoid.use_item_progress\",\n"
                + "        \"skeleton_attack\": \"animation.skeleton.attack\",\n"
                + "        \"sleeping\": \"animation.player.sleeping\",\n"
                + "        \"first_person_base_pose\": \"animation.player.first_person.base_pose\",\n"
                + "        \"first_person_empty_hand\": \"animation.player.first_person.empty_hand\",\n"
                + "        \"first_person_swap_item\": \"animation.player.first_person.swap_item\",\n"
                + "        \"first_person_attack_controller\": \"controller.animation.player.first_person_attack\",\n"
                + "        \"first_person_attack_rotation\": \"animation.player.first_person.attack_rotation\",\n"
                + "        \"first_person_attack_rotation_item\": \"animation.player.first_person.attack_rotation_item\",\n"
                + "        \"first_person_vr_attack_rotation\": \"animation.player.first_person.vr_attack_rotation\",\n"
                + "        \"first_person_walk\": \"animation.player.first_person.walk\",\n"
                + "        \"first_person_map_controller\": \"controller.animation.player.first_person_map\",\n"
                + "        \"first_person_map_hold\": \"animation.player.first_person.map_hold\",\n"
                + "        \"first_person_map_hold_attack\": \"animation.player.first_person.map_hold_attack\",\n"
                + "        \"first_person_map_hold_off_hand\": \"animation.player.first_person.map_hold_off_hand\",\n"
                + "        \"first_person_map_hold_main_hand\": \"animation.player.first_person.map_hold_main_hand\",\n"
                + "        \"first_person_crossbow_equipped\": \"animation.player.first_person.crossbow_equipped\",\n"
                + "        \"first_person_crossbow_hold\": \"animation.player.first_person.crossbow_hold\",\n"
                + "        \"first_person_breathing_bob\": \"animation.player.first_person.breathing_bob\",\n"
                + "        \"third_person_crossbow_equipped\": \"animation.player.crossbow_equipped\",\n"
                + "        \"third_person_bow_equipped\": \"animation.player.bow_equipped\",\n"
                + "        \"crossbow_hold\": \"animation.player.crossbow_hold\",\n"
                + "        \"crossbow_controller\": \"controller.animation.player.crossbow\",\n"
                + "        \"shield_block_main_hand\": \"animation.player.shield_block_main_hand\",\n"
                + "        \"shield_block_off_hand\": \"animation.player.shield_block_off_hand\",\n"
                + "        \"blink\": \"controller.animation.persona.blink\",\n"
                + "        \"fishing_rod\": \"animation.humanoid.fishing_rod\",\n"
                + "        \"holding_spyglass\": \"animation.humanoid.holding_spyglass\",\n"
                + "        \"first_person_shield_block\": \"animation.player.first_person.shield_block\",\n"
                + "        \"tooting_goat_horn\": \"animation.humanoid.tooting_goat_horn\",\n"
                + "        \"holding_brush\": \"animation.humanoid.holding_brush\",\n"
                + "        \"brushing\": \"animation.humanoid.brushing\",\n"
                + "        \"crawling\": \"animation.player.crawl\",\n"
                + "        \"crawling.legs\": \"animation.player.crawl.legs\",\n"
                + "        \"holding_heavy_core\": \"animation.player.holding_heavy_core\"\n"
                + "      },\n"
                + "      \"render_controllers\": [\n"
                + "        {\n"
                + "          \"controller.render.player.first_person_spectator\": \"variable.is_first_person && query.is_spectator\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"controller.render.player.third_person_spectator\": \"!variable.is_first_person && !variable.map_face_icon && query.is_spectator\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"controller.render.player.first_person\": \"variable.is_first_person && !query.is_spectator\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"controller.render.player.third_person\": \"!variable.is_first_person && !variable.map_face_icon && !query.is_spectator\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"controller.render.player.map\": \"variable.map_face_icon\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"" + CAPE_CONTROLLER_ID + "\": \"" + capeVisibilityCondition() + "\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"enable_attachables\": true\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * When the cape draws: third person, or the paperdoll (dressing-room preview), never in first
     * person, on the map icon, or as a spectator. The same condition gates the render controller
     * and its part visibility so the two cannot disagree.
     */
    static String capeVisibilityCondition() {
        return "(!variable.is_first_person || variable.is_paperdoll)"
                + " && !variable.map_face_icon && !query.is_spectator";
    }

    /**
     * The cape geometry.
     *
     * <p>A 10x16x1 box on the player's back, using the vanilla {@code geometry.cape} bone layout
     * (bone {@code cape}, parent {@code body}, pivot at the shoulders) and the standard cape UV
     * unwrap at {@code [0,0]}, which is the layout {@link CapeTexturePainter} paints.
     */
    static String capeModelJson() {
        return "{\n"
                + "  \"format_version\": \"1.12.0\",\n"
                + "  \"minecraft:geometry\": [\n"
                + "    {\n"
                + "      \"description\": {\n"
                + "        \"identifier\": \"" + CAPE_GEOMETRY_ID + "\",\n"
                + "        \"texture_width\": 64,\n"
                + "        \"texture_height\": 32,\n"
                + "        \"visible_bounds_width\": 2,\n"
                + "        \"visible_bounds_height\": 3,\n"
                + "        \"visible_bounds_offset\": [0, 1, 0]\n"
                + "      },\n"
                + "      \"bones\": [\n"
                + "        {\n"
                + "          \"name\": \"body\",\n"
                + "          \"pivot\": [0.0, 24.0, 0.0],\n"
                + "          \"parent\": \"waist\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"name\": \"waist\",\n"
                + "          \"pivot\": [0.0, 12.0, 0.0]\n"
                + "        },\n"
                + "        {\n"
                + "          \"name\": \"cape\",\n"
                + "          \"parent\": \"body\",\n"
                + "          \"pivot\": [0.0, 24.0, 3.0],\n"
                + "          \"bind_pose_rotation\": [0.0, 180.0, 0.0],\n"
                + "          \"rotation\": [0.0, 180.0, 0.0],\n"
                + "          \"cubes\": [\n"
                + "            {\n"
                + "              \"origin\": [-5.0, 8.0, 3.0],\n"
                + "              \"size\": [10, 16, 1],\n"
                + "              \"uv\": [0, 0]\n"
                + "            }\n"
                + "          ]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    /**
     * The cape's cloth animation.
     *
     * <p>A geometry alone renders the cape as a rigid box: the vanilla player controller plays the
     * {@code cape} animation key in third person and the paperdoll, and the pack points that key at
     * this animation, so this is what turns a stiff plank into cloth. It drives the {@code cape}
     * bone with the movement queries Bedrock exposes — {@code modified_move_speed},
     * {@code is_jumping}, {@code vertical_speed} and {@code modified_distance_moved} — through the
     * expressions {@link CapeAnimationCurve} also implements in Java, so the amplitudes have one
     * definition rather than one per language.
     *
     * <p>No {@code loop} key: the default (non-looping) is what vanilla uses for this bone, and the
     * expression is a continuous function of the queries, so it tracks the player either way.
     */
    static String capeAnimationJson() {
        return "{\n"
                + "  \"format_version\": \"1.8.0\",\n"
                + "  \"animations\": {\n"
                + "    \"" + CAPE_ANIMATION_ID + "\": {\n"
                + "      \"bones\": {\n"
                + "        \"cape\": {\n"
                + "          \"rotation\": [\"" + CapeAnimationCurve.leanExpression() + "\", 180.0, \""
                + CapeAnimationCurve.swayExpression() + "\"]\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * The cape render controller.
     *
     * <p>Renders the {@code chimera_cape} geometry with the {@code chimera_cape} texture and
     * material. {@code is_hurt_color} is fully transparent so taking damage does not flash the
     * cape red, which would read as a bug rather than feedback.
     */
    static String capeRenderControllerJson() {
        return "{\n"
                + "  \"format_version\": \"1.8.0\",\n"
                + "  \"render_controllers\": {\n"
                + "    \"" + CAPE_CONTROLLER_ID + "\": {\n"
                + "      \"geometry\": \"Geometry.chimera_cape\",\n"
                + "      \"materials\": [\n"
                + "        {\n"
                + "          \"*\": \"Material.chimera_cape\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"textures\": [\n"
                + "        \"Texture.chimera_cape\"\n"
                + "      ],\n"
                + "      \"part_visibility\": [\n"
                + "        {\n"
                + "          \"cape\": \"" + capeVisibilityCondition() + "\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"is_hurt_color\": {\n"
                + "        \"r\": 0.0,\n"
                + "        \"g\": 0.0,\n"
                + "        \"b\": 0.0,\n"
                + "        \"a\": 0.0\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * Module uuid. A literal rather than derived from the pack uuid: both must be valid hex
     * uuids, and arithmetic on a hex digit can produce a non-hex character.
     */
    static String moduleUuid() {
        return "c8d2b6f3-4e5a-4b7c-8d9e-2f3a4b5c6d7e";
    }

    private static String versionArray(String version) {
        String[] parts = version.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append(", ");
            try {
                builder.append(Integer.parseInt(parts[i]));
            } catch (NumberFormatException e) {
                builder.append(0);
            }
        }
        return builder.toString();
    }
}
