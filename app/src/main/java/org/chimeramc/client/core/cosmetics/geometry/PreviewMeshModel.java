package org.chimeramc.client.core.cosmetics.geometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a parsed {@link BedrockGeometry} into resolved, renderable cubes — the
 * {@code [BedrockGeometryParser] -> [PreviewMeshModel]} stage of the preview pipeline.
 *
 * <p>Bedrock geometry is a <em>bone hierarchy</em>, not a flat box list: a cube's origin is stated in
 * its bone's local space, the bone rotates it about a pivot, and every ancestor bone's rotation then
 * applies on top. Flattening that wrong is what makes an authored hat sit inside the head or a pet's
 * leg detach from its hip, so this class resolves the whole chain once (per model change, never per
 * frame) and hands the renderer plain world-space corners.
 *
 * <p>It also owns the UV rules, which are the other thing a naive reader gets wrong:
 * <ul>
 *   <li>A cube carries either a per-face UV map (Blockbench's default for these assets) or a box-UV
 *       origin + size; the box-UV layout is derived here.</li>
 *   <li>A UV size may be <b>negative</b>, which mirrors the sample. That sign is preserved as
 *       {@code flipH}/{@code flipV} so the renderer can flip the texture instead of reading it
 *       backwards.</li>
 * </ul>
 *
 * <p>Pure Java, no Android types, so the whole resolution is unit-testable: a bone parented to a
 * rotated parent, a negative UV size and a cube-local rotation can all be asserted without a device.
 */
public final class PreviewMeshModel {

    /** Cube corner order used throughout: index = {@code ix | iy<<1 | iz<<2}, 0 = min, 1 = max. */
    private static final int[][] FACE_CORNER_BITS = {
            // TOP (+y)
            {0, 1, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1},
            // BOTTOM (-y)
            {0, 0, 1, 1, 0, 1, 0, 0, 0, 1, 0, 0},
            // LEFT (-x)
            {0, 1, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0},
            // RIGHT (+x)
            {1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 0, 1},
            // FRONT (+z)
            {0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1},
            // BACK (-z)
            {1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0},
    };

    private PreviewMeshModel() {
    }

    /** A resolved cube: world-space corners plus the source texture rectangle for each face. */
    public static final class Box {
        public final String bone;
        /** Eight corners in model space, indexed by {@code ix | iy<<1 | iz<<2}. */
        public final float[][] corners = new float[8][3];
        /** Per-face source UV, indexed by {@code SkinModel.Face.ordinal()} order below. */
        public final FaceUv[] faceUv = new FaceUv[6];
        /** Cumulative bone rotation (own first, then each ancestor), degrees, for shading. */
        public final List<float[]> rotations = new ArrayList<>();

        Box(String bone) {
            this.bone = bone;
        }
    }

    /** One bone's transform: the pivot its rotation turns about and the rotation in degrees. */
    private static final class Transform {
        final float[] pivot;
        final float[] rotation;

        Transform(float[] pivot, float[] rotation) {
            this.pivot = pivot == null ? new float[]{0f, 0f, 0f} : pivot;
            this.rotation = rotation == null ? new float[]{0f, 0f, 0f} : rotation;
        }
    }

    /** A face's source rectangle in atlas pixels, with mirror flags from a negative UV size. */
    public static final class FaceUv {
        public final int u;
        public final int v;
        public final int w;
        public final int h;
        public final boolean flipH;
        public final boolean flipV;

        FaceUv(int u, int v, int w, int h, boolean flipH, boolean flipV) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.flipH = flipH;
            this.flipV = flipV;
        }
    }

    /** Resolves the first model in the file. */
    public static List<Box> build(BedrockGeometry geometry) {
        return build(geometry, 0);
    }

    /** Resolves one model in the file to a flat list of renderable cubes. */
    public static List<Box> build(BedrockGeometry geometry, int modelIndex) {
        List<Box> result = new ArrayList<>();
        if (geometry == null || modelIndex < 0 || modelIndex >= geometry.models.size()) return result;
        BedrockGeometry.GeometryModel model = geometry.models.get(modelIndex);

        Map<String, BedrockGeometry.Bone> byName = new HashMap<>();
        for (BedrockGeometry.Bone bone : model.bones) {
            if (bone.name != null) byName.put(bone.name, bone);
        }

        for (BedrockGeometry.Bone bone : model.bones) {
            List<Transform> chain = transformChain(bone, byName);
            for (BedrockGeometry.Cube cube : bone.cubes) {
                Box box = resolveCube(bone, cube, chain);
                if (box != null) result.add(box);
            }
        }
        return result;
    }

    /**
     * The bone's own transform followed by each ancestor's, innermost first. The corner transform
     * and the shading normal both walk this list, so the light and the geometry cannot disagree.
     */
    private static List<Transform> transformChain(BedrockGeometry.Bone bone,
                                                  Map<String, BedrockGeometry.Bone> byName) {
        List<Transform> chain = new ArrayList<>();
        BedrockGeometry.Bone current = bone;
        int guard = 0;
        while (current != null && guard++ < 64) {
            chain.add(new Transform(current.pivot, current.rotation));
            current = current.parent == null ? null : byName.get(current.parent);
        }
        return chain;
    }

    private static Box resolveCube(BedrockGeometry.Bone bone, BedrockGeometry.Cube cube,
                                   List<Transform> chain) {
        if (cube.origin == null || cube.size == null) return null;
        float inflate = cube.inflate;
        float x0 = cube.origin[0] - inflate;
        float y0 = cube.origin[1] - inflate;
        float z0 = cube.origin[2] - inflate;
        float x1 = cube.origin[0] + cube.size[0] + inflate;
        float y1 = cube.origin[1] + cube.size[1] + inflate;
        float z1 = cube.origin[2] + cube.size[2] + inflate;

        Box box = new Box(bone.name);
        for (Transform transform : chain) {
            box.rotations.add(transform.rotation);
        }

        // Cube-local rotation (rare) is folded into the corner transform about the cube pivot.
        float[] localRot = cube.rotation == null ? new float[]{0f, 0f, 0f} : cube.rotation;
        float[] localPivot = cube.pivot == null ? new float[]{0f, 0f, 0f} : cube.pivot;

        for (int ix = 0; ix <= 1; ix++) {
            for (int iy = 0; iy <= 1; iy++) {
                for (int iz = 0; iz <= 1; iz++) {
                    float[] p = {ix == 0 ? x0 : x1, iy == 0 ? y0 : y1, iz == 0 ? z0 : z1};
                    p = rotateAbout(p, localPivot, localRot);
                    p = applyChain(p, chain);
                    int index = ix | (iy << 1) | (iz << 2);
                    box.corners[index] = p;
                }
            }
        }

        for (int face = 0; face < 6; face++) {
            box.faceUv[face] = resolveFaceUv(cube, face);
        }
        return box;
    }

    /**
     * Applies each bone in the chain's rotation about that bone's pivot, innermost first, so a leg
     * follows the body that carries it.
     */
    private static float[] applyChain(float[] point, List<Transform> chain) {
        float[] p = point;
        for (Transform transform : chain) {
            p = rotateAbout(p, transform.pivot, transform.rotation);
        }
        return p;
    }

    /**
     * Resolves a face's source rectangle, preferring the per-face map and falling back to the
     * Bedrock box-UV unwrap.
     */
    private static FaceUv resolveFaceUv(BedrockGeometry.Cube cube, int face) {
        BedrockGeometry.UvRect rect = perFaceRect(cube.faces, face);
        if (rect != null) {
            // A negative uv_size means the rectangle is stated from its far corner; the bounding
            // box is the same either way, but the sign mirrors the sample. Take the min corner and
            // record the mirror so the renderer can flip instead of reading it backwards.
            float x0 = Math.min(rect.u, rect.u + rect.w);
            float y0 = Math.min(rect.v, rect.v + rect.h);
            return new FaceUv(floor(x0), floor(y0),
                    Math.round(Math.abs(rect.w)), Math.round(Math.abs(rect.h)),
                    rect.w < 0f, rect.h < 0f);
        }
        return boxUv(cube, face);
    }

    private static BedrockGeometry.UvRect perFaceRect(BedrockGeometry.UvPerFace faces, int face) {
        // Order matches SkinModel.Face: TOP, BOTTOM, LEFT, RIGHT, FRONT, BACK.
        switch (face) {
            case 0: return faces.up;
            case 1: return faces.down;
            case 2: return faces.west;
            case 3: return faces.east;
            case 4: return faces.south;
            case 5: return faces.north;
            default: return null;
        }
    }

    /**
     * The Bedrock box-UV unwrap. North = -z, south = +z, east = +x, west = -x, up = +y, down = -y;
     * the atlas is {@code 2*(sx+sz)} wide and {@code sz+sy} tall.
     */
    private static FaceUv boxUv(BedrockGeometry.Cube cube, int face) {
        if (cube.uv == null) {
            return new FaceUv(0, 0, 1, 1, false, false);
        }
        float u = cube.uv[0];
        float v = cube.uv[1];
        float sx = cube.size[0];
        float sy = cube.size[1];
        float sz = cube.size[2];
        switch (face) {
            case 0: // TOP / up
                return rect(u + sz, v, sx, sz);
            case 1: // BOTTOM / down
                return rect(u + sz + sx, v, sx, sz);
            case 2: // LEFT / west
                return rect(u, v + sz, sz, sy);
            case 3: // RIGHT / east
                return rect(u + sz + sx, v + sz, sz, sy);
            case 4: // FRONT / south
                return rect(u + sz + sx + sz, v + sz, sx, sy);
            case 5: // BACK / north
                return rect(u + sz, v + sz, sx, sy);
            default:
                return new FaceUv(0, 0, 1, 1, false, false);
        }
    }

    private static FaceUv rect(float u, float v, float w, float h) {
        return new FaceUv(Math.round(u), Math.round(v), Math.round(w), Math.round(h), false, false);
    }

    /** The four corner indices (into a {@link Box#corners}) for a face, in grid order. */
    public static int[] faceCornerIndices(int face) {
        int[] bits = FACE_CORNER_BITS[face];
        int[] indices = new int[4];
        for (int i = 0; i < 4; i++) {
            indices[i] = bits[i * 3] | (bits[i * 3 + 1] << 1) | (bits[i * 3 + 2] << 2);
        }
        return indices;
    }

    /** Rotates a normal by every rotation in a chain, in order, for per-face shading. */
    public static float[] rotateNormal(float[] normal, List<float[]> chain) {
        float[] n = {normal[0], normal[1], normal[2]};
        for (float[] r : chain) {
            n = rotateVector(n, r);
        }
        return n;
    }

    private static float[] rotateAbout(float[] point, float[] pivot, float[] rotation) {
        float[] pv = pivot == null ? new float[]{0f, 0f, 0f} : pivot;
        float[] local = {point[0] - pv[0], point[1] - pv[1], point[2] - pv[2]};
        float[] rotated = rotateVector(local, rotation);
        return new float[]{rotated[0] + pv[0], rotated[1] + pv[1], rotated[2] + pv[2]};
    }

    private static float[] rotateVector(float[] v, float[] rotation) {
        float rx = component(rotation, 0);
        float ry = component(rotation, 1);
        float rz = component(rotation, 2);
        if (rx == 0f && ry == 0f && rz == 0f) {
            return new float[]{v[0], v[1], v[2]};
        }
        double ax = Math.toRadians(rx);
        double ay = Math.toRadians(ry);
        double az = Math.toRadians(rz);
        // Z, then Y, then X — the order Blockbench applies a bone's rotation.
        double x1 = v[0] * Math.cos(az) - v[1] * Math.sin(az);
        double y1 = v[0] * Math.sin(az) + v[1] * Math.cos(az);
        double z1 = v[2];
        double x2 = x1 * Math.cos(ay) + z1 * Math.sin(ay);
        double z2 = -x1 * Math.sin(ay) + z1 * Math.cos(ay);
        double y2 = y1;
        double y3 = y2 * Math.cos(ax) - z2 * Math.sin(ax);
        double z3 = y2 * Math.sin(ax) + z2 * Math.cos(ax);
        return new float[]{(float) x2, (float) y3, (float) z3};
    }

    private static float component(float[] array, int index) {
        return array != null && index < array.length ? array[index] : 0f;
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }
}
