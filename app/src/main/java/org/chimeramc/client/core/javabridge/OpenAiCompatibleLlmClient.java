package org.chimeramc.client.core.javabridge;

import android.content.Context;

import org.chimeramc.client.settings.LowLatencyNetworkManager;
import org.chimeramc.client.settings.ThermalGovernor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * An OpenAI-compatible chat-completions client, gated by {@link LlmConsent}.
 *
 * <p>The request shape is deliberately the common denominator ({@code model}, {@code messages}) so
 * a user can point the endpoint at OpenAI, a local server, or any compatible gateway without a
 * code change. The client is the last line of defence for consent: it refuses when the user has not
 * recorded consent or did not initiate this specific request, so no code path can send mod source
 * without an explicit user action.
 *
 * <p>The socket configuration mirrors the launcher's other HTTP clients, so the low-latency and
 * game-session quiet-zone behaviour applies here too: a port is speculative work and is refused
 * while a game session is running.
 */
public final class OpenAiCompatibleLlmClient implements LlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int CONNECT_TIMEOUT_S = 30;
    private static final int READ_TIMEOUT_S = 120;

    private final Context context;

    public OpenAiCompatibleLlmClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isConfigured() {
        return LlmSettings.isConfigured(context);
    }

    @Override
    public Result complete(String systemInstruction, String userPrompt, boolean userInitiated) {
        if (!userInitiated) {
            return Result.fail("a port must be started by the user");
        }
        if (!LlmSettings.hasConsent(context)) {
            return Result.fail("consent to send mod source to the LLM has not been given");
        }
        if (!isConfigured()) {
            return Result.fail("no LLM API key is configured");
        }
        if (LowLatencyNetworkManager.isGameSessionActive()) {
            return Result.fail("a game session is running; porting is paused to protect it");
        }
        if (ThermalGovernor.shouldPauseSpeculativeWork()) {
            return Result.fail("the device is too warm to run a port right now");
        }

        String body = buildRequestBody(LlmSettings.model(context), systemInstruction, userPrompt);
        Request request = new Request.Builder()
                .url(LlmSettings.endpoint(context))
                .header("Authorization", "Bearer " + LlmSettings.apiKey(context))
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = buildHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Result.fail("the LLM provider returned HTTP " + response.code());
            }
            String text = response.body() == null ? "" : response.body().string();
            String reply = extractContent(text);
            if (reply == null) {
                return Result.fail("could not read the LLM reply");
            }
            return Result.ok(reply);
        } catch (IOException e) {
            return Result.fail("could not reach the LLM provider: " + e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail("the LLM request failed: " + e.getMessage());
        }
    }

    private static OkHttpClient buildHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS);
        try {
            if (LowLatencyNetworkManager.isEnabled()) {
                builder.socketFactory(LowLatencyNetworkManager.createSocketFactory());
            }
        } catch (Throwable ignored) {
            // The low-latency toggle is an optimisation; a failure here must not block a port.
        }
        return builder.build();
    }

    /** Builds the chat-completions request body. Package-visible so the shape is testable. */
    static String buildRequestBody(String model, String systemInstruction, String userPrompt) {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("model", model == null ? LlmSettings.DEFAULT_MODEL : model);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        messages.add(message("system", systemInstruction));
        messages.add(message("user", userPrompt));
        root.add("messages", messages);
        root.addProperty("temperature", 0.2);
        return root.toString();
    }

    private static com.google.gson.JsonObject message(String role, String content) {
        com.google.gson.JsonObject message = new com.google.gson.JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content == null ? "" : content);
        return message;
    }

    /** Reads {@code choices[0].message.content} from a chat-completions reply. */
    static String extractContent(String responseJson) {
        if (responseJson == null || responseJson.trim().isEmpty()) return null;
        try {
            com.google.gson.JsonElement parsed =
                    com.google.gson.JsonParser.parseString(responseJson);
            if (!parsed.isJsonObject()) return null;
            com.google.gson.JsonElement choices = parsed.getAsJsonObject().get("choices");
            if (choices == null || !choices.isJsonArray() || choices.getAsJsonArray().size() == 0) {
                return null;
            }
            com.google.gson.JsonElement message =
                    choices.getAsJsonArray().get(0).getAsJsonObject().get("message");
            if (message == null || !message.isJsonObject()) return null;
            com.google.gson.JsonElement content = message.getAsJsonObject().get("content");
            return content == null || !content.isJsonPrimitive() ? null : content.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
