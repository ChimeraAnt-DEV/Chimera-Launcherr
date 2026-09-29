package org.chimeramc.client.core.mods.inbuilt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named sets of module on/off states ("loadouts") and their JSON form.
 *
 * <p>Pure: no Android types, no SharedPreferences. The manager owns persistence and the menu
 * owns the UI, so the only thing worth unit-testing here -- that a saved set round-trips and
 * that malformed input degrades to nothing rather than throwing -- is testable on the JVM.
 */
public final class ModLoadoutStore {

    /** A named set of module states. Only ids that were explicitly set are stored. */
    public static final class Loadout {
        public final String name;
        /** Insertion-ordered so a round-trip does not reshuffle the diff. */
        public final Map<String, Boolean> states;

        public Loadout(String name, Map<String, Boolean> states) {
            this.name = name == null ? "" : name.trim();
            this.states = states == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(states);
        }

        public int size() {
            return states.size();
        }

        /** True when this loadout would leave the given map exactly as it already is. */
        public boolean matches(Map<String, Boolean> current) {
            if (current == null) return states.isEmpty();
            for (Map.Entry<String, Boolean> entry : states.entrySet()) {
                if (!entry.getValue().equals(current.get(entry.getKey()))) return false;
            }
            return true;
        }
    }

    /**
     * Captures the current states of the given module ids.
     *
     * <p>{@code lookup} returns the resolved enabled flag for an id; nulls in {@code moduleIds}
     * are skipped rather than stored as a spurious key.
     */
    public static Loadout capture(String name, List<String> moduleIds,
                                  java.util.function.Function<String, Boolean> lookup) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        if (moduleIds != null && lookup != null) {
            for (String id : moduleIds) {
                if (id == null || id.isEmpty()) continue;
                Boolean enabled = lookup.apply(id);
                states.put(id, enabled != null && enabled);
            }
        }
        return new Loadout(name, states);
    }

    /**
     * Re-applies a loadout through the given setter.
     *
     * <p>Only the ids the loadout names are touched, so a loadout saved before a new module was
     * added does not silently switch that module off.
     */
    public static void apply(Loadout loadout, java.util.function.BiConsumer<String, Boolean> setter) {
        if (loadout == null || setter == null) return;
        for (Map.Entry<String, Boolean> entry : loadout.states.entrySet()) {
            setter.accept(entry.getKey(), entry.getValue());
        }
    }

    public static List<String> names(List<Loadout> loadouts) {
        List<String> names = new ArrayList<>();
        if (loadouts == null) return names;
        for (Loadout loadout : loadouts) {
            if (loadout != null && !loadout.name.isEmpty()) names.add(loadout.name);
        }
        return names;
    }

    public static Loadout find(List<Loadout> loadouts, String name) {
        if (loadouts == null || name == null) return null;
        for (Loadout loadout : loadouts) {
            if (loadout != null && name.equals(loadout.name)) return loadout;
        }
        return null;
    }

    /** Replaces or appends by name, keeping the list order for an existing name. */
    public static List<Loadout> upsert(List<Loadout> loadouts, Loadout loadout) {
        List<Loadout> result = loadouts == null ? new ArrayList<>() : new ArrayList<>(loadouts);
        if (loadout == null || loadout.name.isEmpty()) return result;
        for (int i = 0; i < result.size(); i++) {
            if (loadout.name.equals(result.get(i).name)) {
                result.set(i, loadout);
                return result;
            }
        }
        result.add(loadout);
        return result;
    }

    public static List<Loadout> remove(List<Loadout> loadouts, String name) {
        List<Loadout> result = new ArrayList<>();
        if (loadouts == null) return result;
        for (Loadout loadout : loadouts) {
            if (loadout != null && !loadout.name.equals(name)) result.add(loadout);
        }
        return result;
    }

    // ---- JSON --------------------------------------------------------------------------------

    private static final int MAX_NAME_LENGTH = 40;
    private static final int MAX_LOADOUTS = 24;
    private static final int MAX_MODULES = 200;

    public static String toJson(List<Loadout> loadouts) {
        StringBuilder sb = new StringBuilder("[");
        if (loadouts != null) {
            boolean firstOuter = true;
            for (Loadout loadout : loadouts) {
                if (loadout == null || loadout.name.isEmpty()) continue;
                if (!firstOuter) sb.append(',');
                firstOuter = false;
                sb.append("{\"name\":\"").append(escape(loadout.name)).append("\",\"mods\":{");
                boolean firstInner = true;
                for (Map.Entry<String, Boolean> entry : loadout.states.entrySet()) {
                    if (!firstInner) sb.append(',');
                    firstInner = false;
                    sb.append('"').append(escape(entry.getKey())).append("\":")
                            .append(entry.getValue() ? "true" : "false");
                }
                sb.append("}}");
            }
        }
        return sb.append(']').toString();
    }

    public static List<Loadout> fromJson(String json) {
        List<Loadout> result = new ArrayList<>();
        if (json == null) return result;
        int i = 0;
        int n = json.length();
        while (i < n && result.size() < MAX_LOADOUTS) {
            int objStart = json.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = matchingBrace(json, objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart, objEnd + 1);
            Loadout parsed = parseLoadout(obj);
            if (parsed != null) result.add(parsed);
            i = objEnd + 1;
        }
        return result;
    }

    private static Loadout parseLoadout(String obj) {
        String name = readStringField(obj, "name");
        if (name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;
        if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);

        Map<String, Boolean> states = new LinkedHashMap<>();
        int modsStart = obj.indexOf("\"mods\"");
        if (modsStart >= 0) {
            int braceStart = obj.indexOf('{', modsStart);
            int braceEnd = braceStart < 0 ? -1 : matchingBrace(obj, braceStart);
            if (braceStart >= 0 && braceEnd > braceStart) {
                parseStates(obj.substring(braceStart + 1, braceEnd), states);
            }
        }
        return new Loadout(name, states);
    }

    private static void parseStates(String body, Map<String, Boolean> out) {
        int i = 0;
        while (i < body.length() && out.size() < MAX_MODULES) {
            int keyStart = body.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = body.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = unescape(body.substring(keyStart + 1, keyEnd));
            int colon = body.indexOf(':', keyEnd);
            if (colon < 0) break;
            int valueStart = colon + 1;
            while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
                valueStart++;
            }
            boolean value = body.startsWith("true", valueStart);
            if (!key.isEmpty()) out.put(key, value);
            int comma = body.indexOf(',', valueStart);
            if (comma < 0) break;
            i = comma + 1;
        }
    }

    private static String readStringField(String obj, String field) {
        int key = obj.indexOf("\"" + field + "\"");
        if (key < 0) return null;
        int colon = obj.indexOf(':', key);
        if (colon < 0) return null;
        int open = obj.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = open + 1;
        while (close < obj.length()) {
            if (obj.charAt(close) == '"' && obj.charAt(close - 1) != '\\') break;
            close++;
        }
        if (close >= obj.length()) return null;
        return unescape(obj.substring(open + 1, close));
    }

    private static int matchingBrace(String json, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public static List<Loadout> empty() {
        return Collections.emptyList();
    }

    private ModLoadoutStore() {}
}
