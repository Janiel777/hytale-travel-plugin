package com.janiel.hytale.travel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Stops the EngineWriteProbe for the executing player.
 *
 * Usage:
 *   /stopprobeenginewrite
 */
public final class StopProbeEngineWriteCommand extends AbstractPlayerCommand {

    public StopProbeEngineWriteCommand() {
        super("stopprobeenginewrite", "Stops the engine JSON write probe for your player.");
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

        boolean stopped = EngineWriteProbeRegistry.stopProbe(playerUuid);
        if (stopped) {
            context.sendMessage(Message.raw("Engine write probe stopped."));
        } else {
            context.sendMessage(Message.raw("No running probe found for you."));
        }
    }
}
