package org.chimeramc.client.launcher.ui.splash;

/**
 * The splash loader's geometry, as pure arithmetic.
 *
 * <p>The view draws three things: a gem block, the fracture on it, and a pickaxe. Their positions
 * are the part that goes wrong silently on a headless build machine -- a pickaxe anchored at the
 * block's centre covers the gem, and a sprite laid out past the view's edge vanishes -- so the
 * arithmetic lives here and is pinned by a JVM test, while {@code OreBlockLoaderView} only paints.
 *
 * <p>The view is laid out as <b>two square cells plus a gap</b>: the pickaxe in the left cell, the
 * block in the right. That is the whole point of the design -- the tool is beside the cube, never
 * on top of it -- so the boxes computed here must not overlap. {@link #pickaxeOutsideBlock} is the
 * invariant, expressed as a check so a future tweak to the gap cannot quietly reintroduce the
 * overlap.
 */
public final class OreLoaderLayout {

    /**
     * The empty space between the two cells, in sprite-width fractions.
     *
     * <p>A small positive gap keeps the pair reading as one composition while leaving the block's
     * silhouette unbroken. Zero would butt them together and make the tool look attached.
     */
    public static final float GAP_FRACTION = 0.14f;

    private OreLoaderLayout() {
    }

    /** The total width of the art group, in sprite widths (pickaxe + gap + block). */
    public static float groupWidthFraction() {
        return 1f + GAP_FRACTION + 1f;
    }

    /**
     * The side of one sprite cell in pixels.
     *
     * <p>Derived from the view's width alone, because the width is what carries the two cells and
     * the gap; the height only centres the art vertically. A guard of 1 keeps a zero-width view
     * from producing a zero cell, which would make every draw call invisible.
     */
    public static int cellSize(int viewWidth) {
        int side = (int) (viewWidth / (OreCrackSprites.GRID * groupWidthFraction()));
        return Math.max(1, side);
    }

    /** The left edge of the block cell, in pixels. */
    public static float blockLeft(int viewWidth) {
        float cell = cellSize(viewWidth);
        float art = cell * OreCrackSprites.GRID;
        float gap = art * GAP_FRACTION;
        float groupWidth = art * 2f + gap;
        float groupLeft = (viewWidth - groupWidth) / 2f;
        return groupLeft + art + gap;
    }

    /** The left edge of the pickaxe cell, in pixels. */
    public static float pickaxeLeft(int viewWidth) {
        float cell = cellSize(viewWidth);
        float art = cell * OreCrackSprites.GRID;
        float gap = art * GAP_FRACTION;
        float groupWidth = art * 2f + gap;
        return (viewWidth - groupWidth) / 2f;
    }

    /**
     * The top edge shared by both sprites, so they sit on one line, in pixels.
     *
     * <p>The cell size comes from the <em>width</em> (which carries both cells and the gap), so the
     * height is only used to centre the row vertically. Passing the height in as if it set the cell
     * size is the mistake this signature avoids: a wide, short view would compute a cell larger
     * than the view can hold and clip the sprites top and bottom.
     */
    public static float top(int viewWidth, int viewHeight) {
        float art = cellSize(viewWidth) * (float) OreCrackSprites.GRID;
        return (viewHeight - art) / 2f;
    }

    /**
     * Whether the pickaxe cell and the block cell are disjoint.
     *
     * <p>This is the behaviour the whole layout exists to guarantee: the pickaxe animates beside
     * the block rather than over it, so a regression here is visible on a phone as a tool sitting
     * on the gem. Tested as an explicit gap rather than a mere non-overlap so a shrunken gap that
     * merely touches is caught too.
     */
    public static boolean pickaxeOutsideBlock(int viewWidth) {
        float cell = cellSize(viewWidth);
        float art = cell * OreCrackSprites.GRID;
        float pickaxeRight = pickaxeLeft(viewWidth) + art;
        float blockLeft = blockLeft(viewWidth);
        return pickaxeRight < blockLeft;
    }
}
