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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chimeramc.client.R;
import org.chimeramc.client.ui.animation.DynamicAnim;

/**
 * The action sheet for a clip, plus the rename dialog.
 *
 * <p>Touch users have no controller keys, so the sheet is how a clip is played, trimmed, exported,
 * favorited, renamed or deleted from the grid: a long-press on a card opens it. Kept in one place
 * so both screens show the same actions.
 */
final class ReplayClipActionsDialog {

    private ReplayClipActionsDialog() {
    }

    interface Actions {
        void onPlay(ReplayClip clip);

        void onTrim(ReplayClip clip);

        void onExport(ReplayClip clip);

        void onFavorite(ReplayClip clip);

        void onRename(ReplayClip clip);

        void onDelete(ReplayClip clip);
    }

    static void showActions(Activity activity, ReplayStyle style, ReplayClip clip,
                            Actions actions) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 8), dp(activity, 10), dp(activity, 8), dp(activity, 8));
        root.setBackground(ReplayStyle.roundedStroked(style.surfaceGlass(), style.hairline(),
                18f, activity));

        TextView header = new TextView(activity);
        header.setText(clip.displayName());
        header.setTextColor(style.textPrimary());
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        header.setTypeface(null, Typeface.BOLD);
        header.setSingleLine(true);
        header.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 10));
        root.addView(header);

        root.addView(actionRow(activity, style, R.string.replay_action_play, false,
                v -> {
                    dialog.dismiss();
                    if (actions != null) actions.onPlay(clip);
                }));
        root.addView(actionRow(activity, style, R.string.replay_action_trim, false,
                v -> {
                    dialog.dismiss();
                    if (actions != null) actions.onTrim(clip);
                }));
        root.addView(actionRow(activity, style, R.string.replay_action_export, false,
                v -> {
                    dialog.dismiss();
                    if (actions != null) actions.onExport(clip);
                }));
        root.addView(actionRow(activity, style, clip.favorite()
                        ? R.string.replay_action_unfavorite : R.string.replay_action_favorite,
                false, v -> {
                    dialog.dismiss();
                    if (actions != null) actions.onFavorite(clip);
                }));
        root.addView(actionRow(activity, style, R.string.replay_action_rename, false,
                v -> {
                    dialog.dismiss();
                    if (actions != null) actions.onRename(clip);
                }));
        root.addView(actionRow(activity, style, R.string.replay_action_delete, true,
                v -> {
                    dialog.dismiss();
                    if (actions != null) actions.onDelete(clip);
                }));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ReplayStyle.dpInt(activity, 300f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.6f);
        }
        DynamicAnim.animateDialogShow(root);
        dialog.show();
    }

    /** Rename prompt. The typed name is sanitized by the repository before it touches the disk. */
    static void showRename(Activity activity, ReplayStyle style, ReplayClip clip,
                           java.util.function.Consumer<String> onRename) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 16));
        root.setBackground(ReplayStyle.roundedStroked(style.surfaceGlass(), style.hairline(),
                18f, activity));

        TextView title = new TextView(activity);
        title.setText(R.string.replay_rename_title);
        title.setTextColor(style.textPrimary());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        EditText input = new EditText(activity);
        input.setText(clip.displayName());
        input.setHint(R.string.replay_rename_hint);
        input.setTextColor(style.textPrimary());
        input.setHintTextColor(style.textTertiary());
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        input.setSingleLine(true);
        input.setBackground(ReplayStyle.roundedStroked(style.surfaceElevated(), style.hairline(),
                12f, activity));
        input.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(activity, 12);
        root.addView(input, inputParams);
        input.requestFocus();

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        TextView cancel = sheetButton(activity, style, R.string.replay_action_cancel, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = sheetButton(activity, style, R.string.replay_action_save, true);
        save.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            dialog.dismiss();
            if (onRename != null) onRename.accept(value);
        });
        actions.addView(cancel);
        actions.addView(save);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(activity, 14);
        root.addView(actions, actionsParams);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ReplayStyle.dpInt(activity, 340f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.6f);
        }
        DynamicAnim.animateDialogShow(root);
        dialog.show();
    }

    private static View actionRow(Activity activity, ReplayStyle style, int textRes,
                                  boolean destructive, View.OnClickListener listener) {
        TextView row = new TextView(activity);
        row.setText(textRes);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        row.setTextColor(destructive ? style.statusRecord() : style.textSecondary());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(activity, 42));
        row.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        row.setBackground(ReplayStyle.rippled(
                ReplayStyle.rounded(0x00000000, 12f, activity), style.accentFill(50), activity));
        row.setOnClickListener(listener);
        DynamicAnim.applyPressScale(row);
        return row;
    }

    private static TextView sheetButton(Activity activity, ReplayStyle style, int textRes,
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
