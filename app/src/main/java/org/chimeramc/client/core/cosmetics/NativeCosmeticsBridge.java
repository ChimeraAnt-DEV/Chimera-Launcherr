package org.chimeramc.client.core.cosmetics;

import android.graphics.Bitmap;

import org.chimeramc.client.preloader.PreloaderInput;

/**
 * The launcher-side half of the native cosmetics renderer.
 *
 * <p>It turns a cape or accessory image into the raw RGBA the native registry expects and hands it
 * across the JNI boundary, keyed by the player id the cosmetic belongs to. The native side stores
 * the pixels and its skin/cape and texture hooks would substitute them; until a substitution slot
 * resolves for a given build, the registry is still populated and the resource pack carries the
 * cosmetic, so nothing regresses.
 *
 * <p><b>Fail-closed.</b> Every call is guarded ({@link PreloaderInput} swallows a missing symbol),
 * so a build whose library predates this surface simply does no native work and the pack path is
 * unchanged. The launcher never assumes the native path is live; it asks {@link #isLive()}.
 */
public final class NativeCosmeticsBridge {

    private NativeCosmeticsBridge() {
    }

    /**
     * A stable 64-bit key for a string id, so a UUID or a texture name maps to a native key.
     * FNV-1a, matching the geometry hash rule on the native side.
     */
    public static long keyFor(String id) {
        if (id == null) return 0L;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) {
            hash ^= id.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * Uploads a cape image for a player id. A null bitmap clears that player's override.
     *
     * @return true when the pixels were handed to the native registry
     */
    public static boolean setCapeFor(String playerId, Bitmap image) {
        long key = keyFor(playerId);
        if (key == 0L) return false;
        if (image == null) {
            PreloaderInput.setCapeOverride(key, null, 0, 0);
            return true;
        }
        byte[] rgba = toRgba(image);
        if (rgba == null) return false;
        PreloaderInput.setCapeOverride(key, rgba, image.getWidth(), image.getHeight());
        return true;
    }

    /** Uploads a texture-id override (the in-memory texture swap). Null clears it. */
    public static boolean setTextureFor(String textureId, Bitmap image) {
        long key = keyFor(textureId);
        if (key == 0L) return false;
        if (image == null) {
            PreloaderInput.setTextureOverride(key, null, 0, 0);
            return true;
        }
        byte[] rgba = toRgba(image);
        if (rgba == null) return false;
        PreloaderInput.setTextureOverride(key, rgba, image.getWidth(), image.getHeight());
        return true;
    }

    /** Publishes the geometry blob the render hook should draw. */
    public static void setRenderGeometry(byte[] geometry) {
        PreloaderInput.setRenderGeometry(geometry);
    }

    /**
     * Publishes the local player's equipped cosmetics into the native registry.
     *
     * <p>Renders each equipped piece's texture to RGBA and keys it by the piece's catalogue id, so
     * the native skin/cape and texture hooks can substitute it for the local player. Called at
     * launch and on every equip, so the registry tracks the equipped set. A piece that is not
     * equipped clears its entry.
     *
     * <p>Fail-closed: without the native library every upload is a no-op and the pack path is
     * unchanged.
     *
     * @param cape      the equipped cape, or null
     * @param accessory the equipped accessory, or null
     * @param pet       the equipped pet, or null
     */
    public static void publishLocal(CosmeticCatalog.Cape cape,
                                    CosmeticCatalog.Accessory accessory,
                                    CosmeticCatalog.Pet pet) {
        publishCape(cape);
        publishAccessory(accessory);
        publishPet(pet);
    }

    /** Uploads a cape's texture to the native registry (or clears it) keyed by its id. */
    public static void publishCape(CosmeticCatalog.Cape cape) {
        if (cape == null) {
            PreloaderInput.clearCapeOverrides();
            return;
        }
        byte[] png = CapeTexturePainter.paint(cape.color, cape.trimColor, cape.accentColor,
                cape.pattern, cape.branded);
        Bitmap image = decode(png);
        if (image != null) setCapeFor(cape.id, image);
    }

    /** Uploads an accessory's texture to the native registry keyed by its id. */
    public static void publishAccessory(CosmeticCatalog.Accessory accessory) {
        if (accessory == null) {
            PreloaderInput.clearTextureOverrides();
            return;
        }
        byte[] png = AccessoryTexturePainter.paint(accessory.color, accessory.accentColor);
        Bitmap image = decode(png);
        if (image != null) setTextureFor(accessory.id, image);
    }

    /** Uploads a pet's texture to the native registry keyed by its id. */
    public static void publishPet(CosmeticCatalog.Pet pet) {
        if (pet == null) return;
        byte[] png = PaintedAtlas.paint(pet.color, pet.accentColor,
                PetGeometry.TEXTURE_WIDTH, PetGeometry.TEXTURE_HEIGHT,
                PetGeometry.UV_ACCENT_Y, pet.color ^ (pet.species.ordinal() * 131));
        Bitmap image = decode(png);
        if (image != null) setTextureFor(pet.id, image);
    }

    /** Decodes PNG bytes to a bitmap, or null when the bytes are not a decodable image. */
    private static Bitmap decode(byte[] png) {
        if (png == null || png.length == 0) return null;
        try {
            return android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Replaces the cape image on a live {@code SerializedSkinRef}.
     *
     * <p>This is the actual pixel substitution: the cape the renderer samples is the
     * {@code mce::Image} at {@code skinRef + 0xa8} (see {@link SkinImageLayout}), and this copies a
     * supplied image struct over it. The image must be a struct the <em>engine</em> built (via its
     * own loader), not raw RGBA — the binary does not expose {@code mce::Image}'s internal fields,
     * so the launcher cannot synthesise a valid one and must not guess at where the buffer pointer
     * sits.
     *
     * <p>Fails closed: a null address or an image shorter than {@link SkinImageLayout#IMAGE_SIZE}
     * is refused rather than written, and without the native library the call is a no-op.
     *
     * @param skinRefAddress the address of a {@code SerializedSkinRef} (a player's skin)
     * @param imageStruct    the engine-built image struct, at least {@code IMAGE_SIZE} bytes
     * @return true when the struct was copied
     */
    public static boolean swapCapeImage(long skinRefAddress, byte[] imageStruct) {
        if (skinRefAddress == 0L || imageStruct == null
                || imageStruct.length < SkinImageLayout.IMAGE_SIZE) {
            return false;
        }
        return PreloaderInput.swapCapeImage(skinRefAddress, imageStruct);
    }

    /** The address of the cape image inside a skin struct, or 0 when the base is null. */
    public static long capeImageAddress(long skinRefAddress) {
        return SkinImageLayout.capeImageAddress(skinRefAddress);
    }

    /** The engine's image-loader address, or 0 when unresolved on this build. */
    public static long imageLoaderAddress() {
        return PreloaderInput.imageLoaderAddress();
    }

    /** True when the engine can build an image, so the PNG→image→swap path can run. */
    public static boolean canBuildImages() {
        return imageLoaderAddress() != 0L;
    }

    /**
     * The complete cape replacement: build a valid {@code mce::Image} from PNG bytes with the
     * engine's own loader, then copy it over the cape member of the player's skin.
     *
     * <p>This is the end-to-end path. The engine constructs the image (so its internal buffer
     * pointer is correct by construction), and {@link #swapCapeImage} writes it at the verified
     * offset. Every step fails closed: no loader, bad PNG, or a null skin address simply returns
     * false and leaves the vanilla cape alone.
     *
     * @param skinRefAddress the address of the player's {@code SerializedSkinRef}
     * @param capePng        the custom cape as PNG bytes
     * @return true when the engine built the image and it was installed
     */
    public static boolean applyCapePng(long skinRefAddress, byte[] capePng) {
        if (skinRefAddress == 0L || capePng == null || capePng.length == 0) return false;
        byte[] image = PreloaderInput.buildCapeImage(capePng);
        if (image == null || image.length < SkinImageLayout.IMAGE_SIZE) return false;
        return swapCapeImage(skinRefAddress, image);
    }

    /** Clears every native override. Called when cosmetics are unequipped or a session ends. */
    public static void clear() {
        PreloaderInput.clearCapeOverrides();
        PreloaderInput.clearTextureOverrides();
    }

    /** True once the native cosmetics hook has fired this session. */
    public static boolean isLive() {
        return PreloaderInput.isCosmeticsHookLive();
    }

    /** The current native cosmetics counters, or null when unavailable. */
    public static int[] stats() {
        return PreloaderInput.readCosmeticsStats();
    }

    /** Whether the native registry currently holds at least one override. */
    public static boolean hasOverrides() {
        return PreloaderInput.capeOverrideCount() > 0
                || PreloaderInput.textureOverrideCount() > 0;
    }

    // --- The mce::Image pipeline (engine-owned pixel substitution) ---------------------------
    //
    // The loader seam is the one place a launcher-built pixel buffer is safe: the engine receives
    // the RGBA through its own `loadImageFromMemory`, allocates and owns the backing store, and
    // a substitution is a *content-addressed* rule ("whenever the engine loads these exact bytes,
    // build the image from this RGBA instead"), so the lifecycle never involves a launcher `malloc`
    // the game could free. The launcher registers the cosmetic's PNG bytes and its painted RGBA;
    // the native hook performs the swap at upload time. Fail-closed throughout: without a resolved
    // loader address none of this is live and the pack path is unchanged.

    /**
     * Registers a content-addressed substitution: when the engine loads {@code sourceBytes} (the
     * bytes the vanilla skin/cape texture ships as), it gets {@code rgba} instead.
     *
     * @return true when the rule was handed to the native registry (which is always, even on a
     *         build without a live loader — the rule simply never fires there).
     */
    public static boolean registerSubstitution(byte[] sourceBytes, Bitmap replacement) {
        if (sourceBytes == null || sourceBytes.length == 0) return false;
        if (replacement == null) {
            PreloaderInput.setContentSubstitution(sourceBytes, null, 0, 0);
            return true;
        }
        byte[] rgba = toRgba(replacement);
        if (rgba == null) return false;
        PreloaderInput.setContentSubstitution(sourceBytes, rgba,
                replacement.getWidth(), replacement.getHeight());
        return true;
    }

    /** Clears every content-addressed substitution. */
    public static void clearSubstitutions() {
        PreloaderInput.clearContentSubstitutions();
    }

    /** Number of registered content substitutions. */
    public static int substitutionCount() {
        return PreloaderInput.substitutionCount();
    }

    /**
     * Arms the one-shot override: the very next image the engine builds is replaced with {@code
     * rgba}. Used when the launcher itself triggers one known upload.
     */
    public static boolean armNextImageOverride(Bitmap replacement) {
        if (replacement == null) {
            PreloaderInput.clearNextImageOverride();
            return true;
        }
        byte[] rgba = toRgba(replacement);
        if (rgba == null) return false;
        PreloaderInput.armNextImageOverride(rgba, replacement.getWidth(), replacement.getHeight());
        return true;
    }

    /** True once the engine image-pipeline hook has run this session. */
    public static boolean isImagePipelineLive() {
        return PreloaderInput.isMceImageHookLive();
    }

    /** True when the init probe confirmed the engine image loader on this build. */
    public static boolean isImagePathVerified() {
        return PreloaderInput.isImagePathVerified();
    }

    /** {hookCalls, substitutions, overrides, bufferBytesSeen}, or null when unavailable. */
    public static int[] imagePipelineStats() {
        return PreloaderInput.readMceImageHookStats();
    }

    /** True when a proven texture-cache flusher is available post-substitution. */
    public static boolean isTextureFlushAvailable() {
        return PreloaderInput.isTextureCacheFlushAvailable();
    }

    /**
     * A bitmap's pixels as tightly packed RGBA, row-major, top-left origin — the layout the native
     * registry documents. A zero-size or null bitmap yields null.
     */
    static byte[] toRgba(Bitmap image) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) return null;
        int[] argb = new int[width * height];
        image.getPixels(argb, 0, width, 0, 0, width, height);
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            rgba[i * 4] = (byte) ((c >> 16) & 0xFF);      // R
            rgba[i * 4 + 1] = (byte) ((c >> 8) & 0xFF);   // G
            rgba[i * 4 + 2] = (byte) (c & 0xFF);          // B
            rgba[i * 4 + 3] = (byte) ((c >>> 24) & 0xFF); // A
        }
        return rgba;
    }
}
