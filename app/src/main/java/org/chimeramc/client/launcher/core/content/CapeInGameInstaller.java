package org.chimeramc.client.core.content;

import org.chimeramc.client.core.cosmetics.CapeResourcePackBuilder;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Puts the equipped cape onto the player in the running game.
 *
 * <p>The cape is delivered as a resource pack rather than through any native hook. The pack
 * overrides the player's cape texture, so once it is active the cape selected in the dressing
 * room renders with the pack's artwork. See {@link CapeResourcePackBuilder} for why this is
 * texture-only.
 *
 * <p>Installation reuses {@link SkinPackActivator}, which is the launcher's already-proven path
 * into an instance's {@code resource_packs/} and {@code minecraftpe/global_resource_packs.json}.
 * Building a second writer for the same two files is how the two would drift and start
 * clobbering each other's entries.
 *
 * <p>The pack is staged into the app's private files before being applied, so a failure part-way
 * through cannot leave a half-written pack in the instance the game would then refuse to load.
 */
public final class CapeInGameInstaller {

    private static final String STAGING_DIR = "cape_pack";

    private CapeInGameInstaller() {
    }

    /**
     * Builds the cape pack for {@code cape} and makes it active for an instance.
     *
     * <p>Applies to every candidate game data root rather than one resolved path. The game picks
     * its storage from the instance's isolation setting and the player's internal/external
     * choice, and a cape written to only the wrong guess is simply invisible — the same
     * multi-root defence {@code BundledResourcePackInstaller} uses. Applying to a root the game
     * does not read costs one small directory; missing the one it does read costs the feature.
     *
     * @param stagingRoot the app-private directory to build the pack in
     * @param gameDataDirs the instance's candidate game data roots
     * @param cape        the cape to show, or {@code null} to install a blank pack
     */
    public static SkinPackActivator.Result install(File stagingRoot, List<File> gameDataDirs,
                                                   CosmeticCatalog.Cape cape) {
        if (stagingRoot == null) return failure("no staging directory");
        if (gameDataDirs == null || gameDataDirs.isEmpty()) return failure("no instance storage");

        File packDir = new File(stagingRoot, STAGING_DIR);
        try {
            deleteRecursively(packDir);
            CapeResourcePackBuilder.build(packDir, cape);
        } catch (IOException e) {
            return failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        SkinPackActivator.Result last = null;
        boolean anySucceeded = false;
        for (File gameDataDir : gameDataDirs) {
            if (gameDataDir == null) continue;
            last = SkinPackActivator.apply(packDir, gameDataDir);
            anySucceeded |= last.success;
        }
        if (anySucceeded) return SkinPackActivator.Result.ok("cape pack applied");
        return last != null ? last : failure("no instance storage");
    }

    /** Backwards-compatible single-root install. */
    public static SkinPackActivator.Result install(File stagingRoot, File gameDataDir,
                                                   CosmeticCatalog.Cape cape) {
        if (gameDataDir == null) return failure("no instance storage");
        return install(stagingRoot, java.util.Collections.singletonList(gameDataDir), cape);
    }

    /** Removes the cape pack from every candidate root, leaving the player's own packs alone. */
    public static SkinPackActivator.Result uninstall(List<File> gameDataDirs) {
        if (gameDataDirs == null || gameDataDirs.isEmpty()) return failure("no instance storage");
        SkinPackActivator.Result last = null;
        boolean anySucceeded = false;
        for (File gameDataDir : gameDataDirs) {
            if (gameDataDir == null) continue;
            last = SkinPackActivator.unapply(gameDataDir, CapeResourcePackBuilder.PACK_UUID);
            anySucceeded |= last.success;
        }
        if (anySucceeded) return SkinPackActivator.Result.ok("cape pack applied");
        return last != null ? last : failure("no instance storage");
    }

    /** Removes the cape pack from an instance, leaving the player's own packs alone. */
    public static SkinPackActivator.Result uninstall(File gameDataDir) {
        if (gameDataDir == null) return failure("no instance storage");
        return uninstall(java.util.Collections.singletonList(gameDataDir));
    }

    /** True when this launcher is the one that made the cape pack active for the instance. */
    public static boolean isInstalled(File gameDataDir) {
        return SkinPackActivator.isAppliedByLauncher(gameDataDir, CapeResourcePackBuilder.PACK_UUID);
    }

    /** True when the cape pack is active in any of the candidate roots. */
    public static boolean isInstalled(List<File> gameDataDirs) {
        if (gameDataDirs == null) return false;
        for (File gameDataDir : gameDataDirs) {
            if (gameDataDir != null && isInstalled(gameDataDir)) return true;
        }
        return false;
    }

    private static SkinPackActivator.Result failure(String message) {
        return SkinPackActivator.Result.failure(message);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
