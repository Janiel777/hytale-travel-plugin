package com.janiel.hytale.travel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class BackendClient {

    private final HttpClient http;
    private final String baseUrl;
    private final int timeoutMs;

    public BackendClient(String baseUrl, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String prepare(String playerUuid, String fromServer, String toServer, String snapshotJson)
            throws IOException, InterruptedException {

        String body = "{"
                + "\"player_uuid\":\"" + jsonEscape(playerUuid) + "\","
                + "\"from_server\":\"" + jsonEscape(fromServer) + "\","
                + "\"to_server\":\"" + jsonEscape(toServer) + "\","
                + "\"snapshot_json\":\"" + jsonEscape(snapshotJson) + "\""
                + "}";

        String url = baseUrl + "/transfer/prepare";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        System.out.println("[HytaleTravel] POST " + url + " body=" + body);

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("[HytaleTravel] prepare response status=" + res.statusCode() + " body=" + res.body());

        if (res.statusCode() != 200) {
            throw new IOException("prepare failed: status=" + res.statusCode() + " body=" + res.body());
        }

        return extractJsonString(res.body(), "ticket_id");
    }

    public String claimLatest(String playerUuid, String toServer)
            throws IOException, InterruptedException {

        String body = "{"
                + "\"player_uuid\":\"" + jsonEscape(playerUuid) + "\","
                + "\"to_server\":\"" + jsonEscape(toServer) + "\""
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/transfer/claim-latest"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("claim-latest failed: status=" + res.statusCode() + " body=" + res.body());
        }

        return extractJsonString(res.body(), "snapshot_json");
    }

    public String claim(String ticketId, String playerUuid, String toServer)
            throws IOException, InterruptedException {

        String body = "{"
                + "\"ticket_id\":\"" + jsonEscape(ticketId) + "\","
                + "\"player_uuid\":\"" + jsonEscape(playerUuid) + "\","
                + "\"to_server\":\"" + jsonEscape(toServer) + "\""
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/transfer/claim"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("claim failed: status=" + res.statusCode() + " body=" + res.body());
        }

        return extractJsonString(res.body(), "snapshot_json");
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonString(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IOException("missing key in json: " + key + " json=" + json);
        }
        start += pattern.length();

        int end = start;
        boolean escape = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (!escape && c == '"') break;
            if (!escape && c == '\\') {
                escape = true;
            } else {
                escape = false;
            }
            end++;
        }
        if (end >= json.length()) {
            throw new IOException("unterminated json string for key: " + key);
        }

        String raw = json.substring(start, end);
        return raw.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
