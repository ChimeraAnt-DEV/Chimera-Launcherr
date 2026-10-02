package org.chimeramc.client.core.javabridge;

/**
 * The rule for whether an LLM request may be sent at all.
 *
 * <p>The spec is explicit: "No network calls to LLM providers without explicit user consent." That
 * consent is a recorded, versioned decision — not a setting that defaults on, and not implied by
 * having configured an endpoint. Splitting the rule out here makes it testable in isolation, so
 * "does an unconfigured install send a request?" and "does consent survive an endpoint change?"
 * are answered by a unit test rather than by reading the client.
 */
public final class LlmConsent {

    /** The text the user must accept; bumping it invalidates previously recorded consent. */
    public static final String CONSENT_VERSION = "1";

    public static final String CONSENT_TEXT =
            "JavaBridge sends the mod's decompiled source code to the LLM provider you configure, "
            + "over the internet. This is a reimplementation, not the original mod: the generated "
            + "mod is written from scratch and may behave differently. Nothing is uploaded until "
            + "you press Port, and each port asks again. Continue?";

    private LlmConsent() {}

    /**
     * Whether a request may be sent.
     *
     * <p>All three must hold: the user recorded consent for the current consent text version, an
     * endpoint and key are configured, and the user actually pressed Port (the caller passes
     * {@code userInitiated} as true only from the confirm step).
     */
    public static boolean isAllowed(boolean consentGranted, boolean configured,
                                    boolean userInitiated) {
        return consentGranted && configured && userInitiated;
    }

    /** True when the recorded consent version matches the current one. */
    public static boolean isCurrentVersion(String recordedVersion) {
        return CONSENT_VERSION.equals(recordedVersion);
    }
}
