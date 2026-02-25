package com.janiel.hytale.travel.persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the active inventory session (lock + expected version) for each player on this server.
 *
 * This is intentionally minimal: it only holds what the backend requires to save/release safely.
 */
public final class InventorySessionStore {

    public static final class Session {
        public final String playerUuid;
        public final String serverId;
        public final String connectionKey;
        public volatile int expectedVersion;
        public volatile long lockExpiresAtMs;
        public final long acquiredAtMs;

        public Session(String playerUuid, String serverId, String connectionKey, int expectedVersion, long lockExpiresAtMs, long acquiredAtMs) {
            this.playerUuid = playerUuid;
            this.serverId = serverId;
            this.connectionKey = connectionKey;
            this.expectedVersion = expectedVersion;
            this.lockExpiresAtMs = lockExpiresAtMs;
            this.acquiredAtMs = acquiredAtMs;
        }
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private InventorySessionStore() {
    }

    public static void put(Session s) {
        if (s == null || s.playerUuid == null || s.playerUuid.isBlank()) return;
        SESSIONS.put(s.playerUuid, s);
    }

    public static Session get(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return null;
        return SESSIONS.get(playerUuid);
    }

    public static Session remove(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return null;
        return SESSIONS.remove(playerUuid);
    }

    /**
     * Snapshot helper for safe iteration (avoids concurrent modification risks).
     */
    public static java.util.List<Session> snapshot() {
        return new java.util.ArrayList<>(SESSIONS.values());
    }
}
