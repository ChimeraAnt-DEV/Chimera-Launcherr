package org.chimeramc.client.core.content;

import org.chimeramc.client.core.cosmetics.AuthoredGeometry;
import org.chimeramc.client.core.cosmetics.CapeResourcePackBuilder;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Puts the equipped cape onto the player in the running game.
 *
 * <p>The cape is delivered as a resource pack built by {@link CapeResourcePackBuilder}: a player
 * client-entity render controller that draws a cape on the player's back. See that class for why
 * this replaced the old {@code cape_invisible} texture override (a Persona-equipped cape is fetched
 * per account and never samples that file).
 *
 * <p>Installation reuses {@link SkinPackActivator}, which is the launcher's already-proven path
 * into an instance's {@code resource_packs/} and {@code minecraftpe/global_resource_packs.json}.
 * Building a second writer for the same two files is how the two would drift and start clobbering
 * each other's entries.
 *
 * <p>The pack is staged into the app's private files before being applied, so a failure part-way
 * through cannot leave a half-written pack in the instance the game would then refuse to load.
 *
 * <p><b>Applying while the game is running.</b> After the files are written the change is pushed to
 * the live session the same way the in-game pack changer does: an in-place reload if the build
 * supports it, otherwise a relaunch of the same instance. The player never has to back out to the
 * launcher, leave their world, or leave a server — the pack is written to the running world's own
 * list and the session refreshes or relaunches around them.
 */
public final class CapeInGameInstaller {

    private static final String STAGING_DIR = "cape_pack";

    private CapeInGameInstaller() {
    }

    /**
     * Builds the cape pack for {@code cape} and makes it active for an instance, then pushes the
     * change to any running session.
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
     * @return what happened: {@link InGamePackChanger.ApplyOutcome#FAILED} when nothing was written,
     *         otherwise whether the session reloaded, relaunched, or will pick it up on next load
     */
    public static InGamePackChanger.ApplyOutcome install(File stagingRoot, List<File> gameDataDirs,
                                                         CosmeticCatalog.Cape cape) {
        return install(stagingRoot, gameDataDirs, cape, null, null);
    }

    /**
     * Builds the cape + accessory + pet pack and makes it active for an instance, then pushes the
     * change to any running session.
     *
     * @param cape      the cape to show, or {@code null} for none
     * @param accessory the worn hat/accessory to show, or {@code null} for none
     * @param pet       the pet to show on the player, or {@code null} for none
     */
    public static InGamePackChanger.ApplyOutcome install(File stagingRoot, List<File> gameDataDirs,
                                                         CosmeticCatalog.Cape cape,
                                                         CosmeticCatalog.Accessory accessory,
                                                         CosmeticCatalog.Pet pet) {
        return install(stagingRoot, gameDataDirs, cape, accessory, pet, null);
    }

    /**
     * As above, but preferring hand-authored Blockbench models from {@code assets} (see
     * {@link AuthoredGeometry}). A {@code null} opener or an absent model falls back to the
     * procedural geometry, so this is a pure opt-in overlay.
     */
    public static InGamePackChanger.ApplyOutcome install(File stagingRoot, List<File> gameDataDirs,
                                                         CosmeticCatalog.Cape cape,
                                                         CosmeticCatalog.Accessory accessory,
                                                         CosmeticCatalog.Pet pet,
                                                         AuthoredGeometry.AssetOpener assets) {
        if (!writePack(stagingRoot, gameDataDirs, cape, accessory, pet, assets)) {
            return InGamePackChanger.ApplyOutcome.FAILED;
        }

        // The files are written to the global list and every running world's own list; now ask the
        // live session to pick them up. reload -> restart -> next load, exactly like the pack
        // changer, so the player does not have to leave the world for the cape to appear.
        if (InGamePackChanger.requestReload()) return InGamePackChanger.ApplyOutcome.RELOADED;
        if (InGamePackChanger.requestRestart()) return InGamePackChanger.ApplyOutcome.RESTARTING;
        return InGamePackChanger.ApplyOutcome.NEXT_LOAD;
    }

    /** Backwards-compatible single-root install. */
    public static InGamePackChanger.ApplyOutcome install(File stagingRoot, File gameDataDir,
                                                         CosmeticCatalog.Cape cape) {
        if (gameDataDir == null) return InGamePackChanger.ApplyOutcome.FAILED;
        return install(stagingRoot, java.util.Collections.singletonList(gameDataDir), cape, null,
                null);
    }

