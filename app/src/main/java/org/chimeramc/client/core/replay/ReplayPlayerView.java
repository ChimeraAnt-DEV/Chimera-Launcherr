package org.chimeramc.client.core.replay;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.chimeramc.client.R;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.io.File;

/**
 * The embedded clip player: a surface plus transport controls, living inside the Replay overlay.
 *
 * <p>Why a {@link SurfaceView} and {@link MediaPlayer} rather than an external player: the spec
 * requires watching a clip to stay in-game. The old path fired {@code ACTION_VIEW}, which handed
 * the file to whatever video player was installed and backgrounded Minecraft. This view plays the
 * clip in place, so the game is never left.
 *
 * <p>It is deliberately a plain platform player, not ExoPlayer: the launcher already ships no
 * media library, and pulling one in for a single overlay is a large dependency. The trade is that
 * this decodes one clip at a time — which is exactly what the overlay needs, and
 * {@link ReplayPlaybackGate} stops the recorder from competing with it.
 *
 * <p>Controls: play/pause, a scrub bar with a time readout, a 1x/1.5x/2x speed toggle and a close
 * button that returns to the library grid. All of them are inside the same overlay window.
 */
public final class ReplayPlayerView {

    /** Receives transport and lifecycle events; all run on the main thread. */
    public interface Callbacks {
        /** The close button was pressed; the host should tear the player down. */
        void onClose();

        /** Playback is actually running (started or resumed); latch the playback gate. */
        void onPlaybackStarted();

        /** Playback stopped (paused, completed or released); release the playback gate. */
        void onPlaybackEnded();
    }

    /** Playback speed steps, cycled by the speed button. */
    private static final float[] SPEEDS = {1.0f, 1.5f, 2.0f};

    private final Activity activity;
    private final ReplayStyle style;
    private final Callbacks callbacks;

