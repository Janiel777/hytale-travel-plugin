package com.janiel.hytale.travel.services;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.net.BackendClient;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerHeartbeatService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;

    private static ScheduledExecutorService scheduler;
    private static TravelConfig config;
    private static BackendClient backend;
    private static String serverId;
    private static String heartbeatHost;
    private static Integer heartbeatPort;

    private ServerHeartbeatService() {
    }

    public static synchronized void start(@Nonnull TravelConfig cfg, @Nonnull BackendClient client) {
        if (scheduler != null) {
            return;
        }

        config = cfg;
        backend = client;
        serverId = cfg.resolveCurrentServerId();

        if (serverId == null || serverId.isBlank()) {
            LOGGER.atWarning().log("ServerHeartbeatService disabled: current serverId could not be resolved from travel.properties/--port");
            return;
        }

        heartbeatHost = cfg.getProxyHost();
        heartbeatPort = cfg.getListenerPort(serverId);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "server-heartbeat-service");
            t.setDaemon(true);
            return t;
        });

        LOGGER.atInfo().log("ServerHeartbeatService starting with serverId=" + serverId
                + " host=" + safe(heartbeatHost)
                + " port=" + safe(heartbeatPort)
                + " intervalSeconds=" + HEARTBEAT_INTERVAL_SECONDS);

        scheduler.scheduleAtFixedRate(
                ServerHeartbeatService::sendHeartbeatSafely,
                0L,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public static synchronized void stop() {
        if (scheduler == null) {
            return;
        }

        try {
            scheduler.shutdown();
        } finally {
            scheduler = null;
        }
    }

    private static void sendHeartbeatSafely() {
        try {
            int playerCount = Universe.get().getPlayerCount();

            BackendClient.ServerHeartbeatResult result = backend.serverHeartbeat(
                    serverId,
                    "UP",
                    playerCount,
                    heartbeatHost,
                    heartbeatPort
            );

            LOGGER.atInfo().log("SERVER_HEARTBEAT_OK serverId=" + result.serverId
                    + " status=" + result.status
                    + " playerCount=" + result.playerCount
                    + " lastHeartbeatAt=" + result.lastHeartbeatAtIso);
        } catch (Exception ex) {
            LOGGER.atWarning().log("SERVER_HEARTBEAT_FAILED serverId=" + safe(serverId)
                    + " error=" + ex);
        }
    }

    private static String safe(Object value) {
        return value == null ? "<null>" : value.toString();
    }
}