package org.chimeramc.client.core.mods.inbuilt.vip;

import android.content.Context;
import android.graphics.Color;

import org.chimeramc.client.util.PersonalizationManager;

/**
 * The VIP Mod Menu's colour system.
 *
 * <p>Deliberately separate from {@link org.chimeramc.client.core.mods.inbuilt.overlay.ModMenuTheme}:
 * the touch Mod Menu is a dark utilitarian panel, while Screen B has to read as a paid product.
 * Where the touch theme has one card fill, this has a four-tier surface ramp (canvas → surface →
 * elevated glass → hairline), an accent ramp, and a fixed status palette, so every element draws
 * from a named token rather than an inline hex. The screen draws itself in a window, so it cannot
 * inherit the launcher theme the way an Activity can; the user's accent is resolved here.
 */
public final class VipTheme {

    // Tier 1-4 surface ramp. A violet cast on near-black keeps the whole screen in one family.
    private static final int CANVAS = 0xFF0B0A12;
    private static final int SURFACE = 0xFF141221;
    private static final int SURFACE_ELEVATED = 0xFF1E1B2E;
    private static final int SURFACE_GLASS = 0xE61A1729;
    private static final int HAIRLINE = 0xFF2E2A44;

    // Text ramp.
    private static final int TEXT_PRIMARY = 0xFFF4F1FF;
    private static final int TEXT_SECONDARY = 0xFFB6B0CC;
    private static final int TEXT_TERTIARY = 0xFF7B7591;

    // Status. Fixed, not accent-derived, so an enabled module is always the same green.
    private static final int STATUS_ACTIVE = 0xFF3DDC97;
    private static final int STATUS_OFF = 0xFF7B7591;
    private static final int STATUS_UNAVAILABLE = 0xFFFF5C7A;

    private static final int DEFAULT_ACCENT = 0xFF7C5CFF;
    private static final int ACCENT_SECONDARY = 0xFFE070C0;

    private static final int[] GROUP_PALETTE = {
            0xFF7C5CFF, // primary violet
            0xFF4E8DFF, // azure
            0xFFE070C0, // magenta
            0xFFFFB347, // amber
            0xFF3DDC97, // mint
            0xFFB07CFF, // lilac
            0xFF56C7E0, // cyan
            0xFFE0655C  // coral
    };

    private final int accent;
    private final int accentSecondary;
    private final boolean glowEnabled;

    public VipTheme(Context context) {
        int resolved = DEFAULT_ACCENT;
        boolean glow = true;
        try {
            PersonalizationManager pm = new PersonalizationManager(context);
            if (pm.hasCustomAccent()) {
                resolved = pm.getAccentColor();
            }
            glow = pm.isEnableGlowEffects();
        } catch (Throwable ignored) {
        }
        this.accent = resolved;
        this.accentSecondary = blend(resolved, ACCENT_SECONDARY, 0.5f);
        this.glowEnabled = glow;
    }

    public int canvas() {
        return CANVAS;
    }

    public int surface() {
        return SURFACE;
    }

    public int surfaceElevated() {
        return SURFACE_ELEVATED;
    }

    /** Translucent raised glass for the tab bar and dialogs. */
    public int surfaceGlass() {
        return SURFACE_GLASS;
    }

    public int hairline() {
        return HAIRLINE;
    }

    public int textPrimary() {
        return TEXT_PRIMARY;
    }

    public int textSecondary() {
        return TEXT_SECONDARY;
    }

    public int textTertiary() {
        return TEXT_TERTIARY;
    }

    public int statusActive() {
        return STATUS_ACTIVE;
    }

    public int statusOff() {
        return STATUS_OFF;
    }

    public int statusUnavailable() {
        return STATUS_UNAVAILABLE;
    }

    public int accent() {
        return accent;
    }

    public int accentSecondary() {
        return accentSecondary;
    }

    public boolean glowEnabled() {
        return glowEnabled;
    }

    /** The accent at an explicit alpha, for washes, halos and pill fills. */
    public int accentFill(int alpha) {
        return withAlpha(accent, alpha);
    }

    public int secondaryFill(int alpha) {
        return withAlpha(accentSecondary, alpha);
    }

    /** The accent blended toward the surface, for an enabled card fill. */
    public int enabledCardColor() {
        return blend(SURFACE_ELEVATED, accent, 0.16f);
    }

    public int disabledCardColor() {
        return SURFACE_ELEVATED;
    }

    /** Card lift; zero when the user turned glow effects off, matching the rest of the app. */
    public float enabledElevation() {
        return glowEnabled ? 10f : 0f;
    }

    public float disabledElevation() {
        return glowEnabled ? 3f : 0f;
    }

    /** Stable per-group hue so a module's section keeps the same colour across sessions. */
    public int groupColor(String groupId) {
        if (groupId == null || groupId.isEmpty()) return accent;
        int hash = 0;
        for (int i = 0; i < groupId.length(); i++) {
            hash = hash * 31 + groupId.charAt(i);
        }
        return GROUP_PALETTE[Math.abs(hash) % GROUP_PALETTE.length];
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int blend(int base, int over, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(base) * (1 - a) + Color.red(over) * a);
        int g = (int) (Color.green(base) * (1 - a) + Color.green(over) * a);
        int b = (int) (Color.blue(base) * (1 - a) + Color.blue(over) * a);
        return Color.rgb(r, g, b);
    }
}
