package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.core.cosmetics.CosmeticStore;
import org.chimeramc.client.core.content.CapeInGameInstaller;
import org.chimeramc.client.core.content.SkinPackActivator;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.util.LauncherStorage;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.io.File;
import java.util.List;

/**
 * The Cosmetics section of the in-game Mod Menu: the player's character wearing the equipped
 * cape and accessory, and a chip row per cosmetic group to change them.
 *
 * Built in code rather than XML because the cape preview is an animated custom view and the
 * chips are data-driven from {@link CosmeticCatalog}. The equipped selection persists through
 * {@link CosmeticStore}, so it survives closing the menu and relaunching the game.
 *
 * Selecting a cape also offers to put it on the character in-game, which is done by installing a
 * resource pack that overrides the player's cape texture. See {@link CapeInGameInstaller}.
 */
final class CosmeticsPanel {

    private final Activity activity;
    private final CosmeticStore store;
    private final LinearLayout root;
    private final CapePreviewView preview;
    private final LinearLayout capeRow;
    private final LinearLayout accessoryRow;
    private TextView gameStatus;

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
        HorizontalScrollView capeScroll = new HorizontalScrollView(activity);
        capeScroll.setHorizontalScrollBarEnabled(false);
        capeRow = new LinearLayout(activity);
        capeRow.setOrientation(LinearLayout.HORIZONTAL);
        capeScroll.addView(capeRow);
        column.addView(capeScroll);

        column.addView(sectionTitle(R.string.cosmetics_accessories));
        HorizontalScrollView accessoryScroll = new HorizontalScrollView(activity);
        accessoryScroll.setHorizontalScrollBarEnabled(false);
        accessoryRow = new LinearLayout(activity);
        accessoryRow.setOrientation(LinearLayout.HORIZONTAL);
        accessoryScroll.addView(accessoryRow);
        column.addView(accessoryScroll);

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

        rebuildChips();
    }

    /**
     * Installs the equipped cape as a resource pack on the selected instance.
     *
     * <p>This is the step that makes the cape appear on the character in-game, so it reports
     * plainly when there is nothing to apply or no instance to apply it to rather than appearing
     * to succeed.
     */
    private void applyInGame() {
        File gameDataDir = resolveGameDataDir();
        if (gameDataDir == null) {
            toast(R.string.cosmetics_no_instance);
            return;
        }
        CosmeticCatalog.Cape cape = store.getEquippedCape();
        if (cape == null) {
            toast(R.string.cosmetics_no_cape_selected);
            return;
        }
        SkinPackActivator.Result result = CapeInGameInstaller.install(
                new File(activity.getFilesDir(), "cape"), gameDataDir, cape);
        toast(result.success ? R.string.cosmetics_applied_in_game : R.string.cosmetics_apply_failed);
        refreshGameStatus();
    }

    private void removeInGame() {
        File gameDataDir = resolveGameDataDir();
        if (gameDataDir == null) {
            toast(R.string.cosmetics_no_instance);
            return;
        }
        SkinPackActivator.Result result = CapeInGameInstaller.uninstall(gameDataDir);
        toast(result.success ? R.string.cosmetics_removed_in_game : R.string.cosmetics_remove_failed);
        refreshGameStatus();
    }

    private void refreshGameStatus() {
        if (gameStatus == null) return;
        File gameDataDir = resolveGameDataDir();
        if (gameDataDir == null) {
            gameStatus.setText(R.string.cosmetics_no_instance);
            return;
        }
        gameStatus.setText(CapeInGameInstaller.isInstalled(gameDataDir)
                ? R.string.cosmetics_in_game_installed
                : R.string.cosmetics_in_game_not_installed);
    }

    /** The selected instance's game data root, or null when no instance is selected. */
    private File resolveGameDataDir() {
        try {
            VersionManager versionManager = VersionManager.get(activity);
            GameVersion version = versionManager.getSelectedVersion();
            if (version == null) return null;
            return LauncherStorage.getProfileGameDataDir(
                    activity, version.getStorageProfileId(), true);
        } catch (Throwable t) {
            return null;
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

    private void rebuildChips() {
        capeRow.removeAllViews();
        accessoryRow.removeAllViews();

        String equippedCape = store.getEquippedCapeId();
        List<CosmeticCatalog.Cape> capes = CosmeticCatalog.capes();
        for (CosmeticCatalog.Cape c : capes) {
            boolean selected = c.id.equals(equippedCape);
            TextView chip = chip(c.name, selected, c.color, c.trimColor);
            chip.setOnClickListener(v -> {
                String next = CosmeticCatalog.toggleCape(store.getEquippedCapeId(), c.id);
                store.setEquippedCape(next);
                preview.setCape(store.getEquippedCape());
                rebuildChips();
            });
            capeRow.addView(chip);
        }
        TextView noCape = chip(activity.getString(R.string.cosmetics_none), equippedCape == null
                || CosmeticCatalog.NONE.equals(equippedCape), 0xFF2B2F36, 0xFF6C757D);
        noCape.setOnClickListener(v -> {
            store.setEquippedCape(CosmeticCatalog.NONE);
            preview.setCape(null);
            rebuildChips();
        });
        capeRow.addView(noCape);

        String equippedAccessory = store.getEquippedAccessoryId();
        List<CosmeticCatalog.Accessory> accessories = CosmeticCatalog.accessories();
        for (CosmeticCatalog.Accessory a : accessories) {
            if (CosmeticCatalog.NONE.equals(a.id)) continue;
            boolean selected = a.id.equals(equippedAccessory);
            TextView chip = chip(a.name, selected, a.color, null);
            chip.setOnClickListener(v -> {
                String next = CosmeticCatalog.toggleAccessory(store.getEquippedAccessoryId(), a.id);
                store.setEquippedAccessory(next);
                preview.setAccessory(store.getEquippedAccessory());
                rebuildChips();
                if (next != null && !CosmeticCatalog.NONE.equals(next)) {
                    Toast.makeText(activity, activity.getString(R.string.cosmetics_equipped, a.name),
                            Toast.LENGTH_SHORT).show();
                }
            });
            accessoryRow.addView(chip);
        }
    }

    private TextView chip(String label, boolean selected, int fill, Integer trim) {
        TextView chip = new TextView(activity);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setTextColor(selected ? Color.WHITE : 0xFFD6DCE2);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(selected ? blend(fill) : 0xFF23272B);
        if (selected && trim != null) {
            bg.setStroke(dp(2), trim);
        } else if (selected) {
            bg.setStroke(dp(2), getAccent());
        }
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);
        DynamicAnim.applyPressScale(chip);
        return chip;
    }

    /** A slightly lightened fill so a dark cape colour still reads as a filled chip. */
    private static int blend(int color) {
        int r = Math.min(255, Color.red(color) + 48);
        int g = Math.min(255, Color.green(color) + 48);
        int b = Math.min(255, Color.blue(color) + 48);
        return Color.argb(255, r, g, b);
    }

    private int getAccent() {
        return new org.chimeramc.client.util.PersonalizationManager(activity).getAccentColor();
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
