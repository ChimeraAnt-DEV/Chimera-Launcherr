package org.chimeramc.client.core.voice;

/**
 * Parses the relay server address a player types into the Voice screen.
 *
 * <p>It accepts what people actually type — {@code voice.example.com}, {@code 203.0.113.10},
 * {@code host:47902}, an IPv6 literal in brackets — and applies the default port when none is
 * given. Getting this wrong is a silent failure: a mis-parsed address is an unresolvable host and
 * the player sees "could not connect" with no hint that a colon or a bracket was the problem, so
 * the parse is a pure class with its own test rather than inline string splitting.
 */
public final class VoiceRelayAddress {

    /** The port the relay listens on by default, matching the server's own default. */
    public static final int DEFAULT_PORT = 47902;

    public final String host;
    public final int port;

    private VoiceRelayAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Parses an address, or returns null when it is empty or clearly unusable. */
    public static VoiceRelayAddress parse(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.isEmpty()) return null;

        // An IPv6 literal is bracketed so its colons are not read as the port separator.
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) return null;
            String host = value.substring(1, close).trim();
            if (host.isEmpty()) return null;
            String rest = value.substring(close + 1).trim();
            if (rest.isEmpty()) return new VoiceRelayAddress(host, DEFAULT_PORT);
            if (!rest.startsWith(":")) return null;
            Integer port = parsePort(rest.substring(1));
            return port == null ? null : new VoiceRelayAddress(host, port);
        }

        int colon = value.lastIndexOf(':');
        if (colon < 0) {
            return new VoiceRelayAddress(value, DEFAULT_PORT);
        }
        // A bare IPv6 literal with no port has several colons; treat it as a host, not host:port.
        if (value.indexOf(':') != colon) {
            return new VoiceRelayAddress(value, DEFAULT_PORT);
        }
        String host = value.substring(0, colon).trim();
        Integer port = parsePort(value.substring(colon + 1));
        if (host.isEmpty() || port == null) return null;
        return new VoiceRelayAddress(host, port);
    }

    private static Integer parsePort(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            int port = Integer.parseInt(raw.trim());
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A short, human-readable form for the status line. */
    public String display() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return display();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof VoiceRelayAddress)) return false;
        VoiceRelayAddress that = (VoiceRelayAddress) other;
        return port == that.port && host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return host.hashCode() * 31 + port;
    }
}
