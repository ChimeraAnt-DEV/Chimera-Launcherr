package org.chimeramc.client.core.cosmetics;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The hand-authored hat meshes, one per accessory kind, loaded from the pack's assets.
 *
 * <p>The procedural {@link AccessoryGeometry} stacks boxes; a real Blockbench export is a sculpted
 * hat with its own UV-mapped texture. This maps an {@link CosmeticCatalog.AccessoryKind} to its
 * authored model and texture; {@link CapeResourcePackBuilder} asks it first and falls back to the
 * procedural mesh when the asset is absent. The accessory geometry always carries a single
 * {@code acc} bone pivoted at the neck (0, 24, 0), which the pack's
 * {@code animation.chimera_hat_tilt} turns by the head's look — so an authored model named
 * {@code acc} follows the head automatically. A model with a different root bone is retargeted.
 */
public final class AuthoredHatModels {

    /** Asset directory the authored hat models live in. */
    public static final String DIR = "cosmetics/models/hats";

    /**
     * Accessory kind to authored-model file. Only kinds with a real export are listed; the rest
     * keep the procedural mesh. Adding a hat is a file plus one entry here.
     */
    private static final Map<CosmeticCatalog.AccessoryKind, String> FILES = new LinkedHashMap<>();

    static {
        FILES.put(CosmeticCatalog.AccessoryKind.CAP, "cap.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.BEANIE, "beanie.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.CROWN, "crown.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.TOPHAT, "tophat.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.WIZARD_HAT, "wizard_hat.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.HALO, "halo.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.FLOWER, "flower.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.MASK, "mask.geo.json");
        FILES.put(CosmeticCatalog.AccessoryKind.EAR, "ear.geo.json");
    }

    private AuthoredHatModels() {
    }

    /** The authored model file for a kind, or null when it has none. */
    public static String fileFor(CosmeticCatalog.AccessoryKind kind) {
        return kind == null ? null : FILES.get(kind);
    }

    /** True when a kind has a hand-authored mesh. */
    public static boolean hasAuthoredModel(CosmeticCatalog.AccessoryKind kind) {
        return fileFor(kind) != null;
    }

    /** The texture file for a kind, or null when it has no authored model. */
    public static String textureFor(CosmeticCatalog.AccessoryKind kind) {
        String model = fileFor(kind);
        return model == null ? null : model.replace(".geo.json", ".png");
    }

    /**
     * Loads a kind's authored model from the pack assets, retargeted to the hat geometry id, and
     * with its root bone renamed to {@code acc} so the head-tilt animation drives it.
     *
     * @return the model JSON, or null when the kind has no model or the asset is missing/bad
     */
    public static String loadFromAssets(AuthoredGeometry.AssetOpener assets,
                                        CosmeticCatalog.AccessoryKind kind) {
        String file = fileFor(kind);
        if (assets == null || file == null) return null;
        String model = AuthoredGeometry.loadFromAssets(assets, DIR, file,
                AccessoryGeometry.GEOMETRY_ID);
        return model == null ? null : AccBoneRetarget.apply(model);
    }

    /** Loads a kind's authored model from a directory on disk (used by the JVM tests), or null. */
    public static String loadFromDir(File dir, CosmeticCatalog.AccessoryKind kind) {
        String file = fileFor(kind);
        if (dir == null || file == null) return null;
        String model = AuthoredGeometry.loadFromFile(new File(dir, file),
                AccessoryGeometry.GEOMETRY_ID);
        return model == null ? null : AccBoneRetarget.apply(model);
    }
}
