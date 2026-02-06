package com.janiel.hytale.travel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort helper to overwrite a player's persisted JSON before the player fully loads.
 *
 * The official manual only guarantees that player save data lives under the `universe/` directory,
 * but the exact player file name can vary across patchlines.
 *
 * Strategy:
 *  - Try a handful of common candidate paths.
 *  - If none exist, create a default under universe/players/<uuid>.json.
 */
public final class PlayerStateFiles {

    private PlayerStateFiles() {
    }

    public static List<Path> candidatePaths(Path universeDir, String playerUuid) {
        List<Path> out = new ArrayList<>();
        out.add(universeDir.resolve("players").resolve(playerUuid + ".json"));
        out.add(universeDir.resolve("players").resolve(playerUuid).resolve("player.json"));
        out.add(universeDir.resolve("playerdata").resolve(playerUuid + ".json"));
        out.add(universeDir.resolve("playerdata").resolve(playerUuid).resolve("player.json"));
        out.add(universeDir.resolve("players").resolve(playerUuid).resolve("data.json"));
        return out;
    }

    public static Path writeSnapshotJson(Path universeDir, String playerUuid, String snapshotJson) throws Exception {
        if (universeDir == null) {
            universeDir = Path.of("universe");
        }

        List<Path> candidates = candidatePaths(universeDir, playerUuid);
        Path target = null;
        for (Path p : candidates) {
            if (Files.exists(p)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            target = candidates.get(0);
        }

        Files.createDirectories(target.getParent());

        if (Files.exists(target)) {
            String ts = String.valueOf(Instant.now().toEpochMilli());
            Path backup = target.resolveSibling(target.getFileName().toString() + ".bak_" + ts);
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.writeString(target, snapshotJson == null ? "{}" : snapshotJson, StandardCharsets.UTF_8);
        return target;
    }

    public static String readSnapshotJson(Path universeDir, String playerUuid) throws Exception {
        if (universeDir == null) {
            universeDir = Path.of("universe");
        }

        for (Path p : candidatePaths(universeDir, playerUuid)) {
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        }

        // If nothing exists yet, return a minimal object.
        return "{}";
    }

    /**
     * Writes the snapshot but only overwrites Components.Player.Inventory in the existing player json.
     * Everything else (position, stats, per-world state, etc.) remains as-is on the destination server.
     */
    public static Path writeInventoryOnlySnapshot(Path universeDir, String playerUuid, String snapshotJson) throws Exception {
        if (universeDir == null) {
            universeDir = Path.of("universe");
        }

        // Determine target path similar to writeSnapshotJson:
        List<Path> candidates = candidatePaths(universeDir, playerUuid);
        Path target = null;
        for (Path p : candidates) {
            if (Files.exists(p)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            target = candidates.get(0);
        }

        Files.createDirectories(target.getParent());

        String baseJson = "{}";
        if (Files.exists(target)) {
            baseJson = Files.readString(target, StandardCharsets.UTF_8);
        }

        String merged = mergeInventoryOnly(baseJson, snapshotJson);

        // backup
        if (Files.exists(target)) {
            String ts = String.valueOf(java.time.Instant.now().toEpochMilli());
            Path backup = target.resolveSibling(target.getFileName().toString() + ".bak_" + ts);
            Files.copy(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Files.writeString(target, merged, StandardCharsets.UTF_8);
        return target;
    }

    /**
     * Merge snapshot inventory into base JSON at path: Components -> Player -> Inventory
     */
    private static String mergeInventoryOnly(String baseJson, String snapshotJson) {
        if (baseJson == null || baseJson.isBlank()) baseJson = "{}";
        if (snapshotJson == null || snapshotJson.isBlank()) return baseJson;

        Range invIncoming = findValueRangeAtPath(snapshotJson, "Components", "Player", "Inventory");
        if (invIncoming == null) return baseJson;

        String invValue = snapshotJson.substring(invIncoming.start, invIncoming.end);

        Range invBase = findValueRangeAtPath(baseJson, "Components", "Player", "Inventory");
        if (invBase == null) {
            // If base doesn't have the path, do nothing (safer than overwriting whole file)
            return baseJson;
        }

        return baseJson.substring(0, invBase.start) + invValue + baseJson.substring(invBase.end);
    }

    private static final class Range {
        final int start;
        final int end;
        Range(int start, int end) { this.start = start; this.end = end; }
    }

    private static Range findValueRangeAtPath(String json, String k1, String k2, String k3) {
        Range r1 = findObjectFieldValueRange(json, 0, json.length(), k1);
        if (r1 == null) return null;

        Range r2 = findObjectFieldValueRange(json, r1.start, r1.end, k2);
        if (r2 == null) return null;

        return findObjectFieldValueRange(json, r2.start, r2.end, k3);
    }

    private static Range findObjectFieldValueRange(String json, int objStart, int objEnd, String key) {
        // Ensure we're scanning inside an object; if the range includes full json, locate the top object.
        int start = objStart;
        start = skipWs(json, start);
        if (start < objEnd && json.charAt(start) != '{') {
            // Try to find the first '{' within range
            int brace = json.indexOf('{', start);
            if (brace < 0 || brace >= objEnd) return null;
            start = brace;
        }

        int end = objEnd;
        if (end > start && json.charAt(start) == '{') {
            int match = matchBrace(json, start, '{', '}');
            if (match > 0) {
                end = Math.min(end, match + 1);
            }
        }

        int keyPos = findKeyInObject(json, start, end, key);
        if (keyPos < 0) return null;

        int colon = json.indexOf(':', keyPos);
        if (colon < 0 || colon >= end) return null;

        int valStart = skipWs(json, colon + 1);
        if (valStart >= end) return null;

        int valEnd = findJsonValueEnd(json, valStart, end);
        if (valEnd < 0) return null;

        return new Range(valStart, valEnd);
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
            i++;
        }
        return i;
    }

    private static int findKeyInObject(String json, int start, int end, String key) {
        // Scan for "key" tokens, but only at object depth 1 relative to 'start'
        int i = start;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;

        while (i < end) {
            char c = json.charAt(i);

            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                i++;
                continue;
            }

            if (c == '"') {
                // potential key string
                int strStart = i + 1;
                int strEnd = findStringEnd(json, strStart, end);
                if (strEnd < 0) return -1;

                String token = json.substring(strStart, strEnd);
                i = strEnd + 1;

                // After a key, we expect optional ws and ':' at depth==1
                int j = skipWs(json, i);
                if (depth == 1 && j < end && json.charAt(j) == ':' && token.equals(key)) {
                    return (strStart - 1); // position of opening quote
                }
                continue;
            }

            if (c == '{') depth++;
            else if (c == '}') depth--;

            i++;
        }

        return -1;
    }

    private static int findStringEnd(String s, int start, int end) {
        boolean esc = false;
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            if (esc) esc = false;
            else if (c == '\\') esc = true;
            else if (c == '"') return i;
            i++;
        }
        return -1;
    }

    private static int findJsonValueEnd(String json, int valStart, int limit) {
        char c = json.charAt(valStart);
        if (c == '{') {
            int m = matchBrace(json, valStart, '{', '}');
            return (m < 0 || m + 1 > limit) ? -1 : m + 1;
        }
        if (c == '[') {
            int m = matchBrace(json, valStart, '[', ']');
            return (m < 0 || m + 1 > limit) ? -1 : m + 1;
        }
        if (c == '"') {
            int end = findStringEnd(json, valStart + 1, limit);
            return end < 0 ? -1 : end + 1;
        }

        // number / true / false / null
        int i = valStart;
        while (i < limit) {
            char ch = json.charAt(i);
            if (ch == ',' || ch == '}' || ch == ']' || ch == '\n' || ch == '\r') break;
            i++;
        }
        return i;
    }

    private static int matchBrace(String s, int start, char open, char close) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }

            if (c == '"') {
                inStr = true;
                continue;
            }

            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }




