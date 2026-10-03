package org.chimeramc.client.core.cosmetics;

/**
 * The animation state of a pet for one frame, as pure arithmetic.
 *
 * <p>The renderer only needs a handful of scalars — how far a leg has swung, whether a wing is up,
 * how much the body is bobbing — and deriving them here keeps the gait rules unit-testable without
 * a Canvas or a device. It also means the same pet animates identically wherever it is drawn.
 *
 * <p>Everything is driven by a normalised {@code phase} in 0..1 that the caller advances at the
 * gait's own speed, so the walk/run/fly cycles do not depend on the frame rate.
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
        double t = phase * Math.PI * 2.0;

        legSwing = (float) Math.sin(t);
        bodyBob = (float) Math.sin(t * 2.0) * (gait == CosmeticCatalog.PetLocomotion.RUN ? 0.7f : 0.4f);
        headBob = (float) Math.sin(t * 2.0 + 0.6) * 0.25f;
        tailWag = (float) Math.sin(t * 1.5);
        swimStroke = (float) Math.sin(t * 0.6);

        switch (gait) {
            case FLY:
                // Wings beat faster than the body cycle; crawlers get a smaller, buzzing flap.
                wingFlap = (float) ((Math.sin(t * 4.0) + 1.0) * 0.5);
                if (s.isCrawler()) wingFlap *= 0.7f;
                bodyBob = (float) Math.sin(t * 2.0) * 0.8f;
                break;
            case SWIM:
                // A swimmer paddles with its legs and holds its body level; no wing flap.
                wingFlap = 0f;
                legSwing = (float) Math.sin(t * 0.6);
                bodyBob = (float) Math.sin(t * 1.2) * 0.3f;
                break;
            case CROUCH:
                crouchDrop = 1.4f;
                wingFlap = 0f;
                break;
            case RUN:
            case WALK:
            default:
                wingFlap = 0f;
                break;
        }
    }

    /** The gait cycle rate, in cycles per second, for a locomotion. */
    public static float cyclesPerSecond(CosmeticCatalog.PetLocomotion locomotion) {
        if (locomotion == null) return 1.2f;
        switch (locomotion) {
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
