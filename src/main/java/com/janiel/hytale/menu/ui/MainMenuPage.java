package com.janiel.hytale.menu.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.ui.MutationsPage;

import javax.annotation.Nonnull;

public final class MainMenuPage extends ChoiceBasePage {

    private final PlayerRef playerRef;

    private MainMenuPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull ChoiceElement[] elements
    ) {
        super(playerRef, elements, "Pages/JanielMenu/MainMenuHome.ui");
        this.playerRef = playerRef;
    }

    public static MainMenuPage create(@Nonnull PlayerRef playerRef) {
        ChoiceElement[] elements = new ChoiceElement[] {
                new MenuEntryElement(
                        "Mutations",
                        "Open the mutations page and review progression.",
                        "OPEN"
                ),
                new MenuEntryElement(
                        "Equipment",
                        "Open the equipment placeholder page.",
                        "OPEN"
                )
        };

        return new MainMenuPage(playerRef, elements);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ChoicePageEventData data
    ) {
        int index = data.getIndex();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PageManager pages = player.getPageManager();
        if (pages == null) {
            return;
        }

        if (index == 0) {
            pages.openCustomPage(ref, store, MutationsPage.create(playerRef));
            return;
        }

        if (index == 1) {
            pages.openCustomPage(ref, store, EquipmentPage.create(playerRef));
        }
    }
}