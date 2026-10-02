package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the cheat refusal rules.
 *
 * <p>The detector must catch the patterns the spec names, in any of the mod's identifying text,
 * and must not refuse an ordinary mod that merely mentions a related word ("reach" in a build
 * mod's description is a false positive worth guarding against).
 */
public class CheatPatternDetectorTest {

    @Test
    public void refusesKillAuraInTheName() {
        CheatPatternDetector.Verdict verdict =
                CheatPatternDetector.check("killaura", "Kill Aura", "hits everything");
        assertTrue(verdict.refused);
        assertEquals("kill aura", verdict.pattern);
    }

    @Test
    public void refusesWhenThePatternIsOnlyInTheDescription() {
        assertTrue(CheatPatternDetector.check("coolmod", "Cool Mod",
                "includes an aimbot for pvp").refused);
    }

    @Test
    public void refusesWhenThePatternIsOnlyInTheId() {
        assertTrue(CheatPatternDetector.check("wallhack-mod", "Overlay", "").refused);
    }

    @Test
    public void matchesSeparatorVariants() {
        // killaura, kill_aura, kill-aura and "kill aura" must all be caught.
        assertTrue(CheatPatternDetector.isCheat("x", "killaura", ""));
        assertTrue(CheatPatternDetector.isCheat("kill_aura", "", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "kill-aura", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "", "kill aura"));
    }

    @Test
    public void refusesEveryNamedPatternClass() {
        assertTrue(CheatPatternDetector.isCheat("x", "Fly Hack", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "SpeedHack", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "X-Ray", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "AutoClicker", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "Aimbot", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "Reach Hack", ""));
        assertTrue(CheatPatternDetector.isCheat("x", "AntiKnockback", ""));
    }

    @Test
    public void doesNotRefuseAnOrdinaryMod() {
        CheatPatternDetector.Verdict verdict = CheatPatternDetector.check(
                "coolmod", "Cool Mod", "Adds a new sword and a couple of recipes");
        assertFalse(verdict.refused);
        assertTrue(verdict.isClean());
        assertNull(verdict.pattern);
    }

    @Test
    public void doesNotRefuseAWordThatMerelyContainsAKeyword() {
        // "reachable" contains "reach" but is not a reach hack; "spreadsheet" is not "speed".
        assertFalse(CheatPatternDetector.isCheat("x", "Reachable Worlds",
                "makes far lands reachable"));
        assertFalse(CheatPatternDetector.isCheat("x", "Spreadsheet Tool",
                "exports a spreadsheet of your inventory"));
    }

    @Test
    public void toleratesNullFields() {
        CheatPatternDetector.Verdict verdict = CheatPatternDetector.check(null, null, null);
        assertTrue(verdict.isClean());
        assertTrue(CheatPatternDetector.check(null, "Kill Aura", null).refused);
    }

    @Test
    public void checkFromManifestUsesItsFields() {
        JavaModManifest manifest =
                JavaModManifest.parseFabric("{\"id\":\"aimbot\",\"name\":\"Aim\"}").manifest;
        assertNotNull(manifest);
        assertTrue(CheatPatternDetector.check(manifest).refused);
    }

    @Test
    public void checkFromNullManifestIsClean() {
        assertTrue(CheatPatternDetector.check((JavaModManifest) null).isClean());
    }

    @Test
    public void exposesItsKnownPatternsForAHelpText() {
        assertFalse(CheatPatternDetector.knownPatternLabels().isEmpty());
        assertTrue(CheatPatternDetector.knownPatternLabels().contains("kill aura"));
    }
}
