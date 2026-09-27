package org.chimeramc.client.core.antegg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed and validated {@code egg.json} manifest for the .AntEgg mod format.
 *
 * <p>The manifest is the contract between a mod author and the loader. Validation happens here,
 * once, so the extractor and the loaders can assume every field they read is present and sane.
 * A manifest that fails validation yields {@link Result#error} rather than a half-populated
 * object — a partially valid mod would only fail later, at {@code dlopen} time, with a message
 * that says nothing about the manifest.
 */
public final class AntEggManifest {

    /** Mods that ship a C++ shared library. */
    public static final String TYPE_NATIVE = "native";
    /** Mods written in Lua, run by the embedded VM. */
    public static final String TYPE_SCRIPT = "script";

    public final String name;
    public final String version;
    public final String author;
    public final String type;
    public final String entryPoint;
    public final List<String> dependencies;
    public final String description;

    private AntEggManifest(String name, String version, String author, String type,
                           String entryPoint, List<String> dependencies, String description) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.type = type;
        this.entryPoint = entryPoint;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.description = description;
    }

    /** A stable id derived from the manifest name, used for the sandbox and instance folders. */
    public String id() {
        return slug(name);
    }

    public boolean isNative() {
        return TYPE_NATIVE.equals(type);
    }

    public boolean isScript() {
        return TYPE_SCRIPT.equals(type);
    }

    /** The outcome of parsing: exactly one of {@code manifest} / {@code error} is non-null. */
    public static final class Result {
        public final AntEggManifest manifest;
        public final String error;

        private Result(AntEggManifest manifest, String error) {
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

    /**
     * Parses a manifest from raw JSON text using a minimal, dependency-free reader.
     *
     * <p>Deliberately not Gson: an .AntEgg file is untrusted input, and the fields are a fixed,
     * flat set of strings and one string array. Reading them directly keeps the accepted grammar
     * narrow (no nested objects, no numbers where a string is expected), which is the point of
     * validating an import format. Content copied inline into a ZIP entry is exactly the place a
     * lenient parser invites surprises.
     */
    public static Result parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return error("egg.json is empty");
        }
        JsonValue root;
        try {
            root = new JsonReader(json).read();
        } catch (RuntimeException e) {
            return error("egg.json is not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof JsonValue.Object)) {
            return error("egg.json must contain a JSON object");
        }
        JsonValue.Object obj = (JsonValue.Object) root;

        String name = obj.getString("name");
        String type = obj.getString("type");
        String entry = obj.getString("entry_point");
        String version = obj.getString("version");
        String author = obj.getString("author");
        String description = obj.getString("description");

        List<String> dependencies = new ArrayList<>();
        JsonValue deps = obj.get("dependencies");
        if (deps != null) {
            if (!(deps instanceof JsonValue.Array)) {
                return error("dependencies must be an array of mod names");
            }
            for (JsonValue item : ((JsonValue.Array) deps).items) {
                if (!(item instanceof JsonValue.String)) {
                    return error("each dependency must be a string");
                }
                String dep = ((JsonValue.String) item).value.trim();
                if (!dep.isEmpty()) dependencies.add(dep);
            }
        }

        if (name == null || name.trim().isEmpty()) {
            return error("missing required field: name");
        }
        if (author == null || author.trim().isEmpty()) {
            return error("missing required field: author");
        }
        if (version == null || !isSemanticVersion(version.trim())) {
            return error("version must be a semantic version such as 1.0.0");
        }
        if (type == null || (!TYPE_NATIVE.equals(type) && !TYPE_SCRIPT.equals(type))) {
            return error("type must be \"native\" or \"script\"");
        }
        if (entry == null || entry.trim().isEmpty()) {
            return error("missing required field: entry_point");
        }
        String normalizedEntry = entry.trim().replace('\\', '/');
        if (normalizedEntry.startsWith("/") || normalizedEntry.contains("..")) {
            return error("entry_point must stay inside the package");
        }
        if (TYPE_NATIVE.equals(type) && !normalizedEntry.toLowerCase().endsWith(".so")) {
            return error("a native mod's entry_point must be a .so file");
        }
        if (TYPE_SCRIPT.equals(type) && !normalizedEntry.toLowerCase().endsWith(".lua")) {
            return error("a script mod's entry_point must be a .lua file");
        }

        return new Result(new AntEggManifest(
                name.trim(), version.trim(), author.trim(), type,
                normalizedEntry, dependencies, description == null ? "" : description.trim()), null);
    }

    /** True for {@code major.minor.patch}, optionally with a pre-release/build suffix. */
    static boolean isSemanticVersion(String value) {
        if (value == null || value.isEmpty()) return false;
        int coreEnd = value.length();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '-' || c == '+') {
                coreEnd = i;
                break;
            }
        }
        String core = value.substring(0, coreEnd);
        String[] parts = core.split("\\.", -1);
        if (parts.length != 3) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
        }
        return true;
    }

    /** Lowercase, dash-separated id from a display name; safe as a single path segment. */
    static String slug(String value) {
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

    // --- minimal JSON reader ---------------------------------------------------------------

    /** Closed set of JSON shapes this reader produces; only the ones a manifest uses. */
    abstract static class JsonValue {
        static final class String extends JsonValue {
            final java.lang.String value;

            String(java.lang.String value) {
                this.value = value;
            }
        }

        static final class Array extends JsonValue {
            final List<JsonValue> items = new ArrayList<>();
        }

        static final class Object extends JsonValue {
            final java.util.Map<java.lang.String, JsonValue> fields = new java.util.LinkedHashMap<>();

            JsonValue get(java.lang.String key) {
                return fields.get(key);
            }

            java.lang.String getString(java.lang.String key) {
                JsonValue value = fields.get(key);
                if (value instanceof JsonValue.String) {
                    return ((JsonValue.String) value).value;
                }
                return null;
            }
        }
    }

    /**
     * A tiny recursive-descent JSON reader. It rejects anything outside the manifest grammar
     * (numbers, booleans, nulls) rather than coercing it, so a type error in the manifest is
     * reported instead of silently becoming a default.
     */
    static final class JsonReader {
        private final java.lang.String src;
        private int pos;

        JsonReader(java.lang.String src) {
            this.src = src;
        }

        JsonValue read() {
            skipWhitespace();
            JsonValue value = readValue();
            skipWhitespace();
            if (pos != src.length()) {
                throw new IllegalArgumentException("unexpected trailing content");
            }
            return value;
        }

        private JsonValue readValue() {
            skipWhitespace();
            if (pos >= src.length()) throw new IllegalArgumentException("unexpected end of input");
            char c = src.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return new JsonValue.String(readString());
                default:
                    throw new IllegalArgumentException("unsupported value at " + pos);
            }
        }

        private JsonValue.Object readObject() {
            JsonValue.Object obj = new JsonValue.Object();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return obj;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw new IllegalArgumentException("expected object key at " + pos);
                java.lang.String key = readString();
                skipWhitespace();
                if (peek() != ':') throw new IllegalArgumentException("expected ':' at " + pos);
                pos++;
                obj.fields.put(key, readValue());
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                } else if (next == '}') {
                    pos++;
                    return obj;
                } else {
                    throw new IllegalArgumentException("expected ',' or '}' at " + pos);
                }
            }
        }

        private JsonValue.Array readArray() {
            JsonValue.Array array = new JsonValue.Array();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return array;
            }
            while (true) {
                array.items.add(readValue());
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                } else if (next == ']') {
                    pos++;
                    return array;
                } else {
                    throw new IllegalArgumentException("expected ',' or ']' at " + pos);
                }
            }
        }

        private java.lang.String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("dangling escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("bad \\u escape");
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private char peek() {
            if (pos >= src.length()) throw new IllegalArgumentException("unexpected end of input");
            return src.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    return;
                }
            }
        }
    }
}
