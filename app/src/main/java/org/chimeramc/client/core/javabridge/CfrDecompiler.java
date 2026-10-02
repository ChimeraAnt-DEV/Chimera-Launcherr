package org.chimeramc.client.core.javabridge;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link Decompiler} implementation backed by CFR.
 *
 * <p>CFR is used through its public API ({@link CfrDriver}) with a {@link OutputSinkFactory} that
 * captures the decompiled text in memory, so no temp files are written. CFR is MIT-licensed, which
 * is why it can be bundled in this Apache-2.0 APK (see the dependency comment in {@code
 * app/build.gradle}); Procyon, the other decompiler the spec names, is also a candidate but is not
 * on the classpath here.
 *
 * <p>CFR is given the whole jar as its analysis target and asked for specific classes, rather than
 * being pointed at individual class files: it needs the jar's own classpath to resolve references
 * between the mod's classes, and handing it the jar once is both faster and more accurate than
 * repeated single-file runs.
 */
public final class CfrDecompiler implements Decompiler {

    /** CFR is a decompiler, not a sandbox; a mod that makes it thrash must be cut off. */
    private static final long MAX_TOTAL_CHARS = 2L * 1024 * 1024;

    @Override
    public Result decompile(File jar, List<String> classEntries, int maxClasses) {
        if (jar == null || !jar.isFile()) {
            return Result.fail("mod file does not exist");
        }
        if (classEntries == null || classEntries.isEmpty()) {
            return Result.fail("the mod has no classes to decompile");
        }

        List<String> classNames = new ArrayList<>();
        for (String entry : classEntries) {
            if (classNames.size() >= maxClasses) break;
            String name = toClassName(entry);
            if (name != null) classNames.add(name);
        }
        if (classNames.isEmpty()) {
            return Result.fail("the mod has no named classes to decompile");
        }

        StringBuilder source = new StringBuilder(65536);
        List<String> errors = new ArrayList<>();
        Capture capture = new Capture(source, errors, MAX_TOTAL_CHARS);

        Map<String, String> options = new HashMap<>();
        // No comments, no hidden annotations: the model wants behaviour, not decompiler noise.
        options.put("comments", "false");
        options.put("silent", "true");
        options.put("hideutf", "false");
        // Do not let CFR rewrite the jar or write class files anywhere.
        options.put("outputdir", "/dev/null");

        try {
            new CfrDriver.Builder()
                    .withOutputSink(capture)
                    .withOptions(options)
                    .build()
                    .analyse(singletonJarAndClasses(jar, classNames));
        } catch (Throwable t) {
            return Result.fail("decompilation failed: " + t.getMessage());
        }

        if (source.length() == 0) {
            String detail = errors.isEmpty() ? "no source was produced" : errors.get(0);
            return Result.fail("decompilation produced no output: " + detail);
        }
        return Result.ok(source.toString(), capture.classesDecompiled);
    }

    private static List<String> singletonJarAndClasses(File jar, List<String> classNames) {
        List<String> args = new ArrayList<>(classNames.size() + 1);
        args.add(jar.getAbsolutePath());
        args.addAll(classNames);
        return args;
    }

    /** {@code com/example/Mod.class} -> {@code com.example.Mod}. */
    static String toClassName(String entry) {
        if (entry == null) return null;
        String name = entry.replace('\\', '/');
        if (!name.endsWith(".class")) return null;
        name = name.substring(0, name.length() - ".class".length());
        if (name.endsWith("module-info") || name.equals("package-info")) return null;
        return name.replace('/', '.');
    }

    /** Captures CFR's output in memory and stops the run once the size cap is hit. */
    private static final class Capture implements OutputSinkFactory {
        private final StringBuilder sink;
        private final List<String> errors;
        private final long maxChars;
        private int classesDecompiled;

        Capture(StringBuilder sink, List<String> errors, long maxChars) {
            this.sink = sink;
            this.errors = errors;
            this.maxChars = maxChars;
        }

        @Override
        public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
            if (sinkType == SinkType.JAVA && available.contains(SinkClass.DECOMPILED)) {
                return Collections.singletonList(SinkClass.DECOMPILED);
            }
            if (sinkType == SinkType.EXCEPTION && available.contains(SinkClass.STRING)) {
                return Collections.singletonList(SinkClass.STRING);
            }
            return Collections.emptyList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                return (Sink<T>) (Sink<SinkReturns.Decompiled>) decompiled -> {
                    if (sink.length() >= maxChars) return;
                    classesDecompiled++;
                    sink.append("// === ").append(decompiled.getClassName())
                            .append(" ===\n");
                    String java = decompiled.getJava();
                    if (java != null) {
                        long remaining = maxChars - sink.length();
                        sink.append(remaining >= java.length() ? java
                                : java.substring(0, (int) Math.max(0, remaining)));
                    }
                    sink.append("\n\n");
                };
            }
            if (sinkType == SinkType.EXCEPTION && sinkClass == SinkClass.STRING) {
                return (Sink<T>) (Sink<String>) errors::add;
            }
            return null;
        }
    }
}
