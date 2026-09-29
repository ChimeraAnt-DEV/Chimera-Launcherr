package org.chimeramc.client.core.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns a skin pack on or off for an instance by editing the same files the game reads.
 *
 * Applying a skin pack in the launcher means two things have to happen, and doing only the
 * first is why a skin picker can look like it worked while the game ignores it:
 * <ol>
 *   <li>the pack's contents must be present under {@code resource_packs/} under a stable name
 *       (its manifest uuid), and</li>
 *   <li>that uuid and version must be listed in {@code minecraftpe/global_resource_packs.json},
 *       which is the file the game reads to decide what is active.</li>
 * </ol>
 *
 * This class owns both, and records which uuids it added in a launcher-side state file so
 * that un-applying removes only the entries it created. A pack the player activated inside
 * the game itself is left alone, because there is no way to tell their intent from ours and
 * silently dropping their selection would be worse than doing nothing.
 *
 * The format is deliberately the same shape {@code BundledResourcePackInstaller} maintains,
 * so the two writers do not fight over the global pack list.
 */
public final class SkinPackActivator {

    private static final String GLOBAL_RESOURCE_PACKS = "global_resource_packs.json";
    private static final String MANAGED_STATE = "chimeralauncher_applied_skins.json";
    private static final String MINECRAFT_PE_DIR = "minecraftpe";
    private static final String RESOURCE_PACKS_DIR = "resource_packs";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SkinPackActivator() {
    }

    /** Outcome of an apply/unapply, carrying a human-readable reason on failure. */
    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result failed(String message) {
            return new Result(false, message);
        }

