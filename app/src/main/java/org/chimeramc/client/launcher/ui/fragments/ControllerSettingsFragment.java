package org.chimeramc.client.ui.fragments;

import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.chimeramc.client.R;
import org.chimeramc.client.core.minecraft.MinecraftLauncher;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.launcher.controller.ControllerInputProcessor;
import org.chimeramc.client.launcher.controller.ControllerProfile;
import org.chimeramc.client.launcher.controller.ControllerProfileCodec;
import org.chimeramc.client.launcher.controller.ControllerProfileManager;
import org.chimeramc.client.launcher.controller.ControllerType;
import org.chimeramc.client.launcher.controller.StickCalibration;
import org.chimeramc.client.launcher.controller.StickCalibrationSession;
import org.chimeramc.client.launcher.controller.StickCurve;
import org.chimeramc.client.launcher.controller.TriggerCurve;
import org.chimeramc.client.ui.dialogs.CustomAlertDialog;
import org.chimeramc.client.launcher.ui.views.Controller3DView;
import org.chimeramc.client.ui.views.CurvePreviewView;
import org.chimeramc.client.util.PersonalizationManager;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller setup, hosted inside {@code CustomizeActivity} rather than in its own tab.
 *
 * The illustration highlights the physically pressed button, so this screen needs raw key
 * events; the host activity consults {@link #wantsRawKeyEvents()} before it consumes a
 * bumper press for tab navigation.
 */
public class ControllerSettingsFragment extends Fragment {

    private Controller3DView illustration;
    private TextView statusText;
    private TextView illustrationLabel;
    private LinearLayout profileChips;
    private ControllerProfileManager profileManager;
    private ControllerType currentType = ControllerType.XBOX;
    private ControllerType detectedType;
    private int manualIndex;
    private boolean autoSelected;
    private InputManager inputManager;
    private ActivityResultLauncher<String> exportProfileLauncher;
    private ActivityResultLauncher<String[]> importProfileLauncher;

    private android.widget.Switch antiDriftSwitch;
    private TextView antiDriftStatus;
    private TextView calibrateButton;
    private final StickCalibrationSession calibration = new StickCalibrationSession();
    private int lastCalibrationProgress = -1;
    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            refreshDetection();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            refreshDetection();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            refreshDetection();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_controller, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                this::writeProfileExport);
        importProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::readProfileImport);
    }

    private void writeProfileExport(Uri target) {
        if (target == null || profileManager == null) return;
        List<ControllerProfile> profiles = profileManager.getProfiles(currentType);
        String json = ControllerProfileCodec.export(currentType, profiles);
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) throw new java.io.IOException("no output stream");
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(requireContext(),
                    getString(R.string.controller_export_success, target.getLastPathSegment()),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    getString(R.string.controller_export_failed, describe(e)),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void readProfileImport(Uri source) {
        if (source == null || profileManager == null) return;
        String json;
        try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(source)) {
            if (in == null) throw new java.io.IOException("no input stream");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            json = buffer.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    getString(R.string.controller_import_failed, describe(e)),
                    Toast.LENGTH_LONG).show();
            return;
        }

        ControllerProfileCodec.ImportResult result;
        try {
            result = ControllerProfileCodec.importFrom(json);
        } catch (IllegalArgumentException e) {
            // Distinguish "wrong kind of file" from "corrupt file" so the message is useful.
            int message;
            if ("wrong format".equals(e.getMessage())) {
                message = R.string.controller_import_wrong_format;
            } else if ("empty".equals(e.getMessage()) || "no profiles".equals(e.getMessage())) {
                message = R.string.controller_import_empty;
            } else {
                message = R.string.controller_import_malformed;
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            return;
        }

        // Profiles land in the currently shown controller type, since that is the list the
        // user is looking at. A different type in the file is not an error, just ignored.
        List<ControllerProfile> merged = new ArrayList<>(profileManager.getProfiles(currentType));
        int imported = 0;
        int dropped = result.skipped;
        for (ControllerProfile profile : result.profiles) {
            if (merged.size() >= ControllerProfile.MAX_SLOTS) {
                dropped++;
                continue;
            }
            merged.add(profile);
            imported++;
        }
        if (imported == 0) {
            Toast.makeText(requireContext(),
                    getString(R.string.controller_import_full, ControllerProfile.MAX_SLOTS),
                    Toast.LENGTH_LONG).show();
            return;
        }
        profileManager.saveProfiles(currentType, merged);
        refreshProfiles();
        if (dropped > 0) {
            Toast.makeText(requireContext(),
                    getString(R.string.controller_import_partial, imported, dropped),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(),
                    getString(R.string.controller_import_success, imported),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String displayNameOf(GameVersion version) {
        if (version == null) return "";
        if (version.displayName != null && !version.displayName.isEmpty()) return version.displayName;
        if (version.versionCode != null && !version.versionCode.isEmpty()) return version.versionCode;
        return version.directoryName == null ? "" : version.directoryName;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PersonalizationManager pm = new PersonalizationManager(requireContext());
        View root = view.findViewById(R.id.controller_root);
        if (root != null) {
            pm.applyAccentToView(root, requireContext());
        }
        applyCompactAndRounding(pm);

        illustration = view.findViewById(R.id.controller_illustration);
        if (illustration != null) {
            illustration.setAccentColor(pm.getAccentColor());
        }
        statusText = view.findViewById(R.id.controller_status);
        illustrationLabel = view.findViewById(R.id.controller_illustration_label);
        profileChips = view.findViewById(R.id.controller_profile_chips);
        profileManager = new ControllerProfileManager(requireContext());

        view.findViewById(R.id.controller_next_button).setOnClickListener(v -> nextIllustration());
        view.findViewById(R.id.controller_edit_button).setOnClickListener(v -> openEditor());
        view.findViewById(R.id.controller_profile_create).setOnClickListener(v -> createProfile());
        view.findViewById(R.id.controller_profile_rename).setOnClickListener(v -> renameProfile());
        view.findViewById(R.id.controller_profile_duplicate).setOnClickListener(v -> duplicateProfile());
        view.findViewById(R.id.controller_profile_delete).setOnClickListener(v -> deleteProfile());
        view.findViewById(R.id.controller_profile_export).setOnClickListener(v -> startProfileExport());
        view.findViewById(R.id.controller_profile_import).setOnClickListener(v -> startProfileImport());
        view.findViewById(R.id.controller_bind_instance).setOnClickListener(v -> bindToInstance());
        view.findViewById(R.id.controller_bind_clear).setOnClickListener(v -> clearInstanceBinding());

        antiDriftSwitch = view.findViewById(R.id.controller_antidrift_switch);
        antiDriftStatus = view.findViewById(R.id.controller_antidrift_status);
        calibrateButton = view.findViewById(R.id.controller_calibrate);
        antiDriftSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onAntiDriftToggled(isChecked));
        calibrateButton.setOnClickListener(v -> startCalibration());

        inputManager = (InputManager) requireContext().getSystemService(android.content.Context.INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(deviceListener, null);
        }
        refreshDetection();
        refreshProfiles();
    }

    @Override
    public void onDestroyView() {
        if (inputManager != null) {
            try {
                inputManager.unregisterInputDeviceListener(deviceListener);
            } catch (Exception ignored) {
            }
        }
        illustration = null;
        statusText = null;
        illustrationLabel = null;
        profileChips = null;
        antiDriftSwitch = null;
        antiDriftStatus = null;
        calibrateButton = null;
        calibration.cancel();
        super.onDestroyView();
    }

    /** True while the illustration is on screen and must see the raw press. */
    public boolean wantsRawKeyEvents() {
        return illustration != null;
    }

    public void handleHardwareKey(int keyCode, boolean down) {
        if (illustration != null) {
            illustration.handleKeyEvent(keyCode, down);
        }
    }

    public void handleHardwareMotion(MotionEvent event) {
        boolean joystick = (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (!joystick) {
            return;
        }
        // Calibration runs before the illustration so the sampling sees every frame the pad
        // reports, including the ones the illustration would ignore.
        if (calibration.isActive()) {
            feedCalibration(event);
        }
        if (illustration != null) {
            illustration.handleMotionEvent(event);
        }
    }

    /**
     * Feeds one frame to the calibration run and updates the readout.
     *
     * The measurement is written into the active profile and the processor is refreshed as soon
     * as the run completes, so the pad being held still picks up its new threshold immediately —
     * calibrating and then having to reopen the screen to feel the effect would look broken.
     */
    private void feedCalibration(MotionEvent event) {
        boolean complete = calibration.sample(
                event.getAxisValue(MotionEvent.AXIS_X),
                event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ));
        if (complete) {
            finishCalibration();
            return;
        }
        // Repainting on every frame would be wasteful; one update per sample is enough to show
        // progress and is what makes the run feel responsive.
        if (antiDriftStatus == null) return;
        if (calibration.wasLastSampleRejected()) {
            antiDriftStatus.setText(R.string.controller_calibrate_moved);
            return;
        }
        if (calibration.samplesTaken() != lastCalibrationProgress) {
            lastCalibrationProgress = calibration.samplesTaken();
            antiDriftStatus.setText(getString(R.string.controller_calibrate_progress,
                    calibration.samplesTaken(), StickCalibration.SAMPLE_TARGET));
        }
    }

    private void refreshDetection() {
        if (illustration == null || statusText == null) {
            // A device broadcast can land after the view is torn down.
            return;
        }
        ControllerType found = findConnectedController();
        if (found != null) {
            detectedType = found;
            currentType = found;
            autoSelected = true;
            statusText.setText(getString(R.string.controller_status_connected, found.getDisplayName()));
        } else {
            detectedType = null;
            autoSelected = false;
            statusText.setText(getString(R.string.controller_status_none));
        }
        illustration.setType(currentType);
        illustrationLabel.setText(currentType.getDisplayName());
        ControllerInputProcessor.detectAndLoad(requireContext());
        refreshProfiles();
    }

    private ControllerType findConnectedController() {
        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            boolean gamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
            boolean joystick = (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (gamepad || joystick) {
                ControllerType type = ControllerType.from(device);
                if (type != null) return type;
            }
        }
        return null;
    }

    private void nextIllustration() {
        if (illustration == null) return;
        ControllerType[] order = {ControllerType.XBOX, ControllerType.DS4, ControllerType.DUAL_SENSE};
        if (autoSelected) {
            manualIndex = 0;
        }
        manualIndex = (manualIndex + 1) % order.length;
        currentType = order[manualIndex];
        autoSelected = false;
        statusText.setText(getString(R.string.controller_status_preview, currentType.getDisplayName()));
        illustration.setType(currentType);
        illustrationLabel.setText(currentType.getDisplayName());
        refreshProfiles();
    }

    private void applyCompactAndRounding(PersonalizationManager pm) {
        View root = getView() == null ? null : getView().findViewById(R.id.controller_root);
        if (root instanceof ViewGroup viewGroup) {
            int pad = (int) ((pm.isCompactMode() ? 8 : 16) * getResources().getDisplayMetrics().density);
            viewGroup.setPadding(pad, pad, pad, pad);
        }
    }

    private void refreshProfiles() {
        if (profileChips == null || profileManager == null) return;
        profileChips.removeAllViews();
        List<ControllerProfile> profiles = profileManager.getProfiles(currentType);
        int active = profileManager.getActiveSlot(currentType);
        View emptyHint = getView() == null ? null : getView().findViewById(R.id.empty_controller_profiles);
        boolean hasProfiles = profiles != null && !profiles.isEmpty();
        if (emptyHint != null) {
            emptyHint.setVisibility(hasProfiles ? View.GONE : View.VISIBLE);
        }
        if (!hasProfiles) return;
        for (int i = 0; i < profiles.size(); i++) {
            final int slot = i;
            TextView chip = new TextView(requireContext());
            chip.setText(getString(R.string.controller_profile_slot, i + 1, profiles.get(i).getName()));
            chip.setTextSize(12f);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setGravity(android.view.Gravity.CENTER);
            if (i == active) {
                chip.setTextColor(requireContext().getColor(R.color.on_primary));
                chip.setBackgroundResource(R.drawable.bg_filter_chip);
                chip.setSelected(true);
            } else {
                chip.setTextColor(requireContext().getColor(R.color.on_surface));
                chip.setBackgroundResource(R.drawable.bg_filter_chip);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> selectProfile(slot));
            profileChips.addView(chip);
        }
        ControllerProfile activeProfile = profileManager.getActiveProfile(currentType);
        ControllerInputProcessor.setActiveProfile(currentType, activeProfile);
        refreshAntiDriftUi(activeProfile);
    }

    // --- Anti stick drift ------------------------------------------------------------------

    /**
     * Mirrors the active profile's anti-drift state into the switch and the readout.
     *
     * Called wherever the active profile can change, so the switch never shows a state that
     * belongs to a different profile.
     */
    private void refreshAntiDriftUi(ControllerProfile profile) {
        if (antiDriftSwitch == null || antiDriftStatus == null) return;
        boolean enabled = profile != null && profile.isAntiDriftEnabled();
        // Detach first: setChecked from here would otherwise re-enter the toggle handler and
        // write the value straight back to storage on every refresh.
        antiDriftSwitch.setOnCheckedChangeListener(null);
        antiDriftSwitch.setChecked(enabled);
        antiDriftSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onAntiDriftToggled(isChecked));

        if (profile == null) {
            antiDriftStatus.setText(R.string.controller_antidrift_status_off);
            return;
        }
        boolean calibrated = StickCalibration.isCalibrated(profile.getLeftStickNoiseFloor())
                || StickCalibration.isCalibrated(profile.getRightStickNoiseFloor());
        if (!enabled) {
            antiDriftStatus.setText(R.string.controller_antidrift_status_off);
        } else if (calibrated) {
            antiDriftStatus.setText(getString(R.string.controller_antidrift_status_calibrated,
                    formatFloor(profile.getLeftStickNoiseFloor()),
                    formatFloor(profile.getRightStickNoiseFloor())));
        } else {
            antiDriftStatus.setText(R.string.controller_antidrift_status_uncalibrated);
        }
        if (calibrateButton != null) {
            calibrateButton.setEnabled(enabled);
            calibrateButton.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private static String formatFloor(float floor) {
        return String.format(java.util.Locale.US, "%.3f", floor);
    }

    private void onAntiDriftToggled(boolean enabled) {
        ControllerProfile active = profileManager.getActiveProfile(currentType);
        if (active == null) return;
        active.setAntiDriftEnabled(enabled);
        persistActiveProfile(active);
        refreshAntiDriftUi(active);
    }

    private void startCalibration() {
        ControllerProfile active = profileManager.getActiveProfile(currentType);
        if (active == null) return;
        if (!active.isAntiDriftEnabled()) {
            Toast.makeText(requireContext(), R.string.controller_calibrate_needs_toggle, Toast.LENGTH_SHORT).show();
            return;
        }
        if (findConnectedController() == null) {
            Toast.makeText(requireContext(), R.string.controller_calibrate_no_controller, Toast.LENGTH_SHORT).show();
            return;
        }
        calibration.start();
        lastCalibrationProgress = -1;
        org.chimeramc.client.ui.animation.UiTouchFeedback.selection(requireContext());
        if (antiDriftStatus != null) {
            antiDriftStatus.setText(R.string.controller_calibrate_prompt);
        }
        if (calibrateButton != null) {
            calibrateButton.setText(R.string.controller_calibrate_prompt);
        }
    }

    private void finishCalibration() {
        ControllerProfile active = profileManager.getActiveProfile(currentType);
        if (active == null) return;
        active.setLeftStickNoiseFloor(calibration.leftFloor());
        active.setRightStickNoiseFloor(calibration.rightFloor());
        persistActiveProfile(active);
        if (calibrateButton != null) {
            calibrateButton.setText(R.string.controller_calibrate);
        }
        refreshAntiDriftUi(active);
        Toast.makeText(requireContext(), getString(R.string.controller_calibrate_done,
                formatFloor(active.getLeftStickNoiseFloor()),
                formatFloor(active.getRightStickNoiseFloor())), Toast.LENGTH_LONG).show();
    }

    /** Writes the active profile back to its slot and pushes it into the live input path. */
    private void persistActiveProfile(ControllerProfile profile) {
        int slot = profileManager.getActiveSlot(currentType);
        List<ControllerProfile> profiles = profileManager.getProfiles(currentType);
        if (slot >= 0 && slot < profiles.size()) {
            profiles.set(slot, profile);
            profileManager.saveProfiles(currentType, profiles);
        }
        ControllerInputProcessor.setActiveProfile(currentType, profile);
    }

    private void selectProfile(int slot) {
        profileManager.setActiveSlot(currentType, slot);
        ControllerProfile activeProfile = profileManager.getActiveProfile(currentType);
        ControllerInputProcessor.setActiveProfile(currentType, activeProfile);
        org.chimeramc.client.ui.animation.UiTouchFeedback.selection(requireContext());
        Toast.makeText(requireContext(), getString(R.string.controller_profile_active_changed, activeProfile.getName()), Toast.LENGTH_SHORT).show();
        refreshProfiles();
    }

    private void startProfileExport() {
        if (exportProfileLauncher == null) return;
        exportProfileLauncher.launch("chimera-controller-profiles.json");
    }

    private void startProfileImport() {
        if (importProfileLauncher == null) return;
        importProfileLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
    }

    /**
     * Binds the active profile to an instance so that instance always launches with it.
     *
     * Offered as a picker rather than a free-text id because the storage profile id is an
     * internal key; the user should choose a version by name.
     */
    private void bindToInstance() {
        List<GameVersion> versions = VersionManager.get(requireContext()).getInstalledVersions();
        if (versions == null || versions.isEmpty()) {
            Toast.makeText(requireContext(), R.string.controller_bind_none, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[versions.size()];
        for (int i = 0; i < versions.size(); i++) {
            GameVersion version = versions.get(i);
            String name = displayNameOf(version);
            labels[i] = profileManager.hasBinding(MinecraftLauncher.getStorageProfileId(version))
                    ? name + "  \u2022" : name;
        }

        ControllerProfile active = profileManager.getActiveProfile(currentType);
        int activeSlot = profileManager.getActiveSlot(currentType);
        new CustomAlertDialog(requireContext())
                .setTitleText(getString(R.string.controller_bind_title))
                .setMessage(getString(R.string.controller_bind_hint))
                .setItems(labels, (dialog, which) -> {
                    GameVersion version = versions.get(which);
                    String profileId = MinecraftLauncher.getStorageProfileId(version);
                    profileManager.bindInstance(profileId, currentType, activeSlot);
                    // A binding is useless if the running session still holds the old profile.
                    ControllerInputProcessor.setActiveProfile(currentType, active);
                    Toast.makeText(requireContext(),
                            getString(R.string.controller_bind_success, displayNameOf(version)),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.controller_cancel), v -> {
                })
                .show();
    }

    private void clearInstanceBinding() {
        List<GameVersion> versions = VersionManager.get(requireContext()).getInstalledVersions();
        if (versions == null || versions.isEmpty()) {
            Toast.makeText(requireContext(), R.string.controller_bind_none, Toast.LENGTH_SHORT).show();
            return;
        }
        List<GameVersion> bound = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (GameVersion version : versions) {
            if (profileManager.hasBinding(MinecraftLauncher.getStorageProfileId(version))) {
                bound.add(version);
                labels.add(displayNameOf(version));
            }
        }
        if (bound.isEmpty()) {
            Toast.makeText(requireContext(), R.string.controller_bind_none, Toast.LENGTH_SHORT).show();
            return;
        }
        new CustomAlertDialog(requireContext())
                .setTitleText(getString(R.string.controller_bind_clear))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    GameVersion version = bound.get(which);
                    profileManager.clearBinding(MinecraftLauncher.getStorageProfileId(version));
                    Toast.makeText(requireContext(),
                            getString(R.string.controller_bind_cleared, displayNameOf(version)),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.controller_cancel), v -> {
                })
                .show();
    }

    private void createProfile() {
        if (profileManager.getProfiles(currentType).size() >= ControllerProfile.MAX_SLOTS) {
            Toast.makeText(requireContext(), R.string.controller_profile_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        promptForName(getString(R.string.controller_profile_name_prompt), name -> {
            int slot = profileManager.addProfile(currentType, name);
            if (slot >= 0) {
                profileManager.setActiveSlot(currentType, slot);
                Toast.makeText(requireContext(), R.string.controller_profile_created, Toast.LENGTH_SHORT).show();
                refreshProfiles();
            }
        });
    }

    private void renameProfile() {
        int active = profileManager.getActiveSlot(currentType);
        promptForName(getString(R.string.controller_profile_name_prompt), name -> {
            profileManager.renameProfile(currentType, active, name);
            Toast.makeText(requireContext(), R.string.controller_profile_renamed, Toast.LENGTH_SHORT).show();
            refreshProfiles();
        });
    }

    private void duplicateProfile() {
        int active = profileManager.getActiveSlot(currentType);
        profileManager.duplicateProfile(currentType, active, null);
        Toast.makeText(requireContext(), R.string.controller_profile_duplicated, Toast.LENGTH_SHORT).show();
        refreshProfiles();
    }

    private void deleteProfile() {
        int active = profileManager.getActiveSlot(currentType);
        profileManager.deleteProfile(currentType, active);
        org.chimeramc.client.ui.animation.UiTouchFeedback.reject(requireContext());
        Toast.makeText(requireContext(), R.string.controller_profile_deleted, Toast.LENGTH_SHORT).show();
        refreshProfiles();
    }

    private void promptForName(String title, NameCallback callback) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setSingleLine(true);
        CustomAlertDialog dialog = new CustomAlertDialog(requireContext())
                .setTitleText(title)
                .setCustomView(input)
                .setPositiveButton(getString(R.string.controller_ok), v -> {
                    String name = input.getText().toString();
                    callback.onName(name);
                })
                .setNegativeButton(getString(R.string.controller_cancel), v -> {
                });
        dialog.show();
    }

    private void openEditor() {
        ControllerProfile profile = profileManager.getActiveProfile(currentType);
        ControllerProfile working = profile.copy();
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(4));

        content.addView(label(getString(R.string.controller_editor_name_label)));
        android.widget.EditText nameInput = new android.widget.EditText(requireContext());
        nameInput.setText(working.getName());
        content.addView(nameInput);

        content.addView(label(getString(R.string.controller_editor_deadzone_label)));
        android.widget.SeekBar leftDz = seekBar(working.getLeftDeadZone());
        android.widget.SeekBar rightDz = seekBar(working.getRightDeadZone());
        content.addView(label(getString(R.string.controller_editor_left_deadzone)));
        content.addView(leftDz);
        content.addView(label(getString(R.string.controller_editor_right_deadzone)));
        content.addView(rightDz);

        content.addView(label(getString(R.string.controller_editor_sensitivity_label)));
        android.widget.SeekBar leftSens = seekBarSens(working.getLeftStickSensitivity());
        android.widget.SeekBar rightSens = seekBarSens(working.getRightStickSensitivity());
        content.addView(label(getString(R.string.controller_editor_left_sensitivity)));
        content.addView(leftSens);
        content.addView(label(getString(R.string.controller_editor_right_sensitivity)));
        content.addView(rightSens);

        android.widget.Switch vibration = new android.widget.Switch(requireContext());
        vibration.setChecked(working.isVibrationEnabled());
        content.addView(label(getString(R.string.controller_editor_vibration)));
        content.addView(vibration);

        android.widget.Switch antiDrift = new android.widget.Switch(requireContext());
        antiDrift.setChecked(working.isAntiDriftEnabled());
        content.addView(label(getString(R.string.controller_antidrift_title)));
        content.addView(subLabel(getString(R.string.controller_antidrift_summary)));
        content.addView(antiDrift);

        content.addView(label(getString(R.string.controller_editor_stick_curve_label)));
        CurvePreviewView preview = new CurvePreviewView(requireContext());
        preview.setAccentColor(new PersonalizationManager(requireContext()).getAccentColor());
        preview.setProfile(working);
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));

        // Left stick curve.
        content.addView(label(getString(R.string.controller_editor_left_curve)));
        StickCurve leftCurve = working.getLeftCurve();
        Spinner leftStickPreset = stickPresetSpinner(leftCurve);
        Spinner leftStickKind = stickKindSpinner(leftCurve.getKind());
        SeekBar leftStickExp = exponentBar(leftCurve.getExponent(),
                StickCurve.MIN_EXPONENT, StickCurve.MAX_EXPONENT);
        content.addView(subLabel(getString(R.string.controller_editor_curve_preset)));
        content.addView(leftStickPreset);
        content.addView(subLabel(getString(R.string.controller_editor_curve_kind)));
        content.addView(leftStickKind);
        content.addView(subLabel(getString(R.string.controller_editor_curve_exponent)));
        content.addView(leftStickExp);

        // Right stick curve.
        content.addView(label(getString(R.string.controller_editor_right_curve)));
        StickCurve rightCurve = working.getRightCurve();
        Spinner rightStickPreset = stickPresetSpinner(rightCurve);
        Spinner rightStickKind = stickKindSpinner(rightCurve.getKind());
        SeekBar rightStickExp = exponentBar(rightCurve.getExponent(),
                StickCurve.MIN_EXPONENT, StickCurve.MAX_EXPONENT);
        content.addView(subLabel(getString(R.string.controller_editor_curve_preset)));
        content.addView(rightStickPreset);
        content.addView(subLabel(getString(R.string.controller_editor_curve_kind)));
        content.addView(rightStickKind);
        content.addView(subLabel(getString(R.string.controller_editor_curve_exponent)));
        content.addView(rightStickExp);

        // Left trigger curve.
        content.addView(label(getString(R.string.controller_editor_trigger_label)));
        content.addView(label(getString(R.string.controller_editor_left_trigger)));
        TriggerCurve leftTrigger = working.getLeftTriggerCurve();
        Spinner leftTriggerPreset = triggerPresetSpinner(leftTrigger);
        SeekBar leftTriggerDz = deadZoneBar(leftTrigger.getDeadZone());
        SeekBar leftTriggerExp = exponentBar(leftTrigger.getExponent(),
                TriggerCurve.MIN_EXPONENT, TriggerCurve.MAX_EXPONENT);
        content.addView(subLabel(getString(R.string.controller_editor_trigger_preset)));
        content.addView(leftTriggerPreset);
        content.addView(subLabel(getString(R.string.controller_editor_trigger_deadzone)));
        content.addView(leftTriggerDz);
        content.addView(subLabel(getString(R.string.controller_editor_trigger_exponent)));
        content.addView(leftTriggerExp);

        // Right trigger curve.
        content.addView(label(getString(R.string.controller_editor_right_trigger)));
        TriggerCurve rightTrigger = working.getRightTriggerCurve();
        Spinner rightTriggerPreset = triggerPresetSpinner(rightTrigger);
        SeekBar rightTriggerDz = deadZoneBar(rightTrigger.getDeadZone());
        SeekBar rightTriggerExp = exponentBar(rightTrigger.getExponent(),
                TriggerCurve.MIN_EXPONENT, TriggerCurve.MAX_EXPONENT);
        content.addView(subLabel(getString(R.string.controller_editor_trigger_preset)));
        content.addView(rightTriggerPreset);
        content.addView(subLabel(getString(R.string.controller_editor_trigger_deadzone)));
        content.addView(rightTriggerDz);
        content.addView(subLabel(getString(R.string.controller_editor_trigger_exponent)));
        content.addView(rightTriggerExp);

        // Wire the presets and the live preview. A preset write pushes values into the
        // adjacent kind/exponent controls, so the editor never shows a preset name next to
        // numbers that disagree with it.
        Runnable pushToPreview = () -> {
            // The adapters hold display strings, not enum values, so the selection must be read
            // back by position. Casting getSelectedItem() to the enum throws ClassCastException
            // the first time a spinner fires, which is during the dialog's initial layout.
            working.setLeftCurve(new StickCurve(
                    StickCurve.Kind.values()[leftStickKind.getSelectedItemPosition()],
                    exponentFrom(leftStickExp, StickCurve.MIN_EXPONENT, StickCurve.MAX_EXPONENT)));
            working.setRightCurve(new StickCurve(
                    StickCurve.Kind.values()[rightStickKind.getSelectedItemPosition()],
                    exponentFrom(rightStickExp, StickCurve.MIN_EXPONENT, StickCurve.MAX_EXPONENT)));
            working.setLeftTriggerCurve(new TriggerCurve(
                    deadZoneFrom(leftTriggerDz),
                    exponentFrom(leftTriggerExp, TriggerCurve.MIN_EXPONENT, TriggerCurve.MAX_EXPONENT)));
            working.setRightTriggerCurve(new TriggerCurve(
                    deadZoneFrom(rightTriggerDz),
                    exponentFrom(rightTriggerExp, TriggerCurve.MIN_EXPONENT, TriggerCurve.MAX_EXPONENT)));
            preview.setProfile(working);
        };
        leftStickPreset.setOnItemSelectedListener(primedPresetListener(
                presetListener(leftStickKind, leftStickExp, pushToPreview)));
        rightStickPreset.setOnItemSelectedListener(primedPresetListener(
                presetListener(rightStickKind, rightStickExp, pushToPreview)));
        leftTriggerPreset.setOnItemSelectedListener(primedPresetListener(
                triggerPresetListener(leftTriggerDz, leftTriggerExp, pushToPreview)));
        rightTriggerPreset.setOnItemSelectedListener(primedPresetListener(
                triggerPresetListener(rightTriggerDz, rightTriggerExp, pushToPreview)));
        for (SeekBar bar : new SeekBar[]{leftStickExp, rightStickExp, leftTriggerDz, leftTriggerExp,
                rightTriggerDz, rightTriggerExp}) {
            bar.setOnSeekBarChangeListener(changeListener(pushToPreview));
        }
        for (Spinner spinner : new Spinner[]{leftStickKind, rightStickKind}) {
            spinner.setOnItemSelectedListener(selectionListener(pushToPreview));
        }

        android.widget.ScrollView scroller = new android.widget.ScrollView(requireContext());
        scroller.addView(content);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.65f);
        scroller.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(maxHeight, dp(560))));

        CustomAlertDialog dialog = new CustomAlertDialog(requireContext())
                .setTitleText(getString(R.string.controller_edit))
                .setCustomView(scroller)
                .setPositiveButton(getString(R.string.controller_save), v -> {
                    working.setName(nameInput.getText().toString());
                    working.setLeftDeadZone(leftDz.getProgress() / 100f);
                    working.setRightDeadZone(rightDz.getProgress() / 100f);
                    working.setLeftStickSensitivity(0.25f + leftSens.getProgress() / 100f * 2.75f);
                    working.setRightStickSensitivity(0.25f + rightSens.getProgress() / 100f * 2.75f);
                    working.setVibrationEnabled(vibration.isChecked());
                    working.setAntiDriftEnabled(antiDrift.isChecked());
                    int active = profileManager.getActiveSlot(currentType);
                    List<ControllerProfile> profiles = profileManager.getProfiles(currentType);
                    profiles.set(active, working);
                    profileManager.saveProfiles(currentType, profiles);
                    org.chimeramc.client.ui.animation.UiTouchFeedback.confirm(requireContext());
                    refreshProfiles();
                })
                .setNegativeButton(getString(R.string.controller_cancel), v -> {
                });
        dialog.show();
    }

    private TextView label(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(requireContext().getColor(R.color.on_surface));
        tv.setPadding(0, dp(6), 0, dp(2));
        return tv;
    }

    private android.widget.SeekBar seekBar(float value) {
        android.widget.SeekBar sb = new android.widget.SeekBar(requireContext());
        sb.setMax(100);
        sb.setProgress((int) (value * 100f));
        return sb;
    }

    private android.widget.SeekBar seekBarSens(float value) {
        android.widget.SeekBar sb = new android.widget.SeekBar(requireContext());
        sb.setMax(100);
        float scaled = (value - 0.25f) / 2.75f;
        sb.setProgress((int) (scaled * 100f));
        return sb;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // --- Curve editor helpers -------------------------------------------------------------

    /**
     * Slider resolution for a curve exponent.
     *
     * The useful range of an exponent is small (roughly 0.25..4), so the bar maps 0..100 onto
     * the range rather than exposing raw hundredths; the stored value is quantised to 0.01,
     * which is finer than the eye can tell apart while keeping the profile readable.
     */
    private SeekBar exponentBar(float value, float min, float max) {
        SeekBar sb = new SeekBar(requireContext());
        sb.setMax(100);
        float clamped = Math.max(min, Math.min(max, value));
        sb.setProgress((int) (((clamped - min) / (max - min)) * 100f));
        return sb;
    }

    private float exponentFrom(SeekBar bar, float min, float max) {
        return min + (bar.getProgress() / 100f) * (max - min);
    }

    private SeekBar deadZoneBar(float value) {
        SeekBar sb = new SeekBar(requireContext());
        sb.setMax(100);
        sb.setProgress((int) (TriggerCurve.clampDeadZone(value) * 100f));
        return sb;
    }

    private float deadZoneFrom(SeekBar bar) {
        return bar.getProgress() / 100f;
    }

    private TextView subLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(requireContext().getColor(R.color.text_secondary));
        tv.setPadding(0, dp(4), 0, 0);
        return tv;
    }

    private Spinner stickPresetSpinner(StickCurve curve) {
        Spinner spinner = new Spinner(requireContext());
        String[] names = new String[StickCurve.Preset.values().length];
        for (int i = 0; i < names.length; i++) {
            names[i] = stickPresetName(StickCurve.Preset.values()[i]);
        }
        spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, names));
        StickCurve.Preset match = curve == null ? null : curve.matchingPreset();
        if (match != null) {
            spinner.setSelection(match.ordinal());
        }
        return spinner;
    }

    private Spinner stickKindSpinner(StickCurve.Kind kind) {
        Spinner spinner = new Spinner(requireContext());
        StickCurve.Kind[] kinds = StickCurve.Kind.values();
        String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            names[i] = stickKindName(kinds[i]);
        }
        spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, names));
        spinner.setSelection(kind == null ? 0 : kind.ordinal());
        return spinner;
    }

    private Spinner triggerPresetSpinner(TriggerCurve curve) {
        Spinner spinner = new Spinner(requireContext());
        TriggerCurve.Preset[] presets = TriggerCurve.Preset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            names[i] = triggerPresetName(presets[i]);
        }
        spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, names));
        TriggerCurve.Preset match = curve == null ? null : curve.matchingPreset();
        if (match != null) {
            spinner.setSelection(match.ordinal());
        }
        return spinner;
    }

    private String stickPresetName(StickCurve.Preset preset) {
        switch (preset) {
            case PRECISION: return getString(R.string.controller_preset_precision);
            case BALANCED: return getString(R.string.controller_preset_balanced);
            case AGGRESSIVE: return getString(R.string.controller_preset_aggressive);
            case INSTANT: return getString(R.string.controller_preset_instant);
            case LINEAR:
            default: return getString(R.string.controller_preset_linear);
        }
    }

    private String stickKindName(StickCurve.Kind kind) {
        switch (kind) {
            case EXPONENTIAL: return getString(R.string.controller_curve_kind_exponential);
            case EASE_OUT: return getString(R.string.controller_curve_kind_ease_out);
            case SIGMOID: return getString(R.string.controller_curve_kind_sigmoid);
            case LINEAR:
            default: return getString(R.string.controller_curve_kind_linear);
        }
    }

    private String triggerPresetName(TriggerCurve.Preset preset) {
        switch (preset) {
            case SOFT: return getString(R.string.controller_trigger_preset_soft);
            case HAIR_TRIGGER: return getString(R.string.controller_trigger_preset_hair);
            case DELIBERATE: return getString(R.string.controller_trigger_preset_deliberate);
            case LINEAR:
            default: return getString(R.string.controller_trigger_preset_linear);
        }
    }

    private AdapterView.OnItemSelectedListener presetListener(Spinner kindSpinner,
                                                              SeekBar exponentBar,
                                                              Runnable onChange) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                StickCurve.Preset preset = StickCurve.Preset.values()[position];
                kindSpinner.setSelection(preset.toCurve().getKind().ordinal());
                exponentBar.setProgress(progressFor(preset.toCurve().getExponent(),
                        StickCurve.MIN_EXPONENT, StickCurve.MAX_EXPONENT));
                onChange.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }

    private AdapterView.OnItemSelectedListener triggerPresetListener(SeekBar deadZoneBar,
                                                                     SeekBar exponentBar,
                                                                     Runnable onChange) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TriggerCurve.Preset preset = TriggerCurve.Preset.values()[position];
                TriggerCurve curve = preset.toCurve();
                deadZoneBar.setProgress((int) (curve.getDeadZone() * 100f));
                exponentBar.setProgress(progressFor(curve.getExponent(),
                        TriggerCurve.MIN_EXPONENT, TriggerCurve.MAX_EXPONENT));
                onChange.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }

    /**
     * Swallows the preset spinner's first callback so it cannot clobber a custom curve.
     *
     * A Spinner always fires {@code onItemSelected} once after it is laid out, at whatever
     * position is selected. For a curve that matches a preset that is a harmless no-op, but a
     * custom curve sits on position 0 and the callback would silently reset the shape to the
     * first preset the moment the dialog opens. The controls are initialised from the curve
     * before this listener is attached, so dropping the first callback loses nothing.
     */
    private AdapterView.OnItemSelectedListener primedPresetListener(
            AdapterView.OnItemSelectedListener delegate) {
        return new AdapterView.OnItemSelectedListener() {
            private boolean primed = false;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!primed) {
                    primed = true;
                    return;
                }
                delegate.onItemSelected(parent, view, position, id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                delegate.onNothingSelected(parent);
            }
        };
    }

    private static int progressFor(float value, float min, float max) {
        float clamped = Math.max(min, Math.min(max, value));
        return (int) (((clamped - min) / (max - min)) * 100f);
    }

    private SeekBar.OnSeekBarChangeListener changeListener(Runnable onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onChange.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    private AdapterView.OnItemSelectedListener selectionListener(Runnable onChange) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onChange.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }

    private interface NameCallback {
        void onName(String name);
    }
}
