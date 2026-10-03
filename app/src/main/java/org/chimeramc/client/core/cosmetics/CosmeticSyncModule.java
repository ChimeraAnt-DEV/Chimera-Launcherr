package org.chimeramc.client.core.cosmetics;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Advertises the local player's equipped cosmetics to other Chimera users in the same world and
 * collects theirs, over a LAN multicast socket.
 *
 * <p><b>Why multicast, and why a second socket.</b> Two devices in the same Bedrock world are on
 * the same LAN segment, which is exactly the population that can see each other's capes, and
 * multicast reaches all of them with no server and no account — the same reasoning as proximity
 * voice. It is a <em>separate</em> socket on a separate group and port from voice, so the two
 * features cannot interfere and the voice wire format is untouched.
 *
 * <p><b>What this is.</b> It is not a game hook: it makes the equipped cape, accessory and pet
 * visible to <em>other Chimera users</em> who are in the same world, by telling them what to draw.
 * Each client resolves the ids against the catalogue it already ships. It does not make vanilla
 * players see anything — Bedrock gives a client no way to push a cosmetic onto someone else's
 * account — and the in-app note says so.
 *
 * <p><b>Announce, don't poll.</b> The advertisement is re-sent on a slow timer (and immediately
 * when the equipped set changes, via {@link #announce()}), because multicast is lossy and a peer
 * that joined after us must still learn what we wear. A slow timer keeps airtime near zero while
 * guaranteeing convergence.
 */
public final class CosmeticSyncModule {

    private static final String TAG = "CosmeticSync";

    /**
     * Its own multicast group and port, distinct from voice's, so neither module receives the
     * other's datagrams and the voice protocol needs no change.
     */
    public static final String GROUP = "239.255.42.100";
    public static final int PORT = 47902;

    /** How often the advertisement is re-sent. Slow: multicast is for convergence, not streaming. */
    static final long ANNOUNCE_INTERVAL_MS = 3000;

    private final Context context;
    private final CosmeticStore store;
    private final String peerId;
    private final String displayName;
    private final CosmeticSyncRegistry registry = new CosmeticSyncRegistry();

    private MulticastSocket socket;
    private WifiManager.MulticastLock multicastLock;
    private Thread receiveThread;
    private ScheduledExecutorService announcer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * The running module, or null.
     *
     * <p>A static reference is what lets the Cosmetics panel re-announce a change immediately and
     * list the peers it has heard without holding the overlay manager; the panel only exists in the
     * Mod Menu, and the module is the single source of the peer set.
     */
    private static volatile CosmeticSyncModule instance;

    public CosmeticSyncModule(Context context, String peerId, String displayName) {
        this.context = context.getApplicationContext();
        this.store = new CosmeticStore(context);
        this.peerId = peerId == null ? "" : peerId;
        this.displayName = displayName == null ? "" : displayName;
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

    /** Opens the socket and starts announcing; returns false when the network refuses. */
    public boolean start() {
        if (running.get()) return true;
        try {
            InetAddress group = InetAddress.getByName(GROUP);
            MulticastSocket multicastSocket = new MulticastSocket(PORT);
            multicastSocket.setReuseAddress(true);
            multicastSocket.setTimeToLive(1); // link-local: never leaves the LAN segment
            multicastSocket.joinGroup(group);
            socket = multicastSocket;
        } catch (IOException e) {
            Log.w(TAG, "Could not open the cosmetic sync socket", e);
            return false;
        }

        acquireMulticastLock();
        running.set(true);
        instance = this;
        receiveThread = new Thread(this::receiveLoop, "cosmetic-sync-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        announcer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cosmetic-sync-announce");
            t.setDaemon(true);
            return t;
        });
        announcer.scheduleWithFixedDelay(this::announce, 0, ANNOUNCE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        return true;
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        if (instance == this) instance = null;
        if (announcer != null) {
            announcer.shutdownNow();
            announcer = null;
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
        releaseMulticastLock();
        registry.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    public CosmeticSyncRegistry registry() {
        return registry;
    }

    /** Sends the current equipped set immediately, e.g. right after the player changes a cosmetic. */
    public void announce() {
        MulticastSocket target = socket;
        if (target == null || !running.get()) return;
        try {
            byte[] data = CosmeticSyncProtocol.encode(peerId, displayName,
                    store.getEquippedCapeId(), store.getEquippedAccessoryId(),
                    store.getEquippedPetId());
            if (data.length == 0) return;
            target.send(new DatagramPacket(data, data.length,
                    InetAddress.getByName(GROUP), PORT));
        } catch (IOException e) {
            // A lost advertisement is recovered by the next timer tick; never fatal.
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
                CosmeticSyncProtocol.Advert advert = CosmeticSyncProtocol.decode(data);
                // Our own multicast echo comes back to us; the peer id filters it.
                if (advert != null && !advert.peerId.isEmpty() && !advert.peerId.equals(peerId)) {
                    registry.put(advert);
                }
            } catch (IOException e) {
                if (!running.get()) break; // the socket was closed by stop()
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
