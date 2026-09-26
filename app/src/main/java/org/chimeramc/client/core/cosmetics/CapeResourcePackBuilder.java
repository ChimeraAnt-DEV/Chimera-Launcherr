package org.chimeramc.client.core.cosmetics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds the resource pack that makes an equipped cape appear on the player in-game.
 *
 * <p>This is the mechanism that actually puts a cape on the character. Bedrock has no
 * third-party cape API, but the player entity already carries a cape: its client entity
 * definition binds the shortname {@code cape} to the texture
 * {@code textures/entity/cape_invisible}, and the cape geometry is part of the vanilla player
 * model. A resource pack that ships its own {@code textures/entity/cape_invisible.png} replaces
 * that transparent texture, and the cape the player has equipped in the dressing room then
 * renders with the pack's artwork instead. This is how the community cape packs work.
 *
 * <p><b>Deliberately a texture-only pack.</b> It does not override {@code player.entity.json}.
 * Replacing that file is the common way cape packs break: the wiki and the pack authors both
 * note it disables the Character Creator and can make capes vanish outright, unless a
 * {@code min_engine_version} workaround is used that itself has to be re-verified per release.
 * Overriding a texture cannot break the model, so the worst case here is an invisible cape
 * rather than a broken avatar.
 *
 * <p>Because a texture-only pack cannot animate (animated entity textures need a custom
 * material with {@code USE_UV_ANIM} plus a render controller, and materials are flagged
 * unreliable under RenderDragon), animation is confined to the launcher preview. The pack is a
 * static cape.
 *
 * <p>The pack uuid is fixed rather than random so re-applying an edit updates the existing pack
 * in place instead of accumulating one pack per cape change.
 */
public final class CapeResourcePackBuilder {

    /** Stable pack identity; changing it strands an already-applied pack. */
    public static final String PACK_UUID = "b7c1a5e2-3d4f-4a6b-9c8d-1e2f3a4b5c6d";
    public static final String PACK_VERSION = "1.0.0";
    public static final String PACK_NAME = "Chimera Cape";

    /** Paths the game samples for the equipped cape and for an elytra. */
    static final String CAPE_TEXTURE_PATH = "textures/entity/cape_invisible.png";
    static final String ELYTRA_TEXTURE_PATH = "textures/models/armor/elytra.png";
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

        writeFile(new File(targetDir, "manifest.json"), manifestJson().getBytes(StandardCharsets.UTF_8));

        byte[] texture;
        if (cape == null) {
            texture = CapeTexturePainter.paint(0x00000000, 0x00000000, false);
        } else {
            texture = CapeTexturePainter.paint(cape.color, cape.trimColor, cape.branded);
        }

        writeAt(targetDir, CAPE_TEXTURE_PATH, texture);
        writeAt(targetDir, ELYTRA_TEXTURE_PATH, texture);
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
                + "    \"description\": \"Chimera Client cape (texture override).\",\n"
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
