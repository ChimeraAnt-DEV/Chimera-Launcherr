package org.chimeramc.client.core.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The zero-setup cross-client relay, on public infrastructure no one has to run.
 *
 * <p><b>Why not the Go voice relay.</b> That relay reaches a whole session and reconnects, but it
 * must be deployed and maintained by someone, and a cosmetic must be visible between two Glowberry
 * users <em>anywhere</em> without either of them standing up a server. This client instead speaks
 * to <a href="https://ntfy.sh">ntfy.sh</a>, a free public pub/sub service that needs no account,
 * no key and no deployment: a topic is created by the first publish. That is the "infrastructure
 * that already exists" the feature needs.
 *
 * <p><b>HTTP-only, so no new dependency.</b> ntfy exposes plain {@code POST /{topic}} to publish
 * and {@code GET /{topic}/json?poll=1&amp;since=…} to read; both are ordinary
 * {@link HttpURLConnection} calls, so this works on the launcher's existing network stack with
 * nothing added to the build.
 *
 * <p><b>The topic is the rendezvous.</b> Two clients agree on a topic by naming the same world or
 * the same private code, exactly like the voice channel: the topic <em>is</em> the shared secret,
 * so no key exchange or server is needed. A topic derived from a Bedrock world key is reachable by
 * anyone who knows that world, which is the intended scope (the same people you are already
 * playing with); a private code is unguessable.
 *
 * <p>Pure of Android types so the topic rule and the request shapes are unit-testable.
 */
public final class CosmeticSyncCloudRelay {

    /** The public relay. Overridable so a self-hosted ntfy can be pointed at later. */
    public static final String DEFAULT_BASE_URL = "https://ntfy.sh";

    private static final int POLL_TIMEOUT_MS = 25000;
    private static final int PUBLISH_TIMEOUT_MS = 8000;

    private final String baseUrl;

    public CosmeticSyncCloudRelay() {
        this(DEFAULT_BASE_URL);
    }

    public CosmeticSyncCloudRelay(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL : baseUrl.trim();
    }

    /**
     * The topic two clients meet on. A world-derived name is sanitised to ntfy's allowed set and
     * prefixed, so a cosmetic topic can never collide with someone else's ntfy use of the same
     * word.
     */
    public static String topicForWorld(String worldKey) {
        String key = worldKey == null ? "" : worldKey.trim().toLowerCase();
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < key.length() && safe.length() < 48; i++) {
            char c = key.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                safe.append(c);
            } else if (c == ' ' || c == '_' || c == ':' || c == '/') {
                safe.append('-');
            }
        }
        return "glowberry-cosmetics-" + (safe.length() == 0 ? "world" : safe);
    }

    /**
     * Publishes one advertisement. {@code payloadBase64} is the raw {@link CosmeticSyncProtocol}
     * bytes, base64'd by the caller so they survive a text transport.
     *
     * @return true when the relay accepted it
     */
    public boolean publish(String topic, String payloadBase64) {
        if (topic == null || topic.isEmpty() || payloadBase64 == null) return false;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "/" + URLEncoder.encode(topic, "UTF-8"));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(PUBLISH_TIMEOUT_MS);
            connection.setReadTimeout(PUBLISH_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "text/plain");
            byte[] body = payloadBase64.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** One polled message: the base64 payload. */
    public static final class Message {
        public final String payloadBase64;

        Message(String payloadBase64) {
            this.payloadBase64 = payloadBase64;
        }
    }

    /**
     * Reads messages published since {@code sinceEpochSeconds}.
     *
     * <p>The body is one JSON object per line; this pulls each {@code "message"} value without a
     * JSON dependency (the field is a plain base64 string here, so the escape rules are trivial).
     * A malformed line is skipped rather than failing the whole poll.
     *
     * @return the new messages, or an empty list on any failure
     */
    public java.util.List<Message> poll(String topic, long sinceEpochSeconds) {
        java.util.List<Message> out = new java.util.ArrayList<>();
        if (topic == null || topic.isEmpty()) return out;
        HttpURLConnection connection = null;
        try {
            String query = "/" + URLEncoder.encode(topic, "UTF-8")
                    + "/json?poll=1&since=" + Math.max(0, sinceEpochSeconds);
            URL url = new URL(baseUrl + query);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(POLL_TIMEOUT_MS);
            connection.setReadTimeout(POLL_TIMEOUT_MS);
            if (connection.getResponseCode() != 200) return out;
            String body = readAll(connection.getInputStream());
            for (String line : body.split("\n")) {
                String message = jsonStringField(line, "message");
                if (message == null || message.isEmpty()) continue;
                out.add(new Message(message));
            }
        } catch (IOException | RuntimeException e) {
            return out;
        } finally {
            if (connection != null) connection.disconnect();
        }
        return out;
    }

    /** Extracts a top-level string field's value, or null. Handles the escapes ntfy emits. */
    static String jsonStringField(String json, String field) {
        if (json == null) return null;
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        break;
                    default: sb.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
