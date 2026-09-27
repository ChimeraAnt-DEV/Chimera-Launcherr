package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.core.voice.VoiceChannel;
import org.chimeramc.client.core.voice.VoiceChannelDirectory;
import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.util.Collections;
import java.util.List;

/**
 * The Voice section of the in-game Mod Menu: the proximity chat module's channel, its members and
 * the public directory, without leaving the world.
 *
 * <p>Built in code like the other Mod Menu sections, because everything on it is live data from
 * the voice module rather than fixed layout. It is the in-game home of the feature; the standalone
 * {@code VoiceChatActivity} remains for the launcher-side setup and is reached from the Mods tab.
 *
 * <p><b>The module is never started from inside a running game.</b> Starting a channel opens the
 * microphone and a UDP multicast socket, which is a deliberate user action; a tap on the menu
 * entry must not do it silently. The tuned-in state is reported, and the switch that starts the
 * module lives in the Mods tab, so the only thing this panel changes is which channel the voice
 * link is on.
 *
 * <p>The channel controls are hidden while the module is stopped: a join button that appears to
 * work but reaches nobody is worse than saying the feature is off.
 */
final class VoicePanel {

    private final Activity activity;
    private final InbuiltModManager manager;
    private final LinearLayout root;
    private final boolean compact;

    private TextView statusLine;
    private TextView channelName;
    private TextView channelKind;
    private TextView membersLabel;
    private LinearLayout membersContainer;
    private LinearLayout directoryContainer;
    private TextView directoryEmpty;
    private Button leaveButton;
    private Button copyButton;
    private Button shareButton;
    private EditText createName;
    private EditText joinCode;
    private LinearLayout controlColumn;

    VoicePanel(Activity activity, boolean compact) {
        this.activity = activity;
        this.compact = compact;
        this.manager = InbuiltModManager.getInstance(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(compact ? 12 : 18), dp(compact ? 8 : 14),
                dp(compact ? 12 : 18), dp(compact ? 8 : 14));

        root.addView(buildChannelColumn(), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(buildDirectoryColumn(), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        refresh();
    }

    View getView() {
        return root;
    }

    /** Re-reads the live state, so the panel reflects a channel joined from the launcher too. */
    void refresh() {
        VoiceChatModule module = VoiceChatModule.peek();
        boolean running = module != null && module.isRunning();

        // The sub-state reads "mic on"/"listen-only" on its own; prefix it with the module state
        // so the line is unambiguous standing alone in a menu.
        statusLine.setText(running
                ? activity.getString(R.string.voice_mod_menu_running,
                        activity.getString(module.isTransmitting()
                                ? R.string.voice_chat_mic_on : R.string.voice_chat_mic_off))
                : activity.getString(R.string.voice_chat_status_off));
        statusLine.setTextColor(running ? 0xFF7ED29A : 0xFF8F979F);

        controlColumn.setVisibility(running ? View.VISIBLE : View.GONE);

        String current = manager.getVoiceChannel();
        boolean isPrivate = manager.isVoiceChannelPrivate();
        String display = manager.getVoiceChannelName();
        channelName.setText(display.isEmpty() ? channelLabel(current) : display);
        channelKind.setText(isPrivate
                ? activity.getString(R.string.voice_channel_kind_private, current)
                : activity.getString(R.string.voice_channel_kind_public));

        boolean hasCode = isPrivate || VoiceChannel.isJoinCode(current);
        copyButton.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        shareButton.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        leaveButton.setVisibility(
                current.equals(VoiceChannel.WORLD) ? View.GONE : View.VISIBLE);

        renderMembers(module);
        renderDirectory(module);
    }

    private View buildChannelColumn() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, dp(6), 0);

        statusLine = new TextView(activity);
        statusLine.setTextSize(compact ? 10f : 11f);
        column.addView(statusLine);

        TextView title = new TextView(activity);
        title.setText(R.string.voice_current_channel);
        title.setTextSize(compact ? 13f : 15f);
        title.setTextColor(0xFFE6E9EC);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(0, dp(10), 0, dp(4));
        column.addView(title);

        channelName = new TextView(activity);
        channelName.setTextSize(compact ? 12f : 14f);
        channelName.setTextColor(0xFFE6E9EC);
        column.addView(channelName);

        channelKind = new TextView(activity);
        channelKind.setTextSize(compact ? 10f : 11f);
        channelKind.setTextColor(0xFF8F979F);
        column.addView(channelKind);

        LinearLayout codeRow = new LinearLayout(activity);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setPadding(0, dp(6), 0, 0);
        copyButton = pillButton(R.string.voice_copy_code);
        copyButton.setOnClickListener(v -> copyToClipboard(manager.getVoiceChannel()));
        shareButton = pillButton(R.string.voice_share_code);
        shareButton.setOnClickListener(v -> shareCode(manager.getVoiceChannel()));
        codeRow.addView(copyButton);
        codeRow.addView(shareButton);
        column.addView(codeRow);

        membersLabel = new TextView(activity);
        membersLabel.setTextSize(compact ? 10f : 11f);
        membersLabel.setTextColor(0xFF8F979F);
        membersLabel.setPadding(0, dp(10), 0, dp(2));
        column.addView(membersLabel);

        membersContainer = new LinearLayout(activity);
        membersContainer.setOrientation(LinearLayout.VERTICAL);
        column.addView(membersContainer);

        // Controls that change the channel. Grouped so a single visibility flip hides the whole
        // block while the module is stopped.
        controlColumn = new LinearLayout(activity);
        controlColumn.setOrientation(LinearLayout.VERTICAL);
        controlColumn.setPadding(0, dp(12), 0, 0);

        TextView createHeading = sectionHeading(R.string.voice_create_channel);
        controlColumn.addView(createHeading);

        createName = new EditText(activity);
        createName.setHint(R.string.voice_channel_name_hint);
        createName.setTextSize(compact ? 11f : 12f);
        createName.setSingleLine(true);
        controlColumn.addView(createName);

        Button createButton = pillButton(R.string.voice_create_button);
        createButton.setOnClickListener(v -> createPublicChannel());
        controlColumn.addView(createButton);

        controlColumn.addView(sectionHeading(R.string.voice_join_by_code));

        joinCode = new EditText(activity);
        joinCode.setHint(R.string.voice_join_code_hint);
        joinCode.setTextSize(compact ? 11f : 12f);
        joinCode.setSingleLine(true);
        controlColumn.addView(joinCode);

        Button joinButton = pillButton(R.string.voice_join_button);
        joinButton.setOnClickListener(v -> joinTypedChannel());
        controlColumn.addView(joinButton);

        leaveButton = pillButton(R.string.voice_leave_channel);
        leaveButton.setOnClickListener(v -> {
            manager.joinVoiceChannel(VoiceChannel.WORLD, "", false);
            announceAndRefresh();
        });
        controlColumn.addView(leaveButton);

        column.addView(controlColumn);
        return column;
    }

