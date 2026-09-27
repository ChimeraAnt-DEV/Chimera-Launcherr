package org.chimeramc.client.core.antegg;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The bridge between a picked {@code content://} URI and the .AntEgg loader.
 *
 * <p>The loader is file-based (a ZIP reader needs random access to the central directory, which a
 * content stream cannot provide), so the picked document is first copied into the cache. This is
 * the same shape as the launcher's other imports, and it keeps the loader free of Android
 * {@code Uri} handling, which is what makes it unit-testable.
 */
public final class AntEggImport {

    private AntEggImport() {}

    /** Resolves the display name of a picked document, or a fallback. */
    public static String displayName(Context context, Uri uri) {
        if (context == null || uri == null) return "mod.antegg";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
            // Fall through to the path-based name; a picker without DISPLAY_NAME is not fatal.
        }
        String path = uri.getLastPathSegment();
        return path == null || path.isEmpty() ? "mod.antegg" : new File(path).getName();
    }

    /**
     * Copies a picked document to a cache file and loads it.
     *
     * <p>Returns a {@link AntEggLoader.LoadResult}; the caller shows {@code result.error} on
     * failure. The cache copy is deleted after the load, since the loader has already extracted
     * the mod into its sandbox by then.
     */
    public static AntEggLoader.LoadResult importUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return failedResult("no file was selected");
        }
        String name = displayName(context, uri);
        if (!AntEggPackage.looksLikeAntEgg(name)) {
            return failedResult("not an .AntEgg package: " + name);
        }
        File staged = new File(context.getCacheDir(), "antegg_import_" + System.currentTimeMillis()
                + AntEggPackage.EXTENSION);
        try {
            copyToFile(context, uri, staged);
            AntEggLoader.LoadResult result = AntEggLoader.loadMod(context, staged);
            if (!result.success) {
                AntEggLoader.recordFailure(context, name, result.error);
            }
            return result;
        } catch (IOException e) {
            AntEggLoader.recordFailure(context, name, e.getMessage());
            return failedResult("could not read the package: " + e.getMessage());
        } finally {
            if (staged.exists()) {
                //noinspection ResultOfMethodCallIgnored
                staged.delete();
            }
        }
    }

    private static void copyToFile(Context context, Uri uri, File destination) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destination)) {
            if (in == null) throw new IOException("cannot open the selected file");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static AntEggLoader.LoadResult failedResult(String message) {
        return AntEggLoader.LoadResult.fail(message);
    }
}
