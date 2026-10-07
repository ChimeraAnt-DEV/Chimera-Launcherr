package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.chimeramc.client.R;

/**
 * The small GlowberryClient badge shown beside a player who is running the client.
 *
 * <p>A voice channel member or a cosmetic-sync peer is by definition another GlowberryClient user
 * — only this client speaks those protocols — so the badge beside their name is the "I can see
 * you're a Glowberry user" marker. It is deliberately the launcher's own mark, so a player can
 * tell at a glance who shares the client and therefore whose cosmetics they can see.
 *
 * <p>Kept as one helper so the Voice section and the Cosmetics section draw the same badge at the
 * same size; two hand-built ImageViews is how the two would drift.
 */
final class GlowberryBadge {

    private GlowberryBadge() {
    }

    /**
     * A badge view sized for a list row. {@code compact} shrinks it for the compact Mod Menu.
     * The content description makes the meaning available to a screen reader rather than relying
     * on the image alone.
     */
    static ImageView create(Context context, boolean compact) {
        ImageView badge = new ImageView(context);
        badge.setImageResource(R.drawable.ic_glowberry_badge);
        badge.setContentDescription(context.getString(R.string.glowberry_user_badge));
        int size = (int) ((compact ? 12 : 14) * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER_VERTICAL;
        params.setMarginEnd((int) (4 * context.getResources().getDisplayMetrics().density));
        badge.setLayoutParams(params);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return badge;
    }
}
