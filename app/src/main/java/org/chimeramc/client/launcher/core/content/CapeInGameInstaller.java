package org.chimeramc.client.core.content;

import org.chimeramc.client.core.cosmetics.CapeResourcePackBuilder;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;

import java.io.File;
import java.io.IOException;

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
     * @param stagingRoot the app-private directory to build the pack in
     * @param gameDataDir the instance's game data root
     * @param cape        the cape to show, or {@code null} to install a blank pack
     */
    public static SkinPackActivator.Result install(File stagingRoot, File gameDataDir,
                                                   CosmeticCatalog.Cape cape) {
        if (stagingRoot == null) return failure("no staging directory");
        if (gameDataDir == null) return failure("no instance storage");

        File packDir = new File(stagingRoot, STAGING_DIR);
        try {
            deleteRecursively(packDir);
            CapeResourcePackBuilder.build(packDir, cape);
        } catch (IOException e) {
            return failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return SkinPackActivator.apply(packDir, gameDataDir);
    }

    /** Removes the cape pack from an instance, leaving the player's own packs alone. */
    public static SkinPackActivator.Result uninstall(File gameDataDir) {
        return SkinPackActivator.unapply(gameDataDir, CapeResourcePackBuilder.PACK_UUID);
    }

    /** True when this launcher is the one that made the cape pack active for the instance. */
    public static boolean isInstalled(File gameDataDir) {
        return SkinPackActivator.isAppliedByLauncher(gameDataDir, CapeResourcePackBuilder.PACK_UUID);
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
