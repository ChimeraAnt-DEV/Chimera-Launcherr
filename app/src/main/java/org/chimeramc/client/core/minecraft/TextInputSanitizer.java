package org.chimeramc.client.core.minecraft;

/**
 * Keeps control bytes away from the game's key handler.
 *
 * <p>The launcher bridges the Android IME into the game. Two paths carry a character:
 *
 * <ul>
 *   <li>{@code commitText} → a registered mod text callback, or, with none, a synthetic key
 *       press carrying the code point ({@link #isKeyFallbackCodePoint}).
 *   <li>a hardware key → {@code KeyEvent.getUnicodeChar()}, forwarded verbatim to the game.
 * </ul>
 *
 * <p>The game's key handler takes a plain printable code and treats everything else as an
 * unmapped input. Android hands out control code points for dedicated keys (Enter is {@code
 * '\n'} on many IMEs, plus backspace/tab/escape), and feeding one of those through as a
 * "character" is what took the instance down when the player pressed Enter in chat. A control
 * code point therefore reads as "no character" (0) on the hardware path, and is dropped on the
 * synthetic-key fallback — the dedicated key code still delivers the Enter press itself.
 *
 * <p>Pure and Android-free so the rule is unit-testable without a device.
 */
public final class TextInputSanitizer {

    private TextInputSanitizer() {}

    /**
     * The value to forward as a key event's unicode char.
     *
     * <p>Returns 0 for a control or non-printable code point, so the game sees "no character"
     * rather than a byte it cannot map; anything printable (including non-ASCII text) passes
     * through unchanged, because the IME and the game both expect real characters there.
     */
    public static int sanitizeUnicodeChar(int codePoint) {
        if (codePoint <= 0) return 0;
        if (codePoint < 0x20) return 0;
        if (codePoint == 0x7f) return 0;
        return codePoint;
    }

    /**
     * Whether a committed character may be replayed as a key press when no mod consumed it.
     *
     * <p>Only printable ASCII: the fallback reuses the key path, which is defined for that
     * range. A control byte is dropped, and a multi-byte character is left to the IME rather
     * than pushed through a channel that cannot carry it.
     */
    public static boolean isKeyFallbackCodePoint(int codePoint) {
        return codePoint >= 0x20 && codePoint <= 0x7e;
    }
}
