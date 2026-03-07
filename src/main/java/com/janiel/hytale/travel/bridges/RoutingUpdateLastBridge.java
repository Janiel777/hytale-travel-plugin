package com.janiel.hytale.travel.bridges;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.net.BackendClient;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class RoutingUpdateLastBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TravelConfig config;
    private final BackendClient backend;
    private final String currentServerId;

    public RoutingUpdateLastBridge(@Nonnull TravelConfig config, @Nonnull BackendClient backend) {
        this.config = config;
        this.backend = backend;
        this.currentServerId = config.resolveCurrentServerId();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        if (currentServerId == null || currentServerId.isBlank()) {
            LOGGER.atWarning().log("RoutingUpdateLastBridge disabled: current serverId could not be resolved from travel.properties/--port");
            return;
        }

        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        LOGGER.atInfo().log("RoutingUpdateLastBridge registered for PlayerConnectEvent with serverId=" + currentServerId);
    }

    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            LOGGER.atWarning().log("ROUTING_UPDATE_LAST_SKIP reason=playerRef_null serverId=" + currentServerId);
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            LOGGER.atWarning().log("ROUTING_UPDATE_LAST_SKIP reason=playerUuid_null serverId=" + currentServerId);
            return;
        }

        Thread thread = new Thread(() -> callUpdateLast(playerUuid), "routing-update-last-" + playerUuid);
        thread.setDaemon(true);
        thread.start();
    }

    private void callUpdateLast(@Nonnull UUID playerUuid) {
        try {
            BackendClient.RoutingUpdateLastResult result = backend.routingUpdateLast(playerUuid.toString(), currentServerId);

            LOGGER.atInfo().log("ROUTING_UPDATE_LAST_OK playerUuid=" + result.playerUuid
                    + " serverId=" + result.serverId
                    + " updated=" + result.updated);
        } catch (Exception ex) {
            LOGGER.atWarning().log("ROUTING_UPDATE_LAST_FAILED playerUuid=" + playerUuid
                    + " serverId=" + currentServerId
                    + " error=" + ex);
        }
    }
}