package org.chimeramc.client.launcher.ui.splash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The splash loader's art and timing rules, pinned without a device.
 *
 * <p>These are exactly the parts that are invisible on a headless build machine: a ragged sprite
 * row, a crack stage that indexes past the array, or a pace rule that lets progress overstate the
 * real work are all silent until someone looks at a phone. The tests make them loud.
 */
public class SplashLoaderTest {

    @Test
    public void everyBlockRowIsExactlyTheGridWidth() {
        assertSpriteIsSquareAndInked(OreCrackSprites.block(), "block");
    }

    @Test
    public void everyCrackStageIsTheSameSquareGrid() {
        String[][] cracks = OreCrackSprites.cracks();
        assertEquals(OreCrackSprites.CRACK_STAGES, cracks.length);
        for (int i = 0; i < cracks.length; i++) {
            assertEquals("stage " + i, OreCrackSprites.GRID, cracks[i].length);
            for (String row : cracks[i]) {
                assertEquals("stage " + i, OreCrackSprites.GRID, row.length());
                for (char c : row.toCharArray()) {
                    assertTrue("stage " + i + " uses only '#' and '.'", c == '#' || c == '.');
                }
            }
        }
    }

    @Test
    public void thePickaxeIsASquareGridToo() {
        String[] sprite = OreCrackSprites.pickaxe();
        assertEquals(OreCrackSprites.GRID, sprite.length);
        for (String row : sprite) {
            assertEquals(OreCrackSprites.GRID, row.length());
        }
    }

    /**
     * The block must be a gem cube, not a stone block.
     *
     * <p>If the sprite ever loses its shaded bevel ring -- the lighter top-left and darker
     * bottom-right cells -- it stops reading as a cut cube and flattens into a plain square, which
     * is exactly the "ugly block" regression. The gem body and its lit facets are pinned too, so a
     * future edit cannot leave the block unshaded or un-crystalled.
     */
    @Test
    public void theBlockReadsAsAShadedGemCube() {
        String[] block = OreCrackSprites.block();
        int body = 0;
        int lit = 0;
        int shaded = 0;
        for (String row : block) {
            for (char c : row.toCharArray()) {
                if (c == 'o') body++;
                else if (c == 'O') lit++;
                else if (c == 'x') shaded++;
            }
        }
        assertTrue("the gem body must dominate the block", body > 100);
        assertTrue("the top-left bevel and facets must be lit", lit > 12);
        assertTrue("the bottom-right bevel must be shaded", shaded > 12);
    }

    /**
     * The pickaxe needs a head, a lit edge and a handle to read as a tool.
     *
     * <p>A handle with no head is a stick and a head with no handle is a lump; both are silent
     * regressions on a build machine. The lit edge is checked because the head is one flat tone
     * without it.
     */
    @Test
    public void thePickaxeHasAHeadALitEdgeAndAHandle() {
        int head = 0;
        int lit = 0;
        int handle = 0;
        for (String row : OreCrackSprites.pickaxe()) {
            for (char c : row.toCharArray()) {
                if (c == '#') head++;
                else if (c == '/') lit++;
                else if (c == 'h') handle++;
            }
        }
        assertTrue("the iron head must be substantial", head > 40);
        assertTrue("the head needs a lit top edge", lit > 8);
        assertTrue("the pickaxe needs a handle", handle > 15);
    }

    /**
     * The pickaxe must sit beside the block, never on top of it.
     *
     * <p>This is the whole point of the two-cell layout and the bug the redesign fixed: anchored at
     * the block's centre, the sprite covered the gem. Checked across many widths so a change that
     * only works at one size is caught.
     */
    @Test
    public void thePickaxeSitsOutsideTheBlock() {
        for (int width = 40; width <= 2000; width += 7) {
            assertTrue("overlap at width " + width, OreLoaderLayout.pickaxeOutsideBlock(width));
        }
    }

    /** Both sprites must fit inside the view horizontally, or they are clipped to nothing. */
    @Test
    public void bothSpritesFitInsideTheView() {
        for (int width = 40; width <= 2000; width += 7) {
            float cell = OreLoaderLayout.cellSize(width);
            float art = cell * OreCrackSprites.GRID;
            assertTrue("pickaxe starts before the view", OreLoaderLayout.pickaxeLeft(width) >= 0f);
            float blockRight = OreLoaderLayout.blockLeft(width) + art;
            assertTrue("block ends past the view at width " + width, blockRight <= width);
        }
    }

    /** A cell is always at least one pixel, even in a degenerate zero-width view. */
    @Test
    public void cellSizeNeverCollapsesToZero() {
        assertEquals(1, OreLoaderLayout.cellSize(0));
        assertEquals(1, OreLoaderLayout.cellSize(-10));
    }

    @Test
    public void stageZeroIsIntactAndTheLastStageIsHeavilyCracked() {
        assertEquals(0, countInk(OreCrackSprites.crack(0)));
        assertTrue("the final stage must actually look broken",
                countInk(OreCrackSprites.crack(OreCrackSprites.CRACK_STAGES - 1)) > 30);
    }

