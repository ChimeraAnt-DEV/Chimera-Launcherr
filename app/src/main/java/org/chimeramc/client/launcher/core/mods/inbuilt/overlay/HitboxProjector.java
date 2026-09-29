package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure world-to-screen projection for the Hitboxes module.
 *
 * <p>Takes a camera and a set of world-space bounding boxes and returns the screen rectangles
 * to draw, plus the two combat guides: the band a critical hit lands on and the inner box that
 * is the best target for chaining combos. Everything here is arithmetic on floats — no Android
 * or game types — so the projection and the guide placement are unit-testable on their own.
 *
 * <p>Honest scope: the boxes come from whatever feed is installed (see
 * {@link HitboxMod.EntitySource}). Nothing in this class invents a reading; with no feed there
 * is no scene and nothing is drawn.
 */
public final class HitboxProjector {

    /** Entity categories the module draws differently. */
    public enum Kind {
        PLAYER,
        MOB,
        DROPPED_ITEM,
        /** A thrown item in flight: an ender pearl, a wind charge, a snowball, an egg. */
        THROWN_ITEM,
        /** A projectile fired by a weapon: an arrow, a fireball, a trident. */
        PROJECTILE
    }

    /** Anything closer than this is not projected; a box behind the camera has no screen rect. */
    public static final float NEAR_PLANE = 0.05f;

    /** Fraction of a player's box height where the critical-hit band sits (the head). */
    public static final float CRIT_BAND = 0.12f;

    /** Top of the combo target box, as a fraction of box height. */
    public static final float COMBO_TOP = 0.16f;

    /** Bottom of the combo target box, as a fraction of box height. */
    public static final float COMBO_BOTTOM = 0.55f;

    /** Combo box width, as a fraction of the entity's box width. */
    public static final float COMBO_WIDTH = 0.5f;

    /** One axis-aligned world box. {@code y} is the bottom face, matching a standing entity. */
    public static final class Entity {
        public final Kind kind;
        public final float x, y, z;
        public final float width, height, depth;

        public Entity(Kind kind, float x, float y, float z, float width, float height, float depth) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = Math.max(0.01f, width);
            this.height = Math.max(0.01f, height);
            this.depth = Math.max(0.01f, depth);
        }

        public static Entity player(float x, float y, float z) {
            return new Entity(Kind.PLAYER, x, y, z, 0.6f, 1.8f, 0.6f);
        }

        public static Entity mob(float x, float y, float z, float width, float height) {
            return new Entity(Kind.MOB, x, y, z, width, height, width);
        }

        public static Entity droppedItem(float x, float y, float z) {
            return new Entity(Kind.DROPPED_ITEM, x, y, z, 0.25f, 0.25f, 0.25f);
        }

        public static Entity projectile(float x, float y, float z) {
            return new Entity(Kind.PROJECTILE, x, y, z, 0.5f, 0.5f, 0.5f);
        }

        /**
         * A thrown item: an ender pearl, a wind charge, a snowball, an egg.
         *
         * <p>Kept separate from {@link #projectile} because these are thrown by hand and share a
         * distinct, slightly smaller hitbox, and a player trying to hit one mid-air benefits from
         * seeing that they are the same class of target.
         */
        public static Entity thrownItem(float x, float y, float z) {
            return new Entity(Kind.THROWN_ITEM, x, y, z, 0.25f, 0.25f, 0.25f);
        }

