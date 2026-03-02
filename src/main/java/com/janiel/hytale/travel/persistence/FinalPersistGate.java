package com.janiel.hytale.travel.persistence;

import com.hypixel.hytale.logger.HytaleLogger;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.net.BackendClient;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import java.util.UUID;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gate: remember a disconnect time, then when an engine JSON write is observed
 * (ENGINE_WRITE_DETECTED), treat that write as the "final persist" for this disconnect.
 *
 * After the final persist is observed, we:
 *  - read the persisted player JSON from disk
 *  - inventorySave(expectedVersion) to backend
 *  - inventorySessionRelease
 *
 * This avoids saving stale snapshots before the engine actually writes the final JSON.
 */
public final class FinalPersistGate {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<String, PendingDisconnect> PENDING = new ConcurrentHashMap<>();

    private static final class PendingDisconnect {
        public final long disconnectAtMs;
        public final String connectionKey;

        private PendingDisconnect(long disconnectAtMs, String connectionKey) {
            this.disconnectAtMs = disconnectAtMs;
            this.connectionKey = connectionKey;
        }
    }

    private static volatile TravelConfig cfg;
    private static volatile BackendClient backend;

    private FinalPersistGate() {
    }

    public static void initialize(TravelConfig cfg, BackendClient backend) {
        FinalPersistGate.cfg = cfg;
        FinalPersistGate.backend = backend;
        LOGGER.atInfo().log("FinalPersistGate initialized. backendBaseUrl=" + (cfg == null ? "<null>" : cfg.getBackendBaseUrl()));
    }

    public static void markDisconnect(String playerUuid, long disconnectAtMs, String connectionKey) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        PENDING.put(playerUuid, new PendingDisconnect(disconnectAtMs, connectionKey));
        LOGGER.atInfo().log("FINAL_PERSIST_GATE_ARMED playerUuid=" + playerUuid
                + " disconnectAt=" + Instant.ofEpochMilli(disconnectAtMs)
                + " connectionKey=" + (connectionKey == null ? "<none>" : connectionKey));
    }

    public static void onEngineWrite(String playerUuid, long engineWriteAtMs) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }

        PendingDisconnect pd = PENDING.remove(playerUuid);
        if (pd == null) {
            return;
        }
        long disconnectAtMs = pd.disconnectAtMs;
        long deltaMs = engineWriteAtMs - disconnectAtMs;

        LOGGER.atInfo().log("FINAL_PERSIST_AFTER_DISCONNECT playerUuid=" + playerUuid
                + " disconnectAt=" + Instant.ofEpochMilli(disconnectAtMs)
                + " engineWriteAt=" + Instant.ofEpochMilli(engineWriteAtMs)
                + " deltaMs=" + deltaMs);

        // If we don't have an active inventory session, we cannot safely save/release.
        InventorySessionStore.Session session = InventorySessionStore.get(playerUuid);
        if (session == null) {
            LOGGER.atWarning().log("FINAL_PERSIST_NO_SESSION playerUuid=" + playerUuid + " (skipping save/release)");
            return;
        }

        String expectedKey = pd.connectionKey;
        String sessionKey = session.connectionKey;

        if (expectedKey != null && sessionKey != null && !expectedKey.equals(sessionKey)) {
            LOGGER.atWarning().log("FINAL_PERSIST_STALE_DISCONNECT playerUuid=" + playerUuid
                    + " pendingConnectionKey=" + expectedKey
                    + " currentSessionConnectionKey=" + sessionKey
                    + " (skipping save/release)");
            return;
        }

        TravelConfig c = cfg;
        BackendClient b = backend;
        if (c == null || b == null) {
            LOGGER.atWarning().log("FINAL_PERSIST_NOT_INITIALIZED playerUuid=" + playerUuid + " (skipping save/release)");
            return;
        }

        String snapshotJson;
        try {
            snapshotJson = PlayerStateFiles.readSnapshotJson(c.getUniverseDir(), playerUuid);
        } catch (Exception ex) {
            LOGGER.atWarning().log("FINAL_PERSIST_READ_FAILED playerUuid=" + playerUuid + " error=" + ex);
            return;
        }

        String mutationsJson = "{}";
        try {
            mutationsJson = MutationsRepository.readRawJson(UUID.fromString(playerUuid));
        } catch (Exception ignore) {
        }

        long saveStartMs = System.currentTimeMillis();

        try {
//            BackendClient.InventorySaveResult saveRes = b.inventorySave(
//                    playerUuid,
//                    session.serverId,
//                    session.expectedVersion,
//                    snapshotJson
//            );
            BackendClient.ProfileSaveResult saveRes = b.profileSave(
                    playerUuid,
                    session.serverId,
                    session.expectedVersion,
                    snapshotJson,     // state_json
                    snapshotJson,     // inventory_json (por ahora mismo snapshot)
                    mutationsJson     // mutations_json
            );

            long saveTookMs = System.currentTimeMillis() - saveStartMs;

            int newVersion = saveRes.newVersion;
            session.expectedVersion = newVersion;

            LOGGER.atInfo().log("FINAL_PERSIST_SAVE_OK playerUuid=" + playerUuid
                    + " serverId=" + session.serverId
                    + " expectedVersion=" + (newVersion - 1)
                    + " newVersion=" + newVersion
                    + " jsonLen=" + (snapshotJson == null ? 0 : snapshotJson.length())
                    + " tookMs=" + saveTookMs);

            try {
//                BackendClient.InventoryReleaseResult rel = b.inventorySessionRelease(playerUuid, session.serverId);
                BackendClient.ProfileReleaseResult rel = b.profileSessionRelease(playerUuid, session.serverId);

                LOGGER.atInfo().log("FINAL_PERSIST_RELEASE_DONE playerUuid=" + playerUuid
                        + " serverId=" + session.serverId
                        + " released=" + rel.released
                        + " status=" + rel.status);

                if (rel.released) {
                    InventorySessionStore.Session current = InventorySessionStore.get(playerUuid);
                    if (current != null && current == session) {
                        InventorySessionStore.remove(playerUuid);
                    }
                }
            } catch (Exception rex) {
                // If release fails, keep session in memory; lock will still expire by TTL.
                LOGGER.atWarning().log("FINAL_PERSIST_RELEASE_FAILED playerUuid=" + playerUuid
                        + " serverId=" + session.serverId
                        + " error=" + rex);
            }

        } catch (Exception ex) {
            long saveTookMs = System.currentTimeMillis() - saveStartMs;

            // Do NOT release on save failure. Let TTL protect against stale loads.
            LOGGER.atWarning().log("FINAL_PERSIST_SAVE_FAILED playerUuid=" + playerUuid
                    + " serverId=" + session.serverId
                    + " expectedVersion=" + session.expectedVersion
                    + " tookMs=" + saveTookMs
                    + " error=" + ex);
        }

        // Stop probe at the end (after save/release attempts) to avoid interrupting the save flow.
        try {
            boolean stopped = EngineWriteProbeRegistry.stopProbe(playerUuid);
            LOGGER.atInfo().log("ENGINE_WRITE_PROBE_AUTO_STOP playerUuid=" + playerUuid + " stopped=" + stopped);
        } catch (Exception e) {
            LOGGER.atWarning().log("ENGINE_WRITE_PROBE_AUTO_STOP_FAILED playerUuid=" + playerUuid + " error=" + e);
        }

    }

    public static boolean hasPendingDisconnect(String playerUuid) {
        return PENDING.containsKey(playerUuid);
    }
}
