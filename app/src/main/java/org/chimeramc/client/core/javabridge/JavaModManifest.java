package org.chimeramc.client.core.javabridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The metadata block of an imported Java Edition mod, read from {@code fabric.mod.json} or
 * {@code META-INF/mods.toml} (and the legacy {@code META-INF/mcmod.info}).
 *
 * <p>This is pure text parsing with no Android or archive dependency, so the extraction rules can
 * be pinned by a unit test against realistic fixture text. The archive reader only has to hand the
 * raw bytes of the right entry here.
 *
 * <p>Both formats are accepted because the spec names them; a jar that carries neither is not
 * necessarily broken (a very old or a custom loader mod), so {@link Result#isValid} only requires
 * that a mod id or name could be found, not that a specific loader was detected.
 */
public final class JavaModManifest {

    /** Which loader the mod was built for, as far as the metadata reveals. */
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_FORGE = "forge";
    public static final String LOADER_NEOFORGE = "neoforge";
    public static final String LOADER_UNKNOWN = "unknown";

    public final String id;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final String license;
    public final String loader;
    /** Mod ids this mod declares as required; empty when none were declared. */
    public final List<String> dependencies;
    /** Minecraft version constraints declared by the mod, e.g. {@code >=1.20.1}. */
    public final List<String> minecraftVersions;

    private JavaModManifest(String id, String name, String version, String author,
                            String description, String license, String loader,
                            List<String> dependencies, List<String> minecraftVersions) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.license = license;
        this.loader = loader;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.minecraftVersions = Collections.unmodifiableList(minecraftVersions);
    }

    /** The outcome of parsing: a manifest, or a reason nothing usable was found. */
    public static final class Result {
        public final JavaModManifest manifest;
        public final String error;

        private Result(JavaModManifest manifest, String error) {
            this.manifest = manifest;
            this.error = error;
        }

        public boolean isValid() {
            return manifest != null;
        }
    }

    private static Result error(String message) {
        return new Result(null, message);
    }

    /** Parses {@code fabric.mod.json}. */
    public static Result parseFabric(String json) {
        if (json == null || json.trim().isEmpty()) {
            return error("fabric.mod.json is empty");
        }
        // Gson is on the launcher's runtime and this is a one-shot parse of untrusted text; the
        // fields are read defensively below rather than trusting the shape of the document.
        com.google.gson.JsonObject root;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                return error("fabric.mod.json must contain a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return error("fabric.mod.json is not valid JSON: " + e.getMessage());
        }

        String id = str(root, "id");
        String name = str(root, "name");
        String version = str(root, "version");
        String description = str(root, "description");
        String license = str(root, "license");

        String author = "";
        com.google.gson.JsonElement authors = root.get("authors");
        if (authors != null && authors.isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (com.google.gson.JsonElement element : authors.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    names.add(element.getAsString());
                } else if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
                    names.add(str(element.getAsJsonObject(), "name"));
                }
            }
            author = String.join(", ", names);
        } else if (authors != null && authors.isJsonPrimitive()) {
            author = authors.getAsString();
        }

        List<String> dependencies = new ArrayList<>();
        List<String> minecraftVersions = new ArrayList<>();
        com.google.gson.JsonElement depends = root.get("depends");
        if (depends != null && depends.isJsonObject()) {
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry
                    : depends.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if ("minecraft".equals(key)) {
                    minecraftVersions.addAll(asStrings(entry.getValue()));
                } else if (!"fabricloader".equals(key) && !"fabric".equals(key)
                        && !"java".equals(key)) {
                    dependencies.add(key);
                }
            }
        }

        if (isBlank(id) && isBlank(name)) {
            return error("fabric.mod.json has neither an id nor a name");
        }
        return new Result(new JavaModManifest(
                firstNonBlank(id, slug(name)), firstNonBlank(name, id), version, author,
                description, license, LOADER_FABRIC, dependencies, minecraftVersions), null);
    }

    /** Parses a Forge/NeoForge {@code mods.toml} (or the legacy {@code mcmod.info} JSON). */
    public static Result parseForge(String toml) {
        if (toml == null || toml.trim().isEmpty()) {
            return error("mods.toml is empty");
        }
        String trimmed = toml.trim();
        // mcmod.info is JSON (an array), not TOML; detect it before the section scan.
        if (trimmed.startsWith("[{") || trimmed.startsWith("[\n") || trimmed.startsWith("[ ")
                || trimmed.equals("[]")) {
            return parseMcmodInfo(trimmed);
        }

        // A minimal TOML reader: mods.toml is a flat list of [[mods]] tables with string values.
        // This is deliberately not a general TOML parser -- a nested table would be a grammar the
        // import format does not use, and accepting it would invite surprises from untrusted text.
        String modId = "";
        String displayName = "";
        String version = "";
        String authors = "";
        String description = "";
        String license = "";
        String loader = LOADER_FORGE;
        boolean inModsTable = false;

        for (String rawLine : toml.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                inModsTable = line.startsWith("[[mods]]");
                if (line.contains("neoforge")) loader = LOADER_NEOFORGE;
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = stripQuotes(line.substring(equals + 1).trim());
            switch (key) {
                case "modid":
                    if (inModsTable && modId.isEmpty()) modId = value;
                    break;
                case "displayname":
                    if (inModsTable && displayName.isEmpty()) displayName = value;
                    break;
                case "version":
                    if (inModsTable && version.isEmpty()) version = value;
                    break;
                case "authors":
                    if (inModsTable && authors.isEmpty()) authors = value;
                    break;
                case "description":
                    if (inModsTable && description.isEmpty()) description = value;
                    break;
                case "license":
                    if (inModsTable && license.isEmpty()) license = value;
                    break;
                case "modloader":
                    if (value.toLowerCase(Locale.ROOT).contains("neoforge")) loader = LOADER_NEOFORGE;
                    break;
                default:
                    break;
            }
        }

        if (isBlank(modId) && isBlank(displayName)) {
            return error("mods.toml has no [[mods]] entry with a modId or displayName");
        }
        return new Result(new JavaModManifest(
                firstNonBlank(modId, slug(displayName)), firstNonBlank(displayName, modId),
                version, authors, description, license, loader,
                Collections.emptyList(), Collections.emptyList()), null);
    }

    private static Result parseMcmodInfo(String json) {
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() == 0) {
                return error("mcmod.info must be a non-empty array");
            }
            com.google.gson.JsonObject root = parsed.getAsJsonArray().get(0).getAsJsonObject();
            String id = str(root, "modid");
            String name = str(root, "name");
            String version = str(root, "version");
            String authors = str(root, "authorList");
            String description = str(root, "description");
            if (isBlank(id) && isBlank(name)) {
                return error("mcmod.info has neither a modid nor a name");
            }
            return new Result(new JavaModManifest(
                    firstNonBlank(id, slug(name)), firstNonBlank(name, id), version, authors,
                    description, "", LOADER_FORGE, Collections.emptyList(),
                    Collections.emptyList()), null);
        } catch (RuntimeException e) {
            return error("mcmod.info is not valid JSON: " + e.getMessage());
        }
    }

    /** Lowercase, dash-separated id from a display name; mirrors {@code AntEggManifest.slug}. */
    static String slug(String value) {
        if (value == null) return "mod";
        StringBuilder sb = new StringBuilder(value.length());
        boolean lastDash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash && sb.length() > 0) {
                sb.append('-');
                lastDash = true;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        return sb.length() == 0 ? "mod" : sb.toString();
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static List<String> asStrings(com.google.gson.JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null) return out;
        if (element.isJsonArray()) {
            for (com.google.gson.JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) out.add(item.getAsString());
            }
        } else if (element.isJsonPrimitive()) {
            out.add(element.getAsString());
        }
        return out;
    }

    private static String str(com.google.gson.JsonObject obj, String key) {
        com.google.gson.JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? (second == null ? "" : second) : first;
    }
}
