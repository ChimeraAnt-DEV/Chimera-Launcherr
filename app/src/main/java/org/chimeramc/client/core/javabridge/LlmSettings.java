package org.chimeramc.client.core.javabridge;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persisted LLM configuration and consent.
 *
 * <p>Kept separate from the client so the consent decision has a single home and can be reset
 * without touching the transport. The API key lives in the app's private preferences, like the
 * launcher's other credentials; it is never written into a ported mod or a report.
 */
public final class LlmSettings {

    private static final String PREFS = "javabridge_llm";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_CONSENT_VERSION = "consent_version";

    /** A sensible default endpoint; the user can point this at any OpenAI-compatible server. */
    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";

    private LlmSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String endpoint(Context context) {
        String value = prefs(context).getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
        return value == null || value.trim().isEmpty() ? DEFAULT_ENDPOINT : value.trim();
    }

    public static void setEndpoint(Context context, String endpoint) {
        prefs(context).edit().putString(KEY_ENDPOINT, endpoint == null ? "" : endpoint.trim())
                .apply();
    }

    public static String model(Context context) {
        String value = prefs(context).getString(KEY_MODEL, DEFAULT_MODEL);
        return value == null || value.trim().isEmpty() ? DEFAULT_MODEL : value.trim();
    }

    public static void setModel(Context context, String model) {
        prefs(context).edit().putString(KEY_MODEL, model == null ? "" : model.trim()).apply();
    }

    public static String apiKey(Context context) {
        String value = prefs(context).getString(KEY_API_KEY, "");
        return value == null ? "" : value;
    }

    public static void setApiKey(Context context, String apiKey) {
        prefs(context).edit().putString(KEY_API_KEY, apiKey == null ? "" : apiKey.trim()).apply();
    }

    /** True when both an endpoint and a key are present. */
    public static boolean isConfigured(Context context) {
        return !apiKey(context).isEmpty();
    }

    /** Records that the user accepted the current {@link LlmConsent#CONSENT_TEXT}. */
    public static void grantConsent(Context context) {
        prefs(context).edit().putString(KEY_CONSENT_VERSION, LlmConsent.CONSENT_VERSION).apply();
    }

    /** Withdraws consent and clears the stored key. */
    public static void revokeConsent(Context context) {
        prefs(context).edit()
                .remove(KEY_CONSENT_VERSION)
                .remove(KEY_API_KEY)
                .apply();
    }

    /** True when the user accepted the current consent text version. */
    public static boolean hasConsent(Context context) {
        return LlmConsent.isCurrentVersion(prefs(context).getString(KEY_CONSENT_VERSION, null));
    }
}
