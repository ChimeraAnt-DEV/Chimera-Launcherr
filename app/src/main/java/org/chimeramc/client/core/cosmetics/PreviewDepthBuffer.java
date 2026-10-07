package org.chimeramc.client.core.cosmetics;

import java.util.Arrays;

/**
 * A screen-aligned depth buffer for the cosmetics preview's painter's-algorithm renderer.
 *
 * <p>The preview has no hardware depth buffer, so a painter's algorithm orders draws by hand. That
 * works within one convex box but not between the cape and the body: the cape hangs just outside
 * the back plane while the body spans a volume in front of it, so for most camera angles part of
 * the cloth is nearer than the skin and part is behind it. A whole-object before/after choice is
 * therefore wrong for any camera that is not squarely behind or in front, and the symptom is the
 * cape's far quads painting over the torso — the cape "showing through the skin".
 *
 * <p>This buffer fixes that exactly: the body's opaque faces are recorded first (with their
 * projected depth), and a cape pixel is kept only where its depth is at least the body's depth at
 * that pixel. It is a max-depth buffer (nearest surface wins), which is what a normal z-buffer is;
 * an empty pixel never occludes.
 *
 * <p>Pure integer/float maths and Android-free, so the occlusion rule is unit-testable without a
 * device.
 */
public final class PreviewDepthBuffer {

    private int width;
    private int height;
    /**
     * Nearest recorded surface depth per pixel, floored to an int; 0 means "nothing here".
     *
     * <p>Smaller depth is <b>nearer</b> — the camera projects `distance - z2`, so the nearest
     * surface has the smallest value (SkinModelTest pins this). This buffer therefore keeps the
     * <em>minimum</em> depth seen at each pixel.
     */
    private int[] depth;

    /** Sizes the buffer to the frame and clears it. Reuses the array across frames. */
    public void prepare(int frameWidth, int frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0) {
            width = 0;
            height = 0;
            depth = null;
            return;
        }
        if (depth == null || width != frameWidth || height != frameHeight) {
            width = frameWidth;
            height = frameHeight;
            depth = new int[frameWidth * frameHeight];
        } else {
            Arrays.fill(depth, 0);
        }
    }

    public boolean isEmpty() {
        return depth == null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Records a projected quad's depth over the pixels it covers, keeping the nearest surface.
     * The vertices are in grid order {@code [TL, TR, BL, BR]} — the same order
     * {@link SkinModel.Box#faceCorners} returns.
     */
    public void accept(float[] verts, float depthValue) {
        if (depth == null) return;
        final int z = Math.max(0, (int) Math.floor(depthValue));

        float minX = Math.min(Math.min(verts[0], verts[2]), Math.min(verts[4], verts[6]));
        float maxX = Math.max(Math.max(verts[0], verts[2]), Math.max(verts[4], verts[6]));
        float minY = Math.min(Math.min(verts[1], verts[3]), Math.min(verts[5], verts[7]));
        float maxY = Math.max(Math.max(verts[1], verts[3]), Math.max(verts[5], verts[7]));

        int ix0 = Math.max(0, (int) Math.floor(minX));
        int ix1 = Math.min(width - 1, (int) Math.ceil(maxX));
        int iy0 = Math.max(0, (int) Math.floor(minY));
        int iy1 = Math.min(height - 1, (int) Math.ceil(maxY));
        if (ix1 < ix0 || iy1 < iy0) return;

        // Perimeter in grid order: TL -> TR -> BR -> BL. A convex projected quad contains a point
        // when the point is on the same side of all four edges.
        final float ax = verts[0], ay = verts[1];
        final float bx = verts[2], by = verts[3];
        final float cx = verts[6], cy = verts[7];
        final float dx = verts[4], dy = verts[5];

        for (int py = iy0; py <= iy1; py++) {
            float fy = py + 0.5f;
            int row = py * width;
            for (int px = ix0; px <= ix1; px++) {
                float fx = px + 0.5f;
                float c1 = (bx - ax) * (fy - ay) - (by - ay) * (fx - ax);
                float c2 = (cx - bx) * (fy - by) - (cy - by) * (fx - bx);
                float c3 = (dx - cx) * (fy - cy) - (dy - cy) * (fx - cx);
                float c4 = (ax - dx) * (fy - dy) - (ay - dy) * (fx - dx);
                boolean inside = (c1 >= 0 && c2 >= 0 && c3 >= 0 && c4 >= 0)
                        || (c1 <= 0 && c2 <= 0 && c3 <= 0 && c4 <= 0);
                if (!inside) continue;
                // Smaller depth is nearer, so the nearest body surface is the minimum here. Zero
                // marks "unrecorded", so the first real (positive) depth simply fills it.
                int current = depth[row + px];
                if (current == 0 || z < current) depth[row + px] = z;
            }
        }
    }

    /**
     * Whether a surface at {@code depthValue} is visible at a pixel, i.e. it is at least as near as
     * whatever the body recorded there. An empty pixel (or a null buffer) returns true — nothing
     * occludes the cape off the body's silhouette.
     *
     * <p>Smaller depth is nearer, so the surface is visible when its depth is less than or equal to
     * the body's stored (nearest) depth. A cape that is farther than the skin at a pixel — i.e. the
     * skin is in front of it — is rejected, which is exactly what stops the cape showing through.
     */
    public boolean test(float x, float y, float depthValue) {
        if (depth == null) return true;
        int px = (int) x;
        int py = (int) y;
        if (px < 0 || py < 0 || px >= width || py >= height) return true;
        int stored = depth[py * width + px];
        // Zero means "no body surface here" — the camera projection floors at a small positive
        // depth, so a real surface never records 0. An unrecorded pixel occludes nothing.
        if (stored == 0) return true;
        return (int) Math.floor(depthValue) <= stored;
    }
}
