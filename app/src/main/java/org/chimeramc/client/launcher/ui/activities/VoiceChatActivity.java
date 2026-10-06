package org.chimeramc.client.ui.activities;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.core.mods.inbuilt.overlay.VoiceUiKit;
import org.chimeramc.client.core.voice.VoiceChannel;
import org.chimeramc.client.core.voice.VoiceChannelCapacity;
import org.chimeramc.client.core.voice.VoiceChannelDirectory;
import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;
import org.chimeramc.client.core.voice.VoiceProtocol;
import org.chimeramc.client.util.PersonalizationManager;

import java.util.Collections;
import java.util.List;

/**
 * The dedicated Voice tab: master switch, your channel and its members with per-member mute,
 * channel creation (public or private, with a capacity for public rooms), the live public
 * directory and join-by-code.
 *
 * <p>The screen is a view onto state that already exists. The master switch starts and stops the
 * same {@link VoiceChatModule} the in-game overlay uses, channel selection writes the same
 * preference the module beacons, and the directory is built from the beacons already arriving --
 * there is no second source of truth and nothing to keep in sync. It mirrors the in-game
 * {@code VoicePanel}, and both share {@link VoiceUiKit} so their treatments match.
 *
 * <p>Unlike the in-game path, this screen owns the module's lifetime: it starts the link itself
 * so voice can be turned on before launching a world, and it never stops the link on the way out
 * (a later game session reuses the same module).
 *
 * <p>Private channels are join-by-code only and never appear in the directory. Creating a private
 * channel generates a {@code GLOWBERRY-XXXX} code and offers it in the system share sheet, which is
 * the whole invitation mechanism: the code <em>is</em> the channel id, so a friend typing it in
 * lands on the same channel with no server involved.
 *
 * <p><b>Capacity is advisory</b> (no server can eject) and <b>mute is client-side only</b> (the
 * peer is never told); both are stated in the UI rather than implied away.
 */
public class VoiceChatActivity extends BaseActivity {

    private static final long REFRESH_MS = 500L;
    private static final int REQUEST_VOICE_MIC = 0x7C02;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean refreshing;

    private SwitchMaterial masterSwitch;
    private SwitchMaterial relaySwitch;
    private EditText relayAddress;
    private EditText relayPassword;
    private EditText relayTokenSecret;
    private TextView relaySaveButton;
    private TextView relayStatus;
    private TextView masterState;
    private TextView channelName;
    private TextView channelKind;
    private TextView proximityMode;
    private TextView membersLabel;
    private LinearLayout membersContainer;
    private TextView copyCodeButton;
    private TextView shareCodeButton;
    private TextView leaveButton;
    private EditText createName;
    private TextView typePublic;
    private TextView typePrivate;
    private LinearLayout capacityRow;
    private LinearLayout capacityStepper;
    private TextView createButton;
    private EditText joinCode;
    private TextView joinButton;
    private TextView directoryEmpty;
    private LinearLayout directoryContainer;
    private org.chimeramc.client.ui.animation.OverscrollRefreshLayout refreshLayout;

