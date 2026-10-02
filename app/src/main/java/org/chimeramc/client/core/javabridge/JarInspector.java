package org.chimeramc.client.core.javabridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the structure of an imported Java Edition mod jar without decompiling anything.
 *
 * <p>This is the fact-gathering step: which loader metadata the jar carries, how many classes it
 * ships, whether it bundles mixins, whether it ships native libraries, and what the manifest says.
 * Everything here is derived from the archive's central directory and its manifest entries, which
 * is what makes the portability score and the "not supported" decisions possible <em>before</em>
 * any decompilation or network call.
 *
 * <p>Pure Java: {@link ZipFile} and {@link File} only, so the whole inspection runs in a unit test
 * against a hand-built jar.
 */
public final class JarInspector {

    /** A jar whose entry names were too many or too large is refused rather than read fully. */
    private static final int MAX_ENTRIES = 20000;
    private static final long MAX_MANIFEST_BYTES = 1024 * 1024;

    /** What the inspection found. */
    public static final class Inspection {
        public final JavaModManifest manifest;
        /** Total number of {@code .class} entries in the jar. */
        public final int classCount;
        /** Class entries that belong to the mod itself (not a shaded library), best effort. */
        public final int ownClassCount;
        /** True when the jar ships a mixin configuration and mixin classes. */
        public final boolean hasMixins;
        /** Native library entries, e.g. {@code lib/x86_64/libfoo.so}; empty when none. */
        public final List<String> nativeLibraries;
        /** True when the jar carries an {@code assets/} tree with textures/models. */
        public final boolean hasClientAssets;
        /** True when the jar carries data-driven files ({@code data/**}). */
        public final boolean hasDataFiles;
        /** The package prefixes the mod's own classes live under; empty when unknown. */
        public final List<String> packageRoots;
        /** Why the jar could not be read at all, or null. */
        public final String error;

        Inspection(JavaModManifest manifest, int classCount, int ownClassCount, boolean hasMixins,
                   List<String> nativeLibraries, boolean hasClientAssets, boolean hasDataFiles,
                   List<String> packageRoots, String error) {
            this.manifest = manifest;
            this.classCount = classCount;
            this.ownClassCount = ownClassCount;
            this.hasMixins = hasMixins;
            this.nativeLibraries = Collections.unmodifiableList(nativeLibraries);
            this.hasClientAssets = hasClientAssets;
            this.hasDataFiles = hasDataFiles;
            this.packageRoots = Collections.unmodifiableList(packageRoots);
            this.error = error;
        }

        public boolean isValid() {
            return error == null;
        }
    }

    private JarInspector() {}

