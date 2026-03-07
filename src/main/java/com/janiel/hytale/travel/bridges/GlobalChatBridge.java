package com.janiel.hytale.travel.bridges;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.services.GlobalChatWebSocketService;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GlobalChatBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final String currentServerId;

    public GlobalChatBridge(@Nonnull TravelConfig config) {
        this.currentServerId = config.resolveCurrentServerId();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        if (currentServerId == null || currentServerId.isBlank()) {
            LOGGER.atWarning().log("GlobalChatBridge disabled: current serverId could not be resolved");
            return;
        }

        eventRegistry.registerAsyncGlobal(
                PlayerChatEvent.class,
                future -> future.thenApply(this::onPlayerChat)
        );

        LOGGER.atInfo().log("GlobalChatBridge registered for PlayerChatEvent with serverId=" + currentServerId);
    }

    private PlayerChatEvent onPlayerChat(@Nonnull PlayerChatEvent event) {
        try {
            if (event.isCancelled()) {
                return event;
            }

            PlayerRef sender = event.getSender();
            if (sender == null) {
                return event;
            }

            String content = safeTrim(event.getContent());
            if (content.isEmpty()) {
                return event;
            }

            if (!GlobalChatWebSocketService.isConnected()) {
                return event;
            }

            String playerName = resolvePlayerName(sender);
            boolean accepted = GlobalChatWebSocketService.publishGlobalChat(playerName, content);

            if (!accepted) {
                return event;
            }

            event.setCancelled(true);
            GlobalChatWebSocketService.renderLocalGlobalChat(playerName, content);

            LOGGER.atInfo().log("GLOBAL_CHAT_INTERCEPT_OK serverId=" + currentServerId
                    + " playerName=" + playerName
                    + " contentLength=" + content.length());

            return event;
        } catch (Exception ex) {
            LOGGER.atWarning().log("GLOBAL_CHAT_INTERCEPT_FAILED serverId=" + currentServerId
                    + " error=" + ex);
            return event;
        }
    }

    private static String resolvePlayerName(PlayerRef sender) {
        String username = sender.getUsername();
        username = safeTrim(username);
        if (!username.isEmpty()) {
            return username;
        }

        UUID uuid = sender.getUuid();
        return uuid == null ? "UnknownPlayer" : uuid.toString();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}