package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chimeramc.client.ui.animation.DynamicAnim;

/**
 * Shared, code-built "premium" controls for the Voice tab and its in-game panel.
 *
 * <p>The voice UI is built in code (the in-game panel has no XML to inflate), so without a shared
 * kit every screen would re-derive its own pill background and its own press animation. Keeping
 * the treatments here means the launcher screen and the in-game panel look and behave the same,
 * and a single change to the button style reaches both.
 *
 * <p>Everything animates through {@link DynamicAnim}, so the user's "show animations" and animation
 * speed preferences are honoured rather than bypassed: with motion disabled these controls are
 * still fully usable, just static.
 */
public final class VoiceUiKit {

    private VoiceUiKit() {
    }

    /** Corner radius for pills and cards, in dp. */
    public static final float RADIUS_DP = 14f;
    /** Minimum height for a text-bearing control, so a large font scale cannot clip the label. */
    public static final int MIN_CONTROL_HEIGHT_DP = 40;

    /** Builds a rounded gradient fill with an optional hairline stroke. */
    public static GradientDrawable roundedGradient(int startColor, int endColor,
                                                   float radiusPx, int strokeColor, float strokePx) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{startColor, endColor});
        drawable.setCornerRadius(radiusPx);
        if (strokePx > 0f) drawable.setStroke(Math.round(strokePx), strokeColor);
        return drawable;
    }

    /** A flat rounded fill with an optional stroke, for secondary surfaces. */
    public static GradientDrawable roundedFill(int color, float radiusPx,
                                               int strokeColor, float strokePx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        if (strokePx > 0f) drawable.setStroke(Math.round(strokePx), strokeColor);
        return drawable;
    }

    /**
     * A premium pill button: gradient fill, tinted text, min height and a press animation.
     *
     * <p>A primary button fills with the accent; a secondary one is a dark surface with an accent
     * hairline, so a screen with several actions still has an obvious main one.
     */
    public static TextView pillButton(Context context, CharSequence label, int accent,
                                      boolean primary, boolean compact) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextSize(compact ? 11f : 12f);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        float density = context.getResources().getDisplayMetrics().density;
        int padH = Math.round((compact ? 12 : 16) * density);
        int padV = Math.round(9 * density);
        button.setPadding(padH, padV, padH, padV);
        button.setMinHeight(Math.round(MIN_CONTROL_HEIGHT_DP * density));

        applyPillBackground(button, accent, primary);
        button.setTextColor(primary ? 0xFFFFFFFF : lighten(accent, 0.35f));
        button.setClickable(true);
        DynamicAnim.applyPressScale(button);
        return button;
    }

    /** Re-applies a pill's background for the primary/secondary treatment. */
    public static void applyPillBackground(TextView button, int accent, boolean primary) {
        float density = button.getResources().getDisplayMetrics().density;
        float radius = RADIUS_DP * density;
        if (primary) {
            button.setBackground(roundedGradient(darken(accent, 0.12f), lighten(accent, 0.06f),
                    radius, lighten(accent, 0.4f), 0f));
        } else {
            button.setBackground(roundedFill(blend(0xFF1B1E22, accent, 0.12f),
                    radius, withAlpha(accent, 0x66), Math.max(1f, density)));
        }
    }

    /**
     * A two-option segmented control (Public / Private).
     *
     * <p>Returns the container; {@code listener} receives the selected index. The active option is
     * animated between positions with a translation spring rather than a redraw, which is what
     * makes the switch read as a physical control.
     */
    public static LinearLayout segmentedControl(Context context, CharSequence[] labels, int accent,
                                                boolean compact, OnSegmentSelected listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        float density = context.getResources().getDisplayMetrics().density;
        row.setBackground(roundedFill(0xFF15171A, RADIUS_DP * density, withAlpha(accent, 0x55), density));

        TextView[] segments = new TextView[labels.length];
        int padH = Math.round(14 * density);
        int padV = Math.round(8 * density);
        for (int i = 0; i < labels.length; i++) {
            TextView segment = new TextView(context);
            segment.setText(labels[i]);
            segment.setGravity(Gravity.CENTER);
            segment.setTypeface(segment.getTypeface(), Typeface.BOLD);
            segment.setTextSize(compact ? 11f : 12f);
            segment.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            segment.setLayoutParams(params);
            final int index = i;
            segment.setClickable(true);
            segment.setOnClickListener(v -> {
                selectSegment(segments, index, accent);
                if (listener != null) listener.onSegmentSelected(index);
            });
            DynamicAnim.applyPressScale(segment);
            segments[i] = segment;
            row.addView(segment);
        }
        selectSegment(segments, 0, accent);
        return row;
    }

    private static void selectSegment(TextView[] segments, int selected, int accent) {
        float density = segments.length == 0 ? 1f
                : segments[0].getResources().getDisplayMetrics().density;
        for (int i = 0; i < segments.length; i++) {
            TextView segment = segments[i];
            boolean active = i == selected;
            if (active) {
                segment.setBackground(roundedGradient(darken(accent, 0.12f), lighten(accent, 0.06f),
                        RADIUS_DP * density, 0, 0f));
                segment.setTextColor(0xFFFFFFFF);
            } else {
                segment.setBackground(null);
                segment.setTextColor(0xFFA8B0B8);
            }
            if (DynamicAnim.areAnimationsEnabled()) {
                segment.animate().alpha(active ? 1f : 0.82f).setDuration(160).start();
            } else {
                segment.setAlpha(active ? 1f : 0.82f);
            }
        }
    }

    /** Listener for {@link #segmentedControl}. */
    public interface OnSegmentSelected {
        void onSegmentSelected(int index);
    }

    /**
     * A small capacity stepper: a label, a minus, a value and a plus.
     *
     * <p>Used only for public channels, so it is built on demand and hidden for private ones rather
     * than shown disabled — a private channel genuinely has no cap to set.
     */
    public static LinearLayout stepper(Context context, int accent, boolean compact,
                                       int min, int max, int initial, OnStep listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;

        TextView minus = pillButton(context, "\u2212", accent, false, compact);
        TextView value = new TextView(context);
        value.setGravity(Gravity.CENTER);
        value.setTextSize(compact ? 15f : 17f);
        value.setTypeface(value.getTypeface(), Typeface.BOLD);
        value.setTextColor(0xFFE6E9EC);
        value.setPadding(Math.round(14 * density), 0, Math.round(14 * density), 0);
        TextView plus = pillButton(context, "+", accent, false, compact);

        final int[] current = {Math.max(min, Math.min(max, initial))};
        value.setText(String.valueOf(current[0]));
        minus.setOnClickListener(v -> {
            current[0] = Math.max(min, current[0] - 1);
            value.setText(String.valueOf(current[0]));
            pulse(value);
            if (listener != null) listener.onStep(current[0]);
        });
        plus.setOnClickListener(v -> {
            current[0] = Math.min(max, current[0] + 1);
            value.setText(String.valueOf(current[0]));
            pulse(value);
            if (listener != null) listener.onStep(current[0]);
        });

        row.addView(minus);
        row.addView(value);
        row.addView(plus);
        return row;
    }

    /** Callback for {@link #stepper}. */
    public interface OnStep {
        void onStep(int value);
    }

    /**
     * A short scale pulse, used to confirm a discrete step.
     *
     * <p>Falls straight through when motion is disabled, so the value still changes and reads.
     */
    public static void pulse(View view) {
        if (view == null || !DynamicAnim.areAnimationsEnabled()) return;
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate().scaleX(1.14f).scaleY(1.14f).setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    private boolean cancelled;

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (cancelled) return;
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                                .setListener(null).start();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        cancelled = true;
                    }
                })
                .start();
    }

    /**
     * Fades and slides a view in, for a tab switch or a freshly rendered list.
     *
     * <p>Honours the reduced-motion preference: with motion off the view simply appears.
     */
    public static void slideIn(View view, float fromX) {
        if (view == null) return;
        if (!DynamicAnim.areAnimationsEnabled()) {
            view.setAlpha(1f);
            view.setTranslationX(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationX(fromX);
        view.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    /** Staggers a group of views in, cheaply, for a member/directory list. */
    public static void staggerIn(ViewGroup group, float fromX) {
        if (group == null) return;
        if (!DynamicAnim.areAnimationsEnabled()) {
            for (int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setAlpha(1f);
                group.getChildAt(i).setTranslationX(0f);
            }
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationX(fromX);
            child.animate().alpha(1f).translationX(0f)
                    .setStartDelay(Math.min(160L, i * 24L))
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }
    }

    /** Blends two colours by {@code amount} in {@code [0,1]}. */
    public static int blend(int base, int over, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(base) * (1 - a) + Color.red(over) * a);
        int g = (int) (Color.green(base) * (1 - a) + Color.green(over) * a);
        int b = (int) (Color.blue(base) * (1 - a) + Color.blue(over) * a);
        return Color.rgb(r, g, b);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int lighten(int color, float amount) {
        return blend(color, 0xFFFFFFFF, amount);
    }

    public static int darken(int color, float amount) {
        return blend(color, 0xFF000000, amount);
    }
}
