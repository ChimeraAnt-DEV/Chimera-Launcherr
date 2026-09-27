package org.chimeramc.client.launcher.ui.splash;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for the wordmark shatter: cuts a rectangle into a grid of shards.
 *
 * <p>Separated from the animation so the slice arithmetic -- the shards must tile the source
 * exactly, with no gap or overlap -- is unit-testable on a JVM build machine where no
 * {@code Bitmap} can be created. A one-pixel rounding error here shows up on a device as a
 * visible seam between shards, which is exactly the kind of defect that is painful to debug by eye.
 */
public final class ShatterPlan {

    /** One shard, in source-view coordinates. */
    public static final class Shard {
        public final int left;
        public final int top;
        public final int width;
        public final int height;

        Shard(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }

        public float centerX() {
            return left + width / 2f;
        }

        public float centerY() {
            return top + height / 2f;
        }
    }

    private ShatterPlan() {
    }

    /**
     * Tiles {@code width x height} into {@code columns x rows} shards.
     *
     * <p>The last column and row absorb the remainder of an uneven division, so the shards always
     * cover the full source exactly. Distributing the remainder across every shard instead would
     * leave fractional edges and a visible seam.
     */
    public static List<Shard> grid(int width, int height, int columns, int rows) {
        List<Shard> shards = new ArrayList<>();
        if (width <= 0 || height <= 0 || columns <= 0 || rows <= 0) return shards;

        int cols = Math.min(columns, width);
        int rowCount = Math.min(rows, height);
        int cellWidth = Math.max(1, width / cols);
        int cellHeight = Math.max(1, height / rowCount);

        for (int row = 0; row < rowCount; row++) {
            int top = row * cellHeight;
            int h = (row == rowCount - 1) ? height - top : cellHeight;
            if (h <= 0) continue;
            for (int col = 0; col < cols; col++) {
                int left = col * cellWidth;
                int w = (col == cols - 1) ? width - left : cellWidth;
                if (w <= 0) continue;
                shards.add(new Shard(left, top, w, h));
            }
        }
        return shards;
    }
}
