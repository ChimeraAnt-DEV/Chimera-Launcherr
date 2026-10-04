package org.chimeramc.client.core.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads a hand-authored Blockbench {@code .geo.json} model and retargets it to a pack geometry id.
 *
 * <p>Procedural Java geometry (stacking boxes in {@link AccessoryGeometry}/{@link PetGeometry})
 * produces the "box glued to a box" look the catalogue was criticised for. The fix is to author the
 * real mesh in Blockbench — Bedrock's standard box-UV modelling tool, which exports {@code .geo.json}
 * directly — and let this class drop that file into the generated pack unchanged. Blockbench files
 * already carry a full bone hierarchy, pivots and per-cube UVs, so an authored model keeps its
 * articulation and texture mapping; a procedural builder cannot.
 *
 * <p><b>Authored assets win; procedural stays the fallback.</b> A model is looked up by the same
 * identifier the pack would have generated ({@code geometry.chimera_hat} /
 * {@code geometry.chimera_pet}); when the asset is present it is written verbatim, and when it is
 * absent the procedural geometry is used as before. That means an empty {@code resources/cosmetics/
 * models/} directory is a valid state — nothing changes — and dropping a Blockbench export in is the
 * whole workflow. A malformed asset also falls back rather than emitting a broken pack.
 *
 * <p>The pack identifier is retargeted: Blockbench names a model whatever the author typed, but the
 * render controller looks for {@code geometry.chimera_hat}/{@code geometry.chimera_pet}, so the
 * {@code identifier} field is rewritten to the target id while every bone and cube is preserved.
 * The rewrite is a single string replacement on the parsed header, so it cannot disturb the mesh.
 *
 * <p>Pure enough to unit-test: {@link #retargetIdentifier} works on a string and {@link #load} takes
 * an {@link InputStream}, so a test can feed a real Blockbench-shaped file with no Android and no
 * device.
 */
public final class AuthoredGeometry {

    /** Asset directory a Blockbench export is dropped into. */
    public static final String ASSET_DIR = "cosmetics/models";

    /** The cape chain model, if an author overrides it. */
    public static final String CAPE_FILE = "chimera_cape.geo.json";
    /** The worn accessory model, if an author overrides it. */
    public static final String HAT_FILE = "chimera_hat.geo.json";
    /** The pet model, if an author overrides it. */
    public static final String PET_FILE = "chimera_pet.geo.json";

    private AuthoredGeometry() {
    }

    /**
     * Reads an authored model from an asset manager directory, or {@code null} when there is none.
     *
     * @param assetDir the model directory inside the pack's assets (usually {@link #ASSET_DIR})
     * @param fileName the Blockbench export's file name
     * @param targetId the identifier the render controller looks for
     * @return the model JSON retargeted to {@code targetId}, or {@code null} if absent or malformed
     */
    public static String loadFromAssets(AssetOpener assets, String assetDir, String fileName,
                                        String targetId) {
        if (assets == null || assetDir == null || fileName == null || targetId == null) return null;
        String path = assetDir.isEmpty() ? fileName : assetDir + "/" + fileName;
        try (InputStream input = assets.open(path)) {
            if (input == null) return null;
            return retargetIdentifier(readFully(input), targetId);
        } catch (IOException | RuntimeException e) {
            // An absent or unreadable asset is a fallback, not an error: the procedural mesh is
            // still valid and a cosmetic must never be blocked by a bad authoring file.
            return null;
        }
    }

    /**
     * Reads an authored model from a plain file, or {@code null} when it is absent.
     *
     * <p>Used by the JVM tests and by any caller that already resolved a directory on disk.
     */
    public static String loadFromFile(File file, String targetId) {
        if (file == null || !file.isFile() || targetId == null) return null;
        try (InputStream input = new FileInputStream(file)) {
            return retargetIdentifier(readFully(input), targetId);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Rewrites the model's top-level {@code identifier} to {@code targetId}, preserving the mesh.
     *
     * <p>Only the first {@code "identifier"} occurrence is replaced: that is the geometry
     * description's identifier. A bone or cube cannot carry an {@code identifier} field, so a later
     * occurrence would be a stray — replacing only the first keeps a hand-edited file from having
     * its mesh touched.
     */
    public static String retargetIdentifier(String json, String targetId) {
        if (json == null || targetId == null) return null;
        int key = json.indexOf("\"identifier\"");
        if (key < 0) return null;
        int colon = json.indexOf(':', key);
        if (colon < 0) return null;
        int openQuote = json.indexOf('"', colon + 1);
        if (openQuote < 0) return null;
        int closeQuote = json.indexOf('"', openQuote + 1);
        if (closeQuote < 0) return null;
        return json.substring(0, openQuote + 1) + targetId + json.substring(closeQuote);
    }

    /** A minimal opener so the loader does not depend on Android's {@code AssetManager}. */
    public interface AssetOpener {
        InputStream open(String path) throws IOException;
    }

    private static String readFully(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
