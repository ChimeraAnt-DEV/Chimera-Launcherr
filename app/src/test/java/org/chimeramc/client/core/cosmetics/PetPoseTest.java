package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    /**
     * The preview shows the contextual crawler states for the gaits the species actually has, and
     * the beetle (a crawler that can fly) flies alongside instead of clinging.
     *
     * <p>The blown-back/water cling for a spider and an ant is reached in-game by the animation
     * controller on the player's airborne/swimming queries (pinned by {@code PetAnimationsTest});
     * the preview only offers a species its own supported gaits, so a spider cannot be asked to fly
     * here and falls back to a walk.
     */
    @Test
    public void aCrawlersContextualStatesAndTheFlyingBugsFlyAlongside() {
        // The beetle can fly, so it reaches the fly pose — and must not cling.
        PetPose beetleFly = new PetPose();
        beetleFly.compute(CosmeticCatalog.PetSpecies.BEETLE, CosmeticCatalog.PetLocomotion.FLY, 0.25f);
        assertFalse("a beetle flies alongside instead of clinging", beetleFly.ridesHead);

        // A spider has no fly gait, so the preview falls back to a walk rather than a cling.
        PetPose spiderFly = new PetPose();
        spiderFly.compute(CosmeticCatalog.PetSpecies.SPIDER, CosmeticCatalog.PetLocomotion.FLY, 0.25f);
        assertFalse("a spider has no fly gait in the preview", spiderFly.ridesHead);

        // Both still crawl on the head when the player runs and look around when crouching.
        PetPose beetleRun = new PetPose();
        beetleRun.compute(CosmeticCatalog.PetSpecies.BEETLE, CosmeticCatalog.PetLocomotion.RUN, 0.25f);
        assertTrue("a beetle still crawls on the head when running", beetleRun.ridesHead);
        PetPose beetleCrouch = new PetPose();
        beetleCrouch.compute(CosmeticCatalog.PetSpecies.BEETLE,
                CosmeticCatalog.PetLocomotion.CROUCH, 0.25f);
        assertTrue("a beetle still rides the head when crouching", beetleCrouch.ridesHead);
        PetPose spiderRun = new PetPose();
        spiderRun.compute(CosmeticCatalog.PetSpecies.SPIDER, CosmeticCatalog.PetLocomotion.RUN, 0.25f);
        assertTrue("a spider crawls on the head when running", spiderRun.ridesHead);
        PetPose spiderCrouch = new PetPose();
        spiderCrouch.compute(CosmeticCatalog.PetSpecies.SPIDER,
                CosmeticCatalog.PetLocomotion.CROUCH, 0.25f);
        assertTrue("a spider rides the head when crouching", spiderCrouch.ridesHead);
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
