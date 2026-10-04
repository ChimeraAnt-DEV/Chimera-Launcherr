package org.chimeramc.client.core.cosmetics;

/**
 * The animation state of a pet for one frame, as pure arithmetic.
 *
 * <p>The renderer only needs a handful of scalars — how far a leg has swung, whether a wing is up,
 * how much the body is bobbing — and deriving them here keeps the gait rules unit-testable without
 * a Canvas or a device. It also means the same pet animates identically wherever it is drawn.
 *
 * <p><b>Per-bone, not one rigid rock.</b> Earlier this exposed a single {@code legSwing} and a body
 * bob, so a pet moved as one block. It now carries a rotation for each bone the mesh has (head yaw
 * and pitch, tail wag, wing flap, leg swing, body lean), so a leg swings from the hip and the head
 * turns independently of the body — matching the per-bone animations the in-game controller plays.
 *
 * <p><b>Contextual bug behaviour.</b> The crawling bugs do not just trot: when the player runs they
 * crawl in a small loop on the player's head, when the player crouches they stop and look around,
 * and when swimming they cling low and paddle. A crawler with no flight of its own (spider, ant)
 * also clings and leans back into the wind while the player flies; a crawler that can fly (beetle)
 * flies alongside instead. {@link #ridesHead}, {@link #leanX} and
 * {@link #crawlLoopX}/{@link #crawlLoopZ} carry those states, and they are derived from the same
 * rules the in-game {@link PetAnimations} controller uses.
 *
 * <p>Everything is driven by a normalised {@code phase} in 0..1 that the caller advances at the
 * gait's own speed, so the cycles do not depend on the frame rate.
 */
public final class PetPose {

    /** Horizontal leg swing, -1..1. */
    public float legSwing;
    /** Vertical body bob in model pixels. */
    public float bodyBob;
    /** Wing flap, 0 (folded) to 1 (fully raised). */
    public float wingFlap;
    /** Head bob in model pixels. */
    public float headBob;
    /** How far the body sinks when crouching, in model pixels. */
    public float crouchDrop;
    /** Tail wag, -1..1. */
    public float tailWag;
    /** Swim paddle, -1..1 (a slower, wider stroke than a walk). */
    public float swimStroke;
    /** Head yaw in degrees; the head turns independently of the body. */
    public float headYaw;
    /** Head pitch in degrees; a peck or a look down. */
    public float headPitch;
    /** Whole-body pitch in degrees; a cling leans the bug against the wind or water. */
    public float leanX;
    /** Whether the pet is riding the player's head this frame (crawling bugs only). */
    public boolean ridesHead;
    /** The crawl loop's lateral offset on the head, in model pixels. */
    public float crawlLoopX;
    /** The crawl loop's depth offset on the head, in model pixels. */
    public float crawlLoopZ;

