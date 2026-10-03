package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the cosmetic-sync routing rule, with no socket and no Context.
 *
 * <p>The rule decides which of the three transports carries an advertisement. Getting it wrong is
 * a silent failure (advertisements go nowhere and no peer ever sees a cape), so it is a pure
 * class rather than logic buried in the module's socket code.
 */
public class CosmeticSyncConfigTest {

    @Test
    public void noRelayAndNoManualPeerIsLanOnly() {
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, false, "", "", "world", "");
        assertTrue(config.enabled);
        assertFalse(config.relayEnabled);
        assertFalse(config.hasManualPeer());
        assertEquals("lan", config.routeLabel());
    }

    @Test
    public void aConfiguredRelayIsParsedAndWins() {
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, true, "voice.example.com:47902", "secret", "team", "");
        assertTrue(config.relayEnabled);
        assertEquals("voice.example.com", config.relayHost);
        assertEquals(47902, config.relayPort);
        assertEquals("secret", config.relayPassword);
        assertEquals("team", config.channel);
        assertEquals("relay", config.routeLabel());
    }

    @Test
    public void aRelayAddressWithoutAPortGetsTheDefault() {
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, true, "203.0.113.10", "", "world", "");
        assertTrue(config.relayEnabled);
        assertEquals("203.0.113.10", config.relayHost);
        assertEquals(47902, config.relayPort);
    }

    @Test
    public void anUnparseableRelayAddressFallsBackToLan() {
        // The toggle is on but the address cannot be used, so there is no relay route. It must not
        // pretend otherwise and send to a nonsense host.
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, true, "   ", "", "world", "");
        assertFalse(config.relayEnabled);
        assertEquals("lan", config.routeLabel());
    }

    @Test
    public void manualPeerIsTheFallbackWhenNoRelayIsConfigured() {
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, false, "", "", "world", "192.168.1.50:47903");
        assertFalse(config.relayEnabled);
        assertTrue(config.hasManualPeer());
        assertEquals("manual", config.routeLabel());
    }

    @Test
    public void aMalformedManualPeerIsNotUsable() {
        // A bare hostname is valid (default port applies); a bad port is not.
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, false, "", "", "world", "192.168.1.50:notaport");
        assertFalse(config.hasManualPeer());
        assertEquals("lan", config.routeLabel());
    }

    @Test
    public void theRelayWinsOverAManualPeer() {
        // Both are set: the relay is the better route (it reconnects and reaches a whole session),
        // so the manual address is ignored for routing even though it is still parseable.
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, true, "relay.example.com", "", "world", "192.168.1.50:47903");
        assertEquals("relay", config.routeLabel());
        assertTrue(config.hasManualPeer());
    }

    @Test
    public void aBlankChannelDefaultsToTheOpenWorldChannel() {
        CosmeticSyncConfig config = CosmeticSyncConfig.resolve(
                true, false, "", "", "   ", "");
        assertEquals("world", config.channel);
    }
}
