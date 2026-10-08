package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.core.cosmetics.CosmeticStore;
import org.chimeramc.client.core.cosmetics.CosmeticSyncModule;
import org.chimeramc.client.core.cosmetics.CosmeticSyncProtocol;
import org.chimeramc.client.core.cosmetics.NativeCosmeticsBridge;
import org.chimeramc.client.core.cosmetics.NativeCosmeticsFeed;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cosmetics section of the in-game Mod Menu: the player's character wearing the equipped
 * cape, accessory and pet, with a dropdown per cosmetic family to change them.
 *
 * <p>Built in code rather than XML because the preview is an animated custom view and the options
 * are data-driven from {@link CosmeticCatalog}. Each family has over a hundred styles, so a chip
 * row would be unusable; a dropdown keeps the whole catalogue reachable without a wall of buttons.
 * The equipped selection persists through {@link CosmeticStore}, so it survives closing the menu
 * and relaunching the game.
 *
 * <p>Selecting a cape publishes it to the native cosmetics registry
 * ({@link NativeCosmeticsBridge}), which the preloader's player-render hook reads directly. There is
 * no resource pack: the native path is the only route a cosmetic reaches the game, and
 * {@link NativeCosmeticsFeed} reports whether it is live this session.
 */
final class CosmeticsPanel {

    /** One dropdown row: a stable id, the label shown, and what picking it does. */
    private static final class Option {
        final String id;
        final String label;
        final Runnable apply;

        Option(String id, String label, Runnable apply) {
            this.id = id;
            this.label = label;
            this.apply = apply;
        }
    }

    /**
     * A {@code MODE_DROPDOWN} spinner whose list window can be collapsed on demand.
     *
     * <p>{@code Spinner} opens its list in a separate window and offers no public dismiss, so a list
     * left open outlives the panel's view. {@code onDetachedFromWindow} is protected, so this
     * subclass exposes it as {@link #dismiss()}, which is what lets the panel close every open list
     * when the Mod Menu is hidden.
     */
    private static final class DismissibleSpinner extends Spinner {
        DismissibleSpinner(Activity activity) {
            super(activity, MODE_DROPDOWN);
        }

        void dismiss() {
            if (getWindowToken() != null) onDetachedFromWindow();
        }
    }

    private final Activity activity;
    private final CosmeticStore store;
    private final LinearLayout root;
    private final CapePreviewView preview;
    private TextView gameStatus;
    private TextView syncStatus;
    private TextView syncToggle;
    private LinearLayout peersContainer;
    private LinearLayout manualPeerRow;
    private EditText manualPeerInput;

    /** Compact layout, so the peer list can size itself without re-deriving it. */
    private final boolean compact;

    /** True while the panel is programmatically setting a spinner, so its callback is ignored. */
    private boolean suppressSelection;

    /**
     * The dropdown spinners currently attached, so they can be collapsed when the panel is hidden.
     *
     * <p>A {@code MODE_DROPDOWN} spinner opens its list in a separate window that outlives this
     * view: closing the Mod Menu (or starting a game session) while a list is open leaves it
     * floating over whatever comes next. {@link #dismissDropdowns()} collapses every one of them.
     */
    private final List<DismissibleSpinner> dropdowns = new ArrayList<>();

