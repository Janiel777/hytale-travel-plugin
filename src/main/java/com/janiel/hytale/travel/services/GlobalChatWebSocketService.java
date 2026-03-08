package com.janiel.hytale.travel.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.janiel.hytale.travel.config.TravelConfig;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GlobalChatWebSocketService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final long[] RECONNECT_DELAYS_SECONDS = new long[] {1L, 2L, 5L, 10L, 10L, 10L};

    private static ScheduledExecutorService scheduler;
    private static HttpClient httpClient;

    private static String currentServerId;
    private static String websocketUrl;

    private static volatile WebSocket webSocket;
    private static volatile boolean chatSocketConnected;

    private static int reconnectAttempt;
    private static boolean reconnectScheduled;

    private GlobalChatWebSocketService() {
    }

    public static synchronized void start(@Nonnull TravelConfig config) {
        if (scheduler != null) {
            return;
        }

        currentServerId = config.resolveCurrentServerId();
        if (currentServerId == null || currentServerId.isBlank()) {
            LOGGER.atWarning().log("GlobalChatWebSocketService disabled: current serverId could not be resolved");
            return;
        }

        websocketUrl = toWebSocketUrl(config.getBackendBaseUrl()) + "/ws/servers/chat";

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getBackendTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "global-chat-websocket-service");
            t.setDaemon(true);
            return t;
        });

        reconnectAttempt = 0;
        reconnectScheduled = false;
        chatSocketConnected = false;

        LOGGER.atInfo().log("GlobalChatWebSocketService starting with serverId=" + currentServerId
                + " websocketUrl=" + websocketUrl);

        connectNow();
    }

    public static synchronized boolean isConnected() {
        return chatSocketConnected && webSocket != null;
    }

    public static synchronized boolean publishGlobalChat(@Nonnull String playerName, @Nonnull String content) {
        WebSocket socket = webSocket;
        if (!chatSocketConnected || socket == null) {
            return false;
        }

        String normalizedPlayerName = safeTrim(playerName);
        String normalizedContent = safeTrim(content);

        if (normalizedPlayerName.isEmpty() || normalizedContent.isEmpty()) {
            return false;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "chat_publish");
        payload.addProperty("player_name", normalizedPlayerName);
        payload.addProperty("message", normalizedContent);

        try {
            socket.sendText(payload.toString(), true)
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            LOGGER.atWarning().log("GLOBAL_CHAT_PUBLISH_FAILED serverId=" + currentServerId
                                    + " playerName=" + normalizedPlayerName
                                    + " error=" + throwable);
                            handleSocketFailure(socket, "publish_failed");
                        }
                    });

            return true;
        } catch (Exception ex) {
            LOGGER.atWarning().log("GLOBAL_CHAT_PUBLISH_THROW serverId=" + currentServerId
                    + " playerName=" + normalizedPlayerName
                    + " error=" + ex);
            handleSocketFailure(socket, "publish_throw");
            return false;
        }
    }

    public static void renderLocalGlobalChat(@Nonnull String playerName, @Nonnull String content) {
        renderGlobalChat(currentServerId, playerName, content);
    }

    public static void renderRemoteGlobalChat(@Nonnull String originServerId, @Nonnull String playerName, @Nonnull String content) {
        renderGlobalChat(originServerId, playerName, content);
    }

    private static synchronized void connectNow() {
        if (scheduler == null || httpClient == null) {
            return;
        }
        if (webSocket != null) {
            return;
        }

        LOGGER.atInfo().log("GLOBAL_CHAT_WS_CONNECTING serverId=" + currentServerId
                + " url=" + websocketUrl
                + " reconnectAttempt=" + reconnectAttempt);

        try {
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(websocketUrl), new Listener())
                    .whenComplete((socket, throwable) -> {
                        if (throwable != null) {
                            LOGGER.atWarning().log("GLOBAL_CHAT_WS_CONNECT_FAILED serverId=" + currentServerId
                                    + " url=" + websocketUrl
                                    + " error=" + throwable);
                            scheduleReconnect("connect_failed");
                            return;
                        }

                        synchronized (GlobalChatWebSocketService.class) {
                            webSocket = socket;
                        }

                        LOGGER.atInfo().log("GLOBAL_CHAT_WS_CONNECT_OK serverId=" + currentServerId
                                + " url=" + websocketUrl);
                    });
        } catch (Exception ex) {
            LOGGER.atWarning().log("GLOBAL_CHAT_WS_CONNECT_THROW serverId=" + currentServerId
                    + " url=" + websocketUrl
                    + " error=" + ex);
            scheduleReconnect("connect_throw");
        }
    }

    private static synchronized void sendRegister(WebSocket socket) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "register");
        payload.addProperty("server_id", currentServerId);

        socket.sendText(payload.toString(), true)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        LOGGER.atWarning().log("GLOBAL_CHAT_WS_REGISTER_FAILED serverId=" + currentServerId
                                + " error=" + throwable);
                        handleSocketFailure(socket, "register_failed");
                    } else {
                        LOGGER.atInfo().log("GLOBAL_CHAT_WS_REGISTER_SENT serverId=" + currentServerId);
                    }
                });
    }

    private static void handleInboundText(WebSocket socket, String text) {
        try {
            JsonObject payload = JsonParser.parseString(text).getAsJsonObject();
            String type = payload.has("type") && !payload.get("type").isJsonNull()
                    ? payload.get("type").getAsString()
                    : "";

            if ("registered".equals(type)) {
                synchronized (GlobalChatWebSocketService.class) {
                    if (webSocket != socket) {
                        return;
                    }
                    chatSocketConnected = true;
                    reconnectAttempt = 0;
                    reconnectScheduled = false;
                }

                LOGGER.atInfo().log("GLOBAL_CHAT_WS_REGISTERED serverId=" + currentServerId);
                return;
            }

            if ("chat_broadcast".equals(type)) {
                String originServerId = getRequiredString(payload, "origin_server_id");
                String playerName = getRequiredString(payload, "player_name");
                String message = getRequiredString(payload, "message");

                if (originServerId == null || playerName == null || message == null) {
                    LOGGER.atWarning().log("GLOBAL_CHAT_WS_SKIP_INVALID_BROADCAST serverId=" + currentServerId
                            + " payload=" + text);
                    return;
                }

                LOGGER.atInfo().log("GLOBAL_CHAT_WS_RECEIVED serverId=" + currentServerId
                        + " originServerId=" + originServerId
                        + " playerName=" + playerName);

                renderRemoteGlobalChat(originServerId, playerName, message);
                return;
            }

            if ("error".equals(type)) {
                LOGGER.atWarning().log("GLOBAL_CHAT_WS_SERVER_ERROR serverId=" + currentServerId
                        + " payload=" + text);
                return;
            }

            LOGGER.atInfo().log("GLOBAL_CHAT_WS_IGNORED serverId=" + currentServerId
                    + " payload=" + text);
        } catch (Exception ex) {
            LOGGER.atWarning().log("GLOBAL_CHAT_WS_PARSE_FAILED serverId=" + currentServerId
                    + " payload=" + text
                    + " error=" + ex);
        }
    }

    private static void renderGlobalChat(String serverId, String playerName, String content) {
        String formatted = "[" + safeTrim(serverId) + "] " + safeTrim(playerName) + ": " + safeTrim(content);

        World world = findAnyLoadedWorld();
        if (world == null) {
            LOGGER.atWarning().log("GLOBAL_CHAT_RENDER_SKIPPED reason=no_loaded_world formatted=" + formatted);
            return;
        }

        world.execute(() -> {
            try {
                Universe.get().sendMessage(Message.raw(formatted));
            } catch (Exception ex) {
                LOGGER.atWarning().log("GLOBAL_CHAT_RENDER_FAILED formatted=" + formatted + " error=" + ex);
            }
        });
    }

    private static World findAnyLoadedWorld() {
        try {
            Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null || worlds.isEmpty()) {
                return null;
            }

            for (World world : worlds.values()) {
                if (world != null) {
                    return world;
                }
            }

            return null;
        } catch (Exception ex) {
            LOGGER.atWarning().log("GLOBAL_CHAT_FIND_WORLD_FAILED error=" + ex);
            return null;
        }
    }

    private static synchronized void handleSocketFailure(WebSocket socket, String reason) {
        if (webSocket == socket) {
            webSocket = null;
        }

        chatSocketConnected = false;
        scheduleReconnect(reason);
    }

    private static synchronized void scheduleReconnect(String reason) {
        if (scheduler == null) {
            return;
        }
        if (reconnectScheduled) {
            return;
        }

        reconnectScheduled = true;

        int attemptIndex = reconnectAttempt;
        if (attemptIndex < 0) {
            attemptIndex = 0;
        }
        if (attemptIndex >= RECONNECT_DELAYS_SECONDS.length) {
            attemptIndex = RECONNECT_DELAYS_SECONDS.length - 1;
        }

        long delaySeconds = RECONNECT_DELAYS_SECONDS[attemptIndex];
        reconnectAttempt++;

        LOGGER.atWarning().log("GLOBAL_CHAT_WS_RECONNECT_SCHEDULED serverId=" + currentServerId
                + " reason=" + reason
                + " delaySeconds=" + delaySeconds
                + " reconnectAttempt=" + reconnectAttempt);

        scheduler.schedule(() -> {
            synchronized (GlobalChatWebSocketService.class) {
                reconnectScheduled = false;
                if (webSocket != null) {
                    return;
                }
            }

            connectNow();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private static String toWebSocketUrl(String backendBaseUrl) {
        String url = backendBaseUrl == null ? "" : backendBaseUrl.trim();

        if (url.startsWith("https://")) {
            return "wss://" + url.substring("https://".length());
        }
        if (url.startsWith("http://")) {
            return "ws://" + url.substring("http://".length());
        }
        if (url.startsWith("wss://") || url.startsWith("ws://")) {
            return url;
        }

        return "ws://" + url;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String getRequiredString(JsonObject payload, String key) {
        if (!payload.has(key) || payload.get(key).isJsonNull()) {
            return null;
        }

        String value = payload.get(key).getAsString();
        value = safeTrim(value);
        return value.isEmpty() ? null : value;
    }

    private static final class Listener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            LOGGER.atInfo().log("GLOBAL_CHAT_WS_OPEN serverId=" + currentServerId);
            webSocket.request(1);
            sendRegister(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);

            if (last) {
                String fullText = textBuffer.toString();
                textBuffer.setLength(0);
                handleInboundText(webSocket, fullText);
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOGGER.atWarning().log("GLOBAL_CHAT_WS_CLOSE serverId=" + currentServerId
                    + " statusCode=" + statusCode
                    + " reason=" + reason);

            handleSocketFailure(webSocket, "close_" + statusCode);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.atWarning().log("GLOBAL_CHAT_WS_ERROR serverId=" + currentServerId
                    + " error=" + error);
            handleSocketFailure(webSocket, "error");
        }
    }
}