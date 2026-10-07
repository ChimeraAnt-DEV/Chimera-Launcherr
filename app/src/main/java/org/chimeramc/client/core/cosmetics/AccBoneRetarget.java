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

        int rootIndex = -1;
        String rootName = null;
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            String name = nameOf(bone);
            // The bone no other bone names as its parent is the root.
            boolean isParent = false;
            for (int j = 0; j < bones.size(); j++) {
                if (j == i) continue;
                JsonElement parent = bones.get(j).getAsJsonObject().get("parent");
                if (parent != null && !parent.isJsonNull() && name.equals(parent.getAsString())) {
                    isParent = true;
                    break;
                }
            }
            if (!isParent) {
                rootIndex = i;
                rootName = name;
                // Prefer an explicit root (no parent) over a childless leaf.
                JsonElement parent = bone.get("parent");
                if (parent == null || parent.isJsonNull()) break;
            }
        }
        if (rootIndex < 0) return modelJson;

        // Already named acc: nothing to do.
        if (ACC.equals(rootName)) return modelJson;

        JsonObject root = bones.get(rootIndex).getAsJsonObject();
        root.addProperty("name", ACC);
        root.remove("parent");
        return model.toString();
    }

    private static String nameOf(JsonObject bone) {
        JsonElement name = bone.get("name");
        return name == null || name.isJsonNull() ? "" : name.getAsString();
    }
}
