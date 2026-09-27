package org.chimeramc.client.core.antegg;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs a {@code type: "script"} .AntEgg mod's entry point in an embedded Lua VM.
 *
 * <p>The VM is <b>LuaJ</b>, a pure-Java implementation of Lua 5.2 (JSR-223 API
 * {@code org.luaj:luaj-jse}). That choice is deliberate: this APK ships arm64 only and already
 * carries a native toolchain, but a native Lua 5.4/LuaJIT would need a second per-ABI artifact
 * built and kept in step with the preloader, whereas LuaJ is one jar, runs on the launcher's own
 * JVM, and needs no NDK build. The trade-off is speed — LuaJ is an interpreter, not a JIT — which
 * is acceptable because script mods drive setup and configuration, not per-frame game logic.
 *
 * <p>Each mod gets a fresh {@link Globals} with the standard libraries loaded, plus a
 * {@code mod} table carrying the mod's name, version, directory and a {@code sandbox}
 * path so the script can locate its own data files. The entry file is executed with
 * {@code chunk(...)}, so it is a normal Lua script, not a C-style entry function.
 */
public final class AntEggScriptHost {

    /** Result of running a script: success, or a reason a caller can show the user. */
    public static final class Result {
        public final boolean success;
        public final String error;

        private Result(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public static Result ok() {
            return new Result(true, null);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    private AntEggScriptHost() {}

    /**
     * Executes the entry file of a script mod.
     *
     * <p>Returns failure rather than throwing: a broken script must not take the launcher down
     * with it, and the caller needs a string to show in the Mods tab.
     */
    public static Result run(File modDirectory, AntEggManifest manifest) {
        if (modDirectory == null || manifest == null) {
            return Result.fail("no script mod to run");
        }
        File entry = new File(modDirectory, manifest.entryPoint);
        if (!entry.isFile()) {
            return Result.fail("entry_point is missing: " + manifest.entryPoint);
        }
        String source;
        try {
            source = readUtf8(entry);
        } catch (IOException e) {
            return Result.fail("could not read script: " + e.getMessage());
        }

        Globals globals = JsePlatform.standardGlobals();
        globals.set("mod", buildModTable(modDirectory, manifest));
        try {
            LuaValue chunk = globals.load(source, "@" + manifest.entryPoint);
            chunk.call();
            return Result.ok();
        } catch (LuaError e) {
            return Result.fail("script error: " + e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("script failed: " + e.getMessage());
        }
    }

    /** Fills the {@code mod} table a script reads to find its own name and files. */
    private static LuaTable buildModTable(File modDirectory, AntEggManifest manifest) {
        LuaTable table = new LuaTable();
        table.set("name", manifest.name);
        table.set("version", manifest.version);
        table.set("author", manifest.author);
        table.set("id", manifest.id());
        table.set("dir", modDirectory.getAbsolutePath());
        table.set("sandbox", new File(modDirectory, "data").getAbsolutePath());
        LuaTable deps = new LuaTable();
        List<String> dependencies = manifest.dependencies;
        for (int i = 0; i < dependencies.size(); i++) {
            deps.set(i + 1, dependencies.get(i));
        }
        table.set("dependencies", deps);
        return table;
    }

    /** Test seam: runs a Lua source string with no mod table, for the unit tests. */
    public static Result runSource(String source) {
        Globals globals = JsePlatform.standardGlobals();
        try {
            globals.load(source, "@test.lua").call();
            return Result.ok();
        } catch (LuaError e) {
            return Result.fail("script error: " + e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("script failed: " + e.getMessage());
        }
    }

    private static String readUtf8(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** The Lua standard libraries the sandbox exposes; documented so the surface is reviewable. */
    public static List<String> exposedStandardLibraries() {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(
                "base", "package", "string", "table", "math", "coroutine", "io", "os")));
    }

    /** Unused except to keep a direct handle on the input stream type for future data files. */
    static InputStream openStream(File file) throws IOException {
        return Files.newInputStream(file.toPath());
    }
}
