package org.chimeramc.client.core.antegg;

import android.content.Context;
import android.util.Log;

import org.chimeramc.client.core.mods.Mod;
import org.chimeramc.client.core.mods.ModLoadDiagnostics;
import org.chimeramc.client.core.mods.ModManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Loads {@code .AntEgg} packages: validate, extract to a sandbox, then hand the entry point to the
 * right runtime.
 *
 * <p>This is the class the launcher UI calls. The two runtimes are deliberately different seams:
 *
 * <ul>
 *   <li><b>native</b> — the extracted {@code .so} is passed by absolute path to the preloader's
 *       existing native-mod injection entry point ({@link ModManager#initializeLoadedMod}), the
 *       same call an ordinary native mod uses. .AntEgg adds packaging and validation, not a second
 *       injection mechanism. (The injection API is referred to in the original spec as
 *       "ApexAntLamina"; in this tree it is the {@code pl} preloader bridge — see AGENTS.md, there
 *       is no class or symbol by the other name here.)</li>
 *   <li><b>script</b> — the entry {@code .lua} file is executed by the embedded Lua VM
 *       ({@link AntEggScriptHost}).</li>
 * </ul>
 *
 * <p>Every failure is recorded through {@link ModLoadDiagnostics} so the Mods tab can show why an
 * import did not take, rather than the file silently not appearing.
 */
public final class AntEggLoader {

    private static final String TAG = "AntEggLoader";
    /** Sandbox root: {@code getExternalFilesDir()/AntEggs/}, per the format spec. */
    public static final String SANDBOX_DIR_NAME = "AntEggs";

    private AntEggLoader() {}

    /** What happened to one package. */
    public static final class LoadResult {
        public final boolean success;
        public final AntEggManifest manifest;
        public final File directory;
        public final String error;

        private LoadResult(boolean success, AntEggManifest manifest, File directory, String error) {
            this.success = success;
            this.manifest = manifest;
            this.directory = directory;
            this.error = error;
        }

        static LoadResult ok(AntEggManifest manifest, File directory) {
            return new LoadResult(true, manifest, directory, null);
        }

        public static LoadResult fail(String error) {
            return new LoadResult(false, null, null, error);
        }
    }

    /** The sandbox root, falling back to internal storage when external is unavailable. */
    public static File sandboxRoot(Context context) {
        File external = context.getExternalFilesDir(null);
        File base = external != null ? external : context.getFilesDir();
        return new File(base, SANDBOX_DIR_NAME);
    }

    /**
     * Imports and loads a package.
     *
     * <p>Extraction happens first and completely; a package that fails validation is never
     * extracted, and a package that fails to load is never removed — it stays in the sandbox so a
     * later fix (a rebuilt {@code .so}, a corrected dependency) can retry without re-importing.
     */
    public static LoadResult loadMod(Context context, File packageFile) {
        if (context == null) {
            return LoadResult.fail("no application context");
        }
        if (packageFile == null || !packageFile.isFile()) {
            return LoadResult.fail("package file does not exist");
        }
        if (!AntEggPackage.looksLikeAntEgg(packageFile.getName())) {
            return LoadResult.fail("not an .AntEgg package: " + packageFile.getName());
        }

        File sandbox = sandboxRoot(context);
        if (!sandbox.isDirectory() && !sandbox.mkdirs()) {
            return fail(context, packageFile.getName(),
                    LoadResult.fail("could not create the AntEggs sandbox"));
        }

        AntEggPackage.Result extracted = AntEggPackage.extract(packageFile, sandbox);
        if (!extracted.isValid()) {
            Log.w(TAG, "Rejected " + packageFile.getName() + ": " + extracted.error);
            return fail(context, packageFile.getName(), LoadResult.fail(extracted.error));
        }
        AntEggManifest manifest = extracted.manifest;

        String dependencyError = missingDependencies(sandbox, manifest);
        if (dependencyError != null) {
            return fail(context, manifest.name, LoadResult.fail(dependencyError));
        }

        if (manifest.isNative()) {
            return fail(context, manifest.name, loadNative(manifest, extracted.directory));
        }
        if (manifest.isScript()) {
            return fail(context, manifest.name, loadScript(manifest, extracted.directory));
        }
        return fail(context, manifest.name,
                LoadResult.fail("unsupported mod type: " + manifest.type));
    }

    private static LoadResult loadNative(AntEggManifest manifest, File directory) {
        File library = new File(directory, manifest.entryPoint);
        if (!library.isFile()) {
            return LoadResult.fail("native entry point is missing: " + manifest.entryPoint);
        }
        Mod mod = new Mod(manifest.id(), library.getName(), manifest.entryPoint, manifest.name,
                Collections.emptyList(), manifest.author, manifest.version, null, null,
                manifest.description, directory.getAbsolutePath(),
                new File(directory, "config").getAbsolutePath(), false, 0, true, 0);
        boolean loaded = ModManager.initializeLoadedMod(
                library.getAbsolutePath(), directory.getAbsolutePath(), mod);
        if (!loaded) {
            return LoadResult.fail("native injection failed for " + manifest.name);
        }
        return LoadResult.ok(manifest, directory);
    }

    private static LoadResult loadScript(AntEggManifest manifest, File directory) {
        AntEggScriptHost.Result result = AntEggScriptHost.run(directory, manifest);
        if (!result.success) {
            return LoadResult.fail(result.error);
        }
        return LoadResult.ok(manifest, directory);
    }

    /**
     * Returns a human-readable reason the manifest's dependencies are unmet, or null when they are.
     *
     * <p>Dependency names are matched against the ids of mods already in the sandbox. This is a
     * flat, name-based check on purpose: .AntEgg has no version-range syntax, so inventing one here
     * would accept manifests the format cannot express.
     */
    static String missingDependencies(File sandbox, AntEggManifest manifest) {
        if (manifest.dependencies.isEmpty()) return null;
        List<String> installed = new ArrayList<>();
        for (String id : AntEggPackage.installedIds(sandbox)) {
            installed.add(id.toLowerCase(Locale.ROOT));
        }
        List<String> missing = new ArrayList<>();
        for (String dependency : manifest.dependencies) {
            String wanted = AntEggManifest.slug(dependency);
            if (!installed.contains(wanted)) {
                missing.add(dependency);
            }
        }
        if (missing.isEmpty()) return null;
        return "missing dependencies: " + String.join(", ", missing);
    }

    private static LoadResult fail(Context context, String modName, LoadResult result) {
        if (!result.success) {
            recordFailure(context, modName, result.error);
        }
        return result;
    }

    /**
     * Records a failed import in the launch diagnostics so the Mods tab can show it.
     *
     * <p>Diagnostics are a whole-file replace, so the existing set is read, the new failure
     * prepended, and the result written back. A failure to persist the note must never fail the
     * import itself, hence the catch.
     */
    public static void recordFailure(Context context, String modName, String reason) {
        if (context == null) return;
        try {
            List<ModLoadDiagnostics.Record> records = new ArrayList<>(ModLoadDiagnostics.getAll(context));
            records.add(0, new ModLoadDiagnostics.Record(
                    AntEggManifest.slug(modName), modName, "0", reason,
                    ModLoadDiagnostics.KIND_DLOPEN, System.currentTimeMillis()));
            ModLoadDiagnostics.record(context, records);
        } catch (Throwable t) {
            Log.w(TAG, "Could not record AntEgg load failure", t);
        }
    }
}
