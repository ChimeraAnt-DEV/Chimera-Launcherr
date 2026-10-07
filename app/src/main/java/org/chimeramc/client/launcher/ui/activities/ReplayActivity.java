package org.chimeramc.client.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chimeramc.client.R;
import org.chimeramc.client.core.replay.ReplayPanel;

/**
 * The launcher-side Replay screen, reached from the Launch tab's Replay card.
 *
 * <p>The Replay feature's only other homes are the two in-game Mod Menu screens, which exist only
 * during a session. This screen hosts the same {@link ReplayPanel} the Mod Menu hosts, so the clip
 * library can be browsed, played, trimmed and exported from the launcher itself -- there is no
 * second clip list, sort or export implementation.
 *
 * <p>The panel owns a background executor and a live recorder listener, so it is disposed in
 * {@link #onDestroy()} rather than merely dropped; {@code onShown}/{@code onHidden} bracket the
 * live refresh around the screen's visibility.
 */
public class ReplayActivity extends BaseActivity {

    private ReplayPanel replayPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background_dark));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = new TextView(this);
        title.setText(R.string.launch_toggle_replay);
        title.setTextColor(getColor(R.color.on_surface));
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        FrameLayout container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        replayPanel = new ReplayPanel(this, false);
        container.addView(replayPanel.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        replayPanel.setGamepadDetected(hasConnectedGamepad());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (replayPanel != null) replayPanel.onShown();
    }

    @Override
    protected void onPause() {
        if (replayPanel != null) replayPanel.onHidden();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (replayPanel != null) {
            replayPanel.dispose();
            replayPanel = null;
        }
        super.onDestroy();
    }

    private boolean hasConnectedGamepad() {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
                    || (sources & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
