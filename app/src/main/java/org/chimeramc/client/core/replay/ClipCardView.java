package org.chimeramc.client.core.replay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One clip card in the Replay grid.
 *
 * <p>Built in code because the library is live data, and drawn from {@link ReplayStyle} so Screen A
 * and Screen B render the same card. It carries the thumbnail, name, duration, date/size, the
 * favorite star and the highlight badge; press feedback is the shared
 * {@code DynamicAnim.applyPressScale} used everywhere else.
 */
final class ClipCardView extends FrameLayout {

    private final Context context;
    private final ReplayStyle style;

    private final ImageView thumbnail;
    private final TextView durationPill;
    private final TextView name;
    private final TextView meta;
    private final TextView favorite;
    private final TextView highlightBadge;

    private ReplayClip clip;
    private Runnable onOpen;
    private Runnable onFavorite;
    private Runnable onLongPress;

    ClipCardView(Context context, ReplayStyle style) {
        super(context);
        this.context = context;
        this.style = style;

        setClipToPadding(false);
        setBackground(ReplayStyle.rounded(style.surfaceElevated(), 14f, context));
        setPadding(0, 0, 0, 0);
        setElevation(style.cardElevation());
        if (style.glowEnabled()) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            ReplayStyle.dp(context, 14f));
                }
            });
        }

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Thumbnail with a rounded top edge and a bottom scrim.
        FrameLayout thumbFrame = new FrameLayout(context);
        thumbnail = new ImageView(context);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackground(ReplayStyle.verticalGradient(
                ReplayStyle.blend(style.surfaceElevated(), style.accent(), 0.25f),
                ReplayStyle.blend(style.surfaceElevated(), style.accentSecondary(), 0.18f),
                0f, context));
        thumbFrame.addView(thumbnail, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        View scrim = new View(context);
        scrim.setBackground(ReplayStyle.verticalGradient(0x00000000, 0xB3000000, 0f, context));
        FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        scrimParams.gravity = Gravity.BOTTOM;
        thumbFrame.addView(scrim, scrimParams);

        durationPill = new TextView(context);
        durationPill.setTextColor(0xFFF4F1FF);
        durationPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        durationPill.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        durationPill.setBackground(ReplayStyle.pill(0x99000000, context));
        durationPill.setPadding(ReplayStyle.dpInt(context, 7), ReplayStyle.dpInt(context, 2),
                ReplayStyle.dpInt(context, 7), ReplayStyle.dpInt(context, 2));
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pillParams.gravity = Gravity.BOTTOM | Gravity.END;
        pillParams.setMargins(0, 0, ReplayStyle.dpInt(context, 8), ReplayStyle.dpInt(context, 8));
        thumbFrame.addView(durationPill, pillParams);

        highlightBadge = new TextView(context);
        highlightBadge.setText("★");
        highlightBadge.setTextColor(0xFFFFD166);
        highlightBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        highlightBadge.setBackground(ReplayStyle.pill(0x99000000, context));
        highlightBadge.setPadding(ReplayStyle.dpInt(context, 6), ReplayStyle.dpInt(context, 1),
                ReplayStyle.dpInt(context, 6), ReplayStyle.dpInt(context, 1));
        highlightBadge.setVisibility(GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.BOTTOM | Gravity.START;
        badgeParams.setMargins(ReplayStyle.dpInt(context, 8), 0, 0, ReplayStyle.dpInt(context, 8));
        thumbFrame.addView(highlightBadge, badgeParams);

        column.addView(thumbFrame, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, ReplayStyle.dpInt(context, 118f)));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(ReplayStyle.dpInt(context, 10), ReplayStyle.dpInt(context, 8),
                ReplayStyle.dpInt(context, 10), ReplayStyle.dpInt(context, 10));

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        name = new TextView(context);
        name.setTextColor(style.textPrimary());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f));

        favorite = new TextView(context);
        favorite.setText("☆");
        favorite.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        favorite.setTextColor(style.textTertiary());
        favorite.setPadding(ReplayStyle.dpInt(context, 6), ReplayStyle.dpInt(context, 0),
                ReplayStyle.dpInt(context, 0), 0);
        favorite.setOnClickListener(v -> {
            if (onFavorite != null) onFavorite.run();
        });
        nameRow.addView(favorite);
        info.addView(nameRow);

        meta = new TextView(context);
        meta.setTextColor(style.textTertiary());
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        meta.setSingleLine(true);
        meta.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = ReplayStyle.dpInt(context, 3);
        info.addView(meta, metaParams);

        column.addView(info, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        setOnClickListener(v -> {
            if (onOpen != null) onOpen.run();
        });
        setOnLongClickListener(v -> {
            if (onLongPress != null) {
                onLongPress.run();
                return true;
            }
            return false;
        });
        org.chimeramc.client.ui.animation.DynamicAnim.applyPressScale(this);
    }

    void setClip(ReplayClip value) {
        this.clip = value;
        name.setText(value.displayName());
        durationPill.setText(ReplayFormat.duration(value.durationMs()));
        meta.setText(value.dateLabel() + "  ·  " + ReplayFormat.size(value.sizeBytes()));
        favorite.setText(value.favorite() ? "★" : "☆");
        favorite.setTextColor(value.favorite() ? 0xFFFFD166 : style.textTertiary());
        highlightBadge.setVisibility(value.highlight() ? VISIBLE : GONE);
    }

    void setThumbnail(Bitmap bitmap) {
        if (bitmap != null) thumbnail.setImageBitmap(bitmap);
    }

    void setOnOpen(Runnable runnable) {
        this.onOpen = runnable;
    }

    void setOnFavorite(Runnable runnable) {
        this.onFavorite = runnable;
    }

    void setOnLongPress(Runnable runnable) {
        this.onLongPress = runnable;
    }

    ReplayClip clip() {
        return clip;
    }
}
