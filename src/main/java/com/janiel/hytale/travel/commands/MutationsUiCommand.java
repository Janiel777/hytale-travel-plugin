package com.janiel.hytale.travel.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.ui.MutationsPage;

import javax.annotation.Nonnull;

/**
 * Temporary command used during development to open the Mutations custom page.
 *
 * Later we will open this from a keybind (I) / inventory subpage.
 */
public final class MutationsUiCommand extends AbstractPlayerCommand {

    public MutationsUiCommand() {
        super("mutations", "Opens the custom mutations UI.");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("mutations: could not resolve Player component."));
            return;
        }

        PageManager pages = player.getPageManager();
        if (pages == null) {
            context.sendMessage(Message.raw("mutations: PageManager not available."));
            return;
        }

        pages.openCustomPage(ref, store, MutationsPage.create(playerRef));
    }
}