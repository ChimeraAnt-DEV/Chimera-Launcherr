package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Pins the join-token format the Go relay verifies.
 *
 * <p>The token is built on one side and checked on the other, so a wrong HMAC input, base64 flavour
 * or separator does not fail a build -- it fails every connection at runtime. The golden literal
 * below is produced by {@code server/voice-relay/token.go} and cross-checked in
 * {@code token_test.go}; if the two formats drift, one suite fails instead of the relay silently
 * rejecting everyone.
 */
public class VoiceTokenTest {

    private static final String SECRET = "correct horse battery staple";
    /** 2023-11-15T00:00:00Z + 6h = 1700021600. */
    private static final long NOW = 1_700_000_000L;
    private static final String GOLDEN =
            "v1.ZGV2aWNlLTEyM3wxNzAwMDIxNjAw.MUlxqkbsc9MPGxhmOc1LfqroTszlz8iTUeustnpABHI";

    @Test
    public void mintingMatchesTheGoldenTokenTheRelayVerifies() {
        assertEquals(GOLDEN, VoiceToken.issue(SECRET, "device-123", 6 * 60 * 60, NOW));
    }

    @Test
    public void theGoldenTokenVerifiesAndYieldsItsDevice() {
        assertEquals("device-123", VoiceToken.verify(SECRET, GOLDEN, NOW));
    }

    @Test
    public void anExpiredTokenIsRejected() {
        assertNull(VoiceToken.verify(SECRET, GOLDEN, NOW + 6 * 60 * 60 + 1));
    }

    @Test
    public void aTokenSignedWithAnotherSecretIsRejected() {
        String other = VoiceToken.issue("a different secret", "device-123", 3600, NOW);
        assertNotNull(other);
        assertNull(VoiceToken.verify(SECRET, other, NOW));
    }

    @Test
    public void noSecretMeansNoToken() {
        assertNull(VoiceToken.issue("", "device-123", 3600, NOW));
        assertNull(VoiceToken.issue(null, "device-123", 3600, NOW));
    }

    @Test
    public void anOverlongTtlIsClampedToTheServerMaximum() {
        // Requesting a decade must not produce a token the server rejects as too-long-lived.
        String decade = VoiceToken.issue(SECRET, "d", 10L * 365 * 24 * 60 * 60, NOW);
        assertNotNull(decade);
        assertEquals("d", VoiceToken.verify(SECRET, decade, NOW));
        assertNull(VoiceToken.verify(SECRET, decade,
                NOW + VoiceToken.MAX_TTL_SECONDS + 1));
    }

    @Test
    public void aSeparatorOrControlByteInTheDeviceIdIsStripped() {
        String token = VoiceToken.issue(SECRET, "a|b\tc", 3600, NOW);
        assertNotNull(token);
        String device = VoiceToken.verify(SECRET, token, NOW);
        assertNotNull(device);
        assertEquals(-1, device.indexOf('|'));
        assertEquals(-1, device.indexOf('\t'));
    }

    @Test
    public void malformedTokensAreRejected() {
        for (String bad : new String[]{
                "", "nope", "v1.only-two-parts", "v9.aaaa.bbbb", "v1.!!!.???", "v1...x"}) {
            assertNull("accepted " + bad, VoiceToken.verify(SECRET, bad, NOW));
        }
    }
}
