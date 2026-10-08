package org.chimeramc.client.core.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.Set;

/**
 * Renames an authored pet model's bones onto the animation vocabulary the pet controller drives.
 *
 * <p>A Blockbench export names its bones whatever the author typed — {@code HEAD}, {@code cuello1},
 * {@code patas}, {@code wingLeft1}. The pet animation controller plays rotations and positions on
 * a fixed vocabulary ({@code pet}, {@code head}, {@code tail}, {@code wing_l}, {@code wing_r},
 * {@code leg_a}..{@code leg_c}), so an authored model whose bones do not use those names renders
 * but never moves — the "model shows up but is stiff" case. This class is the bridge: it renames
 * the bones it can classify and re-parents everything else to the root, so the authored mesh
 * animates with the existing gait set (walk/run/crouch/fly/swim) with no per-model animation file.
 *
 * <p><b>Only the first match for a slot is renamed.</b> A model with several {@code leg} bones maps
 * them to {@code leg_a}, {@code leg_b}, {@code leg_c} in order; any beyond the third keep their own
 * name (parented to the root) so no cube is lost and no name collides.
 *
 * <p><b>Every cube survives.</b> Bones that are not renamed keep their name and are re-parented to
 * the root, so they still render and follow the body — only their own articulation is lost, which
 * is the right trade for a name the controller cannot drive.
 *
 * <p>Pure {@code JsonObject} work, so the mapping is unit-testable with no device.
 */
public final class PetBoneRetarget {

    /** The root bone every other bone hangs from. */
    public static final String ROOT = "pet";

    private static final String[] LEG_SLOTS = {"leg_a", "leg_b", "leg_c"};

    private PetBoneRetarget() {
    }

    /**
     * Retargets the first geometry in {@code modelJson} to the pet vocabulary.
     *
     * @return the retargeted JSON, or the input unchanged when it is not a parseable geometry
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
        JsonObject geo = geos.get(0).getAsJsonObject();
        JsonArray bones = geo.getAsJsonArray("bones");
        if (bones == null || bones.size() == 0) return modelJson;

        // The authored hierarchy is preserved. An earlier version re-parented every non-root bone
        // directly to the pet root, which is catastrophic for a real Blockbench rig: a dragon's tail
        // is a chain (body -> cola0 -> cola1 -> ...) and its legs are nested, so hanging each bone
        // off the root makes every pivot absolute to the root and the mesh collapses into a knot of
        // detached cubes — the "cursed" pet. Renaming is the whole job; the parent edges must not be
        // touched beyond following a renamed parent.
        String[] original = new String[bones.size()];
        String[] assigned = new String[bones.size()];
        Set<String> used = new HashSet<>();
        // Guarantee a root: an authored model with no bone named "pet" gets its first bone promoted
        // (and its children follow the rename), so the controller always has a root to drive.
        int rootIndex = -1;
        for (int i = 0; i < bones.size(); i++) {
            if (ROOT.equals(nameOf(bones.get(i).getAsJsonObject()))) {
                rootIndex = i;
                break;
            }
        }
        if (rootIndex < 0) rootIndex = 0;
        used.add(ROOT);
        assigned[rootIndex] = ROOT;
        original[rootIndex] = nameOf(bones.get(rootIndex).getAsJsonObject());

        int headSlot = 0, tailSlot = 0, wingSlot = 0, legSlot = 0;
        for (int i = 0; i < bones.size(); i++) {
            if (i == rootIndex) continue;
            String name = nameOf(bones.get(i).getAsJsonObject());
            original[i] = name;
            String slot = null;
            if (headSlot == 0 && isHead(name)) {
                slot = "head";
                headSlot = 1;
            } else if (tailSlot == 0 && isTail(name)) {
                slot = "tail";
                tailSlot = 1;
            } else if (wingSlot < 2 && isWing(name)) {
                boolean left = name.toLowerCase().contains("left")
                        || name.toLowerCase().endsWith("l");
                if (left && !used.contains("wing_l")) slot = "wing_l";
                else if (!left && !used.contains("wing_r")) slot = "wing_r";
                else slot = used.contains("wing_l") ? "wing_r" : "wing_l";
                wingSlot++;
            } else if (legSlot < LEG_SLOTS.length && isLeg(name)) {
                slot = LEG_SLOTS[legSlot++];
            }
            if (slot != null && !used.contains(slot)) {
                assigned[i] = slot;
            } else {
                assigned[i] = unique(name, used);
            }
            used.add(assigned[i]);
        }

        // Old name -> new name, first occurrence wins, so a child's parent reference follows.
        java.util.Map<String, String> rename = new java.util.HashMap<>();
        for (int i = 0; i < bones.size(); i++) {
            if (original[i] != null && !original[i].isEmpty() && !rename.containsKey(original[i])) {
                rename.put(original[i], assigned[i]);
            }
        }

        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            bone.addProperty("name", assigned[i]);
            if (bone.has("parent") && !bone.get("parent").isJsonNull()) {
                String parent = bone.get("parent").getAsString();
                String renamed = rename.get(parent);
                if (renamed != null) bone.addProperty("parent", renamed);
            }
        }
        return model.toString();
    }

    private static String nameOf(JsonObject bone) {
        JsonElement name = bone.get("name");
        return name == null || name.isJsonNull() ? "" : name.getAsString();
    }

    private static String unique(String name, Set<String> used) {
        String base = name == null || name.isEmpty() ? "bone" : name;
        if (!used.contains(base)) return base;
        int n = 2;
        while (used.contains(base + "_" + n)) n++;
        return base + "_" + n;
    }

    private static boolean isHead(String n) {
        String s = n.toLowerCase();
        return s.contains("head") || s.contains("neck") || s.contains("cuello") || s.contains("jaw")
                || s.contains("beak") || s.contains("mouth") || s.contains("snout")
                || s.contains("forehead") || s.contains("boca");
    }

    private static boolean isTail(String n) {
        String s = n.toLowerCase();
        return s.contains("tail") || s.contains("cola");
    }

    private static boolean isWing(String n) {
        String s = n.toLowerCase();
        return s.contains("wing") || s.contains("ala") || s.contains("feather");
    }

    private static boolean isLeg(String n) {
        String s = n.toLowerCase();
        return s.contains("leg") || s.contains("foot") || s.contains("paw") || s.contains("thigh")
                || s.contains("patas") || s.contains("pata") || s.contains("arm")
                || s.contains("hand") || s.contains("claw") || s.contains("dedos");
    }
}
