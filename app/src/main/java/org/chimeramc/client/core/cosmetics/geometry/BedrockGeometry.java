package org.chimeramc.client.core.cosmetics.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed Blockbench {@code .geo.json} model: the plain data shape of Bedrock's geometry format.
 *
 * <p>This is the {@code [Raw .geo.json Asset] -> [BedrockGeometryParser]} stage of the preview
 * pipeline. It is a thin POJO — no Android, no rendering — so the parse result is inspectable in a
 * JVM unit test. The real work (rotation, UV orientation, model-space placement) lives in
 * {@link PreviewMeshModel}; this type only mirrors the file's structure so a malformed field is a
 * {@code null} here rather than a crash in the renderer.
 *
 * <p>Units are Bedrock model pixels, which are the same unit as {@code SkinModel}'s model pixels
 * (the vanilla head is {@code [-4,24,-4]..[4,32,4]} in both), so a parsed bone/cube can be drawn in
 * the preview's player space without a conversion factor.
 */
public final class BedrockGeometry {

    /** The model's format version, e.g. {@code "1.12.0"} or {@code "1.8.0"}. */
    public String formatVersion;

    /** The models in the file. A file normally holds exactly one. */
    public final List<GeometryModel> models = new ArrayList<>();

    /** One geometry: a description plus a bone hierarchy. */
    public static final class GeometryModel {
        public Description description = new Description();
        public final List<Bone> bones = new ArrayList<>();
    }

    /** The geometry header: identifier and the atlas the UVs are written against. */
    public static final class Description {
        /** e.g. {@code "geometry.glowberry.wing"}. */
        public String identifier;
        public int textureWidth;
        public int textureHeight;
    }

    /**
     * A bone: a named joint with an optional parent, a rotation pivot and its cubes.
     *
     * <p>{@code parent} is what makes an authored rig articulate — a hat bone parented to the head,
     * a pet's legs parented to the body. The parser keeps the hierarchy; {@link PreviewMeshModel}
     * resolves each bone's world transform by walking it.
     */
    public static final class Bone {
        public String name;
        public String parent;
        /** {@code [x, y, z]} origin for this bone's rotation. */
        public float[] pivot;
        /** {@code [pitch, yaw, roll]} in degrees, applied about {@link #pivot}. */
        public float[] rotation;
        public final List<Cube> cubes = new ArrayList<>();
    }

    /** A cube: an axis-aligned box in the bone's local space, with per-face UVs. */
    public static final class Cube {
        /** {@code [x, y, z]} of the cube's minimum corner. */
        public float[] origin;
        /** {@code [width, height, depth]}. */
        public float[] size;
        /** Optional local cube rotation, {@code [pitch, yaw, roll]} degrees about {@link #pivot}. */
        public float[] rotation;
        /** Optional pivot for {@link #rotation}. */
        public float[] pivot;
        /** Per-face UV when the cube carries an explicit map; otherwise {@link #uv} is used. */
        public final UvPerFace faces = new UvPerFace();
        /** Box-UV origin {@code [u, v]} for the automatic 3-face + mirrored layout. */
        public float[] uv;
        /** Box-UV size {@code [w, h]} for the automatic layout. */
        public float[] uvSize;
        /** Optional inflate (grows the cube on every axis), common on rounded parts. */
        public float inflate;
    }

    /** Per-face UV map, as Blockbench writes it when a cube is not box-UV mapped. */
    public static final class UvPerFace {
        public UvRect north;
        public UvRect south;
        public UvRect east;
        public UvRect west;
        public UvRect up;
        public UvRect down;
    }

    /**
     * A UV rectangle: an atlas offset and a size that may be negative, which mirrors the sample.
     *
     * <p>The sign is load-bearing. Blockbench emits a negative {@code uv_size} component whenever a
     * face is mirrored (the whole of the west/east/north/south set on a symmetric cube), and the
     * renderer must flip the sample to match or the texture reads backwards on those faces.
     */
    public static final class UvRect {
        public float u;
        public float v;
        public float w;
        public float h;

        public UvRect() {
        }

        public UvRect(float u, float v, float w, float h) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
        }
    }
}
