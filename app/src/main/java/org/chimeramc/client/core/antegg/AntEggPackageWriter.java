package org.chimeramc.client.core.antegg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a {@code .AntEgg} package from a manifest and a set of in-memory files.
 *
 * <p>This is the inverse of {@link AntEggPackage} and the last step of the JavaBridge auto-porter:
 * the generated manifest and the generated source are packaged here, then handed to the same
 * {@link AntEggLoader} path an ordinary import uses. Writing the package ourselves — rather than
 * shelling out to {@code zip} or a third-party archive library — keeps the produced archive
 * identical in shape to what the reader expects, which is what makes the round trip testable.
 *
 * <p>The manifest is serialized by this class, not by the caller, so a generated package cannot
 * ship a manifest the parser would reject: every field is emitted through {@link #jsonString} and
 * the result is validated before the archive is written.
 */
public final class AntEggPackageWriter {

    /** The files that make up a package, keyed by their path inside the archive. */
    public static final class PackageFiles {
        private final Map<String, byte[]> files = new LinkedHashMap<>();

        public PackageFiles add(String path, String text) {
            return addBytes(path, text.getBytes(StandardCharsets.UTF_8));
        }

        public PackageFiles addBytes(String path, byte[] bytes) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("package entry path must not be empty");
            }
            files.put(path.replace('\\', '/'), bytes == null ? new byte[0] : bytes);
            return this;
        }

        public boolean isEmpty() {
            return files.isEmpty();
        }

        Map<String, byte[]> entries() {
            return files;
        }
    }

    private AntEggPackageWriter() {}

    /** The outcome of a write: the created file, or a reason the caller can show. */
    public static final class Result {
        public final boolean success;
        public final File file;
        public final String error;

        private Result(boolean success, File file, String error) {
            this.success = success;
            this.file = file;
            this.error = error;
        }

        public static Result ok(File file) {
            return new Result(true, file, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }

    /**
     * Writes {@code destination} as a valid .AntEgg package.
     *
     * <p>The manifest is validated first and the file is only created once the manifest is
     * accepted, so a bad generation attempt does not leave a half-written archive behind.
     */
    public static Result write(AntEggManifest manifest, PackageFiles files, File destination) {
        if (manifest == null) {
            return Result.fail("no manifest to package");
        }
        if (files == null || files.isEmpty()) {
            return Result.fail("the package has no files");
        }
        if (destination == null) {
            return Result.fail("no destination for the package");
        }
        String json = manifestJson(manifest);
        if (!AntEggManifest.parse(json).isValid()) {
            return Result.fail("generated manifest is not valid");
        }
        if (!files.entries().containsKey(manifest.entryPoint)) {
            return Result.fail("the entry point is not among the packaged files: "
                    + manifest.entryPoint);
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return Result.fail("could not create the package directory");
        }

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destination))) {
            zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
            zip.putNextEntry(new ZipEntry(AntEggPackage.MANIFEST_NAME));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            for (Map.Entry<String, byte[]> entry : files.entries().entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            return Result.ok(destination);
        } catch (IOException e) {
            // Do not leave a truncated archive the discovery scan could mistake for installed.
            if (destination.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destination.delete();
            }
            return Result.fail("could not write the package: " + e.getMessage());
        }
    }

    /** Serializes a manifest to the JSON grammar {@link AntEggManifest#parse} accepts. */
    static String manifestJson(AntEggManifest manifest) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\n");
        appendField(sb, "name", manifest.name, true);
        appendField(sb, "version", manifest.version, true);
        appendField(sb, "author", manifest.author, true);
        appendField(sb, "type", manifest.type, true);
        appendField(sb, "entry_point", manifest.entryPoint, true);
        appendField(sb, "description", manifest.description, true);
        if (manifest.aiPorted) {
            sb.append("  \"ai_ported\": true,\n");
        }
        if (manifest.originMod != null && !manifest.originMod.isEmpty()) {
            appendField(sb, "origin_mod", manifest.originMod, true);
        }
        if (manifest.originAuthor != null && !manifest.originAuthor.isEmpty()) {
            appendField(sb, "origin_author", manifest.originAuthor, true);
        }
        sb.append("  \"dependencies\": [");
        for (int i = 0; i < manifest.dependencies.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsonString(manifest.dependencies.get(i)));
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append("  ").append(jsonString(key)).append(": ")
                .append(jsonString(value == null ? "" : value));
        sb.append(comma ? ",\n" : "\n");
    }

    /** A JSON string literal with the escapes the manifest reader understands. */
    static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** Writes a UTF-8 text file directly; used for debugging a generated port outside a package. */
    static void writeText(File file, String text) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file),
                StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }
}
