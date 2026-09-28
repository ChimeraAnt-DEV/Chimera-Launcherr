package org.chimeramc.client.core.content;

import org.chimeramc.client.core.cosmetics.PngWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Wraps one or more flat skin PNGs into a Bedrock skin pack.
 *
 * <p>A bare PNG is not something the game can see: Minecraft only reads skins that arrive inside
 * a pack with a {@code skin_pack} module, a {@code skins.json} entry per skin and a lang file
 * naming them. The Skins screen could already pick a PNG (it opened {@code *}/{@code *}) but the
 * import went through {@code ContentImporter}, which only recognises {@code .mcworld},
 * {@code .mcaddon} and {@code .mcpack} — so the file was selected and then silently did
 * nothing. This builder is what turns the image into a pack the existing activation path
 * ({@link SkinPackActivator}) can then turn on.
 *
 * <p>This class is deliberately Android-free apart from {@code Bitmap}-bearing inputs it never
 * touches: it works from ARGB pixel arrays so the whole thing is unit-testable on the JVM, the
 * same reason {@link PngWriter} exists.
 */
public final class SkinPackBuilder {

    /** Bedrock skin geometries: classic (4px arms) and slim (3px arms). */
    public static final String GEOMETRY_CLASSIC = "geometry.humanoid.custom";
    public static final String GEOMETRY_SLIM = "geometry.humanoid.customSlim";

    /**
     * Pack format version. 2 is long-supported and matches the cape pack; a higher value would
     * be refused by older clients, and the launcher supports a range of Bedrock versions.
     */
    private static final int FORMAT_VERSION = 2;

    private SkinPackBuilder() {
    }

    /** One image to place into the pack, with the name to show for it. */
    public static final class SkinEntry {
        final String displayName;
        final int[] argb;
        final int width;
        final int height;

        public SkinEntry(String displayName, int[] argb, int width, int height) {
            this.displayName = displayName;
            this.argb = argb;
            this.width = width;
            this.height = height;
        }
    }

    /** Result of writing a pack, with everything the caller needs to report or apply it. */
    public static final class BuiltPack {
        public final File directory;
        public final String uuid;
        public final String version;
        public final int skinCount;

        BuiltPack(File directory, String uuid, String version, int skinCount) {
            this.directory = directory;
            this.uuid = uuid;
            this.version = version;
            this.skinCount = skinCount;
        }
    }

    /** Thrown for an input the game cannot use, with a message meant for the user. */
    public static final class InvalidSkinException extends Exception {
        public InvalidSkinException(String message) {
            super(message);
        }
    }

    /**
     * Builds a skin pack containing every given image.
     *
     * <p>All images must be a valid skin size and the same arm model; a pack describes one
     * geometry per skin entry, so mixing classic and slim in one pack is allowed (each entry
     * carries its own geometry) but the caller is expected to have asked once, which is why the
     * common case passes a single geometry.
     *
     * @param parentDir    directory to create the pack in (the instance's {@code skin_packs/})
     * @param packName     human-readable pack name, shown by the game
     * @param entries      one or more skins
     * @param slim         true for the 3px-arm model, false for the classic 4px model
     */
    public static BuiltPack build(File parentDir, String packName, List<SkinEntry> entries,
                                  boolean slim) throws IOException, InvalidSkinException {
        if (parentDir == null) throw new IOException("no skin pack directory");
        if (entries == null || entries.isEmpty()) throw new InvalidSkinException("No image selected");
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("cannot create " + parentDir);
        }

        String safeName = packName == null || packName.trim().isEmpty()
                ? "Custom Skin" : packName.trim();

        // A random uuid per build, not a fixed one: each import is a distinct pack the user can
        // delete or deactivate on its own, and re-importing an edited image must not be silently
        // broadcast over the previous pack the way a fixed uuid would.
        String packUuid = randomUuid();
        String moduleUuid = randomUuid();
        String directoryName = sanitizeFileName(safeName) + "_" + shortId(packUuid);
        File packDir = new File(parentDir, directoryName);
        if (!packDir.mkdirs() && !packDir.isDirectory()) {
            throw new IOException("cannot create " + packDir);
        }