    CosmeticsPanel(Activity activity, boolean compact) {
        this.activity = activity;
        this.compact = compact;
        this.store = new CosmeticStore(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(compact ? 10 : 16), dp(compact ? 8 : 12),
                dp(compact ? 10 : 16), dp(compact ? 8 : 12));

        preview = new CapePreviewView(activity);
        preview.setCape(store.getEquippedCapeForDisplay());
        preview.setAccessory(store.getEquippedAccessory());
        preview.setPet(store.getEquippedPet());
        preview.setContentDescription(activity.getString(R.string.cosmetics_preview_description));

        // The preview gets its own column so the caption can state which skin is on screen.
        // Without it a failed skin lookup is indistinguishable from a successful one.
        LinearLayout previewColumn = new LinearLayout(activity);
        previewColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, compact ? 0.42f : 0.36f);
        root.addView(previewColumn, previewParams);
        previewColumn.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView skinLine = new TextView(activity);
        skinLine.setTextSize(compact ? 9f : 10f);
        skinLine.setTextColor(0xFF8F979F);
        skinLine.setGravity(Gravity.CENTER);
        skinLine.setPadding(0, dp(4), 0, 0);
        if (preview.isShowingFallbackSkin()) {
            skinLine.setText(R.string.cosmetics_skin_fallback);
        } else if (preview.isShowingDefaultSteve()) {
            skinLine.setText(R.string.cosmetics_skin_default_steve);
        } else {
            skinLine.setText(activity.getString(R.string.cosmetics_skin_source,
                    preview.getSkinSourceName()));
        }
        previewColumn.addView(skinLine);

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, compact ? 0.58f : 0.64f);
        scrollerParams.setMarginStart(dp(compact ? 8 : 14));
        root.addView(scroller, scrollerParams);

        column.addView(sectionTitle(R.string.cosmetics_capes));
        column.addView(dropdown(capeOptions(), store.getEquippedCapeId()));

        column.addView(sectionTitle(R.string.cosmetics_accessories));
        column.addView(dropdown(accessoryOptions(), store.getEquippedAccessoryId()));

        // Honest about what the accessory styles are: distinct meshes tinted by palette, so the
        // 100+ entries are not mistaken for 100+ hand-sculpted models.
        TextView accessoryNote = new TextView(activity);
        accessoryNote.setText(R.string.accessory_style_note);
        accessoryNote.setTextSize(compact ? 9f : 10f);
        accessoryNote.setTextColor(0xFF8F979F);
        accessoryNote.setPadding(0, dp(4), 0, dp(6));
        column.addView(accessoryNote);

        column.addView(sectionTitle(R.string.cosmetics_pets));
        column.addView(dropdown(petOptions(), store.getEquippedPetId()));
        column.addView(petGaitRow());

        TextView note = new TextView(activity);
        note.setText(R.string.cosmetics_scope_note);
        note.setTextSize(compact ? 10f : 11f);
        note.setTextColor(0xFF8F979F);
        note.setPadding(0, dp(10), 0, 0);
        column.addView(note);

        column.addView(sectionTitle(R.string.cosmetics_in_game));
        gameStatus = new TextView(activity);
        gameStatus.setTextSize(compact ? 10f : 11f);
        gameStatus.setTextColor(0xFF8F979F);
        column.addView(gameStatus);

        // Cosmetic sync: whether the equipped set is being advertised to other Chimera users in the
        // world, and how many have been heard. This is the cross-player half of "cosmetic sync",
        // distinct from the native renderer status above.
        syncStatus = new TextView(activity);
        syncStatus.setTextSize(compact ? 10f : 11f);
        syncStatus.setTextColor(0xFF8F979F);
        syncStatus.setPadding(0, dp(4), 0, 0);
        column.addView(syncStatus);

        syncToggle = gameButton(R.string.cosmetics_sync_toggle_on, false, v -> {
            InbuiltModManager manager = InbuiltModManager.getInstance(activity);
            manager.setCosmeticSyncEnabled(!manager.isCosmeticSyncEnabled());
            refreshSyncStatus();
        });
        column.addView(syncToggle);

        // The peers we can see. Every entry is another GlowberryClient user (only this client
        // speaks the sync protocol), shown with the Glowberry mark and what they are wearing, so
        // "who can see my cosmetics, and whose can I see" is answered directly rather than as a
        // bare count.
        column.addView(sectionTitle(R.string.cosmetics_peers_title));
        peersContainer = new LinearLayout(activity);
        peersContainer.setOrientation(LinearLayout.VERTICAL);
        column.addView(peersContainer);

        // The manual peer fallback: when no Go relay is configured, a player can paste a direct
        // host:port so a peer not on the same Wi-Fi still sees the cosmetics. It is hidden while a
        // relay is configured, because the relay is the better route and the manual address is
        // ignored then.
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        manualPeerRow = new LinearLayout(activity);
        manualPeerRow.setOrientation(LinearLayout.HORIZONTAL);
        manualPeerRow.setPadding(0, dp(4), 0, 0);
        manualPeerInput = new EditText(activity);
        manualPeerInput.setHint(R.string.cosmetics_sync_manual_hint);
        manualPeerInput.setSingleLine(true);
        manualPeerInput.setTextSize(compact ? 10f : 11f);
        manualPeerInput.setText(manager.getCosmeticManualPeer());
        manualPeerRow.addView(manualPeerInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        manualPeerRow.addView(gameButton(R.string.cosmetics_sync_manual_save, false, v -> {
            manager.setCosmeticManualPeer(manualPeerInput.getText().toString());
            toast(R.string.cosmetics_sync_manual_saved);
            CosmeticSyncModule.requestAnnounce();
            refreshSyncStatus();
        }));
        column.addView(manualPeerRow);

        // There is no Apply/Remove button any more: a cosmetic reaches the game through the native
        // renderer the instant it is equipped, so the only thing left to show is whether that
        // native path is live this session. A fallback build still shows the equipped preview.
        refreshGameStatus();
        refreshSyncStatus();
    }

    /**
     * Publishes the newly equipped set to the native registry and tells the running sync module to
     * re-advertise now, so a peer sees a cape/accessory/pet swap without waiting for the slow timer.
     *
     * <p>The publish is what makes the swap appear in-game instantly: the preloader's render hook
     * reads the registry, so there is no pack to write and no world reload to wait for. A build
     * whose native hook is not live simply shows the equipped preview until it is.
     */
    private void onCosmeticChanged() {
        try {
            NativeCosmeticsBridge.publishLocal(
                    store.getEquippedCapeForDisplay(),
                    store.getEquippedAccessory(),
                    store.getEquippedPet());
        } catch (Throwable ignored) {
            // Fail-closed: the preview and the equipped selection are unaffected.
        }
        CosmeticSyncModule.requestAnnounce();
        refreshGameStatus();
        refreshSyncStatus();
    }

    private void refreshSyncStatus() {
        if (syncStatus == null) return;
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        boolean enabled = manager.isCosmeticSyncEnabled();
        if (syncToggle != null) {
            syncToggle.setText(enabled
                    ? R.string.cosmetics_sync_toggle_off
                    : R.string.cosmetics_sync_toggle_on);
        }
        // The manual address only matters when there is no relay; hide the row while a relay is
        // configured so the UI does not offer a control whose value is ignored.
        if (manualPeerRow != null) {
            manualPeerRow.setVisibility(manager.isVoiceRelayEnabled()
                    ? View.GONE : View.VISIBLE);
        }
        if (!enabled) {
            syncStatus.setText(R.string.cosmetics_sync_off);
            renderPeers(null);
            return;
        }
        CosmeticSyncModule module = CosmeticSyncModule.peek();
        if (module == null || !module.isRunning()) {
            // The link only runs during a game session (the overlay manager starts it there), so
            // outside a session the honest status is "starts with the game", not "off".
            syncStatus.setText(R.string.cosmetics_sync_with_game);
            renderPeers(null);
            return;
        }
        String route = module.routeLabel();
        syncStatus.setText(activity.getString(R.string.cosmetics_sync_seen,
                module.registry().size(), route));
        renderPeers(module);
    }

    /**
     * Lists the GlowberryClient users whose cosmetics we can see.
     *
     * <p>Every entry carries the Glowberry mark — only this client speaks the sync protocol, so a
     * peer in this list is a Glowberry user by construction — and what they are wearing, resolved
     * against our own catalogue. That answers both halves of the question directly: who can see my
     * cosmetics, and whose cosmetics I can see.
     */
    private void renderPeers(CosmeticSyncModule module) {
        if (peersContainer == null) return;
        peersContainer.removeAllViews();
        if (module == null) {
            peersContainer.addView(peerLine(activity.getString(R.string.cosmetics_peers_none)));
            return;
        }
        java.util.List<CosmeticSyncProtocol.Advert> peers = module.registry().snapshot();
        if (peers.isEmpty()) {
            peersContainer.addView(peerLine(activity.getString(R.string.cosmetics_peers_none)));
            return;
        }
        for (CosmeticSyncProtocol.Advert peer : peers) {
            peersContainer.addView(buildPeerRow(module, peer));
        }
    }

    /** One peer row: the Glowberry mark, the peer's name, and what they are wearing. */
    private View buildPeerRow(CosmeticSyncModule module, CosmeticSyncProtocol.Advert peer) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(GlowberryBadge.create(activity, compact));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(activity);
        name.setText(peer.name.isEmpty() ? peer.peerId : peer.name);
        name.setTextSize(compact ? 11f : 12f);
        name.setTextColor(0xFFE6E9EC);
        labels.addView(name);
        TextView wearing = new TextView(activity);
        wearing.setText(peerWearingLine(peer));
        wearing.setTextSize(compact ? 9f : 10f);
        wearing.setTextColor(0xFF8F979F);
        labels.addView(wearing);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** "Wearing: <cape / accessory / pet>", or "Wearing: nothing" when the set is empty. */
    private String peerWearingLine(CosmeticSyncProtocol.Advert peer) {
        java.util.List<String> parts = new ArrayList<>();
        CosmeticCatalog.Cape cape = CosmeticCatalog.equippedCape(peer.capeId);
        CosmeticCatalog.Accessory accessory = CosmeticCatalog.equippedAccessory(peer.accessoryId);
        CosmeticCatalog.Pet pet = CosmeticCatalog.equippedPet(peer.petId);
        if (cape != null) parts.add(cape.name);
        if (accessory != null) parts.add(accessory.name);
        if (pet != null) parts.add(pet.name);
        if (parts.isEmpty()) return activity.getString(R.string.cosmetics_peer_wearing_none);
        return activity.getString(R.string.cosmetics_peer_wearing,
                android.text.TextUtils.join(", ", parts));
    }

    private TextView peerLine(String text) {
        TextView line = new TextView(activity);
        line.setText(text);
        line.setTextSize(compact ? 9f : 10f);
        line.setTextColor(0xFF8F979F);
        line.setPadding(0, dp(2), 0, dp(2));
        return line;
    }

    // ---- Dropdowns -----------------------------------------------------------------------

    private List<Option> capeOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(CosmeticCatalog.NONE,
                activity.getString(R.string.cosmetics_none), () -> {
            store.setEquippedCape(CosmeticCatalog.NONE);
            preview.setCape(null);
            refreshGameStatus();
            onCosmeticChanged();
        }));
        for (final CosmeticCatalog.Cape cape : CosmeticCatalog.capes()) {
            options.add(new Option(cape.id, cape.name, () -> {
                store.setEquippedCape(cape.id);
                preview.setCape(cape);
                refreshGameStatus();
                onCosmeticChanged();
            }));
        }
        return options;
    }

    private List<Option> accessoryOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(CosmeticCatalog.NONE,
                activity.getString(R.string.cosmetics_none), () -> {
            store.setEquippedAccessory(CosmeticCatalog.NONE);
            preview.setAccessory(null);
            onCosmeticChanged();
        }));
        for (final CosmeticCatalog.Accessory accessory : CosmeticCatalog.accessories()) {
            if (CosmeticCatalog.NONE.equals(accessory.id)) continue;
            options.add(new Option(accessory.id, accessory.name, () -> {
                store.setEquippedAccessory(accessory.id);
                preview.setAccessory(accessory);
                onCosmeticChanged();
            }));
        }
        return options;
    }

    private List<Option> petOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(CosmeticCatalog.NONE,
                activity.getString(R.string.cosmetics_none), () -> {
            store.setEquippedPet(CosmeticCatalog.NONE);
            preview.setPet(null);
            onCosmeticChanged();
        }));
        for (final CosmeticCatalog.Pet pet : CosmeticCatalog.pets()) {
            options.add(new Option(pet.id, pet.name, () -> {
                store.setEquippedPet(pet.id);
                preview.setPet(pet);
                onCosmeticChanged();
            }));
        }
        return options;
    }

    /**
     * A labelled dropdown over one family.
     *
     * <p>Options are matched by id, never by position arithmetic, so a catalogue edit cannot make
     * the spinner point at the wrong entry. The callback is suppressed while the initial selection
     * is set, so opening the panel does not re-apply the equipped item.
     */
    private Spinner dropdown(List<Option> options, String selectedId) {
        List<String> labels = new ArrayList<>(options.size());
        for (Option o : options) labels.add(o.label);

        DismissibleSpinner spinner = new DismissibleSpinner(activity);
        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels));

        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id.equals(selectedId)) {
                suppressSelection = true;
                spinner.setSelection(i, false);
                suppressSelection = false;
                break;
            }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSelection) return;
                options.get(position).apply.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(6));
        spinner.setLayoutParams(lp);
        spinner.setBackground(dropdownBackground());
        dropdowns.add(spinner);
        return spinner;
    }

    /**
     * The gait selector for the equipped pet.
     *
     * <p>A pet only animates the gaits its species supports, so this offers exactly those. Walking
     * is the default; flying and swimming are the two the request called out, and a swimmer gets a
     * dedicated swim set while a crawler gets an elytra when it flies.
     */
    private View petGaitRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(4));

        TextView label = new TextView(activity);
        label.setText(R.string.cosmetics_pet_gait);
        label.setTextSize(11f);
        label.setTextColor(0xFF8F979F);
        label.setPadding(0, 0, dp(8), 0);
        row.addView(label);

        final List<CosmeticCatalog.PetLocomotion> gaits = availableGaits();
        List<String> labels = new ArrayList<>();
        for (CosmeticCatalog.PetLocomotion g : gaits) labels.add(gaitName(g));

        DismissibleSpinner spinner = new DismissibleSpinner(activity);
        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels));
        int current = gaits.indexOf(preview.getPetLocomotion());
        if (current > 0) spinner.setSelection(current, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSelection) return;
                preview.setPetLocomotion(gaits.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        row.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dropdowns.add(spinner);
        return row;
    }

    private List<CosmeticCatalog.PetLocomotion> availableGaits() {
        List<CosmeticCatalog.PetLocomotion> gaits = new ArrayList<>();
        CosmeticCatalog.Pet pet = store.getEquippedPet();
        CosmeticCatalog.PetSpecies species = pet == null ? null : pet.species;
        for (CosmeticCatalog.PetLocomotion g : CosmeticCatalog.PetLocomotion.values()) {
            if (species == null || species.supports(g)) gaits.add(g);
        }
        if (gaits.isEmpty()) gaits.add(CosmeticCatalog.PetLocomotion.WALK);
        return gaits;
    }

    private String gaitName(CosmeticCatalog.PetLocomotion gait) {
        switch (gait) {
            case IDLE: return activity.getString(R.string.cosmetics_gait_idle);
            case RUN: return activity.getString(R.string.cosmetics_gait_run);
            case CROUCH: return activity.getString(R.string.cosmetics_gait_crouch);
            case FLY: return activity.getString(R.string.cosmetics_gait_fly);
            case SWIM: return activity.getString(R.string.cosmetics_gait_swim);
            case WALK:
            default: return activity.getString(R.string.cosmetics_gait_walk);
        }
    }

    private GradientDrawable dropdownBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF23272B);
        bg.setStroke(dp(1), 0xFF3A4048);
        return bg;
    }

    // ---- Native application status -------------------------------------------------------

    /**
     * Reports whether the native cosmetics renderer is live this session.
     *
     * <p>There is no pack to install or remove any more: the equipped set is published to the
     * native registry and the preloader's player-render hook draws it directly. This line is the
     * honest status of that route, so a player on a build whose hook has not resolved is told the
     * cosmetic is preview-only rather than left wondering why it does not show.
     */
    private void refreshGameStatus() {
        if (gameStatus == null) return;
        try {
            gameStatus.setText(NativeCosmeticsFeed.isNativeRenderLive()
                    ? R.string.cosmetics_native_active
                    : R.string.cosmetics_native_inactive);
        } catch (Throwable t) {
            gameStatus.setText(R.string.cosmetics_native_inactive);
        }
    }

    private void toast(int res) {
        Toast.makeText(activity, activity.getString(res), Toast.LENGTH_SHORT).show();
    }

    private TextView gameButton(int labelRes, boolean primary, View.OnClickListener listener) {
        TextView button = new TextView(activity);
        button.setText(labelRes);
        button.setTextSize(12f);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(9), dp(16), dp(9));
        button.setTextColor(Color.WHITE);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(primary ? getAccent() : 0xFF23272B);
        if (!primary) bg.setStroke(dp(1), 0xFF3A4048);
        button.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        button.setLayoutParams(lp);
        button.setOnClickListener(listener);
        DynamicAnim.applyPressScale(button);
        return button;
    }

    View getView() {
        return root;
    }

    /**
     * Collapses every open dropdown and stops the preview's animation.
     *
     * <p>Called when the Mod Menu is hidden and when a game session starts. A {@code MODE_DROPDOWN}
     * spinner's list is a separate window; dismissing the panel's view does not close it, so without
     * this a cape/accessory/pet list stays floating over the game or the launcher after the menu is
     * gone. Stopping the preview here also keeps an off-screen view from driving a frame callback.
     */
    void onHidden() {
        dismissDropdowns();
        if (preview != null) preview.stopPreview();
    }

    /** Collapses every tracked dropdown. Safe to call repeatedly and when none is open. */
    void dismissDropdowns() {
        for (DismissibleSpinner spinner : dropdowns) {
            if (spinner != null) spinner.dismiss();
        }
    }

    private TextView sectionTitle(int res) {
        TextView title = new TextView(activity);
        title.setText(res);
        title.setTextSize(12f);
        title.setAllCaps(true);
        title.setTextColor(getAccent());
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(4), 0, dp(6));
        return title;
    }

    private int getAccent() {
        return new org.chimeramc.client.util.PersonalizationManager(activity).getAccentColor();
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
