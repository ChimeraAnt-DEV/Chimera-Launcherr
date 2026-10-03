package org.chimeramc.client.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.core.content.CapeInGameInstaller;
import org.chimeramc.client.core.content.InGamePackChanger;
import org.chimeramc.client.launcher.core.content.CosmeticsDiagnostics;
import org.chimeramc.client.launcher.core.content.CosmeticsDiagnostics.Check;
import org.chimeramc.client.launcher.core.content.CosmeticsDiagnostics.Status;
import org.chimeramc.client.util.LauncherStorage;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports which link of the cape pipeline is broken, on the device.
 *
 * <p>Capes can fail silently (see {@code CapeResourcePackBuilder}'s own note), so this screen
 * turns the silent failure into named checks, offers a magenta test cape that cannot be missed if
 * the renderer honours the override, and a copy button for the report. Until a check confirms the
 * failing link, cosmetics must not be promised in release notes.
 */
public class CosmeticsStatusActivity extends BaseActivity {

    /** A solid magenta stands out against any skin, so "did anything appear" is unambiguous. */
    private static final CosmeticCatalog.Cape TEST_CAPE =
            new CosmeticCatalog.Cape("test_magenta", "Test Cape", 0xFFFF00FF, 0xFFFFFFFF, 0xFFFFFFFF,
                    CosmeticCatalog.CapePattern.SOLID, false, false);

    private LinearLayout checksContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String lastReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cosmetics_status);
        DynamicAnim.applyPressScaleRecursively(findViewById(android.R.id.content));
        checksContainer = findViewById(R.id.cosmetics_checks_container);

        Button testCape = findViewById(R.id.cosmetics_test_cape_button);
        if (testCape != null) testCape.setOnClickListener(v -> installTestCape());
        Button copyReport = findViewById(R.id.cosmetics_copy_report_button);
        if (copyReport != null) copyReport.setOnClickListener(v -> copyReport());
        Button refresh = findViewById(R.id.cosmetics_refresh_button);
        if (refresh != null) refresh.setOnClickListener(v -> runChecks());

        runChecks();
    }

    private void runChecks() {
        VersionManager manager = VersionManager.get(this);
        GameVersion version = manager == null ? null : manager.getSelectedVersion();
        List<File> roots = candidateRoots(version);
        File stagingRoot = getExternalFilesDir(null) != null
                ? getExternalFilesDir(null) : getFilesDir();
        String gameVersion = version == null ? null : version.versionCode;

        List<Check> checks = CosmeticsDiagnostics.run(roots, stagingRoot, gameVersion);
        lastReport = CosmeticsDiagnostics.report(checks);
        renderChecks(checks);
    }

    private List<File> candidateRoots(GameVersion version) {
        if (version == null) return new ArrayList<>();
        return LauncherStorage.getCandidateGameDataDirs(
                this, version.getStorageProfileId(), version.versionIsolation);
    }

    private void renderChecks(List<Check> checks) {
        if (checksContainer == null) return;
        checksContainer.removeAllViews();
        for (Check check : checks) {
            checksContainer.addView(buildRow(check));
        }
    }

    private View buildRow(Check check) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(params);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText(verdictLabel(check.status));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11f);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(dp(10));
        pill.setColor(verdictColor(check.status));
        badge.setBackground(pill);
        header.addView(badge);

        TextView label = new TextView(this);
        label.setText(check.label);
        label.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        label.setTextSize(15f);
        label.setPadding(dp(10), 0, 0, 0);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(label);
        row.addView(header);

        TextView detail = new TextView(this);
        detail.setText(check.detail);
        detail.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
        detail.setTextSize(13f);
        detail.setPadding(0, dp(4), 0, 0);
        row.addView(detail);
        return row;
    }

    private String verdictLabel(Status status) {
        switch (status) {
            case OK: return getString(R.string.cosmetics_status_ok);
            case FAIL: return getString(R.string.cosmetics_status_fail);
            default: return getString(R.string.cosmetics_status_manual);
        }
    }

    private int verdictColor(Status status) {
        switch (status) {
            case OK: return 0xFF2FBF71;
            case FAIL: return 0xFFE0455A;
            default: return 0xFFB08300;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Installs the obvious magenta cape so the renderer link can be decided by eye. */
    private void installTestCape() {
        VersionManager manager = VersionManager.get(this);
        GameVersion version = manager == null ? null : manager.getSelectedVersion();
        if (version == null) {
            Toast.makeText(this, R.string.cosmetics_no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        List<File> roots = candidateRoots(version);
        File stagingRoot = getExternalFilesDir(null) != null
                ? getExternalFilesDir(null) : getFilesDir();

        Toast.makeText(this, R.string.skins_loading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final InGamePackChanger.ApplyOutcome outcome =
                    CapeInGameInstaller.install(stagingRoot, roots, TEST_CAPE);
            mainHandler.post(() -> {
                if (outcome == InGamePackChanger.ApplyOutcome.FAILED) {
                    Toast.makeText(this, R.string.cosmetics_test_cape_failed,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.cosmetics_test_cape_applied,
                            Toast.LENGTH_LONG).show();
                }
                runChecks();
            });
        }).start();
    }

    private void copyReport() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("cosmetics-status", lastReport));
        Toast.makeText(this, R.string.cosmetics_report_copied, Toast.LENGTH_SHORT).show();
    }
}
