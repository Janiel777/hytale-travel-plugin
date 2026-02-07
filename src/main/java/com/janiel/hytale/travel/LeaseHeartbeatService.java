package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the backend inventory lock (lease) alive while a player is actively playing.
 *
 * Uses ONLY existing backend endpoint: inventory/session/acquire
 * - Does NOT apply inventory_json to the player
 * - Only refreshes in-memory session fields: expectedVersion + lockExpiresAtMs
 * - Skips players with FinalPersistGate pending disconnect (gate must own the save/release window)
 */
public final class LeaseHeartbeatService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // How often we scan sessions.
    private static final long TICK_MS = 20_000L;

    // If the lease expires within this window, refresh it.
    private static final long RENEW_BEFORE_MS = 30_000L;

    private static ScheduledExecutorService scheduler;

    private LeaseHeartbeatService() {
    }

    public static void start(BackendClient backend) {
        if (scheduler != null) return;

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "HytaleTravel-LeaseHeartbeat");
            t.setDaemon(true);
            return t;
        };

        scheduler = Executors.newSingleThreadScheduledExecutor(tf);
        scheduler.scheduleAtFixedRate(() -> tick(backend), TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);

        LOGGER.atInfo().log("LeaseHeartbeatService started tickMs=" + TICK_MS + " renewBeforeMs=" + RENEW_BEFORE_MS);
    }

    public static void stop() {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        scheduler = null;
    }

    private static void tick(BackendClient backend) {
        if (backend == null) return;

        List<InventorySessionStore.Session> sessions = InventorySessionStore.snapshot();
        long now = System.currentTimeMillis();

        for (InventorySessionStore.Session s : sessions) {
            if (s == null || s.playerUuid == null || s.playerUuid.isBlank()) continue;

            // If a disconnect gate is armed, do not touch the lease.
            if (FinalPersistGate.hasPendingDisconnect(s.playerUuid)) {
                continue;
            }

            // Not close to expiry yet.
            if (now + RENEW_BEFORE_MS < s.lockExpiresAtMs) {
                continue;
            }

            try {
                BackendClient.InventorySessionAcquireResult res = backend.inventorySessionAcquire(s.playerUuid, s.serverId);

                long newLockExpMs = parseIsoToEpochMs(res.lockExpiresAtIso);

                int oldVersion = s.expectedVersion;
                long oldExp = s.lockExpiresAtMs;

                // Update ONLY in-memory session. Do NOT apply inventory_json.
                s.expectedVersion = res.version;
                s.lockExpiresAtMs = newLockExpMs;

                LOGGER.atInfo().log("LEASE_RENEW_OK playerUuid=" + s.playerUuid
                        + " serverId=" + s.serverId
                        + " expectedVersion=" + oldVersion + "->" + s.expectedVersion
                        + " lockExp=" + Instant.ofEpochMilli(oldExp) + "->" + Instant.ofEpochMilli(s.lockExpiresAtMs));

            } catch (Exception ex) {
                LOGGER.atWarning().log("LEASE_RENEW_FAILED playerUuid=" + s.playerUuid
                        + " serverId=" + s.serverId
                        + " lockExpiresAt=" + Instant.ofEpochMilli(s.lockExpiresAtMs)
                        + " error=" + ex);
            }
        }
    }

    private static long parseIsoToEpochMs(String iso) {
        if (iso == null || iso.isBlank()) return 0L;
        return Instant.parse(iso).toEpochMilli();
    }
}
