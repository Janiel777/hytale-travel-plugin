package com.janiel.hytale.travel.net;

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

    public static final class InventorySessionAcquireResult {
        public final String inventoryJson;
        public final int version;
        public final String lockExpiresAtIso;

        public InventorySessionAcquireResult(String inventoryJson, int version, String lockExpiresAtIso) {
            this.inventoryJson = inventoryJson;
            this.version = version;
            this.lockExpiresAtIso = lockExpiresAtIso;
        }
    }

    public static final class InventorySaveResult {
        public final int newVersion;

        public InventorySaveResult(int newVersion) {
            this.newVersion = newVersion;
        }
    }

    public static final class InventoryReleaseResult {
        public final boolean released;
        public final String status;

        public InventoryReleaseResult(boolean released, String status) {
            this.released = released;
            this.status = status;
        }
    }

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

    public InventorySessionAcquireResult inventorySessionAcquire(String playerUuid, String serverId)
            throws IOException, InterruptedException {

        String body = "{"
                + "\"player_uuid\":\"" + jsonEscape(playerUuid) + "\","
                + "\"server_id\":\"" + jsonEscape(serverId) + "\""
                + "}";

        String url = baseUrl + "/inventory/session/acquire";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new IOException("inventory session acquire failed: status=" + res.statusCode() + " body=" + res.body());
        }

        String invJson = extractJsonString(res.body(), "inventory_json");
        int version = (int) extractJsonLong(res.body(), "version");
        String lockExp = extractJsonString(res.body(), "lock_expires_at");
        return new InventorySessionAcquireResult(invJson, version, lockExp);
    }

    public InventorySaveResult inventorySave(String playerUuid, String serverId, int expectedVersion, String inventoryJson)
            throws IOException, InterruptedException {

        String body = "{"
                + "\"player_uuid\":\"" + jsonEscape(playerUuid) + "\","
                + "\"server_id\":\"" + jsonEscape(serverId) + "\","
                + "\"expected_version\":" + expectedVersion + ","
                + "\"inventory_json\":\"" + jsonEscape(inventoryJson) + "\""
                + "}";

        String url = baseUrl + "/inventory/save";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new IOException("inventory save failed: status=" + res.statusCode() + " body=" + res.body());
        }

        int newVersion = (int) extractJsonLong(res.body(), "new_version");
        return new InventorySaveResult(newVersion);
    }

    public InventoryReleaseResult inventorySessionRelease(String playerUuid, String serverId)
            throws IOException, InterruptedException {

        String body = "{"
                + "\"player_uuid\":\"" + jsonEscape(playerUuid) + "\","
                + "\"server_id\":\"" + jsonEscape(serverId) + "\""
                + "}";

        String url = baseUrl + "/inventory/session/release";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new IOException("inventory session release failed: status=" + res.statusCode() + " body=" + res.body());
        }

        boolean released = extractJsonBoolean(res.body(), "released");
        String status = extractJsonString(res.body(), "status");
        return new InventoryReleaseResult(released, status);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static long extractJsonLong(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IOException("missing key in json: " + key + " json=" + json);
        }
        start += pattern.length();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
            start++;
        }
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c < '0' || c > '9') && c != '-') break;
            end++;
        }
        if (end == start) {
            throw new IOException("expected number for key: " + key + " json=" + json);
        }
        return Long.parseLong(json.substring(start, end));
    }

    private static boolean extractJsonBoolean(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            throw new IOException("missing key in json: " + key + " json=" + json);
        }
        start += pattern.length();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
            start++;
        }
        if (json.startsWith("true", start)) return true;
        if (json.startsWith("false", start)) return false;
        throw new IOException("expected boolean for key: " + key + " json=" + json);
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
