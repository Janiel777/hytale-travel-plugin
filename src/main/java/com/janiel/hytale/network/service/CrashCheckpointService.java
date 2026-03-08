package com.janiel.hytale.network.service;

import com.hypixel.hytale.logger.HytaleLogger;
import com.janiel.hytale.core.config.TravelConfig;
import com.janiel.hytale.network.backend.BackendClient;
import com.janiel.hytale.network.persistence.FinalPersistGate;
import com.janiel.hytale.network.persistence.InventorySessionStore;
import com.janiel.hytale.network.persistence.PlayerStateFiles;
import com.janiel.hytale.mutations.persistence.MutationsRepository;
import java.util.UUID;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Crash-safety checkpoint:
 * - Fixed interval (default 2 minutes)
 * - Only if session active
 * - Only if JSON changed since last checkpoint
 * - NEVER releases the session
 * - Updates expectedVersion after successful save
 *
 * Intentionally isolated from FinalPersistGate.
 */
public final class CrashCheckpointService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final long INTERVAL_MS = 120_000L;

    private static ScheduledExecutorService scheduler;

    // playerUuid -> lastSavedHash
    private static final Map<String, String> LAST_SAVED_HASH = new ConcurrentHashMap<>();

    private CrashCheckpointService() {
    }

    public static void start(TravelConfig cfg, BackendClient backend) {
        if (scheduler != null) return;

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "HytaleTravel-CrashCheckpoint");
            t.setDaemon(true);
            return t;
        };

        scheduler = Executors.newSingleThreadScheduledExecutor(tf);

        scheduler.scheduleAtFixedRate(
                () -> tick(cfg, backend),
                INTERVAL_MS,
                INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("CrashCheckpointService started intervalMs=" + INTERVAL_MS);
    }

    public static void stop() {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        scheduler = null;
        LAST_SAVED_HASH.clear();
    }

    private static void tick(TravelConfig cfg, BackendClient backend) {
        List<InventorySessionStore.Session> sessions = InventorySessionStore.snapshot();
        for (InventorySessionStore.Session session : sessions) {
            try {
                checkpointOne(cfg, backend, session);
            } catch (Exception e) {
                LOGGER.atWarning().log("CrashCheckpoint failed player=" + session.playerUuid + " err=" + e);
            }
        }
    }

    private static void checkpointOne(TravelConfig cfg, BackendClient backend, InventorySessionStore.Session session) throws Exception {
        if (cfg == null || backend == null || session == null) return;

        // If a disconnect gate is armed for this player, do NOT checkpoint.
        // FinalPersistGate must be the only saver in that window (after first engine write post-disconnect).
        if (FinalPersistGate.hasPendingDisconnect(session.playerUuid)) {
            return;
        }

        String snapshotJson = PlayerStateFiles.readSnapshotJson(cfg.getUniverseDir(), session.playerUuid);

        String mutationsJson = "{}";
        try {
            mutationsJson = MutationsRepository.readRawJson(UUID.fromString(session.playerUuid));
        } catch (Exception ignore) {
        }

        String combined = (snapshotJson == null ? "" : snapshotJson) + "\n---\n" + (mutationsJson == null ? "" : mutationsJson);
        String hash = sha256Hex(combined);

        String last = LAST_SAVED_HASH.get(session.playerUuid);
        if (hash.equals(last)) {
            return; // no change since last checkpoint
        }

//        BackendClient.InventorySaveResult saveRes = backend.inventorySave(
//                session.playerUuid,
//                session.serverId,
//                session.expectedVersion,
//                snapshotJson
//        );

        BackendClient.ProfileSaveResult saveRes = backend.profileSave(
                session.playerUuid,
                session.serverId,
                session.expectedVersion,
                snapshotJson,
                snapshotJson,
                mutationsJson
        );

        session.expectedVersion = saveRes.newVersion;
        LAST_SAVED_HASH.put(session.playerUuid, hash);

        LOGGER.atInfo().log("CrashCheckpoint saved player=" + session.playerUuid + " newVersion=" + saveRes.newVersion);
    }

    private static String sha256Hex(String s) throws Exception {
        if (s == null) s = "";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
