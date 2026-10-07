package org.chimeramc.client.core.cosmetics;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The hand-authored hat meshes, keyed by catalogue accessory id, loaded from the pack's assets.
 *
 * <p>The procedural {@link AccessoryGeometry} stacks boxes; these are real Blockbench exports with
 * their own UV-mapped textures (see {@code resources/cosmetics/models/hats}). The catalogue id is
 * the file base name, so {@code orig_witch} maps to {@code orig_witch.geo.json} with no separate
 * table — adding a hat is a file plus one catalogue entry.
 *
 * <p>{@link #IDS} is the single source of truth for which cosmetic ids are authored; the catalogue
 * and the pack builder both read it, so a file and a catalogue entry cannot drift apart.
 */
public final class AuthoredHatModels {

    /** Asset directory the authored hat models live in. */
    public static final String DIR = "cosmetics/models/hats";

    /**
     * Every authored accessory id. An id that is not here keeps the procedural mesh. The ids match
     * both the catalogue entry and the asset file name.
     */
    public static final Set<String> IDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            // Original models (made for this project).
            "orig_acorn_cap", "orig_bat_headband", "orig_cat_ears_tail", "orig_cozy_beanie",
            "orig_halo_blocky", "orig_leaf_crown", "orig_mushroom_cap", "orig_pumpkin",
            "orig_straw_hat", "orig_witch",
            // VoxelBear hats (CC BY - see resources/cosmetics/CREDITS.md).
            "vb_cap_blue", "vb_cap_green", "vb_cap_rainbow", "vb_cardboard_box", "vb_chef_hat",
            "vb_clown_nose_wig", "vb_crown", "vb_hard_hat", "vb_leprechaun_hat", "vb_mage_hat",
            "vb_miner_helmet", "vb_mushroom_blue", "vb_mushroom_green", "vb_mushroom_red",
            "vb_paper_bag", "vb_santa_hat", "vb_straw_hat", "vb_striped_cone_hat",
            "vb_top_hat_black", "vb_ushanka", "vb_warm_hat_red"
    )));

    private AuthoredHatModels() {
    }

    /** True when a catalogue accessory id has a hand-authored mesh. */
    public static boolean hasAuthoredModel(String accessoryId) {
        return accessoryId != null && IDS.contains(accessoryId);
    }

    /** The authored model file for an accessory id, or null when it has none. */
    public static String fileFor(String accessoryId) {
        return hasAuthoredModel(accessoryId) ? accessoryId + ".geo.json" : null;
    }

    /** The texture file for an accessory id, or null when it has no authored model. */
    public static String textureFor(String accessoryId) {
        return hasAuthoredModel(accessoryId) ? accessoryId + ".png" : null;
    }

    /**
     * Loads an accessory's authored model from the pack assets, retargeted to the hat geometry id,
     * with its root bone renamed to {@code acc} so the head-tilt animation drives it.
     *
     * @return the model JSON, or null when the id has no model or the asset is missing/bad
     */
    public static String loadFromAssets(AuthoredGeometry.AssetOpener assets, String accessoryId) {
        String file = fileFor(accessoryId);
        if (assets == null || file == null) return null;
        String model = AuthoredGeometry.loadFromAssets(assets, DIR, file,
                AccessoryGeometry.GEOMETRY_ID);
        return model == null ? null : AccBoneRetarget.apply(model);
    }

    /** Loads an accessory's authored model from a directory on disk (used by the JVM tests). */
    public static String loadFromDir(File dir, String accessoryId) {
        String file = fileFor(accessoryId);
        if (dir == null || file == null) return null;
        String model = AuthoredGeometry.loadFromFile(new File(dir, file),
                AccessoryGeometry.GEOMETRY_ID);
        return model == null ? null : AccBoneRetarget.apply(model);
    }
}
