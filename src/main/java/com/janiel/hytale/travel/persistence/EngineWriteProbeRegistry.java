package com.janiel.hytale.travel.persistence;

import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds running EngineWriteProbe instances per player UUID.
 */
public final class EngineWriteProbeRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<String, EngineWriteProbe> PROBES = new ConcurrentHashMap<>();

    private EngineWriteProbeRegistry() {
    }

    public static boolean startProbe(Path universeDir, String playerUuid, long intervalMs, int readsPerTick) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        // Stop existing probe (if any) to apply new parameters.
        stopProbe(playerUuid);

        EngineWriteProbe probe = new EngineWriteProbe(universeDir, playerUuid, intervalMs, readsPerTick);
        PROBES.put(playerUuid, probe);
        probe.start();

        LOGGER.atInfo().log("EngineWriteProbeRegistry started probe for playerUuid=" + playerUuid
                + " intervalMs=" + intervalMs
                + " readsPerTick=" + readsPerTick);

        return true;
    }

    public static boolean stopProbe(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        EngineWriteProbe probe = PROBES.remove(playerUuid);
        if (probe != null) {
            probe.stop();
            return true;
        }
        return false;
    }

    public static boolean isRunning(String playerUuid) {
        EngineWriteProbe probe = PROBES.get(playerUuid);
        return probe != null && probe.isRunning();
    }

    public static long getLastEngineWriteDetectedAtMs(String playerUuid) {
        EngineWriteProbe probe = PROBES.get(playerUuid);
        if (probe == null) {
            return 0L;
        }
        return probe.getLastEngineWriteDetectedAtMs();
    }

    public static long getEngineWriteCount(String playerUuid) {
        EngineWriteProbe probe = PROBES.get(playerUuid);
        if (probe == null) {
            return 0L;
        }
        return probe.getEngineWriteCount();
    }
}
