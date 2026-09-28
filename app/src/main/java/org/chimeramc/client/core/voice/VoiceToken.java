package org.chimeramc.client.core.voice;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Derives the signed join token a relay expects, from the shared secret the player was given.
 *
 * <p>The secret is what the user holds; it never leaves the device. Each connection presents a
 * short-lived token signed with it instead, so a token captured off the wire is useless once it
 * expires and cannot be replayed forever. The token also carries a device id, so a peer cannot be
 * impersonated.
 *
 * <p>The format is fixed by the server's verifier
 * ({@code server/voice-relay/token.go}) and pinned by a golden-vector test, because a mismatch
 * here is not a compile error -- it is a relay that refuses every connection.
 *
 * <p>Format: {@code v1.<base64url(deviceId|expiryUnix)>.<base64url(hmacSHA256(secret, "v1|" + payload))>}.
 */
public final class VoiceToken {

    /** The token format version; the signature covers this string so formats cannot be confused. */
    static final String VERSION = "v1";

    /** The default lifetime of a derived token; short enough that a leaked one ages out quickly. */
    public static final long DEFAULT_TTL_SECONDS = 6 * 60 * 60;

    /** The longest lifetime the server will accept; minting beyond it is rejected server-side. */
    public static final long MAX_TTL_SECONDS = 30L * 24 * 60 * 60;

    private VoiceToken() {
    }

    /**
     * Mints a token for a device, valid for {@code ttlSeconds} from {@code nowEpochSeconds}.
     *
     * <p>Returns null when there is no secret to sign with, so the caller can fall back to a plain
     * password rather than sending an unsigned token the server would reject.
     */
    public static String issue(String secret, String deviceId, long ttlSeconds, long nowEpochSeconds) {
        if (secret == null || secret.isEmpty()) return null;
        long ttl = ttlSeconds;
        if (ttl <= 0 || ttl > MAX_TTL_SECONDS) ttl = MAX_TTL_SECONDS;
        String device = sanitizeDeviceId(deviceId);
        String payload = device + "|" + (nowEpochSeconds + ttl);
        String signature = hmacBase64Url(secret, VERSION + "|" + payload);
        if (signature == null) return null;
        return VERSION + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8)) + "."
                + signature;
    }

    /**
     * Verifies a token locally and returns its device id, or null when it is malformed, expired,
     * or not signed with this secret. Used by the tests to prove the client and the server agree
     * without a network.
     */
    public static String verify(String secret, String token, long nowEpochSeconds) {
        if (secret == null || secret.isEmpty() || token == null) return null;
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) return null;
        byte[] payloadBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String expected = hmacBase64Url(secret, VERSION + "|"
                + new String(payloadBytes, StandardCharsets.UTF_8));
        if (expected == null || !constantTimeEquals(expected, parts[2])) return null;

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        int sep = payload.lastIndexOf('|');
        if (sep <= 0) return null;
        String device = payload.substring(0, sep);
        long expiry;
        try {
            expiry = Long.parseLong(payload.substring(sep + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (device.isEmpty() || expiry < nowEpochSeconds) return null;
        return device;
    }

    private static String hmacBase64Url(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** A comparison that does not stop at the first differing byte. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) return false;
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }

    /** Keeps the device id printable, separator-free and bounded, matching the server's rule. */
    static String sanitizeDeviceId(String id) {
        if (id == null) return "device";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < 0x21 || c == '|' || c == 0x7f) continue;
            out.append(c);
        }
        if (out.length() == 0) return "device";
        return out.length() > 64 ? out.substring(0, 64) : out.toString();
    }
}
