package org.chimeramc.client.core.cosmetics;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The hand-authored pet meshes, keyed by catalogue pet id, loaded from the pack's assets.
 *
 * <p>The procedural {@link PetGeometry} stacks boxes; these are real Blockbench exports with their
 * own UV-mapped textures (see {@code resources/cosmetics/models/pets}). The catalogue id is the
 * file base name, so {@code pet_fire_dragon} maps to {@code pet_fire_dragon.geo.json} with no
 * separate table. {@link #IDS} is the single source of truth for which pet ids are authored, so
 * the catalogue and the pack builder cannot drift.
 */
public final class AuthoredPetModels {

    /** Asset directory the authored pet models live in. */
    public static final String DIR = "cosmetics/models/pets";

    /** Every authored pet id; any other pet keeps the procedural mesh. */
    public static final Set<String> IDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "pet_copper_golem", "pet_fire_dragon", "pet_owl", "pet_seraphim", "pet_shark"
    )));

    private AuthoredPetModels() {
    }

    /** True when a catalogue pet id has a hand-authored mesh. */
    public static boolean hasAuthoredModel(String petId) {
        return petId != null && IDS.contains(petId);
    }

    /** The authored model file for a pet id, or null when it has none. */
    public static String fileFor(String petId) {
        return hasAuthoredModel(petId) ? petId + ".geo.json" : null;
    }

    /** The texture file for a pet id, or null when it has no authored model. */
    public static String textureFor(String petId) {
        return hasAuthoredModel(petId) ? petId + ".png" : null;
    }

    /**
     * Loads a pet's authored model from the pack assets, retargeted to {@code geometry.chimera_pet}
     * and with its bones renamed onto the gait vocabulary so it animates.
     *
     * @return the model JSON, or null when the id has no model or the asset is missing/bad
     */
    public static String loadFromAssets(AuthoredGeometry.AssetOpener assets, String petId) {
        String file = fileFor(petId);
        if (assets == null || file == null) return null;
        String model = AuthoredGeometry.loadFromAssets(assets, DIR, file, PetGeometry.GEOMETRY_ID);
        return model == null ? null : PetBoneRetarget.apply(model);
    }

    /** Loads a pet's authored model from a directory on disk (used by the JVM tests). */
    public static String loadFromDir(File dir, String petId) {
        String file = fileFor(petId);
        if (dir == null || file == null) return null;
        String model = AuthoredGeometry.loadFromFile(new File(dir, file), PetGeometry.GEOMETRY_ID);
        return model == null ? null : PetBoneRetarget.apply(model);
    }
}