    /**
     * Inspects a jar file.
     *
     * <p>A jar that cannot be opened or has no recognisable metadata still returns a valid
     * inspection with an empty manifest; the scorer treats "no metadata" as a low-portability
     * signal rather than an error, because a user may still want to see why.
     */
    public static Inspection inspect(File jar) {
        if (jar == null || !jar.isFile()) {
            return new Inspection(null, 0, 0, false, new ArrayList<>(), false, false,
                    new ArrayList<>(), "mod file does not exist");
        }
        int classCount = 0;
        int ownClassCount = 0;
        boolean hasMixins = false;
        boolean hasClientAssets = false;
        boolean hasDataFiles = false;
        List<String> nativeLibraries = new ArrayList<>();
        List<String> packageRoots = new ArrayList<>();
        String fabricJson = null;
        String forgeToml = null;
        String mcmodInfo = null;
        boolean sawMixinConfig = false;
        int entryCount = 0;

        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (++entryCount > MAX_ENTRIES) {
                    return new Inspection(null, classCount, ownClassCount, hasMixins,
                            nativeLibraries, hasClientAssets, hasDataFiles, packageRoots,
                            "the jar has too many entries to inspect");
                }
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) continue;

                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".class")) {
                    classCount++;
                    if (isOwnClass(name)) {
                        ownClassCount++;
                        String root = packageRoot(name);
                        if (root != null && !packageRoots.contains(root)) {
                            packageRoots.add(root);
                        }
                    }
                    // A class in a "mixin" package is a mixin even without a config entry; the
                    // config branch below is the other, more explicit signal.
                    if (lower.contains("mixin")) hasMixins = true;
                } else if (lower.endsWith(".so") || lower.endsWith(".dll")
                        || lower.endsWith(".dylib") || lower.endsWith(".jnilib")) {
                    nativeLibraries.add(name);
                } else if (name.equals("fabric.mod.json")) {
                    fabricJson = readText(zip, entry);
                } else if (lower.endsWith("meta-inf/mods.toml")) {
                    forgeToml = readText(zip, entry);
                } else if (lower.endsWith("meta-inf/mcmod.info")) {
                    mcmodInfo = readText(zip, entry);
                } else if (lower.contains(".mixins.json")) {
                    sawMixinConfig = true;
                    hasMixins = true;
                } else if (lower.startsWith("assets/")) {
                    hasClientAssets = true;
                } else if (lower.startsWith("data/")) {
                    hasDataFiles = true;
                }
            }
        } catch (IOException e) {
            return new Inspection(null, 0, 0, false, new ArrayList<>(), false, false,
                    new ArrayList<>(), "could not read the jar: " + e.getMessage());
        }

        if (sawMixinConfig) hasMixins = true;

        JavaModManifest manifest = null;
        String manifestError = null;
        if (fabricJson != null) {
            JavaModManifest.Result result = JavaModManifest.parseFabric(fabricJson);
            if (result.isValid()) manifest = result.manifest;
            else manifestError = result.error;
        }
        if (manifest == null && forgeToml != null) {
            JavaModManifest.Result result = JavaModManifest.parseForge(forgeToml);
            if (result.isValid()) manifest = result.manifest;
            else manifestError = result.error;
        }
        if (manifest == null && mcmodInfo != null) {
            JavaModManifest.Result result = JavaModManifest.parseForge(mcmodInfo);
            if (result.isValid()) manifest = result.manifest;
            else manifestError = result.error;
        }

        return new Inspection(manifest, classCount, ownClassCount, hasMixins, nativeLibraries,
                hasClientAssets, hasDataFiles, packageRoots,
                manifest == null ? manifestError : null);
    }

    /** Heuristic: a mod's own classes are not under a shaded third-party root. */
    private static boolean isOwnClass(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.startsWith("net/minecraft/")
                && !lower.startsWith("net/fabricmc/")
                && !lower.startsWith("net/neoforged/")
                && !lower.startsWith("net/minecraftforge/")
                && !lower.startsWith("org/spongepowered/")
                && !lower.startsWith("com/google/")
                && !lower.startsWith("org/apache/")
                && !lower.startsWith("org/jetbrains/")
                && !lower.startsWith("kotlin/")
                && !lower.startsWith("org/luaj/");
    }

    private static String packageRoot(String classEntry) {
        int slash = classEntry.lastIndexOf('/');
        if (slash < 0) return null;
        // Collapse to the first two path segments: com/example, not com/example/mod/impl.
        String[] parts = classEntry.substring(0, slash).split("/");
        if (parts.length >= 2) return parts[0] + "/" + parts[1];
        return parts[0];
    }

    private static String readText(ZipFile zip, ZipEntry entry) {
        if (entry.getSize() > MAX_MANIFEST_BYTES) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > MAX_MANIFEST_BYTES) return null;
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Lists the class entries of a jar, for the decompiler to work from. Best effort. */
    public static List<String> classEntries(File jar) {
        List<String> classes = new ArrayList<>();
        if (jar == null || !jar.isFile()) return classes;
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.toLowerCase(Locale.ROOT).endsWith(".class")) {
                    classes.add(name);
                }
            }
        } catch (IOException ignored) {
            // An unreadable jar yields an empty list; the caller reports the inspection error.
        }
        return classes;
    }
}
