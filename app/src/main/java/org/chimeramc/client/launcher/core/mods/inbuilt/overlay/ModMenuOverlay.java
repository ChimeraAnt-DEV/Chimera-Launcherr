package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.chimeramc.client.R;
import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.core.mods.inbuilt.ExternalModuleProvider;
import org.chimeramc.client.core.mods.inbuilt.InbuiltModuleProvider;
import org.chimeramc.client.core.mods.inbuilt.UnifiedMod;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ModMenuOverlay {
    private enum ModuleFilter {
        ALL,
        FAVORITES,
        ENABLED,
        INBUILT,
        EXTERNAL,
        PVP
    }

    private final Activity activity;
    private View overlayView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams wmParams;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isShowing = false;

    private RecyclerView modsRecycler;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchRunnable = this::applyFilters;
    private ModMenuAdapter adapter;
    private ModMenuTheme theme;
    private boolean hasStaggeredOnce = false;
    private EditText searchInput;
    private ImageButton clearSearchBtn;
    private TextView navModules, navSettings, navHudEditor, navCosmetics, navPacks;
    private TextView filterAll, filterFavorites, filterEnabled, filterInbuilt, filterExternal, filterPvp;
    private TextView moduleCountText, emptyStateText;
    private TextView compactFilterSelector, compactModuleCount;
    private View settingsContainer;
    private View modulesContainer;
    private FrameLayout cosmeticsContainer;
    private FrameLayout packsContainer;
    private PackChangerPanel packChangerPanel;
    private View emptyState;
    private View menuContainer;
    private View modMenuTopBar;
    private View modMenuLogo;
    private View filterBar;
    private View compactFilterBar;
    private CosmeticsPanel cosmeticsPanel;
    private Switch notificationsSwitch;
    private Switch pauseMenuOnlySwitch;
    private Switch compactModeSwitch;
    private SeekBar modMenuOpacitySeekBar;
    private TextView modMenuOpacityText;
    private SeekBar modMenuButtonOpacitySeekBar;
    private TextView modMenuButtonOpacityText;
    private SeekBar hudButtonSizeSeekBar;
    private TextView hudButtonSizeText;
    private boolean updatingHudButtonSize = false;
    private boolean compactMode = false;
    private GridLayoutManager modsLayoutManager;

    private List<UnifiedMod> allMods = new ArrayList<>();
    private List<UnifiedMod> filteredMods = new ArrayList<>();
    private final Set<String> favoriteKeys = new HashSet<>();
    private ModuleFilter activeFilter = ModuleFilter.ALL;

    private ModMenuCallback callback;
    private ModNotificationManager notificationManager;

    private void crossfade(View view) {
        view.setAlpha(0f);
        view.setTranslationX(30f);
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
            .start();
    }

    private void animateMenuEnter(final View menuContainer) {
        menuContainer.setAlpha(0f);
        menuContainer.setScaleX(0.85f);
        menuContainer.setScaleY(0.85f);

        menuContainer.post(() -> {
            menuContainer.setPivotX(menuContainer.getWidth() / 2f);
            menuContainer.setPivotY(menuContainer.getHeight() / 2f);

            int opacity = InbuiltModManager.getInstance(activity).getModMenuOpacity();
            float targetAlpha = opacity / 100f;

            menuContainer.animate()
                .alpha(targetAlpha)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .withLayer()
                .start();
        });
    }

    private void animateMenuExit(final View menuContainer, Runnable onEnd) {
        menuContainer.setPivotX(menuContainer.getWidth() / 2f);
        menuContainer.setPivotY(menuContainer.getHeight() / 2f);

        menuContainer.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
            .withLayer()
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    menuContainer.animate().setListener(null);
                    onEnd.run();
                }
            })
            .start();
    }

    private int getAccentColor() {
        return theme != null ? theme.accent() : ModMenuTheme.DEFAULT_ACCENT;
    }

    public interface ModMenuCallback {
        void onModToggled(String modId, boolean enabled);
        void onButtonOpacityChanged(int opacity);
    }

    public ModMenuOverlay(Activity activity) {
        this.activity = activity;
        this.theme = new ModMenuTheme(activity);
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
        this.notificationManager = new ModNotificationManager(activity);
    }

    public void setCallback(ModMenuCallback callback) {
        this.callback = callback;
    }

    public void show() {
        if (isShowing) {
            refreshMods();
            return;
        }
        showInternal();
    }

    private void showInternal() {
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);

            // The overlay root is clickable so a background tap closes the menu. A clickable
            // View is focusable, and a controller's d-pad or stick moves the focus onto it; the
            // platform then paints its default focus highlight — a translucent white wash over
            // the whole screen, which reads as "the menu put a 50% white overlay up" whenever the
            // player so much as nudges the stick. The highlight is suppressed here rather than
            // only in XML so it cannot come back through a theme or a re-inflated layout.
            disableFocusHighlight(overlayView);

            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            overlayView.setSystemUiVisibility(uiOptions);

            overlayView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    if (overlayView != null) {
                        overlayView.setSystemUiVisibility(uiOptions);
                    }
                }
            });

            setupViews();
            loadMods();

            wmParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            );
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                wmParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            wmParams.gravity = Gravity.CENTER;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();

            windowManager.addView(overlayView, wmParams);
            isShowing = true;

            overlayView.setAlpha(0f);
            overlayView.animate().alpha(1f).setDuration(220).start();

            View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
            if (menuContainer != null) {
                animateMenuEnter(menuContainer);
            }
        } catch (Exception e) {
            showFallback();
        }
    }

    private void showFallback() {
        if (isShowing) return;
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) return;

        overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
        disableFocusHighlight(overlayView);
        setupViews();
        loadMods();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootView.addView(overlayView, params);
        isShowing = true;
        wmParams = null;

        overlayView.setAlpha(0f);
        overlayView.animate().alpha(1f).setDuration(220).start();

        View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        if (menuContainer != null) {
            animateMenuEnter(menuContainer);
        }
    }

    /**
     * Stops the platform painting a focus highlight over the whole overlay.
     *
     * <p>A clickable View is implicitly focusable. The d-pad and the analogue stick both move
     * focus, and when the overlay root takes it the framework draws its default focus highlight:
     * a full-screen translucent white wash. To the player that is a 50% white overlay appearing
     * the moment the controller is touched, which obscures the game and the menu itself.
     *
     * <p>{@code focusable=false} on the root is the primary fix — it removes the view from focus
     * search entirely, so no highlight can be painted for any input. It is applied to the root
     * passed in (this helper is called with the overlay root); children keep their focusability
     * for controller navigation and only get the highlight flag cleared. Only the highlight flag
     * is ever touched — never the background, which carries each control's real styling. minSdk
     * is 28, so {@code setDefaultFocusHighlightEnabled} is always available.
     */
    private static void disableFocusHighlight(View view) {
        if (view == null) return;
        view.setFocusable(false);
        view.setDefaultFocusHighlightEnabled(false);
        clearFocusHighlightRecursive(view);
    }

    private static void clearFocusHighlightRecursive(View view) {
        if (view == null) return;
        view.setDefaultFocusHighlightEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearFocusHighlightRecursive(group.getChildAt(i));
            }
        }
    }


    private void setupViews() {
        menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        modMenuTopBar = overlayView.findViewById(R.id.mod_menu_topbar);
        modMenuLogo = overlayView.findViewById(R.id.mod_menu_logo);
        filterBar = overlayView.findViewById(R.id.filter_bar);
        compactFilterBar = overlayView.findViewById(R.id.compact_filter_bar);
        compactFilterSelector = overlayView.findViewById(R.id.compact_filter_selector);
        compactModuleCount = overlayView.findViewById(R.id.compact_module_count);
        ImageButton closeBtn = overlayView.findViewById(R.id.btn_close_menu);
        searchInput = overlayView.findViewById(R.id.search_input);
        clearSearchBtn = overlayView.findViewById(R.id.btn_clear_search);
        modsRecycler = overlayView.findViewById(R.id.mods_grid_recycler);
        navModules = overlayView.findViewById(R.id.nav_modules);
        navSettings = overlayView.findViewById(R.id.nav_settings);
        navHudEditor = overlayView.findViewById(R.id.nav_hud_editor);
        navCosmetics = overlayView.findViewById(R.id.nav_cosmetics);
        navPacks = overlayView.findViewById(R.id.nav_packs);
        filterAll = overlayView.findViewById(R.id.filter_all);
        filterFavorites = overlayView.findViewById(R.id.filter_favorites);
        filterEnabled = overlayView.findViewById(R.id.filter_enabled);
        filterInbuilt = overlayView.findViewById(R.id.filter_inbuilt);
        filterExternal = overlayView.findViewById(R.id.filter_external);
        filterPvp = overlayView.findViewById(R.id.filter_pvp);
        moduleCountText = overlayView.findViewById(R.id.module_count_text);
        settingsContainer = overlayView.findViewById(R.id.settings_container);
        modulesContainer = overlayView.findViewById(R.id.modules_container);
        cosmeticsContainer = overlayView.findViewById(R.id.cosmetics_container);
        packsContainer = overlayView.findViewById(R.id.packs_container);
        emptyState = overlayView.findViewById(R.id.empty_state);
        emptyStateText = overlayView.findViewById(R.id.empty_state_text);
        notificationsSwitch = overlayView.findViewById(R.id.switch_notifications);
        pauseMenuOnlySwitch = overlayView.findViewById(R.id.switch_pause_menu_only);
        compactModeSwitch = overlayView.findViewById(R.id.switch_compact_mod_menu);

        View hudEditorTools = overlayView.findViewById(R.id.hud_editor_tools);
        View hudEditorDragHandle = overlayView.findViewById(R.id.hud_editor_drag_handle);
        if (hudEditorDragHandle != null) setupHudEditorDragHandle(hudEditorDragHandle, hudEditorTools);
        View btnHudSave = overlayView.findViewById(R.id.btn_hud_save);
        View btnHudCancel = overlayView.findViewById(R.id.btn_hud_cancel);
        View modMenuContainer = overlayView.findViewById(R.id.mod_menu_container);
        hudButtonSizeSeekBar = overlayView.findViewById(R.id.seekbar_hud_button_size);
        hudButtonSizeText = overlayView.findViewById(R.id.text_hud_button_size);

        if (hudButtonSizeSeekBar != null) {
            hudButtonSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || updatingHudButtonSize) return;
                    InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
                    if (manager != null) {
                        manager.setSelectedHudEditorButtonSize(progress);
                    }
                    updateHudButtonSizeText(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            updateHudEditorSizeControls(0);
        }

        if (navHudEditor != null) {
            navHudEditor.setOnClickListener(v -> enterHudEditorMode(modMenuContainer, hudEditorTools));
        }

        // Touch feedback on the stable chrome (nav + filter chips + close). Recycler rows get
        // their own feedback in the adapter, since they are recycled and rebound.
        for (View v : new View[]{navModules, navSettings, navHudEditor, navCosmetics,
                filterAll, filterFavorites, filterEnabled, filterInbuilt, filterExternal, filterPvp,
                closeBtn, clearSearchBtn}) {
            if (v != null) DynamicAnim.applyPressScale(v);
        }

        if (btnHudSave != null) {
            btnHudSave.setOnClickListener(v -> {
                exitHudEditorMode(modMenuContainer, hudEditorTools);
            });
        }

        View btnHudReset = overlayView.findViewById(R.id.btn_hud_reset);
        if (btnHudReset != null) {
            btnHudReset.setOnClickListener(v -> {
                InbuiltOverlayManager.getInstance().resetAllPositionsToCenter();
            });
        }

        if (btnHudCancel != null) {
            btnHudCancel.setOnClickListener(v -> {
                exitHudEditorMode(modMenuContainer, hudEditorTools);
            });
        }

        // Close on background tap
        overlayView.setOnClickListener(v -> {
            // Only hide if not in HUD editor mode
            if (hudEditorTools == null || hudEditorTools.getVisibility() != View.VISIBLE) {
                hide();
            }
        });
        menuContainer.setOnClickListener(v -> {}); // Consume clicks
        if (hudEditorTools != null) {
            hudEditorTools.setOnClickListener(v -> {}); // Consume clicks
        }

        closeBtn.setOnClickListener(v -> hide());

        // Search functionality
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 60L);
                clearSearchBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        clearSearchBtn.setOnClickListener(v -> {
            searchInput.setText("");
            clearSearchBtn.setVisibility(View.GONE);
        });
        setupFilterButtons();
        setupCompactFilter();

        View btnBackToModules = overlayView.findViewById(R.id.btn_back_to_modules);
        if (btnBackToModules != null) {
            btnBackToModules.setOnClickListener(v -> showModulesSection());
        }

        // Navigation
        navModules.setOnClickListener(v -> showModulesSection());
        navSettings.setOnClickListener(v -> showSettingsSection());
        if (navCosmetics != null) navCosmetics.setOnClickListener(v -> showCosmeticsSection());
        if (navPacks != null) navPacks.setOnClickListener(v -> showPacksSection());

        // Settings
        InbuiltModManager modManager = InbuiltModManager.getInstance(activity);
        compactMode = modManager.isModMenuCompact();
        notificationsSwitch.setChecked(modManager.isNotificationsEnabled());
        notificationsSwitch.setOnCheckedChangeListener((btn, checked) -> {
            modManager.setNotificationsEnabled(checked);
        });

        if (pauseMenuOnlySwitch != null) {
            pauseMenuOnlySwitch.setChecked(modManager.isPauseMenuOnly());
            pauseMenuOnlySwitch.setOnCheckedChangeListener((btn, checked) -> {
                modManager.setPauseMenuOnly(checked);
            });
        }

        if (compactModeSwitch != null) {
            compactModeSwitch.setChecked(compactMode);
            compactModeSwitch.setOnCheckedChangeListener((btn, checked) -> {
                modManager.setModMenuCompact(checked);
                setCompactMode(checked);
            });
        }

        modMenuOpacitySeekBar = overlayView.findViewById(R.id.seekbar_mod_menu_opacity);
        modMenuOpacityText = overlayView.findViewById(R.id.text_mod_menu_opacity);
        int currentMenuOpacity = modManager.getModMenuOpacity();
        modMenuOpacitySeekBar.setProgress(currentMenuOpacity);
        modMenuOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, currentMenuOpacity));
        modMenuOpacitySeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    modMenuOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, progress));
                    modManager.setModMenuOpacity(progress);
                    applyMenuOpacity();
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        modMenuButtonOpacitySeekBar = overlayView.findViewById(R.id.seekbar_mod_menu_button_opacity);
        modMenuButtonOpacityText = overlayView.findViewById(R.id.text_mod_menu_button_opacity);
        int currentButtonOpacity = modManager.getModMenuButtonOpacity();
        modMenuButtonOpacitySeekBar.setProgress(currentButtonOpacity);
        modMenuButtonOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, currentButtonOpacity));
        modMenuButtonOpacitySeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    modMenuButtonOpacityText.setText(activity.getString(R.string.mod_menu_percent_value, progress));
                    modManager.setModMenuButtonOpacity(progress);
                    if (callback != null) {
                        callback.onButtonOpacityChanged(progress);
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        applyMenuOpacity();

        adapter = new ModMenuAdapter(new ModMenuTheme(activity));
        adapter.setCompactMode(compactMode);
        modsLayoutManager = new GridLayoutManager(activity, compactMode ? 1 : 4);
        modsLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter != null && adapter.isGroupHeader(position)
                    ? modsLayoutManager.getSpanCount()
                    : 1;
            }
        });
        modsRecycler.setLayoutManager(modsLayoutManager);
        modsRecycler.setItemAnimator(null);
        modsRecycler.setHasFixedSize(true);
        adapter.setOnModActionListener(new ModMenuAdapter.OnModActionListener() {
            @Override
            public void onToggle(UnifiedMod mod, boolean enabled) {
                mod.applyEnabled(enabled);
                InbuiltModManager modManager = InbuiltModManager.getInstance(activity);
                if (enabled && modManager.isNotificationsEnabled()) {
                    notificationManager.show(mod.getName(), mod.getStableKey());
                }
                if (callback != null) {
                    callback.onModToggled(mod.getStableKey(), enabled);
                }
                if (activeFilter == ModuleFilter.ENABLED) {
                    applyFilters();
                }
            }
            @Override
            public void onConfig(UnifiedMod mod) {
                showConfigSection(mod);
            }
            @Override
            public void onFavoriteChanged(UnifiedMod mod, boolean favorite) {
                InbuiltModManager.getInstance(activity).setModFavorite(mod.getStableKey(), favorite);
                if (favorite) {
                    favoriteKeys.add(mod.getStableKey());
                } else {
                    favoriteKeys.remove(mod.getStableKey());
                }
                if (activeFilter == ModuleFilter.FAVORITES) {
                    applyFilters();
                }
            }
        });
        modsRecycler.setAdapter(adapter);
        applyCompactModeLayout(compactMode);

        showModulesSection();
    }

    private void showModulesSection() {
        updateNavigationItems(true, false, false, false, false);

        if (modulesContainer.getVisibility() != View.VISIBLE) {
            modulesContainer.setVisibility(View.VISIBLE);
            crossfade(modulesContainer);
        }
        settingsContainer.setVisibility(View.GONE);
        hideCosmetics();
        hidePacks();

        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            if (modConfigContainer != null) modConfigContainer.setVisibility(View.GONE);
            if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
            if (configHeader != null) configHeader.setVisibility(View.GONE);
            updateFilterBarVisibility();
        }
    }

    private void showSettingsSection() {
        updateNavigationItems(false, false, false, true, false);

        modulesContainer.setVisibility(View.GONE);
        hideCosmetics();
        hidePacks();
        if (settingsContainer.getVisibility() != View.VISIBLE) {
            settingsContainer.setVisibility(View.VISIBLE);
            crossfade(settingsContainer);
        }

        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            if (modConfigContainer != null) modConfigContainer.setVisibility(View.GONE);
            if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
            if (configHeader != null) configHeader.setVisibility(View.GONE);
            if (filterBar != null) filterBar.setVisibility(View.GONE);
            if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
        }
    }

    /**
     * Cosmetics: the player's Minecraft character plus capes and accessories.
     *
     * The panel is created lazily and owns the animated cape preview, so this method only
     * decides which section is on screen.
     */
    private void showCosmeticsSection() {
        updateNavigationItems(false, true, false, false, false);

        modulesContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
        hidePacks();
        if (cosmeticsContainer != null) {
            if (cosmeticsPanel == null) {
                cosmeticsPanel = new CosmeticsPanel(activity, compactMode);
                cosmeticsContainer.addView(cosmeticsPanel.getView(),
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
            }
            cosmeticsContainer.setVisibility(View.VISIBLE);
            crossfade(cosmeticsContainer);
        }

        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            if (modConfigContainer != null) modConfigContainer.setVisibility(View.GONE);
            if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
            if (configHeader != null) configHeader.setVisibility(View.GONE);
            if (filterBar != null) filterBar.setVisibility(View.GONE);
            if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
        }
    }

    private void hideCosmetics() {
        if (cosmeticsContainer != null) cosmeticsContainer.setVisibility(View.GONE);
    }

    /**
     * The in-game pack changer: the instance's resource packs with an on/off toggle each.
     *
     * Built lazily and rebuilt on entry because the pack list changes whenever the player
     * imports a pack from the launcher. Only reachable when the instance enabled it, which the
     * panel reports itself rather than hiding the tab — a hidden destination reads as a bug.
     */
    private void showPacksSection() {
        updateNavigationItems(false, false, false, false, true);

        modulesContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
        hideCosmetics();
        hidePacks();
        if (packsContainer != null) {
            packsContainer.removeAllViews();
            packChangerPanel = new PackChangerPanel(activity, compactMode);
            packsContainer.addView(packChangerPanel.getView(),
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            packsContainer.setVisibility(View.VISIBLE);
            crossfade(packsContainer);
        }

        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            if (modConfigContainer != null) modConfigContainer.setVisibility(View.GONE);
            if (searchContainer != null) searchContainer.setVisibility(View.GONE);
            if (configHeader != null) configHeader.setVisibility(View.VISIBLE);
            TextView configTitle = overlayView.findViewById(R.id.config_title);
            if (configTitle != null) configTitle.setText(R.string.pack_changer_title);
            if (filterBar != null) filterBar.setVisibility(View.GONE);
            if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
        }
    }

    private void hidePacks() {
        if (packsContainer != null) packsContainer.setVisibility(View.GONE);
    }
    private void showConfigSection(UnifiedMod mod) {
        if (mod.openCustomConfig()) {
            hide();
            return;
        }
        updateNavigationItems(false, false, false, false, false);

        modulesContainer.setVisibility(View.GONE);
        settingsContainer.setVisibility(View.GONE);
        hideCosmetics();
        hidePacks();

        if (overlayView != null) {
            View modConfigContainer = overlayView.findViewById(R.id.mod_config_container);
            View searchContainer = overlayView.findViewById(R.id.search_container);
            View configHeader = overlayView.findViewById(R.id.config_header);
            ViewGroup modConfigContent = overlayView.findViewById(R.id.mod_config_content);
            TextView configTitle = overlayView.findViewById(R.id.config_title);

            if (modConfigContainer != null) {
                modConfigContainer.setVisibility(View.VISIBLE);
                crossfade(modConfigContainer);
            }
            if (searchContainer != null) searchContainer.setVisibility(View.GONE);
            if (configHeader != null) configHeader.setVisibility(View.VISIBLE);
            if (filterBar != null) filterBar.setVisibility(View.GONE);
            if (compactFilterBar != null) compactFilterBar.setVisibility(View.GONE);
            if (configTitle != null) configTitle.setText(mod.getName());

            if (modConfigContent != null) {
                ModConfigView.render(activity, modConfigContent, mod, compactMode, () -> {
                    InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
                    if (overlayManager != null) {
                        overlayManager.applyConfigurationChanges(mod.getId());
                    }
                });
            }
        }
    }

    private void setupHudEditorDragHandle(View handle, View tools) {
        final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private int pointerId = -1;
            private boolean dragging;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (overlayView == null || tools == null || tools.getVisibility() != View.VISIBLE) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pointerId = event.getPointerId(0);
                        downX = event.getRawX();
                        downY = event.getRawY();
                        dragging = false;
                        if (wmParams != null) {
                            startX = wmParams.x;
                            startY = wmParams.y;
                        } else {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
                            startX = params.leftMargin;
                            startY = params.topMargin;
                        }
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (pointerId < 0 || event.getPointerId(0) != pointerId) return true;
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) dragging = true;
                        if (dragging) moveHudEditorTools(startX + Math.round(dx), startY + Math.round(dy));
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        if (event.getPointerId(event.getActionIndex()) != pointerId) return true;
                        pointerId = -1;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (pointerId >= 0 && !dragging) view.performClick();
                        // Fall through to release the gesture on both up and cancellation.
                    case MotionEvent.ACTION_CANCEL:
                        pointerId = -1;
                        dragging = false;
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void moveHudEditorTools(int x, int y) {
        if (overlayView == null) return;
        OverlayBounds.Position position = OverlayBounds.clampPosition(activity, overlayView, x, y);
        if (wmParams != null && windowManager != null) {
            wmParams.x = position.x;
            wmParams.y = position.y;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.leftMargin = position.x;
            params.topMargin = position.y;
            overlayView.setLayoutParams(params);
        }
    }

    private void enterHudEditorMode(View modMenuContainer, View hudEditorTools) {
        updateNavigationItems(false, false, true, false, false);

        if (modMenuContainer != null) {
            modMenuContainer.setVisibility(View.GONE);
        }
        if (hudEditorTools != null) {
            hudEditorTools.setVisibility(View.VISIBLE);
            hudEditorTools.setAlpha(1f);
            hudEditorTools.setTranslationX(0f);
        }
        if (overlayView != null) {
            overlayView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            overlayView.setClickable(false);
        }
        if (wmParams != null && windowManager != null) {
            wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            wmParams.gravity = Gravity.TOP | Gravity.LEFT;
            wmParams.x = 0;
            wmParams.y = 0;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView != null && overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = 0;
            params.topMargin = 0;
            overlayView.setLayoutParams(params);
        }
        // Wait for the compact tool window to be measured before centering it.
        final View editorRoot = overlayView;
        if (editorRoot != null) editorRoot.post(() -> {
            if (overlayView != editorRoot || hudEditorTools == null || hudEditorTools.getVisibility() != View.VISIBLE) return;
            OverlayBounds.Position rightEdge = OverlayBounds.clampPosition(
                    activity, editorRoot, Integer.MAX_VALUE, 0);
            moveHudEditorTools(rightEdge.x / 2, 0);
        });
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.setHudEditorSelectionListener(this::updateHudEditorSizeControls);
            overlayManager.setHudEditorMode(true);
        }
    }

    private void exitHudEditorMode(View modMenuContainer, View hudEditorTools) {
        if (hudEditorTools != null) {
            hudEditorTools.setVisibility(View.GONE);
        }
        if (overlayView != null) {
            overlayView.setClickable(true);
        }
        if (wmParams != null && windowManager != null) {
            wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            wmParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            wmParams.gravity = Gravity.CENTER;
            wmParams.x = 0;
            wmParams.y = 0;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView != null && overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;
            params.leftMargin = 0;
            params.topMargin = 0;
            overlayView.setLayoutParams(params);
        }
        if (modMenuContainer != null) {
            modMenuContainer.setVisibility(View.VISIBLE);
            animateMenuEnter(modMenuContainer);
        }
        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.setHudEditorMode(false);
            overlayManager.setHudEditorSelectionListener(null);
        }
        showModulesSection();
    }

    private void updateHudEditorSizeControls(int sizeDp) {
        if (hudButtonSizeSeekBar == null) return;
        boolean hasSelection = sizeDp > 0;
        hudButtonSizeSeekBar.setEnabled(hasSelection);
        if (!hasSelection) {
            updateHudButtonSizeText(0);
            return;
        }
        updatingHudButtonSize = true;
        hudButtonSizeSeekBar.setProgress(sizeDp);
        updatingHudButtonSize = false;
        updateHudButtonSizeText(sizeDp);
    }

    private void updateHudButtonSizeText(int size) {
        if (hudButtonSizeText == null) return;
        if (size <= 0) {
            hudButtonSizeText.setText(activity.getString(R.string.overlay_button_size));
        } else {
            hudButtonSizeText.setText(activity.getString(R.string.overlay_button_size_value, size));
        }
    }

    private void setupCompactFilter() {
        if (compactFilterSelector == null) return;
        compactFilterSelector.setOnClickListener(this::showCompactFilterMenu);
        updateCompactFilterSelector();
    }

    private void showCompactFilterMenu(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add(0, 100, 0, R.string.filter_all);
        popup.getMenu().add(0, 101, 1, R.string.mod_menu_favorites);
        popup.getMenu().add(0, 102, 2, R.string.mod_menu_filter_enabled);
        popup.getMenu().add(0, 103, 3, R.string.mod_menu_filter_inbuilt);
        popup.getMenu().add(0, 104, 4, R.string.mod_menu_filter_external);
        popup.getMenu().add(0, 105, 5, R.string.mod_menu_filter_pvp);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 101:
                    setModuleFilter(ModuleFilter.FAVORITES);
                    break;
                case 102:
                    setModuleFilter(ModuleFilter.ENABLED);
                    break;
                case 103:
                    setModuleFilter(ModuleFilter.INBUILT);
                    break;
                case 104:
                    setModuleFilter(ModuleFilter.EXTERNAL);
                    break;
                case 105:
                    setModuleFilter(ModuleFilter.PVP);
                    break;
                case 100:
                default:
                    setModuleFilter(ModuleFilter.ALL);
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void setCompactMode(boolean compact) {
        compactMode = compact;
        if (modsLayoutManager != null) {
            modsLayoutManager.setSpanCount(compact ? 1 : 4);
        }
        if (adapter != null) {
            adapter.setCompactMode(compact);
            adapter.updateMods(filteredMods, favoriteKeys);
        }
        applyCompactModeLayout(compact);
        updateFilterBarVisibility();
        updateFilterButtons();
        updateModuleCount();
    }

    private void applyCompactModeLayout(boolean compact) {
        if (menuContainer == null) return;
        ViewGroup.LayoutParams rawParams = menuContainer.getLayoutParams();
        if (rawParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
            if (compact) {
                int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                int availableWidth = Math.max(1, screenWidth - dp(36));
                int desiredWidth = Math.min(dp(392), Math.round(screenWidth * 0.40f));
                int minimumWidth = Math.min(dp(312), availableWidth);
                params.width = Math.min(availableWidth, Math.max(minimumWidth, desiredWidth));
                int availableHeight = Math.max(1, screenHeight - dp(24));
                params.height = Math.min(dp(560), availableHeight);
                params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                params.setMargins(dp(18), dp(12), 0, dp(12));
                params.setMarginStart(dp(18));
                params.setMarginEnd(0);
            } else {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.CENTER;
                params.setMargins(dp(32), dp(20), dp(32), dp(20));
                params.setMarginStart(dp(32));
                params.setMarginEnd(dp(32));
            }
            menuContainer.setLayoutParams(params);
        }

        // The top bar keeps every destination reachable in both modes. Compact mode only
        // narrows the window; it must never hide the navigation, which is what the old
        // sidebar/compact-icon swap did once the compact icons were removed.
        boolean modulesSelected = modulesContainer != null && modulesContainer.getVisibility() == View.VISIBLE;
        boolean settingsSelected = settingsContainer != null && settingsContainer.getVisibility() == View.VISIBLE;
        boolean cosmeticsSelected = cosmeticsContainer != null && cosmeticsContainer.getVisibility() == View.VISIBLE;
        boolean packsSelected = packsContainer != null && packsContainer.getVisibility() == View.VISIBLE;
        updateNavigationItems(modulesSelected, cosmeticsSelected, false, settingsSelected, packsSelected);

        if (modsRecycler != null) {
            int padding = dp(compact ? 4 : 14);
            modsRecycler.setPadding(padding, padding, padding, padding);
        }
        menuContainer.requestLayout();
    }

    private void updateFilterBarVisibility() {
        boolean modulesVisible = modulesContainer != null && modulesContainer.getVisibility() == View.VISIBLE;
        if (filterBar != null) {
            filterBar.setVisibility(modulesVisible && !compactMode ? View.VISIBLE : View.GONE);
        }
        if (compactFilterBar != null) {
            compactFilterBar.setVisibility(modulesVisible && compactMode ? View.VISIBLE : View.GONE);
        }
    }

    private void updateCompactFilterSelector() {
        if (compactFilterSelector == null) return;
        compactFilterSelector.setText(getFilterLabelRes(activeFilter));
        compactFilterSelector.setTextColor(getAccentColor());
    }

    private int getFilterLabelRes(ModuleFilter filter) {
        switch (filter) {
            case FAVORITES:
                return R.string.mod_menu_favorites;
            case ENABLED:
                return R.string.mod_menu_filter_enabled;
            case INBUILT:
                return R.string.mod_menu_filter_inbuilt;
            case EXTERNAL:
                return R.string.mod_menu_filter_external;
            case PVP:
                return R.string.mod_menu_filter_pvp;
            case ALL:
            default:
                return R.string.filter_all;
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void setupFilterButtons() {
        if (filterAll != null) {
            filterAll.setOnClickListener(v -> setModuleFilter(ModuleFilter.ALL));
        }
        if (filterFavorites != null) {
            filterFavorites.setOnClickListener(v -> setModuleFilter(ModuleFilter.FAVORITES));
        }
        if (filterEnabled != null) {
            filterEnabled.setOnClickListener(v -> setModuleFilter(ModuleFilter.ENABLED));
        }
        if (filterInbuilt != null) {
            filterInbuilt.setOnClickListener(v -> setModuleFilter(ModuleFilter.INBUILT));
        }
        if (filterExternal != null) {
            filterExternal.setOnClickListener(v -> setModuleFilter(ModuleFilter.EXTERNAL));
        }
        if (filterPvp != null) {
            filterPvp.setOnClickListener(v -> setModuleFilter(ModuleFilter.PVP));
        }
        updateFilterButtons();
    }

    private void setModuleFilter(ModuleFilter filter) {
        activeFilter = filter;
        updateFilterButtons();
        applyFilters();
    }

    private void applyFilters() {
        filteredMods.clear();
        String query = searchInput != null
            ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT)
            : "";

        Map<String, GroupedMods> groupedMatches = new LinkedHashMap<>();
        for (UnifiedMod mod : allMods) {
            if (matchesActiveFilter(mod) && matchesQuery(mod, query)) {
                GroupedMods group = groupedMatches.computeIfAbsent(
                    mod.getGroupId(), ignored -> new GroupedMods());
                group.add(mod, isFavorite(mod));
            }
        }
        for (GroupedMods group : groupedMatches.values()) {
            group.appendTo(filteredMods);
        }

        if (adapter != null) {
            adapter.updateMods(filteredMods, favoriteKeys);
            if (!hasStaggeredOnce) {
                hasStaggeredOnce = true;
                DynamicAnim.staggerRecyclerChildren(modsRecycler);
            }
        }
        updateEmptyState();
        updateModuleCount();
    }

    private boolean matchesActiveFilter(UnifiedMod mod) {
        switch (activeFilter) {
            case FAVORITES:
                return isFavorite(mod);
            case ENABLED:
                return mod.isEnabled();
            case INBUILT:
                return mod.getSource() == UnifiedMod.Source.INBUILT;
            case EXTERNAL:
                return mod.getSource() == UnifiedMod.Source.EXTERNAL;
            case PVP:
                return ModIds.isPvpModule(mod.getId());
            case ALL:
            default:
                return true;
        }
    }

    private boolean matchesQuery(UnifiedMod mod, String query) {
        if (query.isEmpty()) return true;
        String searchText = (
            safeString(mod.getName()) + " " +
            safeString(mod.getDescription()) + " " +
            safeString(mod.getId()) + " " +
            safeString(mod.getModId()) + " " +
            safeString(mod.getGroupName()) + " " +
            safeString(mod.getGroupId())
        ).toLowerCase(Locale.ROOT);
        return searchText.contains(query);
    }

    private boolean isFavorite(UnifiedMod mod) {
        return favoriteKeys.contains(mod.getStableKey());
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private void updateFilterButtons() {
        updateFilterButton(filterAll, activeFilter == ModuleFilter.ALL);
        updateFilterButton(filterFavorites, activeFilter == ModuleFilter.FAVORITES);
        updateFilterButton(filterEnabled, activeFilter == ModuleFilter.ENABLED);
        updateFilterButton(filterInbuilt, activeFilter == ModuleFilter.INBUILT);
        updateFilterButton(filterExternal, activeFilter == ModuleFilter.EXTERNAL);
        updateFilterButton(filterPvp, activeFilter == ModuleFilter.PVP);
        updateCompactFilterSelector();
    }

    private void updateFilterButton(TextView view, boolean selected) {
        if (view == null) return;
        view.setTextColor(selected ? getAccentColor() : 0xFFA8B0B8);
        view.setAlpha(1f);
        Drawable background = view.getBackground();
        if (background != null) {
            background.mutate().setTint(selected ? theme.accentFill(51) : 0xFF24282C);
        }
    }

    /**
     * Re-tints one top-bar entry for the selected/unselected state.
     *
     * Each entry is a single {@code TextView} carrying its icon as a compound drawable, so
     * tinting the compound drawable is what colours the icon — there is no separate
     * {@code ImageButton} to keep in step.
     */
    private void updateNavigationItem(TextView view, boolean selected) {
        if (view == null) return;
        int color = selected ? getAccentColor() : 0xFFA8B0B8;
        view.setTextColor(color);
        view.setAlpha(selected ? 1f : 0.82f);
        view.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        view.setTypeface(view.getTypeface(), selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void updateNavigationItems(boolean modules, boolean cosmetics, boolean hudEditor,
                                       boolean settings) {
        updateNavigationItems(modules, cosmetics, hudEditor, settings, false);
    }

    private void updateNavigationItems(boolean modules, boolean cosmetics, boolean hudEditor,
                                       boolean settings, boolean packs) {
        updateNavigationItem(navModules, modules);
        updateNavigationItem(navCosmetics, cosmetics);
        updateNavigationItem(navHudEditor, hudEditor);
        updateNavigationItem(navSettings, settings);
        updateNavigationItem(navPacks, packs);
    }

    private void updateModuleCount() {
        String value = activity.getString(
            R.string.mod_menu_module_count,
            filteredMods.size(),
            allMods.size());
        if (moduleCountText != null) {
            moduleCountText.setText(value);
        }
        if (compactModuleCount != null) {
            compactModuleCount.setText(value);
        }
    }

    private void loadMods() {
        allMods.clear();

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        favoriteKeys.clear();
        favoriteKeys.addAll(manager.getFavoriteModKeys());
        allMods.addAll(InbuiltModuleProvider.load(activity));
        allMods.addAll(ExternalModuleProvider.load(activity));

        applyFilters();
    }

    private void filterMods(String query) {
        applyFilters();
    }

    private void updateEmptyState() {
        if (emptyState != null) {
            emptyState.setVisibility(filteredMods.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (emptyStateText != null) {
            String query = searchInput != null ? searchInput.getText().toString().trim() : "";
            if (!query.isEmpty()) {
                emptyStateText.setText(R.string.mod_menu_no_matches);
            } else if (activeFilter == ModuleFilter.FAVORITES) {
                emptyStateText.setText(R.string.mod_menu_no_favorites);
            } else if (activeFilter == ModuleFilter.PVP) {
                emptyStateText.setText(R.string.mod_menu_pvp_empty);
            } else {
                emptyStateText.setText(R.string.mod_menu_no_mods);
            }
        }
    }

    public void refreshMods() {
        loadMods();
    }

    private void applyMenuOpacity() {
        if (overlayView != null) {
            View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
            if (menuContainer != null) {
                int opacity = InbuiltModManager.getInstance(activity).getModMenuOpacity();
                menuContainer.setAlpha(opacity / 100f);
            }
        }
    }

    public void hide() {
        if (!isShowing || overlayView == null) return;

        InbuiltOverlayManager overlayManager = InbuiltOverlayManager.getInstance();
        if (overlayManager != null) {
            overlayManager.setHudEditorMode(false);
            overlayManager.setHudEditorSelectionListener(null);
        }

        Runnable performHide = () -> {
            handler.post(() -> {
                try {
                    if (wmParams != null && windowManager != null) {
                        windowManager.removeView(overlayView);
                    } else {
                        ViewGroup rootView = activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            rootView.removeView(overlayView);
                        }
                    }
                } catch (Exception ignored) {}
                overlayView = null;
                isShowing = false;
                hasStaggeredOnce = false;
            });
        };

        View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        if (menuContainer != null) {
            animateMenuExit(menuContainer, performHide);
        } else {
            performHide.run();
        }
        overlayView.animate().alpha(0f).setDuration(180).start();
    }

    public boolean isShowing() {
        return isShowing;
    }

    private static class GroupedMods {
        private final List<UnifiedMod> favorites = new ArrayList<>();
        private final List<UnifiedMod> others = new ArrayList<>();

        void add(UnifiedMod mod, boolean favorite) {
            if (favorite) {
                favorites.add(mod);
            } else {
                others.add(mod);
            }
        }

        void appendTo(List<UnifiedMod> target) {
            sort(favorites);
            sort(others);
            target.addAll(favorites);
            target.addAll(others);
        }

        private void sort(List<UnifiedMod> mods) {
            mods.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        }
    }
}
