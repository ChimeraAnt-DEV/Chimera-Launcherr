package org.chimeramc.client.core.voice;

import android.content.Context;
import android.util.Log;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.ArrayList;
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
    private final VoiceMutes mutes = new VoiceMutes();
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
        // Restore the local mute set once, at construction. The set never goes on the wire, so
        // this is the only place it needs loading; toggles persist on the way out.
        InbuiltModManager manager = InbuiltModManager.getInstance(this.context);
        if (manager != null) manager.loadVoiceMutes(mutes);
    }

    public static synchronized VoiceChatModule get(Context context, String displayName) {
        if (instance == null) {
            instance = new VoiceChatModule(context, displayName);
        }
        return instance;
    }

    /**
     * Returns the existing module, or creates an idle one with the stored display name.
     *
     * <p>Used by the launcher-side Voice screen, which must be able to start the link itself --
     * during a game the in-game overlay {@link #get} creates the singleton, but from the launcher
     * nothing else has. The module is inert until {@link #start} is called.
     */
    public static synchronized VoiceChatModule getPreferred(Context context) {
        if (instance == null) {
            String name = null;
            try {
                name = android.os.Build.MODEL;
            } catch (Throwable ignored) {
            }
            instance = new VoiceChatModule(context, name);
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

    /** The channel's advertised display name; "" on the open, unnamed channel. */
    private String currentChannelName() {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        return manager == null ? "" : manager.getVoiceChannelName();
    }

    /** The channel's advertised visibility byte. */
    private byte currentChannelVisibility() {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        return manager == null
                ? VoiceProtocol.VISIBILITY_PUBLIC
                : manager.getVoiceChannelVisibility();
    }

    private float currentRange() {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        return manager == null ? 24f : manager.getVoiceRangeBlocks();
    }

    /** The advertised capacity for the current channel; private channels advertise none. */
    private int currentChannelCapacity() {
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        return manager == null ? VoiceProtocol.CAPACITY_NONE : manager.getVoiceChannelCapacity();
    }

    /** The local mic level to advertise: live while transmitting, 0 while listen-only. */
    private float localLevel() {
        return transmit ? audio.currentLevel() : 0f;
    }

    /** Whether the local mic is currently off, so peers can draw the muted icon immediately. */
    private boolean localSelfMuted() {
        return !transmit;
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
                currentChannelVisibility(), currentChannelName(), currentChannelCapacity(),
                localLevel(), localSelfMuted(),
                position[0], position[1], position[2], sequence++));
    }

    private void sendBye() {
        VoiceTransport active = transport;
        if (active == null) return;
        float[] position = localPosition();
        active.send(VoiceProtocol.encodeBye(peerId, displayName, currentChannel(),
                currentChannelVisibility(), currentChannelName(),
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
                currentChannelVisibility(), currentChannelName(), currentChannelCapacity(),
                localLevel(), localSelfMuted(),
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
        VoicePeer peer = new VoicePeer(packet.peerId, packet.name,
                packet.x, packet.y, packet.z, channel, packet.channelName, packet.visibility,
                packet.capacity, packet.level, packet.muted, now);
        if (packet.type == VoiceProtocol.TYPE_BEACON) {
            registry.put(peer);
            return;
        }

        if (packet.type == VoiceProtocol.TYPE_AUDIO) {
            registry.put(peer);
            // A peer the listener muted locally is not mixed in; they are never told. The beacon
            // they keep sending already carries their self-mute state, so this only has to cover
            // the local mute. Their self-mute needs no handling here: the microphone being off
            // means no audio frames arrive at all.
            if (mutes.isMuted(packet.peerId)) {
                return;
            }
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

    /** The public channels peers are currently on, for the directory screen. */
    public List<VoiceChannelDirectory.Channel> publicChannels() {
        return VoiceChannelDirectory.build(registry.snapshot(), currentChannel());
    }

    /** The peers on the local channel, for the "current channel" member list. */
    public List<VoicePeer> channelMembers() {
        return VoiceChannelDirectory.membersOf(registry.snapshot(), currentChannel());
    }

    /**
     * The peers the listener can currently hear, for the in-world nametag icons.
     *
     * <p>Unlike {@link #channelMembers()} this includes peers on a different but audible
     * channel (the open channel reaches across), which is exactly the set whose icons must
     * be drawn. The caller filters further per tag.
     */
    public List<VoicePeer> audiblePeers() {
        return new ArrayList<>(registry.snapshot());
    }

    /**
     * The most recent advertisement for one peer, or null when it is not currently heard.
     *
     * <p>The nametag icon needs the level/mute of a peer that may be on a <em>different</em> but
     * audible channel (the open channel reaches across, and a world listener hears everyone), so
     * looking it up by id is required; {@link #channelMembers()} would miss those.
     */
    public VoicePeer findPeer(String peerId) {
        if (peerId == null) return null;
        for (VoicePeer peer : registry.snapshot()) {
            if (peerId.equals(peer.id)) return peer;
        }
        return null;
    }

    // --- Per-member client-side mute ------------------------------------------------------

    /** The local per-member mute set. Viewer-only; nothing in it is ever transmitted. */
    public VoiceMutes mutes() {
        return mutes;
    }

    /**
     * Toggles a local mute on a peer and persists it.
     *
     * <p>Client-side only: the peer is not notified and their audio is simply not mixed in.
     *
     * @return the new muted state
     */
    public boolean toggleMute(String peerId) {
        boolean nowMuted = mutes.toggle(peerId);
        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        if (manager != null) manager.saveVoiceMutes(mutes);
        return nowMuted;
    }

    /** Whether the listener has muted this peer locally. */
    public boolean isMuted(String peerId) {
        return mutes.isMuted(peerId);
    }

    /** The local channel id currently selected. */
    public String channel() {
        return currentChannel();
    }

    /** Live microphone level in {@code [0,1]} for the indicator; 0 when not transmitting. */
    public float micLevel() {
        return transmit ? audio.currentLevel() : 0f;
    }

    /** The audio engine's smoothed level without the transmit gate, for the meter's own logic. */
    public float rawMicLevel() {
        return audio.currentLevel();
    }

    /**
     * Sends a beacon immediately.
     *
     * <p>Switching channel is a persisted preference, but peers only learn of it on the next
     * periodic beacon -- up to a second of "I switched and nobody heard me". This is called from
     * the UI right after a switch so the new channel is advertised at once.
     */
    public void announceNow() {
        if (!running) return;
        Thread thread = new Thread(this::sendBeacon, "voice-announce");
        thread.setDaemon(true);
        thread.start();
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
