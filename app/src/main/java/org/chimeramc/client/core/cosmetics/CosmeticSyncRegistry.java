package org.chimeramc.client.core.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The peers whose equipped cosmetics we have heard, keyed by peer id.
 *
 * <p>An advertisement is not a presence heartbeat — a player who stops sending is still wearing
 * the cape — so an entry is only replaced by a newer advertisement for the same peer or dropped
 * when that peer leaves. The registry is a plain, thread-safe-enough store (a synchronized map) of
 * the latest advertisement per peer; {@link CosmeticSyncModule} owns the socket and this owns the
 * meaning.
 *
 * <p>Pure and Android-free, so {@code CosmeticSyncTest} can drive it directly.
 */
public final class CosmeticSyncRegistry {

    private final Map<String, CosmeticSyncProtocol.Advert> byPeer = new LinkedHashMap<>();

    /**
     * Records (or refreshes) a peer's advertisement. An advert with no peer id is ignored, and a
     * {@link CosmeticSyncProtocol#TYPE_REQUEST} is not stored: it carries no cosmetics, it only
     * asks us to send ours.
     */
    public synchronized void put(CosmeticSyncProtocol.Advert advert) {
        if (advert == null || advert.isRequest() || advert.peerId.isEmpty()) return;
        byPeer.put(advert.peerId, advert);
    }

    /** Whether a peer is already known, so a first sighting can be told apart from a routine one. */
    public synchronized boolean contains(String peerId) {
        return peerId != null && byPeer.containsKey(peerId);
    }

    /** Removes a peer that has left. */
    public synchronized void remove(String peerId) {
        if (peerId != null) byPeer.remove(peerId);
    }

    public synchronized void clear() {
        byPeer.clear();
    }

    public synchronized CosmeticSyncProtocol.Advert get(String peerId) {
        return peerId == null ? null : byPeer.get(peerId);
    }

    public synchronized int size() {
        return byPeer.size();
    }

    /** A stable snapshot for the UI, newest peers last. */
    public synchronized List<CosmeticSyncProtocol.Advert> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(byPeer.values()));
    }

    /**
     * The cape a peer is wearing, resolved against the local catalogue, or null for none/unknown.
     *
     * <p>Resolution is deliberately local: an id this build does not know (a newer client's cape)
     * reads as no cape rather than a crash or a placeholder, so a version mismatch is a missing
     * cape and nothing more.
     */
    public synchronized CosmeticCatalog.Cape capeFor(String peerId) {
        CosmeticSyncProtocol.Advert advert = byPeer.get(peerId);
        return advert == null ? null : CosmeticCatalog.equippedCape(advert.capeId);
    }

    public synchronized CosmeticCatalog.Accessory accessoryFor(String peerId) {
        CosmeticSyncProtocol.Advert advert = byPeer.get(peerId);
        return advert == null ? null : CosmeticCatalog.equippedAccessory(advert.accessoryId);
    }

    public synchronized CosmeticCatalog.Pet petFor(String peerId) {
        CosmeticSyncProtocol.Advert advert = byPeer.get(peerId);
        return advert == null ? null : CosmeticCatalog.equippedPet(advert.petId);
    }
}
