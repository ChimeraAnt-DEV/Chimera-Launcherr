package org.chimeramc.client.core.javabridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to port mods whose whole purpose is a cheat, and names the pattern that matched.
 *
 * <p>The spec is explicit: "Do not silently port mods with known cheat patterns (kill aura, etc.)
 * — refuse and warn." This detector is deliberately a small, curated keyword list rather than a
 * classifier: a false refusal costs the user a port they could have reviewed, while a false
 * acceptance would generate a client-side cheat from a mod the user may not have realised was one.
 * The bias is therefore toward refusing and telling the user exactly why.
 *
 * <p>It matches on the mod's id, name and description — the text a user actually sees — not on
 * decompiled source, because source is noisy and the identifiers a cheat mod uses are routinely
 * obfuscated. A mod that hides its purpose from its own metadata is out of scope for a keyword
 * check; what this catches is the honest majority that describe what they do.
 */
public final class CheatPatternDetector {

    /** One known cheat pattern and the human-readable label shown in the refusal. */
    public static final class Pattern {
        public final String label;
        final List<String> keywords;

        Pattern(String label, List<String> keywords) {
            this.label = label;
            this.keywords = keywords;
        }
    }

    private static final List<Pattern> PATTERNS = buildPatterns();

    private CheatPatternDetector() {}

    private static List<Pattern> buildPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        patterns.add(new Pattern("kill aura", keywords("killaura", "kill aura", "kill-aura",
                "aura attack", "auraattack", "auto attack entities")));
        patterns.add(new Pattern("reach / hit-range extension", keywords("reach hack", "reachhack",
                "extended reach", "long reach", "range hack", "hit reach")));
        patterns.add(new Pattern("fly / no-clip", keywords("flyhack", "fly hack", "fly-hack",
                "noclip", "no-clip", "no clip", "freecam flight")));
        patterns.add(new Pattern("speed / movement cheat", keywords("speedhack", "speed hack",
                "bhop", "bunny hop", "auto jump cheat", "sprint hack")));
        patterns.add(new Pattern("x-ray / wallhack", keywords("xray", "x-ray", "wallhack",
                "wall hack", "see through walls", "esp hack", "player esp", "entity esp")));
        patterns.add(new Pattern("auto-clicker", keywords("autoclicker", "auto-clicker",
                "auto clicker", "auto-click", "auto click")));
        patterns.add(new Pattern("aim assist / aimbot", keywords("aimbot", "aim bot", "aim-bot",
                "silent aim", "triggerbot", "trigger bot")));
        patterns.add(new Pattern("duplication / item glitch", keywords("dupe glitch", "duping",
                "item duplication exploit", "dupe hack")));
        patterns.add(new Pattern("anti-knockback / no-fall", keywords("antiknockback",
                "anti-knockback", "no knockback", "nofall", "no-fall", "no fall damage")));
        return Collections.unmodifiableList(patterns);
    }

    private static List<String> keywords(String... values) {
        List<String> list = new ArrayList<>(values.length);
        Collections.addAll(list, values);
        return Collections.unmodifiableList(list);
    }

    /** The outcome of a check: clean, or refused with the matched pattern's label. */
    public static final class Verdict {
        public final boolean refused;
        /** The matched pattern label, or null when clean. */
        public final String pattern;

        private Verdict(boolean refused, String pattern) {
            this.refused = refused;
            this.pattern = pattern;
        }

        public boolean isClean() {
            return !refused;
        }
    }

    private static final Verdict CLEAN = new Verdict(false, null);

    /** The "no cheat pattern matched" verdict, for callers that have no manifest to check. */
    public static Verdict clean() {
        return CLEAN;
    }

    /**
     * Checks a mod's identifying text for a known cheat pattern.
     *
     * <p>All three fields are searched because a cheat mod may put the giveaway in any of them;
     * nulls are tolerated so a manifest with no description is still checked.
     */
    public static Verdict check(String id, String name, String description) {
        String haystack = join(id, name, description);
        for (Pattern pattern : PATTERNS) {
            for (String keyword : pattern.keywords) {
                if (haystack.contains(keyword)) {
                    return new Verdict(true, pattern.label);
                }
            }
        }
        return CLEAN;
    }

    /** Convenience overload for a parsed Java mod manifest. */
    public static Verdict check(JavaModManifest manifest) {
        if (manifest == null) return CLEAN;
        return check(manifest.id, manifest.name, manifest.description);
    }

    /** True when the text contains a known cheat pattern. */
    public static boolean isCheat(String id, String name, String description) {
        return check(id, name, description).refused;
    }

    /** The distinct labels this detector knows, for a "what we refuse" help text. */
    public static List<String> knownPatternLabels() {
        List<String> labels = new ArrayList<>(PATTERNS.size());
        for (Pattern pattern : PATTERNS) {
            labels.add(pattern.label);
        }
        return labels;
    }

    private static String join(String id, String name, String description) {
        StringBuilder sb = new StringBuilder();
        appendNormalized(sb, id);
        appendNormalized(sb, name);
        appendNormalized(sb, description);
        return sb.toString();
    }

    private static void appendNormalized(StringBuilder sb, String value) {
        if (value == null) return;
        // Collapse separators so "kill_aura", "kill-aura" and "KillAura" all hit "killaura".
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
        }
        sb.append(' ');
        // Also keep a separator-free copy in the haystack so "kill aura" and "killaura" both match.
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        sb.append(' ');
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
    }
}
