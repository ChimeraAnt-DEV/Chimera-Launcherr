package org.chimeramc.client.launcher.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes controller profiles as portable JSON, for sharing a setup or carrying one
 * between devices.
 *
 * The format is intentionally a small wrapper rather than a bare array of profiles: a header
 * with a format id and schema version lets a future change be recognised instead of being
 * silently mis-parsed, and gives a place to put the controller type — which the profile list
 * alone cannot express, since a slot is only meaningful for one controller type.
 *
 * Import is defensive. A hand-edited or third-party file can contain out-of-range values,
 * missing fields, or plain garbage, and importing must never leave a profile that makes the
 * input path misbehave, so every field is clamped and the result is capped at
 * {@link ControllerProfile#MAX_SLOTS} profiles.
 */
public final class ControllerProfileCodec {

    public static final String FORMAT_ID = "chimera.controller.profiles";
    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENVELOPE_TYPE = new TypeToken<Envelope>() {}.getType();

    private ControllerProfileCodec() {
    }

    /** Wire format for the exported file. */
    public static final class Envelope {
        public String format = FORMAT_ID;
        public int schemaVersion = SCHEMA_VERSION;
        public String controllerType;
        public List<ControllerProfile> profiles = new ArrayList<>();
    }

    /** Result of an import: the usable profiles plus how many entries were dropped. */
    public static final class ImportResult {
        public final List<ControllerProfile> profiles;
        public final int skipped;

        ImportResult(List<ControllerProfile> profiles, int skipped) {
            this.profiles = profiles;
            this.skipped = skipped;
        }

        public boolean isEmpty() {
            return profiles.isEmpty();
        }
    }

    /** Serialises one controller type's profiles. */
    public static String export(ControllerType type, List<ControllerProfile> profiles) {
        Envelope envelope = new Envelope();
        envelope.controllerType = type == null ? ControllerType.XBOX.name() : type.name();
        if (profiles != null) {
            envelope.profiles = profiles;
        }
        return GSON.toJson(envelope);
    }

    /**
     * Parses an exported file.
     *
     * @throws IllegalArgumentException when the text is not a controller-profile bundle, so
     *         the caller can tell the user the file is the wrong kind rather than showing
     *         "imported 0 profiles".
     */
    public static ImportResult importFrom(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        Envelope envelope;
        try {
            envelope = GSON.fromJson(json, ENVELOPE_TYPE);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("malformed");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("malformed");
        }
        // Tolerate a missing format only when there is at least one profile, so a bare array
        // style export from another tool still works while pure garbage is still rejected.
        if (envelope.format != null && !FORMAT_ID.equals(envelope.format)) {
            throw new IllegalArgumentException("wrong format");
        }
        if (envelope.profiles == null || envelope.profiles.isEmpty()) {
            throw new IllegalArgumentException("no profiles");
        }

        List<ControllerProfile> clean = new ArrayList<>();
        int skipped = 0;
        for (ControllerProfile candidate : envelope.profiles) {
            if (candidate == null) {
                skipped++;
                continue;
            }
            if (clean.size() >= ControllerProfile.MAX_SLOTS) {
                skipped++;
                continue;
            }
            clean.add(sanitize(candidate));
        }
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("no usable profiles");
        }
        return new ImportResult(clean, skipped);
    }

    /** Controller type recorded in an export, or null when absent/unknown. */
    public static ControllerType typeOf(String json) {
        try {
            Envelope envelope = GSON.fromJson(json, ENVELOPE_TYPE);
            if (envelope == null || envelope.controllerType == null) return null;
            for (ControllerType type : ControllerType.values()) {
                if (type.name().equals(envelope.controllerType)) return type;
            }
        } catch (JsonSyntaxException ignored) {
        }
        return null;
    }

    /**
     * Rebuilds a profile through its public setters so every clamp runs. Gson bypasses
     * constructors and setters, so a value like a dead zone of 5.0 would otherwise be
     * written straight into the object and later break the response math.
     */
    private static ControllerProfile sanitize(ControllerProfile raw) {
        String name = raw.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "Imported";
        }
        ControllerProfile clean = new ControllerProfile(name.trim());
        clean.setLeftDeadZone(raw.getLeftDeadZone());
        clean.setRightDeadZone(raw.getRightDeadZone());
        clean.setLeftStickSensitivity(raw.getLeftStickSensitivity());
        clean.setRightStickSensitivity(raw.getRightStickSensitivity());
        clean.setVibrationEnabled(raw.isVibrationEnabled());
        clean.setLeftCurve(raw.getLeftCurve());
        clean.setRightCurve(raw.getRightCurve());
        clean.setLeftTriggerCurve(raw.getLeftTriggerCurve());
        clean.setRightTriggerCurve(raw.getRightTriggerCurve());
        // Anti-drift survives a round trip, including the measured floors, so re-importing a
        // profile on the same pad does not force the user to calibrate again.
        clean.setAntiDriftEnabled(raw.isAntiDriftEnabled());
        clean.setLeftStickNoiseFloor(raw.getLeftStickNoiseFloor());
        clean.setRightStickNoiseFloor(raw.getRightStickNoiseFloor());
        // Remaps go through setRemap so a null map or an out-of-range key cannot survive.
        java.util.Map<Integer, Integer> remaps = raw.getButtonRemaps();
        if (remaps != null) {
            for (java.util.Map.Entry<Integer, Integer> entry : remaps.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                clean.setRemap(entry.getKey(), entry.getValue());
            }
        }
        clean.setRumbleStrength(raw.getRumbleStrength());
        // Alternate maps are rebuilt through the same setters so a layer from a hand-edited file
        // cannot carry a negative key code into the hot path.
        java.util.List<RemapLayer> layers = raw.getRemapLayers();
        if (layers != null) {
            for (RemapLayer rawLayer : layers) {
                if (rawLayer == null) continue;
                RemapLayer layer = new RemapLayer(
                        rawLayer.getName() == null ? "Layer" : rawLayer.getName().trim());
                layer.setModifierKeyCode(rawLayer.getModifierKeyCode());
                java.util.Map<Integer, Integer> layerRemaps = rawLayer.getRemaps();
                if (layerRemaps != null) {
                    for (java.util.Map.Entry<Integer, Integer> entry : layerRemaps.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) continue;
                        layer.setRemap(entry.getKey(), entry.getValue());
                    }
                }
                clean.addRemapLayer(layer);
            }
        }
        // Click limits and repeat rates were previously dropped on import; carry them so a profile
        // shared to stop double-fire does not arrive with the fix missing.
        for (java.util.Map.Entry<Integer, Integer> entry : raw.getRepeatRates().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                clean.setRepeatRate(entry.getKey(), entry.getValue());
            }
        }
        return clean;
    }
}
