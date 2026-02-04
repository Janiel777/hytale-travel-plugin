package com.janiel.hytale.travel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Small signed payload (<= 4KB) that travels through the client during server referral.
 *
 * IMPORTANT: The client can tamper with payload bytes. Always verify signatures.
 */
public final class TravelPayload {

    // Keep field names short to stay under 4KB.
    // v: version
    // pu: player_uuid
    // ts: to_server
    // tk: ticket_id
    // n: nonce
    // s: signature (base64url)

    private final int version;
    private final String playerUuid;
    private final String toServer;
    private final String ticketId;
    private final String nonce;
    private final String signature;

    private TravelPayload(int version,
                          String playerUuid,
                          String toServer,
                          String ticketId,
                          String nonce,
                          String signature) {
        this.version = version;
        this.playerUuid = playerUuid;
        this.toServer = toServer;
        this.ticketId = ticketId;
        this.nonce = nonce;
        this.signature = signature;
    }

    public int getVersion() { return version; }
    public String getPlayerUuid() { return playerUuid; }
    public String getToServer() { return toServer; }
    public String getTicketId() { return ticketId; }
    public String getNonce() { return nonce; }
    public String getSignature() { return signature; }

    public static byte[] createSignedBytes(String playerUuid,
                                           String toServer,
                                           String ticketId,
                                           String sharedSecret,
                                           String nonce) {
        int v = 1;
        String unsignedJson = toUnsignedJson(v, playerUuid, toServer, ticketId, nonce);
        String sig = sign(unsignedJson, sharedSecret);
        String fullJson = "{" +
                "\"v\":" + v + "," +
                "\"pu\":\"" + jsonEscape(playerUuid) + "\"," +
                "\"ts\":\"" + jsonEscape(toServer) + "\"," +
                "\"tk\":\"" + jsonEscape(ticketId) + "\"," +
                "\"n\":\"" + jsonEscape(nonce) + "\"," +
                "\"s\":\"" + jsonEscape(sig) + "\"" +
                "}";
        return fullJson.getBytes(StandardCharsets.UTF_8);
    }

    public static TravelPayload parseAndVerify(byte[] payloadBytes, String sharedSecret) {
        if (payloadBytes == null || payloadBytes.length == 0) return null;

        String json = new String(payloadBytes, StandardCharsets.UTF_8);

        Integer v = tryExtractJsonInt(json, "v");
        String pu = tryExtractJsonString(json, "pu");
        String ts = tryExtractJsonString(json, "ts");
        String tk = tryExtractJsonString(json, "tk");
        String n = tryExtractJsonString(json, "n");
        String s = tryExtractJsonString(json, "s");

        if (v == null || pu == null || ts == null || tk == null || n == null || s == null) {
            return null;
        }

        String unsignedJson = toUnsignedJson(v, pu, ts, tk, n);
        String expected = sign(unsignedJson, sharedSecret);
        if (!constantTimeEquals(expected, s)) {
            return null;
        }

        return new TravelPayload(v, pu, ts, tk, n, s);
    }

    private static String toUnsignedJson(int v, String pu, String ts, String tk, String n) {
        return "{" +
                "\"v\":" + v + "," +
                "\"pu\":\"" + jsonEscape(pu) + "\"," +
                "\"ts\":\"" + jsonEscape(ts) + "\"," +
                "\"tk\":\"" + jsonEscape(tk) + "\"," +
                "\"n\":\"" + jsonEscape(n) + "\"" +
                "}";
    }

    private static String sign(String unsignedJson, String sharedSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(unsignedJson.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign travel payload", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static Integer tryExtractJsonInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        idx += pattern.length();

        int end = idx;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c < '0' || c > '9') break;
            end++;
        }
        if (end == idx) return null;
        try {
            return Integer.parseInt(json.substring(idx, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String tryExtractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();

        int end = start;
        boolean escape = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (!escape && c == '"') break;
            if (!escape && c == '\\') escape = true;
            else escape = false;
            end++;
        }
        if (end >= json.length()) return null;

        String raw = json.substring(start, end);
        return raw.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