        StringBuilder skinsJson = new StringBuilder();
        skinsJson.append("{\n  \"skins\": [\n");
        StringBuilder lang = new StringBuilder();
        lang.append("pack.name=").append(safeName).append('\n');
        lang.append("pack.description=Imported with GlowberryClient\n");

        for (int i = 0; i < entries.size(); i++) {
            SkinEntry entry = entries.get(i);
            int[] rgba = validateAndNormalise(entry);

            String textureFile = "skin_" + i + ".png";
            writeFile(new File(packDir, textureFile), PngWriter.encode(64, 64, rgba));

            String localization = sanitizeLocalization(safeName + "_" + i);
            String geometry = slim ? GEOMETRY_SLIM : GEOMETRY_CLASSIC;
            String displayName = entry.displayName == null || entry.displayName.trim().isEmpty()
                    ? safeName : entry.displayName.trim();

            skinsJson.append("    {\n");
            skinsJson.append("      \"localization_name\": \"").append(localization).append("\",\n");
            skinsJson.append("      \"geometry\": \"").append(geometry).append("\",\n");
            skinsJson.append("      \"texture\": \"").append(textureFile).append("\",\n");
            skinsJson.append("      \"type\": \"free\"\n");
            skinsJson.append(i == entries.size() - 1 ? "    }\n" : "    },\n");

            lang.append("skin.").append(localization).append('=').append(displayName).append('\n');
        }

        skinsJson.append("  ],\n");
        skinsJson.append("  \"serialize_name\": \"").append(safeName).append("\",\n");
        skinsJson.append("  \"localization_name\": \"").append(safeName).append("\"\n}\n");

        writeFile(new File(packDir, "skins.json"),
                skinsJson.toString().getBytes(StandardCharsets.UTF_8));
        writeFile(new File(packDir, "texts/en_US.lang"), lang.toString().getBytes(StandardCharsets.UTF_8));
        writeFile(new File(packDir, "manifest.json"),
                manifestJson(safeName, packUuid, moduleUuid).getBytes(StandardCharsets.UTF_8));

        // A pack icon is optional but the game shows a blank tile without one; reuse the first
        // skin so the entry is recognisable in the pack list.
        writeFile(new File(packDir, "pack_icon.png"), PngWriter.encode(64, 64, validateAndNormalise(entries.get(0))));