    /**
     * Recomputes the pose for a species and the gait it is currently using.
     *
     * @param species    the pet's species
     * @param locomotion the gait to animate; a gait the species does not support is treated as a walk
     * @param phase      a 0..1 cycle position, advanced by {@link #cyclesPerSecond}
     */
    public void compute(CosmeticCatalog.PetSpecies species, CosmeticCatalog.PetLocomotion locomotion,
                        float phase) {
        CosmeticCatalog.PetSpecies s = species == null ? CosmeticCatalog.PetSpecies.CAT : species;
        CosmeticCatalog.PetLocomotion gait = locomotion;
        if (gait == null || !s.supports(gait)) {
            gait = s.supports(CosmeticCatalog.PetLocomotion.WALK)
                    ? CosmeticCatalog.PetLocomotion.WALK
                    : CosmeticCatalog.PetLocomotion.FLY;
        }
        reset();
        double t = phase * Math.PI * 2.0;

        boolean crawler = s.isCrawler();
        boolean clingsInAir = s.clingsInAir();
        legSwing = (float) Math.sin(t);
        bodyBob = (float) Math.sin(t * 2.0)
                * (gait == CosmeticCatalog.PetLocomotion.RUN ? 0.7f : 0.4f);
        headBob = (float) Math.sin(t * 2.0 + 0.6) * 0.25f;
        tailWag = (float) Math.sin(t * 1.5);
        swimStroke = (float) Math.sin(t * 0.6);

        switch (gait) {
            case IDLE:
                legSwing = 0f;
                bodyBob = (float) Math.sin(t) * 0.25f;
                headYaw = (float) Math.sin(t * 0.5) * 7f;
                tailWag = (float) Math.sin(t * 0.8);
                break;
            case FLY:
                wingFlap = (float) ((Math.sin(t * 4.0) + 1.0) * 0.5);
                bodyBob = (float) Math.sin(t * 2.0) * 0.8f;
                if (clingsInAir) {
                    // A crawling bug that cannot fly clings flat and leans back into the wind; its
                    // elytra buzz. A crawler that *can* fly (beetle) uses the ordinary fly pose
                    // above and flies alongside the player instead.
                    wingFlap *= 0.7f;
                    ridesHead = true;
                    leanX = -55f;
                    headPitch = -20f;
                }
                break;
            case SWIM:
                wingFlap = 0f;
                legSwing = (float) Math.sin(t * 0.6);
                bodyBob = (float) Math.sin(t * 1.2) * 0.3f;
                if (crawler) {
                    // A distinct cling: low and level with a surface bob, not the airborne lean.
                    ridesHead = true;
                    leanX = -12f;
                    headPitch = 8f;
                    headYaw = (float) Math.sin(t * 0.8) * 12f;
                }
                break;
            case CROUCH:
                wingFlap = 0f;
                legSwing = 0f;
                if (crawler) {
                    // Stop crawling, hold still and look around.
                    ridesHead = true;
                    headYaw = (float) Math.sin(t * 0.75) * 35f;
                    bodyBob = 0f;
                } else {
                    crouchDrop = 1.4f;
                    headPitch = 10f;
                }
                break;
            case RUN:
                wingFlap = 0f;
                if (crawler) {
                    // A small looping crawl in place on the player's head.
                    ridesHead = true;
                    crawlLoopX = (float) Math.cos(t) * 0.5f;
                    crawlLoopZ = (float) Math.sin(t) * 0.5f;
                    bodyBob = Math.abs((float) Math.sin(t * 2.0)) * 0.15f;
                }
                break;
            case WALK:
            default:
                wingFlap = 0f;
                break;
        }
    }

    private void reset() {
        legSwing = 0f;
        bodyBob = 0f;
        wingFlap = 0f;
        headBob = 0f;
        crouchDrop = 0f;
        tailWag = 0f;
        swimStroke = 0f;
        headYaw = 0f;
        headPitch = 0f;
        leanX = 0f;
        ridesHead = false;
        crawlLoopX = 0f;
        crawlLoopZ = 0f;
    }

    /** The gait cycle rate, in cycles per second, for a locomotion. */
    public static float cyclesPerSecond(CosmeticCatalog.PetLocomotion locomotion) {
        if (locomotion == null) return 1.2f;
        switch (locomotion) {
            case IDLE: return 0.6f;
            case RUN: return 2.6f;
            case CROUCH: return 0.7f;
            case FLY: return 3.0f;
            case SWIM: return 1.6f;
            case WALK:
            default: return 1.2f;
        }
    }

    /**
     * The gait a pet should use for a given motion context.
     *
     * <p>A pet that cannot do the requested gait falls back to the closest one it can, so a
     * non-flying cat asked to fly simply runs instead of standing frozen.
     */
    public static CosmeticCatalog.PetLocomotion resolveGait(CosmeticCatalog.PetSpecies species,
                                                            CosmeticCatalog.PetLocomotion wanted) {
        if (species == null) return CosmeticCatalog.PetLocomotion.WALK;
        if (wanted != null && species.supports(wanted)) return wanted;
        if (species.supports(CosmeticCatalog.PetLocomotion.WALK)) {
            return CosmeticCatalog.PetLocomotion.WALK;
        }
        return CosmeticCatalog.PetLocomotion.FLY;
    }
}
