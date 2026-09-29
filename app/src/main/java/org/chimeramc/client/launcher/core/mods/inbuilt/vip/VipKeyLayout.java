package org.chimeramc.client.core.mods.inbuilt.vip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The keyboard illustration's geometry.
 *
 * <p>The VIP Keyboard tab draws a physical keyboard and highlights whichever key is bound, so the
 * key positions have to be data rather than a bitmap. This is a 60% layout in "key units"
 * (a letter key is 1u wide): five letter rows 15u across plus an arrow cluster, every key placed by
 * accumulating its width along the row. The view multiplies by the available width, so the drawing
 * scales from a phone to a tablet without touching the table.
 *
 * <p>Pure and Android-free: the numeric key codes mirror {@code android.view.KeyEvent} constants
 * so the class neither imports nor initialises any platform type, which is what lets the layout be
 * a JVM test.
 */
public final class VipKeyLayout {

    /** One physical cap: its code, legend, top-left in key units and width in key units. */
    public static final class Key {
        public final int code;
        public final String legend;
        public final float x;
        public final float y;
        public final float width;
        public final boolean modifier;

        Key(int code, String legend, float x, float y, float width, boolean modifier) {
            this.code = code;
            this.legend = legend;
            this.x = x;
            this.y = y;
            this.width = width;
            this.modifier = modifier;
        }
    }

    // Mirrors android.view.KeyEvent.* so the table is testable off-device.
    static final int KEY_GRAVE = 68;
    static final int KEY_MINUS = 69;
    static final int KEY_EQUALS = 70;
    static final int KEY_BACKSPACE = 67;
    static final int KEY_TAB = 61;
    static final int KEY_LEFT_BRACKET = 71;
    static final int KEY_RIGHT_BRACKET = 72;
    static final int KEY_BACKSLASH = 73;
    static final int KEY_CAPS = 115;
    static final int KEY_SEMICOLON = 74;
    static final int KEY_APOSTROPHE = 75;
    static final int KEY_ENTER = 66;
    static final int KEY_SHIFT_LEFT = 59;
    static final int KEY_SHIFT_RIGHT = 60;
    static final int KEY_COMMA = 55;
    static final int KEY_PERIOD = 56;
    static final int KEY_SLASH = 76;
    static final int KEY_CTRL_LEFT = 113;
    static final int KEY_CTRL_RIGHT = 114;
    static final int KEY_META_LEFT = 117;
    static final int KEY_META_RIGHT = 118;
    static final int KEY_ALT_LEFT = 57;
    static final int KEY_ALT_RIGHT = 58;
    static final int KEY_SPACE = 62;
    static final int KEY_MENU = 82;

    static final int KEY_UP = 19;
    static final int KEY_DOWN = 20;
    static final int KEY_LEFT = 21;
    static final int KEY_RIGHT = 22;

    /** Rows of the main board, in key units; the arrow cluster follows as two more bands. */
    private static final int BOARD_ROWS = 5;
    /** Width of the main board in key units; every board row sums to exactly this. */
    private static final float BOARD_WIDTH = 15f;

    private final List<Key> keys;