    private final FrameLayout root;
    private final SurfaceView surface;
    private TextView title;
    private TextView playPause;
    private TextView speedButton;
    private SeekBar seekBar;
    private TextView positionText;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private ReplayClip clip;
    private int speedIndex;
    private boolean prepared;
    private boolean playing;
    /** Whether the gate is currently latched by this view, so callbacks alternate. */
    private boolean gateLatched;
    /** True while the user drags the scrub bar, so the tick does not fight the thumb. */
    private boolean scrubbing;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (playing) mainHandler.postDelayed(this, 250L);
        }
    };

    public ReplayPlayerView(Activity activity, ReplayStyle style, ReplayClip clip,
                            Callbacks callbacks) {
        this.activity = activity;
        this.style = style;
        this.callbacks = callbacks;

        root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(activity);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        root.addView(buildTopBar(), topBarParams());
        root.addView(buildControls(), controlsParams());

        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                attachSurface(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                attachSurface(holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (player != null) {
                    try {
                        player.setDisplay(null);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });

        // The user may tap the video area to toggle playback, matching every video player.
        surface.setOnClickListener(v -> togglePlayPause());
    }

    public View getView() {
        return root;
    }

    /** Starts playing {@code clip}, replacing any previous one. */
    public void show(ReplayClip clip) {
        this.clip = clip;
        title.setText(clip == null ? "" : clip.displayName());
        releasePlayer();
        prepared = false;
        playing = false;
        speedIndex = 0;
        updateSpeedLabel();
        updateProgress();
        if (clip == null) return;
        startPlayer(clip.file());
    }

    /** Stops playback and releases all media resources. Safe to call repeatedly. */
    public void release() {
        mainHandler.removeCallbacks(progressTick);
        releasePlayer();
        prepared = false;
        playing = false;
        unlatchGate();
    }

    // ---------------------------------------------------------------------------------------------

    private void startPlayer(File file) {
        if (file == null || !file.isFile()) {
            Toast.makeText(activity, R.string.replay_playback_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.setLooping(false);
                applySpeed();
                attachSurface(surface.getHolder());
                startPlayback();
            });
            player.setOnCompletionListener(mp -> {
                playing = false;
                unlatchGate();
                playPause.setText("▶");
                updateProgress();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                playing = false;
                unlatchGate();
                Toast.makeText(activity, R.string.replay_playback_hint, Toast.LENGTH_SHORT)
                        .show();
                return true;
            });
            player.prepareAsync();
        } catch (Throwable t) {
            Toast.makeText(activity, R.string.replay_playback_hint, Toast.LENGTH_SHORT).show();
        }
    }

    private void attachSurface(SurfaceHolder holder) {
        if (player == null || holder == null || !prepared) return;
        try {
            player.setDisplay(holder);
        } catch (Throwable ignored) {
        }
    }

    private void togglePlayPause() {
        if (player == null || !prepared) return;
        if (playing) {
            player.pause();
            playing = false;
            unlatchGate();
            playPause.setText("▶");
            mainHandler.removeCallbacks(progressTick);
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (player == null || !prepared) return;
        try {
            player.start();
        } catch (Throwable t) {
            return;
        }
        playing = true;
        playPause.setText("⏸");
        latchGate();
        mainHandler.removeCallbacks(progressTick);
        mainHandler.post(progressTick);
    }

    /** Re-seeks the current speed after a start, since some decoders reset the params. */
    private void applySpeed() {
        if (player == null) return;
        try {
            android.media.PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(SPEEDS[speedIndex]);
            player.setPlaybackParams(params);
        } catch (Throwable ignored) {
            // Pre-N decoders may not support speed; playback stays at 1x.
        }
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        applySpeed();
        updateSpeedLabel();
    }

    private void updateSpeedLabel() {
        speedButton.setText(SPEEDS[speedIndex] == 1.0f
                ? activity.getString(R.string.replay_player_speed_1x)
                : activity.getString(R.string.replay_player_speed, SPEEDS[speedIndex]));
    }

    private void updateProgress() {
        if (player == null || !prepared) return;
        int duration = player.getDuration();
        int position = player.getCurrentPosition();
        if (duration > 0) {
            seekBar.setMax(duration);
            if (!scrubbing) seekBar.setProgress(position);
            positionText.setText(ReplayFormat.duration(position) + " / "
                    + ReplayFormat.duration(duration));
        }
    }

    private void releasePlayer() {
        mainHandler.removeCallbacks(progressTick);
        if (player != null) {
            try {
                player.setDisplay(null);
            } catch (Throwable ignored) {
            }
            try {
                player.reset();
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
    }

    private void latchGate() {
        if (gateLatched) return;
        gateLatched = true;
        if (callbacks != null) callbacks.onPlaybackStarted();
    }

    private void unlatchGate() {
        if (!gateLatched) return;
        gateLatched = false;
        if (callbacks != null) callbacks.onPlaybackEnded();
    }

    // ---------------------------------------------------------------------------------------------
    // Control construction
    // ---------------------------------------------------------------------------------------------

    private FrameLayout.LayoutParams topBarParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        return params;
    }

    private FrameLayout.LayoutParams controlsParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        return params;
    }

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(ReplayStyle.withAlpha(style.canvas(), 0xCC));
        int pad = ReplayStyle.dpInt(activity, 12);
        row.setPadding(pad, ReplayStyle.dpInt(activity, 8), pad, ReplayStyle.dpInt(activity, 8));

        title = new TextView(activity);
        title.setTextColor(style.textPrimary());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(activity);
        close.setText(R.string.replay_player_close);
        close.setTextColor(style.textPrimary());
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        close.setTypeface(null, Typeface.BOLD);
        close.setBackground(ReplayStyle.rippled(
                ReplayStyle.pill(style.surfaceElevated(), activity), style.accentFill(60), activity));
        close.setPadding(ReplayStyle.dpInt(activity, 12), ReplayStyle.dpInt(activity, 6),
                ReplayStyle.dpInt(activity, 12), ReplayStyle.dpInt(activity, 6));
        close.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onClose();
        });
        DynamicAnim.applyPressScale(close);
        row.addView(close);
        return row;
    }

    private View buildControls() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(ReplayStyle.withAlpha(style.canvas(), 0xCC));
        int pad = ReplayStyle.dpInt(activity, 12);
        column.setPadding(pad, ReplayStyle.dpInt(activity, 8), pad, ReplayStyle.dpInt(activity, 8));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        playPause = new TextView(activity);
        playPause.setText("⏸");
        playPause.setTextColor(style.textPrimary());
        playPause.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        playPause.setGravity(Gravity.CENTER);
        playPause.setBackground(ReplayStyle.rippled(
                ReplayStyle.rounded(style.surfaceElevated(), 10f, activity),
                style.accentFill(60), activity));
        playPause.setPadding(ReplayStyle.dpInt(activity, 12), ReplayStyle.dpInt(activity, 4),
                ReplayStyle.dpInt(activity, 12), ReplayStyle.dpInt(activity, 4));
        playPause.setOnClickListener(v -> togglePlayPause());
        DynamicAnim.applyPressScale(playPause);
        row.addView(playPause);

        seekBar = new SeekBar(activity);
        seekBar.setPadding(ReplayStyle.dpInt(activity, 8), 0, ReplayStyle.dpInt(activity, 8), 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null && prepared) {
                    player.seekTo(progress);
                    positionText.setText(ReplayFormat.duration(progress) + " / "
                            + ReplayFormat.duration(player.getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                scrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                scrubbing = false;
            }
        });
        row.addView(seekBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        positionText = new TextView(activity);
        positionText.setTextColor(style.textSecondary());
        positionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        positionText.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMarginStart(ReplayStyle.dpInt(activity, 8));
        row.addView(positionText, timeParams);

        speedButton = new TextView(activity);
        speedButton.setTextColor(style.textPrimary());
        speedButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        speedButton.setTypeface(null, Typeface.BOLD);
        speedButton.setBackground(ReplayStyle.rippled(
                ReplayStyle.pill(style.surfaceElevated(), activity), style.accentFill(60), activity));
        speedButton.setPadding(ReplayStyle.dpInt(activity, 10), ReplayStyle.dpInt(activity, 5),
                ReplayStyle.dpInt(activity, 10), ReplayStyle.dpInt(activity, 5));
        speedButton.setOnClickListener(v -> cycleSpeed());
        DynamicAnim.applyPressScale(speedButton);
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        speedParams.setMarginStart(ReplayStyle.dpInt(activity, 8));
        row.addView(speedButton, speedParams);

        column.addView(row);
        return column;
    }
}
