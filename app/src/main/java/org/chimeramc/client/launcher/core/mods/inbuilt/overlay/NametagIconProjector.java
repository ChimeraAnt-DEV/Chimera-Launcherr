package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure world-to-screen placement for the in-world Voice nametag icons.
 *
 * <p>Each tag is a player in the same world whose name label sits above their head; this projects
 * the point just above that label so the icon can be drawn beside it. It shares
 * {@link HitboxProjector.Camera} with the Hitboxes module on purpose: the camera convention is the
 * one thing a native feed has to agree on, and having two copies of it in the module set is how
 * one of them silently drifts.
 *
 * <p>No Android or game types -- just floats -- so the placement, the behind-camera cull and the
 * left/right offset are unit-testable without a device.
 */
public final class NametagIconProjector {

    /** Icon edge length as a fraction of the projected label height. */
    public static final float ICON_SCALE = 0.55f;

    /** Gap between the label and the icon, as a fraction of the projected label height. */
    public static final float ICON_GAP = 0.18f;

    /** Below this projected label height the icon would be illegible, so it is dropped. */
    public static final float MIN_LABEL_HEIGHT_PX = 10f;

    /** One in-world player the icon may be drawn for. */
    public static final class Tag {
        public final String peerId;
        public final String name;
        public final float x, y, z;
        /** World height of the name label above the player's feet, in blocks. */
        public final float labelHeight;
        /** The channel the player is on, so the "same channel" rule can be applied here. */
        public final String channel;

        public Tag(String peerId, String name, float x, float y, float z,
                   float labelHeight, String channel) {
            this.peerId = peerId;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.labelHeight = labelHeight;
            this.channel = channel;
        }
    }

    /** A placed icon: where to draw it and how big. */
    public static final class Placed {
        public final String peerId;
        public final float centerX, centerY, size;
        public final float distance;

        Placed(String peerId, float centerX, float centerY, float size, float distance) {
            this.peerId = peerId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.size = size;
            this.distance = distance;
        }
    }

    private NametagIconProjector() {
    }

    /**
     * Projects the icons for the tags that are on a channel the listener can hear.
     *
     * <p>Far to near, so a nearer icon is drawn over a farther one at the same screen position.
     * A tag behind the camera, or one whose label projects too small to read, is skipped.
     */
    public static List<Placed> project(HitboxProjector.Camera camera, List<Tag> tags,
                                       String listenerChannel) {
        List<Placed> placed = new ArrayList<>();
        if (camera == null || tags == null) return placed;

        float[] forward = camera.forward();
        float[] right = normalize(cross(forward, new float[]{0f, 1f, 0f}));
        if (right == null) {
            right = normalize(cross(forward, new float[]{1f, 0f, 0f}));
        }
        float[] up = normalize(cross(right, forward));
        if (up == null) return placed;

        float focal = (camera.screenHeight / 2f)
                / (float) Math.tan(Math.toRadians(camera.fovDeg) / 2f);

        for (Tag tag : tags) {
            if (tag == null || tag.peerId == null) continue;
            if (!org.chimeramc.client.core.voice.VoiceChannel.canHear(
                    listenerChannel, tag.channel)) {
                continue;
            }
            // Label anchor: above the feet by the label height, plus half a block for the label's
            // own baseline, which is where a nametag's text actually sits.
            float anchorY = tag.y + tag.labelHeight + 0.5f;
            float dx = tag.x - camera.x;
            float dy = anchorY - camera.y;
            float dz = tag.z - camera.z;
            float depth = forward[0] * dx + forward[1] * dy + forward[2] * dz;
            if (depth <= HitboxProjector.NEAR_PLANE) continue;

            float camX = right[0] * dx + right[1] * dy + right[2] * dz;
            float camY = up[0] * dx + up[1] * dy + up[2] * dz;
            float screenX = camera.screenWidth / 2f + (camX / depth) * focal;
            float screenY = camera.screenHeight / 2f - (camY / depth) * focal;

            // A one-block-tall label at this depth, in pixels; drives the icon size, so a distant
            // player's icon shrinks with their name rather than staying a fixed blob.
            float labelHeightPx = focal / depth;
            if (labelHeightPx < MIN_LABEL_HEIGHT_PX) continue;

            float size = Math.max(8f, labelHeightPx * ICON_SCALE);
            // Sit the icon to the right of the label centre, clear of the text.
            float centerX = screenX + size * (0.5f + ICON_GAP);
            float centerY = screenY - size * 0.5f;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            placed.add(new Placed(tag.peerId, centerX, centerY, size, distance));
        }

        placed.sort((a, b) -> Float.compare(b.distance, a.distance));
        return placed;
    }

    /** Tags with no provider installed, as an empty list. */
    public static List<Tag> emptyTags() {
        return Collections.emptyList();
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
}
