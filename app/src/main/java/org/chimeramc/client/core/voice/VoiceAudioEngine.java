package org.chimeramc.client.core.voice;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Microphone capture and speaker playback for proximity voice.
 *
 * <p>Raw PCM, not an Opus codec: at 16&nbsp;kHz mono a 20&nbsp;ms frame is 640 bytes, which fits a
 * multicast datagram several times over on a LAN, and it needs no per-ABI codec build. The trade
 * is bandwidth, and on the local network that two players in one Bedrock world share it simply
 * does not matter. {@link VoiceProtocol} carries the frame; this class is only the device half.
 *
 * <p>Capture and playback are independent: a listen-only client (mic off) never opens the
 * recorder, and a transmit-only client still gets its frames sent. Both directions run on their
 * own thread and are stopped by {@link #stop()}.
 */
public final class VoiceAudioEngine {

    private static final String TAG = "VoiceAudioEngine";

    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_MS = 20;
    public static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;
    public static final int FRAME_BYTES = FRAME_SAMPLES * 2; // 16-bit mono

    /** Frames whose peak is below this are silence and are not transmitted. */
    private static final int SILENCE_PEAK = 220;

    /** What a captured frame triggers; runs on the capture thread. */
    public interface FrameSink {
        void onFrame(byte[] pcm, int length);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger volumePercent = new AtomicInteger(100);
    private AudioRecord record;
    private AudioTrack track;
    private Thread captureThread;
    private Thread playThread;
    private FrameSink sink;
    private volatile boolean micEnabled = true;

    /** Opens the device; returns false when a capture or playback stream cannot be created. */
    public boolean start(boolean transmit) {
        if (running.get()) return true;
        micEnabled = transmit;

        if (transmit && !openCapture()) {
            return false;
        }
        if (!openPlayback()) {
            closeCapture();
            return false;
        }
        running.set(true);

        if (transmit) {
            captureThread = new Thread(this::captureLoop, "voice-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        }
        playThread = new Thread(this::playLoop, "voice-playback");
        playThread.setDaemon(true);
        playThread.start();
        return true;
    }

    public void setFrameSink(FrameSink sink) {
        this.sink = sink;
    }

    public void setMicEnabled(boolean enabled) {
        micEnabled = enabled;
    }

    public boolean isMicEnabled() {
        return micEnabled;
    }

    /**
     * Opens the microphone and the capture thread if they are not already running.
     *
     * <p>Called when the player turns their mic on after the session started listen-only — either
     * because they toggled it, or because the record permission was granted after the fact. A
     * plain {@link #setMicEnabled} only gates an already-running capture loop, so without this the
     * switch would flip and no audio would ever be sent.
     *
     * @return true when capture is running after the call
     */
    public synchronized boolean ensureCapture() {
        if (captureThread != null && captureThread.isAlive()) return true;
        if (!running.get()) return false;
        if (!openCapture()) return false;
        micEnabled = true;
        captureThread = new Thread(this::captureLoop, "voice-capture");
        captureThread.setDaemon(true);
        captureThread.start();
        return true;
    }

    /** Stops only the capture side, leaving playback running (listen-only). */
    public synchronized void stopCapture() {
        micEnabled = false;
        // Closing the record makes the capture loop observe a null device and exit on its own.
        closeCapture();
        join(captureThread);
        captureThread = null;
    }

    public void setVolumePercent(int percent) {
        volumePercent.set(Math.max(0, Math.min(200, percent)));
    }

    private boolean openCapture() {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) minBuffer = FRAME_BYTES * 4;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, FRAME_BYTES * 4));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                closeCapture();
                return false;
            }
            record.startRecording();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Could not open the microphone", t);
            closeCapture();
            return false;
        }
    }

    private boolean openPlayback() {
        try {
            int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) minBuffer = FRAME_BYTES * 8;
            track = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, FRAME_BYTES * 8),
                    AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                closePlayback();
                return false;
            }
            track.play();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Could not open the speaker", t);
            closePlayback();
            return false;
        }
    }

    private void captureLoop() {
        byte[] frame = new byte[FRAME_BYTES];
        while (running.get()) {
            AudioRecord active = record;
            if (active == null) break;
            int read;
            try {
                read = active.read(frame, 0, frame.length);
            } catch (Throwable t) {
                break;
            }
            if (read <= 0) continue;
            if (!micEnabled) continue;
            if (isSilent(frame, read)) continue;
            FrameSink currentSink = sink;
            if (currentSink != null) {
                byte[] copy = new byte[read];
                System.arraycopy(frame, 0, copy, 0, read);
                currentSink.onFrame(copy, read);
            }
        }
    }

    /** Peak-sample test so a muted room does not stream constant silence to every peer. */
    static boolean isSilent(byte[] pcm, int length) {
        int peak = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int magnitude = Math.abs(sample);
            if (magnitude > peak) peak = magnitude;
            if (peak > SILENCE_PEAK) return false;
        }
        return true;
    }

    /**
     * Writes one received frame to the speaker, scaled by the caller-supplied gain.
     *
     * <p>Called from the transport thread, so the write must tolerate a stream that has not
     * finished opening: {@code write} on an uninitialised track returns a negative error rather
     * than throwing, which is handled here.
     */
    public void play(byte[] pcm, int length, float gain) {
        AudioTrack active = track;
        if (active == null || pcm == null || length <= 0) return;
        float scaled = gain * (volumePercent.get() / 100f);
        if (scaled <= 0f) return;
        length = Math.min(length, pcm.length);
        if (scaled >= 0.999f) {
            try {
                active.write(pcm, 0, length);
            } catch (Throwable ignored) {
            }
            return;
        }
        byte[] mixed = new byte[length];
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int out = Math.round(sample * Math.min(scaled, 1f));
            out = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, out));
            mixed[i] = (byte) (out & 0xFF);
            mixed[i + 1] = (byte) ((out >> 8) & 0xFF);
        }
        try {
            active.write(mixed, 0, length);
        } catch (Throwable ignored) {
        }
    }

    private void playLoop() {
        // Playback is driven by incoming frames, not by this loop; it only keeps the thread
        // alive so stop() has something to join and the track is not torn down underneath a
        // concurrent write.
        while (running.get()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            closeCapture();
            closePlayback();
            return;
        }
        closeCapture();
        closePlayback();
        join(captureThread);
        captureThread = null;
        join(playThread);
        playThread = null;
    }

    private static void join(Thread thread) {
        if (thread == null) return;
        try {
            thread.join(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeCapture() {
        AudioRecord active = record;
        record = null;
        if (active == null) return;
        try {
            if (active.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                active.stop();
            }
        } catch (Throwable ignored) {
        }
        active.release();
    }

    private void closePlayback() {
        AudioTrack active = track;
        track = null;
        if (active == null) return;
        try {
            active.stop();
        } catch (Throwable ignored) {
        }
        active.release();
    }
}
