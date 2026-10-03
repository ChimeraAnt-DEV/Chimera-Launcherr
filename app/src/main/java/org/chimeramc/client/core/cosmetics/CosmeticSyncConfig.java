package org.chimeramc.client.core.cosmetics;

/**
 * Where cosmetic sync should send its advertisements, resolved once when the module starts.
 *
 * <p>There are three transports and the choice is a small, testable rule rather than logic buried
 * in the socket code:
 *
 * <ol>
 *   <li><b>LAN multicast</b> — always on. Two devices in the same Bedrock world are on the same
 *       segment, so multicast reaches all of them with no server and no setup. This is the
 *       zero-configuration default.</li>
 *   <li><b>Relay</b> — when the player has configured the same Go relay Proximity Voice Chat uses,
 *       advertisements travel through it as {@code COSMETIC_MANIFEST} frames, so a cape is visible
 *       to a Chimera user who is <em>not</em> on the same Wi-Fi.</li>
 *   <li><b>Manual peer</b> — the fallback when no relay is configured: the player pastes a
 *       {@code host:port} and advertisements are unicast straight to it. It is deliberately manual
 *       because a direct peer address cannot be discovered automatically.</li>
 * </ol>
 *
 * <p>The relay and manual paths are mutually exclusive: a configured relay is the better route
 * (it reconnects, keeps NAT open and reaches a whole session), so the manual address is only
 * consulted when there is no relay. Multicast is independent and runs alongside either.
 *
 * <p>Pure and Android-free so the routing rule is unit-testable without a socket or a Context.
 */
public final class CosmeticSyncConfig {

    /** The LAN multicast group and port, distinct from voice's so neither hears the other. */
    public static final String GROUP = "239.255.42.100";
    public static final int PORT = 47902;

    /** Whether cosmetic sync is enabled at all. */
    public final boolean enabled;
    /** Whether the relay route is configured (relay enabled and a parseable address). */
    public final boolean relayEnabled;
    /** The parsed relay host, or "" when not on the relay. */
    public final String relayHost;
    public final int relayPort;
    /** The relay password, or "" for an open relay. */
    public final String relayPassword;
    /** The channel whose members can hear the sender; matches the voice channel. */
    public final String channel;
    /** The manual unicast peer as {@code host:port}, or "" when none was pasted. */
    public final String manualPeer;

    private CosmeticSyncConfig(boolean enabled, boolean relayEnabled, String relayHost,
                               int relayPort, String relayPassword, String channel,
                               String manualPeer) {
        this.enabled = enabled;
        this.relayEnabled = relayEnabled;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.relayPassword = relayPassword;
        this.channel = channel;
        this.manualPeer = manualPeer;
    }

    /**
     * Builds the config from the raw preference values.
     *
     * <p>{@code relayAddress} is parsed with the same rules the Voice screen uses, so an address
     * that works for voice works here; an unparseable one simply means "no relay", which is why
     * {@link #relayEnabled} is derived from a successful parse rather than the toggle alone.
     */
    public static CosmeticSyncConfig resolve(boolean enabled, boolean relayEnabled,
                                             String relayAddress, String relayPassword,
                                             String channel, String manualPeer) {
        boolean relay = false;
        String host = "";
        int port = 0;
        if (relayEnabled) {
            // Reuse VoiceRelayAddress so host/port parsing (IPv6 brackets, default port) cannot
            // drift between voice and cosmetics.
            org.chimeramc.client.core.voice.VoiceRelayAddress parsed =
                    org.chimeramc.client.core.voice.VoiceRelayAddress.parse(relayAddress);
            if (parsed != null) {
                relay = true;
                host = parsed.host;
                port = parsed.port;
            }
        }
        String manual = manualPeer == null ? "" : manualPeer.trim();
        return new CosmeticSyncConfig(enabled, relay, host, port,
                relayPassword == null ? "" : relayPassword,
                channel == null || channel.trim().isEmpty() ? "world" : channel.trim(),
                manual);
    }

    /** Whether a manual unicast peer is configured and usable (host:port with a numeric port). */
    public boolean hasManualPeer() {
        return !manualPeer.isEmpty()
                && org.chimeramc.client.core.voice.VoiceRelayAddress.parse(manualPeer) != null;
    }

    /** A short label for the status line: which route is carrying advertisements. */
    public String routeLabel() {
        if (relayEnabled) return "relay";
        if (hasManualPeer()) return "manual";
        return "lan";
    }
}
