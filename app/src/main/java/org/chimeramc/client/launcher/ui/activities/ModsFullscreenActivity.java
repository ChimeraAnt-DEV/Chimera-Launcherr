package org.chimeramc.client.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.core.view.ViewCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.widget.NestedScrollView;
import android.graphics.Canvas;
import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.FileHandler;
import org.chimeramc.client.core.mods.Mod;
import org.chimeramc.client.core.mods.ModLoadDiagnostics;
import org.chimeramc.client.core.mods.ModManager;
import org.chimeramc.client.core.mods.ModSafeMode;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.ui.adapter.ModsAdapter;
import org.chimeramc.client.ui.dialogs.CustomAlertDialog;
import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.ui.views.MainViewModel;
import org.chimeramc.client.ui.views.MainViewModelFactory;
import org.chimeramc.client.util.PersonalizationManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModsFullscreenActivity extends BaseActivity {

    private RecyclerView modsRecycler;
    private ModsAdapter modsAdapter;
    private MainViewModel viewModel;
    private TextView totalModsCount;
    private TextView enabledModsCount;
    private ActivityResultLauncher<Intent> pickModLauncher;
    private FileHandler fileHandler;
    private InbuiltModManager inbuiltModManager;
    private int lastModsCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mods_fullscreen);
        setActiveNavTab(R.id.nav_tab_mods);

        View root = findViewById(android.R.id.content);
        if (root != null) {
            DynamicAnim.applyPressScaleRecursively(root);
        }

        inbuiltModManager = InbuiltModManager.getInstance(this);
        setupViews();
        setupViewModel();
        setupRecyclerView();
        setupLoadDiagnostics();
        fileHandler = new FileHandler(this, viewModel, VersionManager.get(this));
        
        pickModLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        fileHandler.processIncomingFilesWithConfirmation(result.getData(),
                                new ImportCallback(), true);
                    }
                }
        );
    }

    /**
     * A Java Edition .jar cannot be imported as a Bedrock mod, so the picker hands it to the
     * JavaBridge screen instead. The callback is attached to the existing FileOperationCallback
     * object, which keeps the two flows from having to be told apart at the call site.
     */
    private final class ImportCallback implements FileHandler.FileOperationCallback,
            FileHandler.JavaBridgeHandoff {
        @Override
        public void onSuccess(int processedFiles) {
            Toast.makeText(ModsFullscreenActivity.this, getString(R.string.files_processed, processedFiles), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(String errorMessage) {
            Toast.makeText(ModsFullscreenActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProgressUpdate(int progress) {
        }

        @Override
        public void onJavaJarSelected(Uri jarUri) {
            Intent intent = new Intent(ModsFullscreenActivity.this, JavaBridgeActivity.class);
            intent.putExtra(JavaBridgeActivity.EXTRA_JAR_URI, jarUri.toString());
            startActivity(intent);
        }
    }

    private void setupViews() {
        Button addModButton = findViewById(R.id.add_mod_fullscreen_button);

        addModButton.setVisibility(View.VISIBLE);
        addModButton.setOnClickListener(v -> {
            startFilePicker();
        });
        DynamicAnim.applyPressScale(addModButton);

        Button modHubButton = findViewById(R.id.mod_hub_fullscreen_button);
        modHubButton.setOnClickListener(v -> startActivity(new Intent(this, ModHubActivity.class)));
        DynamicAnim.applyPressScale(modHubButton);

        Button javaBridgeButton = findViewById(R.id.javabridge_fullscreen_button);
        javaBridgeButton.setOnClickListener(v ->
                startActivity(new Intent(this, JavaBridgeActivity.class)));
        DynamicAnim.applyPressScale(javaBridgeButton);

        Button modMenuButton = findViewById(R.id.mod_menu_button);
        boolean isMenuEnabled = inbuiltModManager.isModMenuEnabled();
        modMenuButton.setText(getString(R.string.mod_menu) + ": " + (isMenuEnabled ? "ON" : "OFF"));
        modMenuButton.setOnClickListener(v -> {
            boolean current = inbuiltModManager.isModMenuEnabled();
            inbuiltModManager.setModMenuEnabled(!current);
            modMenuButton.setText(getString(R.string.mod_menu) + ": " + (!current ? "ON" : "OFF"));
            Toast.makeText(this, !current ? R.string.mod_menu_enabled : R.string.mod_menu_disabled, Toast.LENGTH_SHORT).show();
        });
        DynamicAnim.applyPressScale(modMenuButton);

        totalModsCount = findViewById(R.id.total_mods_count);
        enabledModsCount = findViewById(R.id.enabled_mods_count);

        PersonalizationManager personalizationManager = new PersonalizationManager(this);
        View root = findViewById(android.R.id.content);
        if (root != null) {
            personalizationManager.applyAccentToView(root, this);
        }
    }

    /**
     * Shows why mods failed to load on the last launch, and offers to disable the mods that
     * were already live when the process died.
     *
     * A native mod that crashes the process does so mid-load, so neither the launch log nor the
     * normal "skipped incompatible mods" dialog can name it. {@link ModSafeMode} records the
     * loaded mods as they load, so a launch marker still set here means the previous launch
     * crashed and the recorded mods are exactly the ones that were live.
     */
    private void setupLoadDiagnostics() {
        List<ModLoadDiagnostics.Record> failures = ModLoadDiagnostics.getAll(this);
        Map<String, ModLoadDiagnostics.Record> byMod = new LinkedHashMap<>();
        for (ModLoadDiagnostics.Record failure : failures) {
            byMod.put(failure.modId, failure);
        }
        if (modsAdapter != null) {
            modsAdapter.setLoadFailures(byMod);
        }

        if (!failures.isEmpty()) {
            showLoadFailureBanner(failures);
        }

        if (ModSafeMode.hasCrashLoop(this)) {
            promptForCrashSafeMode();
        }
    }

    private void showLoadFailureBanner(List<ModLoadDiagnostics.Record> failures) {
        View banner = findViewById(R.id.mod_load_diagnostics_banner);
        if (banner == null) {
            return;
        }
        TextView message = findViewById(R.id.mod_load_diagnostics_message);
        TextView details = findViewById(R.id.mod_load_diagnostics_details);

        message.setText(getString(R.string.mod_load_diagnostics_message, failures.size()));

        StringBuilder lines = new StringBuilder();
        for (ModLoadDiagnostics.Record failure : failures) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            String name = failure.modName.isEmpty() ? failure.modId : failure.modName;
            lines.append(getString(R.string.mod_name_bullet, name,
                    getString(ModsAdapter.reasonResForKind(failure.kind))));
        }
        details.setText(lines.toString());

        View dismiss = findViewById(R.id.mod_load_diagnostics_dismiss);
        if (dismiss != null) {
            dismiss.setOnClickListener(v -> {
                banner.setVisibility(View.GONE);
                ModLoadDiagnostics.clear(this);
                if (modsAdapter != null) {
                    modsAdapter.setLoadFailures(java.util.Collections.emptyMap());
                }
            });
        }
        banner.setVisibility(View.VISIBLE);
    }

    /**
     * Offers to disable the mods recorded as loaded before the crash. Disabling is the only
     * useful action: the user cannot be expected to know which of several native mods was at
     * fault, and leaving them enabled guarantees the same crash loop.
     */
    private void promptForCrashSafeMode() {
        List<String> suspectIds = ModSafeMode.suspectModIds(this);
        if (suspectIds.isEmpty()) {
            ModSafeMode.acknowledgeCrash(this, false);
            return;
        }

        List<Mod> installed = viewModel.getModsLiveData().getValue();
        List<Mod> suspects = new ArrayList<>();
        if (installed != null) {
            for (Mod mod : installed) {
                if (suspectIds.contains(mod.getId()) && mod.isEnabled()) {
                    suspects.add(mod);
                }
            }
        }
        if (suspects.isEmpty()) {
            ModSafeMode.acknowledgeCrash(this, false);
            return;
        }

        StringBuilder names = new StringBuilder();
        for (Mod mod : suspects) {
            if (names.length() > 0) {
                names.append('\n');
            }
            names.append("- ").append(mod.getDisplayName());
        }

        String lastCrash = formatCrashTime(ModSafeMode.getLastCrashTime(this));
        new CustomAlertDialog(this)
                .setTitleText(getString(R.string.mod_safe_mode_title))
                .setMessage(getString(R.string.mod_safe_mode_message,
                        lastCrash, suspects.size(), names.toString()))
                .setBlurBackground(true)
                .setPositiveButton(getString(R.string.mod_safe_mode_disable), v -> {
                    for (Mod mod : suspects) {
                        viewModel.setModEnabled(mod.getId(), false);
                    }
                    ModSafeMode.acknowledgeCrash(this, true);
                    Toast.makeText(this, R.string.mod_safe_mode_disabled, Toast.LENGTH_LONG).show();
                    viewModel.refreshMods();
                })
                .setNegativeButton(getString(R.string.mod_safe_mode_keep), v ->
                        ModSafeMode.acknowledgeCrash(this, false))
                .show();
    }

    private String formatCrashTime(long timestampMs) {
        if (timestampMs <= 0L) {
            return getString(R.string.mod_safe_mode_recent);
        }
        return new java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(timestampMs));
    }

    private void startFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickModLauncher.launch(intent);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this, new MainViewModelFactory(getApplication())).get(MainViewModel.class);

        org.chimeramc.client.core.versions.GameVersion selectedVersion = VersionManager.get(this).getSelectedVersion();
        if (selectedVersion != null) {
            viewModel.setCurrentVersion(selectedVersion);
        }

        viewModel.getModsLiveData().observe(this, this::updateModsUI);
    }

    private void setupRecyclerView() {
        modsRecycler = findViewById(R.id.mods_recycler_fullscreen);
        modsAdapter = new ModsAdapter(new ArrayList<>());
        modsRecycler.setLayoutManager(new LinearLayoutManager(this));
        modsRecycler.setAdapter(modsAdapter);
        if (modsRecycler.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) modsRecycler.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        modsRecycler.post(() -> DynamicAnim.staggerRecyclerChildren(modsRecycler));

        modsAdapter.setOnModClickListener((mod, position, sharedView) -> {
            Intent intent = new Intent(this, ModDetailActivity.class);
            intent.putExtra("mod_filename", mod.getId());
            intent.putExtra("mod_position", position);
            startActivity(intent);
        });

        modsAdapter.setOnModEnableChangeListener((mod, enabled) -> {
            if (viewModel != null) {
                viewModel.setModEnabled(mod.getId(), enabled);
                updateModsCount(); 
            }
        });
        
        modsAdapter.setOnModReorderListener(reorderedMods -> {
            if (viewModel != null) {
                viewModel.reorderMods(reorderedMods);
                Toast.makeText(this, R.string.mod_reordered, Toast.LENGTH_SHORT).show();
            }
        });
        
        NestedScrollView nestedScrollView = findViewById(R.id.nested_scroll_view);
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            private long lastScrollTime = 0;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                modsAdapter.moveItem(fromPosition, toPosition);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                modsAdapter.commitReorder();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive && nestedScrollView != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastScrollTime > 16) {
                        int[] location = new int[2];
                        viewHolder.itemView.getLocationOnScreen(location);
                        int y = location[1];

                        nestedScrollView.getLocationOnScreen(location);
                        int svY = location[1];
                        int svHeight = nestedScrollView.getHeight();

                        int scrollZone = (int) (80 * recyclerView.getResources().getDisplayMetrics().density);
                        int scrollAmount = 0;
                        int maxScrollSpeed = 15;

                        if (y < svY + scrollZone) {
                            float ratio = 1.0f - Math.max(0, y - svY) / (float) scrollZone;
                            scrollAmount = (int) (-maxScrollSpeed * ratio);
                        } else if (y + viewHolder.itemView.getHeight() > svY + svHeight - scrollZone) {
                            float ratio = 1.0f - Math.max(0, svY + svHeight - (y + viewHolder.itemView.getHeight())) / (float) scrollZone;
                            scrollAmount = (int) (maxScrollSpeed * ratio);
                        }

                        if (scrollAmount != 0) {
                            nestedScrollView.scrollBy(0, scrollAmount);
                            lastScrollTime = now;
                            recyclerView.invalidate();
                        }
                    }
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                Mod mod = modsAdapter.getItem(pos);
                new CustomAlertDialog(ModsFullscreenActivity.this)
                        .setTitleText(getString(R.string.dialog_title_delete_mod))
                        .setMessage(getString(R.string.dialog_message_delete_mod))
                        .setPositiveButton(getString(R.string.dialog_positive_delete), v -> {
                            viewModel.removeMod(mod);
                            modsAdapter.removeAt(pos);
                            updateModsCount();
                        })
                        .setNegativeButton(getString(R.string.dialog_negative_cancel), v -> {
                            modsAdapter.notifyItemChanged(pos);
                        })
                        .show();
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(modsRecycler);
        modsAdapter.setItemTouchHelper(itemTouchHelper);
    }



    private void updateModsUI(List<Mod> mods) {
        if (modsAdapter != null) {
            modsAdapter.updateMods(mods);
            updateModsCount();
            if (modsRecycler != null) {
                int count = (mods != null) ? mods.size() : 0;
                if (lastModsCount == -1 || count != lastModsCount) {
                    modsRecycler.post(() -> DynamicAnim.staggerRecyclerChildren(modsRecycler));
                }
                lastModsCount = count;
            }
        }

        View emptyView = findViewById(R.id.empty_mods);
        if (emptyView != null) {
            View menuButton = findViewById(R.id.mod_menu_button);
            boolean supported = menuButton == null || menuButton.getVisibility() == View.VISIBLE;
            emptyView.setVisibility(supported && (mods == null || mods.isEmpty()) ? View.VISIBLE : View.GONE);
        }
    }

    private void updateModsCount() {
        List<Mod> mods = viewModel.getModsLiveData().getValue();
        
        int total = (mods != null ? mods.size() : 0);
        int enabled = 0;
        
        if (mods != null) {
            for (Mod mod : mods) {
                if (mod.isEnabled()) {
                    enabled++;
                }
            }
        }

        totalModsCount.setText(String.valueOf(total));
        enabledModsCount.setText(String.valueOf(enabled));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupViews();
        if (viewModel != null) {
            viewModel.refreshMods();
        }
        updateModsCount();
    }
}
