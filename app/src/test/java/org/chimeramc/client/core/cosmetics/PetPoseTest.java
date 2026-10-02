package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The pet gait rules, pinned so a flying cat or a swimmer without a swim set is caught here rather
 * than in the preview.
 */
public class PetPoseTest {

    @Test
    public void anUnsupportedGaitFallsBackToAUsableOne() {
        // A cat cannot fly, so asking for flight must not leave it in a frozen gait.
        assertEquals(CosmeticCatalog.PetLocomotion.WALK,
                PetPose.resolveGait(CosmeticCatalog.PetSpecies.CAT, CosmeticCatalog.PetLocomotion.FLY));
        // A null request is a walk for a walker and the sole gait for a walkless species.
        assertEquals(CosmeticCatalog.PetLocomotion.WALK,
                PetPose.resolveGait(CosmeticCatalog.PetSpecies.CAT, null));
    }

    @Test
    public void aSupportedGaitIsKept() {
        assertEquals(CosmeticCatalog.PetLocomotion.FLY,
                PetPose.resolveGait(CosmeticCatalog.PetSpecies.PARROT,
                        CosmeticCatalog.PetLocomotion.FLY));
    }

    @Test
    public void runningSwingsTheLegsFurtherThanWalking() {
        PetPose walk = new PetPose();
        walk.compute(CosmeticCatalog.PetSpecies.DOG, CosmeticCatalog.PetLocomotion.WALK, 0.25f);
        PetPose run = new PetPose();
        run.compute(CosmeticCatalog.PetSpecies.DOG, CosmeticCatalog.PetLocomotion.RUN, 0.25f);
        assertTrue("a run must bob more than a walk",
                Math.abs(run.bodyBob) >= Math.abs(walk.bodyBob));
    }

    @Test
    public void flyingBeatsTheWings() {
        PetPose pose = new PetPose();
        pose.compute(CosmeticCatalog.PetSpecies.PARROT, CosmeticCatalog.PetLocomotion.FLY, 0.1f);
        assertTrue("a flyer must have a wing flap", pose.wingFlap > 0f);
        assertTrue("a wing flap stays normalised", pose.wingFlap <= 1f);
    }

    @Test
    public void swimmingPaddlesWithNoWings() {
        PetPose pose = new PetPose();
        pose.compute(CosmeticCatalog.PetSpecies.AXOLOTL, CosmeticCatalog.PetLocomotion.SWIM, 0.3f);
        assertEquals("a swimmer folds its wings", 0f, pose.wingFlap, 1e-5f);
        assertTrue("a swimmer paddles", Math.abs(pose.swimStroke) > 0f);
    }

    @Test
    public void crouchingSinksTheBody() {
        PetPose pose = new PetPose();
        pose.compute(CosmeticCatalog.PetSpecies.CAT, CosmeticCatalog.PetLocomotion.CROUCH, 0.5f);
        assertTrue("a crouch must lower the body", pose.crouchDrop > 0f);
    }

    @Test
    public void aCrawlersFlapIsSmallerThanAFlyers() {
        PetPose crawler = new PetPose();
        crawler.compute(CosmeticCatalog.PetSpecies.SPIDER, CosmeticCatalog.PetLocomotion.FLY, 0.1f);
        PetPose flyer = new PetPose();
        flyer.compute(CosmeticCatalog.PetSpecies.PARROT, CosmeticCatalog.PetLocomotion.FLY, 0.1f);
        assertTrue("a crawler's elytra buzzes less than a bird's wings",
                crawler.wingFlap <= flyer.wingFlap);
    }

    @Test
    public void gaitsHaveDistinctSpeeds() {
        assertTrue(PetPose.cyclesPerSecond(CosmeticCatalog.PetLocomotion.RUN)
                > PetPose.cyclesPerSecond(CosmeticCatalog.PetLocomotion.WALK));
        assertTrue(PetPose.cyclesPerSecond(CosmeticCatalog.PetLocomotion.FLY)
                > PetPose.cyclesPerSecond(CosmeticCatalog.PetLocomotion.SWIM));
    }

    @Test
    public void everyGaitProducesFinitePoseValues() {
        for (CosmeticCatalog.PetSpecies species : CosmeticCatalog.PetSpecies.values()) {
            for (CosmeticCatalog.PetLocomotion gait : CosmeticCatalog.PetLocomotion.values()) {
                PetPose pose = new PetPose();
                for (int i = 0; i <= 10; i++) {
                    pose.compute(species, gait, i / 10f);
                    assertTrue(Float.isFinite(pose.legSwing));
                    assertTrue(Float.isFinite(pose.bodyBob));
                    assertTrue(Float.isFinite(pose.wingFlap));
                    assertTrue(Float.isFinite(pose.crouchDrop));
                }
            }
        }
    }
}
