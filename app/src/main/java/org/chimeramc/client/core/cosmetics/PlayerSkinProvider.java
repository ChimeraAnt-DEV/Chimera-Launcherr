package org.chimeramc.client.core.cosmetics;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import org.chimeramc.client.core.content.ResourcePackItem;
import org.chimeramc.client.core.content.ResourcePackManager;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

/**
 * Finds and normalises the player's actual skin texture, so the cosmetics preview shows their
 * own character rather than a stand-in.
 *
 * <p>Resolution order matters. A skin the player explicitly set in the launcher wins, because
 * that is the one they told us they use. Otherwise the applied skin pack's first texture is
 * used. The built-in fallback is only reached when neither exists, and the preview says so
 * instead of quietly showing a stranger's face.
 *
 * <p>Textures are normalised to a 64x64 atlas. Legacy 64x32 skins are copied into the top half
 * of a 64x64 bitmap, which is the same trick the game uses: the modern UV layout is a superset,
 * so the legacy regions land in the right place and the second-layer regions come out
 * transparent, exactly as they should for a skin that has no overlay.
 */
public final class PlayerSkinProvider {

    private static final String SKINS_PREFS = "skins_state";
    private static final String KEY_APPLIED_NAME = "applied_name";

    /** Where a skin the user picked directly in the launcher is remembered. */
    private static final String PREFS = "cosmetics_skin";
    private static final String KEY_CUSTOM_SKIN_PATH = "custom_skin_path";

    /** The result of a lookup: the atlas plus where it came from. */
    public static final class SkinBitmap {
        public final Bitmap bitmap;
        /** True when this is the built-in placeholder rather than the player's own skin. */
        public final boolean isFallback;
        public final String sourceName;

        SkinBitmap(Bitmap bitmap, boolean isFallback, String sourceName) {
            this.bitmap = bitmap;
            this.isFallback = isFallback;
            this.sourceName = sourceName;
        }
    }

    private PlayerSkinProvider() {
    }

    /** Remembers a skin file the player chose in the launcher. */
    public static void setCustomSkinPath(Context context, String path) {
        prefs(context).edit().putString(KEY_CUSTOM_SKIN_PATH, path).apply();
    }

    /**
     * Records the skin pack the player activated for the selected instance.
     *
     * <p>Written by the Skins screen when it activates or removes a pack. Without it the lookup
     * below has no pack name to match against, so an applied pack was never found and the
     * preview silently fell back to the placeholder — the pack was active in the game but the
     * character on screen was still a stranger's.
     */
    public static void setAppliedSkinPackName(Context context, String packName) {
        SharedPreferences.Editor editor = skinsPrefs(context).edit();
        if (packName == null || packName.isEmpty()) {
            editor.remove(KEY_APPLIED_NAME);
        } else {
            editor.putString(KEY_APPLIED_NAME, packName);
        }
        editor.apply();
    }

    public static String getAppliedSkinPackName(Context context) {
        return skinsPrefs(context).getString(KEY_APPLIED_NAME, null);
    }

    public static String getCustomSkinPath(Context context) {
        return prefs(context).getString(KEY_CUSTOM_SKIN_PATH, null);
    }

