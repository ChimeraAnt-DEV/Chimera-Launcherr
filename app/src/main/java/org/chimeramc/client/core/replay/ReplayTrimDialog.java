package org.chimeramc.client.core.replay;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.chimeramc.client.R;
import org.chimeramc.client.ui.animation.DynamicAnim;

/**
 * The Tier 3 trim dialog.
 *
 * <p>Two handles over the clip's timeline, a live length readout, and Save. The dialog works on a
 * {@link ReplayTrim}, which owns the clamping, so dragging a handle past the other one or off the
 * end cannot produce an invalid window — the dialog only has to reflect what the trim says.
 */
final class ReplayTrimDialog {

    /** Called with a validated trim when the player confirms. */
    interface OnTrim {
        void onTrim(ReplayTrim trim);
    }

    private ReplayTrimDialog() {
    }

    static void show(Activity activity, ReplayStyle style, ReplayClip clip, OnTrim callback) {
        long duration = clip.durationMs();
        if (duration < ReplayTrim.MIN_LENGTH_MS) {
            android.widget.Toast.makeText(activity, R.string.replay_trim_too_short,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 16));
        root.setBackground(ReplayStyle.roundedStroked(style.surfaceGlass(), style.hairline(),
                18f, activity));

        TextView title = new TextView(activity);
        title.setText(R.string.replay_trim_title);
        title.setTextColor(style.textPrimary());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText(clip.displayName());
        subtitle.setTextColor(style.textTertiary());
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(activity, 2);
        root.addView(subtitle, subtitleParams);

        TextView lengthLabel = new TextView(activity);
        lengthLabel.setTextColor(style.accent());
        lengthLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        lengthLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams lengthParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lengthParams.topMargin = dp(activity, 14);
        root.addView(lengthLabel, lengthParams);

        SeekBar startBar = new SeekBar(activity);
        startBar.setMax((int) duration);
        startBar.setProgress(0);
        SeekBar endBar = new SeekBar(activity);
        endBar.setMax((int) duration);
        endBar.setProgress((int) duration);
        root.addView(startBar, seekParams(activity));
        root.addView(endBar, seekParams(activity));

        ReplayTrim[] current = {new ReplayTrim(0, duration, duration)};
        Runnable refresh = () -> {
            ReplayTrim trim = current[0];
            lengthLabel.setText(activity.getString(R.string.replay_trim_length,
                    ReplayFormat.duration(trim.lengthMs())));
        };

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                current[0] = new ReplayTrim(startBar.getProgress(), endBar.getProgress(), duration);
                refresh.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        startBar.setOnSeekBarChangeListener(listener);
        endBar.setOnSeekBarChangeListener(listener);
        refresh.run();

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(activity, 16);

        TextView cancel = actionButton(activity, style, R.string.replay_action_cancel, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = actionButton(activity, style, R.string.replay_action_save, true);
        save.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onTrim(current[0]);
        });
        actions.addView(cancel);
        actions.addView(save);
        root.addView(actions, actionsParams);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.6f);
            window.addFlags(Window.FEATURE_NO_TITLE);
        }
        DynamicAnim.animateDialogShow(root);
        dialog.show();
    }

    private static LinearLayout.LayoutParams seekParams(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, 4);
        return params;
    }

    private static TextView actionButton(Activity activity, ReplayStyle style, int textRes,
                                         boolean primary) {
        TextView button = new TextView(activity);
        button.setText(textRes);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 18), dp(activity, 9), dp(activity, 18), dp(activity, 9));
        button.setTextColor(primary ? style.textPrimary() : style.textSecondary());
        button.setBackground(primary
                ? ReplayStyle.rounded(style.accentFill(60), 12f, activity)
                : ReplayStyle.rounded(style.surfaceElevated(), 12f, activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(activity, 8));
        button.setLayoutParams(params);
        DynamicAnim.applyPressScale(button);
        return button;
    }

    private static int dp(Activity activity, int value) {
        return ReplayStyle.dpInt(activity, value);
    }
}
