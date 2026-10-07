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
