package org.chimeramc.client.core.javabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the consent gate that stands between mod source and the network.
 *
 * <p>The spec forbids sending mod source to an LLM without explicit user consent. These tests are
 * the reason that rule cannot be quietly relaxed: they assert every combination that would allow a
 * send, so removing one condition fails here.
 */
public class LlmConsentTest {

    @Test
    public void allowsASendOnlyWithConsentConfigurationAndAUserAction() {
        assertTrue(LlmConsent.isAllowed(true, true, true));
    }

    @Test
    public void refusesWithoutConsent() {
        assertFalse(LlmConsent.isAllowed(false, true, true));
    }

    @Test
    public void refusesWithoutConfiguration() {
        assertFalse(LlmConsent.isAllowed(true, false, true));
    }

    @Test
    public void refusesWithoutAUserAction() {
        // This is the guard against a background path ever sending mod source on its own.
        assertFalse(LlmConsent.isAllowed(true, true, false));
    }

    @Test
    public void refusesWhenNothingIsSet() {
        assertFalse(LlmConsent.isAllowed(false, false, false));
    }

    @Test
    public void onlyTheCurrentConsentVersionCounts() {
        assertTrue(LlmConsent.isCurrentVersion(LlmConsent.CONSENT_VERSION));
        assertFalse(LlmConsent.isCurrentVersion("0"));
        assertFalse(LlmConsent.isCurrentVersion(null));
        assertFalse(LlmConsent.isCurrentVersion(""));
    }

    @Test
    public void theConsentTextStatesItIsAReimplementation() {
        assertTrue(LlmConsent.CONSENT_TEXT.contains("reimplementation"));
        assertTrue(LlmConsent.CONSENT_TEXT.contains("decompiled source"));
    }
}
