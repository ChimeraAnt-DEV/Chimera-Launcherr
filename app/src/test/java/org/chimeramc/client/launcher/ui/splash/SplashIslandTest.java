package org.chimeramc.client.launcher.ui.splash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/**
 * Pins the floating-island silhouette: it has a grass cap, a tapering underside, a tree, and it
 * turns as a turntable. The shape is the whole point of the hero backdrop, so it is asserted here
 * rather than only being visible on a device.
 */
public class SplashIslandTest {

    private static SplashIsland seeded() {
        SplashIsland island = new SplashIsland();
        island.seed(new Random(), 0x15EAF00DL);
        return island;
    }

    @Test
    public void itBuildsWithoutOverflowingItsCap() {
        SplashIsland island = seeded();
        assertTrue("island should have blocks", island.blockCount() > 0);
        assertTrue("island must stay within MAX_BLOCKS",
                island.blockCount() <= SplashIsland.MAX_BLOCKS);
    }

    @Test
    public void theCapIsGrassAtTheTopOfTheModel() {
        SplashIsland island = seeded();
        int grass = 0;
        for (int i = 0; i < island.blockCount(); i++) {
            if (island.kind(i) == SplashIsland.KIND_GRASS) {
                grass++;
                assertEquals("grass lives at y=0", 0, island.y(i));
            }
        }
        assertTrue("there should be a grass cap", grass >= 12);
    }

    @Test
    public void theUndersideTapersToAStonePoint() {
        SplashIsland island = seeded();
        // The lowest block is stone, and it is narrower than the cap — the island's point.
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < island.blockCount(); i++) {
            lowest = Math.min(lowest, island.y(i));
        }
        boolean pointIsStone = false;
        int widest = 0;
        for (int i = 0; i < island.blockCount(); i++) {
            if (island.y(i) == lowest && island.kind(i) == SplashIsland.KIND_STONE) {
                pointIsStone = true;
            }
            widest = Math.max(widest, Math.abs(island.x(i)));
        }
        assertTrue("the underside should taper to stone", pointIsStone);
        assertTrue("the cap should be wider than a single block", widest >= 3);
    }

    @Test
    public void itCarriesATree() {
        SplashIsland island = seeded();
        int logs = 0;
        int leaves = 0;
        for (int i = 0; i < island.blockCount(); i++) {
            if (island.kind(i) == SplashIsland.KIND_LOG) logs++;
            if (island.kind(i) == SplashIsland.KIND_LEAVES) leaves++;
        }
        assertTrue("a tree needs a trunk", logs >= 2);
        assertTrue("a tree needs a canopy", leaves >= 4);
    }

    @Test
    public void theIslandTurnsAsATurntable() {
        SplashIsland island = new SplashIsland();
        assertEquals(0f, island.yaw(0f), 0.001f);
        assertTrue(island.yaw(4f) > 0f);
        // A full turn returns to the start.
        float period = 360f / SplashIsland.SPIN_DEG_PER_SEC;
        assertEquals(0f, island.yaw(period), 0.01f);
    }

    @Test
    public void theShapeIsStableForAGivenSeed() {
        SplashIsland a = seeded();
        SplashIsland b = seeded();
        assertEquals(a.blockCount(), b.blockCount());
        for (int i = 0; i < a.blockCount(); i++) {
            assertEquals(a.x(i), b.x(i));
            assertEquals(a.y(i), b.y(i));
            assertEquals(a.z(i), b.z(i));
            assertEquals(a.kind(i), b.kind(i));
        }
    }
}
