package org.chimeramc.client.launcher.ui.splash;

/**
 * Original pixel art for the splash loader: a crystal-ore block, nine crack stages, and a
 * pickaxe. Nothing here is traced from a game asset; the shapes are drawn to read as the same
 * <em>kind</em> of thing (a faceted ore cube, a fracture spreading across it, a swung tool)
 * without reusing any Mojang texture.
 *
 * <p>Every sprite is a square grid of characters: {@code #} is a filled cell and {@code .} empty.
 * The block additionally uses a small shading palette. Keeping the art as string data means it is
 * reviewable in a diff and unit-testable on a JVM build machine, where no {@code Canvas} exists --
 * which is exactly why the geometry lives here and not in the view.
 */
public final class OreCrackSprites {

    private OreCrackSprites() {
    }

    /**
     * The ore block, on a 16x16 grid.
     *
     * <p>Shading characters: {@code #} is the base stone, {@code +} the lit top-left facet,
     * {@code -} the shaded bottom-right facet, {@code o} the crystal, {@code O} the crystal
     * highlight. Using several tones for the stone rather than one is what makes the cube read as
     * faceted rather than flat.
     */
    private static final String[] BLOCK = {
            "++++++++++++++++",
            "++++++++++++++++",
            "+++###########--",
            "+++##ooo######--",
            "+++#ooOoo#####--",
            "++##ooooo#####--",
            "++##ooo######o--",
            "++##########ooO-",
            "++#######o#ooo--",
            "++#######oo#o---",
            "++#######o------",
            "+#########------",
            "+#o#######------",
            "+oo######-------",
            "--o######-------",
            "----------------",
    };

    /**
     * Nine crack stages, each on the same 16x16 grid.
     *
     * <p>Stage 0 is empty and each later stage adds fracture lines along a path that grows from
     * the top-left facet toward the bottom-right. They are stored in full rather than derived, so
     * a stage can never be a partial composite of an earlier one by accident: what is in the array
     * is exactly what is painted.
     */
    private static final String[][] CRACKS = {
            // Stage 0 -- intact.
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 1.
            {
                    "................",
                    "................",
                    "................",
                    "......#.........",
                    "......#.........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 2.
            {
                    "................",
                    "................",
                    "......#.........",
                    "......##........",
                    "......##........",
                    "......#.........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 3.
            {
                    "................",
                    "................",
                    "......#.........",
                    "......##........",
                    "......###.......",
                    "......###.......",
                    ".......#........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 4 -- a second branch splits off.
            {
                    "................",
                    "................",
                    "......#.........",
                    "......##........",
                    "......###.......",
                    "......####......",
                    ".......##.......",
                    "....#..#........",
                    "....#...........",
                    "....#...........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 5.
            {
                    "................",
                    "................",
                    "......#.........",
                    "......##.#......",
                    "......#####.....",
                    "......#####.....",
                    ".......###......",
                    "....#..##.......",
                    "....##..........",
                    "....##..........",
                    ".....#..........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 6.
            {
                    "................",
                    "................",
                    "......#.........",
                    "......##.#......",
                    "......#####...#.",
                    "......#####..##.",
                    ".......#####.##.",
                    "....#..##...##..",
                    "....##..........",
                    "....###.........",
                    ".....##.........",
                    "......#.........",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            // Stage 7.
            {
                    "................",
                    "......#.........",
                    "......#.........",
                    "......##.#....#.",
                    "......#####..###",
                    "......#####..###",
                    ".......######.##",
                    "....#..##...###.",
                    "....##......###.",
                    "....###......#..",
                    ".....##.........",
                    "......##........",
                    ".......#........",
                    "................",
                    "................",
                    "................",
            },
            // Stage 8.
            {
                    "........#.......",
                    "......###.......",
                    "......###.......",
                    "......##.#....#.",
                    "......#####..###",
                    "......#####.####",
                    ".......#########",
                    "....#..##...####",
                    "....##......####",
                    "....####.....##.",
                    ".....##.#.....#.",
                    "......###.......",
                    ".......##.......",
                    "........#.......",
                    "................",
                    "................",
            },
            // Stage 9 -- about to give.
            {
                    "........#.......",
                    "......####......",
                    "......####.#....",
                    "......#####.#.#.",
                    "......##########",
                    "......##########",
                    "......#########.",
                    "....####...#####",
                    "....###.....####",
                    "....####.....##.",
                    "....#####....##.",
                    ".....####.....#.",
                    "......####......",
                    ".......##.......",
                    "........#.......",
                    "................",
            },
    };

    /**
     * The pickaxe, on a 16x16 grid.
     *
     * <p>{@code #} is the iron head, {@code /} its lit edge, {@code h} the wooden handle. The
     * handle runs to the bottom-left corner so a rotation about the view's pivot reads as a swing
     * from a raised position rather than a spin about the middle of the sprite.
     */
    private static final String[] PICKAXE = {
            "................",
            ".......////.....",
            "......######....",
            ".....#######....",
            "....########...h",
            "....#####.###..h",
            "....####...##.h.",
            "....###.....h.h.",
            "...#.#.....h....",
            "...........h....",
            "..........h.....",
            ".........h......",
            "........h.......",
            ".......h........",
            "......h.........",
            ".....h..........",
    };

    /** The logical side of every sprite; each row string is exactly this long. */
    public static final int GRID = 16;

    /**
     * The number of crack stages; progress maps across {@code 0..CRACK_STAGES-1}.
     *
     * <p>Ten, matching the ten sprites (intact through "about to give"). This must equal the real
     * array length: the loader picks a stage from this value, so a mismatch either throws or hides
     * art that was drawn.
     */
    public static final int CRACK_STAGES = 10;

    /** The ore block sprite, {@code GRID} rows of {@code GRID}. */
    public static String[] block() {
        return BLOCK.clone();
    }

    /** All crack stages, each {@code GRID} rows of {@code GRID}. */
    public static String[][] cracks() {
        return cloneGrid(CRACKS);
    }

    /**
     * The crack stage for a load fraction in {@code [0,1]}.
     *
     * <p>{@code stage = floor(fraction * 9)}, clamped, so progress 100 lands on the final stage
     * and an out-of-range input cannot index past the array.
     */
    public static int crackStageFor(float fraction) {
        if (Float.isNaN(fraction) || fraction <= 0f) return 0;
        if (fraction >= 1f) return CRACK_STAGES - 1;
        int stage = (int) Math.floor(fraction * CRACK_STAGES);
        return Math.max(0, Math.min(stage, CRACK_STAGES - 1));
    }

    /** A single crack stage, clamped to a valid index. */
    public static String[] crack(int stage) {
        int index = Math.max(0, Math.min(stage, CRACK_STAGES - 1));
        return CRACKS[index].clone();
    }

    /** The pickaxe sprite, {@code GRID} rows of {@code GRID}. */
    public static String[] pickaxe() {
        return PICKAXE.clone();
    }

    private static String[][] cloneGrid(String[][] source) {
        String[][] copy = new String[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}
