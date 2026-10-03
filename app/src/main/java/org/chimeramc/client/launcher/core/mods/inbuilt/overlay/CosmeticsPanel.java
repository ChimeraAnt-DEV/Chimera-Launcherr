package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.core.content.CapeInGameInstaller;
import org.chimeramc.client.core.content.InGamePackChanger;
import org.chimeramc.client.core.content.SkinPackActivator;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.core.cosmetics.CosmeticStore;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.util.LauncherStorage;

import java.io.File;
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
 * <p>Selecting a cape also offers to put it on the character in-game, which is done by installing
 * a resource pack that adds a cape model to the player entity. See {@link CapeInGameInstaller}.
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

    private final Activity activity;
    private final CosmeticStore store;
    private final LinearLayout root;
    private final CapePreviewView preview;
    private TextView gameStatus;

    /** True while the panel is programmatically setting a spinner, so its callback is ignored. */
    private boolean suppressSelection;

    CosmeticsPanel(Activity activity, boolean compact) {
        this.activity = activity;
        this.store = new CosmeticStore(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(compact ? 10 : 16), dp(compact ? 8 : 12),
                dp(compact ? 10 : 16), dp(compact ? 8 : 12));

        preview = new CapePreviewView(activity);
        preview.setCape(store.getEquippedCape());
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

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(6), 0, 0);
        actions.addView(gameButton(R.string.cosmetics_apply_in_game, true, v -> applyInGame()));
        actions.addView(gameButton(R.string.cosmetics_remove_in_game, false, v -> removeInGame()));
        column.addView(actions);
        refreshGameStatus();
    }

    // ---- Dropdowns -----------------------------------------------------------------------

    private List<Option> capeOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option(CosmeticCatalog.NONE,
                activity.getString(R.string.cosmetics_none), () -> {
            store.setEquippedCape(CosmeticCatalog.NONE);
            preview.setCape(null);
            refreshGameStatus();
        }));
        for (final CosmeticCatalog.Cape cape : CosmeticCatalog.capes()) {
            options.add(new Option(cape.id, cape.name, () -> {
                store.setEquippedCape(cape.id);
                preview.setCape(cape);
                refreshGameStatus();
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
        }));
        for (final CosmeticCatalog.Accessory accessory : CosmeticCatalog.accessories()) {
            if (CosmeticCatalog.NONE.equals(accessory.id)) continue;
            options.add(new Option(accessory.id, accessory.name, () -> {
                store.setEquippedAccessory(accessory.id);
                preview.setAccessory(accessory);
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
        }));
        for (final CosmeticCatalog.Pet pet : CosmeticCatalog.pets()) {
            options.add(new Option(pet.id, pet.name, () -> {
                store.setEquippedPet(pet.id);
                preview.setPet(pet);
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

        Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
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

        Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
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

    // ---- In-game application -------------------------------------------------------------

    /**
     * Installs the equipped cape as a resource pack on the selected instance.
     *
     * <p>This is the step that makes the cape appear on the character in-game, so it reports
     * plainly when there is nothing to apply or no instance to apply it to rather than appearing
     * to succeed.
     */
    private void applyInGame() {
        List<File> gameDataDirs = resolveGameDataDirs();
        if (gameDataDirs.isEmpty()) {
            toast(R.string.cosmetics_no_instance);
            return;
        }
        CosmeticCatalog.Cape cape = store.getEquippedCape();
        CosmeticCatalog.Accessory accessory = store.getEquippedAccessory();
        CosmeticCatalog.Pet pet = store.getEquippedPet();
        if (cape == null && accessory == null && pet == null) {
            toast(R.string.cosmetics_no_cape_selected);
            return;
        }
        InGamePackChanger.ApplyOutcome outcome = CapeInGameInstaller.install(
                new File(activity.getFilesDir(), "cape"), gameDataDirs, cape, accessory, pet);
        switch (outcome) {
            case RELOADED:
                toast(R.string.cosmetics_applied_live);
                break;
            case RESTARTING:
                toast(R.string.cosmetics_applied_restarting);
                break;
            case NEXT_LOAD:
                toast(R.string.cosmetics_applied_next_load);
                break;
            default:
                toast(R.string.cosmetics_apply_failed);
                break;
        }
        refreshGameStatus();
    }

    private void removeInGame() {
        List<File> gameDataDirs = resolveGameDataDirs();
        if (gameDataDirs.isEmpty()) {
            toast(R.string.cosmetics_no_instance);
            return;
        }
        SkinPackActivator.Result result = CapeInGameInstaller.uninstall(gameDataDirs);
        toast(result.success ? R.string.cosmetics_removed_in_game : R.string.cosmetics_remove_failed);
        refreshGameStatus();
    }

    private void refreshGameStatus() {
        if (gameStatus == null) return;
        List<File> gameDataDirs = resolveGameDataDirs();
        if (gameDataDirs.isEmpty()) {
            gameStatus.setText(R.string.cosmetics_no_instance);
            return;
        }
        gameStatus.setText(CapeInGameInstaller.isInstalled(gameDataDirs)
                ? R.string.cosmetics_in_game_installed
                : R.string.cosmetics_in_game_not_installed);
    }

    /**
     * The selected instance's candidate game data roots, empty when no instance is selected.
     *
     * <p>Resolved through {@link LauncherStorage#getCandidateGameDataDirs} rather than a
     * hardcoded {@code getProfileGameDataDir(..., true)}: the game reads its packs from wherever
     * its storage resolves to, so installing into one fixed path made the cape apply
     * "successfully" to a directory the running game never loaded — the cape was written but
     * never appeared. Writing to every candidate root is the same defence the bundled-pack
     * installer uses.
     */
    private List<File> resolveGameDataDirs() {
        try {
            VersionManager versionManager = VersionManager.get(activity);
            GameVersion version = versionManager.getSelectedVersion();
            if (version == null) return java.util.Collections.emptyList();
            return LauncherStorage.getCandidateGameDataDirs(
                    activity, version.getStorageProfileId(), version.versionIsolation);
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
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
