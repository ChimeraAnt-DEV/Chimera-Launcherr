package org.chimeramc.client.core.voice;

import android.content.Context;
import android.util.Log;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.List;
import java.util.UUID;

/**
 * The proximity voice chat module: transport + registry + audio, driven from one place.
 *
 * <p>It is a launcher-side peer link between Chimera users in the same world. Nothing here talks
 * to the game; the microphone is captured by the launcher, framed by {@link VoiceProtocol}, sent
 * on the LAN multicast group by {@link VoiceTransport}, and played back through
 * {@link VoiceAudioEngine} with the gain {@link VoiceChannel} computes from distance and channel.
 *
 * <p><b>The position seam.</b> Distance needs a world position, which lives in the game process
 * and is not readable from the launcher (see the native-feed notes in AGENTS.md). A
 * {@link PositionSource} is therefore an injection point, exactly like the combat modules'
 * {@code WorldSource}. When none is installed the module does not fabricate a position: it runs
 * in <b>channel mode</b>, where everyone on your channel is audible at full volume because there
 * is no distance to fall off. That is a real, usable mode — it is just not proximity — and the
 * overlay says so rather than pretending a range is being applied.
 */
public final class VoiceChatModule {

    private static final String TAG = "VoiceChatModule";

    /** Supplies the local player's world position, or null when it cannot be read. */
    public interface PositionSource {
        /**
         * @return {x, y, z} in blocks, or null when unavailable.
         */
        float[] read();
    }

    private static volatile VoiceChatModule instance;

    private final Context context;
    private final VoiceRegistry registry = new VoiceRegistry();
    private final String peerId;
    private final String displayName;
    private final VoiceAudioEngine audio = new VoiceAudioEngine();
    private VoiceTransport transport;
    private PositionSource positionSource;
    private Thread beaconThread;
    private volatile boolean running;
    private volatile boolean transmit = true;
    private volatile int sequence;
    private volatile long lastSendMs;
    private volatile String lastError;

    private VoiceChatModule(Context context, String displayName) {
        this.context = context.getApplicationContext();
        this.peerId = UUID.randomUUID().toString().substring(0, 8);
        this.displayName = displayName == null || displayName.trim().isEmpty()
                ? "Chimera" : displayName.trim();
    }

    public static synchronized VoiceChatModule get(Context context, String displayName) {
        if (instance == null) {
            instance = new VoiceChatModule(context, displayName);
        }
        return instance;
    }

    public static VoiceChatModule peek() {
        return instance;
    }

    public static void setPositionSource(PositionSource source) {
        VoiceChatModule current = instance;
        if (current != null) current.positionSource = source;
    }

    /** The device name other players see, so a peer list is not a wall of hex ids. */
    public String displayName() {
        return displayName;
    }

    public String peerId() {
        return peerId;
    }

