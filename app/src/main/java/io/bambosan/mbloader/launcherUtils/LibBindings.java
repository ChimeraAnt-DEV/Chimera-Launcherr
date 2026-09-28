package io.bambosan.mbloader.launcherUtils;

import android.util.Log;

/**
 * JNI bindings for mtbinloader2 (mbl2) - shader/materialbin compatibility fixer.
 * This library fixes resource-pack materialbin compatibility across Bedrock versions.
 * Only for 64-bit (arm64-v8a) instances.
 */
public class LibBindings {
    private static final boolean LOADED;

    static {
        boolean loaded = false;
        try {
            // mtbinloader2 is only bundled for 64-bit installs (arm64-v8a);
            // on a 32-bit launcher build it simply isn't present, so degrade gracefully
            // instead of letting a static initializer crash the whole process.
            System.loadLibrary("mtbinloader2");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w("LibBindings", "mtbinloader2 unavailable—materialbin autofix disabled", e);
        }
        LOADED = loaded;
    }

    /**
     * Whether the native fixer actually loaded.
     *
     * <p>The bundled {@code arm64-v8a} library is a prebuilt drop-in, so on an unusual build it
     * may be absent while every Java call still compiles. Callers check this before reporting a
     * fixer as "loaded" in the launch log, so the log cannot claim work an unavailable library
     * never did.
     */
    public static boolean isAvailable() {
        return LOADED;
    }

    /**
     * Set autofix versions for shader compatibility.
     * @param minVersion Minimum Bedrock version string (e.g., "1.20.0")
     * @param maxVersion Maximum Bedrock version string (e.g., "1.21.0")
     */
    public static native void setAutofixVersions(String minVersion, String maxVersion);

    /**
     * Enable or disable lightmap autofixer.
     * @param enabled true to enable, false to disable
     */
    public static native void setLightmapAutofixer(boolean enabled);

    /**
     * Enable or disable texture LOD autofixer.
     * @param enabled true to enable, false to disable
     */
    public static native void setTextureLodAutofixer(boolean enabled);

    /**
     * Supply a known-good materialbin to use in place of a version's own.
     *
     * <p>This is the fixer's escape hatch: when the shipped autofix cannot map a version's
     * material bins, a player can drop in a compatible file and the loader prefers it. It is
     * exported by the native library but was never bound, so nothing could reach it.
     *
     * @param path absolute path to the materialbin file (the native layer reads it itself)
     */
    public static native void addCustomFile(String path);

    /**
     * Applies the autofix ranges for a concrete Bedrock version.
     *
     * <p>The fixer needs the version range up front; without a call it has no idea which
     * material-bin revision it is looking at, and its range-gated fixes never engage. The
     * instance's own version is passed as both ends of the range, which is the only honest
     * answer available offline: the fixer then sees exactly one version to target rather than
     * an invented window that could suppress a fix the game needs.
     *
     * <p>No-ops when the native library is missing or the version is blank, so an unreadable
     * version degrades to "no autofix" instead of a bogus range.
     */
    public static void applyAutofixVersions(String bedrockVersion) {
        if (!LOADED) return;
        String version = normalize(bedrockVersion);
        if (version.isEmpty()) return;
        try {
            setAutofixVersions(version, version);
        } catch (Throwable t) {
            Log.w("LibBindings", "Failed to set autofix versions for " + version, t);
        }
    }

    /**
     * Strips the channel suffix some version strings carry so the fixer sees a plain number.
     *
     * <p>Delegates to {@link org.chimeramc.client.core.minecraft.VersionCodeNormalizer} so the
     * rule is JVM-testable: this class cannot be instantiated under a plain unit test because its
     * static initializer loads a native library.
     */
    static String normalize(String bedrockVersion) {
        return org.chimeramc.client.core.minecraft.VersionCodeNormalizer.normalize(bedrockVersion);
    }
}