        public float minX() { return x - width / 2f; }
        public float maxX() { return x + width / 2f; }
        public float minY() { return y; }
        public float maxY() { return y + height; }
        public float minZ() { return z - depth / 2f; }
        public float maxZ() { return z + depth / 2f; }
    }

    /**
     * Camera and viewport.
     *
     * <p>Yaw is degrees clockwise from +Z when viewed from above; pitch is degrees upward.
     * These conventions are stated here because a native feed has to agree with them, and a
     * mismatch shows up only as boxes that are consistently offset.
     */
    public static final class Camera {
        public final float x, y, z;
        public final float yawDeg, pitchDeg;
        public final float fovDeg;
        public final int screenWidth, screenHeight;

        public Camera(float x, float y, float z, float yawDeg, float pitchDeg,
                      float fovDeg, int screenWidth, int screenHeight) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.fovDeg = Math.max(10f, Math.min(170f, fovDeg));
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        /**
         * Unit forward vector implied by {@link #yawDeg} and {@link #pitchDeg}.
         *
         * <p>Yaw increases clockwise seen from above (the Minecraft convention), so forward's X
         * component is negated relative to the naive spherical form. Getting this sign wrong
         * mirrors every projected box horizontally, which a feed supplying real yaw would hit.
         */
        public float[] forward() {
            return forwardFrom(yawDeg, pitchDeg);
        }
    }

    /** A screen-space rectangle, in pixels. */
    public static final class Rect {
        public final float left, top, right, bottom;

        public Rect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    /** One entity's draw instruction. */
    public static final class Projected {
        public final Kind kind;
        public final Rect box;
        public final boolean aimedAt;
        /** Horizontal line across the box where a critical hit lands; null for non-players. */
        public final Float critY;
        /** Best combo target inside the box; null for non-players. */
        public final Rect comboBox;
        public final float distance;

        Projected(Kind kind, Rect box, boolean aimedAt, Float critY, Rect comboBox, float distance) {
            this.kind = kind;
            this.box = box;
            this.aimedAt = aimedAt;
            this.critY = critY;
            this.comboBox = comboBox;
            this.distance = distance;
        }
    }

    /** Everything the overlay needs for one frame. */
    public static final class Frame {
        /** The aim line: screen anchor to the crosshair. Never null. */
        public final Rect lookLine;
        public final List<Projected> entities;

        Frame(Rect lookLine, List<Projected> entities) {
            this.lookLine = lookLine;
            this.entities = Collections.unmodifiableList(entities);
        }
    }

    /** A complete scene: camera plus the entities to project. */
    public static final class Scene {
        public final Camera camera;
        public final List<Entity> entities;

        public Scene(Camera camera, List<Entity> entities) {
            this.camera = camera;
            this.entities = entities != null ? entities : Collections.emptyList();
        }
    }

    /**
     * The camera's orthonormal basis and focal length, computed once per frame.
     *
     * <p>Exposed so the entity projection and the aim-line projection share one basis. Computing
     * it twice would let a heading change between the two, and the aim line would disagree with
     * the box it points at, which is exactly the mismatch the module exists to avoid.
     */
    public static final class Basis {
        public final Camera camera;
        public final float[] forward;
        public final float[] right;
        public final float[] up;
        public final float focal;

        Basis(Camera camera, float[] forward, float[] right, float[] up, float focal) {
            this.camera = camera;
            this.forward = forward;
            this.right = right;
            this.up = up;
            this.focal = focal;
        }
    }

    private HitboxProjector() {}

    /**
     * Projects a scene.
     *
     * <p>The look line runs from a screen anchor below centre to the crosshair. It is drawn that
     * way rather than as a world ray because the camera's own forward ray projects to a single
     * point — the centre — so a world ray would have nothing to draw. The line is a fixed
     * on-screen representation of the aim direction, and an entity turns blue when the crosshair
     * actually points at it.
     */
    public static Frame project(Scene scene) {
        if (scene == null || scene.camera == null) {
            return new Frame(null, new ArrayList<>());
        }
        Camera camera = scene.camera;
        Basis basis = basis(camera);
        float[] forward = basis.forward;
        float[] right = basis.right;
        float[] up = basis.up;
        float focal = basis.focal;

        List<Projected> projected = new ArrayList<>();
        for (Entity entity : scene.entities) {
            Projected result = projectEntity(entity, camera, forward, right, up, focal);
            if (result != null) projected.add(result);
        }
        // Far to near, so a nearer box paints over one behind it.
        projected.sort((a, b) -> Float.compare(b.distance, a.distance));

        float cx = camera.screenWidth / 2f;
        float cy = camera.screenHeight / 2f;
        Rect lookLine = new Rect(cx, cy, cx, cy + camera.screenHeight * 0.18f);
        return new Frame(lookLine, projected);
    }

    /**
     * The camera's forward/right/up basis and focal length.
     *
     * <p>Degenerate when looking straight up or down (the world up vector is parallel to forward),
     * so a perpendicular is picked from a different axis rather than returning a null basis that
     * would blank the whole frame.
     */
    /**
     * Unit forward vector for a yaw/pitch pair, in degrees.
     *
     * <p>The one place the yaw convention lives. Yaw increases clockwise seen from above (the
     * Minecraft convention), so forward's X component is negated relative to the naive spherical
     * form; getting this sign wrong mirrors every projected box and every peer look line
     * horizontally. {@link Camera#forward()} and the peer look ray both call this so a box and the
     * line drawn beside it can never disagree about which way "forward" is.
     */
    public static float[] forwardFrom(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitch);
        return new float[]{
                (float) (-Math.sin(yaw) * cosPitch),
                (float) Math.sin(pitch),
                (float) (Math.cos(yaw) * cosPitch)
        };
    }

    /**
     * Projects one world point to screen space, or null when it is behind the near plane.
     *
     * <p>Null rather than a clamped value on purpose: a point behind the camera has no screen
     * position, and drawing a line to an invented one would put a peer's look direction across the
     * wrong half of the screen.
     */
    public static float[] projectPoint(float worldX, float worldY, float worldZ, Basis basis) {
        if (basis == null) return null;
        float dx = worldX - basis.camera.x;
        float dy = worldY - basis.camera.y;
        float dz = worldZ - basis.camera.z;
        float depth = dot(dx, dy, dz, basis.forward);
        if (depth <= NEAR_PLANE) return null;
        float camX = dot(dx, dy, dz, basis.right);
        float camY = dot(dx, dy, dz, basis.up);
        float sx = basis.camera.screenWidth / 2f + (camX / depth) * basis.focal;
        float sy = basis.camera.screenHeight / 2f - (camY / depth) * basis.focal;
        return new float[]{sx, sy};
    }

    /** Eye height above an entity's feet, in blocks; the anchor for a look ray. */
    public static final float EYE_HEIGHT = 1.62f;

    /**
     * The screen-space look ray for a peer: from its eye out along its own heading.
     *
     * <p>Returns null when the peer advertised no rotation (both angles 0 is the wire's "unknown"
     * sentinel) or when either end is behind the near plane. A missing line is the honest outcome -
     * a line at a guessed heading would be worse than none, because the player would trust it.
     *
     * @param yawDeg   the peer's yaw, as advertised
     * @param pitchDeg the peer's pitch, as advertised (positive looking down, Minecraft's sign)
     */
    public static Rect peerLookRay(float x, float y, float z, float yawDeg, float pitchDeg,
                                   float lengthBlocks, Basis basis) {
        if (basis == null) return null;
        if (yawDeg == 0f && pitchDeg == 0f) return null;
        float[] forward = forwardFrom(yawDeg, -pitchDeg);
        float eyeY = y + EYE_HEIGHT;
        float[] head = projectPoint(x, eyeY, z, basis);
        if (head == null) return null;
        float[] tip = projectPoint(x + forward[0] * lengthBlocks,
                eyeY + forward[1] * lengthBlocks,
                z + forward[2] * lengthBlocks, basis);
        if (tip == null) return null;
        return new Rect(head[0], head[1], tip[0], tip[1]);
    }

    public static Basis basis(Camera camera) {
        if (camera == null) return null;
        float[] forward = camera.forward();
        float[] right = normalize(cross(forward, new float[]{0f, 1f, 0f}));
        if (right == null) {
            right = normalize(cross(forward, new float[]{1f, 0f, 0f}));
        }
        float[] up = normalize(cross(right, forward));
        float focal = (camera.screenHeight / 2f)
                / (float) Math.tan(Math.toRadians(camera.fovDeg) / 2f);
        return new Basis(camera, forward, right, up, focal);
    }

    /**
     * Projects a single world-space box directly, for callers that do not build a whole scene
     * (the network-feed hitbox overlay, which has one peer's box to draw at a time).
     */
    public static Projected projectEntity(Entity entity, Basis basis) {
        if (entity == null || basis == null) return null;
        return projectEntity(entity, basis.camera, basis.forward, basis.right, basis.up, basis.focal);
    }

    private static Projected projectEntity(Entity entity, Camera camera, float[] forward,
                                           float[] right, float[] up, float focal) {
        float[] xs = {entity.minX(), entity.maxX()};
        float[] ys = {entity.minY(), entity.maxY()};
        float[] zs = {entity.minZ(), entity.maxZ()};

        float minScreenX = Float.MAX_VALUE, minScreenY = Float.MAX_VALUE;
        float maxScreenX = -Float.MAX_VALUE, maxScreenY = -Float.MAX_VALUE;
        float nearestDepth = Float.MAX_VALUE;
        boolean any = false;

        for (float wx : xs) {
            for (float wy : ys) {
                for (float wz : zs) {
                    float dx = wx - camera.x;
                    float dy = wy - camera.y;
                    float dz = wz - camera.z;
                    float depth = dot(dx, dy, dz, forward);
                    if (depth <= NEAR_PLANE) continue;
                    float camX = dot(dx, dy, dz, right);
                    float camY = dot(dx, dy, dz, up);
                    float sx = camera.screenWidth / 2f + (camX / depth) * focal;
                    float sy = camera.screenHeight / 2f - (camY / depth) * focal;
                    minScreenX = Math.min(minScreenX, sx);
                    maxScreenX = Math.max(maxScreenX, sx);
                    minScreenY = Math.min(minScreenY, sy);
                    maxScreenY = Math.max(maxScreenY, sy);
                    nearestDepth = Math.min(nearestDepth, depth);
                    any = true;
                }
            }
        }
        if (!any) return null;

        Rect box = new Rect(minScreenX, minScreenY, maxScreenX, maxScreenY);
        boolean aimedAt = crosshairHits(entity, camera, forward);
        float distance = (float) Math.sqrt(
                sq(entity.x - camera.x) + sq(entity.y + entity.height / 2f - camera.y) + sq(entity.z - camera.z));

        Float critY = null;
        Rect combo = null;
        if (entity.kind == Kind.PLAYER) {
            critY = box.top + box.height() * CRIT_BAND;
            float comboWidth = box.width() * COMBO_WIDTH;
            float comboCentre = (box.left + box.right) / 2f;
            combo = new Rect(
                    comboCentre - comboWidth / 2f,
                    box.top + box.height() * COMBO_TOP,
                    comboCentre + comboWidth / 2f,
                    box.top + box.height() * COMBO_BOTTOM);
        }
        return new Projected(entity.kind, box, aimedAt, critY, combo, distance);
    }

    /**
     * Slab test: does the crosshair ray from the camera enter the entity's box?
     *
     * <p>This is computed rather than taken from the feed, so the blue highlight cannot disagree
     * with the geometry that is actually drawn.
     */
    public static boolean crosshairHits(Entity entity, Camera camera, float[] forward) {
        if (entity == null || camera == null || forward == null) return false;
        float tMin = 0f;
        float tMax = Float.MAX_VALUE;

        float[] origin = {camera.x, camera.y, camera.z};
        float[] mins = {entity.minX(), entity.minY(), entity.minZ()};
        float[] maxs = {entity.maxX(), entity.maxY(), entity.maxZ()};

        for (int axis = 0; axis < 3; axis++) {
            float direction = forward[axis];
            if (Math.abs(direction) < 1e-6f) {
                if (origin[axis] < mins[axis] || origin[axis] > maxs[axis]) return false;
                continue;
            }
            float t1 = (mins[axis] - origin[axis]) / direction;
            float t2 = (maxs[axis] - origin[axis]) / direction;
            if (t1 > t2) {
                float swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }
        return true;
    }

    private static float dot(float x, float y, float z, float[] v) {
        return x * v[0] + y * v[1] + z * v[2];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float[] normalize(float[] v) {
        if (v == null) return null;
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length < 1e-6f) return null;
        return new float[]{v[0] / length, v[1] / length, v[2] / length};
    }

    private static float sq(float value) {
        return value * value;
    }
}
