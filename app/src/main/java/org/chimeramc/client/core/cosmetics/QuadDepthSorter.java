package org.chimeramc.client.core.cosmetics;

/**
 * Orders projected quads for a painter's-algorithm draw by two keys.
 *
 * <p>A single depth key over every face of every box is what produced the preview's triangular
 * holes. Two faces belonging to different boxes can have almost equal centroid depth at some yaw,
 * and a humanoid is full of such pairs (the head sits on the torso, an arm touches the torso).
 * When the sort interleaves them, a face that should be hidden paints over a nearer one, or is
 * skipped — which reads exactly as a missing triangle.
 *
 * <p>Ordering by the owning box first keeps a convex box's six faces together, so two boxes can
 * never interleave. That is exact for a scene of disjoint convex boxes, which the player model is:
 * no two of its boxes overlap (they only share faces), so "which box is in front" is a total
 * order and the per-face key only needs to break ties <em>within</em> one box.
 *
 * <p>Pure and allocation-free, so it is unit-testable without Android and safe on the per-frame
 * draw path.
 */
public final class QuadDepthSorter {

    private QuadDepthSorter() {
    }

    /**
     * Whether quad {@code a} must be painted before quad {@code b} (i.e. {@code a} is farther).
     *
     * @param groupDepthA owning box's depth for {@code a}
     * @param depthA      {@code a}'s own face depth
     */
    public static boolean paintsBefore(float groupDepthA, float depthA,
                                       float groupDepthB, float depthB) {
        if (groupDepthA != groupDepthB) return groupDepthA < groupDepthB;
        return depthA < depthB;
    }

    /** Comparator-style result for {@link #paintsBefore}. */
    public static int compare(float groupDepthA, float depthA,
                              float groupDepthB, float depthB) {
        if (groupDepthA != groupDepthB) return Float.compare(groupDepthA, groupDepthB);
        return Float.compare(depthA, depthB);
    }

    /**
     * Insertion-sorts {@code order[0..count)} in place, farthest group first.
     *
     * <p>{@code order} must start as the identity permutation. The count is small and fixed (the
     * cape mesh, the model's faces), so insertion sort is both the simplest and the cheapest
     * choice, and it allocates nothing.
     */
    public static void sort(int[] order, float[] groupDepth, float[] depth, int count) {
        for (int i = 1; i < count; i++) {
            int key = order[i];
            float keyGroup = groupDepth[key];
            float keyDepth = depth[key];
            int j = i - 1;
            while (j >= 0 && paintsBefore(keyGroup, keyDepth,
                    groupDepth[order[j]], depth[order[j]])) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }
    }
}
