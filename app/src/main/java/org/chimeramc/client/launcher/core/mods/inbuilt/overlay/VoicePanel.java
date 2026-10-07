package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.voice.VoiceChannel;
import org.chimeramc.client.core.voice.VoiceChannelCapacity;
import org.chimeramc.client.core.voice.VoiceChannelDirectory;
import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;
import org.chimeramc.client.core.voice.VoiceProtocol;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.util.Collections;
import java.util.List;

/**
 * The Voice section of the in-game Mod Menu: the proximity chat module's channel, its members, the
 * per-member mute controls, channel creation (public or private, with a capacity for public rooms)
 * and the public directory, without leaving the world.
 *
 * <p>Built in code like the other Mod Menu sections, because everything on it is live data from
 * the voice module rather than fixed layout. It is the in-game home of the feature; the standalone
 * {@code VoiceChatActivity} mirrors it for the launcher side and both use {@link VoiceUiKit} so the
 * treatments match.
 *
 * <p><b>The module is never started from inside a running game.</b> Starting a channel opens the
 * microphone and a UDP multicast socket, which is a deliberate user action; a tap on the menu
 * entry must not do it silently. The tuned-in state is reported, and the switch that starts the
 * module lives in the Mods tab, so the only thing this panel changes is which channel the voice
 * link is on.
 *
 * <p><b>Per-member mute is client-side only.</b> Muting a member adds them to the local mute set;
 * their audio is not mixed in for this listener and they are never told. See the module's
 * {@code toggleMute}.
 *
 * <p><b>Capacity is advisory.</b> There is no server, so a host cannot eject anyone; the cap is
 * advertised in the directory and a would-be joiner declines a full room. See
 * {@link VoiceChannelCapacity}.
 */
final class VoicePanel {

    private final Activity activity;
    private final InbuiltModManager manager;
    private final LinearLayout root;
    private final boolean compact;
    private final int accent;

    private TextView statusLine;
    private TextView channelName;
    private TextView channelKind;
    private TextView membersLabel;
    private LinearLayout membersContainer;
    private LinearLayout directoryContainer;
    private TextView directoryEmpty;
    private TextView leaveButton;
    private TextView copyButton;
    private TextView shareButton;
    private EditText createName;
    private EditText joinCode;
    private LinearLayout createCapacityRow;
    private LinearLayout controlColumn;

    private boolean createPrivate;

