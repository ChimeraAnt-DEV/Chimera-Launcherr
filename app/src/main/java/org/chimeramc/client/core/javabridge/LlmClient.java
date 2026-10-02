package org.chimeramc.client.core.javabridge;

/**
 * Sends a porting prompt to an LLM and returns its reply.
 *
 * <p>An interface so the porter can be driven by a fake in tests without a network call, and so the
 * provider (OpenAI-compatible chat completions today) is one implementation behind a stable seam.
 * Implementations must refuse to send unless {@link LlmConsent#isAllowed} holds — the consent rule
 * is the caller's, but the client is the last line of defence and re-checks it.
 */
public interface LlmClient {

    /** The outcome of one request. */
    final class Result {
        public final boolean success;
        public final String reply;
        public final String error;

        private Result(boolean success, String reply, String error) {
            this.success = success;
            this.reply = reply;
            this.error = error;
        }

        public static Result ok(String reply) {
            return new Result(true, reply, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }

    /**
     * Completes a prompt.
     *
     * @param systemInstruction the porting rules; sent as the system message
     * @param userPrompt        the mod source and API mapping; sent as the user message
     * @param userInitiated     true only when the user pressed Port for this request; a false here
     *                          must result in a refusal with no network traffic
     */
    Result complete(String systemInstruction, String userPrompt, boolean userInitiated);

    /** True when an endpoint and API key are configured; a client that is not configured refuses. */
    boolean isConfigured();
}