    /**
     * Writes (or removes) the cape + accessory + pet pack without asking any session to reload.
     *
     * <p>This is the launch-time path: the game is about to start, so there is nothing to refresh
     * and nothing to relaunch. It is what removes the friction of applying a cosmetic mid-session —
     * the equipped pieces are written to the pack list before every launch, so the next time the
     * player enters a world they are simply already on them, with no restart in between.
     *
     * @return true when the pack list was written as requested
     */
    public static boolean installQuietly(File stagingRoot, List<File> gameDataDirs,
                                         CosmeticCatalog.Cape cape) {
        return installQuietly(stagingRoot, gameDataDirs, cape, null, null);
    }

    public static boolean installQuietly(File stagingRoot, List<File> gameDataDirs,
                                         CosmeticCatalog.Cape cape,
                                         CosmeticCatalog.Accessory accessory) {
        return installQuietly(stagingRoot, gameDataDirs, cape, accessory, null);
    }

    public static boolean installQuietly(File stagingRoot, List<File> gameDataDirs,
                                         CosmeticCatalog.Cape cape,
                                         CosmeticCatalog.Accessory accessory,
                                         CosmeticCatalog.Pet pet) {
        return installQuietly(stagingRoot, gameDataDirs, cape, accessory, pet, null);
    }

    /** As above, preferring hand-authored Blockbench models when {@code assets} has them. */
    public static boolean installQuietly(File stagingRoot, List<File> gameDataDirs,
                                         CosmeticCatalog.Cape cape,
                                         CosmeticCatalog.Accessory accessory,
                                         CosmeticCatalog.Pet pet,
                                         AuthoredGeometry.AssetOpener assets) {
        return writePack(stagingRoot, gameDataDirs, cape, accessory, pet, assets);
    }

    /** Removes the cape pack from every candidate root without asking a session to reload. */
    public static boolean uninstallQuietly(List<File> gameDataDirs) {
        if (gameDataDirs == null || gameDataDirs.isEmpty()) return false;
        boolean removed = false;
        for (File gameDataDir : gameDataDirs) {
            if (gameDataDir == null) continue;
            SkinPackActivator.Result result =
                    SkinPackActivator.unapply(gameDataDir, CapeResourcePackBuilder.PACK_UUID);
            if (result.success) {
                InGamePackChanger.setActive(gameDataDir, CapeResourcePackBuilder.PACK_UUID,
                        CapeResourcePackBuilder.PACK_VERSION, false);
                removed = true;
            }
        }
        return removed;
    }

    /** Builds the pack and writes it into every candidate root; no session interaction. */
    private static boolean writePack(File stagingRoot, List<File> gameDataDirs,
                                     CosmeticCatalog.Cape cape,
                                     CosmeticCatalog.Accessory accessory,
                                     CosmeticCatalog.Pet pet,
                                     AuthoredGeometry.AssetOpener assets) {
        if (stagingRoot == null || gameDataDirs == null || gameDataDirs.isEmpty()) return false;

        File packDir = new File(stagingRoot, STAGING_DIR);
        try {
            deleteRecursively(packDir);
            CapeResourcePackBuilder.build(packDir, cape, accessory, pet, assets);
        } catch (IOException e) {
            return false;
        }

        boolean wrote = false;
        for (File gameDataDir : gameDataDirs) {
            if (gameDataDir == null) continue;
            // SkinPackActivator copies the pack into resource_packs/<uuid> and records it in the
            // global list; setActive then adds it to every running world's own pack list, which is
            // the file the *loaded* world reads. Doing only the global list leaves a running world
            // unchanged until it is re-entered.
            SkinPackActivator.Result result = SkinPackActivator.apply(packDir, gameDataDir);
            if (!result.success) continue;
            InGamePackChanger.setActive(gameDataDir, CapeResourcePackBuilder.PACK_UUID,
                    CapeResourcePackBuilder.PACK_VERSION, true);
            wrote = true;
        }
        return wrote;
    }

    /** Removes the cape pack from every candidate root, leaving the player's own packs alone. */
    public static SkinPackActivator.Result uninstall(List<File> gameDataDirs) {
        if (gameDataDirs == null || gameDataDirs.isEmpty()) return failure("no instance storage");
        SkinPackActivator.Result last = null;
        boolean anySucceeded = false;
        for (File gameDataDir : gameDataDirs) {
            if (gameDataDir == null) continue;
            last = SkinPackActivator.unapply(gameDataDir, CapeResourcePackBuilder.PACK_UUID);
            if (last.success) {
                // Drop it from every running world's list too, so the cape disappears from a
                // session already in progress rather than only on the next world load.
                InGamePackChanger.setActive(gameDataDir, CapeResourcePackBuilder.PACK_UUID,
                        CapeResourcePackBuilder.PACK_VERSION, false);
            }
            anySucceeded |= last.success;
        }
        if (anySucceeded) {
            // Drop it from the running session too, so the cape disappears without a manual reload.
            InGamePackChanger.requestReload();
            return SkinPackActivator.Result.ok("cape pack removed");
        }
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
