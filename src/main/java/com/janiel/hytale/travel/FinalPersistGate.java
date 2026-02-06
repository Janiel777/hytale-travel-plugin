package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal gate: remember a disconnect time, then when an engine JSON write is observed
 * (ENGINE_WRITE_DETECTED), log the delta and clear the pending entry.
 *
 * For now this only logs. Later we can put: read JSON -> save to backend -> release lock.
 */
public final class FinalPersistGate {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<String, Long> PENDING_DISCONNECT_AT_MS = new ConcurrentHashMap<>();

    private FinalPersistGate() {
    }

    public static void markDisconnect(String playerUuid, long disconnectAtMs) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        PENDING_DISCONNECT_AT_MS.put(playerUuid, disconnectAtMs);
        LOGGER.atInfo().log("FINAL_PERSIST_GATE_ARMED playerUuid=" + playerUuid
                + " disconnectAt=" + Instant.ofEpochMilli(disconnectAtMs));
    }

    public static void onEngineWrite(String playerUuid, long engineWriteAtMs) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }

        Long disconnectAtMs = PENDING_DISCONNECT_AT_MS.remove(playerUuid);
        if (disconnectAtMs == null) {
            return; // no pending disconnect
        }

        long deltaMs = engineWriteAtMs - disconnectAtMs;

        LOGGER.atInfo().log("FINAL_PERSIST_AFTER_DISCONNECT playerUuid=" + playerUuid
                + " disconnectAt=" + Instant.ofEpochMilli(disconnectAtMs)
                + " engineWriteAt=" + Instant.ofEpochMilli(engineWriteAtMs)
                + " deltaMs=" + deltaMs);
    }

    public static boolean hasPendingDisconnect(String playerUuid) {
        return PENDING_DISCONNECT_AT_MS.containsKey(playerUuid);
    }
}
