package org.chimeramc.client.ui.fragments;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.chimeramc.client.R;
import org.chimeramc.client.core.content.ContentImporter;
import org.chimeramc.client.core.content.ResourcePackItem;
import org.chimeramc.client.core.content.ResourcePackManager;
import org.chimeramc.client.core.content.SkinPackActivator;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.ui.adapter.SkinsAdapter;
import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.util.LauncherStorage;
import org.chimeramc.client.util.PersonalizationManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Skin pack management, hosted inside {@code CustomizeActivity}. */
public class SkinsSettingsFragment extends Fragment {

    private static final String PREFS_NAME = "skins_state";
    private static final String KEY_APPLIED_TYPE = "applied_type";
    private static final String KEY_APPLIED_NAME = "applied_name";

    private RecyclerView recycler;
    private SkinsAdapter adapter;
    private View loadingOverlay;
    private View emptyView;
    private VersionManager versionManager;
    private ActivityResultLauncher<String> importLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_skins, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PersonalizationManager pm = new PersonalizationManager(requireContext());
        view.setPadding(0, (int) ((pm.isCompactMode() ? 8 : 16) * getResources().getDisplayMetrics().density),
                0, (int) ((pm.isCompactMode() ? 8 : 16) * getResources().getDisplayMetrics().density));

        recycler = view.findViewById(R.id.skins_recycler);
        loadingOverlay = view.findViewById(R.id.skins_loading_overlay);
        emptyView = view.findViewById(R.id.skins_empty);
        Button importButton = view.findViewById(R.id.skins_import_button);
        Button emptyImportButton = view.findViewById(R.id.skins_empty_import_button);
        importButton.setOnClickListener(v -> startImport());
        emptyImportButton.setOnClickListener(v -> startImport());

        versionManager = VersionManager.get(requireContext());
        adapter = new SkinsAdapter();
        adapter.setOnSkinActionListener(this::applySkinPack);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        importSkinFile(uri);
                    }
                }
        );

        loadSkins();
    }

    @Override
    public void onDestroyView() {
        recycler = null;
        adapter = null;
        loadingOverlay = null;
        emptyView = null;
        super.onDestroyView();
    }

    private void startImport() {
        try {
            importLauncher.launch("*/*");
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importSkinFile(Uri uri) {
        Toast.makeText(requireContext(), R.string.skins_loading, Toast.LENGTH_SHORT).show();
        GameVersion version = versionManager.getSelectedVersion();
        File gameDataDir = resolveGameDataDir(requireContext(), version);
        File resDir = new File(gameDataDir, "resource_packs");
        File behDir = new File(gameDataDir, "behavior_packs");
        File skinDir = new File(gameDataDir, "skin_packs");
        List<Uri> uris = new ArrayList<>();
        uris.add(uri);
        new ContentImporter(requireContext()).importContent(uris, resDir, behDir, skinDir, null,
                new ContentImporter.ImportCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUi(message, true);
                    }

                    @Override
                    public void onError(String error) {
                        runOnUi(error, false);
                    }

                    @Override
                    public void onProgress(int progress) {
                    }
                });
    }

    private void runOnUi(String message, boolean longToast) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), message, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_LONG).show();
            loadSkins();
        });
    }

    private void loadSkins() {
        if (recycler == null) return;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        }
        new Thread(() -> {
            List<ResourcePackItem> packs = readSkinPacks();
            String applied = readAppliedPackName();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (adapter == null || recycler == null) return;
                adapter.updateSkinPacks(packs, applied, true);
                boolean hasPacks = !packs.isEmpty();
                if (emptyView != null) emptyView.setVisibility(hasPacks ? View.GONE : View.VISIBLE);
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                recycler.setVisibility(hasPacks ? View.VISIBLE : View.GONE);
                recycler.post(() -> DynamicAnim.staggerRecyclerChildren(recycler));
            });
        }).start();
    }

    private List<ResourcePackItem> readSkinPacks() {
        GameVersion version = versionManager.getSelectedVersion();
        if (version == null) return new ArrayList<>();
        ResourcePackManager manager = new ResourcePackManager(requireContext());
        manager.setCurrentVersion(version);
        List<ResourcePackItem> packs = manager.getSkinPacks();
        return packs != null ? packs : new ArrayList<>();
    }

    /**
     * Turns a skin pack on or off for the selected instance.
     *
     * The pack is registered in the game's global resource pack list, not merely recorded in
     * a launcher preference, because that file is what the game actually reads. The previous
     * implementation only wrote a preference, so "applied" never changed anything in-game.
     */
    private void applySkinPack(ResourcePackItem pack) {
        GameVersion version = versionManager.getSelectedVersion();
        if (version == null) {
            Toast.makeText(requireContext(), R.string.skins_no_version, Toast.LENGTH_LONG).show();
            return;
        }
        String profileId = version.getStorageProfileId();
        File gameDataDir = resolveGameDataDir(requireContext(), version);
        File source = pack.getFile();
        SkinPackActivator.PackIdentity identity = SkinPackActivator.readIdentity(source);
        if (identity == null) {
            Toast.makeText(requireContext(), R.string.skins_not_a_pack, Toast.LENGTH_LONG).show();
            return;
        }

        boolean applied = SkinPackActivator.isAppliedByLauncher(gameDataDir, identity.uuid);
        SkinPackActivator.Result result = applied
                ? SkinPackActivator.unapply(gameDataDir, identity.uuid)
                : SkinPackActivator.apply(source, gameDataDir);
        if (!result.success) {
            Toast.makeText(requireContext(),
                    getString(R.string.skins_apply_failed, result.message), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(),
                getString(applied ? R.string.skins_removed : R.string.skins_applied, pack.getPackName()),
                Toast.LENGTH_SHORT).show();
        loadSkins();
    }

    /** Name of the pack currently activated by the launcher for the selected instance, if any. */
    private String readAppliedPackName() {
        GameVersion version = versionManager.getSelectedVersion();
        if (version == null) return null;
        File gameDataDir = resolveGameDataDir(requireContext(), version);
        for (ResourcePackItem pack : readSkinPacks()) {
            SkinPackActivator.PackIdentity identity = SkinPackActivator.readIdentity(pack.getFile());
            if (identity != null && SkinPackActivator.isAppliedByLauncher(gameDataDir, identity.uuid)) {
                return pack.getPackName();
            }
        }
        return null;
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
    }

    /**
     * The game data root the selected instance actually plays from.
     *
     * <p>Skin packs must land in the same directory the game loads, so this follows the player's
     * storage setting rather than a fixed path. Falling back to the installed-profile id when no
     * version is selected keeps the import usable before an instance is chosen.
     */
    private File resolveGameDataDir(android.content.Context context, GameVersion version) {
        if (version == null) {
            return LauncherStorage.getActiveGameDataDir(
                    context,
                    LauncherStorage.INSTALLED_MINECRAFT_PROFILE_ID,
                    true,
                    LauncherStorage.readSavedContentStorageType(context));
        }
        return LauncherStorage.getActiveGameDataDir(
                context,
                version.getStorageProfileId(),
                version.versionIsolation,
                LauncherStorage.readSavedContentStorageType(context));
    }
}
