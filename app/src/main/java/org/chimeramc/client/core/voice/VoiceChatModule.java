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

    /** Frames of jitter buffer kept per peer; the relay's own default depth is well under this. */
    private static final int JITTER_CAPACITY = VoiceJitterBuffer.MAX_DEPTH * 4;

    private final Context context;
    private final VoiceRegistry registry = new VoiceRegistry();
    private final VoiceMutes mutes = new VoiceMutes();
    private final String peerId;
    private final String displayName;
    private final VoiceAudioEngine audio = new VoiceAudioEngine();
    private final VoiceMixer mixer = new VoiceMixer();
    private final VoiceCodec codec = VoiceCodec.create();
    /** One reorder buffer per peer; sequencing only means anything within one sender's stream. */
    private final java.util.concurrent.ConcurrentHashMap<String, PeerStream> streams =
            new java.util.concurrent.ConcurrentHashMap<>();
    private VoiceLink transport;
    private PositionSource positionSource;
    private Thread beaconThread;
    private Thread playbackThread;
    private volatile boolean running;
    private volatile boolean transmit = true;
    private volatile int sequence;
    private volatile long lastSendMs;
    private volatile String lastError;

    /**
     * One peer's receive state: where its frames wait and the gain to play them at.
     *
     * <p>The two transports queue differently. The relay's frames arrive out of order and need the
     * {@link VoiceJitterBuffer}; the LAN's arrive in order, so a plain queue is right and adding
     * 60&nbsp;ms of reorder latency to a link that has none would be pure loss. Either way the
     * frame waits here and only the playback thread touches the mixer, so the receive thread and
     * the mixer can never race on the same buffer.
     */
    private static final class PeerStream {
        final VoiceJitterBuffer buffer = VoiceJitterBuffer.forRelay();
        final java.util.concurrent.ConcurrentLinkedQueue<byte[]> pending =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        volatile boolean relayMode;
        volatile float gain = 1f;
        volatile long lastSeenMs;

        byte[] nextFrame(long nowMs) {
            return relayMode ? buffer.poll(nowMs) : pending.poll();
        }
    }

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

    /**
     * True while no position feed is installed at all, so distance can never be applied.
     *
     * <p>This is the persistent state; for "can I read a position this frame" use
     * {@link #hasLivePosition()}, which is false here <em>and</em> whenever the fail-closed native
     * read has nothing live to report.
     */
    public boolean isChannelMode() {
        return positionSource == null;
    }

    /**
     * Whether a position can be read <em>right now</em>, as opposed to whether a feed is installed.
     *
     * <p>The feed is fail-closed: with no world loaded — before the first frame, during a loading
     * screen, after leaving a session — the native read returns null. That is a different state
     * from "no feed", and treating it as a real origin would make the distance rule fire against
     * a phantom (0,0,0) and mute or mis-gain every peer. So the live read, not the field, decides.
     */
    public boolean hasLivePosition() {
        return sanitizePosition(readFromSource()) != null;
    }

    /** The local position, or the origin when no live read is possible. */
    private float[] localPosition() {
        float[] value = readLocalPosition();
        return value != null ? value : new float[]{0f, 0f, 0f};
    }

    /**
     * The local position, or null when the feed is absent or has nothing live to report.
     *
     * <p>Callers that apply the distance rule must use this, not the origin-returning
     * {@link #localPosition()}: the origin is only meaningful on the wire, never as "where the
     * player is".
     */
    private float[] readLocalPosition() {
        return sanitizePosition(readFromSource());
    }

    private float[] readFromSource() {
        PositionSource source = positionSource;
        if (source == null) return null;
        try {
            return source.read();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Accepts a raw feed value only when it is three finite numbers; otherwise null. A NaN from a
     * torn native read would otherwise propagate through the distance math and produce NaN gain,
     * which silences audio with no diagnosable cause. Pure, so the fail-closed rule is testable.
     */
    static float[] sanitizePosition(float[] raw) {
        if (raw == null || raw.length < 3) return null;
        if (Float.isNaN(raw[0]) || Float.isNaN(raw[1]) || Float.isNaN(raw[2])) return null;
        if (Float.isInfinite(raw[0]) || Float.isInfinite(raw[1]) || Float.isInfinite(raw[2])) {
            return null;
        }
        return raw;
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

        transport = createLink(manager);
        if (transport == null || !transport.start()) {
            audio.stop();
            transport = null;
            lastError = "could not open the network";
            return false;
        }

        running = true;
        beaconThread = new Thread(this::beaconLoop, "voice-beacon");
        beaconThread.setDaemon(true);
        beaconThread.start();
        playbackThread = new Thread(this::playbackLoop, "voice-mix");
        playbackThread.setDaemon(true);
        playbackThread.start();
        return true;
    }

    /**
     * Builds the link the session should run on.
     *
     * <p>Relay when the player has enabled it and given a usable address, multicast otherwise.
     * A configured-but-unparseable address falls back to multicast rather than failing the whole
     * module: the LAN path is always available, so a typo in the server field degrades to "works
     * at home" instead of "voice is broken".
     */
    private VoiceLink createLink(InbuiltModManager manager) {
        if (manager != null && manager.isVoiceRelayEnabled()) {
            VoiceRelayAddress parsed = VoiceRelayAddress.parse(manager.getVoiceRelayAddress());
            if (parsed != null) {
                return new VoiceRelayTransport(parsed, displayName,
                        manager.getVoiceRelayPassword(), currentChannel(), new VoiceLink.Listener() {
                    @Override
                    public void onDatagram(byte[] data, int length) {
                        VoiceChatModule.this.onDatagram(data, length);
                    }

                    @Override
                    public void onStopped(String reason) {
                        lastError = reason;
                    }
                });
            }
            lastError = "relay address is not valid; using LAN";
        }
        return new VoiceTransport(context, new VoiceTransport.Listener() {
            @Override
            public void onDatagram(byte[] data, int length) {
                VoiceChatModule.this.onDatagram(data, length);
            }

            @Override
            public void onStopped(String reason) {
                lastError = reason;
            }
        });
    }

    /** Whether the running (or last-started) session is on the relay rather than the LAN. */
    public boolean isRelayMode() {
        return transport instanceof VoiceRelayTransport;
    }

    /** The relay's connection state, for the status line; false on the LAN transport. */
    public boolean isRelayConnected() {
        VoiceLink link = transport;
        return link instanceof VoiceRelayTransport && ((VoiceRelayTransport) link).isConnected();
    }

    /** The server-assigned client id, or 0 on the LAN transport / before the handshake. */
    public long relayClientId() {
        VoiceLink link = transport;
        return link instanceof VoiceRelayTransport ? ((VoiceRelayTransport) link).clientId() : 0L;
    }

    /** True while the audio path is encoding to Opus (the relay) rather than sending raw PCM. */
    public boolean isUsingOpus() {
        VoiceLink link = transport;
        return link != null && link.prefersOpus() && codec.isAvailable();
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
        Thread playback = playbackThread;
        playbackThread = null;
        if (playback != null) {
            playback.interrupt();
            try {
                playback.join(400);
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
        streams.clear();
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
        VoiceLink active = transport;
        if (active == null) return;
        float[] position = localPosition();
        active.send(encodeOutgoing(VoiceProtocol.TYPE_BEACON, null,
                position[0], position[1], position[2], sequence++));
    }

    private void sendBye() {
        VoiceLink active = transport;
        if (active == null) return;
        float[] position = localPosition();
        active.send(encodeOutgoing(VoiceProtocol.TYPE_BYE, null,
                position[0], position[1], position[2], 0));
    }

    /**
     * Frames one outgoing packet, on whichever transport is active.
     *
     * <p>The LAN transport keeps the v3 shape with raw PCM so an existing local peer is unaffected;
     * the relay uses v4, carrying the server-assigned client id and the codec byte. Keeping the
     * branch here — one place that knows the transport's format — means the session logic above it
     * never has to.
     */
    private byte[] encodeOutgoing(byte type, byte[] payload, float x, float y, float z, int seq) {
        VoiceLink active = transport;
        byte codecByte = payload != null && isUsingOpus()
                ? VoiceProtocol.CODEC_OPUS : VoiceProtocol.CODEC_PCM;
        if (active instanceof VoiceRelayTransport) {
            VoiceRelayTransport relay = (VoiceRelayTransport) active;
            return VoiceProtocol.encodeRelayBeacon(relay.clientId(), type, peerId, displayName,
                    currentChannel(), currentChannelVisibility(), currentChannelName(),
                    currentChannelCapacity(), localLevel(), localSelfMuted(),
                    x, y, z, seq, codecByte, payload);
        }
        return VoiceProtocol.encodeLegacy(type, peerId, displayName, currentChannel(),
                currentChannelVisibility(), currentChannelName(), currentChannelCapacity(),
                localLevel(), localSelfMuted(), x, y, z, seq, payload);
    }

    private void onCapturedFrame(byte[] pcm, int length) {
        VoiceLink active = transport;
        if (active == null || !running) return;
        long now = android.os.SystemClock.uptimeMillis();
        lastSendMs = now;
        float[] position = localPosition();

        byte[] payload = pcm;
        int payloadLength = length;
        if (isUsingOpus()) {
            byte[] encoded = codec.encode(pcm, length);
            if (encoded == null) return; // a failed encode drops one frame, never falls back mid-stream
            payload = encoded;
            payloadLength = encoded.length;
        }
        byte[] frame = new byte[payloadLength];
        System.arraycopy(payload, 0, frame, 0, payloadLength);

        active.send(encodeOutgoing(VoiceProtocol.TYPE_AUDIO, frame,
                position[0], position[1], position[2], sequence++));
    }

    private void onDatagram(byte[] data, int length) {
        VoiceProtocol.Packet packet = VoiceProtocol.decode(data);
        if (packet == null) return;
        // On the LAN our own multicast echo is filtered by the transport; on the relay the server
        // rewrites the sender id, so the peer id is still the reliable "is this me" test.
        if (peerId.equals(packet.peerId)) return;

        long now = android.os.SystemClock.uptimeMillis();
        if (packet.type == VoiceProtocol.TYPE_BYE) {
            registry.remove(packet.peerId);
            streams.remove(packet.peerId);
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
            // A peer the listener muted locally is not mixed in; they are never told. Their own
            // self-mute needs no handling here: the microphone being off means no frames arrive.
            if (mutes.isMuted(packet.peerId)) {
                streams.remove(packet.peerId);
                return;
            }
            PeerStream stream = streams.computeIfAbsent(packet.peerId, k -> new PeerStream());
            stream.gain = gainFor(packet, channel);
            stream.lastSeenMs = now;

            byte[] pcm = packet.isOpus() ? codec.decode(packet.payload, packet.payload.length)
                    : packet.payload;
            if (pcm == null) return; // a frame that would not decode is dropped, not substituted
            // The LAN path arrives in order and needs no reordering; only the relay path buffers.
            // Feeding a LAN stream through a buffer would add 60 ms of latency for nothing.
            if (transport instanceof VoiceRelayTransport) {
                stream.relayMode = true;
                stream.buffer.offer(packet.sequence, pcm, now);
            } else {
                stream.relayMode = false;
                // Bound the queue so a peer flooding faster than 50 fps cannot grow it without end.
                if (stream.pending.size() < 64) {
                    stream.pending.add(pcm);
                }
            }
        }
    }

    /** The gain a peer's audio should be played at, from distance and channel. */
    private float gainFor(VoiceProtocol.Packet packet, String channel) {
        return gainFor(packet, channel, currentChannel(), currentRange(), readLocalPosition());
    }

    /**
     * The gain decision, as a pure function of the live position.
     *
     * <p>Separated from {@link #gainFor} so the three states — no feed, no live read, and a real
     * position — are unit-testable without a device or a running session. The middle state is the
     * one that matters: the native feed is fail-closed, so a frame arriving between world loads
     * has no position, and measuring it against a phantom origin would drop audio that should be
     * playing. With no position the channel rule stands in, which is the module's documented
     * channel mode; it is never silently "distance from (0,0,0)".
     */
    static float gainFor(VoiceProtocol.Packet packet, String channel, String listenerChannel,
                         float rangeBlocks, float[] position) {
        if (!VoiceChannel.canHear(listenerChannel, channel)) {
            return 0f;
        }
        if (position == null) {
            return 1f;
        }
        float distance = (float) Math.sqrt(
                sq(packet.x - position[0]) + sq(packet.y - position[1]) + sq(packet.z - position[2]));
        return VoiceChannel.gain(distance, rangeBlocks, listenerChannel, channel);
    }

    /**
     * Pumps the mixer and every jitter buffer on a fixed clock.
     *
     * <p>Playback must be paced by the audio device's frame rate, not by packet arrival: releasing
     * on arrival is what produces choppy audio over a jittery link. Every tick this drains one
     * frame from each peer's reorder buffer, mixes them into a single frame, and writes it once.
     * That also fixes the multi-talker case — two people at once are summed here instead of two
     * interleaved {@code AudioTrack} writes.
     */
    private void playbackLoop() {
        long nextTick = android.os.SystemClock.uptimeMillis();
        while (running) {
            nextTick += VoiceAudioEngine.FRAME_MS;
            long now = android.os.SystemClock.uptimeMillis();
            long wait = nextTick - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else if (wait < -VoiceAudioEngine.FRAME_MS * 4) {
                // Fell far behind (a stalled thread); resync rather than trying to catch up.
                nextTick = now;
            }

            long stamp = android.os.SystemClock.uptimeMillis();
            mixer.clear();
            for (java.util.Map.Entry<String, PeerStream> entry : streams.entrySet()) {
                PeerStream stream = entry.getValue();
                if (stamp - stream.lastSeenMs > 2000) {
                    streams.remove(entry.getKey(), stream);
                    continue;
                }
                byte[] frame = stream.nextFrame(stamp);
                if (frame != null) {
                    mixer.add(frame, frame.length, stream.gain);
                }
            }
            mixer.setFrameBytes(VoiceAudioEngine.FRAME_BYTES);
            mixer.mixInto((pcm, len) -> audio.play(pcm, len, 1f));
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

    /** The audio engine, for the relay/codec status line and the mic meter. */
    public boolean isOpusCodecAvailable() {
        return codec.isAvailable();
    }

    /** The last failure reason, or null. */
    public String transportError() {
        VoiceLink link = transport;
        if (link instanceof VoiceRelayTransport) {
            String error = ((VoiceRelayTransport) link).lastError();
            if (error != null) return error;
        }
        return lastError;
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
        float[] position = readLocalPosition();
        if (position != null) {
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
