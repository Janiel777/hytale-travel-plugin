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

/**
 * Starts the EngineWriteProbe for the executing player.
 *
 * Usage:
 *   /probeenginewrite <intervalMs> <readsPerTick>
 */
public final class ProbeEngineWriteCommand extends AbstractPlayerCommand {

    private final TravelConfig cfg;

    private final RequiredArg<String> intervalMsArg;
    private final RequiredArg<String> readsPerTickArg;

    public ProbeEngineWriteCommand(@Nonnull TravelConfig cfg) {
        super("probeenginewrite", "Starts the engine JSON write probe for your player.");
        this.cfg = cfg;

        this.intervalMsArg = withRequiredArg("intervalMs", "Probe tick interval in milliseconds", ArgTypes.STRING);
        this.readsPerTickArg = withRequiredArg("readsPerTick", "How many reads to do each probe tick", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String playerUuid = PlayerIdUtil.getPlayerUuid(playerRef);
        if (playerUuid == null || playerUuid.isBlank()) {
            context.sendMessage(Message.raw("Could not resolve your player uuid in this patchline."));
            return;
        }

        long intervalMs;
        int readsPerTick;

        try {
            intervalMs = Long.parseLong(String.valueOf(intervalMsArg.get(context)).trim());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Usage: /probeenginewrite <intervalMs> <readsPerTick>"));
            return;
        }

        try {
            readsPerTick = Integer.parseInt(String.valueOf(readsPerTickArg.get(context)).trim());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Usage: /probeenginewrite <intervalMs> <readsPerTick>"));
            return;
        }

        if (intervalMs < 10L) intervalMs = 10L;
        if (readsPerTick < 1) readsPerTick = 1;

        boolean ok = EngineWriteProbeRegistry.startProbe(cfg.getUniverseDir(), playerUuid, intervalMs, readsPerTick);
        if (!ok) {
            context.sendMessage(Message.raw("Failed to start probe."));
            return;
        }

        context.sendMessage(Message.raw("Engine write probe started. intervalMs=" + intervalMs + " readsPerTick=" + readsPerTick));
        context.sendMessage(Message.raw("Marker key: " + PlayerStateFiles.ENGINE_WRITE_PROBE_KEY));
        context.sendMessage(Message.raw("Watch server logs for: ENGINE_WRITE_DETECTED"));
    }
}
