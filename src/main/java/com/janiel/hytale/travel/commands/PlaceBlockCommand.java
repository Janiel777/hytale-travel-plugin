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

import javax.annotation.Nonnull;

public class PlaceBlockCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> blockIdArg;

    public PlaceBlockCommand() {
        super("placeblock", "Debug: places a block by id at the player's position (y-1) or y if air.");
        this.blockIdArg = withRequiredArg("blockId", "Block type id (e.g. Forgotten_Temple_Portal_Enter)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String blockId = blockIdArg.get(context);
        if (blockId == null || blockId.trim().isEmpty()) {
            context.sendMessage(Message.raw("Usage: /placeblock <blockId>"));
            return;
        }
        blockId = blockId.trim();

        int x = (int) Math.floor(playerRef.getTransform().getPosition().getX());
        int y = (int) Math.floor(playerRef.getTransform().getPosition().getY());
        int z = (int) Math.floor(playerRef.getTransform().getPosition().getZ());

        // Prefer placing at y-1 (block under feet). If it's air, place at y instead.
        int targetY = y - 1;
        String under = safeBlockId(world, x, targetY, z);
        if (under != null && under.toLowerCase().contains("air")) {
            targetY = y;
        }

        int finalTargetY = targetY;
        String finalBlockId = blockId;

        // World provides execute(Runnable) in this patchline; place in the world's thread.
        world.execute(() -> {
            try {
                world.setBlock(x, finalTargetY, z, finalBlockId);
            } catch (Throwable t) {
                // best-effort: swallow to avoid crashing the server thread
            }
        });

        context.sendMessage(Message.raw("Placed blockId=" + blockId + " at x=" + x + " y=" + targetY + " z=" + z));
    }

    private static String safeBlockId(World world, int x, int y, int z) {
        try {
            Object blockType = world.getBlockType(x, y, z);
            if (blockType == null) {
                return null;
            }
            return String.valueOf(blockType.getClass().getMethod("getId").invoke(blockType));
        } catch (Throwable t) {
            return null;
        }
    }
}
