package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * D-pad/analogue navigation over the Mod Menu's module grid.
 *
 * <p>The overlay lives in a window the d-pad cannot focus (its root is deliberately unfocusable so
 * the platform stops painting a white focus wash over the game), so controller navigation is
 * computed here and applied to the list rather than left to the framework's focus system.
 *
 * <p>The grid is not a plain fixed-width table: a group header spans the whole row (see the
 * adapter's {@code SpanSizeLookup}), so a header occupies a row of its own. Column arithmetic that
 * ignored that would step onto a header and appear to skip a row, so the rows are laid out
 * explicitly here.
 *
 * <p>Pure and Android-free, so every edge -- moving off the end, a run of header rows, a menu with
 * nothing selectable -- is a JVM test rather than something found on a device mid-fight.
 */
public final class ModMenuNavigation {

    /** No item is focused yet; the next directional press selects the first selectable item. */
    public static final int NONE = -1;

    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private ModMenuNavigation() {
    }

    /**
     * The index a directional press should move to.
     *
     * @param current    the focused index, or {@link #NONE}
     * @param columns    the grid's column count (1 for a list)
     * @param fullRow    one flag per item; true for a group header, which spans its own row
     * @param selectable one flag per item; a header or an unavailable module is not selectable
     * @return the new index, or {@link #NONE} when nothing can be focused
     */
    public static int move(int current, int columns, boolean[] fullRow, boolean[] selectable,
                           Direction direction) {
        if (selectable == null || selectable.length == 0) return NONE;
        if (columns < 1) columns = 1;
        if (!isSelectable(selectable, current)) return firstSelectable(selectable);

        List<int[]> rows = buildRows(columns, fullRow, selectable.length);
        int rowIndex = rowOf(rows, current);
        if (rowIndex < 0) return current;

        switch (direction) {
            case LEFT:
                return previousSelectable(selectable, current);
            case RIGHT:
                return nextSelectable(selectable, current);
            case UP:
                return rowStep(rows, selectable, rowIndex, current, -1);
            case DOWN:
                return rowStep(rows, selectable, rowIndex, current, 1);
            default:
                return current;
        }
    }

    /**
     * Lays the items out into rows.
     *
     * <p>A full-row item becomes a row of its own; ordinary items fill rows of {@code columns}.
     */
    private static List<int[]> buildRows(int columns, boolean[] fullRow, int length) {
        List<int[]> rows = new ArrayList<>();
        int i = 0;
        while (i < length) {
            if (fullRow != null && i < fullRow.length && fullRow[i]) {
                rows.add(new int[]{i});
                i++;
                continue;
            }
            List<Integer> row = new ArrayList<>();
            while (i < length && row.size() < columns
                    && !(fullRow != null && i < fullRow.length && fullRow[i])) {
                row.add(i);
                i++;
            }
            int[] array = new int[row.size()];
            for (int j = 0; j < array.length; j++) array[j] = row.get(j);
            rows.add(array);
        }
        return rows;
    }

    private static int rowOf(List<int[]> rows, int index) {
        for (int r = 0; r < rows.size(); r++) {
            for (int value : rows.get(r)) {
                if (value == index) return r;
            }
        }
        return -1;
    }

    /**
     * Moves to the nearest selectable item in the next row that has one.
     *
     * <p>Rows with nothing selectable (a group header, a whole row of unavailable modules) are
     * skipped rather than swallowing the press, so the selection always lands somewhere new.
     */
    private static int rowStep(List<int[]> rows, boolean[] selectable, int rowIndex, int current,
                               int delta) {
        int column = columnOf(rows.get(rowIndex), current);
        for (int r = rowIndex + delta; r >= 0 && r < rows.size(); r += delta) {
            int found = nearestSelectable(rows.get(r), selectable, column);
            if (found != NONE) return found;
        }
        return current;
    }

    private static int nearestSelectable(int[] row, boolean[] selectable, int column) {
        if (row.length == 0) return NONE;
        int clamped = Math.min(column, row.length - 1);
        if (isSelectable(selectable, row[clamped])) return row[clamped];
        for (int offset = 1; offset < row.length; offset++) {
            int right = column + offset;
            if (right < row.length && isSelectable(selectable, row[right])) return row[right];
            int left = column - offset;
            if (left >= 0 && isSelectable(selectable, row[left])) return row[left];
        }
        return NONE;
    }

    private static int columnOf(int[] row, int index) {
        for (int i = 0; i < row.length; i++) {
            if (row[i] == index) return i;
        }
        return 0;
    }

    private static int previousSelectable(boolean[] selectable, int current) {
        for (int i = current - 1; i >= 0; i--) {
            if (selectable[i]) return i;
        }
        return current;
    }

    private static int nextSelectable(boolean[] selectable, int current) {
        for (int i = current + 1; i < selectable.length; i++) {
            if (selectable[i]) return i;
        }
        return current;
    }

    private static int firstSelectable(boolean[] selectable) {
        for (int i = 0; i < selectable.length; i++) {
            if (selectable[i]) return i;
        }
        return NONE;
    }

    private static boolean isSelectable(boolean[] selectable, int index) {
        return index >= 0 && index < selectable.length && selectable[index];
    }
}
