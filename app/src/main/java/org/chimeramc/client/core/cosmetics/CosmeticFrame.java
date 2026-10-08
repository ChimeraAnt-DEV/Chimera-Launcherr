package org.chimeramc.client.core.cosmetics;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One frame of cosmetic transform data, packed into the byte layout the native preloader reads.
 *
 * <p>This is the Java half of the native cosmetics render path. The launcher computes the cape
 * chain's per-segment lean/sway, the pet's per-bone rotations and the hat's head-look on the game's
 * own frame tick, packs them here, and hands the buffer across JNI every frame. The native render
 * hook then draws the meshes directly in the live frame — no resource pack, no world reload.
 *
 * <p>Pure Java (no Android types), so the wire layout is unit-testable: a wrong field order or a
 * missing length prefix is exactly the bug that only surfaces when the native side misreads every
 * value after it, and there is no device in the build environment to catch it.
 *
 * <p><b>Layout</b> (little-endian, the ARM64 wire order):
 * <pre>
 *   u32 magic            'CHF1'
 *   u32 flags            bit0 cape, bit1 accessory, bit2 pet
 *   u32 capeSegments     per-segment {f32 leanDeg, f32 swayDeg}
 *   u32 accessoryKind    catalogue AccessoryKind ordinal (0 = none)
 *   f32 headPitchDeg, f32 headYawDeg
 *   u32 petBones         per-bone {u16 nameLen, name utf8, f32 rotX, f32 rotY, f32 rotZ}
 *   f32 petOffsetX/Y/Z   where the pet sits relative to the player
 *   f32 petBodyBob, f32 petCrouchDrop, f32 petLeanX
 * </pre>
 */
public final class CosmeticFrame {

    /** The frame magic; bumped when the layout changes so the native side can reject a stale build. */
    public static final int MAGIC = 0x43484631; // 'CHF1'

    public static final int FLAG_CAPE = 1;
    public static final int FLAG_ACCESSORY = 1 << 1;
    public static final int FLAG_PET = 1 << 2;

    /** One pet bone's rotation, in degrees, about its own pivot. */
    public static final class BoneRotation {
        public final String bone;
        public final float rotX, rotY, rotZ;

        public BoneRotation(String bone, float rotX, float rotY, float rotZ) {
            this.bone = bone == null ? "" : bone;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
        }
    }

    public final int flags;
    /** Per-segment {lean, sway} in degrees, flattened: index*2 = lean, index*2+1 = sway. */
    public final float[] capeLeanSway;
    public final int accessoryKind;
    public final float headPitchDeg;
    public final float headYawDeg;
    public final List<BoneRotation> petBones;
    public final float petOffsetX, petOffsetY, petOffsetZ;
    public final float petBodyBob, petCrouchDrop, petLeanX;

    private CosmeticFrame(int flags, float[] capeLeanSway, int accessoryKind,
                          float headPitchDeg, float headYawDeg, List<BoneRotation> petBones,
                          float petOffsetX, float petOffsetY, float petOffsetZ,
                          float petBodyBob, float petCrouchDrop, float petLeanX) {
        this.flags = flags;
        this.capeLeanSway = capeLeanSway;
        this.accessoryKind = accessoryKind;
        this.headPitchDeg = headPitchDeg;
        this.headYawDeg = headYawDeg;
        this.petBones = petBones;
        this.petOffsetX = petOffsetX;
        this.petOffsetY = petOffsetY;
        this.petOffsetZ = petOffsetZ;
        this.petBodyBob = petBodyBob;
        this.petCrouchDrop = petCrouchDrop;
        this.petLeanX = petLeanX;
    }

    /** A frame with nothing equipped; the render hook draws nothing. */
    public static CosmeticFrame empty() {
        return new CosmeticFrame(0, new float[0], 0, 0f, 0f, new ArrayList<>(),
                0f, 0f, 0f, 0f, 0f, 0f);
    }

