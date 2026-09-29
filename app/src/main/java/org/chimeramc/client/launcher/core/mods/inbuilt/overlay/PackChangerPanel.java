package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chimeramc.client.R;
import org.chimeramc.client.core.content.InGamePackChanger;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.util.LauncherStorage;

import java.io.File;
import java.util.List;

/**
 * The Packs section of the in-game Mod Menu: the instance's resource packs with an on/off switch
 * each, so the player can change what is applied without leaving the world.
 *
 * <p>It is only offered when the instance has the in-game pack changer enabled in Instance
 * Settings. The section is built in code because the pack list is data-driven and must be
 * re-read after every toggle. It writes the same {@code global_resource_packs.json} the game
 * reads, through {@link InGamePackChanger}.
 */
final class PackChangerPanel {

    private final Activity activity;
    private final boolean compact;
    private final LinearLayout root;
    private final LinearLayout listColumn;
    private TextView statusLine;

    PackChangerPanel(Activity activity, boolean compact) {
        this.activity = activity;
        this.compact = compact;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(compact ? 12 : 18), dp(compact ? 8 : 14),
                dp(compact ? 12 : 18), dp(compact ? 8 : 14));

        TextView title = new TextView(activity);
        title.setText(R.string.pack_changer_title);
        title.setTextSize(compact ? 13f : 15f);
        title.setTextColor(0xFFE6E9EC);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        statusLine = new TextView(activity);
        statusLine.setTextSize(compact ? 10f : 11f);
        statusLine.setTextColor(0xFF8F979F);
        statusLine.setPadding(0, dp(2), 0, dp(8));
        root.addView(statusLine);

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        listColumn = new LinearLayout(activity);
        listColumn.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(listColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rebuild();
    }

    View getView() {
        return root;
    }

    private void rebuild() {
        listColumn.removeAllViews();

        if (!isEnabledForInstance()) {
            statusLine.setText(R.string.pack_changer_disabled);
            return;
        }
        File gameDataDir = resolveListingDir();
        if (gameDataDir == null) {
            statusLine.setText(R.string.pack_changer_no_instance);
            return;
        }

        List<File> dirs = resolveGameDataDirs();
        List<InGamePackChanger.PackEntry> packs = InGamePackChanger.listPacks(dirs);
        int activeCount = 0;
        for (InGamePackChanger.PackEntry pack : packs) {
            if (pack.active) activeCount++;
        }
        statusLine.setText(activity.getString(R.string.pack_changer_status,
                activeCount, packs.size()));

        if (packs.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.pack_changer_empty);
            empty.setTextSize(compact ? 11f : 12f);
            empty.setTextColor(0xFF8F979F);
            empty.setPadding(0, dp(12), 0, 0);
            listColumn.addView(empty);
            return;
        }

        for (InGamePackChanger.PackEntry pack : packs) {
            listColumn.addView(buildRow(pack));
        }
    }

    private View buildRow(InGamePackChanger.PackEntry pack) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(activity);
        name.setText(pack.name);
        name.setTextSize(compact ? 12f : 13f);
        name.setTextColor(0xFFE6E9EC);
        labels.addView(name);

        TextView meta = new TextView(activity);
        meta.setText(activity.getString(R.string.pack_changer_pack_meta, pack.version,
                pack.active ? activity.getString(R.string.pack_changer_on)
                        : activity.getString(R.string.pack_changer_off)));
        meta.setTextSize(compact ? 10f : 11f);
        meta.setTextColor(pack.active ? 0xFFA88CFF : 0xFF8F979F);
        labels.addView(meta);

        // A pill toggle rather than a SwitchMaterial: the overlay is inflated from a plain
        // FrameLayout and has no Material theme context here.
        TextView toggle = new TextView(activity);
        toggle.setPadding(dp(14), dp(6), dp(14), dp(6));
        toggle.setTextSize(compact ? 11f : 12f);
        toggle.setText(pack.active ? R.string.pack_changer_disable
                : R.string.pack_changer_enable);
        toggle.setTextColor(pack.active ? 0xFFE6E9EC : 0xFFA8B0B8);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(pack.active ? 0x556236E8 : 0x3324282C);
        background.setStroke(dp(1), pack.active ? 0xFF6236E8 : 0x44FFFFFF);
        toggle.setBackground(background);
        toggle.setOnClickListener(v -> {
            // Apply to every candidate root the way the cape/installer does: the game picks its
            // storage from isolation + internal/external, and a write to only the wrong guess is
            // silently ignored. setActive writes both the global list and each world's own list,
            // because the running world reads the latter.
            boolean ok = false;
            for (File gameDataDir : resolveGameDataDirs()) {
                ok |= InGamePackChanger.setActive(
                        gameDataDir, pack.uuid, pack.version, !pack.active);
            }
            if (!ok) {
                statusLine.setText(R.string.pack_changer_write_failed);
                return;
            }
            // Ask the running session to re-read its packs. When no live hook is installed the
            // write still applies on the next world load, and we say so rather than pretending
            // the change is live.
            boolean live = InGamePackChanger.requestReload();
            rebuild();
            statusLine.setText(live ? R.string.pack_changer_reloaded
                    : R.string.pack_changer_reload_on_next_load);
        });
        row.addView(toggle);

        return row;
    }

    /** True when the selected instance opted into the in-game pack changer. */
    private boolean isEnabledForInstance() {
        try {
            GameVersion version = VersionManager.get(activity).getSelectedVersion();
            return version != null && version.inGamePackChangerEnabled;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The first candidate root, used only for listing (the game's packs live in one root). */
    private File resolveListingDir() {
        List<File> dirs = resolveGameDataDirs();
        return dirs.isEmpty() ? null : dirs.get(0);
    }

    private List<File> resolveGameDataDirs() {
        try {
            GameVersion version = VersionManager.get(activity).getSelectedVersion();
            if (version == null) return java.util.Collections.emptyList();
            return LauncherStorage.getCandidateGameDataDirs(
                    activity, version.getStorageProfileId(), version.versionIsolation);
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
        }
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