    public VipKeyLayout() {
        List<Key> out = new ArrayList<>();
        int y = 0;

        float x = 0f;
        x = add(out, KEY_GRAVE, "`", x, y, 1f, false);
        for (char c = '1'; c <= '9'; c++) x = add(out, digit(c), String.valueOf(c), x, y, 1f, false);
        x = add(out, digit('0'), "0", x, y, 1f, false);
        x = add(out, KEY_MINUS, "-", x, y, 1f, false);
        x = add(out, KEY_EQUALS, "=", x, y, 1f, false);
        add(out, KEY_BACKSPACE, "DEL", x, y, 2f, true);

        y++;
        x = 0f;
        x = add(out, KEY_TAB, "TAB", x, y, 1.5f, true);
        for (char c : "QWERTYUIOP".toCharArray()) {
            x = add(out, letter(c), String.valueOf(c), x, y, 1f, false);
        }
        x = add(out, KEY_LEFT_BRACKET, "[", x, y, 1f, false);
        x = add(out, KEY_RIGHT_BRACKET, "]", x, y, 1f, false);
        add(out, KEY_BACKSLASH, "\\", x, y, 1.5f, false);

        y++;
        x = 0f;
        x = add(out, KEY_CAPS, "CAPS", x, y, 1.75f, true);
        for (char c : "ASDFGHJKL".toCharArray()) {
            x = add(out, letter(c), String.valueOf(c), x, y, 1f, false);
        }
        x = add(out, KEY_SEMICOLON, ";", x, y, 1f, false);
        x = add(out, KEY_APOSTROPHE, "'", x, y, 1f, false);
        add(out, KEY_ENTER, "ENTER", x, y, 2.25f, true);

        y++;
        x = 0f;
        x = add(out, KEY_SHIFT_LEFT, "SHIFT", x, y, 2.25f, true);
        for (char c : "ZXCVBNM".toCharArray()) {
            x = add(out, letter(c), String.valueOf(c), x, y, 1f, false);
        }
        x = add(out, KEY_COMMA, ",", x, y, 1f, false);
        x = add(out, KEY_PERIOD, ".", x, y, 1f, false);
        x = add(out, KEY_SLASH, "/", x, y, 1f, false);
        add(out, KEY_SHIFT_RIGHT, "SHIFT", x, y, 2.75f, true);

        y++;
        x = 0f;
        x = add(out, KEY_CTRL_LEFT, "CTRL", x, y, 1.25f, true);
        x = add(out, KEY_META_LEFT, "WIN", x, y, 1.25f, true);
        x = add(out, KEY_ALT_LEFT, "ALT", x, y, 1.25f, true);
        x = add(out, KEY_SPACE, "SPACE", x, y, 6.25f, true);
        x = add(out, KEY_ALT_RIGHT, "ALT", x, y, 1.25f, true);
        x = add(out, KEY_META_RIGHT, "WIN", x, y, 1.25f, true);
        x = add(out, KEY_MENU, "MENU", x, y, 1.25f, true);
        add(out, KEY_CTRL_RIGHT, "CTRL", x, y, 1.25f, true);

        // Arrow cluster, tucked under the right of the board: inverted-T (up above, three below),
        // kept inside the board's key-unit width so it lines up with the rows above.
        y++;
        add(out, KEY_UP, "\u25B2", 13f, y, 1f, false);
        y++;
        add(out, KEY_LEFT, "\u25C0", 12f, y, 1f, false);
        add(out, KEY_DOWN, "\u25BC", 13f, y, 1f, false);
        add(out, KEY_RIGHT, "\u25B6", 14f, y, 1f, false);

        this.keys = Collections.unmodifiableList(out);
    }

    private static float add(List<Key> out, int code, String legend, float x, int y,
                             float width, boolean modifier) {
        out.add(new Key(code, legend, x, y, width, modifier));
        return x + width;
    }

    /** A letter key code: KEYCODE_A..KEYCODE_Z are contiguous, 29..54. */
    private static int letter(char c) {
        return 29 + (Character.toUpperCase(c) - 'A');
    }

    /** A digit key code: KEYCODE_0..KEYCODE_9 are contiguous, 7..16. */
    private static int digit(char c) {
        return 7 + (c - '0');
    }

    /** The main board is this many key units wide; the view scales against it. */
    public float widthUnits() {
        return BOARD_WIDTH;
    }

    /** Number of row bands drawn, including the arrow cluster. */
    public int rowCount() {
        return BOARD_ROWS + 2;
    }

    public List<Key> keys() {
        return keys;
    }

    /** The key a code maps to, or null when the keyboard has no such cap. */
    public Key keyForCode(int code) {
        for (Key key : keys) {
            if (key.code == code) return key;
        }
        return null;
    }
}
