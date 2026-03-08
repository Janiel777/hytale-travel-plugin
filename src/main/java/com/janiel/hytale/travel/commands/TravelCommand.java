package com.janiel.hytale.travel.commands;

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
import com.janiel.hytale.core.config.TravelConfig;
import com.janiel.hytale.travel.model.TravelPayload;
import com.janiel.hytale.network.backend.BackendClient;
import com.janiel.hytale.core.util.PlayerIdUtil;

import javax.annotation.Nonnull;
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
            context.sendMessage(Message.raw("Available: " + formatAvailable(cfg.getListenerTargets().keySet())));
            return;
        }

        TravelConfig.ListenerTarget target = cfg.getListenerTarget(serverId);
        if (target == null) {
            context.sendMessage(Message.raw("Invalid listener target for serverId: " + serverId));
            return;
        }

        String host = target.getHost();
        int port = target.getPort();

        // Referral payload is signed so the client cannot tamper with the intended target server.
        // Inventory persistence is handled via backend inventory sessions:
        //  - lock is acquired on connect (setup stage)
        //  - save+release happens after the engine's final JSON persist on disconnect/travel
        String playerUuid = PlayerIdUtil.getPlayerUuid(playerRef);
        byte[] payloadBytes = null;

        if (playerUuid == null) {
            context.sendMessage(Message.raw("Warning: could not resolve player uuid; sending travel without signed payload."));
        } else {
            String secret = cfg.getPayloadHmacSecret();
            if (secret == null || secret.isBlank()) {
                context.sendMessage(Message.raw("Warning: payloadHmacSecret is not set; travel will not carry a signed payload."));
            } else {
                try {
                    String nonce = UUID.randomUUID().toString();

                    // ticketId is intentionally empty in the inventory-session flow.
                    payloadBytes = TravelPayload.createSignedBytes(playerUuid, serverId, "", secret, nonce);

                    if (payloadBytes.length > 4096) {
                        context.sendMessage(Message.raw("Warning: referral payload exceeds 4KB; sending without payload."));
                        payloadBytes = null;
                    }
                } catch (Exception ex) {
                    context.sendMessage(Message.raw("Warning: could not build referral payload; sending without payload. " + ex.getMessage()));
                    payloadBytes = null;
                }
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

    private String formatAvailable(java.util.Set<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", serverIds);
    }
}