    VoicePanel(Activity activity, boolean compact) {
        this.activity = activity;
        this.compact = compact;
        this.manager = InbuiltModManager.getInstance(activity);
        this.accent = new ModMenuTheme(activity).accent();

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

        int capacity = manager.getVoiceChannelCapacity();
        if (isPrivate) {
            channelKind.setText(activity.getString(R.string.voice_channel_kind_private, current));
        } else if (capacity > VoiceProtocol.CAPACITY_NONE) {
            channelKind.setText(activity.getString(
                    R.string.voice_channel_kind_public_capped, capacity));
        } else {
            channelKind.setText(R.string.voice_channel_kind_public);
        }

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
        copyButton = VoiceUiKit.pillButton(activity,
                activity.getString(R.string.voice_copy_code), accent, false, compact);
        copyButton.setOnClickListener(v -> copyToClipboard(manager.getVoiceChannel()));
        shareButton = VoiceUiKit.pillButton(activity,
                activity.getString(R.string.voice_share_code), accent, true, compact);
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
        // The mute note sits under the list so the "you, not them" semantics are stated where the
        // buttons are, not only in a dialog.
        column.addView(hint(R.string.voice_mute_scope));

        // Controls that change the channel. Grouped so a single visibility flip hides the whole
        // block while the module is stopped.
        controlColumn = new LinearLayout(activity);
        controlColumn.setOrientation(LinearLayout.VERTICAL);
        controlColumn.setPadding(0, dp(12), 0, 0);
        buildCreateControls();
        buildJoinControls();

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.addView(controlColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return column;
    }

    /** The create-a-channel block: name, type toggle, hidden-until-public capacity and button. */
    private void buildCreateControls() {
        controlColumn.addView(sectionHeading(R.string.voice_create_channel));

        createName = new EditText(activity);
        createName.setHint(R.string.voice_channel_name_hint);
        createName.setTextSize(compact ? 11f : 12f);
        createName.setSingleLine(true);
        controlColumn.addView(createName);

        createPrivate = false;
        LinearLayout typeRow = VoiceUiKit.segmentedControl(activity,
                new CharSequence[]{
                        activity.getString(R.string.voice_channel_public),
                        activity.getString(R.string.voice_channel_private_short)},
                accent, compact, index -> {
                    createPrivate = index == 1;
                    // Capacity is public-only; a private channel has no cap to set because the
                    // code already bounds who can join.
                    createCapacityRow.setVisibility(createPrivate ? View.GONE : View.VISIBLE);
                });
        typeRow.setPadding(0, dp(6), 0, 0);
        controlColumn.addView(typeRow);

        createCapacityRow = new LinearLayout(activity);
        createCapacityRow.setOrientation(LinearLayout.VERTICAL);
        createCapacityRow.setPadding(0, dp(6), 0, 0);
        createCapacityRow.addView(sectionHeading(R.string.voice_capacity_label));
        int initialCapacity = Math.max(VoiceChannelCapacity.MIN_HOST_CAPACITY,
                manager.getVoiceChannelCapacity() == VoiceProtocol.CAPACITY_NONE
                        ? 8 : manager.getVoiceChannelCapacity());
        LinearLayout stepper = VoiceUiKit.stepper(activity, accent, compact,
                VoiceChannelCapacity.MIN_HOST_CAPACITY, VoiceChannelCapacity.MAX_HOST_CAPACITY,
                initialCapacity,
                value -> manager.setVoiceChannelCapacity(value));
        createCapacityRow.addView(stepper);
        createCapacityRow.addView(hint(R.string.voice_capacity_hint));
        controlColumn.addView(createCapacityRow);

        TextView createButton = VoiceUiKit.pillButton(activity,
                activity.getString(R.string.voice_create_button), accent, true, compact);
        createButton.setOnClickListener(v -> createChannel());
        controlColumn.addView(createButton);
    }

    private void buildJoinControls() {
        controlColumn.addView(sectionHeading(R.string.voice_join_by_code));

        joinCode = new EditText(activity);
        joinCode.setHint(R.string.voice_join_code_hint);
        joinCode.setTextSize(compact ? 11f : 12f);
        joinCode.setSingleLine(true);
        controlColumn.addView(joinCode);

        TextView joinButton = VoiceUiKit.pillButton(activity,
                activity.getString(R.string.voice_join_button), accent, true, compact);
        joinButton.setOnClickListener(v -> joinTypedChannel());
        controlColumn.addView(joinButton);

        leaveButton = VoiceUiKit.pillButton(activity,
                activity.getString(R.string.voice_leave_channel), accent, false, compact);
        leaveButton.setOnClickListener(v -> {
            manager.joinVoiceChannel(VoiceChannel.WORLD, "", false);
            announceAndRefresh();
        });
        controlColumn.addView(leaveButton);
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

    /** Creates and joins a channel, applying the public capacity or generating a private code. */
    private void createChannel() {
        String typed = createName.getText() == null
                ? "" : createName.getText().toString().trim();

        if (createPrivate) {
            // The code is both the id and the invite; a typed name is only a label, so the
            // channel is still creatable with the field left blank.
            String id = VoiceChannel.generateCode();
            String displayName = typed.isEmpty() ? id : typed;
            manager.joinVoiceChannel(id, displayName, true);
            createName.setText("");
            announceAndRefresh();
            copyToClipboard(id);
            shareCode(id);
            return;
        }

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
        if (joinCode == null) return;
        String typed = joinCode.getText() == null ? "" : joinCode.getText().toString().trim();
        if (typed.isEmpty()) {
            Toast.makeText(activity, R.string.voice_join_code_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = VoiceChannel.normalize(typed);
        boolean isPrivate = VoiceChannel.isJoinCode(normalized);
        if (!isPrivate) {
            VoiceChannelDirectory.Channel target = findPublicChannel(normalized);
            if (target != null && !VoiceChannelCapacity.canJoin(
                    target.memberCount, target.capacity, false)) {
                Toast.makeText(activity,
                        activity.getString(R.string.voice_channel_full, target.name),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        manager.joinVoiceChannel(normalized, typed, isPrivate);
        joinCode.setText("");
        announceAndRefresh();
        Toast.makeText(activity, activity.getString(R.string.voice_joined_channel, normalized),
                Toast.LENGTH_SHORT).show();
    }

    private void joinPublicChannel(VoiceChannelDirectory.Channel channel) {
        // Capacity is advisory (no server can eject), so a full room is refused here rather than
        // joined with a promise that cannot be kept.
        if (!VoiceChannelCapacity.canJoin(channel.memberCount, channel.capacity, false)) {
            Toast.makeText(activity, activity.getString(R.string.voice_channel_full, channel.name),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        manager.joinVoiceChannel(channel.id, channel.name, false);
        announceAndRefresh();
        Toast.makeText(activity, activity.getString(R.string.voice_joined_channel, channel.name),
                Toast.LENGTH_SHORT).show();
    }

    /** The directory entry for a channel id, or null when it is not currently advertised. */
    private VoiceChannelDirectory.Channel findPublicChannel(String id) {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module == null) return null;
        for (VoiceChannelDirectory.Channel channel : module.publicChannels()) {
            if (channel.id.equals(id)) return channel;
        }
        return null;
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
            membersContainer.addView(buildMemberRow(module, peer));
        }
        VoiceUiKit.staggerIn(membersContainer, dp(12));
    }

    /**
     * One member row: the peer's name plus a mute pill.
     *
     * <p>The pill's label and treatment reflect the local mute only -- the peer is never told, so
     * the wording is "Mute"/"Unmute" as a viewer's choice, not a status they would see.
     */
    private View buildMemberRow(VoiceChatModule module, VoicePeer peer) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        // Every member of this list is another GlowberryClient user -- only this client speaks the
        // voice protocol -- so the mark beside the name is the "I can see you're a Glowberry user"
        // badge, and therefore whose cosmetics sync to us.
        row.addView(GlowberryBadge.create(activity, compact));

        TextView name = new TextView(activity);
        name.setText(peer.name);
        name.setTextSize(compact ? 11f : 12f);
        name.setTextColor(0xFFE6E9EC);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (module == null) return row;
        boolean muted = module.isMuted(peer.id);
        TextView mute = VoiceUiKit.pillButton(activity, activity.getString(
                muted ? R.string.voice_unmute : R.string.voice_mute), accent, !muted, compact);
        mute.setOnClickListener(v -> {
            module.toggleMute(peer.id);
            // Re-render just this button so the toggle feels instant; a full refresh would rebuild
            // the list and lose the pulse.
            updateMuteButton(mute, module.isMuted(peer.id));
        });
        row.addView(mute);
        return row;
    }

    private void updateMuteButton(TextView button, boolean muted) {
        button.setText(activity.getString(muted ? R.string.voice_unmute : R.string.voice_mute));
        VoiceUiKit.applyPillBackground(button, accent, !muted);
        button.setTextColor(muted ? 0xFFFFFFFF : VoiceUiKit.lighten(accent, 0.35f));
        VoiceUiKit.pulse(button);
    }

    private void renderDirectory(VoiceChatModule module) {
        directoryContainer.removeAllViews();
        List<VoiceChannelDirectory.Channel> channels = module == null
                ? Collections.emptyList() : module.publicChannels();
        directoryEmpty.setVisibility(channels.isEmpty() ? View.VISIBLE : View.GONE);
        for (VoiceChannelDirectory.Channel channel : channels) {
            directoryContainer.addView(buildDirectoryRow(channel));
        }
        VoiceUiKit.staggerIn(directoryContainer, dp(16));
    }

    private View buildDirectoryRow(VoiceChannelDirectory.Channel channel) {
        boolean current = channel.current;
        boolean full = !current && channel.isFull();

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        float density = activity.getResources().getDisplayMetrics().density;
        int padH = dp(10);
        int padV = dp(8);
        row.setPadding(padH, padV, padH, padV);
        row.setBackground(VoiceUiKit.roundedFill(
                current ? VoiceUiKit.blend(0xFF1B1E22, accent, 0.12f) : 0xFF1B1E22,
                VoiceUiKit.RADIUS_DP * density,
                current ? accent : 0x22FFFFFF,
                Math.max(1f, density)));

        TextView name = new TextView(activity);
        name.setText(current
                ? activity.getString(R.string.voice_directory_current, channel.name)
                : channel.name);
        name.setTextSize(compact ? 12f : 13f);
        name.setTextColor(current ? accent : 0xFFE6E9EC);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        row.addView(name);

        TextView meta = new TextView(activity);
        if (channel.capacity > VoiceProtocol.CAPACITY_NONE) {
            meta.setText(activity.getString(R.string.voice_directory_capacity,
                    VoiceChannelCapacity.describe(channel.memberCount, channel.capacity)));
        } else {
            meta.setText(activity.getResources().getQuantityString(
                    R.plurals.voice_member_count, channel.memberCount, channel.memberCount));
        }
        meta.setTextSize(compact ? 10f : 11f);
        meta.setTextColor(full ? 0xFFE5484D : 0xFF8F979F);
        row.addView(meta);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);

        // Tapping the channel you are already on would be a no-op; a full room refuses politely.
        if (current) {
            row.setAlpha(0.8f);
        } else {
            row.setAlpha(full ? 0.7f : 1f);
            row.setClickable(true);
            row.setOnClickListener(v -> joinPublicChannel(channel));
            DynamicAnim.applyPressScale(row);
        }
        return row;
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
        heading.setTypeface(heading.getTypeface(), Typeface.BOLD);
        heading.setPadding(0, dp(8), 0, dp(2));
        return heading;
    }

    private TextView hint(int textRes) {
        TextView hint = new TextView(activity);
        hint.setText(textRes);
        hint.setTextSize(compact ? 9f : 10f);
        hint.setTextColor(0xFF6E767E);
        hint.setPadding(0, dp(2), 0, 0);
        return hint;
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
