package com.janiel.hytale.portal.commands;

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
import com.janiel.hytale.core.config.TravelConfig;
import com.janiel.hytale.portal.ui.PortalChoicePage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PortalUiCommand extends AbstractPlayerCommand {

    private final TravelConfig cfg;

    public PortalUiCommand(@Nonnull TravelConfig cfg) {
        super("portalui", "Opens the custom portal selector UI.");
        this.cfg = cfg;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("portalui: could not resolve Player component."));
            return;
        }

        PageManager pages = player.getPageManager();
        if (pages == null) {
            context.sendMessage(Message.raw("portalui: PageManager not available."));
            return;
        }

        List<String> serverIds = new ArrayList<>(cfg.getListenerTargets().keySet());
        Collections.sort(serverIds);

        if (serverIds.isEmpty()) {
            context.sendMessage(Message.raw("portalui: no serverIds configured."));
            return;
        }

        PortalChoicePage page = PortalChoicePage.create(playerRef, cfg, serverIds, 0);
        pages.openCustomPage(ref, store, page);
    }
}