        return new BuiltPack(packDir, packUuid, "1.0.0", entries.size());
    }

    /**
     * Validates a skin and returns it as a 64x64 ARGB array.
     *
     * <p>Accepts 64x64 and 128x128 (HD, downsampled to the 64x64 atlas the pack expects) and
     * converts a legacy 64x32 into 64x64 by mirroring the limbs. Anything else is rejected with
     * the message the UI shows, because a wrong-size skin renders as garbage rather than
     * failing, which is worse than a clear refusal.
     */
    static int[] validateAndNormalise(SkinEntry entry) throws InvalidSkinException {
        int w = entry.width;
        int h = entry.height;
        if (entry.argb == null || entry.argb.length < w * h) {
            throw new InvalidSkinException("Skins must be 64x64 or 128x128 PNG");
        }
        if (w == 64 && h == 64) {
            return entry.argb;
        }
        if (w == 128 && h == 128) {
            return downsample(entry.argb, 128, 128, 64, 64);
        }
        if (w == 64 && h == 32) {
            return legacyToModern(entry.argb);
        }
        if (w == 128 && h == 64) {
            // HD legacy: downsample to 64x32 first, then apply the same conversion.
            int[] small = downsample(entry.argb, 128, 64, 64, 32);
            return legacyToModern(small);
        }
        throw new InvalidSkinException("Skins must be 64x64 or 128x128 PNG");
    }

    /** True when the size is one the builder will accept. Shared with the UI for pre-checks. */
    public static boolean isAcceptableSize(int width, int height) {
        return (width == 64 && (height == 64 || height == 32))
                || (width == 128 && (height == 128 || height == 64));
    }

    private static int[] downsample(int[] src, int sw, int sh, int dw, int dh) {
        int[] out = new int[dw * dh];
        int xRatio = sw / dw;
        int yRatio = sh / dh;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                out[y * dw + x] = src[(y * yRatio) * sw + (x * xRatio)];
            }
        }
        return out;
    }

    /**
     * Expands a legacy 64x32 skin into the 64x64 atlas.
     *
     * <p>Builds the modern left limbs by mirroring the legacy right limbs vertically into the
     * bottom half of the atlas, which is the standard conversion: in the legacy layout the two
     * arms (and legs) shared one texture, and the modern layout wants the left ones placed as
     * flipped copies. The remaining bottom-half regions stay transparent, which is correct — a
     * legacy skin has no second-layer overlay.
     */
    private static int[] legacyToModern(int[] legacy) {
        int[] out = new int[64 * 64];
        // Top half is an exact copy.
        System.arraycopy(legacy, 0, out, 0, 64 * 32);

        // Right arm (40,16)-(56,32) -> right arm bottom (32,48)-(48,64) and
        //                             left arm bottom  (48,48)-(64,64)
        mirrorRegion(legacy, 40, 16, 16, 16, out, 32, 48);
        mirrorRegion(legacy, 40, 16, 16, 16, out, 48, 48);
        // Right leg (0,16)-(16,32) -> left leg (16,48)-(32,64) and right leg (0,48)-(16,64)
        mirrorRegion(legacy, 0, 16, 16, 16, out, 16, 48);
        mirrorRegion(legacy, 0, 16, 16, 16, out, 0, 48);
        return out;
    }

    /** Copies a region flipped vertically, which is how the legacy limbs become the modern ones. */
    private static void mirrorRegion(int[] src, int sx, int sy, int w, int h,
                                     int[] dst, int dx, int dy) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dstX = dx + x;
                int dstY = dy + (h - 1 - y);
                if (dstX < 0 || dstX >= 64 || dstY < 0 || dstY >= 64) continue;
                int srcX = sx + x;
                int srcY = sy + y;
                if (srcX < 0 || srcX >= 64 || srcY < 0 || srcY >= 32) continue;
                dst[dstY * 64 + dstX] = src[srcY * 64 + srcX];
            }
        }
    }

    private static String manifestJson(String name, String headerUuid, String moduleUuid) {
        return "{\n"
                + "  \"format_version\": " + FORMAT_VERSION + ",\n"
                + "  \"header\": {\n"
                + "    \"name\": \"pack.name\",\n"
                + "    \"description\": \"pack.description\",\n"
                + "    \"uuid\": \"" + headerUuid + "\",\n"
                + "    \"version\": [1, 0, 0],\n"
                + "    \"min_engine_version\": [1, 13, 0]\n"
                + "  },\n"
                + "  \"modules\": [\n"
                + "    {\n"
                + "      \"type\": \"skin_pack\",\n"
                + "      \"uuid\": \"" + moduleUuid + "\",\n"
                + "      \"version\": [1, 0, 0]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    /** Bedrock localization keys may not contain spaces or punctuation. */
    static String sanitizeLocalization(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.length() == 0 ? "skin" : out.toString();
    }

    static String sanitizeFileName(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') out.append(c);
            else if (c == ' ') out.append('_');
        }
        return out.length() == 0 ? "skin_pack" : out.toString();
    }

    private static String shortId(String uuid) {
        return uuid.replace("-", "").substring(0, 8);
    }

    private static String randomUuid() {
        // Random rather than SecureRandom: this is a pack identifier, not a secret.
        Random random = new Random();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(String.format(Locale.ROOT, "%02x", bytes[i]));
        }
        return sb.toString();
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(data);
        }
    }
}
