package org.chimeramc.client.core.javabridge;

import java.util.List;

/**
 * Builds the prompt sent to the LLM, and interprets its answer.
 *
 * <p>Kept separate from the network client so the exact instructions — and, more importantly, the
 * rule that the model must answer in the two-part format the porter can parse — are unit-testable
 * without a network call. The model is asked to choose Lua for simple tweaks and C++ for anything
 * that needs real behaviour, mirroring the spec's "C++ (for complex mods) or Lua (for simple
 * tweaks)".
 */
public final class PortPrompt {

    /** Which target language the model chose. */
    public static final String LANG_LUA = "lua";
    public static final String LANG_CPP = "cpp";

    /** The parsed answer: a target language and the generated entry-point source. */
    public static final class Answer {
        public final String language;
        public final String source;
        /** The model's one-line explanation of what it implemented. */
        public final String summary;

        Answer(String language, String source, String summary) {
            this.language = language;
            this.source = source;
            this.summary = summary;
        }

        public boolean isLua() {
            return LANG_LUA.equals(language);
        }

        public boolean isCpp() {
            return LANG_CPP.equals(language);
        }
    }

    private PortPrompt() {}

    /**
     * The system instruction.
     *
     * <p>It states the honesty constraints the spec requires: the output is a reimplementation, it
     * must not claim to be the original, and it must not produce cheat behaviour. The model is told
     * the target API surface explicitly so it does not invent one.
     */
    public static String systemInstruction() {
        return "You are a porting assistant that reimplements Java Edition Minecraft mods for a "
                + "Bedrock client mod runtime called AntEgg. You do NOT translate bytecode; you "
                + "write a new, small implementation with equivalent visible behaviour. "
                + "You must obey these rules:\n"
                + "1. Output ONLY a reimplementation. Never copy decompiled Java verbatim.\n"
                + "2. Choose Lua for simple tweaks (a command, a small tick effect, a recipe). "
                + "Choose C++ for anything needing real behaviour or state.\n"
                + "3. Never implement cheat behaviour: no kill aura, aimbot, reach/hit-range "
                + "extension, fly/noclip, speed, x-ray/wallhack, auto-clicker or duplication. "
                + "If the source does this, output a comment refusing and nothing else.\n"
                + "4. Do not fabricate game APIs. Use only the AntEgg target API given to you; "
                + "if an API is missing, say so in the summary instead of inventing one.\n"
                + "5. Answer in exactly this format, with no other text:\n"
                + "LANG: lua   (or)  LANG: cpp\n"
                + "SUMMARY: <one sentence>\n"
                + "===CODE===\n"
                + "<the full entry-point source>";
    }

    /**
     * Builds the user prompt from decompiled source, the mod manifest and the API mapping.
     *
     * <p>The decompiled source is truncated to a bounded size: a large mod's full decompilation
     * would blow past any model's context and cost, and the porter's job is the top-level
     * behaviour, not every helper. Truncation is explicit in the prompt so the model knows it is
     * seeing a partial view.
     */
    public static String userPrompt(String decompiledSource, JavaModManifest manifest,
                                    List<String> mappedTargets, int maxSourceChars) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Port this Java Edition mod to AntEgg.\n\n");
        if (manifest != null) {
            sb.append("Mod id: ").append(manifest.id).append('\n');
            sb.append("Mod name: ").append(manifest.name).append('\n');
            sb.append("Loader: ").append(manifest.loader).append('\n');
            if (!manifest.description.isEmpty()) {
                sb.append("Description: ").append(manifest.description).append('\n');
            }
            sb.append('\n');
        }
        if (mappedTargets != null && !mappedTargets.isEmpty()) {
            sb.append("Use these AntEgg target APIs where relevant:\n");
            for (String target : mappedTargets) {
                sb.append("- ").append(target).append('\n');
            }
            sb.append('\n');
        }
        sb.append("Decompiled source");
        String source = decompiledSource == null ? "" : decompiledSource;
        if (source.length() > maxSourceChars) {
            source = source.substring(0, maxSourceChars)
                    + "\n// ... truncated for length ...\n";
            sb.append(" (truncated):\n");
        } else {
            sb.append(":\n");
        }
        sb.append("```java\n").append(source).append("\n```\n");
        return sb.toString();
    }

    /**
     * Parses the model's answer.
     *
     * <p>A model that ignores the format is treated as a failed port, not coerced: taking the
     * whole reply as source would ship the model's prose as a mod. The parser is strict about the
     * {@code ===CODE===} fence and lenient about the {@code LANG} line's spelling.
     */
    public static Answer parse(String reply) {
        if (reply == null || reply.trim().isEmpty()) return null;
        String language = null;
        String summary = "";
        int codeIndex = reply.indexOf("===CODE===");
        if (codeIndex < 0) {
            // Some models fence the code without the marker; accept a single fenced block.
            String fenced = extractFence(reply);
            if (fenced == null) return null;
            language = guessLanguage(reply, fenced);
            return new Answer(language, fenced, "");
        }
        String header = reply.substring(0, codeIndex);
        String source = reply.substring(codeIndex + "===CODE===".length()).trim();
        for (String line : header.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "LANG:", 0, 5)) {
                String value = trimmed.substring(5).trim().toLowerCase(java.util.Locale.ROOT);
                if (value.startsWith("lua")) language = LANG_LUA;
                else if (value.startsWith("cpp") || value.startsWith("c++")) language = LANG_CPP;
            } else if (trimmed.regionMatches(true, 0, "SUMMARY:", 0, 8)) {
                summary = trimmed.substring(8).trim();
            }
        }
        if (source.isEmpty()) return null;
        if (language == null) language = guessLanguage(reply, source);
        return new Answer(language, source, summary);
    }

    private static String extractFence(String reply) {
        int start = reply.indexOf("```");
        if (start < 0) return null;
        int firstNewline = reply.indexOf('\n', start);
        if (firstNewline < 0) return null;
        int end = reply.indexOf("```", firstNewline);
        if (end < 0) return null;
        String body = reply.substring(firstNewline + 1, end).trim();
        return body.isEmpty() ? null : body;
    }

    private static String guessLanguage(String reply, String source) {
        String lower = reply.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("lang: lua") || lower.contains("lang:lua")) return LANG_LUA;
        if (lower.contains("lang: cpp") || lower.contains("lang: c++")) return LANG_CPP;
        // Fall back to a content sniff: a Lua mod references the `mod` table; C++ includes headers.
        if (source.contains("#include") || source.contains("extern \"C\"")) return LANG_CPP;
        return LANG_LUA;
    }

    /** The entry-point file name for a generated mod of the given language. */
    public static String entryPointName(String language, String modId) {
        String id = modId == null || modId.isEmpty() ? "ported_mod" : modId;
        return LANG_CPP.equals(language) ? id + ".so" : id + ".lua";
    }
}
