package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Pins the relay address parse, because a mis-parse is a silent "could not connect". */
public class VoiceRelayAddressTest {

    @Test
    public void aBareHostGetsTheDefaultPort() {
        VoiceRelayAddress address = VoiceRelayAddress.parse("voice.example.com");
        assertEquals("voice.example.com", address.host);
        assertEquals(VoiceRelayAddress.DEFAULT_PORT, address.port);
    }

    @Test
    public void anExplicitPortIsHonoured() {
        VoiceRelayAddress address = VoiceRelayAddress.parse("voice.example.com:5000");
        assertEquals("voice.example.com", address.host);
        assertEquals(5000, address.port);
    }

    @Test
    public void aRawIpv4AddressIsAccepted() {
        VoiceRelayAddress address = VoiceRelayAddress.parse("203.0.113.10");
        assertEquals("203.0.113.10", address.host);
        assertEquals(VoiceRelayAddress.DEFAULT_PORT, address.port);
    }

    @Test
    public void aBracketedIpv6LiteralIsParsedWithItsPort() {
        VoiceRelayAddress address = VoiceRelayAddress.parse("[2001:db8::1]:47902");
        assertEquals("2001:db8::1", address.host);
        assertEquals(47902, address.port);
    }

    @Test
    public void aBareIpv6LiteralIsNotSplitOnItsColons() {
        VoiceRelayAddress address = VoiceRelayAddress.parse("2001:db8::1");
        assertEquals("2001:db8::1", address.host);
        assertEquals(VoiceRelayAddress.DEFAULT_PORT, address.port);
    }

    @Test
    public void surroundingWhitespaceIsTrimmed() {
        VoiceRelayAddress address = VoiceRelayAddress.parse("  voice.example.com:1234  ");
        assertEquals("voice.example.com", address.host);
        assertEquals(1234, address.port);
    }

    @Test
    public void emptyAndUnusableInputsAreRejected() {
        assertNull(VoiceRelayAddress.parse(null));
        assertNull(VoiceRelayAddress.parse(""));
        assertNull(VoiceRelayAddress.parse("   "));
        assertNull(VoiceRelayAddress.parse("host:notaport"));
        assertNull(VoiceRelayAddress.parse("host:0"));
        assertNull(VoiceRelayAddress.parse("host:70000"));
        assertNull(VoiceRelayAddress.parse(":47902"));
        assertNull(VoiceRelayAddress.parse("[2001:db8::1"));
    }

    @Test
    public void displayIsHostColonPort() {
        assertEquals("voice.example.com:47902",
                VoiceRelayAddress.parse("voice.example.com").display());
    }

    @Test
    public void equalityIsByHostAndPort() {
        assertEquals(VoiceRelayAddress.parse("host:1"), VoiceRelayAddress.parse("host:1"));
        assertEquals(VoiceRelayAddress.parse("host:1").hashCode(),
                VoiceRelayAddress.parse("host:1").hashCode());
    }
}
