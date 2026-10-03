package org.chimeramc.client.core.cosmetics;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.chimeramc.client.core.voice.VoiceProtocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Advertises the local player's equipped cosmetics to other Chimera users in the same world and
 * collects theirs.
 *
 * <p><b>Two discovery sources, one manifest.</b> The advertisement is the same {@link
 * CosmeticSyncProtocol} datagram either way; only the transport changes. LAN multicast reaches
 * everyone on the same segment with no server, and is always on. When the player has configured
 * the same Go relay Proximity Voice Chat uses, the manifest is additionally carried as a {@link
 * VoiceProtocol#TYPE_COSMETIC_MANIFEST} frame the relay fans out to the peers in the sender's
 * session — so a cape is visible to a Chimera user who is <em>not</em> on the same Wi-Fi. When no
 * relay is configured, the player can paste a {@code host:port} and the manifest is unicast
 * straight to it. See {@link CosmeticSyncConfig} for the routing rule.
 *
 * <p><b>What this is.</b> It is not a game hook: it makes the equipped cape, accessory and pet
 * visible to <em>other Chimera users</em> who are in the same world, by telling them what to draw.
 * Each client resolves the ids against the catalogue it already ships. It does not make vanilla
 * players see anything — Bedrock gives a client no way to push a cosmetic onto someone else's
 * account — and the in-app note says so.
 *
 * <p><b>Announce, don't poll.</b> The advertisement is re-sent on a slow timer (and immediately
 * when the equipped set changes, via {@link #announce()}), because both transports are lossy and a
 * peer that joined after us must still learn what we wear. A slow timer keeps airtime near zero
 * while guaranteeing convergence.
 */
public final class CosmeticSyncModule {

    private static final String TAG = "CosmeticSync";

    /** Its own multicast group and port, distinct from voice's, so neither module hears the other. */
    public static final String GROUP = CosmeticSyncConfig.GROUP;
    public static final int PORT = CosmeticSyncConfig.PORT;

    /** How often the advertisement is re-sent. Slow: this is for convergence, not streaming. */
    static final long ANNOUNCE_INTERVAL_MS = 3000;

    /** Relay keepalive period, matching the voice relay's own default. */
    private static final int RELAY_KEEPALIVE_MS = 3000;

    /** How long the relay read loop blocks before re-checking whether it should still run. */
    private static final int RELAY_READ_TIMEOUT_MS = 500;

    private final Context context;
    private final CosmeticStore store;
    private final String peerId;
    private final String displayName;
    private final CosmeticSyncConfig config;
    private final CosmeticSyncRegistry registry = new CosmeticSyncRegistry();

    private volatile MulticastSocket socket;
    private WifiManager.MulticastLock multicastLock;
    private Thread receiveThread;
    private ScheduledExecutorService announcer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Opens the blocking transports off the caller thread; see {@link #start()}. */
    private Thread starterThread;

    /** Set by {@link #stop()} so a start that is still opening sockets aborts instead of running. */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Runs on-demand advertisements off the UI thread.
     *
     * <p>{@link #announce()} is called straight from a Spinner's {@code onItemSelected}, i.e. the
     * main thread, and every route in {@link #broadcastNow(byte[])} does blocking socket I/O. A
     * blocking send on the main thread throws {@link
     * android.os.NetworkOnMainThreadException} and took the whole game down on every equip. The
     * timer-driven announces already ran on the {@link #announcer} thread; this executor is the
     * same escape hatch for the on-demand path.
     */
    private ExecutorService broadcaster;

    // Relay route: a unicast UDP session with the Go relay. The manifest rides inside a v4
    // COSMETIC_MANIFEST frame; the relay assigns the client id and fans the frame out.
    private DatagramSocket relaySocket;
    private InetSocketAddress relayServer;
    private volatile long relayClientId;
    private Thread relayThread;

    // Manual route: unicast straight to a pasted host:port, no server.
    private InetSocketAddress manualPeer;

    /**
     * The running module, or null.
     *
     * <p>A static reference is what lets the Cosmetics panel re-announce a change immediately and
     * list the peers it has heard without holding the overlay manager; the panel only exists in the
     * Mod Menu, and the module is the single source of the peer set.
     */
    private static volatile CosmeticSyncModule instance;

    public CosmeticSyncModule(Context context, String peerId, String displayName) {
        this(context, peerId, displayName, CosmeticSyncConfig.resolve(true, false, "", "",
                "world", ""));
    }

    /**
     * The full constructor, taking the resolved routing config.
     *
     * <p>Kept separate from the preference read so the module can be driven in a test with an
     * explicit config rather than a Context.
     */
    public CosmeticSyncModule(Context context, String peerId, String displayName,
                              CosmeticSyncConfig config) {
        this.context = context.getApplicationContext();
        this.store = new CosmeticStore(context);
        this.peerId = peerId == null ? "" : peerId;
        this.displayName = displayName == null ? "" : displayName;
        this.config = config == null
                ? CosmeticSyncConfig.resolve(true, false, "", "", "world", "")
                : config;
    }

    /** The running module, or null. */
    public static CosmeticSyncModule peek() {
        return instance;
    }

    /**
     * Asks the running module to re-send the advertisement now, e.g. right after a cosmetic change.
     * A no-op when the module is not running, so callers need no null dance.
     */
    public static void requestAnnounce() {
        CosmeticSyncModule module = instance;
        if (module != null) module.announce();
    }

    /** The route currently carrying advertisements: "lan", "relay" or "manual". */
    public String routeLabel() {
        return config.routeLabel();
    }

    /**
     * Opens the transports and starts announcing.
     *
     * <p><b>Fully asynchronous.</b> Every step here — the multicast {@code joinGroup}, the relay
     * socket, the manual peer's DNS resolve — is blocking network I/O, and callers reach this from
     * the UI thread (the Cosmetics panel toggling sync on, the overlay manager starting a session).
     * Doing that inline threw {@link android.os.NetworkOnMainThreadException} and killed the game
     * the moment a cosmetic was equipped. The whole startup therefore runs on a background thread
     * and this method returns immediately.
     *
     * @return true if the module is enabled (startup has been scheduled); false if it is disabled.
     *         A socket failure is logged and leaves the module stopped rather than thrown.
     */
    public boolean start() {
        if (running.get()) return true;
        if (!config.enabled) return false;
        if (starterThread != null && starterThread.isAlive()) return true;
        cancelled.set(false);
        starterThread = new Thread(this::openAndStart, "cosmetic-sync-start");
        starterThread.setDaemon(true);
        starterThread.start();
        return true;
    }

    /** The blocking half of {@link #start()}; runs on the {@code cosmetic-sync-start} thread. */
    private void openAndStart() {
        try {
            InetAddress group = InetAddress.getByName(GROUP);
            MulticastSocket multicastSocket = new MulticastSocket(PORT);
            multicastSocket.setReuseAddress(true);
            multicastSocket.setTimeToLive(1); // link-local: never leaves the LAN segment
            multicastSocket.joinGroup(group);
            socket = multicastSocket;
        } catch (IOException e) {
            Log.w(TAG, "Could not open the cosmetic sync socket", e);
            return;
        }
        // stop() may have run while the socket was being opened; if so, close and bail.
        if (cancelled.get()) {
            MulticastSocket opened = socket;
            socket = null;
            if (opened != null) opened.close();
            return;
        }
        running.set(true);
        instance = this;

        acquireMulticastLock();

        receiveThread = new Thread(this::receiveLoop, "cosmetic-sync-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        // The relay/manual routes are opened after the LAN one and independently: a failure here
        // must not stop the LAN path that already works.
        if (config.relayEnabled) startRelay();
        else if (config.hasManualPeer()) resolveManualPeer();

        announcer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cosmetic-sync-announce");
            t.setDaemon(true);
            return t;
        });
        announcer.scheduleWithFixedDelay(this::announce, 0, ANNOUNCE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        // On-demand announces (an equip in the Cosmetics panel, a relay HELLO_ACK, a peer's
        // request) are queued here so no caller ever blocks on a socket from the UI thread.
        broadcaster = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cosmetic-sync-broadcast");
            t.setDaemon(true);
            return t;
        });
    }

    public void stop() {
        // Mark cancelled first, so a start still opening sockets aborts rather than coming up
        // after this stop (which would leave the module running with no way to reach it).
        cancelled.set(true);
        if (!running.getAndSet(false)) return;
        if (instance == this) instance = null;
        if (announcer != null) {
            announcer.shutdownNow();
            announcer = null;
        }
        if (broadcaster != null) {
            broadcaster.shutdownNow();
            broadcaster = null;
        }
        MulticastSocket target = socket;
        socket = null;
        if (target != null) {
            try {
                target.leaveGroup(InetAddress.getByName(GROUP));
            } catch (IOException ignored) {
                // Leaving a group we are about to close anyway is best-effort.
            }
            target.close();
        }
        stopRelay();
        releaseMulticastLock();
        registry.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    public CosmeticSyncRegistry registry() {
        return registry;
    }

    /**
     * Queues an immediate advertisement of the current equipped set.
     *
     * <p>Safe to call from any thread — including the UI thread straight from a Spinner's
     * selection callback — because the actual socket I/O runs on the {@link #broadcaster} thread.
     * That is the fix for the {@code NetworkOnMainThreadException} that crashed the game on every
     * equip. A no-op before {@link #start()} or after {@link #stop()}.
     */
    public void announce() {
        ExecutorService executor = broadcaster;
        if (executor == null || executor.isShutdown()) return;
        try {
            executor.execute(this::broadcastNow);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Raced with stop(); the module is shutting down and there is nothing to send.
        }
    }

    /** Reads the equipped set and sends one advertisement; runs on the broadcaster thread. */
    private void broadcastNow() {
        byte[] data = CosmeticSyncProtocol.encode(peerId, displayName,
                store.getEquippedCapeId(), store.getEquippedAccessoryId(),
                store.getEquippedPetId());
        if (data.length == 0) return;
        broadcast(data);
    }

    /** Sends a datagram on every configured route. A lost one is recovered by the next tick. */
    private void broadcast(byte[] data) {
        // LAN multicast.
        MulticastSocket target = socket;
        if (target != null && running.get()) {
            try {
                target.send(new DatagramPacket(data, data.length,
                        InetAddress.getByName(GROUP), PORT));
            } catch (IOException e) {
                // Recovered by the next timer tick; never fatal.
            }
        }
        // Relay: wrap the manifest in a v4 frame the relay fans out.
        sendRelay(data);
        // Manual unicast peer.
        DatagramSocket manual = relaySocket; // reuse the same unicast socket when present
        if (manualPeer != null) {
            try {
                if (manual != null && !manual.isClosed()) {
                    manual.send(new DatagramPacket(data, data.length, manualPeer));
                }
            } catch (IOException e) {
                // The peer may be offline; the next tick retries.
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[CosmeticSyncProtocol.MAX_PAYLOAD];
        while (running.get()) {
            MulticastSocket target = socket;
            if (target == null) break;
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                target.receive(packet);
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                handleIncoming(data);
            } catch (IOException e) {
                if (!running.get()) break; // the socket was closed by stop()
            }
        }
    }

    /** Decodes and records one manifest, answering a request and prompting a first-sighted peer. */
    private void handleIncoming(byte[] data) {
        CosmeticSyncProtocol.Advert advert = CosmeticSyncProtocol.decode(data);
        // Our own multicast echo comes back to us; the peer id filters it.
        if (advert == null || advert.peerId.isEmpty() || advert.peerId.equals(peerId)) {
            return;
        }
        if (advert.isRequest()) {
            // A peer asked everyone to state their set now, so answer immediately rather than
            // making them wait for the slow timer. This is what makes cosmetics appear promptly
            // when someone joins the world.
            announce();
            return;
        }
        // The first time we hear a peer, ask them (and the group) to re-state their set, so a
        // player who joined after us is not invisible until their next timer tick.
        boolean firstSight = !registry.contains(advert.peerId);
        registry.put(advert);
        if (firstSight) requestAll();
    }

    /** Asks every listener to re-advertise immediately; used when a new peer is first heard. */
    private void requestAll() {
        byte[] data = CosmeticSyncProtocol.encodeRequest(peerId, displayName);
        if (data.length == 0) return;
        broadcast(data);
    }

    // --- Relay route ------------------------------------------------------------------------

    private void startRelay() {
        try {
            relayServer = new InetSocketAddress(
                    InetAddress.getByName(config.relayHost), config.relayPort);
            DatagramSocket unicast = new DatagramSocket();
            unicast.setSoTimeout(RELAY_READ_TIMEOUT_MS);
            relaySocket = unicast;
        } catch (IOException e) {
            Log.w(TAG, "Could not open the cosmetic relay socket", e);
            relaySocket = null;
            relayServer = null;
            return;
        }
        relayThread = new Thread(this::relayLoop, "cosmetic-sync-relay");
        relayThread.setDaemon(true);
        relayThread.start();
    }

    private void stopRelay() {
        DatagramSocket target = relaySocket;
        relaySocket = null;
        relayServer = null;
        relayClientId = 0L;
        if (target != null) target.close();
        Thread thread = relayThread;
        relayThread = null;
        if (thread != null) {
            try {
                thread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * The relay session: HELLO once, then keep the NAT mapping open and read fan-out frames. The
     * handshake is idempotent server-side, so a re-HELLO replaces the session rather than
     * accumulating one; that is what lets a plain retry recover from a dropped packet.
     */
    private void relayLoop() {
        sendRelayHello();
        long lastKeepalive = System.currentTimeMillis();
        byte[] buffer = new byte[VoiceProtocol.MAX_PAYLOAD + 512];
        while (running.get()) {
            DatagramSocket target = relaySocket;
            InetSocketAddress server = relayServer;
            if (target == null || server == null || target.isClosed()) break;
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                target.receive(packet);
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                handleRelayFrame(data);
            } catch (SocketTimeoutException timeout) {
                // Expected: fall through to the keepalive below.
            } catch (IOException e) {
                if (!running.get()) break;
            }
            long now = System.currentTimeMillis();
            if (now - lastKeepalive >= RELAY_KEEPALIVE_MS) {
                lastKeepalive = now;
                sendRelayPong();
            }
        }
    }

    private void sendRelayHello() {
        DatagramSocket target = relaySocket;
        InetSocketAddress server = relayServer;
        if (target == null || server == null || target.isClosed()) return;
        try {
            byte[] credential = config.relayPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] hello = VoiceProtocol.encodeHello(displayName, config.channel, credential);
            target.send(new DatagramPacket(hello, hello.length, server));
        } catch (IOException e) {
            // The loop retries; a failed hello is not fatal.
        }
    }

    private void sendRelayPong() {
        DatagramSocket target = relaySocket;
        InetSocketAddress server = relayServer;
        if (target == null || server == null || target.isClosed()) return;
        try {
            byte[] pong = VoiceProtocol.encodePong(relayClientId);
            target.send(new DatagramPacket(pong, pong.length, server));
        } catch (IOException e) {
            // Keepalive is best-effort.
        }
    }

    /** Sends a manifest over the relay, wrapped in a v4 COSMETIC_MANIFEST frame. */
    private void sendRelay(byte[] manifest) {
        DatagramSocket target = relaySocket;
        InetSocketAddress server = relayServer;
        if (target == null || server == null || target.isClosed()) return;
        try {
            byte[] frame = VoiceProtocol.encodeCosmeticManifest(relayClientId, peerId, displayName,
                    config.channel, manifest);
            if (frame.length == 0) return;
            target.send(new DatagramPacket(frame, frame.length, server));
        } catch (IOException e) {
            // The next announce tick retries.
        }
    }

    /**
     * Handles a frame from the relay. Only a HELLO_ACK (to learn our assigned id) and a
     * COSMETIC_MANIFEST (a peer's cosmetics) are interesting; everything else is ignored.
     */
    private void handleRelayFrame(byte[] data) {
        VoiceProtocol.Packet packet = VoiceProtocol.decode(data);
        if (packet == null) return;
        if (packet.type == VoiceProtocol.TYPE_HELLO_ACK) {
            relayClientId = packet.clientId;
            // Re-announce now that we have an id, so peers learn our set without waiting a tick.
            announce();
            return;
        }
        if (packet.type == VoiceProtocol.TYPE_COSMETIC_MANIFEST && packet.payload != null) {
            handleIncoming(packet.payload);
        }
    }

    // --- Manual route -----------------------------------------------------------------------

    private void resolveManualPeer() {
        try {
            org.chimeramc.client.core.voice.VoiceRelayAddress parsed =
                    org.chimeramc.client.core.voice.VoiceRelayAddress.parse(config.manualPeer);
            if (parsed == null) return;
            manualPeer = new InetSocketAddress(InetAddress.getByName(parsed.host), parsed.port);
            // A unicast socket is needed for the manual send; reuse the relay socket slot so the
            // two routes share one socket when only one is active.
            if (relaySocket == null) {
                DatagramSocket unicast = new DatagramSocket();
                unicast.setSoTimeout(RELAY_READ_TIMEOUT_MS);
                relaySocket = unicast;
                relayThread = new Thread(this::manualReceiveLoop, "cosmetic-sync-manual");
                relayThread.setDaemon(true);
                relayThread.start();
            }
        } catch (IOException e) {
            manualPeer = null;
        }
    }

    private void manualReceiveLoop() {
        byte[] buffer = new byte[CosmeticSyncProtocol.MAX_PAYLOAD];
        while (running.get()) {
            DatagramSocket target = relaySocket;
            if (target == null || target.isClosed()) break;
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                target.receive(packet);
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                handleIncoming(data);
            } catch (SocketTimeoutException timeout) {
                // Expected; loop to re-check running.
            } catch (IOException e) {
                if (!running.get()) break;
            }
        }
    }

    private void acquireMulticastLock() {

        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            multicastLock = wifi.createMulticastLock("chimera-cosmetics");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
        } catch (Throwable t) {
            // A device without a WifiManager still gets unicast sockets; do not fail over the lock.
            Log.w(TAG, "Could not acquire the multicast lock", t);
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Throwable ignored) {
            // Releasing a lock we may not hold must not throw out of stop().
        }
        multicastLock = null;
    }
}