    private View buildDirectoryColumn() {
        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(6), 0, 0, 0);

        TextView title = new TextView(activity);
        title.setText(R.string.voice_public_directory);
        title.setTextSize(compact ? 13f : 15f);
        title.setTextColor(0xFFE6E9EC);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        column.addView(title);

        directoryEmpty = new TextView(activity);
        directoryEmpty.setText(R.string.voice_directory_empty);
        directoryEmpty.setTextSize(compact ? 10f : 11f);
        directoryEmpty.setTextColor(0xFF8F979F);
        directoryEmpty.setPadding(0, dp(6), 0, 0);
        column.addView(directoryEmpty);

        directoryContainer = new LinearLayout(activity);
        directoryContainer.setOrientation(LinearLayout.VERTICAL);
        column.addView(directoryContainer);

        scroller.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    /** Creates and joins a public channel; private codes are made from the launcher screen. */
    private void createPublicChannel() {
        String typed = createName.getText() == null
                ? "" : createName.getText().toString().trim();
        if (typed.isEmpty()) {
            Toast.makeText(activity, R.string.voice_channel_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        manager.joinVoiceChannel(typed, typed, false);
        createName.setText("");
        announceAndRefresh();
        Toast.makeText(activity, activity.getString(R.string.voice_channel_created, typed),
                Toast.LENGTH_SHORT).show();
    }

    private void joinTypedChannel() {
        String typed = joinCode.getText() == null ? "" : joinCode.getText().toString().trim();
        if (typed.isEmpty()) {
            Toast.makeText(activity, R.string.voice_join_code_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = VoiceChannel.normalize(typed);
        manager.joinVoiceChannel(normalized, typed, VoiceChannel.isJoinCode(normalized));
        joinCode.setText("");
        announceAndRefresh();
        Toast.makeText(activity, activity.getString(R.string.voice_joined_channel, normalized),
                Toast.LENGTH_SHORT).show();
    }

    private void joinPublicChannel(VoiceChannelDirectory.Channel channel) {
        manager.joinVoiceChannel(channel.id, channel.name, false);
        announceAndRefresh();
        Toast.makeText(activity, activity.getString(R.string.voice_joined_channel, channel.name),
                Toast.LENGTH_SHORT).show();
    }

    /** Pushes the new channel to running peers, then re-reads. Only a live module needs telling. */
    private void announceAndRefresh() {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module != null && module.isRunning()) {
            module.applyConfig(manager);
            module.announceNow();
        }
        refresh();
    }

    private void copyToClipboard(String code) {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(
                ClipData.newPlainText(activity.getString(R.string.voice_channel_code_label), code));
        Toast.makeText(activity, activity.getString(R.string.voice_code_copied, code),
                Toast.LENGTH_SHORT).show();
    }

    private void shareCode(String code) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.voice_share_subject));
        share.putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.voice_share_body, code));
        activity.startActivity(Intent.createChooser(
                share, activity.getString(R.string.voice_share_chooser)));
    }

    private void renderMembers(VoiceChatModule module) {
        membersContainer.removeAllViews();
        List<VoicePeer> members = module == null
                ? Collections.emptyList() : module.channelMembers();
        membersLabel.setText(activity.getResources().getQuantityString(
                R.plurals.voice_members, members.size(), members.size()));
        if (members.isEmpty()) {
            membersContainer.addView(memberRow(activity.getString(R.string.voice_members_none), false));
            return;
        }
        for (VoicePeer peer : members) {
            membersContainer.addView(memberRow(peer.name, true));
        }
    }

    private void renderDirectory(VoiceChatModule module) {
        directoryContainer.removeAllViews();
        List<VoiceChannelDirectory.Channel> channels = module == null
                ? Collections.emptyList() : module.publicChannels();
        directoryEmpty.setVisibility(channels.isEmpty() ? View.VISIBLE : View.GONE);
        for (VoiceChannelDirectory.Channel channel : channels) {
            boolean current = channel.current;
            TextView row = new TextView(activity);
            row.setText(activity.getString(R.string.voice_directory_row,
                    channel.name,
                    activity.getResources().getQuantityString(
                            R.plurals.voice_member_count, channel.memberCount, channel.memberCount)));
            row.setTextSize(compact ? 11f : 12f);
            row.setTextColor(current ? 0xFF6236E8 : 0xFFE6E9EC);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));

            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF1B1E22);
            background.setCornerRadius(dp(8));
            background.setStroke(dp(1), current ? 0xFF6236E8 : 0x22FFFFFF);
            row.setBackground(background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            row.setLayoutParams(params);

            // Tapping the channel you are already on would be a no-op; disable it rather than
            // leave a control that looks active and does nothing.
            if (current) {
                row.setAlpha(0.8f);
            } else {
                row.setOnClickListener(v -> joinPublicChannel(channel));
                DynamicAnim.applyPressScale(row);
            }
            directoryContainer.addView(row);
        }
    }

    private TextView memberRow(String label, boolean present) {
        TextView row = new TextView(activity);
        row.setText(label);
        row.setTextSize(compact ? 11f : 12f);
        row.setTextColor(present ? 0xFFE6E9EC : 0xFF8F979F);
        row.setPadding(0, dp(3), 0, dp(3));
        return row;
    }

    private TextView sectionHeading(int textRes) {
        TextView heading = new TextView(activity);
        heading.setText(textRes);
        heading.setTextSize(compact ? 10f : 11f);
        heading.setTextColor(0xFF8F979F);
        heading.setPadding(0, dp(8), 0, dp(2));
        return heading;
    }

    private Button pillButton(int textRes) {
        Button button = new Button(activity);
        button.setText(textRes);
        button.setTextSize(compact ? 11f : 12f);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        button.setLayoutParams(params);
        DynamicAnim.applyPressScale(button);
        return button;
    }

    private String channelLabel(String channel) {
        return VoiceChannel.WORLD.equals(channel)
                ? activity.getString(R.string.voice_chat_channel_world)
                : channel;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
