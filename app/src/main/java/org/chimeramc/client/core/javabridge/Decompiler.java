package org.chimeramc.client.core.javabridge;

import java.io.File;
import java.util.List;

/**
 * Turns a jar's {@code .class} files into Java source.
 *
 * <p>An interface, not a static call, so the porter's orchestration can be tested with a fake that
 * returns fixed source without invoking a real decompiler — and so the decompiler backend can be
 * swapped (CFR today) without touching the porter.
 */
public interface Decompiler {

    /** The decompiled source for a jar, or a reason it could not be produced. */
    final class Result {
        public final boolean success;
        /** Concatenated source of the decompiled classes, joined with class banners. */
        public final String source;
        /** How many classes were actually decompiled. */
        public final int decompiledCount;
        public final String error;

        private Result(boolean success, String source, int decompiledCount, String error) {
            this.success = success;
            this.source = source;
            this.decompiledCount = decompiledCount;
            this.error = error;
        }

        public static Result ok(String source, int decompiledCount) {
            return new Result(true, source, decompiledCount, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, 0, error);
        }
    }

    /**
     * Decompiles up to {@code maxClasses} of the jar's own classes.
     *
     * <p>The cap is deliberate: a mod can ship hundreds of classes, and the porter only needs
     * enough source to understand the top-level behaviour. The caller decides the cap so the
     * "how much will we send" question is answered in one place.
     */
    Result decompile(File jar, List<String> classEntries, int maxClasses);
}
