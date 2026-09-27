package org.chimeramc.client.core.antegg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads, validates and extracts a {@code .AntEgg} package.
 *
 * <p>A .AntEgg is a ZIP archive with a custom extension whose root holds {@code egg.json} plus the
 * mod's files. This class owns the two things that must not be improvised at each call site: the
 * manifest is validated before anything is written, and every entry is extracted to a path that is
 * proven to stay inside the sandbox — a ZIP entry name is attacker-controlled and
 * {@code ../../databases/...} in an entry name is the classic archive escape.
 */
public final class AntEggPackage {

    public static final String EXTENSION = ".antegg";
    public static final String MANIFEST_NAME = "egg.json";
    /** Guards against a small archive inflating into an unbounded write. */
    private static final long MAX_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;

    private AntEggPackage() {}

    /** The outcome of an inspect/extract, carrying either a manifest or a human-readable reason. */
    public static final class Result {
        public final AntEggManifest manifest;
        public final File directory;
        public final String error;

        private Result(AntEggManifest manifest, File directory, String error) {
            this.manifest = manifest;
            this.directory = directory;
            this.error = error;
        }

        public boolean isValid() {
            return manifest != null && error == null;
        }
    }

    private static Result error(String message) {
        return new Result(null, null, message);
    }

    public static boolean looksLikeAntEgg(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(EXTENSION);
    }

    /** Reads and validates the manifest without extracting anything. */
    public static Result inspect(File archive) {
        if (archive == null || !archive.isFile()) {
            return error("package file does not exist");
        }
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifestEntry = findManifest(zip);
            if (manifestEntry == null) {
                return error("package has no " + MANIFEST_NAME);
            }
            String json;
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                json = readAll(in);
            }
            AntEggManifest.Result parsed = AntEggManifest.parse(json);
            if (!parsed.isValid()) {
                return error(parsed.error);
            }
            return new Result(parsed.manifest, null, null);
        } catch (IOException e) {
            return error("could not read package: " + e.getMessage());
        }
    }

    /**
     * Extracts a package into {@code sandboxRoot/<mod id>/}, replacing any previous copy.
     *
     * <p>The extraction is staged into a sibling directory and moved into place only after the
     * whole archive has been written, so a truncated or hostile archive cannot leave a half-mod
     * that the discovery scan would then treat as installed.
     *
     * @param sandboxRoot directory the extracted mods live under (never the archive's own tree)
     */
    public static Result extract(File archive, File sandboxRoot) {
        Result inspected = inspect(archive);
        if (!inspected.isValid()) {
            return inspected;
        }
        AntEggManifest manifest = inspected.manifest;

        File stageDir = new File(sandboxRoot, "." + manifest.id() + ".extracting");
        File targetDir = new File(sandboxRoot, manifest.id());
        deleteRecursively(stageDir);
        if (!stageDir.mkdirs()) {
            return error("could not create sandbox directory: " + stageDir.getAbsolutePath());
        }

        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() > MAX_ENTRIES) {
                return error("package has too many entries");
            }
            long totalWritten = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.isEmpty()) continue;
                File target = safeResolve(stageDir, name);
                if (target == null) {
                    deleteRecursively(stageDir);
                    return error("package entry escapes the sandbox: " + name);
                }
                if (entry.isDirectory()) {
                    if (!target.mkdirs() && !target.isDirectory()) {
                        deleteRecursively(stageDir);
                        return error("could not create directory: " + name);
                    }
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                    deleteRecursively(stageDir);
                    return error("could not create directory for: " + name);
                }
                try (InputStream in = zip.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        totalWritten += read;
                        if (totalWritten > MAX_UNCOMPRESSED_BYTES) {
                            out.close();
                            deleteRecursively(stageDir);
                            return error("package expands beyond the size limit");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }

            if (!new File(stageDir, manifest.entryPoint).isFile()) {
                deleteRecursively(stageDir);
                return error("entry_point is missing from the package: " + manifest.entryPoint);
            }

            deleteRecursively(targetDir);
            if (!stageDir.renameTo(targetDir)) {
                deleteRecursively(stageDir);
                return error("could not move the extracted mod into place");
            }
            return new Result(manifest, targetDir, null);
        } catch (IOException e) {
            deleteRecursively(stageDir);
            return error("could not extract package: " + e.getMessage());
        }
    }

    private static ZipEntry findManifest(ZipFile zip) {
        ZipEntry exact = zip.getEntry(MANIFEST_NAME);
        if (exact != null) return exact;
        // Some tools nest the package one folder deep; accept a single "foo/egg.json".
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith("/" + MANIFEST_NAME)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Resolves an archive entry name under {@code root}, or null when it would escape.
     *
     * <p>The check is done on canonical paths rather than by rejecting {@code ..} textually: a
     * symlinked parent or an odd encoding can defeat a string test, but the canonical path is what
     * the filesystem will actually open.
     */
    private static File safeResolve(File root, String entryName) {
        try {
            File rootCanonical = root.getCanonicalFile();
            File resolved = new File(rootCanonical, entryName).getCanonicalFile();
            String rootPath = rootCanonical.getPath();
            String resolvedPath = resolved.getPath();
            if (!resolvedPath.equals(rootPath) && !resolvedPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return resolved;
        } catch (IOException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Deletes a tree; failure is reported through the return value for callers that care. */
    public static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }

    /** Lists the mod ids currently present in a sandbox, for the loader's discovery step. */
    public static List<String> installedIds(File sandboxRoot) {
        List<String> ids = new ArrayList<>();
        File[] children = sandboxRoot == null ? null : sandboxRoot.listFiles();
        if (children == null) return ids;
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) {
                ids.add(child.getName());
            }
        }
        return ids;
    }

    /** Reads a mod's manifest back out of an extracted sandbox directory. */
    public static Result readExtracted(File modDirectory) {
        if (modDirectory == null || !modDirectory.isDirectory()) {
            return error("extracted mod directory does not exist");
        }
        File manifestFile = new File(modDirectory, MANIFEST_NAME);
        if (!manifestFile.isFile()) {
            return error("extracted mod has no " + MANIFEST_NAME);
        }
        try (InputStream in = new FileInputStream(manifestFile)) {
            AntEggManifest.Result parsed = AntEggManifest.parse(readAll(in));
            if (!parsed.isValid()) {
                return error(parsed.error);
            }
            return new Result(parsed.manifest, modDirectory, null);
        } catch (IOException e) {
            return error("could not read manifest: " + e.getMessage());
        }
    }
}
