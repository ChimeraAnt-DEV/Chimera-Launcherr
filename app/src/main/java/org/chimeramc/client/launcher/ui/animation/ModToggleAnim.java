package org.chimeramc.client.ui.animation;

import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Icon-specific micro-animations for module toggles.
 *
 * <p>A stock switch flipping is the same gesture for every module, which says nothing about what
 * was just turned on. These are short, per-icon and cheap: the module "behaves" for a moment when
 * it is switched on, so the toggle reads as arming a feature rather than changing a setting.
 *
 * <p>Only modules with distinct iconography get one (Armor HUD, Voice Chat). This is a pattern to
 * grow per icon, not a system built all at once; anything else falls through to no animation.
 * Every effect is a transform on the icon view, so flipping a long list never triggers layout.
 */
public final class ModToggleAnim {

    private ModToggleAnim() {
    }

    /**
     * Runs the animation for a module id, if it has one, on the module's own icon.
     *
     * @param iconView the module icon, animated in place
     * @param modId    the inbuilt module id
     * @param enabled  the new state
     */
    public static void play(View iconView, String modId, boolean enabled) {
        if (iconView == null || modId == null) return;
        if (!DynamicAnim.areAnimationsEnabled()) return;

        if ("armor_hud".equals(modId)) {
            equipSnap(iconView, enabled);
        } else if ("voice_chat".equals(modId)) {
            micPulse(iconView, enabled);
        }
    }

    /**
     * Armor HUD: the icon "equips" -- drops in from above with a snap and a small overshoot, the
     * way a piece of armor lands in its slot. Disabling settles it back without the drop.
     */
    private static void equipSnap(View icon, boolean enabled) {
        icon.animate().cancel();
        if (!enabled) {
            icon.animate()
                    .translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(120L)
                    .start();
            return;
        }
        float drop = -6f * icon.getResources().getDisplayMetrics().density;
        icon.setTranslationY(drop);
        icon.setScaleX(1.08f);
        icon.setScaleY(1.08f);
        icon.animate()
                .translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(180L)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();
    }

    /**
     * Voice Chat: the mic gives one quick pulse on enable.
     *
     * <p>A scale pulse rather than an overlay sprite: the in-game meter is a live level display
     * driven by real audio, so there is no level to draw here. What carries the identity is the
     * mic icon itself, which is already the module's artwork; adding a second copy of the sprite
     * would just be a smaller duplicate of the thing the user is already looking at.
     */
    private static void micPulse(View icon, boolean enabled) {
        icon.animate().cancel();
        if (!enabled) {
            icon.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
            return;
        }
        icon.animate()
                .scaleX(1.14f).scaleY(1.14f)
                .setDuration(140L)
                .setInterpolator(new OvershootInterpolator(2.4f))
                .withEndAction(() -> {
                    if (!DynamicAnim.areAnimationsEnabled()) {
                        icon.setScaleX(1f);
                        icon.setScaleY(1f);
                        return;
                    }
                    icon.animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
                })
                .start();
    }
}
