package org.chimeramc.client.core.cosmetics.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parses Bedrock {@code .geo.json} geometry into {@link BedrockGeometry}.
 *
 * <p>Bedrock's format is loose in ways a strict data binding would reject, and every one of those
 * loose spots appears in real Blockbench exports: {@code format_version} is a string in 1.12 files
 * but a number in 1.8 ones, a cube's {@code size} may be one number or three, {@code uv} is either
 * an origin+size pair (box UV) or a per-face object, and {@code inflate} is optional. A strict
 * binding would throw on a valid file, so this parser reads the tree by hand and tolerates each
 * variant — a missing optional field is left {@code null} and the renderer falls back.
 *
 * <p>It never throws on malformed input: a bad file returns {@code null}, which the loader treats as
 * "no authored mesh" and the preview falls back to the procedural model. That is the same
 * fail-closed contract the pack builder relies on, so one broken asset cannot take down the preview.
 */
public final class BedrockGeometryParser {

    private BedrockGeometryParser() {
    }

    /**
     * Parses a {@code .geo.json} document.
     *
     * @return the parsed geometry, or {@code null} when the text is not a usable geometry
     */
    public static BedrockGeometry parse(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement rootElement = JsonParser.parseString(json);
            if (rootElement == null || !rootElement.isJsonObject()) return null;
            JsonObject root = rootElement.getAsJsonObject();

            BedrockGeometry geometry = new BedrockGeometry();
            geometry.formatVersion = stringOrNull(root.get("format_version"));

            JsonElement modelsElement = root.get("minecraft:geometry");
            if (modelsElement == null) {
                // 1.8 files key the array differently.
                modelsElement = root.get("geometry");
            }
            if (modelsElement == null || !modelsElement.isJsonArray()) return null;

            for (JsonElement modelElement : modelsElement.getAsJsonArray()) {
                if (!modelElement.isJsonObject()) continue;
                BedrockGeometry.GeometryModel model = parseModel(modelElement.getAsJsonObject());
                if (model != null) geometry.models.add(model);
            }
            return geometry.models.isEmpty() ? null : geometry;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BedrockGeometry.GeometryModel parseModel(JsonObject modelObject) {
        BedrockGeometry.GeometryModel model = new BedrockGeometry.GeometryModel();

        JsonObject description = modelObject.has("description")
                && modelObject.get("description").isJsonObject()
                ? modelObject.getAsJsonObject("description") : new JsonObject();
        model.description.identifier = stringOrNull(description.get("identifier"));
        model.description.textureWidth = intOrDefault(description.get("texture_width"), 64);
        model.description.textureHeight = intOrDefault(description.get("texture_height"), 64);

        JsonElement bonesElement = modelObject.get("bones");
        if (bonesElement == null || !bonesElement.isJsonArray()) return null;
        for (JsonElement boneElement : bonesElement.getAsJsonArray()) {
            if (!boneElement.isJsonObject()) continue;
            BedrockGeometry.Bone bone = parseBone(boneElement.getAsJsonObject());
            if (bone != null) model.bones.add(bone);
        }
        return model;
    }

    private static BedrockGeometry.Bone parseBone(JsonObject boneObject) {
        BedrockGeometry.Bone bone = new BedrockGeometry.Bone();
        bone.name = stringOrNull(boneObject.get("name"));
        if (bone.name == null) return null;
        bone.parent = stringOrNull(boneObject.get("parent"));
        bone.pivot = floatArray(boneObject.get("pivot"), 3);
        bone.rotation = floatArray(boneObject.get("rotation"), 3);

        JsonElement cubesElement = boneObject.get("cubes");
        if (cubesElement != null && cubesElement.isJsonArray()) {
            for (JsonElement cubeElement : cubesElement.getAsJsonArray()) {
                if (!cubeElement.isJsonObject()) continue;
                BedrockGeometry.Cube cube = parseCube(cubeElement.getAsJsonObject());
                if (cube != null) bone.cubes.add(cube);
            }
        }
        return bone;
    }

    private static BedrockGeometry.Cube parseCube(JsonObject cubeObject) {
        BedrockGeometry.Cube cube = new BedrockGeometry.Cube();
        cube.origin = floatArray(cubeObject.get("origin"), 3);
        cube.size = floatArray(cubeObject.get("size"), 3);
        if (cube.origin == null || cube.size == null) return null;
        cube.rotation = floatArray(cubeObject.get("rotation"), 3);
        cube.pivot = floatArray(cubeObject.get("pivot"), 3);
        cube.inflate = floatOrDefault(cubeObject.get("inflate"), 0f);

        JsonElement uvElement = cubeObject.get("uv");
        if (uvElement != null && uvElement.isJsonObject()) {
            JsonObject uvObject = uvElement.getAsJsonObject();
            if (uvObject.has("north") || uvObject.has("up") || uvObject.has("down")) {
                parsePerFace(uvObject, cube.faces);
            } else {
                cube.uv = floatArray(uvObject.get("uv"), 2);
                cube.uvSize = floatArray(uvObject.get("uv_size"), 2);
            }
        } else if (uvElement != null && uvElement.isJsonArray()) {
            // 1.8 form: the cube's own "uv" is the box-UV origin, with the unwrap size derived
            // from the cube dimensions.
            cube.uv = floatArray(uvElement, 2);
        }
        return cube;
    }

    private static void parsePerFace(JsonObject uvObject, BedrockGeometry.UvPerFace faces) {
        faces.north = parseRect(uvObject.get("north"));
        faces.south = parseRect(uvObject.get("south"));
        faces.east = parseRect(uvObject.get("east"));
        faces.west = parseRect(uvObject.get("west"));
        faces.up = parseRect(uvObject.get("up"));
        faces.down = parseRect(uvObject.get("down"));
    }

    private static BedrockGeometry.UvRect parseRect(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        float[] uv = floatArray(object.get("uv"), 2);
        float[] size = floatArray(object.get("uv_size"), 2);
        if (uv == null || size == null) return null;
        return new BedrockGeometry.UvRect(uv[0], uv[1], size[0], size[1]);
    }

    private static String stringOrNull(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        return element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int intOrDefault(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float floatOrDefault(JsonElement element, float fallback) {
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Reads a numeric array. A single number is broadcast to every slot, which is how Bedrock
     * writes a cube whose size is uniform ({@code "size": 4}).
     */
    private static float[] floatArray(JsonElement element, int length) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            float[] values = new float[length];
            for (int i = 0; i < length; i++) {
                values[i] = i < array.size() ? array.get(i).getAsFloat() : 0f;
            }
            return values;
        }
        if (element.isJsonPrimitive()) {
            float single = element.getAsFloat();
            float[] values = new float[length];
            for (int i = 0; i < length; i++) values[i] = single;
            return values;
        }
        return null;
    }
}