        /** Exposed so other pack installers can report failure through the same type. */
        public static Result failure(String message) {
            return new Result(false, message);
        }
    }

    /** Identity of a skin pack as the game knows it. */
    public static final class PackIdentity {
        public final String uuid;
        public final String version;

        PackIdentity(String uuid, String version) {
            this.uuid = uuid;
            this.version = version;
        }
    }

    /**
     * Reads a pack's uuid and version from its manifest.
     *
     * @return null when the file is not a readable pack, so the caller can report "not a skin
     *         pack" instead of writing a broken global pack entry.
     */
    public static PackIdentity readIdentity(File pack) {
        if (pack == null) return null;
        if (pack.isDirectory()) {
            return readManifest(new File(pack, "manifest.json"));
        }
        if (!pack.isFile()) return null;
        // A pack file is a zip; the manifest can be at the root or one level down.
        try (ZipFile zip = new ZipFile(pack)) {
            for (String candidate : new String[]{"manifest.json", "skin_pack/manifest.json"}) {
                ZipEntry entry = zip.getEntry(candidate);
                if (entry == null) continue;
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
                    return parseIdentity(new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static PackIdentity readManifest(File manifest) {
        if (!manifest.isFile()) return null;
        try (FileReader reader = new FileReader(manifest)) {
            return parseIdentity(JsonParser.parseReader(reader).toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static PackIdentity parseIdentity(String json) {
        try {
            JsonObject document = JsonParser.parseString(json).getAsJsonObject();
            JsonObject header = document.getAsJsonObject("header");
            if (header == null || !header.has("uuid")) return null;
            String uuid = header.get("uuid").getAsString().trim().toLowerCase(Locale.ROOT);
            if (uuid.isEmpty()) return null;
            String version = "1.0.0";
            if (header.has("version")) {
                JsonElement versionElement = header.get("version");
                if (versionElement.isJsonArray()) {
                    JsonArray array = versionElement.getAsJsonArray();
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < array.size(); i++) {
                        if (i > 0) builder.append('.');
                        builder.append(array.get(i).getAsInt());
                    }
                    version = builder.toString();
                    // The game's global pack format expects a three-part version.
                    while (version.split("\\.").length < 3) version = version + ".0";
                } else {
                    version = versionElement.getAsString();
                }
            }
            return new PackIdentity(uuid, version);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Makes a skin pack active for one instance.
     *
     * @param gameDataDir the instance's game data root (the folder holding {@code resource_packs})
     */
    public static Result apply(File packSource, File gameDataDir) {
        if (packSource == null || !packSource.exists()) {
            return Result.failed("pack file missing");
        }
        if (gameDataDir == null) {
            return Result.failed("no instance storage");
        }
        PackIdentity identity = readIdentity(packSource);
        if (identity == null) {
            return Result.failed("not a readable skin pack");
        }

        try {
            File resourcePacksDir = new File(gameDataDir, RESOURCE_PACKS_DIR);
            ensureDirectory(resourcePacksDir);
            File target = new File(resourcePacksDir, identity.uuid);
            // Copy under a staging name first so a failed copy cannot leave a half-written
            // pack that Minecraft then refuses to load.
            File staging = new File(resourcePacksDir, identity.uuid + ".chimeralauncher_tmp");
            deleteRecursively(staging);
            copyRecursively(packSource, staging);
            deleteRecursively(target);
            moveReplace(staging, target);

            File minecraftPeDir = new File(gameDataDir, MINECRAFT_PE_DIR);
            ensureDirectory(minecraftPeDir);
            File globalFile = new File(minecraftPeDir, GLOBAL_RESOURCE_PACKS);
            List<SkinEntry> entries = removeManaged(globalFile, identity.uuid);
            entries.add(new SkinEntry(identity.uuid, identity.version));
            writeGlobal(globalFile, entries);

            addManaged(gameDataDir, identity.uuid);
            return Result.ok(identity.uuid);
        } catch (Exception e) {
            return Result.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** Removes only the pack this launcher applied, leaving the player's own selections alone. */
    public static Result unapply(File gameDataDir, String uuid) {
        if (gameDataDir == null || uuid == null || uuid.trim().isEmpty()) {
            return Result.failed("nothing to remove");
        }
        String key = uuid.trim().toLowerCase(Locale.ROOT);
        if (!managedUuids(gameDataDir).contains(key)) {
            return Result.failed("not applied by the launcher");
        }
        try {
            File globalFile = new File(new File(gameDataDir, MINECRAFT_PE_DIR), GLOBAL_RESOURCE_PACKS);
            writeGlobal(globalFile, removeManaged(globalFile, key));
            deleteRecursively(new File(new File(gameDataDir, RESOURCE_PACKS_DIR), key));
            removeManagedUuid(gameDataDir, key);
            return Result.ok(key);
        } catch (Exception e) {
            return Result.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** True when this launcher is the one that made the pack active. */
    public static boolean isAppliedByLauncher(File gameDataDir, String uuid) {
        return uuid != null && managedUuids(gameDataDir).contains(uuid.trim().toLowerCase(Locale.ROOT));
    }

    /** Drops entries the launcher owns, leaving anything the game wrote untouched. */
    private static List<SkinEntry> removeManaged(File globalFile, String uuidToRemove) {
        List<SkinEntry> result = new ArrayList<>();
        if (globalFile.isFile()) {
            try (FileReader reader = new FileReader(globalFile)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed != null && parsed.isJsonArray()) {
                    for (JsonElement element : parsed.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject object = element.getAsJsonObject();
                        String packId = object.has("pack_id")
                                ? object.get("pack_id").getAsString().toLowerCase(Locale.ROOT)
                                : null;
                        if (packId != null && packId.equals(uuidToRemove)) continue;
                        SkinEntry entry = new SkinEntry(packId, versionOf(object));
                        result.add(entry);
                    }
                }
            } catch (Exception ignored) {
                // A corrupt global file is replaced with the entry we are adding; keeping
                // garbage would stop the game from reading any global pack at all.
            }
        }
        return result;
    }

    private static void writeGlobal(File globalFile, List<SkinEntry> entries) throws IOException {
        JsonArray array = new JsonArray();
        for (SkinEntry entry : entries) {
            if (entry.uuid == null) continue;
            JsonObject object = new JsonObject();
            object.addProperty("pack_id", entry.uuid);
            object.add("version", versionArray(entry.version));
            array.add(object);
        }
        ensureDirectory(globalFile.getParentFile());
        File temp = new File(globalFile.getParentFile(), globalFile.getName() + ".chimeralauncher_tmp");
        try (FileWriter writer = new FileWriter(temp, false)) {
            GSON.toJson(array, writer);
        }
        moveReplace(temp, globalFile);
    }

    private static List<String> managedUuids(File gameDataDir) {
        List<String> result = new ArrayList<>();
        File state = new File(new File(gameDataDir, MINECRAFT_PE_DIR), MANAGED_STATE);
        if (!state.isFile()) return result;
        try (FileReader reader = new FileReader(state)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("uuids");
            if (array == null) return result;
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    result.add(element.getAsString().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void addManaged(File gameDataDir, String uuid) throws IOException {
        List<String> uuids = managedUuids(gameDataDir);
        if (!uuids.contains(uuid)) uuids.add(uuid);
        writeManaged(gameDataDir, uuids);
    }

    private static void removeManagedUuid(File gameDataDir, String uuid) throws IOException {
        List<String> uuids = managedUuids(gameDataDir);
        uuids.remove(uuid);
        writeManaged(gameDataDir, uuids);
    }

    private static void writeManaged(File gameDataDir, List<String> uuids) throws IOException {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (String uuid : uuids) array.add(uuid);
        root.add("uuids", array);
        File state = new File(new File(gameDataDir, MINECRAFT_PE_DIR), MANAGED_STATE);
        ensureDirectory(state.getParentFile());
        try (FileWriter writer = new FileWriter(state, false)) {
            GSON.toJson(root, writer);
        }
    }

    private static void copyRecursively(File source, File target) throws IOException {
        if (source.isDirectory()) {
            ensureDirectory(target);
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) copyRecursively(child, new File(target, child.getName()));
            return;
        }
        ensureDirectory(target.getParentFile());
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void moveReplace(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) throw new IOException("Failed to delete " + file);
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (dir == null || dir.isDirectory()) return;
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("Failed to create " + dir);
    }

    /**
     * Coerces a version to the JSON form the game expects: a three-number array ({@code [1,0,0]}).
     *
     * <p>A pack-list entry is matched against the manifest's own array version, and a string
     * version is not comparable, so the game silently drops the pack. Writing {@code "version"}
     * as a string was exactly why an applied skin pack (and a cape pack) could look enabled in the
     * launcher while the game ignored it.
     */
    private static JsonArray versionArray(String version) {
        int[] parts = {1, 0, 0};
        if (version != null && !version.trim().isEmpty()) {
            String[] tokens = version.trim().split("\\.");
            for (int i = 0; i < 3 && i < tokens.length; i++) {
                try {
                    parts[i] = Integer.parseInt(tokens[i].trim());
                } catch (NumberFormatException ignored) {
                    parts[i] = 0;
                }
            }
        }
        JsonArray array = new JsonArray();
        array.add(parts[0]);
        array.add(parts[1]);
        array.add(parts[2]);
        return array;
    }

    /** Reads a version whether it was written as a string, an array, or is absent. */
    private static String versionOf(JsonObject entry) {
        if (entry == null || !entry.has("version")) return null;
        JsonElement value = entry.get("version");
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) builder.append('.');
                builder.append(array.get(i).getAsInt());
            }
            return builder.toString();
        }
        return value.getAsString();
    }

    private static final class SkinEntry {
        final String uuid;
        final String version;

        SkinEntry(String uuid, String version) {
            this.uuid = uuid;
            this.version = version;
        }
    }
}
