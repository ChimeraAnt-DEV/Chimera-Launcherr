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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enables or disables the resource packs of an instance without leaving the game.
 *
 * <p>Bedrock decides which packs are active by reading
 * {@code minecraftpe/global_resource_packs.json}: an array of {@code {pack_id, version}} entries.
 * The packs themselves live under {@code resource_packs/<uuid>/}. This class reads the installed
 * packs from the folder and rewrites the global list, which is the file the running game reloads
 * when the world's pack selection is refreshed.
 *
 * <p>Deliberately File-based rather than Context-based so the list/merge logic is unit-testable
 * on the JVM, the same split the rest of the content package uses.
 *
 * <p>This is the launcher's own writer for the global pack list. {@link SkinPackActivator} owns
 * the skin-pack path and records what *it* applied so it never removes a player's own choice;
 * here the toggle <em>is</em> the player's explicit instruction, so entries are added and removed
 * directly, and unrelated entries are preserved verbatim.
 */
public final class InGamePackChanger {

    private static final String RESOURCE_PACKS_DIR = "resource_packs";
    private static final String MINECRAFT_PE_DIR = "minecraftpe";
    private static final String GLOBAL_RESOURCE_PACKS = "global_resource_packs.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private InGamePackChanger() {
    }

    /** One installed resource pack and whether the game currently has it active. */
    public static final class PackEntry {
        public final String uuid;
        public final String name;
        public final String version;
        public final File directory;
        public final boolean active;

        PackEntry(String uuid, String name, String version, File directory, boolean active) {
            this.uuid = uuid;
            this.name = name;
            this.version = version;
            this.directory = directory;
            this.active = active;
        }
    }

    /**
     * Lists the instance's resource packs, newest-listed first, sorted by name.
     *
     * <p>Never throws: a game data root that does not exist yet simply yields an empty list, so
     * the in-game panel shows "no packs" rather than crashing the menu.
     */
    public static List<PackEntry> listPacks(File gameDataDir) {
        List<PackEntry> result = new ArrayList<>();
        if (gameDataDir == null) return result;

        Set<String> active = activeUuids(gameDataDir);
        File resourcePacks = new File(gameDataDir, RESOURCE_PACKS_DIR);
        File[] children = resourcePacks.listFiles();
        if (children == null) return result;

        for (File child : children) {
            SkinPackActivator.PackIdentity identity = SkinPackActivator.readIdentity(child);
            if (identity == null) continue;
            String name = readPackName(child);
            result.add(new PackEntry(identity.uuid, name, identity.version, child,
                    active.contains(identity.uuid)));
        }
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    /** Uuids currently listed in {@code global_resource_packs.json}. */
    public static Set<String> activeUuids(File gameDataDir) {
        Set<String> result = new LinkedHashSet<>();
        JsonArray array = readGlobalArray(globalFile(gameDataDir));
        if (array == null) return result;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (object.has("pack_id")) {
                result.add(object.get("pack_id").getAsString().trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    /**
     * Turns a pack on or off in the instance's global list.
     *
     * @return {@code true} when the list was written; {@code false} for a bad argument or an IO
     *         failure, so the caller can leave the switch where it was instead of lying.
     */
    public static boolean setActive(File gameDataDir, String uuid, String version, boolean active) {
        if (gameDataDir == null || uuid == null || uuid.trim().isEmpty()) return false;
        String key = uuid.trim().toLowerCase(Locale.ROOT);
        File global = globalFile(gameDataDir);

        List<JsonObject> kept = new ArrayList<>();
        JsonArray existing = readGlobalArray(global);
        if (existing != null) {
            for (JsonElement element : existing) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                boolean same = object.has("pack_id")
                        && object.get("pack_id").getAsString().trim().toLowerCase(Locale.ROOT).equals(key);
                if (same) continue;
                kept.add(object);
            }
        }

        if (active) {
            JsonObject entry = new JsonObject();
            entry.addProperty("pack_id", key);
            entry.addProperty("version", version == null || version.isEmpty() ? "1.0.0" : version);
            kept.add(entry);
        }

        try {
            writeGlobal(global, kept);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static File globalFile(File gameDataDir) {
        if (gameDataDir == null) return null;
        return new File(new File(gameDataDir, MINECRAFT_PE_DIR), GLOBAL_RESOURCE_PACKS);
    }

    private static JsonArray readGlobalArray(File global) {
        if (global == null || !global.isFile()) return null;
        try (FileReader reader = new FileReader(global)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonArray()) {
                return parsed.getAsJsonArray();
            }
        } catch (Exception ignored) {
            // A corrupt list is treated as empty and rewritten by the next toggle; keeping
            // unparseable bytes would stop the game from reading any global pack.
        }
        return null;
    }

    private static void writeGlobal(File global, List<JsonObject> entries) throws IOException {
        File parent = global.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Failed to create " + parent);
        }
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);

        File temp = new File(parent, global.getName() + ".chimeralauncher_tmp");
        try (FileWriter writer = new FileWriter(temp, false)) {
            GSON.toJson(array, writer);
        }
        try {
            Files.move(temp.toPath(), global.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), global.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The human-readable pack name from its manifest header, falling back to the folder name. */
    private static String readPackName(File pack) {
        try {
            File manifest = pack.isDirectory()
                    ? new File(pack, "manifest.json")
                    : null;
            if (manifest != null && manifest.isFile()) {
                try (FileReader reader = new FileReader(manifest)) {
                    JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject header = document.getAsJsonObject("header");
                    if (header != null && header.has("name")) {
                        String name = header.get("name").getAsString().trim();
                        if (!name.isEmpty()) return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return pack.getName();
    }

    /** Convenience for callers that only need the count of active packs. */
    public static int activeCount(File gameDataDir) {
        return activeUuids(gameDataDir).size();
    }

    /** Unmodifiable empty list, so callers can avoid a null check. */
    public static List<PackEntry> emptyList() {
        return Collections.emptyList();
    }
}