    public VoiceRegistry registry() {
        return registry;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isTransmitting() {
        return transmit;
    }

    public String lastError() {
        return lastError;
    }

    /** True while no position feed is installed, so distance cannot be applied. */
    public boolean isChannelMode() {
        return positionSource == null;
    }

    /**
     * Starts the link: opens the socket and the audio device, then beacons the local state.
     *
     * <p>Audio is started with the microphone only when transmitting. A listen-only client still
     * opens playback, so it hears everyone without ever holding the recorder.
     */
    public synchronized boolean start(InbuiltModManager manager) {
        if (running) return true;
        if (manager == null) return false;
        transmit = manager.isVoiceMicEnabled();

        if (!audio.start(transmit)) {
            if (transmit) {
                // The mic could not be opened (usually a revoked permission); fall back to
                // listen-only rather than failing the whole module.
                transmit = false;
                if (!audio.start(false)) {
                    lastError = "audio device unavailable";
                    return false;
                }
            } else {
                lastError = "audio device unavailable";
                return false;
            }
        }
        audio.setFrameSink(this::onCapturedFrame);
        audio.setVolumePercent(manager.getVoiceVolumePercent());

        transport = new VoiceTransport(context, new VoiceTransport.Listener() {
            @Override
            public void onDatagram(byte[] data, int length) {
                VoiceChatModule.this.onDatagram(data, length);
            }

            @Override
            public void onStopped(String reason) {
                lastError = reason;
            }
        });
        if (!transport.start()) {
            audio.stop();
            transport = null;
            lastError = "could not open the network";
            return false;
        }

        running = true;
        beaconThread = new Thread(this::beaconLoop, "voice-beacon");
        beaconThread.setDaemon(true);
        beaconThread.start();
        return true;
    }

    public synchronized void stop() {
        if (!running) {
            audio.stop();
            return;
        }
        running = false;
        sendBye();
        Thread beacon = beaconThread;
        beaconThread = null;
        if (beacon != null) {
            try {
                beacon.join(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (transport != null) {
            transport.stop();
            transport = null;
        }
        audio.stop();
        registry.clear();
    }

    public void applyConfig(InbuiltModManager manager) {
        if (manager == null) return;
        boolean wanted = manager.isVoiceMicEnabled();
        if (wanted && running) {
            // Turning the mic on after a listen-only start has to open the recorder, not just
            // flip a flag in front of a capture loop that was never started.
            transmit = audio.ensureCapture();
        } else {
            transmit = false;
            if (running) audio.stopCapture();
        }
        audio.setMicEnabled(transmit);
        audio.setVolumePercent(manager.getVoiceVolumePercent());
    }

    /** The listener's channel, pushed into every outgoing packet so peers can filter. */
    private String currentChannel() {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        return manager == null
                ? VoiceChannel.WORLD
                : manager.getVoiceChannel();
    }

    private float currentRange() {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        return manager == null ? 24f : manager.getVoiceRangeBlocks();
    }

    private void beaconLoop() {
        while (running) {
            try {
                sendBeacon();
                registry.evictStale(android.os.SystemClock.uptimeMillis());
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendBeacon() {
        VoiceTransport active = transport;
        if (active == null) return;
        float[] position = localPosition();
        active.send(VoiceProtocol.encodeBeacon(peerId, displayName, currentChannel(),
                position[0], position[1], position[2], sequence++));
    }

    private void sendBye() {
        VoiceTransport active = transport;
        if (active == null) return;
        float[] position = localPosition();
        active.send(VoiceProtocol.encodeBye(peerId, displayName, currentChannel(),
                position[0], position[1], position[2]));
    }

    /** The local position, or the origin when no source is installed (channel mode). */
    private float[] localPosition() {
        PositionSource source = positionSource;
        if (source == null) return new float[]{0f, 0f, 0f};
        try {
            float[] value = source.read();
            return value != null && value.length >= 3 ? value : new float[]{0f, 0f, 0f};
        } catch (Throwable t) {
            return new float[]{0f, 0f, 0f};
        }
    }

    private void onCapturedFrame(byte[] pcm, int length) {
        VoiceTransport active = transport;
        if (active == null || !running) return;
        long now = android.os.SystemClock.uptimeMillis();
        lastSendMs = now;
        float[] position = localPosition();
        active.send(VoiceProtocol.encodeAudio(peerId, displayName, currentChannel(),
                position[0], position[1], position[2], sequence++, pcm));
    }

    private void onDatagram(byte[] data, int length) {
        VoiceProtocol.Packet packet = VoiceProtocol.decode(data);
        if (packet == null) return;
        if (peerId.equals(packet.peerId)) return; // our own echo

        long now = android.os.SystemClock.uptimeMillis();
        if (packet.type == VoiceProtocol.TYPE_BYE) {
            registry.remove(packet.peerId);
            return;
        }

        String channel = VoiceChannel.normalize(packet.channel);
        if (packet.type == VoiceProtocol.TYPE_BEACON) {
            registry.put(new VoicePeer(packet.peerId, packet.name,
                    packet.x, packet.y, packet.z, channel, now));
            return;
        }

        if (packet.type == VoiceProtocol.TYPE_AUDIO) {
            registry.put(new VoicePeer(packet.peerId, packet.name,
                    packet.x, packet.y, packet.z, channel, now));
            String listenerChannel = currentChannel();
            float gain;
            if (isChannelMode()) {
                gain = VoiceChannel.canHear(listenerChannel, channel) ? 1f : 0f;
            } else {
                float[] position = localPosition();
                float distance = (float) Math.sqrt(
                        sq(packet.x - position[0]) + sq(packet.y - position[1]) + sq(packet.z - position[2]));
                gain = VoiceChannel.gain(distance, currentRange(), listenerChannel, channel);
            }
            audio.play(packet.payload, packet.payload.length, gain);
        }
    }

    private static float sq(float value) {
        return value * value;
    }

    /** Peers currently audible, for the overlay. */
    public List<VoiceRegistry.Audible> audibleNow() {
        float[] position = localPosition();
        if (!isChannelMode()) {
            return registry.audible(position[0], position[1], position[2],
                    currentChannel(), currentRange());
        }
        // Channel mode: no positions, so everyone on a reachable channel counts as in range.
        List<VoiceRegistry.Audible> reachable = new java.util.ArrayList<>();
        for (VoicePeer peer : registry.peers()) {
            if (VoiceChannel.canHear(currentChannel(), peer.channel)) {
                reachable.add(new VoiceRegistry.Audible(peer, 1f));
            }
        }
        return reachable;
    }
}
