package org.chimeramc.client.core.replay;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.chimeramc.client.R;
import org.chimeramc.client.ui.animation.DynamicAnim;

/**
 * The Replay settings dialog: the clip-length limit, the storage cap, quality, burn-in, watermark
 * and the highlight triggers.
 *
 * <p>Every value is written straight through {@link ReplaySettings}, so the panel and the recorder
 * read one source of truth. The dialog also carries the scope note — the honest statement that
 * highlight capture flags the clip being recorded rather than rewinding an encoded buffer — so the
 * limitation is visible where the trigger is turned on, not buried in a doc.
 */
final class ReplaySettingsDialog {

    private ReplaySettingsDialog() {
    }

    static void show(Activity activity, ReplayStyle style, Runnable onChanged) {
        ReplaySettings settings = ReplaySettings.get(activity);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 16));
        root.setBackground(ReplayStyle.roundedStroked(style.surfaceGlass(), style.hairline(),
                18f, activity));

        TextView title = new TextView(activity);
        title.setText(R.string.replay_settings_title);
        title.setTextColor(style.textPrimary());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        // Clip length limit.
        TextView clipLabel = sectionLabel(activity, style,
                activity.getString(R.string.replay_settings_clip_limit));
        root.addView(clipLabel, topParams(activity, 14));
        SeekBar clipBar = new SeekBar(activity);
        clipBar.setMax(ReplaySettings.MAX_CLIP_LIMIT_MINUTES - 1);
        clipBar.setProgress(settings.clipLimitMinutes() - 1);
        TextView clipValue = valueLabel(activity, style);
        clipValue.setText(settings.clipLimitMinutes() + " min");
        clipBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minutes = progress + 1;
                clipValue.setText(minutes + " min");
                if (fromUser) settings.setClipLimitMinutes(minutes);
            }
        });
        root.addView(clipBar);
        root.addView(clipValue);

        // Storage cap.
        root.addView(sectionLabel(activity, style,
                activity.getString(R.string.replay_settings_storage_cap)),
                topParams(activity, 14));
        SeekBar capBar = new SeekBar(activity);
        capBar.setMax(9);
        capBar.setProgress(capSteps(settings.storageCapBytes()));
        TextView capValue = valueLabel(activity, style);
        capValue.setText(ReplayFormat.size(settings.storageCapBytes()));
        capBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long bytes = capBytesForStep(progress);
                capValue.setText(ReplayFormat.size(bytes));
                if (fromUser) {
                    settings.setStorageCapBytes(bytes);
                    if (onChanged != null) onChanged.run();
                }
            }
        });
        root.addView(capBar);
        root.addView(capValue);

        // Quality.
        root.addView(toggleRow(activity, style, R.string.replay_settings_quality,
                settings.forceLowQuality(), checked -> {
                    settings.setForceLowQuality(checked);
                    if (onChanged != null) onChanged.run();
                }), topParams(activity, 12));

        // Burn-in + watermark.
        root.addView(toggleRow(activity, style, R.string.replay_settings_burn_metadata,
                settings.burnMetadata(), settings::setBurnMetadata));
        root.addView(toggleRow(activity, style, R.string.replay_settings_watermark,
                settings.watermark(), settings::setWatermark));

        // Highlight triggers.
        root.addView(sectionLabel(activity, style,
                activity.getString(R.string.replay_settings_triggers)), topParams(activity, 14));
        root.addView(toggleRow(activity, style, R.string.replay_settings_trigger_death,
                settings.triggerDeath(), settings::setTriggerDeath));
        root.addView(toggleRow(activity, style,
                activity.getString(R.string.replay_settings_trigger_streak,
                        settings.streakThreshold()),
                settings.triggerKillStreak(), settings::setTriggerKillStreak));
        root.addView(toggleRow(activity, style,
                activity.getString(R.string.replay_settings_trigger_combo,
                        settings.comboThreshold()),
                settings.triggerCombo(), settings::setTriggerCombo));

        // Scope note.
        TextView note = new TextView(activity);
        note.setText(R.string.replay_scope_note);
        note.setTextColor(style.textTertiary());
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        note.setLineSpacing(dp(activity, 2), 1f);
        root.addView(note, topParams(activity, 16));

        TextView close = new TextView(activity);
        close.setText(R.string.replay_action_cancel);
        close.setTextColor(style.textPrimary());
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        close.setTypeface(null, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(activity, 18), dp(activity, 9), dp(activity, 18), dp(activity, 9));
        close.setBackground(ReplayStyle.rounded(style.accentFill(60), 12f, activity));
        close.setOnClickListener(v -> dialog.dismiss());
        DynamicAnim.applyPressScale(close);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = dp(activity, 16);
        closeParams.gravity = Gravity.END;
        root.addView(close, closeParams);

        scroll.addView(root);
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.round(style.dp(activity, 380)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.6f);
        }
        DynamicAnim.animateDialogShow(root);
        dialog.show();
    }

    private static View toggleRow(Activity activity, ReplayStyle style, int textRes,
                                  boolean checked, ToggleCallback callback) {
        return toggleRow(activity, style, activity.getString(textRes), checked, callback);
    }

    private static View toggleRow(Activity activity, ReplayStyle style, String label,
                                  boolean checked, ToggleCallback callback) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(activity, 8), 0, dp(activity, 8));

        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextColor(style.textSecondary());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(activity);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((button, isChecked) -> callback.onToggle(isChecked));
        row.addView(toggle);
        return row;
    }

    private static TextView sectionLabel(Activity activity, ReplayStyle style, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(style.textPrimary());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setTypeface(null, Typeface.BOLD);
        label.setLetterSpacing(0.02f);
        return label;
    }

    private static TextView valueLabel(Activity activity, ReplayStyle style) {
        TextView label = new TextView(activity);
        label.setTextColor(style.accent());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setGravity(Gravity.END);
        return label;
    }

    /** The 10 discrete storage-cap steps, 1 GB to 32 GB. */
    static long capBytesForStep(int step) {
        int clamped = Math.max(0, Math.min(9, step));
        long gb = 1L + clamped * 3L + (clamped >= 4 ? 1L : 0L);
        // 1,4,7,10,14,17,20,23,26,29 GB roughly; clamped to the policy range below.
        return ReplayStoragePolicy.clampCapBytes(gb * 1024L * 1024L * 1024L);
    }

    static int capSteps(long bytes) {
        long gb = Math.max(1L, bytes / (1024L * 1024L * 1024L));
        return (int) Math.max(0L, Math.min(9L, (gb - 1) / 3L));
    }

    private interface ToggleCallback {
        void onToggle(boolean checked);
    }

    /** A listener with the two no-op callbacks already filled in. */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private static LinearLayout.LayoutParams topParams(Activity activity, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, topDp);
        return params;
    }

    private static int dp(Activity activity, int value) {
        return ReplayStyle.dpInt(activity, value);
    }
}
