package org.chimeramc.client.core.minecraft;

/**
 * Normalises a Bedrock version string for the shader compatibility fixer.
 *
 * <p>The fixer compares numeric parts, so a channel suffix on a version string must not become
 * part of a part: handing it {@code "1.21.132.1-preview"} as a version makes its range-gated
 * fixes compare against a value no real build has. Pure and Android-free so the rule is
 * unit-testable without a device — {@code LibBindings} owns the JNI call, not the parsing.
 */
public final class VersionCodeNormalizer {

    private VersionCodeNormalizer() {}

    /**
     * Strips a channel suffix ({@code -preview}, {@code _RC3}, …) and any trailing dot from a
     * version string, leaving the plain numeric form, or {@code ""} when there is none.
     */
    public static String normalize(String version) {
        if (version == null) return "";
        return version.trim().replaceAll("(?i)[^0-9.].*$", "").replaceAll("\\.+$", "");
    }
}
