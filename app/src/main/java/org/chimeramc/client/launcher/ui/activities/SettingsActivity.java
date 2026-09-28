package org.chimeramc.client.ui.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.chimeramc.client.R;
import org.chimeramc.client.core.crash.CrashReporter;
import org.chimeramc.client.preloader.PreloaderSignatureRulesManager;
import org.chimeramc.client.settings.FeatureSettings;
import org.chimeramc.client.settings.LowLatencyNetworkManager;
import org.chimeramc.client.settings.PerformancePresetManager;
import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.ui.dialogs.LogcatOverlayManager;
import org.chimeramc.client.util.GithubReleaseUpdater;
import org.chimeramc.client.util.LanguageManager;
import org.chimeramc.client.util.LauncherFonts;
import org.chimeramc.client.util.LauncherSettingsBackup;
import org.chimeramc.client.util.LauncherStorage;
import org.chimeramc.client.util.PermissionsHandler;
import org.chimeramc.client.util.PersonalizationManager;
import org.chimeramc.client.util.ThemeManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private PermissionsHandler permissionsHandler;
    private ActivityResultLauncher<Intent> permissionResultLauncher;
    private ActivityResultLauncher<Intent> bgImagePickerLauncher;
    private ActivityResultLauncher<String> settingsExportLauncher;
    private ActivityResultLauncher<String[]> settingsImportLauncher;
    private int updateButtonTapCount = 0;
    private long lastUpdateButtonTapTime = 0;
    private static final int EASTER_EGG_TAP_COUNT = 3;
    private static final long TAP_TIMEOUT_MS = 2000;

    private TextView tabBasic;
    private TextView tabPersonalize;
    private TextView tabUpdates;
    private TextView tabMigration;
    private TextView tabAbout;

    private View sectionBasic;
    private View sectionPersonalize;
    private View sectionUpdates;
    private View sectionMigration;
    private View sectionAbout;

    private static final String KEY_SELECTED_TAB = "selected_tab_index";
    private int selectedTabIndex = 0;

    private PersonalizationManager personalizationManager;
    private LinearLayout colorGridContainer;
    private LinearLayout moreColorsContainer;
    private TextView bgImageStatus;
    private TextView bgImageBlurValue;
    private TextView bgImageBrightnessValue;
    private ImageView bgImagePreview;
    private TextView migrationCleanupStatus;
    private Button migrationCleanupButton;
    private SwitchMaterial switchSharedStorageLayout;
    private TextView sharedStorageLayoutStatus;
    private TextView preloaderSigsLastUpdateText;
    private Button preloaderSigsUpdateButton;
    
    // UI Customization fields
    private TextView animationSpeedValue;
    private TextView uiTransparencyValue;
    private TextView cardRoundingValue;
    private TextView iconSizeValue;
    private TextView fontScaleValue;
    private TextView blurIntensityValue;
    private SwitchMaterial switchShowAnimations;
    private SwitchMaterial switchEnableGlow;
    private SwitchMaterial switchCompactMode;
    private Button btnResetCustomizations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        DynamicAnim.applyPressScaleRecursively(findViewById(android.R.id.content));

        setupNavBar();

        personalizationManager = new PersonalizationManager(this);

        if (savedInstanceState != null) {
            selectedTabIndex = savedInstanceState.getInt(KEY_SELECTED_TAB, 0);
        }

        permissionsHandler = PermissionsHandler.getInstance();
        permissionResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (permissionsHandler != null) {
                        permissionsHandler.onActivityResult(result.getResultCode(), result.getData());
                    }
                }
        );
        permissionsHandler.setActivity(this, permissionResultLauncher);

        bgImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            personalizationManager.setBackgroundImage(uri, this);
                            updateBgImageUI();
                            recreate();
                        }
                    }
                }
        );

        settingsExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) exportSettingsBackup(uri);
                }
        );

        settingsImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) importSettingsBackup(uri);
                }
        );

        initTabs();
        setupBasicSection();
        setupPersonalizeSection();
        setupUICustomizationSection();
        setupUpdatesSection();
        setupMigrationSection();
        setupAboutSection();

        TextView[] tabs = getSettingsTabs();
        if (selectedTabIndex >= tabs.length) {
            selectedTabIndex = 0;
        }
        selectTab(tabs[selectedTabIndex]);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, selectedTabIndex);
    }

    private void initTabs() {
        tabBasic = findViewById(R.id.tab_basic);
        tabPersonalize = findViewById(R.id.tab_personalize);
        tabUpdates = findViewById(R.id.tab_updates);
        tabMigration = findViewById(R.id.tab_migration);
        tabAbout = findViewById(R.id.tab_about);

        sectionBasic = findViewById(R.id.section_basic);
        sectionPersonalize = findViewById(R.id.section_personalize);
        sectionUpdates = findViewById(R.id.section_updates);
        sectionMigration = findViewById(R.id.section_migration);
        sectionAbout = findViewById(R.id.section_about);

        tabBasic.setOnClickListener(v -> { selectedTabIndex = 0; selectTab(tabBasic); });
        tabPersonalize.setOnClickListener(v -> { selectedTabIndex = 1; selectTab(tabPersonalize); });
        tabUpdates.setOnClickListener(v -> { selectedTabIndex = 2; selectTab(tabUpdates); });
        tabAbout.setOnClickListener(v -> { selectedTabIndex = 3; selectTab(tabAbout); });
        tabMigration.setOnClickListener(v -> { selectedTabIndex = 4; selectTab(tabMigration); });
    }

    private void selectTab(TextView selectedTab) {
        TextView[] tabs = getSettingsTabs();
        View[] sections = {sectionBasic, sectionPersonalize, sectionUpdates, sectionAbout, sectionMigration};

        int accent = personalizationManager.getAccentColor();

        for (int i = 0; i < tabs.length; i++) {
            boolean isSelected = tabs[i] == selectedTab;

            if (isSelected) {
                if (accent != 0) {
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    gd.setColor(accent);
                    gd.setCornerRadius(16 * getResources().getDisplayMetrics().density);
                    tabs[i].setBackground(gd);
                } else {
                    tabs[i].setBackgroundResource(R.drawable.bg_tab_selected);
                }
                tabs[i].setTextColor(Color.WHITE);
                tabs[i].setTextSize(13);
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_tab_unselected);
                tabs[i].setTextColor(getColor(R.color.text_secondary));
            }

            if (isSelected) {
                sections[i].setVisibility(View.VISIBLE);
                sections[i].setAlpha(0f);
                sections[i].animate().alpha(1f).setDuration(200).start();
            } else {
                sections[i].setVisibility(View.GONE);
            }
        }
    }

    private TextView[] getSettingsTabs() {
        return new TextView[]{tabBasic, tabPersonalize, tabUpdates, tabAbout, tabMigration};
    }

    private void setupBasicSection() {
        LanguageManager languageManager = new LanguageManager(this);
        FeatureSettings fs = FeatureSettings.getInstance();
        setupPerformancePreset();

        // Keep English at the top (default language).
        // Sort all other languages alphabetically by their display name.
        String[] languageOptions = {
                getString(R.string.english),
                getString(R.string.chinese),
                getString(R.string.french),
                getString(R.string.hindi),
                getString(R.string.indonesian),
                getString(R.string.japanese),
                getString(R.string.portuguese),
                getString(R.string.russian),
                getString(R.string.spanish),
                getString(R.string.turkish),
                getString(R.string.vietnamese)
        };

        String currentCode = languageManager.getCurrentLanguage();
        int defaultIdx = switch (currentCode) {
            case "zh", "zh-CN" -> 1;
            case "fr" -> 2;
            case "hi" -> 3;
            case "idn" -> 4;
            case "ja" -> 5;
            case "pt" -> 6;
            case "ru" -> 7;
            case "es" -> 8;
            case "tr", "tr-TR" -> 9;
            case "vi" -> 10;
            default -> 0;
        };

        TextView languageCurrent = findViewById(R.id.language_current);
        languageCurrent.setText(languageOptions[defaultIdx]);

        Spinner languageSpinner = findViewById(R.id.language_spinner);
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, languageOptions);
        langAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        languageSpinner.setAdapter(langAdapter);
        languageSpinner.setPopupBackgroundResource(R.drawable.bg_popup_menu_rounded);
        languageSpinner.setSelection(defaultIdx);
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String code = switch (position) {
                    case 1 -> "zh-CN";
                    case 2 -> "fr";
                    case 3 -> "hi";
                    case 4 -> "idn";
                    case 5 -> "ja";
                    case 6 -> "pt";
                    case 7 -> "ru";
                    case 8 -> "es";
                    case 9 -> "tr";
                    case 10 -> "vi";
                    default -> "en";
                };
                if (!code.equals(languageManager.getCurrentLanguage())) {
                    languageManager.setAppLanguage(code);
                }
                languageCurrent.setText(languageOptions[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Font Family dropdown — each row is rendered in its own face so the choice is obvious
        // before applying, and the preview line below shows a full sentence in the selection.
        Spinner fontSpinner = findViewById(R.id.font_family_spinner);
        final TextView fontPreview = findViewById(R.id.font_family_preview);
        if (fontSpinner != null) {
            final List<LauncherFonts.Entry> fontEntries = LauncherFonts.entries();
            ArrayAdapter<LauncherFonts.Entry> fontAdapter =
                    new ArrayAdapter<LauncherFonts.Entry>(this, R.layout.spinner_font_item, fontEntries) {
                        @Override
                        public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            View v = super.getView(position, convertView, parent);
                            bindFontRow(v, fontEntries.get(position));
                            return v;
                        }

                        @Override
                        public View getDropDownView(int position, View convertView,
                                                    android.view.ViewGroup parent) {
                            View v = super.getDropDownView(position, convertView, parent);
                            bindFontRow(v, fontEntries.get(position));
                            return v;
                        }
                    };
            fontAdapter.setDropDownViewResource(R.layout.spinner_font_item);
            fontSpinner.setAdapter(fontAdapter);
            fontSpinner.setPopupBackgroundResource(R.drawable.bg_popup_menu_rounded);
            fontSpinner.setSelection(LauncherFonts.indexOf(personalizationManager.getFontFamily()));
            updateFontPreview(fontPreview, personalizationManager.getFontFamily());
            fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                private boolean first = true;

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (first) {
                        // The Spinner fires once on layout; applying then would "change" the font
                        // to the value it already holds and trigger a needless recreate.
                        first = false;
                        return;
                    }
                    LauncherFonts.Entry entry = fontEntries.get(position);
                    personalizationManager.setFontFamily(entry.key);
                    updateFontPreview(fontPreview, entry.key);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        SwitchMaterial switchLogcat = findViewById(R.id.switch_logcat);
        switchLogcat.setChecked(fs.isLogcatOverlayEnabled());
        switchLogcat.setOnCheckedChangeListener((btn, checked) -> {
            fs.setLogcatOverlayEnabled(checked);
            try {
                LogcatOverlayManager mgr = LogcatOverlayManager.getInstance();
                if (mgr != null) mgr.refreshVisibility();
            } catch (Throwable ignored) {}
        });

        SwitchMaterial switchCrashUpload = findViewById(R.id.switch_crash_upload);
        switchCrashUpload.setChecked(fs.isCrashUploadEnabled());
        switchCrashUpload.setOnCheckedChangeListener((btn, checked) -> {
            fs.setCrashUploadEnabled(checked);
            CrashReporter.refreshCrashlyticsCollection(this);
        });

        SwitchMaterial switchManagedLogin = findViewById(R.id.switch_managed_login);
        switchManagedLogin.setChecked(fs.isLauncherManagedMcLoginEnabled());
        switchManagedLogin.setOnCheckedChangeListener((btn, checked) -> fs.setLauncherManagedMcLoginEnabled(checked));

        SwitchMaterial switchLowInputDelay = findViewById(R.id.switch_low_input_delay);
        switchLowInputDelay.setChecked(fs.isLowInputDelayEnabled());
        switchLowInputDelay.setOnCheckedChangeListener((btn, checked) -> {
            fs.setLowInputDelayEnabled(checked);
            org.chimeramc.pojavcontrols.PojavControls.setLowLatencyMode(checked);
            // The controller response curve and dead zone are derived from this flag, so the
            // loaded profile must be rebuilt for the change to apply without a restart.
            org.chimeramc.client.launcher.controller.ControllerInputProcessor.reload(this);
        });

        SwitchMaterial switchGxCore = findViewById(R.id.switch_gxcore);
        switchGxCore.setChecked(fs.isGxCoreEnabled());
        switchGxCore.setOnCheckedChangeListener((btn, checked) -> fs.setGxCoreEnabled(checked));

        SwitchMaterial switchReduceNetworkLatency = findViewById(R.id.switch_reduce_network_latency);
        switchReduceNetworkLatency.setChecked(fs.isReduceNetworkLatencyEnabled());
        switchReduceNetworkLatency.setOnCheckedChangeListener((btn, checked) -> {
            fs.setReduceNetworkLatencyEnabled(checked);
            if (checked) {
                LowLatencyNetworkManager.prefetchDnsOnBackground();
            }
        });

        setupCurseForgeKeyRow();
    }

    /**
     * Wires the Battery / Balanced / Performance choice.
     *
     * The preset writes through the individual feature settings, so this method also has to
     * re-read the dependent switches afterwards: otherwise flipping to Performance would
     * change the stored value but leave the "Reduce Network Latency" switch above showing the
     * old state, which reads as the preset having done nothing.
     */
    private void setupPerformancePreset() {
        MaterialButtonToggleGroup group = findViewById(R.id.performance_preset_group);
        TextView description = findViewById(R.id.performance_preset_description);
        if (group == null) return;

        PerformancePresetManager.Preset current = PerformancePresetManager.current(this);
        group.check(buttonIdFor(current));
        if (description != null) {
            description.setText(PerformancePresetManager.describe(current));
        }
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) return;
            PerformancePresetManager.Preset preset = presetFor(checkedId);
            PerformancePresetManager.apply(this, preset);
            if (description != null) {
                description.setText(PerformancePresetManager.describe(preset));
            }
            syncPerformanceDependentSwitches();
        });
    }

    /** Mirrors the preset's settings back into the individual switches on the same screen. */
    private void syncPerformanceDependentSwitches() {
        FeatureSettings fs = FeatureSettings.getInstance();
        SwitchMaterial lowInputDelay = findViewById(R.id.switch_low_input_delay);
        if (lowInputDelay != null) {
            lowInputDelay.setChecked(fs.isLowInputDelayEnabled());
        }
        SwitchMaterial reduceLatency = findViewById(R.id.switch_reduce_network_latency);
        if (reduceLatency != null) {
            reduceLatency.setChecked(fs.isReduceNetworkLatencyEnabled());
        }
        org.chimeramc.client.launcher.controller.ControllerInputProcessor.reload(this);
    }

    private static int buttonIdFor(PerformancePresetManager.Preset preset) {
        switch (preset) {
            case BATTERY:
                return R.id.performance_preset_battery;
            case PERFORMANCE:
                return R.id.performance_preset_performance;
            case BALANCED:
            default:
                return R.id.performance_preset_balanced;
        }
    }

    private static PerformancePresetManager.Preset presetFor(int buttonId) {
        if (buttonId == R.id.performance_preset_battery) return PerformancePresetManager.Preset.BATTERY;
        if (buttonId == R.id.performance_preset_performance) return PerformancePresetManager.Preset.PERFORMANCE;
        return PerformancePresetManager.Preset.BALANCED;
    }

    private void setupCurseForgeKeyRow() {
        TextView status = findViewById(R.id.curseforge_api_key_status);
        com.google.android.material.button.MaterialButton button = findViewById(R.id.btn_curseforge_api_key);
        if (status == null || button == null) return;
        refreshCurseForgeKeyRow(status, button);


        View.OnClickListener open = v -> org.chimeramc.client.core.curseforge.CurseForgeKeyDialog.show(
                this, () -> refreshCurseForgeKeyRow(status, button));
        button.setOnClickListener(open);
        status.setOnClickListener(open);
    }

    private void refreshCurseForgeKeyRow(TextView status,
                                         com.google.android.material.button.MaterialButton button) {
        String key = org.chimeramc.client.core.curseforge.CurseForgeKeyStore.getApiKey(this);
        if (key.isEmpty()) {
            status.setText(R.string.curseforge_api_key_not_set);
            button.setText(R.string.curseforge_api_key_set);
        } else {
            status.setText(getString(R.string.curseforge_api_key_configured, maskKey(key)));
            button.setText(R.string.curseforge_api_key_change);
        }
    }

    /** Shows enough of the key to tell two of them apart without exposing it. */
    private static String maskKey(String key) {
        if (key.length() <= 8) return "\u2026";
        return key.substring(0, 4) + "\u2026" + key.substring(key.length() - 4);
    }

    private void setupPersonalizeSection() {
        ThemeManager themeManager = new ThemeManager(this);

        View itemSystem = findViewById(R.id.theme_item_system);
        View itemLight = findViewById(R.id.theme_item_light);
        View itemDark = findViewById(R.id.theme_item_dark);

        refreshThemeSelectionUI();

        if (itemSystem != null && itemLight != null && itemDark != null) {
            itemSystem.setOnClickListener(v -> { themeManager.setThemeMode(0); });
            itemLight.setOnClickListener(v -> { themeManager.setThemeMode(1); });
            itemDark.setOnClickListener(v -> { themeManager.setThemeMode(2); });
        }

        setupColorPicker();
        setupBackgroundImagePicker();
    }

    private void setupUICustomizationSection() {
        // Initialize UI references
        animationSpeedValue = findViewById(R.id.animation_speed_value);
        uiTransparencyValue = findViewById(R.id.ui_transparency_value);
        cardRoundingValue = findViewById(R.id.card_rounding_value);
        iconSizeValue = findViewById(R.id.icon_size_value);
        fontScaleValue = findViewById(R.id.font_scale_value);
        blurIntensityValue = findViewById(R.id.blur_intensity_value);
        switchShowAnimations = findViewById(R.id.switch_show_animations);
        switchEnableGlow = findViewById(R.id.switch_enable_glow);
        switchCompactMode = findViewById(R.id.switch_compact_mode);
        btnResetCustomizations = findViewById(R.id.btn_reset_customizations);
        // Animation Speed SeekBar
        SeekBar seekAnimationSpeed = findViewById(R.id.seek_animation_speed);
        if (seekAnimationSpeed != null) {
            float currentSpeed = personalizationManager.getAnimationSpeed();
            int progress = (int) ((currentSpeed - PersonalizationManager.ANIMATION_SPEED_MIN) * 100);
            seekAnimationSpeed.setProgress(progress);
            updateAnimationSpeedValue(currentSpeed);
            
            seekAnimationSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    float speed = PersonalizationManager.ANIMATION_SPEED_MIN + (progress / 100f);
                    personalizationManager.setAnimationSpeed(speed);
                    updateAnimationSpeedValue(speed);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // UI Transparency SeekBar
        SeekBar seekUiTransparency = findViewById(R.id.seek_ui_transparency);
        if (seekUiTransparency != null) {
            int currentTransparency = personalizationManager.getUiTransparency();
            seekUiTransparency.setProgress(currentTransparency);
            updateUiTransparencyValue(currentTransparency);
            
            seekUiTransparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    personalizationManager.setUiTransparency(progress);
                    updateUiTransparencyValue(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Card Rounding SeekBar
        SeekBar seekCardRounding = findViewById(R.id.seek_card_rounding);
        if (seekCardRounding != null) {
            int currentRounding = personalizationManager.getCardRounding();
            seekCardRounding.setProgress(currentRounding);
            updateCardRoundingValue(currentRounding);
            
            seekCardRounding.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    personalizationManager.setCardRounding(progress);
                    updateCardRoundingValue(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Icon Size SeekBar
        SeekBar seekIconSize = findViewById(R.id.seek_icon_size);
        if (seekIconSize != null) {
            int currentIconSize = personalizationManager.getIconSize();
            int progress = currentIconSize - PersonalizationManager.ICON_SIZE_MIN;
            seekIconSize.setProgress(progress);
            updateIconSizeValue(currentIconSize);
            
            seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    int size = PersonalizationManager.ICON_SIZE_MIN + progress;
                    personalizationManager.setIconSize(size);
                    updateIconSizeValue(size);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Font Scale SeekBar
        SeekBar seekFontScale = findViewById(R.id.seek_font_scale);
        if (seekFontScale != null) {
            float currentScale = personalizationManager.getFontScale();
            int progress = (int) ((currentScale - PersonalizationManager.FONT_SCALE_MIN) * 100);
            seekFontScale.setProgress(progress);
            updateFontScaleValue(currentScale);
            
            seekFontScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    float scale = PersonalizationManager.FONT_SCALE_MIN + (progress / 100f);
                    personalizationManager.setFontScale(scale);
                    updateFontScaleValue(scale);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Blur Intensity SeekBar
        SeekBar seekBlurIntensity = findViewById(R.id.seek_blur_intensity);
        if (seekBlurIntensity != null) {
            int currentBlur = personalizationManager.getBlurIntensity();
            seekBlurIntensity.setProgress(currentBlur);
            updateBlurIntensityValue(currentBlur);
            
            seekBlurIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    personalizationManager.setBlurIntensity(progress);
                    updateBlurIntensityValue(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Show Animations Toggle
        if (switchShowAnimations != null) {
            switchShowAnimations.setChecked(personalizationManager.isShowAnimations());
            switchShowAnimations.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setShowAnimations(isChecked);
            });
        }

        // Enable Glow Effects Toggle
        if (switchEnableGlow != null) {
            switchEnableGlow.setChecked(personalizationManager.isEnableGlowEffects());
            switchEnableGlow.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setEnableGlowEffects(isChecked);
            });
        }

        // Compact Mode Toggle
        if (switchCompactMode != null) {
            switchCompactMode.setChecked(personalizationManager.isCompactMode());
            switchCompactMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setCompactMode(isChecked);
            });
        }

        // Firework touch effect
        SwitchMaterial switchFirework = findViewById(R.id.switch_firework_touch);
        SwitchMaterial switchFireworkAccent = findViewById(R.id.switch_firework_accent);
        Spinner spinnerFirework = findViewById(R.id.spinner_firework_intensity);
        View rowFireworkIntensity = findViewById(R.id.row_firework_intensity);
        View rowFireworkAccent = findViewById(R.id.row_firework_accent);
        if (switchFirework != null) {
            switchFirework.setChecked(personalizationManager.isFireworkTouchEnabled());
            applyFireworkEnabledState(rowFireworkIntensity, rowFireworkAccent, switchFirework.isChecked());
            switchFirework.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setFireworkTouchEnabled(isChecked);
                applyFireworkEnabledState(rowFireworkIntensity, rowFireworkAccent, isChecked);
                refreshFireworkLayer();
            });
        }
        if (spinnerFirework != null) {
            String[] levels = {
                    getString(R.string.firework_low),
                    getString(R.string.firework_medium),
                    getString(R.string.firework_high),
            };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, levels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFirework.setAdapter(adapter);
            spinnerFirework.setSelection(personalizationManager.getFireworkIntensity());
            spinnerFirework.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    personalizationManager.setFireworkIntensity(position);
                    refreshFireworkLayer();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
        if (switchFireworkAccent != null) {
            switchFireworkAccent.setChecked(personalizationManager.isFireworkFollowAccent());
            switchFireworkAccent.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setFireworkFollowAccent(isChecked);
                refreshFireworkLayer();
            });
        }

        // Haptic Feedback Toggle
        SwitchMaterial switchHaptics = findViewById(R.id.switch_haptic_feedback);
        if (switchHaptics != null) {
            switchHaptics.setChecked(personalizationManager.isHapticFeedbackEnabled());
            switchHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setHapticFeedbackEnabled(isChecked);
                if (isChecked) {
                    org.chimeramc.client.ui.animation.UiTouchFeedback.confirm(this);
                }
            });
        }

        // Dynamic Color Toggle — only meaningful where the platform can produce a palette.
        SwitchMaterial switchDynamicColor = findViewById(R.id.switch_dynamic_color);
        if (switchDynamicColor != null) {
            boolean supported = ThemeManager.isDynamicColorSupported();
            switchDynamicColor.setEnabled(supported);
            switchDynamicColor.setAlpha(supported ? 1f : 0.5f);
            switchDynamicColor.setChecked(supported && personalizationManager.isDynamicColorEnabled());
            switchDynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
                personalizationManager.setDynamicColorEnabled(isChecked);
                recreate();
            });
        }

        // Reset Button
        if (btnResetCustomizations != null) {
            btnResetCustomizations.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.settings_reset)
                    .setMessage(R.string.reset_settings_confirm)
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        personalizationManager.resetAllCustomizations();
                        setupUICustomizationSection();
                        recreate();
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
            });
        }
    }

    private void updateAnimationSpeedValue(float speed) {
        if (animationSpeedValue != null) {
            animationSpeedValue.setText(getString(R.string.animation_speed_value, speed));
        }
    }

    private void updateUiTransparencyValue(int transparency) {
        if (uiTransparencyValue != null) {
            uiTransparencyValue.setText(getString(R.string.ui_transparency_value, transparency));
        }
    }

    private void updateCardRoundingValue(int rounding) {
        if (cardRoundingValue != null) {
            cardRoundingValue.setText(getString(R.string.card_rounding_value, rounding));
        }
    }

    private void updateIconSizeValue(int size) {
        if (iconSizeValue != null) {
            iconSizeValue.setText(getString(R.string.icon_size_value, size));
        }
    }

    private void updateFontScaleValue(float scale) {
        if (fontScaleValue != null) {
            fontScaleValue.setText(getString(R.string.font_scale_value, scale));
        }
    }

    /** Renders a dropdown row's label in that row's own face. */
    private void bindFontRow(View row, LauncherFonts.Entry entry) {
        if (!(row instanceof TextView)) return;
        TextView tv = (TextView) row;
        tv.setText(entry.labelRes);
        tv.setTypeface(LauncherFonts.typeface(this, entry.key));
    }

    private void updateFontPreview(TextView preview, String key) {
        if (preview == null) return;
        preview.setTypeface(LauncherFonts.typeface(this, key));
    }

    private void updateBlurIntensityValue(int intensity) {
        if (blurIntensityValue != null) {
            blurIntensityValue.setText(getString(R.string.blur_intensity_value, intensity));
        }
    }

    private void refreshThemeSelectionUI() {
        ThemeManager themeManager = new ThemeManager(this);
        int currentMode = themeManager.getCurrentMode();

        TextView textSystem = findViewById(R.id.theme_text_system);
        TextView textLight = findViewById(R.id.theme_text_light);
        TextView textDark = findViewById(R.id.theme_text_dark);

        ImageView iconSystem = findViewById(R.id.theme_icon_system);
        ImageView iconLight = findViewById(R.id.theme_icon_light);
        ImageView iconDark = findViewById(R.id.theme_icon_dark);

        int accent = personalizationManager.getAccentColor();
        int selectedColor = accent != 0 ? accent : getColor(R.color.on_surface);
        int unselectedColor = getColor(R.color.text_secondary);

        if (textSystem != null) {
            textSystem.setTypeface(null, currentMode == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            textSystem.setTextColor(currentMode == 0 ? selectedColor : getColor(R.color.on_surface));
        }
        if (textLight != null) {
            textLight.setTypeface(null, currentMode == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            textLight.setTextColor(currentMode == 1 ? selectedColor : getColor(R.color.on_surface));
        }
        if (textDark != null) {
            textDark.setTypeface(null, currentMode == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            textDark.setTextColor(currentMode == 2 ? selectedColor : getColor(R.color.on_surface));
        }

        if (iconSystem != null) iconSystem.setImageTintList(android.content.res.ColorStateList.valueOf(currentMode == 0 ? selectedColor : unselectedColor));
        if (iconLight != null) iconLight.setImageTintList(android.content.res.ColorStateList.valueOf(currentMode == 1 ? selectedColor : unselectedColor));
        if (iconDark != null) iconDark.setImageTintList(android.content.res.ColorStateList.valueOf(currentMode == 2 ? selectedColor : unselectedColor));
    }

    /**
     * Greys out the intensity/accent rows while the effect is off, so the dependency is obvious.
     */
    private void applyFireworkEnabledState(View intensityRow, View accentRow, boolean enabled) {
        float alpha = enabled ? 1f : 0.4f;
        if (intensityRow != null) {
            intensityRow.setAlpha(alpha);
            intensityRow.setEnabled(enabled);
        }
        if (accentRow != null) {
            accentRow.setAlpha(alpha);
            accentRow.setEnabled(enabled);
        }
    }

    /** Pushes a changed firework setting to the live layer without recreating the activity. */
    private void refreshFireworkLayer() {
        View root = findViewById(android.R.id.content);
        if (root instanceof ViewGroup) {
            org.chimeramc.client.ui.views.FireworkTouchLayer layer =
                    findFireworkLayer((ViewGroup) root);
            if (layer != null) layer.refresh(this);
        }
    }

    /** Finds the shared firework layer in a view tree, or null. */
    private static org.chimeramc.client.ui.views.FireworkTouchLayer findFireworkLayer(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof org.chimeramc.client.ui.views.FireworkTouchLayer) {
                return (org.chimeramc.client.ui.views.FireworkTouchLayer) child;
            }
            if (child instanceof ViewGroup) {
                org.chimeramc.client.ui.views.FireworkTouchLayer found =
                        findFireworkLayer((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void setupColorPicker() {
        colorGridContainer = findViewById(R.id.color_preset_grid);
        moreColorsContainer = findViewById(R.id.color_more_grid);

        if (colorGridContainer != null && moreColorsContainer != null) {
            int currentAccent = personalizationManager.getAccentColor();
            buildColorGrid(colorGridContainer, PersonalizationManager.PRESET_COLORS, currentAccent);
            buildColorGrid(moreColorsContainer, PersonalizationManager.MORE_COLORS, currentAccent);
        }

        android.widget.EditText inputCustomColor = findViewById(R.id.input_custom_color);
        Button btnApplyColor = findViewById(R.id.btn_apply_color);
        if (inputCustomColor != null && btnApplyColor != null) {
            btnApplyColor.setOnClickListener(v -> {
                String input = inputCustomColor.getText().toString().trim();
                try {
                    int color;
                    if (input.startsWith("#")) {
                        color = Color.parseColor(input);
                    } else if (input.contains(",")) {
                        String[] parts = input.split(",");
                        if (parts.length == 3) {
                            color = Color.rgb(Integer.parseInt(parts[0].trim()),
                                    Integer.parseInt(parts[1].trim()),
                                    Integer.parseInt(parts[2].trim()));
                        } else {
                            throw new IllegalArgumentException("Invalid RGB format");
                        }
                    } else {
                        color = Color.parseColor("#" + input);
                    }
                    personalizationManager.setAccentColor(color);
                    refreshColorPickerInPlace();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.invalid_color_format, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void buildColorGrid(LinearLayout container, int[] colors, int selectedColor) {
        container.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int circleSize = (int) (32 * density);
        int margin = (int) (4 * density);
        int checkSize = (int) (14 * density);

        int columns = 15;
        int index = 0;
        while (index < colors.length) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int col = 0; col < columns && index < colors.length; col++, index++) {
                int color = colors[index];

                FrameLayout wrapper = new FrameLayout(this);
                LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(circleSize, circleSize);
                wrapParams.setMargins(margin, margin, margin, margin);
                wrapper.setLayoutParams(wrapParams);

                View circle = new View(this);
                circle.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                GradientDrawable circleDrawable = new GradientDrawable();
                circleDrawable.setShape(GradientDrawable.OVAL);
                circleDrawable.setColor(color);
                if (color == selectedColor) {
                    circleDrawable.setStroke((int) (2 * density), Color.WHITE);
                }
                circle.setBackground(circleDrawable);
                wrapper.addView(circle);

                if (color == selectedColor) {
                    ImageView check = new ImageView(this);
                    FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(checkSize, checkSize);
                    checkParams.gravity = Gravity.CENTER;
                    check.setLayoutParams(checkParams);
                    check.setImageResource(R.drawable.ic_check);
                    check.setColorFilter(Color.WHITE);
                    wrapper.addView(check);
                }

                wrapper.setClickable(true);
                wrapper.setFocusable(true);
                final int finalColor = color;
                wrapper.setOnClickListener(v -> {
                    personalizationManager.setAccentColor(finalColor);
                    refreshColorPickerInPlace();
                });
                DynamicAnim.applyPressScale(wrapper);

                row.addView(wrapper);
            }

            container.addView(row);
        }

    }

    private void refreshColorPickerInPlace() {
        setupColorPicker();
        PersonalizationManager pm = new PersonalizationManager(this);
        int accent = pm.getAccentColor();
        
        pm.applyToActivity(this);

        refreshThemeSelectionUI();
        
        TextView[] tabs = getSettingsTabs();
        selectTab(tabs[selectedTabIndex]);
        
        View settingsTitle = findViewById(R.id.settings_title);
        if (settingsTitle instanceof TextView && accent != 0) {
            ((TextView) settingsTitle).setTextColor(accent);
        }
        
        Button btnSelectImage = findViewById(R.id.btn_select_bg_image);
        if (btnSelectImage != null && accent != 0) {
            btnSelectImage.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnSelectImage.setTextColor(Color.WHITE);
        }
        
        Button btnCheckUpdate = findViewById(R.id.btn_check_update);
        if (btnCheckUpdate != null && accent != 0) {
            btnCheckUpdate.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnCheckUpdate.setTextColor(Color.WHITE);
        }

        Button btnUpdatePreloaderSigs = findViewById(R.id.btn_update_preloader_sigs);
        if (btnUpdatePreloaderSigs != null && accent != 0) {
            btnUpdatePreloaderSigs.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnUpdatePreloaderSigs.setTextColor(Color.WHITE);
        }
        
        SwitchMaterial switchLogcat = findViewById(R.id.switch_logcat);
        if (switchLogcat != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchLogcat.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchLogcat.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }
        
        SwitchMaterial switchManagedLogin = findViewById(R.id.switch_managed_login);
        if (switchManagedLogin != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchManagedLogin.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchManagedLogin.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }

        SwitchMaterial switchCrashUpload = findViewById(R.id.switch_crash_upload);
        if (switchCrashUpload != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchCrashUpload.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchCrashUpload.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }
        
        SwitchMaterial switchLowInputDelay = findViewById(R.id.switch_low_input_delay);
        if (switchLowInputDelay != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchLowInputDelay.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchLowInputDelay.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }

        SwitchMaterial switchGxCore = findViewById(R.id.switch_gxcore);
        if (switchGxCore != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchGxCore.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchGxCore.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }

        SwitchMaterial switchReduceNetworkLatency = findViewById(R.id.switch_reduce_network_latency);
        if (switchReduceNetworkLatency != null && accent != 0) {
            int[][] states = {{android.R.attr.state_checked}, {}};
            switchReduceNetworkLatency.setThumbTintList(new ColorStateList(states, new int[]{accent, 0xFFAAAAAA}));
            int trackChecked = Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent));
            switchReduceNetworkLatency.setTrackTintList(new ColorStateList(states, new int[]{trackChecked, 0xFF555555}));
        }

        TextView navAppName = findViewById(R.id.nav_app_name);
        if (navAppName != null && accent != 0) {
            pm.applySolidAccentText(navAppName, accent);
        }
        
        Button navSignInBtn = findViewById(R.id.nav_sign_in_button);
        if (navSignInBtn != null && accent != 0) {
            navSignInBtn.setBackgroundTintList(ColorStateList.valueOf(accent));
            navSignInBtn.setTextColor(Color.WHITE);
        }
        
        int[] navTabIds = {R.id.nav_tab_launch, R.id.nav_tab_instances, R.id.nav_tab_customize, R.id.nav_tab_settings};
        for (int id : navTabIds) {
            TextView navTab = findViewById(id);
            if (navTab != null && id == R.id.nav_tab_settings && accent != 0) {
                navTab.setTextColor(accent);
                navTab.setTypeface(navTab.getTypeface(), android.graphics.Typeface.BOLD);
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(navTab, ColorStateList.valueOf(accent));
            }
        }
    }

    private void setupBackgroundImagePicker() {
        bgImageStatus = findViewById(R.id.bg_image_status);
        bgImagePreview = findViewById(R.id.bg_image_preview);
        Button btnSelectImage = findViewById(R.id.btn_select_bg_image);
        Button btnClearImage = findViewById(R.id.btn_clear_bg_image);

        if (btnSelectImage == null) return;

        setupBackgroundImageControls();
        updateBgImageUI();

        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            bgImagePickerLauncher.launch(intent);
        });

        if (btnClearImage != null) {
            btnClearImage.setOnClickListener(v -> {
                personalizationManager.clearBackgroundImage();
                updateBgImageUI();
                recreate();
            });
        }
    }

    private void setupBackgroundImageControls() {
        SeekBar blurSeek = findViewById(R.id.seek_bg_image_blur);
        SeekBar brightnessSeek = findViewById(R.id.seek_bg_image_brightness);
        bgImageBlurValue = findViewById(R.id.bg_image_blur_value);
        bgImageBrightnessValue = findViewById(R.id.bg_image_brightness_value);

        if (blurSeek != null) {
            blurSeek.setMax(PersonalizationManager.BG_BLUR_MAX);
            blurSeek.setProgress(personalizationManager.getBackgroundImageBlur());
            updateBgImageBlurValue(blurSeek.getProgress());
            blurSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateBgImageBlurValue(progress);
                    if (!fromUser) return;
                    personalizationManager.setBackgroundImageBlur(progress);
                    if (personalizationManager.supportsRealtimeBackgroundBlur()) {
                        refreshBackgroundImageEffects();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    refreshBackgroundImageEffects();
                }
            });
        }

        if (brightnessSeek != null) {
            brightnessSeek.setMin(PersonalizationManager.BG_BRIGHTNESS_MIN);
            brightnessSeek.setMax(PersonalizationManager.BG_BRIGHTNESS_MAX);
            brightnessSeek.setProgress(personalizationManager.getBackgroundImageBrightness());
            updateBgImageBrightnessValue(brightnessSeek.getProgress());
            brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateBgImageBrightnessValue(progress);
                    if (!fromUser) return;
                    personalizationManager.setBackgroundImageBrightness(progress);
                    refreshBackgroundImageColorEffects();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    private void updateBgImageBlurValue(int blurRadius) {
        if (bgImageBlurValue != null) {
            bgImageBlurValue.setText(getString(R.string.bg_image_blur_value, blurRadius));
        }
    }

    private void updateBgImageBrightnessValue(int brightnessPercent) {
        if (bgImageBrightnessValue != null) {
            bgImageBrightnessValue.setText(getString(R.string.bg_image_brightness_value, brightnessPercent));
        }
    }

    private void refreshBackgroundImageEffects() {
        personalizationManager.refreshBackgroundEffects(this);
        if (bgImagePreview != null) {
            personalizationManager.refreshBackgroundImageView(bgImagePreview);
        }
    }

    private void refreshBackgroundImageColorEffects() {
        personalizationManager.refreshBackgroundColorEffects(this);
        if (bgImagePreview != null) {
            personalizationManager.applyBackgroundImageEffects(bgImagePreview);
        }
    }

    private void updateBgImageUI() {
        if (bgImageStatus == null) return;
        boolean hasBackgroundImage = personalizationManager.hasBackgroundImage();
        View effectControls = findViewById(R.id.bg_image_effect_controls);
        if (effectControls != null) {
            effectControls.setVisibility(hasBackgroundImage ? View.VISIBLE : View.GONE);
        }

        if (hasBackgroundImage) {
            bgImageStatus.setText(R.string.bg_image_selected);
            if (bgImagePreview != null) {
                if (personalizationManager.applyBackgroundImageToView(bgImagePreview)) {
                    bgImagePreview.setVisibility(View.VISIBLE);
                }
            }
            View btnClear = findViewById(R.id.btn_clear_bg_image);
            if (btnClear != null) btnClear.setVisibility(View.VISIBLE);
        } else {
            bgImageStatus.setText(R.string.bg_image_none);
            if (bgImagePreview != null) {
                bgImagePreview.setImageDrawable(null);
                bgImagePreview.setVisibility(View.GONE);
            }
            View btnClear = findViewById(R.id.btn_clear_bg_image);
            if (btnClear != null) btnClear.setVisibility(View.GONE);
        }
    }



    private void setupUpdatesSection() {
        try {
            String localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView versionText = findViewById(R.id.version_text);
            versionText.setText(getString(R.string.version_prefix) + localVersion);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        Button btnCheckUpdate = findViewById(R.id.btn_check_update);
        btnCheckUpdate.setOnClickListener(v -> handleUpdateButtonClick());

        preloaderSigsLastUpdateText = findViewById(R.id.preloader_sigs_last_update);
        preloaderSigsUpdateButton = findViewById(R.id.btn_update_preloader_sigs);
        refreshPreloaderSigsLastUpdateUi();
        if (preloaderSigsUpdateButton != null) {
            preloaderSigsUpdateButton.setOnClickListener(v -> handlePreloaderSigsUpdateClick());
        }
    }

    private void handlePreloaderSigsUpdateClick() {
        if (preloaderSigsUpdateButton == null) {
            return;
        }
        if (!PreloaderSignatureRulesManager.hasRemoteRulesUrl()) {
            Toast.makeText(this, R.string.preloader_sigs_no_remote_url, Toast.LENGTH_SHORT).show();
            return;
        }

        preloaderSigsUpdateButton.setEnabled(false);
        preloaderSigsUpdateButton.setText(R.string.preloader_sigs_updating);
        PreloaderSignatureRulesManager.refreshNow(this, result -> {
            if (isFinishing()) {
                return;
            }
            preloaderSigsUpdateButton.setEnabled(true);
            preloaderSigsUpdateButton.setText(R.string.preloader_sigs_update);
            refreshPreloaderSigsLastUpdateUi();

            if (result.success) {
                Toast.makeText(this, R.string.preloader_sigs_update_success, Toast.LENGTH_SHORT).show();
            } else {
                String detail = result.message.isEmpty()
                        ? getString(R.string.unknown_error)
                        : result.message;
                Toast.makeText(this, getString(R.string.preloader_sigs_update_failed, detail), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshPreloaderSigsLastUpdateUi() {
        if (preloaderSigsLastUpdateText == null) {
            return;
        }

        long updateTime = PreloaderSignatureRulesManager.getLastSuccessfulUpdateTime(this);
        String updateText = updateTime > 0L
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(new Date(updateTime))
                : getString(R.string.preloader_sigs_never_updated);
        preloaderSigsLastUpdateText.setText(getString(R.string.preloader_sigs_last_update, updateText));
    }

    private void setupMigrationSection() {
        switchSharedStorageLayout = findViewById(R.id.switch_shared_storage_layout);
        sharedStorageLayoutStatus = findViewById(R.id.shared_storage_layout_status);
        TextView legacyPath = findViewById(R.id.migration_cleanup_path);
        migrationCleanupStatus = findViewById(R.id.migration_cleanup_status);
        migrationCleanupButton = findViewById(R.id.btn_cleanup_legacy_dir);

        if (switchSharedStorageLayout != null) {
            switchSharedStorageLayout.setChecked(LauncherStorage.isUsingNewSharedStorage(this));
            switchSharedStorageLayout.setOnCheckedChangeListener((buttonView, isChecked) -> {
                LauncherStorage.setUseNewSharedStorage(this, isChecked);
                refreshSharedStorageLayoutUi();
                Toast.makeText(this, R.string.shared_storage_layout_changed, Toast.LENGTH_LONG).show();
            });
        }
        refreshSharedStorageLayoutUi();

        if (legacyPath != null) {
            legacyPath.setText(LauncherStorage.getLegacyRoot().getAbsolutePath());
        }
        if (migrationCleanupButton != null) {
            migrationCleanupButton.setOnClickListener(v -> confirmCleanupLegacyDir());
        }
        refreshMigrationCleanupUi();
    }

    private void refreshSharedStorageLayoutUi() {
        if (sharedStorageLayoutStatus == null) return;

        int modeRes = LauncherStorage.isUsingNewSharedStorage(this)
                ? R.string.shared_storage_layout_mode_new
                : R.string.shared_storage_layout_mode_legacy;
        sharedStorageLayoutStatus.setText(getString(
                R.string.shared_storage_layout_status,
                getString(modeRes),
                LauncherStorage.getSharedInternalGameDataDisplayPath(this),
                LauncherStorage.getSharedExternalGameDataDisplayPath(this)
        ));
    }

    private void refreshMigrationCleanupUi() {
        if (migrationCleanupStatus == null || migrationCleanupButton == null) return;

        boolean migrationCompleted = LauncherStorage.isMigrationCompleted(this);
        boolean legacyExists = LauncherStorage.getLegacyRoot().isDirectory();
        migrationCleanupButton.setEnabled(migrationCompleted && legacyExists);

        if (!migrationCompleted) {
            migrationCleanupStatus.setText(R.string.migration_cleanup_unavailable_not_completed);
        } else if (!legacyExists) {
            migrationCleanupStatus.setText(R.string.migration_cleanup_unavailable_missing);
        } else {
            migrationCleanupStatus.setText(R.string.migration_cleanup_ready);
        }
    }

    private void confirmCleanupLegacyDir() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.migration_cleanup_confirm_title)
                .setMessage(R.string.migration_cleanup_confirm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> cleanupLegacyDir())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void cleanupLegacyDir() {
        if (migrationCleanupButton != null) {
            migrationCleanupButton.setEnabled(false);
        }
        AsyncTask.execute(() -> {
            LauncherStorage.LegacyCleanupResult result = LauncherStorage.cleanupLegacyRoot(this);
            runOnUiThread(() -> {
                refreshMigrationCleanupUi();
                if (result.success) {
                    String message = getString(
                            R.string.migration_cleanup_success,
                            result.deletedFiles,
                            formatBytes(result.deletedBytes)
                    );
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (migrationCleanupStatus != null) {
                        migrationCleanupStatus.setText(message);
                    }
                } else {
                    String message = getString(R.string.migration_cleanup_failed, result.errorMessage);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (migrationCleanupStatus != null) {
                        migrationCleanupStatus.setText(message);
                    }
                }
            });
        });
    }

    private void setupAboutSection() {
        findViewById(R.id.settings_btn_github).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ChimeraAnt-DEV/ChimeraLauncher"))));

        findViewById(R.id.settings_btn_discord).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/jsnzw4ueAt"))));

        View openAbout = findViewById(R.id.settings_open_about);
        if (openAbout != null) {
            openAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        }

        View export = findViewById(R.id.settings_export_backup);
        if (export != null) {
            export.setOnClickListener(v ->
                    settingsExportLauncher.launch("chimera-launcher-settings.json"));
        }
        View importButton = findViewById(R.id.settings_import_backup);
        if (importButton != null) {
            importButton.setOnClickListener(v ->
                    settingsImportLauncher.launch(new String[]{"application/json", "text/*", "*/*"}));
        }
    }

    /**
     * Writes the launcher's configuration to the chosen document.
     *
     * Credentials are excluded by {@link LauncherSettingsBackup}; the toast repeats that so a
     * user cannot reasonably think they are sharing a full account backup.
     */
    private void exportSettingsBackup(Uri target) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(target)) {
            if (out == null) throw new java.io.IOException("Could not open the selected file");
            String json = LauncherSettingsBackup.export(this, true, true);
            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, R.string.settings_backup_exported, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_backup_failed, describe(e)), Toast.LENGTH_LONG).show();
        }
    }

    private void importSettingsBackup(Uri source) {
        try (java.io.InputStream in = getContentResolver().openInputStream(source)) {
            if (in == null) throw new java.io.IOException("Could not open the selected file");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            String json = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            LauncherSettingsBackup.ImportResult result = LauncherSettingsBackup.importFrom(this, json);
            if (!result.success) {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, getString(R.string.settings_backup_imported, result.profileCount),
                    Toast.LENGTH_LONG).show();
            // Rebuild the screen so every control shows the restored values rather than the
            // ones it was bound with on entry.
            recreate();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_backup_failed, describe(e)), Toast.LENGTH_LONG).show();
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void handleUpdateButtonClick() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastUpdateButtonTapTime > TAP_TIMEOUT_MS) {
            updateButtonTapCount = 0;
        }

        updateButtonTapCount++;
        lastUpdateButtonTapTime = currentTime;

        if (updateButtonTapCount >= EASTER_EGG_TAP_COUNT) {
            updateButtonTapCount = 0;
            triggerEasterEgg();
        } else {
            new GithubReleaseUpdater(this, "ChimeraAnt-DEV", "ChimeraLauncher", permissionResultLauncher).checkUpdate();
        }
    }

    private void triggerEasterEgg() {
        try {
            String encoded = "aHR0cHM6Ly95b3V0dS5iZS9GdHV0TEE2M0NwOD9zaT1CSExEWHZLOTZPZ1A0NUI4";
            String url = new String(Base64.decode(encoded, Base64.DEFAULT));
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupNavBar() {
        setActiveNavTab(R.id.nav_tab_settings);
        findViewById(R.id.nav_tab_settings).setOnClickListener(v -> {});
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0);
    }
}