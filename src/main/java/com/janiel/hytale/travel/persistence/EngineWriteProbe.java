package com.janiel.hytale.travel.persistence;

import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects when the engine overwrites a player's persisted JSON by using a marker field.
 *
 * How it works:
 *  - The probe writes a top-level marker field into the player's JSON.
 *  - If the engine later persists the player, the engine-written JSON is expected to NOT include this marker.
 *  - When we read and the marker is missing, we treat that as "engine wrote".
 *  - We immediately re-insert the marker so the next engine write can be detected.
 *
 * This is intentionally best-effort and string-based to avoid dependencies on JSON libraries.
 */
public final class EngineWriteProbe {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path universeDir;
    private final String playerUuid;
    private final long intervalMs;
    private final int readsPerTick;

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Path playerFilePath;

    private volatile long lastEngineWriteDetectedAtMs = 0L;
    private volatile long engineWriteCount = 0L;
    private volatile long readCount = 0L;

    public long getLastEngineWriteDetectedAtMs() {
        return lastEngineWriteDetectedAtMs;
    }

    public long getEngineWriteCount() {
        return engineWriteCount;
    }

    public long getReadCount() {
        return readCount;
    }

    public EngineWriteProbe(Path universeDir, String playerUuid, long intervalMs, int readsPerTick) {
        this.universeDir = universeDir;
        this.playerUuid = playerUuid;
        this.intervalMs = Math.max(10L, intervalMs);
        this.readsPerTick = Math.max(1, readsPerTick);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "HytaleTravel-EngineWriteProbe-" + (playerUuid == null ? "unknown" : playerUuid));
            t.setDaemon(true);
            return t;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(tf);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            this.playerFilePath = PlayerStateFiles.resolvePlayerFilePath(universeDir, playerUuid);
            ensureMarkerPresent();
        } catch (Exception e) {
            LOGGER.atWarning().log("EngineWriteProbe failed to initialize for playerUuid=" + playerUuid + ": " + e);
            running.set(false);
            return;
        }

        LOGGER.atInfo().log("EngineWriteProbe started. playerUuid=" + playerUuid
                + " file=" + playerFilePath
                + " intervalMs=" + intervalMs
                + " readsPerTick=" + readsPerTick);

        this.scheduler.scheduleAtFixedRate(this::tickSafe, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            // Note: Avoid shutdownNow() here; stop() can be called from the scheduler thread itself.
            // shutdownNow() interrupts the thread and causes FINAL_PERSIST save to fail with InterruptedException.
            scheduler.shutdown();

            try {
                if (!scheduler.awaitTermination(250, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }

        } catch (Exception ignored) {
        }

        LOGGER.atInfo().log("EngineWriteProbe stopped. playerUuid=" + playerUuid
                + " reads=" + readCount
                + " engineWritesDetected=" + engineWriteCount);
    }


    public boolean isRunning() {
        return running.get();
    }

    private void tickSafe() {
        if (!running.get()) {
            return;
        }
        try {
            tick();
        } catch (Throwable t) {
            LOGGER.atWarning().log("EngineWriteProbe tick failed playerUuid=" + playerUuid + ": " + t);
        }
    }

    private void tick() throws Exception {
        if (playerFilePath == null) {
            playerFilePath = PlayerStateFiles.resolvePlayerFilePath(universeDir, playerUuid);
        }

        for (int i = 0; i < readsPerTick; i++) {
            String json = "{}";
            if (Files.exists(playerFilePath)) {
                json = Files.readString(playerFilePath, StandardCharsets.UTF_8);
            }

            readCount++;

            boolean hasMarker = PlayerStateFiles.topLevelHasKey(json, PlayerStateFiles.ENGINE_WRITE_PROBE_KEY);
            if (!hasMarker) {
                // Engine overwrote the file (or file was created fresh).
                engineWriteCount++;

                long nowMs = System.currentTimeMillis();
                long deltaMs = lastEngineWriteDetectedAtMs == 0L ? -1L : (nowMs - lastEngineWriteDetectedAtMs);
                lastEngineWriteDetectedAtMs = nowMs;

                LOGGER.atInfo().log("ENGINE_WRITE_DETECTED playerUuid=" + playerUuid
                        + " count=" + engineWriteCount
                        + " deltaMs=" + deltaMs
                        + " at=" + Instant.ofEpochMilli(nowMs)
                        + " readsPerTick=" + readsPerTick
                        + " intervalMs=" + intervalMs);

                // If we recently saw a disconnect, this is likely the engine's final persist after disconnect.
                FinalPersistGate.onEngineWrite(playerUuid, nowMs);

                // Re-insert marker so we can detect the next overwrite.
                String marked = PlayerStateFiles.upsertTopLevelStringField(json, PlayerStateFiles.ENGINE_WRITE_PROBE_KEY, "1");
                PlayerStateFiles.writeSnapshotJsonNoBackup(universeDir, playerUuid, marked);
            }
        }
    }

    private void ensureMarkerPresent() throws Exception {
        String json = "{}";
        if (playerFilePath != null && Files.exists(playerFilePath)) {
            json = Files.readString(playerFilePath, StandardCharsets.UTF_8);
        }

        String marked = PlayerStateFiles.upsertTopLevelStringField(json, PlayerStateFiles.ENGINE_WRITE_PROBE_KEY, "1");
        PlayerStateFiles.writeSnapshotJsonNoBackup(universeDir, playerUuid, marked);
    }
}
