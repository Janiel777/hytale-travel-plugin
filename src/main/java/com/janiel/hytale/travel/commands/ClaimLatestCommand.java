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
import com.janiel.hytale.network.backend.BackendClient;
import com.janiel.hytale.core.util.PlayerIdUtil;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.StringJoiner;

public class ClaimLatestCommand extends AbstractPlayerCommand {

    private final TravelConfig cfg;
    private final BackendClient backend;
    private final RequiredArg<String> serverIdArg;

    public ClaimLatestCommand(@Nonnull TravelConfig cfg, @Nonnull BackendClient backend) {
        super("claimlatest", "Claims latest transfer snapshot for /travel testing.");
        this.cfg = cfg;
        this.backend = backend;

        // Required positional arg: /claimlatest <serverId>
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
            context.sendMessage(Message.raw("Usage: /claimlatest <serverId>"));
            return;
        }

        serverId = serverId.trim();
        if (serverId.isEmpty()) {
            context.sendMessage(Message.raw("Usage: /claimlatest <serverId>"));
            return;
        }

        if (!cfg.hasServerId(serverId)) {
            context.sendMessage(Message.raw("Unknown serverId: " + serverId));
            context.sendMessage(Message.raw("Available: " + formatAvailable(cfg.getListenerTargets().keySet())));
            return;
        }

        String playerUuid = PlayerIdUtil.getPlayerUuid(playerRef);
        if (playerUuid == null) {
            context.sendMessage(Message.raw("Could not resolve player uuid."));
            return;
        }

        try {
            String snapshotJson = backend.claimLatest(playerUuid, serverId);
            context.sendMessage(Message.raw("Claimed latest snapshot for to_server=" + serverId + ": " + snapshotJson));
        } catch (Exception ex) {
            context.sendMessage(Message.raw("Claimlatest failed: " + ex.getMessage()));
        }
    }

    private static String formatAvailable(Set<String> serverIds) {
        StringJoiner sj = new StringJoiner(", ");
        for (String id : serverIds) {
            sj.add(id);
        }
        return sj.toString();
    }
}
