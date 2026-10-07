package org.chimeramc.client.core.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Renames an authored accessory model's root bone to {@code acc} so the head-tilt animation drives
 * it.
 *
 * <p>The pack's {@code animation.chimera_hat_tilt} turns the {@code acc} bone by the player's
 * {@code query.target_x_rotation}/{@code query.target_y_rotation} — the same queries the vanilla
 * head bone uses — which is what makes a hat follow the head. An authored export names its root
 * bone whatever the author typed ({@code hat}, {@code halo}, {@code cat}), so without this the
 * model would render but stay bolt-upright while the player looks around. The root is renamed and
 * its pivot is kept; every child bone keeps its parent chain, so a multi-bone model (cat
 * ears/tail) still articulates.
 *
 * <p>Pure {@code JsonObject} work, so the rename is unit-testable with no device.
 */
public final class AccBoneRetarget {

    /** The bone the head-tilt animation drives. */
    public static final String ACC = "acc";

    private AccBoneRetarget() {
    }

    /**
     * Renames the first geometry's root bone to {@code acc}, or the whole model unchanged when it
     * is not a parseable geometry or already has an {@code acc} bone.
     */
    public static String apply(String modelJson) {
        if (modelJson == null || modelJson.isEmpty()) return modelJson;
        JsonObject model;
        try {
            model = JsonParser.parseString(modelJson).getAsJsonObject();
        } catch (RuntimeException e) {
            return modelJson;
        }
        JsonArray geos = model.getAsJsonArray("minecraft:geometry");
        if (geos == null || geos.size() == 0) return modelJson;
        JsonArray bones = geos.get(0).getAsJsonObject().getAsJsonArray("bones");
        if (bones == null || bones.size() == 0) return modelJson;

        // The head-attach bone is the one the head-look rotation should turn. For a plain hat that
        // is the single root bone (a hat is one bone at the head top). For a multi-bone model the
        // root can be at the feet — the cat's root is at (0,0,0) with its ears and tail beneath it —
        // so rotating the root would swing the whole animal about the ground. The attach point is
        // therefore the bone that has children and sits highest, which picks the hat root for a
        // hat and the `ears` bone (at the neck) for the cat, leaving the tail's own chain alone.
        int attachIndex = -1;
        double bestY = -Double.MAX_VALUE;
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            boolean hasChildren = false;
            for (int j = 0; j < bones.size(); j++) {
                if (j == i) continue;
                JsonElement parent = bones.get(j).getAsJsonObject().get("parent");
                if (parent != null && !parent.isJsonNull()
                        && nameOf(bone).equals(parent.getAsString())) {
                    hasChildren = true;
                    break;
                }
            }
            if (!hasChildren) continue;
            double y = pivotY(bone);
            if (y > bestY) {
                bestY = y;
                attachIndex = i;
            }
        }
        // A single-bone hat has no parent bone: its one bone is the attach point. Fall back to the
        // topmost root (a bone with no parent), then to the first bone.
        if (attachIndex < 0) {
            for (int i = 0; i < bones.size(); i++) {
                JsonElement parent = bones.get(i).getAsJsonObject().get("parent");
                if (parent == null || parent.isJsonNull()) {
                    attachIndex = i;
                    break;
                }
            }
        }
        if (attachIndex < 0) attachIndex = 0;

        String oldName = nameOf(bones.get(attachIndex).getAsJsonObject());
        // Already named acc: nothing to do.
        if (ACC.equals(oldName)) return modelJson;

        JsonObject attach = bones.get(attachIndex).getAsJsonObject();
        attach.addProperty("name", ACC);

        // Every child that named the old bone as its parent must be repointed, or it would hang
        // from a bone that no longer exists and the child chain (a cat's ears and tail) would
        // detach from the model.
        if (oldName != null && !oldName.isEmpty()) {
            for (int i = 0; i < bones.size(); i++) {
                if (i == attachIndex) continue;
                JsonObject bone = bones.get(i).getAsJsonObject();
                JsonElement parent = bone.get("parent");
                if (parent != null && !parent.isJsonNull()
                        && oldName.equals(parent.getAsString())) {
                    bone.addProperty("parent", ACC);
                }
            }
        }
        return model.toString();
    }

    /** A bone's pivot Y, or 0 when it has none. */
    private static double pivotY(JsonObject bone) {
        JsonElement pivot = bone.get("pivot");
        if (pivot == null || !pivot.isJsonArray()) return 0.0;
        JsonArray arr = pivot.getAsJsonArray();
        return arr.size() >= 2 ? arr.get(1).getAsDouble() : 0.0;
    }

    private static String nameOf(JsonObject bone) {
        JsonElement name = bone.get("name");
        return name == null || name.isJsonNull() ? "" : name.getAsString();
    }
}
