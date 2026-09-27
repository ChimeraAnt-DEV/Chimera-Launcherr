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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.core.voice.VoiceChannel;
import org.chimeramc.client.core.voice.VoiceChannelDirectory;
import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoicePeer;
import org.chimeramc.client.util.PersonalizationManager;

import java.util.Collections;
import java.util.List;

/**
 * The dedicated Voice tab: master switch, your channel and its members, the live public
 * directory, join-by-code and channel creation.
 *
 * <p>The screen is a view onto state that already exists. The master switch starts and stops the
 * same {@link VoiceChatModule} the in-game overlay uses, channel selection writes the same
 * preference the module beacons, and the directory is built from the beacons already arriving --
 * there is no second source of truth and nothing to keep in sync.
 *
 * <p>Unlike the in-game path, this screen owns the module's lifetime: it starts the link itself
 * so voice can be turned on before launching a world, and it never stops the link on the way out
 * (a later game session reuses the same module).
 *
 * <p>Private channels are join-by-code only and never appear in the directory. Creating a private
 * channel generates a {@code CHIMERA-XXXX} code and offers it in the system share sheet, which is
 * the whole invitation mechanism: the code <em>is</em> the channel id, so a friend typing it in
 * lands on the same channel with no server involved.
 */
public class VoiceChatActivity extends BaseActivity {

    private static final long REFRESH_MS = 500L;
    private static final int REQUEST_VOICE_MIC = 0x7C02;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean refreshing;

    private SwitchMaterial masterSwitch;
    private TextView masterState;
    private TextView channelName;
    private TextView channelKind;
    private TextView membersLabel;
    private LinearLayout membersContainer;
    private Button copyCodeButton;
    private Button shareCodeButton;
    private Button leaveButton;
    private EditText createName;
    private SwitchMaterial createPrivate;
    private Button createButton;
    private EditText joinCode;
    private Button joinButton;
    private TextView directoryEmpty;
    private LinearLayout directoryContainer;
    private org.chimeramc.client.ui.animation.OverscrollRefreshLayout refreshLayout;

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

        View back = findViewById(R.id.voice_back);
        if (back != null) back.setOnClickListener(v -> finish());

        bindViews();
        wireControls();
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
        masterState = findViewById(R.id.voice_master_state);
        channelName = findViewById(R.id.voice_channel_name);
        channelKind = findViewById(R.id.voice_channel_kind);
        membersLabel = findViewById(R.id.voice_members_label);
        membersContainer = findViewById(R.id.voice_members_container);
        copyCodeButton = findViewById(R.id.voice_copy_code_button);
        shareCodeButton = findViewById(R.id.voice_share_code_button);
        leaveButton = findViewById(R.id.voice_leave_button);
        createName = findViewById(R.id.voice_create_name);
        createPrivate = findViewById(R.id.voice_create_private);
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
    }

    /** Starts or stops the link from the launcher, requesting the mic only when transmitting. */
    private void setVoiceRunning(boolean enabled) {
        if (!enabled) {
            VoiceChatModule existing = VoiceChatModule.peek();
            if (existing != null && existing.isRunning()) existing.stop();
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
        boolean isPrivate = createPrivate.isChecked();

        String id;
        String displayName;
        if (isPrivate) {
            // The code is both the id and the invite; a typed name is only a label, so the
            // channel is still creatable with the field left blank.
            id = VoiceChannel.generateCode();
            displayName = typed.isEmpty() ? id : typed;
        } else {
            if (typed.isEmpty()) {
                Toast.makeText(this, R.string.voice_channel_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            id = typed;
            displayName = typed;
        }

        manager().joinVoiceChannel(id, displayName, isPrivate);
        announceAndRefresh();

        if (isPrivate) {
            copyToClipboard(id);
            shareCode(id);
        } else {
            Toast.makeText(this, getString(R.string.voice_channel_created, displayName),
                    Toast.LENGTH_SHORT).show();
        }
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
        manager().joinVoiceChannel(normalized, typed, isPrivate);
        announceAndRefresh();
        Toast.makeText(this, getString(R.string.voice_joined_channel, normalized),
                Toast.LENGTH_SHORT).show();
    }

    /** Switches to a public channel tapped in the directory. */
    private void joinPublicChannel(VoiceChannelDirectory.Channel channel) {
        manager().joinVoiceChannel(channel.id, channel.name, false);
        announceAndRefresh();
        Toast.makeText(this, getString(R.string.voice_joined_channel, channel.name),
                Toast.LENGTH_SHORT).show();
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
        channelKind.setText(isPrivate
                ? getString(R.string.voice_channel_kind_private, current)
                : getString(R.string.voice_channel_kind_public));

        // The share buttons only make sense for a channel that has a code to share.
        boolean hasCode = isPrivate || VoiceChannel.isJoinCode(current);
        copyCodeButton.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        shareCodeButton.setVisibility(hasCode ? View.VISIBLE : View.GONE);
        leaveButton.setVisibility(current.equals(VoiceChannel.WORLD) ? View.GONE : View.VISIBLE);

        renderMembers();
        renderDirectory();
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
            addMemberRow(getString(R.string.voice_members_none), false);
            return;
        }
        for (VoicePeer peer : members) {
            addMemberRow(peer.name, true);
        }
    }

    private void addMemberRow(String label, boolean present) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        row.setTextColor(getColor(present ? R.color.on_surface : R.color.text_secondary));
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
            count.setText(getResources().getQuantityString(
                    R.plurals.voice_member_count, channel.memberCount, channel.memberCount));
            row.setOnClickListener(v -> joinPublicChannel(channel));
            directoryContainer.addView(row);
        }
    }
}
