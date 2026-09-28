package org.chimeramc.client.core.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the rule that keeps control code points off the game's key path.
 *
 * <p>The regression is concrete: Enter arrives from several IMEs as {@code '\n'}, and forwarding
 * that as a character alongside the key press took the instance down when chat was sent.
 */
public class TextInputSanitizerTest {

    @Test
    public void enterControlPointReadsAsNoCharacter() {
        assertEquals(0, TextInputSanitizer.sanitizeUnicodeChar('\n'));
    }

    @Test
    public void otherControlPointsAreDropped() {
        assertEquals(0, TextInputSanitizer.sanitizeUnicodeChar(0));
        assertEquals(0, TextInputSanitizer.sanitizeUnicodeChar('\t'));
        assertEquals(0, TextInputSanitizer.sanitizeUnicodeChar('\r'));
        assertEquals(0, TextInputSanitizer.sanitizeUnicodeChar(0x1b));
        assertEquals(0, TextInputSanitizer.sanitizeUnicodeChar(0x7f));
    }

    @Test
    public void printableAsciiPassesThrough() {
        assertEquals('a', TextInputSanitizer.sanitizeUnicodeChar('a'));
        assertEquals('Z', TextInputSanitizer.sanitizeUnicodeChar('Z'));
        assertEquals(' ', TextInputSanitizer.sanitizeUnicodeChar(' '));
        assertEquals('!', TextInputSanitizer.sanitizeUnicodeChar('!'));
    }

    @Test
    public void nonAsciiTextIsPreserved() {
        assertEquals(0x00e9, TextInputSanitizer.sanitizeUnicodeChar(0x00e9));
        assertEquals(0x1f600, TextInputSanitizer.sanitizeUnicodeChar(0x1f600));
    }

    @Test
    public void keyFallbackAcceptsPrintableAsciiOnly() {
        assertTrue(TextInputSanitizer.isKeyFallbackCodePoint('a'));
        assertTrue(TextInputSanitizer.isKeyFallbackCodePoint('~'));
        assertFalse(TextInputSanitizer.isKeyFallbackCodePoint('\n'));
        assertFalse(TextInputSanitizer.isKeyFallbackCodePoint(' ' - 1));
        assertFalse(TextInputSanitizer.isKeyFallbackCodePoint(0x00e9));
    }
}
