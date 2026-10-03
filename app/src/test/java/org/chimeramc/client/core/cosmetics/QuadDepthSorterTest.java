package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the preview's painter's order. The bug this guards is the triangular hole: when two boxes'
 * faces are sorted on a single depth key they interleave, and a hidden face paints over a nearer
 * one. Ordering by the owning box first makes that impossible for disjoint convex boxes.
 */
public class QuadDepthSorterTest {

    @Test
    public void aNearerBoxPaintsAfterAFartherOne() {
        // Group 1 is nearer than group 0, so group 0 must come first regardless of face depth.
        assertTrue(QuadDepthSorter.paintsBefore(0f, 5f, 1f, 0f));
        assertFalse(QuadDepthSorter.paintsBefore(1f, 0f, 0f, 5f));
    }

    @Test
    public void withinOneBoxTheFartherFacePaintsFirst() {
        assertTrue(QuadDepthSorter.paintsBefore(2f, 1f, 2f, 3f));
        assertFalse(QuadDepthSorter.paintsBefore(2f, 3f, 2f, 1f));
    }

    /**
     * The exact regression: a far box's near face has a greater face depth than a near box's far
     * face. A single-key sort would put the near box's far face first and let the far box's near
     * face paint over it — a hole. The two-key sort must keep the far box entirely first.
     */
    @Test
    public void aFarBoxsNearFaceNeverOvertakesANearBoxsFarFace() {
        float farBoxGroup = 0f, farBoxFace = 9f;   // far box, but its nearest face
        float nearBoxGroup = 4f, nearBoxFace = 1f; // near box, its farthest face
        assertTrue("far box paints first even though its face depth is greater",
                QuadDepthSorter.paintsBefore(farBoxGroup, farBoxFace, nearBoxGroup, nearBoxFace));
        // And the single-key order would have got it wrong, which is why group is primary.
        assertTrue("the buggy single-key order disagrees", farBoxFace > nearBoxFace);
    }

    @Test
    public void sortOrdersWholeGroupsTogether() {
        // Two boxes of two faces each, deliberately interleaved by face depth.
        int[] order = {0, 1, 2, 3};
        float[] group = {0f, 0f, 1f, 1f};
        float[] depth = {5f, 2f, 4f, 1f};
        QuadDepthSorter.sort(order, group, depth, 4);
        // Group 0 (farther) first, its faces farthest-first; then group 1 likewise. The key
        // invariant: the two groups never interleave (0,1 stay before 2,3).
        assertEquals(1, order[0]); // group 0, face depth 2
        assertEquals(0, order[1]); // group 0, face depth 5
        assertEquals(3, order[2]); // group 1, face depth 1
        assertEquals(2, order[3]); // group 1, face depth 4
        assertTrue("group 0's faces stay together", order[0] < 2 && order[1] < 2);
        assertTrue("group 1's faces stay together", order[2] >= 2 && order[3] >= 2);
    }

    @Test
    public void compareIsZeroOnlyForIdenticalKeys() {
        assertEquals(0, QuadDepthSorter.compare(2f, 3f, 2f, 3f));
        assertTrue(QuadDepthSorter.compare(1f, 0f, 2f, 0f) < 0);
        assertTrue(QuadDepthSorter.compare(2f, 0f, 1f, 0f) > 0);
        assertTrue(QuadDepthSorter.compare(1f, 0f, 1f, 1f) < 0);
    }
}
