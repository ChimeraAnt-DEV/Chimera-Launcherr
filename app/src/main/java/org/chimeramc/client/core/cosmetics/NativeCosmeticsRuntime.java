package org.chimeramc.client.core.cosmetics;

import android.content.Context;

/**
 * Boots the native cosmetics system and keeps it in step with the equipped set.
 *
 * <p>There is exactly one place that initialises cosmetics now: this class. It publishes the
 * equipped cape/accessory/pet into the native registry and installs the sink that renders a peer's
 * advertised cosmetics, so the renderer always has the current data with no resource pack and no
 * world reload. {@code LauncherApplication} calls {@link #init} once at process start, and
 * {@link #syncEquipped} is called before every launch and on every equip.
 *
 * <p>Fail-closed throughout: without the native library every call is a no-op and the preview still
 * shows the equipped set.
 */
public final class NativeCosmeticsRuntime {

    private static volatile boolean initialized;
    private static CosmeticStore store;

    private NativeCosmeticsRuntime() {
    }

    /**
     * Initialises the native cosmetics system: publishes the equipped set and installs the
     * peer-advert sink. Idempotent, so it is safe to call from both {@code Application.onCreate} and
     * a game session start.
     */
    public static void init(Context context) {
        if (context == null) return;
        if (store == null) store = new CosmeticStore(context);
        // Install the render sink even if a previous init ran: the sync module clears nothing here,
        // and re-installing is cheap.
        CosmeticSyncModule.setAdvertListener(NativeCosmeticsRuntime::renderPeerAdvert);
        if (initialized) return;
        initialized = true;
        syncEquipped(context);
    }

    /**
     * Publishes the currently equipped cosmetics into the native registry. Called at startup, before
     * every launch, and whenever the player changes a cosmetic.
     */
    public static void syncEquipped(Context context) {
        try {
            CosmeticStore s = store != null ? store : (context == null ? null
                    : (store = new CosmeticStore(context)));
            if (s == null) return;
            NativeCosmeticsBridge.publishLocal(
                    s.getEquippedCapeForDisplay(), s.getEquippedAccessory(), s.getEquippedPet());
        } catch (Throwable ignored) {
            // A cosmetic must never take the process down; the preview still shows the selection.
        }
    }

    /**
     * Renders a received peer advertisement: resolves the advertised catalogue ids against the local
     * catalogue and uploads the pixels, keyed by the peer's id, so the renderer can draw their
     * cosmetic. Runs on the sync receive thread.
     */
    private static void renderPeerAdvert(CosmeticSyncProtocol.Advert advert) {
        if (advert == null || advert.peerId.isEmpty()) return;
        try {
            NativeCosmeticsBridge.publishPeer(advert.peerId,
                    CosmeticCatalog.equippedCape(advert.capeId),
                    CosmeticCatalog.equippedAccessory(advert.accessoryId),
                    CosmeticCatalog.equippedPet(advert.petId));
        } catch (Throwable ignored) {
            // A bad payload must not stop the receive loop.
        }
    }

    /** True once {@link #init} has run. */
    public static boolean isInitialized() {
        return initialized;
    }
}
