package org.chimeramc.client.core.cosmetics;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The hand-authored pet meshes, one per species, loaded from the pack's assets.
 *
 * <p>The procedural {@link PetGeometry} stacks boxes and reads as "a box glued to a box". A real
 * Blockbench export keeps its own bone hierarchy, pivots and per-cube UVs, so it looks like a
 * sculpted animal instead. This class is the single place that maps a species to its authored
 * model and texture; {@link CapeResourcePackBuilder} asks it first and falls back to the
 * procedural mesh only when the asset is absent or malformed.
 *
 * <p><b>Identifier retargeting.</b> Each authored file carries its author's identifier
 * ({@code geometry.fire_dragon}); the render controller looks for {@code geometry.chimera_pet}, so
 * the identifier is rewritten while every bone and cube is preserved (see
 * {@link AuthoredGeometry#retargetIdentifier}). Two species must not share a file, so the mapping
 * is one-to-one.
 *
 * <p>Pure string/file work with no Android types, so a JVM test can feed the real assets and assert
 * the mapping and the retarget.
 */
public final class AuthoredPetModels {

    /** Asset directory the authored pet models live in. */
    public static final String DIR = "cosmetics/models/pets";

    /**
     * Species to authored-model file. Only the species that have a real export are listed; every
     * other species keeps the procedural mesh. Adding a model is a file plus one entry here.
     */
    private static final Map<CosmeticCatalog.PetSpecies, String> FILES = new LinkedHashMap<>();

    static {
        FILES.put(CosmeticCatalog.PetSpecies.DRAGON, "dragon.geo.json");
        FILES.put(CosmeticCatalog.PetSpecies.PARROT, "parrot.geo.json");
        FILES.put(CosmeticCatalog.PetSpecies.DRAGONFLY, "dragonfly.geo.json");
        FILES.put(CosmeticCatalog.PetSpecies.AXOLOTL, "axolotl.geo.json");
        FILES.put(CosmeticCatalog.PetSpecies.WOLF, "wolf.geo.json");
    }

    private AuthoredPetModels() {
    }

    /** The authored model file for a species, or null when it has none. */
    public static String fileFor(CosmeticCatalog.PetSpecies species) {
        return species == null ? null : FILES.get(species);
    }

    /** True when a species has a hand-authored mesh. */
    public static boolean hasAuthoredModel(CosmeticCatalog.PetSpecies species) {
        return fileFor(species) != null;
    }

    /** The texture file for a species, or null when it has no authored model. */
    public static String textureFor(CosmeticCatalog.PetSpecies species) {
        String model = fileFor(species);
        return model == null ? null : model.replace(".geo.json", ".png");
    }

    /**
     * Loads a species' authored model from the pack assets, retargeted to {@code geometry.chimera_pet}.
     *
     * @return the model JSON, or null when the species has no model or the asset is missing/bad
     */
    public static String loadFromAssets(AuthoredGeometry.AssetOpener assets,
                                        CosmeticCatalog.PetSpecies species) {
        String file = fileFor(species);
        if (assets == null || file == null) return null;
        String model = AuthoredGeometry.loadFromAssets(assets, DIR, file, PetGeometry.GEOMETRY_ID);
        // Retarget the author's bone names onto the vocabulary the gait controller drives, so the
        // authored mesh animates (walks, runs, flaps) instead of rendering stiff.
        return model == null ? null : PetBoneRetarget.apply(model);
    }

    /**
     * Loads a species' authored model from a directory on disk (used by the JVM tests), or null.
     */
    public static String loadFromDir(File dir, CosmeticCatalog.PetSpecies species) {
        String file = fileFor(species);
        if (dir == null || file == null) return null;
        String model = AuthoredGeometry.loadFromFile(new File(dir, file), PetGeometry.GEOMETRY_ID);
        return model == null ? null : PetBoneRetarget.apply(model);
    }
}