    /**
     * Builds a frame from the equipped pieces and the current motion/pose state.
     *
     * @param cape           the equipped cape, or null
     * @param accessory      the worn accessory, or null
     * @param pet            the equipped pet, or null
     * @param petPose        the pet's per-frame pose (already computed), or null
     * @param moveSpeed      the player's horizontal speed, clamped 0..1
     * @param jumping        whether the player is airborne
     * @param verticalSpeed  the player's vertical speed
     * @param distanceMoved  accumulated horizontal distance (drives the flutter phase)
     * @param capeFlap       the vanilla cape-flap signal 0..1, or 0
     * @param bodyYawDegrees the player's yaw, for the bounded turn-sway term
     * @param headPitchDeg   the head look pitch, for a head-worn accessory
     * @param headYawDeg     the head look yaw, for a head-worn accessory
     * @param capeSegments   segments in the cape chain (CapeGeometry.SEGMENT_COUNT)
     */
    public static CosmeticFrame build(CosmeticCatalog.Cape cape, CosmeticCatalog.Accessory accessory,
                                      CosmeticCatalog.Pet pet, PetPose petPose,
                                      double moveSpeed, boolean jumping, double verticalSpeed,
                                      double distanceMoved, double capeFlap, double bodyYawDegrees,
                                      float headPitchDeg, float headYawDeg, int capeSegments) {
        int flags = 0;
        float[] leanSway = new float[0];
        if (cape != null && capeSegments > 0) {
            flags |= FLAG_CAPE;
            leanSway = new float[capeSegments * 2];
            for (int i = 1; i <= capeSegments; i++) {
                leanSway[(i - 1) * 2] = (float) CapeAnimationCurve.segmentLeanDegrees(
                        i, capeSegments, moveSpeed, jumping, verticalSpeed, distanceMoved, capeFlap);
                leanSway[(i - 1) * 2 + 1] = (float) CapeAnimationCurve.segmentSwayDegrees(
                        i, capeSegments, moveSpeed, distanceMoved, bodyYawDegrees);
            }
        }
        int accessoryKind = 0;
        if (accessory != null && accessory.kind != CosmeticCatalog.AccessoryKind.NONE) {
            flags |= FLAG_ACCESSORY;
            accessoryKind = accessory.kind.ordinal();
        }
        List<BoneRotation> bones = new ArrayList<>();
        float offX = 0f, offY = 0f, offZ = 0f, bob = 0f, crouch = 0f, leanX = 0f;
        if (pet != null && petPose != null) {
            flags |= FLAG_PET;
            // The same per-bone vocabulary the in-game gait controller drives; only the bones the
            // pose actually moves are emitted, so the native side applies exactly these.
            bones.add(new BoneRotation("head", petPose.headPitch, petPose.headYaw, 0f));
            bones.add(new BoneRotation("tail", 0f, petPose.tailWag * 16f, 0f));
            bones.add(new BoneRotation("wing_l", 0f, 0f, petPose.wingFlap * 55f));
            bones.add(new BoneRotation("wing_r", 0f, 0f, -petPose.wingFlap * 55f));
            bones.add(new BoneRotation("leg_a", 0f, 0f, petPose.legSwing * 30f));
            bones.add(new BoneRotation("leg_b", 0f, 0f, -petPose.legSwing * 30f));
            bones.add(new BoneRotation("leg_c", 0f, 0f, petPose.legSwing * 30f));
            offX = 20f; // beside the player, matching the preview
            offY = petPose.bodyBob - petPose.crouchDrop;
            offZ = 0f;
            bob = petPose.bodyBob;
            crouch = petPose.crouchDrop;
            leanX = petPose.leanX;
        }
        return new CosmeticFrame(flags, leanSway, accessoryKind, headPitchDeg, headYawDeg, bones,
                offX, offY, offZ, bob, crouch, leanX);
    }

    /** Serialises this frame to the native wire layout. */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        putInt(out, MAGIC);
        putInt(out, flags);
        putInt(out, capeLeanSway.length / 2);
        for (float value : capeLeanSway) putFloat(out, value);
        putInt(out, accessoryKind);
        putFloat(out, headPitchDeg);
        putFloat(out, headYawDeg);
        putInt(out, petBones.size());
        for (BoneRotation bone : petBones) {
            byte[] name = bone.bone.getBytes(StandardCharsets.UTF_8);
            putShort(out, name.length);
            out.write(name, 0, name.length);
            putFloat(out, bone.rotX);
            putFloat(out, bone.rotY);
            putFloat(out, bone.rotZ);
        }
        putFloat(out, petOffsetX);
        putFloat(out, petOffsetY);
        putFloat(out, petOffsetZ);
        putFloat(out, petBodyBob);
        putFloat(out, petCrouchDrop);
        putFloat(out, petLeanX);
        return out.toByteArray();
    }

    private static void putInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void putShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void putFloat(ByteArrayOutputStream out, float value) {
        putInt(out, Float.floatToIntBits(value));
    }
}