    @Test
    public void crackStageMapsProgressAcrossTheRange() {
        assertEquals(0, OreCrackSprites.crackStageFor(0f));
        assertEquals(1, OreCrackSprites.crackStageFor(0.12f));
        assertEquals(5, OreCrackSprites.crackStageFor(0.5f));
        assertEquals(OreCrackSprites.CRACK_STAGES - 1, OreCrackSprites.crackStageFor(1f));
    }

    @Test
    public void crackStageClampsOutOfRangeInput() {
        // A NaN, a negative and an over-1 must all index a real array rather than throw.
        assertEquals(0, OreCrackSprites.crackStageFor(-5f));
        assertEquals(0, OreCrackSprites.crackStageFor(Float.NaN));
        assertEquals(OreCrackSprites.CRACK_STAGES - 1, OreCrackSprites.crackStageFor(9f));
        // crack() clamps too, so an out-of-range stage returns a real sprite, never null.
        assertNotNull(OreCrackSprites.crack(OreCrackSprites.CRACK_STAGES));
        assertEquals(OreCrackSprites.GRID, OreCrackSprites.crack(OreCrackSprites.CRACK_STAGES).length);
    }

    @Test
    public void theFastPathStillGetsAPacedRunUp() {
        // Init instantly done, but nothing on the clock yet: the display must not jump to 1, or a
        // fast start would flash the whole loader past the user.
        assertEquals(0f, SplashTimeline.displayed(1f, 0L), 0.0001f);
        assertEquals(0.5f, SplashTimeline.displayed(1f, SplashTimeline.PACE_MS / 2), 0.0001f);
        assertEquals(1f, SplashTimeline.displayed(1f, SplashTimeline.PACE_MS), 0.0001f);
    }

    @Test
    public void theDisplayNeverRunsAheadOfRealWork() {
        // Slow init: the load is only a quarter done, so the display must stall at a quarter no
        // matter how much time has passed. This is the property that keeps the number honest.
        assertEquals(0.25f, SplashTimeline.displayed(0.25f, SplashTimeline.PACE_MS * 4), 0.0001f);
    }

    @Test
    public void aSlowInitHoldsInsteadOfCompleting() {
        assertTrue(SplashTimeline.isHolding(0.5f, SplashTimeline.PACE_MS * 2));
        assertFalse(SplashTimeline.isLoadComplete(0.5f, SplashTimeline.PACE_MS * 2));
    }

    @Test
    public void completionRequiresRealWorkAndThePacedDisplay() {
        // Real work done but the pace has not caught up: still not finished.
        assertFalse(SplashTimeline.isLoadComplete(1f, 0L));
        assertTrue(SplashTimeline.isLoadComplete(1f, SplashTimeline.PACE_MS));
        // The pace alone, with no real work, is never completion.
        assertFalse(SplashTimeline.isLoadComplete(0.9f, SplashTimeline.PACE_MS * 10));
    }

    @Test
    public void holdingIsFalseOnceLoadingCompletes() {
        assertFalse(SplashTimeline.isHolding(1f, SplashTimeline.PACE_MS));
    }

    @Test
    public void shardGridTilesTheSourceExactly() {
        // A 6x3 grid over a size that does not divide evenly must still cover every pixel with no
        // gap and no overlap, or the shatter shows seams.
        int width = 101;
        int height = 37;
        java.util.List<ShatterPlan.Shard> shards =
                ShatterPlan.grid(width, height, 6, 3);
        assertEquals(18, shards.size());

        int covered = 0;
        for (ShatterPlan.Shard shard : shards) {
            assertTrue(shard.right() <= width);
            assertTrue(shard.bottom() <= height);
            covered += shard.width * shard.height;
        }
        assertEquals(width * height, covered);
    }

    @Test
    public void theLastShardAbsorbsAnUnevenRemainder() {
        java.util.List<ShatterPlan.Shard> shards = ShatterPlan.grid(64, 32, 6, 3);
        ShatterPlan.Shard last = shards.get(shards.size() - 1);
        assertEquals(64, last.right());
        assertEquals(32, last.bottom());
    }

    @Test
    public void degenerateGridsProduceNoShards() {
        assertTrue(ShatterPlan.grid(0, 10, 6, 3).isEmpty());
        assertTrue(ShatterPlan.grid(10, 0, 6, 3).isEmpty());
        assertTrue(ShatterPlan.grid(-1, -1, 6, 3).isEmpty());
    }

    private static void assertSpriteIsSquareAndInked(String[] sprite, String name) {
        assertEquals(name, OreCrackSprites.GRID, sprite.length);
        for (String row : sprite) {
            assertEquals(name, OreCrackSprites.GRID, row.length());
        }
        assertTrue(name + " must draw something", countInk(sprite) > 20);
    }

    private static int countInk(String[] sprite) {
        int filled = 0;
        for (String row : sprite) {
            for (char c : row.toCharArray()) {
                if (c != '.') filled++;
            }
        }
        return filled;
    }
}