    // ================= Engine write probe marker helpers =================

    /**
     * Marker key used by EngineWriteProbe. The engine is expected to overwrite the file without this key,
     * so when the key disappears we know a real engine write happened.
     */
    public static final String ENGINE_WRITE_PROBE_KEY = "__hytaleTravelWriteProbe";

    /**
     * Best-effort: resolve the current player JSON path (first existing candidate, otherwise the default candidate).
     * This does NOT create backups.
     */
    public static Path resolvePlayerFilePath(Path universeDir, String playerUuid) throws Exception {
        if (universeDir == null) {
            universeDir = Path.of("universe");
        }

        List<Path> candidates = candidatePaths(universeDir, playerUuid);
        for (Path p : candidates) {
            if (Files.exists(p)) {
                return p;
            }
        }

        // Default candidate if nothing exists yet.
        Path target = candidates.get(0);
        Files.createDirectories(target.getParent());
        return target;
    }

    /**
     * Writes snapshot JSON without creating a backup. Intended for probe/marker writes to avoid spamming backups.
     */
    public static Path writeSnapshotJsonNoBackup(Path universeDir, String playerUuid, String snapshotJson) throws Exception {
        Path target = resolvePlayerFilePath(universeDir, playerUuid);
        Files.createDirectories(target.getParent());
        Files.writeString(target, snapshotJson == null ? "{}" : snapshotJson, StandardCharsets.UTF_8);
        return target;
    }

    /**
     * Returns true if the top-level object contains the given key. This scans only at object depth 1.
     */
    public static boolean topLevelHasKey(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return false;
        }
        Range r = findObjectFieldValueRange(json, 0, json.length(), key);
        return r != null;
    }

    /**
     * Upserts a top-level string field. If the key exists, its JSON value is replaced.
     * If it does not exist, it is inserted as the first field in the root object.
     *
     * Note: This is a best-effort string-based manipulation to avoid depending on a JSON library.
     */
    public static String upsertTopLevelStringField(String json, String key, String value) {
        if (key == null || key.isBlank()) {
            return json == null ? "{}" : json;
        }
        if (json == null || json.isBlank()) {
            json = "{}";
        }

        String quoted = quoteJsonString(value == null ? "" : value);

        Range r = findObjectFieldValueRange(json, 0, json.length(), key);
        if (r != null) {
            // Replace value range.
            return json.substring(0, r.start) + quoted + json.substring(r.end);
        }

        // Insert new field into root object.
        int i = skipWs(json, 0);
        if (i >= json.length() || json.charAt(i) != '{') {
            // Not an object; fall back to a minimal object.
            return "{\"" + key + "\":" + quoted + "}";
        }

        int afterBrace = i + 1;
        int j = skipWs(json, afterBrace);

        // If object is empty: "{   }"
        if (j < json.length() && json.charAt(j) == '}') {
            return json.substring(0, afterBrace) + "\"" + key + "\":" + quoted + json.substring(j);
        }

        // Non-empty object: insert "key":value, right after '{'
        return json.substring(0, afterBrace) + "\"" + key + "\":" + quoted + "," + json.substring(afterBrace);
    }

    private static String quoteJsonString(String s) {
        StringBuilder out = new StringBuilder();
        out.append('\"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '\"') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                out.append(c);
            }
        }
        out.append('\"');
        return out.toString();
    }

}
