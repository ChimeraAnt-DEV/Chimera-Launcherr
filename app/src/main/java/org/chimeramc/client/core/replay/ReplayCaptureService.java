package org.chimeramc.client.core.replay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.chimeramc.client.R;

/**
 * The Tier 1 recorder, as a foreground service.
 *
 * <p>Why a service: a {@code MediaProjection} session must own a foreground service with the
 * {@code mediaProjection} type from API 34, and even before that the capture has to survive the
 * player leaving the launcher UI for the game activity. Doing it in the overlay would tie the
 * recording to a window that is not the one being recorded.
 *
 * <p>The service owns exactly one recorder at a time and reports completion through
 * {@link ReplayManager} so the UI has a single state machine. Quality, the clip-length limit and
 * the burn-in fields all come from {@link ReplaySettings}; nothing here invents a value.
 */
public class ReplayCaptureService extends Service {

    private static final String TAG = "ReplayCapture";

    public static final String ACTION_START = "org.chimeramc.client.replay.START";
    public static final String ACTION_STOP = "org.chimeramc.client.replay.STOP";

    private static final String EXTRA_RESULT_CODE = "resultCode";
    private static final String EXTRA_RESULT_DATA = "resultData";

    private static final String CHANNEL_ID = "replay_capture";
    private static final int NOTIFICATION_ID = 0x5250;

    private MediaProjection projection;
    private MediaRecorder recorder;
    private VirtualDisplay virtualDisplay;
    private HandlerThread recorderThread;
    private Handler recorderHandler;

    private java.io.File outputFile;
    private long startedAtMs;
    private int clipLimitMs;
    private boolean recording;

    /** Starts a capture with the permission result the activity just obtained. */
    public static void start(Context context, int resultCode, Intent resultData) {
        Intent intent = new Intent(context, ReplayCaptureService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData);
        try {
            ContextCompat.startForegroundService(context.getApplicationContext(), intent);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to start the replay capture service", t);
            { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFailed(); }
        }
    }

    public static void stop(Context context) {
        try {
            context.getApplicationContext().startService(
                    new Intent(context, ReplayCaptureService.class).setAction(ACTION_STOP));
        } catch (Throwable t) {
            Log.w(TAG, "Unable to signal the replay capture service", t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        recorderThread = new HandlerThread("replay-recorder");
        recorderThread.start();
        recorderHandler = new Handler(recorderThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction())) {
            startForegroundCompat();
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (resultData == null) {
                { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFailed(); }
                stopSelf();
                return START_NOT_STICKY;
            }
            beginCapture(resultCode, resultData);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseCapture();
        if (recorderThread != null) {
            recorderThread.quitSafely();
            recorderThread = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private void beginCapture(int resultCode, Intent resultData) {
        if (recording) return;
        if (ReplayPlaybackGate.isPlaybackActive()) {
            // The device is already decoding a clip in the embedded player; starting an encode now
            // would put two media sessions on the same hardware. Refuse rather than stutter both.
            Log.w(TAG, "Not starting capture while replay playback is active");
            { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFailed(); }
            stopSelf();
            return;
        }
        try {
            ReplaySettings settings = ReplaySettings.get(this);
            ReplayQuality.Profile profile = ReplayQuality.select(
                    settings.forceLowQuality()
                            || ReplayQuality.isLowEnd(readTotalMemory()));
            DisplayMetrics metrics = readDisplayMetrics();

            outputFile = ReplayStorage.uniqueFile(ReplayStorage.replayDir(this),
                    ReplayStorage.newClipName(System.currentTimeMillis()));
            clipLimitMs = settings.clipLimitMinutes() * 60_000;

            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) {
                { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFailed(); }
                stopSelf();
                return;
            }

            recorder = new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(profile.width, profile.height);
            recorder.setVideoFrameRate(profile.frameRate);
            recorder.setVideoEncodingBitRate(profile.bitRate);
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();

            int density = metrics == null ? DisplayMetrics.DENSITY_DEFAULT : metrics.densityDpi;
            virtualDisplay = projection.createVirtualDisplay(
                    "chimera-replay",
                    profile.width,
                    profile.height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder.getSurface(),
                    null, recorderHandler);

            recorder.start();
            startedAtMs = System.currentTimeMillis();
            recording = true;
            { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingStarted(outputFile, profile, startedAtMs); }
            scheduleLimitStop();
        } catch (Throwable t) {
            Log.w(TAG, "Replay capture failed to start", t);
            releaseCapture();
            { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFailed(); }
            stopSelf();
        }
    }

    /** The clip-length limit, enforced on the recorder's own thread so it cannot be missed. */
    private void scheduleLimitStop() {
        if (recorderHandler == null || clipLimitMs <= 0) return;
        recorderHandler.postDelayed(() -> {
            if (recording) stopRecording();
        }, clipLimitMs);
    }

    private void stopRecording() {
        if (!recording) {
            stopSelf();
            return;
        }
        long durationMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        java.io.File finished = outputFile;
        releaseCapture();
        if (finished != null && finished.isFile() && finished.length() > 0L) {
            { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFinished(finished, durationMs); }
        } else {
            { ReplayManager m = ReplayManager.get(); if (m != null) m.onRecordingFailed(); }
        }
        stopSelf();
    }

    private void releaseCapture() {
        recording = false;
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Throwable ignored) {
            }
            virtualDisplay = null;
        }
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Throwable ignored) {
                // stop() throws when no frames were written; the empty file is discarded above.
            }
            try {
                recorder.reset();
                recorder.release();
            } catch (Throwable ignored) {
            }
            recorder = null;
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Throwable ignored) {
            }
            projection = null;
        }
    }

    private long readTotalMemory() {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return 0L;
            android.app.ActivityManager.MemoryInfo info =
                    new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return info.totalMem;
        } catch (Throwable t) {
            return 0L;
        }
    }

    private DisplayMetrics readDisplayMetrics() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return null;
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            return metrics;
        } catch (Throwable t) {
            return null;
        }
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.replay_capture_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, ReplayManager.launcherActivityClass());
        open.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_glowberry_mono)
                .setContentTitle(getString(R.string.replay_capture_notification_title))
                .setContentText(getString(R.string.replay_capture_notification_text))
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }
}
