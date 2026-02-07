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

public class DebugBlockCommand extends AbstractPlayerCommand {

    public DebugBlockCommand() {
        super("debugblock", "Debug: prints the block type id at and around the player's feet.");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        // Try to sample a few y-levels to reduce confusion about which block is the portal anchor.
        int x = (int) Math.floor(playerRef.getTransform().getPosition().getX());
        int y = (int) Math.floor(playerRef.getTransform().getPosition().getY());
        int z = (int) Math.floor(playerRef.getTransform().getPosition().getZ());

        String b0 = safeBlockId(world, x, y - 1, z);
        String b1 = safeBlockId(world, x, y, z);
        String b2 = safeBlockId(world, x, y + 1, z);

        context.sendMessage(Message.raw("debugblock @ x=" + x + " y=" + y + " z=" + z));
        context.sendMessage(Message.raw("  y-1: " + b0));
        context.sendMessage(Message.raw("   y : " + b1));
        context.sendMessage(Message.raw("  y+1: " + b2));
    }

    private static String safeBlockId(World world, int x, int y, int z) {
        try {
            Object blockType = world.getBlockType(x, y, z);
            if (blockType == null) {
                return "(null)";
            }
            // BlockType has getId() in this patchline.
            return String.valueOf(blockType.getClass().getMethod("getId").invoke(blockType));
        } catch (Throwable t) {
            return "(error: " + t.getClass().getSimpleName() + ")";
        }
    }
}
