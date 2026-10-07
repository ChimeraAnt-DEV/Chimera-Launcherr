package org.chimeramc.client.core.cosmetics;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.util.ArrayList;
import java.util.List;

/**
 * Local cache of the last advertisement heard from each peer.
 *
 * <p>A peer's cosmetics are discovered over the network, which costs a round trip (LAN is instant,
 * the cloud relay can be a second). Caching the last advertisement per peer means a peer seen
 * before shows their cape immediately on the next session, before the first beacon arrives — the
 * "instant on subsequent loads" behaviour. The network still refreshes it, so a changed cosmetic
 * still updates live; the cache is only a head start.
 *
 * <p>The advertisement itself is stored (base64 of the wire bytes), not a re-derivation, so a
 * cached entry and a live one decode through exactly the same {@link CosmeticSyncProtocol} path
 * and cannot drift.
 */
public final class CosmeticSyncCache {

    private static final String PREFS = "cosmetic_sync_cache";
    private static final String KEY_PREFIX = "peer_";
    /** Cap on cached peers, so an old world's peers cannot grow the store without bound. */
    private static final int MAX_PEERS = 64;

    private final SharedPreferences prefs;

    public CosmeticSyncCache(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Stores (or refreshes) one peer's advertisement. A request carries no data and is ignored. */
    public void put(CosmeticSyncProtocol.Advert advert, byte[] rawWireBytes) {
        if (advert == null || advert.isRequest() || advert.peerId.isEmpty()
                || rawWireBytes == null || rawWireBytes.length == 0) {
            return;
        }
        String encoded = Base64.encodeToString(rawWireBytes, Base64.NO_WRAP);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PREFIX + advert.peerId, encoded);
        if (prefs.getAll().size() >= MAX_PEERS) pruneOne(editor, advert.peerId);
        editor.apply();
    }

    /** The cached advertisements, decoded; malformed entries are skipped. */
    public List<CosmeticSyncProtocol.Advert> load() {
        List<CosmeticSyncProtocol.Advert> out = new ArrayList<>();
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(KEY_PREFIX)) continue;
            Object value = entry.getValue();
            if (!(value instanceof String)) continue;
            try {
                byte[] raw = Base64.decode((String) value, Base64.NO_WRAP);
                CosmeticSyncProtocol.Advert advert = CosmeticSyncProtocol.decode(raw);
                if (advert != null && !advert.peerId.isEmpty()) out.add(advert);
            } catch (RuntimeException ignored) {
                // A corrupt entry is dropped rather than failing the whole load.
            }
        }
        return out;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    /** Drops one other peer's entry when the store is full, keeping the cap honest. */
    private void pruneOne(SharedPreferences.Editor editor, String justAdded) {
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX) && !key.equals(KEY_PREFIX + justAdded)) {
                editor.remove(key);
                return;
            }
        }
    }
}