    private boolean createPrivate;
    private int accent;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!refreshing) return;
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private InbuiltModManager manager() {
        return InbuiltModManager.getInstance(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        PersonalizationManager pm = new PersonalizationManager(this);
        View root = findViewById(R.id.voice_root);
        if (root != null) pm.applyAccentToView(root, this);
        accent = pm.hasCustomAccent() ? pm.getAccentColor() : 0xFF6236E8;

        View back = findViewById(R.id.voice_back);
        if (back != null) back.setOnClickListener(v -> finish());

        bindViews();
        buildTypeToggle();
        buildCapacityStepper();
        wireControls();
        stylePrimaryButtons();
        refresh();
    }

    /**
     * Voice is a Mod Menu destination, not a launcher nav tab, so it carries no nav bar.
     *
     * <p>The screen is opened from the in-game Mod Menu and from the Mods screen; without this it
     * would inject the launcher's own top bar, which is the very thing its tab was removed from.
     */
    @Override
    protected boolean shouldSkipNavBar() {
        return true;
    }

    /** No nav bar is shown, so bumper keys must not try to cycle launcher tabs. */
    @Override
    protected boolean shouldHandleNavKeys() {
        return false;
    }

    private void bindViews() {
        masterSwitch = findViewById(R.id.voice_master_switch);
        relaySwitch = findViewById(R.id.voice_relay_switch);
        relayAddress = findViewById(R.id.voice_relay_address);
        relayPassword = findViewById(R.id.voice_relay_password);
        relayTokenSecret = findViewById(R.id.voice_relay_token_secret);
        relaySaveButton = findViewById(R.id.voice_relay_save_button);
        relayStatus = findViewById(R.id.voice_relay_status);
        masterState = findViewById(R.id.voice_master_state);
        channelName = findViewById(R.id.voice_channel_name);
        channelKind = findViewById(R.id.voice_channel_kind);
        proximityMode = findViewById(R.id.voice_proximity_mode);
        membersLabel = findViewById(R.id.voice_members_label);
        membersContainer = findViewById(R.id.voice_members_container);
        copyCodeButton = findViewById(R.id.voice_copy_code_button);
        shareCodeButton = findViewById(R.id.voice_share_code_button);
        leaveButton = findViewById(R.id.voice_leave_button);
        createName = findViewById(R.id.voice_create_name);
        typePublic = findViewById(R.id.voice_type_public);
        typePrivate = findViewById(R.id.voice_type_private);
        capacityRow = findViewById(R.id.voice_capacity_row);
        capacityStepper = findViewById(R.id.voice_capacity_stepper);
        createButton = findViewById(R.id.voice_create_button);
        joinCode = findViewById(R.id.voice_join_code);
        joinButton = findViewById(R.id.voice_join_button);
        directoryEmpty = findViewById(R.id.voice_directory_empty);
        directoryContainer = findViewById(R.id.voice_directory_container);
        refreshLayout = findViewById(R.id.voice_directory_refresh);
        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(this::refreshFromPull);
        }
    }

    /** Applies a premium gradient treatment to the buttons the XML declares. */
    private void stylePrimaryButtons() {
        stylePill(createButton, true);
        stylePill(joinButton, true);
        stylePill(shareCodeButton, false);
        stylePill(copyCodeButton, false);
        stylePill(leaveButton, false);
    }

    private void stylePill(TextView button, boolean primary) {
        if (button == null) return;
        button.setTextColor(primary ? 0xFFFFFFFF : VoiceUiKit.lighten(accent, 0.35f));
        VoiceUiKit.applyPillBackground(button, accent, primary);
        org.chimeramc.client.ui.animation.DynamicAnim.applyPressScale(button);
    }

    /** Builds the Public/Private segmented toggle out of the two TextViews in the layout. */
    private void buildTypeToggle() {
        createPrivate = false;
        typePublic.setOnClickListener(v -> setChannelType(false));
        typePrivate.setOnClickListener(v -> setChannelType(true));
        setChannelType(false);
    }

    private void setChannelType(boolean privateChannel) {
        createPrivate = privateChannel;
        paintSegment(typePublic, !privateChannel);
        paintSegment(typePrivate, privateChannel);
        // Capacity is public-only; a private channel has no cap to set because the code already
        // bounds who can join.
        capacityRow.setVisibility(privateChannel ? View.GONE : View.VISIBLE);
    }

    private void paintSegment(TextView segment, boolean active) {
        if (segment == null) return;
        float density = getResources().getDisplayMetrics().density;
        if (active) {
            segment.setBackground(VoiceUiKit.roundedGradient(
                    VoiceUiKit.darken(accent, 0.12f), VoiceUiKit.lighten(accent, 0.06f),
                    VoiceUiKit.RADIUS_DP * density, 0, 0f));
            segment.setTextColor(0xFFFFFFFF);
        } else {
            segment.setBackground(null);
            segment.setTextColor(getColor(R.color.on_surface));
        }
    }

    private void buildCapacityStepper() {
        capacityStepper.removeAllViews();
        int initial = Math.max(VoiceChannelCapacity.MIN_HOST_CAPACITY,
                manager().getVoiceChannelCapacity() == VoiceProtocol.CAPACITY_NONE
                        ? 8 : manager().getVoiceChannelCapacity());
        LinearLayout stepper = VoiceUiKit.stepper(this, accent, false,
                VoiceChannelCapacity.MIN_HOST_CAPACITY, VoiceChannelCapacity.MAX_HOST_CAPACITY,
                initial, value -> manager().setVoiceChannelCapacity(value));
        capacityStepper.addView(stepper);
    }

    /**
     * A pulled refresh: re-reads the directory from the peers heard so far.
     *
     * <p>There is no server query to make, so the honest behaviour is to re-render from the live
     * registry and say so. The subtle vibration is the confirmation that the pull registered,
     * since the gesture deliberately has no spinner.
     */
    private void refreshFromPull() {
        renderDirectory();
        org.chimeramc.client.ui.animation.UiTouchFeedback.pressView(refreshLayout);
    }

    private void wireControls() {
        masterSwitch.setChecked(manager().resolveInbuiltModEnabled(ModIds.VOICE_CHAT, false));
        masterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            manager().setInbuiltModEnabled(ModIds.VOICE_CHAT, isChecked);
            setVoiceRunning(isChecked);
            refresh();
        });

        createButton.setOnClickListener(v -> createChannel());
        joinButton.setOnClickListener(v -> joinTypedChannel());
        leaveButton.setOnClickListener(v -> {
            manager().joinVoiceChannel(VoiceChannel.WORLD, "", false);
            announceAndRefresh();
        });
        copyCodeButton.setOnClickListener(v -> copyToClipboard(manager().getVoiceChannel()));
        shareCodeButton.setOnClickListener(v -> shareCode(manager().getVoiceChannel()));
        wireRelayControls();
    }

    /**
     * Wires the relay server fields.
     *
     * <p>Applying the settings persists them and restarts the link, because a running session holds
     * the transport it started with: changing the address without a restart would leave the old
     * socket live and the new one unused. If the link is not running there is nothing to restart,
     * and the values are simply stored for the next start.
     */
    private void wireRelayControls() {
        if (relaySwitch == null) return;
        relaySwitch.setChecked(manager().isVoiceRelayEnabled());
        relayAddress.setText(manager().getVoiceRelayAddress());
        relayPassword.setText(manager().getVoiceRelayPassword());
        if (relayTokenSecret != null) relayTokenSecret.setText(manager().getVoiceRelayTokenSecret());

        relaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            manager().setVoiceRelayEnabled(isChecked);
            applyRelaySettings();
        });
        relaySaveButton.setOnClickListener(v -> applyRelaySettings());
    }

    private void applyRelaySettings() {
        manager().setVoiceRelayAddress(relayAddress.getText() == null
                ? "" : relayAddress.getText().toString());
        manager().setVoiceRelayPassword(relayPassword.getText() == null
                ? "" : relayPassword.getText().toString());
        if (relayTokenSecret != null) {
            manager().setVoiceRelayTokenSecret(relayTokenSecret.getText() == null
                    ? "" : relayTokenSecret.getText().toString());
        }

        if (manager().isVoiceRelayEnabled()
                && org.chimeramc.client.core.voice.VoiceRelayAddress.parse(
                        manager().getVoiceRelayAddress()) == null) {
            Toast.makeText(this, R.string.voice_relay_address_invalid, Toast.LENGTH_SHORT).show();
        }

        VoiceChatModule module = VoiceChatModule.peek();
        if (module != null && module.isRunning()) {
            module.stop();
            module.start(manager());
        }
        refresh();
    }

    /** Starts or stops the link from the launcher, requesting the mic only when transmitting. */
    private void setVoiceRunning(boolean enabled) {
        if (!enabled) {
            // Best-effort: a teardown that throws (a device already released, a transport whose
            // reconnect races the stop) must not crash the screen the user is standing on.
            try {
                VoiceChatModule existing = VoiceChatModule.peek();
                if (existing != null && existing.isRunning()) existing.stop();
            } catch (Throwable t) {
                android.util.Log.w("VoiceChatActivity", "Voice chat stop failed", t);
            }
            return;
        }
        if (manager().isVoiceMicEnabled()
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_MIC);
        }
        VoiceChatModule module = VoiceChatModule.getPreferred(this);
        module.start(manager());
    }

    /** Creates a channel, generating a code when private, and joins it immediately. */
    private void createChannel() {
        String typed = createName.getText() == null ? "" : createName.getText().toString().trim();

        if (createPrivate) {
            // The code is both the id and the invite; a typed name is only a label, so the
            // channel is still creatable with the field left blank.
            String id = VoiceChannel.generateCode();
            String displayName = typed.isEmpty() ? id : typed;
            manager().joinVoiceChannel(id, displayName, true);
            announceAndRefresh();
            copyToClipboard(id);
            shareCode(id);
            return;
        }

        if (typed.isEmpty()) {
            Toast.makeText(this, R.string.voice_channel_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        manager().joinVoiceChannel(typed, typed, false);
        announceAndRefresh();
        Toast.makeText(this, getString(R.string.voice_channel_created, typed),
                Toast.LENGTH_SHORT).show();
    }

    private void joinTypedChannel() {
        String typed = joinCode.getText() == null ? "" : joinCode.getText().toString().trim();
        if (typed.isEmpty()) {
            Toast.makeText(this, R.string.voice_join_code_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = VoiceChannel.normalize(typed);
        // A code joins privately; a plain name joins the public room of that name. The id is the
        // join key either way, so this is the same path a directory tap takes.
        boolean isPrivate = VoiceChannel.isJoinCode(normalized);
        if (!isPrivate) {
            VoiceChannelDirectory.Channel target = findPublicChannel(normalized);
            if (target != null && !VoiceChannelCapacity.canJoin(
                    target.memberCount, target.capacity, false)) {
                Toast.makeText(this, getString(R.string.voice_channel_full, target.name),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        manager().joinVoiceChannel(normalized, typed, isPrivate);
        announceAndRefresh();
        Toast.makeText(this, getString(R.string.voice_joined_channel, normalized),
                Toast.LENGTH_SHORT).show();
    }

    /** Switches to a public channel tapped in the directory. */
    private void joinPublicChannel(VoiceChannelDirectory.Channel channel) {
        if (!VoiceChannelCapacity.canJoin(channel.memberCount, channel.capacity, false)) {
            Toast.makeText(this, getString(R.string.voice_channel_full, channel.name),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        manager().joinVoiceChannel(channel.id, channel.name, false);
        announceAndRefresh();
        Toast.makeText(this, getString(R.string.voice_joined_channel, channel.name),
                Toast.LENGTH_SHORT).show();
    }

    private VoiceChannelDirectory.Channel findPublicChannel(String id) {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module == null) return null;
        for (VoiceChannelDirectory.Channel channel : module.publicChannels()) {
            if (channel.id.equals(id)) return channel;
        }
        return null;
    }

    private void announceAndRefresh() {
        VoiceChatModule module = VoiceChatModule.peek();
        if (module != null && module.isRunning()) {
            module.applyConfig(manager());
            module.announceNow();
        }
        refresh();
    }

    private void copyToClipboard(String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.voice_channel_code_label), code));
        Toast.makeText(this, getString(R.string.voice_code_copied, code), Toast.LENGTH_SHORT).show();
    }

    private void shareCode(String code) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.voice_share_subject));
        share.putExtra(Intent.EXTRA_TEXT, getString(R.string.voice_share_body, code));
        startActivity(Intent.createChooser(share, getString(R.string.voice_share_chooser)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshing = true;
        handler.post(refreshRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // A mic granted after a listen-only start has to open the recorder; start() alone would
        // leave the switch on with nothing being captured.
        if (requestCode == REQUEST_VOICE_MIC) {
            VoiceChatModule module = VoiceChatModule.peek();
            if (module != null && module.isRunning()) module.applyConfig(manager());
        }
    }

    @Override
    protected void onPause() {
        refreshing = false;
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        refreshing = false;
        handler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    private void refresh() {
        boolean running = isVoiceRunning();
        masterState.setText(running ? R.string.voice_master_on : R.string.voice_master_off);

        String current = manager().getVoiceChannel();
        boolean isPrivate = manager().isVoiceChannelPrivate();
        String display = manager().getVoiceChannelName();
        channelName.setText(display.isEmpty() ? channelLabel(current) : display);

        int capacity = manager().getVoiceChannelCapacity();
        if (isPrivate) {
            channelKind.setText(getString(R.string.voice_channel_kind_private, current));
        } else if (capacity > VoiceProtocol.CAPACITY_NONE) {
            channelKind.setText(getString(R.string.voice_channel_kind_public_capped, capacity));
        } else {
            channelKind.setText(R.string.voice_channel_kind_public);
        }

        renderProximityMode();

        // The share buttons only make sense for a channel that has a code to share.
        boolean hasCode = isPrivate || VoiceChannel.isJoinCode(current);
        copyCodeButton.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        shareCodeButton.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        leaveButton.setVisibility(current.equals(VoiceChannel.WORLD) ? View.GONE : View.VISIBLE);

        renderMembers();
        renderDirectory();
        renderRelayStatus();
    }

    /**
     * Renders whether the distance rule is actually being applied.
     *
     * <p>The position feed is fail-closed, so "the seam is installed" and "a position is readable
     * this frame" are different states, and only the second means distance is in effect. Saying so
     * keeps the screen honest: a player in a loading screen hears everyone at full volume and would
     * otherwise assume proximity was broken.
     */
    private void renderProximityMode() {
        if (proximityMode == null) return;
        VoiceChatModule module = VoiceChatModule.peek();
        boolean live = module != null && module.isRunning() && module.hasLivePosition();
        proximityMode.setText(live ? R.string.voice_proximity_on : R.string.voice_proximity_waiting);
    }

    /**
     * Renders the relay connection state.
     *
     * <p>It reads the module's live view rather than the preferences: the setting says what the
     * player asked for, the status says what actually happened, and a reconnect is only visible in
     * the second. A configured relay that has not connected yet reads as connecting, not as
     * connected, so the status never claims a link that is not there.
     */
    private void renderRelayStatus() {
        if (relayStatus == null) return;
        if (!manager().isVoiceRelayEnabled()) {
            relayStatus.setText(R.string.voice_relay_off);
            return;
        }
        String configured = manager().getVoiceRelayAddress();
        org.chimeramc.client.core.voice.VoiceRelayAddress parsed =
                org.chimeramc.client.core.voice.VoiceRelayAddress.parse(configured);
        if (parsed == null) {
            relayStatus.setText(R.string.voice_relay_address_invalid);
            return;
        }

        VoiceChatModule module = VoiceChatModule.peek();
        if (module == null || !module.isRunning() || !module.isRelayMode()) {
            relayStatus.setText(getString(R.string.voice_relay_connecting, parsed.display()));
            return;
        }
        if (module.isRelayConnected()) {
            String codec = module.isUsingOpus() ? "Opus" : "PCM";
            relayStatus.setText(getString(R.string.voice_relay_connected_codec,
                    parsed.display(), module.relayClientId(), codec));
        } else {
            String error = module.transportError();
            relayStatus.setText(error == null
                    ? getString(R.string.voice_relay_reconnecting, parsed.display())
                    : getString(R.string.voice_relay_error, error));
        }
    }

    private boolean isVoiceRunning() {
        VoiceChatModule module = VoiceChatModule.peek();
        return module != null && module.isRunning();
    }

    private String channelLabel(String channel) {
        return VoiceChannel.WORLD.equals(channel)
                ? getString(R.string.voice_chat_channel_world)
                : channel;
    }

    private void renderMembers() {
        membersContainer.removeAllViews();
        VoiceChatModule module = VoiceChatModule.peek();
        List<VoicePeer> members = module == null
                ? Collections.emptyList()
                : module.channelMembers();
        membersLabel.setText(getResources().getQuantityString(
                R.plurals.voice_members, members.size(), members.size()));

        if (members.isEmpty()) {
            addMemberRow(getString(R.string.voice_members_none));
            return;
        }
        for (VoicePeer peer : members) {
            addMemberView(buildMemberRow(module, peer));
        }
    }

    /** One member row: the peer's name plus a local mute pill (the peer is never told). */
    private View buildMemberRow(VoiceChatModule module, VoicePeer peer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float density = getResources().getDisplayMetrics().density;
        row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

        TextView name = new TextView(this);
        name.setText(peer.name);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        name.setTextColor(getColor(R.color.on_surface));
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean muted = module.isMuted(peer.id);
        TextView mute = VoiceUiKit.pillButton(this, getString(
                muted ? R.string.voice_unmute : R.string.voice_mute), accent, !muted, false);
        mute.setOnClickListener(v -> {
            module.toggleMute(peer.id);
            updateMuteButton(mute, module.isMuted(peer.id));
        });
        row.addView(mute);
        return row;
    }

    private void updateMuteButton(TextView button, boolean muted) {
        button.setText(getString(muted ? R.string.voice_unmute : R.string.voice_mute));
        VoiceUiKit.applyPillBackground(button, accent, !muted);
        button.setTextColor(muted ? 0xFFFFFFFF : VoiceUiKit.lighten(accent, 0.35f));
        VoiceUiKit.pulse(button);
    }

    private void addMemberView(View view) {
        membersContainer.addView(view);
    }

    private void addMemberRow(String label) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        row.setTextColor(getColor(R.color.text_secondary));
        float density = getResources().getDisplayMetrics().density;
        row.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
        membersContainer.addView(row);
    }

    private void renderDirectory() {
        directoryContainer.removeAllViews();
        VoiceChatModule module = VoiceChatModule.peek();
        List<VoiceChannelDirectory.Channel> channels = module == null
                ? Collections.emptyList()
                : module.publicChannels();

        directoryEmpty.setVisibility(channels.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (VoiceChannelDirectory.Channel channel : channels) {
            View row = inflater.inflate(R.layout.item_voice_channel, directoryContainer, false);
            TextView name = row.findViewById(R.id.voice_channel_row_name);
            TextView count = row.findViewById(R.id.voice_channel_row_count);
            name.setText(channel.current
                    ? getString(R.string.voice_directory_current, channel.name)
                    : channel.name);
            if (channel.capacity > VoiceProtocol.CAPACITY_NONE) {
                count.setText(getString(R.string.voice_directory_capacity,
                        VoiceChannelCapacity.describe(channel.memberCount, channel.capacity)));
                count.setTextColor(channel.isFull() && !channel.current
                        ? 0xFFE5484D : getColor(R.color.text_secondary));
            } else {
                count.setText(getResources().getQuantityString(
                        R.plurals.voice_member_count, channel.memberCount, channel.memberCount));
            }
            boolean full = !channel.current && channel.isFull();
            if (channel.current) {
                row.setAlpha(0.8f);
            } else {
                row.setAlpha(full ? 0.7f : 1f);
                row.setOnClickListener(v -> joinPublicChannel(channel));
            }
            directoryContainer.addView(row);
        }
        VoiceUiKit.staggerIn(directoryContainer, (float) (16 * getResources().getDisplayMetrics().density));
    }
}
