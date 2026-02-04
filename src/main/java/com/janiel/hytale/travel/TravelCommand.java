package com.janiel.hytale.travel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

public class TravelCommand extends AbstractPlayerCommand {

    private final TravelConfig cfg;
    private final BackendClient backend;
    private final RequiredArg<String> serverIdArg;

    public TravelCommand(@Nonnull TravelConfig cfg, @Nonnull BackendClient backend) {
        super("travel", "Adds /travel to refer players between servers via proxy listeners.");
        this.cfg = cfg;
        this.backend = backend;

        // Required positional arg: /travel <serverId>
        this.serverIdArg = withRequiredArg("serverId", "Target server id", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String serverId = serverIdArg.get(context);
        if (serverId == null) {
            context.sendMessage(Message.raw("Usage: /travel <serverId>"));
            return;
        }

        serverId = serverId.trim();
        if (serverId.isEmpty()) {
            context.sendMessage(Message.raw("Usage: /travel <serverId>"));
            return;
        }

        if (!cfg.hasServerId(serverId)) {
            context.sendMessage(Message.raw("Unknown serverId: " + serverId));
            context.sendMessage(Message.raw("Available: " + formatAvailable(cfg.getListenerPorts())));
            return;
        }

        Integer port = cfg.getListenerPort(serverId);
        if (port == null) {
            context.sendMessage(Message.raw("Invalid listener port for serverId: " + serverId));
            return;
        }

        String host = cfg.getProxyHost();

        // Create backend transfer ticket BEFORE referral.
        String playerUuid = PlayerIdUtil.getPlayerUuid(playerRef);
        byte[] payloadBytes = null;

        if (playerUuid == null) {
            context.sendMessage(Message.raw("Warning: could not resolve player uuid; backend prepare skipped."));
        } else {
            String snapshotJson = "{}";
            try {
                // Read current persisted player JSON from this server before transferring.
                snapshotJson = PlayerStateFiles.readSnapshotJson(cfg.getUniverseDir(), playerUuid);

                String fromServerId = cfg.resolveCurrentServerId();
                if (fromServerId == null) {
                    fromServerId = "unknown";
                }

                String ticketId = backend.prepare(playerUuid, fromServerId, serverId, snapshotJson);
                context.sendMessage(Message.raw("Prepared transfer ticket: " + ticketId));

                String secret = cfg.getPayloadHmacSecret();
                if (secret == null || secret.isBlank()) {
                    context.sendMessage(Message.raw("Warning: payloadHmacSecret is not set; transfer will not carry a signed ticket payload."));
                } else {
                    String nonce = UUID.randomUUID().toString();
                    payloadBytes = TravelPayload.createSignedBytes(playerUuid, serverId, ticketId, secret, nonce);

                    if (payloadBytes.length > 4096) {
                        context.sendMessage(Message.raw("Warning: referral payload exceeds 4KB; sending without payload."));
                        payloadBytes = null;
                    }
                }
            } catch (Exception ex) {
                context.sendMessage(Message.raw("Warning: backend prepare failed; proceeding with travel. " + ex.getMessage()));
            }
        }

        context.sendMessage(Message.raw("Referring to: " + host + ":" + port + " (serverId=" + serverId + ")"));

        // Player referral (client reconnects to target host/port)
        if (payloadBytes != null) {
            playerRef.referToServer(host, port, payloadBytes);
        } else {
            playerRef.referToServer(host, port);
        }
    }

    private static String formatAvailable(Map<String, Integer> ports) {
        StringJoiner sj = new StringJoiner(", ");
        for (String id : ports.keySet()) {
            sj.add(id);
        }
        return sj.toString();
    }
}
