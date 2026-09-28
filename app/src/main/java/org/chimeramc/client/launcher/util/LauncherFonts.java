package org.chimeramc.client.util;

import android.content.Context;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chimeramc.client.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalogue of fonts the launcher can wear.
 *
 * <p>Every entry is a font bundled in {@code res/font}, so no download is needed and a chosen
 * face is available offline. The {@link #key} is what is persisted; the resource id is resolved
 * through {@link #typeface} which never throws, so a key that no longer maps to a bundled file
 * degrades to the system face rather than crashing every screen.
 */
public final class LauncherFonts {

    /** Persisted when the user has not picked a face; also the app's original look. */
    public static final String DEFAULT_KEY = "misans";

    public static final class Entry {
        public final String key;
        public final int labelRes;
        public final int fontRes;

        Entry(String key, int labelRes, int fontRes) {
            this.key = key;
            this.labelRes = labelRes;
            this.fontRes = fontRes;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, Typeface> CACHE = new LinkedHashMap<>();

    static {
        add("misans", R.string.font_misans, R.font.misans);
        add("inter", R.string.font_inter, R.font.inter);
        add("opensans", R.string.font_opensans, R.font.opensans);
        add("lato", R.string.font_lato, R.font.lato);
        add("montserrat", R.string.font_montserrat, R.font.montserrat);
        add("poppins", R.string.font_poppins, R.font.poppins);
        add("nunito", R.string.font_nunito, R.font.nunito);
        add("quicksand", R.string.font_quicksand, R.font.quicksand);
        add("raleway", R.string.font_raleway, R.font.raleway);
        add("rubik", R.string.font_rubik, R.font.rubik);
        add("worksans", R.string.font_worksans, R.font.worksans);
        add("barlow", R.string.font_barlow, R.font.barlow);
        add("manrope", R.string.font_manrope, R.font.manrope);
        add("dmsans", R.string.font_dmsans, R.font.dmsans);
        add("spacegrotesk", R.string.font_spacegrotesk, R.font.spacegrotesk);
        add("outfit", R.string.font_outfit, R.font.outfit);
        add("sora", R.string.font_sora, R.font.sora);
        add("merriweather", R.string.font_merriweather, R.font.merriweather);
        add("playfairdisplay", R.string.font_playfairdisplay, R.font.playfairdisplay);
        add("lora", R.string.font_lora, R.font.lora);
        add("jetbrainsmono", R.string.font_jetbrainsmono, R.font.jetbrainsmono);
        add("firacode", R.string.font_firacode, R.font.firacode);
        add("bebasneue", R.string.font_bebasneue, R.font.bebasneue);
        add("pressstart2p", R.string.font_pressstart2p, R.font.pressstart2p);
        add("cinzel", R.string.font_cinzel, R.font.cinzel);
        add("josefinsans", R.string.font_josefinsans, R.font.josefinsans);
        add("caveat", R.string.font_caveat, R.font.caveat);
        add("orbitron", R.string.font_orbitron, R.font.orbitron);
    }

    private LauncherFonts() {}

    private static void add(String key, int labelRes, int fontRes) {
        ENTRIES.add(new Entry(key, labelRes, fontRes));
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static int indexOf(String key) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).key.equals(key)) return i;
        }
        return 0;
    }

    public static String keyAt(int index) {
        if (index < 0 || index >= ENTRIES.size()) return DEFAULT_KEY;
        return ENTRIES.get(index).key;
    }

    /**
     * Loads a face by key, caching per key.
     *
     * Returns the system default when the key is unknown or the file cannot be read, so a
     * corrupted install shows a readable app rather than nothing.
     */
    public static Typeface typeface(Context context, String key) {
        String resolved = key == null ? DEFAULT_KEY : key;
        Typeface cached = CACHE.get(resolved);
        if (cached != null) return cached;

        Typeface tf = null;
        int index = indexOf(resolved);
        int fontRes = ENTRIES.get(index).fontRes;
        try {
            tf = androidx.core.content.res.ResourcesCompat.getFont(context, fontRes);
        } catch (Exception e) {
            tf = null;
        }
        if (tf == null) tf = Typeface.DEFAULT;
        CACHE.put(resolved, tf);
        return tf;
    }

    /** Applies the chosen face to every {@link TextView} below {@code root}, inclusive. */
    public static void applyRecursive(View root, Typeface typeface) {
        if (root instanceof TextView) {
            ((TextView) root).setTypeface(typeface);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), typeface);
            }
        }
    }
}