    /**
     * Resolves the player's skin.
     *
     * <p>Never throws and never returns null: a decode failure degrades to the fallback, because
     * a cosmetics screen that crashes is worse than one showing a generic character.
     */
    public static SkinBitmap resolve(Context context) {
        String custom = getCustomSkinPath(context);
        if (custom != null && !custom.isEmpty()) {
            Bitmap decoded = decodeFile(new File(custom));
            if (decoded != null) {
                return new SkinBitmap(normalise(decoded), false, new File(custom).getName());
            }
        }

        File applied = findAppliedSkinFile(context);
        if (applied != null) {
            Bitmap decoded = decodeFile(applied);
            if (decoded != null) {
                return new SkinBitmap(normalise(decoded), false, applied.getName());
            }
        }

        return new SkinBitmap(fallbackSkin(), true, "Chimera default");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences skinsPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(SKINS_PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Locates the texture belonging to the applied skin pack.
     *
     * The pack name recorded when the player applied it is matched against the loaded packs, so
     * an unrelated installed pack cannot be shown by accident. Inside the pack, any PNG that
     * looks like a skin atlas is accepted, preferring one under a {@code skins/} folder.
     */
    private static File findAppliedSkinFile(Context context) {
        try {
            SharedPreferences skins = skinsPrefs(context);
            String appliedName = skins.getString(KEY_APPLIED_NAME, null);
            if (appliedName == null || appliedName.isEmpty()) return null;

            VersionManager versionManager = VersionManager.get(context);
            GameVersion version = versionManager.getSelectedVersion();
            if (version == null) return null;

            ResourcePackManager manager = new ResourcePackManager(context);
            manager.setCurrentVersion(version);
            List<ResourcePackItem> packs = manager.getSkinPacks();
            if (packs == null) return null;

            for (ResourcePackItem pack : packs) {
                if (pack == null || pack.getPackName() == null) continue;
                if (!appliedName.equals(pack.getPackName())) continue;
                File found = findSkinTexture(pack.getFile());
                if (found != null) return found;
            }
        } catch (Throwable ignored) {
            // A missing version, an unreadable pack list or a corrupt pack must not stop the
            // preview from drawing something.
        }
        return null;
    }

    private static File findSkinTexture(File packDir) {
        if (packDir == null) return null;
        File skinsDir = new File(packDir, "skins");
        File fromSkins = firstSkinIn(skinsDir);
        if (fromSkins != null) return fromSkins;
        return firstSkinIn(packDir);
    }

    private static File firstSkinIn(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        // Sort so the choice is stable between openings instead of following the filesystem.
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) {
            if (f.isDirectory()) {
                File nested = firstSkinIn(f);
                if (nested != null) return nested;
            } else if (looksLikeSkin(f)) {
                return f;
            }
        }
        return null;
    }

    /**
     * A skin atlas is a PNG whose dimensions are a multiple of 64x32. Reading only the header
     * avoids decoding every file in a pack just to reject it.
     */
    private static boolean looksLikeSkin(File file) {
        if (file == null || !file.getName().toLowerCase().endsWith(".png")) return false;
        try (FileInputStream in = new FileInputStream(file)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) return false;
            return w % 64 == 0 && h % 32 == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Bitmap decodeFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Copies a skin into a 64x64 atlas at its native scale.
     *
     * <p>The atlas is a 64x32 grid, so the source is scaled by a whole factor of its width and
     * drawn at the top-left: a 64x32 legacy skin lands in the top half (where the modern UV
     * layout expects those regions) and an HD 128x128 skin is halved to 64x64. Stretching the
     * source to fill 64x64 instead would vertically double a legacy skin, putting the arms and
     * legs regions where the hat and body overlay belong — the character then renders with its
     * own textures in the wrong places.
     */
    public static Bitmap normalise(Bitmap source) {
        if (source == null) return fallbackSkin();
        Bitmap out = Bitmap.createBitmap(SkinModel.ATLAS_SIZE, SkinModel.ATLAS_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();
        paint.setFilterBitmap(false);
        int[] dst = atlasDrawSize(source.getWidth(), source.getHeight());
        canvas.drawBitmap(source, new Rect(0, 0, source.getWidth(), source.getHeight()),
                new Rect(0, 0, dst[0], dst[1]), paint);
        return out;
    }

    /**
     * Where a source texture lands in the 64x64 atlas, as {width, height} in atlas pixels.
     *
     * <p>Pure so the sizing rule is testable without a Bitmap: the atlas is a 64x32 grid, so the
     * source is divided by a whole factor of its width and never scaled up. A 64x32 legacy skin
     * therefore keeps its height and occupies the top half, and a 128x128 HD skin becomes
     * 64x64. Filling the whole atlas instead would stretch a legacy skin vertically and sample
     * the wrong region for every limb.
     */
    public static int[] atlasDrawSize(int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return new int[]{SkinModel.ATLAS_SIZE, SkinModel.ATLAS_SIZE};
        }
        int scale = Math.max(1, sourceWidth / SkinModel.ATLAS_SIZE);
        int dstW = Math.max(1, Math.min(SkinModel.ATLAS_SIZE, sourceWidth / scale));
        int dstH = Math.max(1, Math.min(SkinModel.ATLAS_SIZE, sourceHeight / scale));
        return new int[]{dstW, dstH};
    }

    /**
     * A recognisable default character, drawn rather than shipped as an asset.
     *
     * <p>The placeholder is deliberately greyscale. It is what the preview shows before the player
     * imports a skin or applies a skin pack, and a coloured stand-in (the brand-violet shirt it
     * used to wear) read as an actual skin — the blue/white result users saw was this placeholder
     * wearing brand colours, not a rendering fault. A neutral grey, correctly shaded character is
     * unmistakably a placeholder while still looking like a Minecraft model.
     *
     * <p>Painted from the same UV table the renderer samples, so the stand-in cannot drift from
     * the model. A hardcoded region list previously used the wrong columns for the left arm and
     * leg, so the placeholder rendered with mismatched limbs.
     */
    public static Bitmap fallbackSkin() {
        Bitmap bmp = Bitmap.createBitmap(SkinModel.ATLAS_SIZE, SkinModel.ATLAS_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        paint.setFilterBitmap(false);

        // Palette: a single grey ramp, no chroma, so this can never be mistaken for a real skin.
        final int skinTone = 0xFF9AA0A6;
        final int hair = 0xFF5E646B;
        final int shirt = 0xFF7C838B;
        final int shirtShade = 0xFF6A7078;
        final int hand = 0xFFB4B9BF;
        final int trousers = 0xFF545A61;
        final int shoes = 0xFF3A3E44;
        final int eyeWhite = 0xFFE6E9EC;
        final int eyePupil = 0xFF2B2F36;

        // Fill the whole atlas with the skin tone, then paint each region over it.
        canvas.drawColor(skinTone);

        // Head: hair on the top and back, face on the front.
        fill(canvas, paint, 8, 0, 8, 8, hair);
        fill(canvas, paint, 16, 0, 8, 8, hair);
        fill(canvas, paint, 0, 8, 8, 8, hair);
        fill(canvas, paint, 24, 8, 8, 8, hair);
        fill(canvas, paint, 8, 8, 8, 8, skinTone);
        fill(canvas, paint, 16, 8, 8, 8, hair);
        // A brow and two eyes so the face reads as a face at preview size.
        fill(canvas, paint, 9, 11, 6, 1, hair);
        fill(canvas, paint, 10, 12, 2, 2, eyeWhite);
        fill(canvas, paint, 12, 12, 1, 2, eyePupil);
        fill(canvas, paint, 14, 12, 1, 2, eyePupil);
        fill(canvas, paint, 15, 12, 1, 2, eyeWhite);

        // Hair volume on the second layer: top, back and both sides, leaving the front open so
        // the face shows through. The renderer drops an all-transparent overlay crop, so the
        // unpainted front simply costs nothing.
        fill(canvas, paint, 40, 0, 8, 8, hair);
        fill(canvas, paint, 48, 0, 8, 8, hair);
        fill(canvas, paint, 32, 8, 8, 8, hair);
        fill(canvas, paint, 48, 8, 8, 8, hair);
        fill(canvas, paint, 56, 8, 8, 8, hair);

        // Torso: a grey shirt with the side and back strips shaded a step darker.
        fill(canvas, paint, 20, 20, 8, 12, shirt);
        fill(canvas, paint, 16, 20, 4, 12, shirtShade);
        fill(canvas, paint, 28, 20, 4, 12, shirtShade);
        fill(canvas, paint, 32, 20, 8, 12, shirtShade);
        fill(canvas, paint, 20, 16, 8, 4, shirt);
        fill(canvas, paint, 28, 16, 8, 4, shirtShade);

        // Limbs: painted from the same UV table the renderer samples, so the stand-in cannot
        // drift from the model.
        for (SkinModel.Box box : SkinModel.boxes()) {
            boolean arm = box.id.startsWith("arm");
            boolean leg = box.id.startsWith("leg");
            if (!arm && !leg) continue;
            int limb = arm ? shirt : trousers;
            int limbShade = arm ? shirtShade : 0xFF474D54;
            int end = arm ? hand : shoes;
            for (SkinModel.Face face : SkinModel.Face.values()) {
                SkinModel.Uv uv = box.baseUv(face);
                if (uv == null) continue;
                int color;
                switch (face) {
                    case FRONT:
                    case TOP:
                    case BOTTOM:
                        color = limb;
                        break;
                    default:
                        color = limbShade;
                        break;
                }
                fill(canvas, paint, uv.u, uv.v, uv.w, uv.h, color);
            }
            // The lower quarter of the front and side strips is the exposed hand/shoe.
            for (SkinModel.Face f : new SkinModel.Face[]{
                    SkinModel.Face.FRONT, SkinModel.Face.LEFT,
                    SkinModel.Face.RIGHT, SkinModel.Face.BACK}) {
                SkinModel.Uv uv = box.baseUv(f);
                if (uv == null) continue;
                fill(canvas, paint, uv.u, uv.v + uv.h - 4, uv.w, 4, end);
            }
        }

        return bmp;
    }

    private static void fill(Canvas canvas, Paint paint, int u, int v, int w, int h, int color) {
        paint.setColor(color);
        canvas.drawRect(u, v, u + w, v + h, paint);
    }
}
