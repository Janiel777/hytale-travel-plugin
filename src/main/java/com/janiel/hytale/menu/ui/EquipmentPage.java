package com.janiel.hytale.menu.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class EquipmentPage extends ChoiceBasePage {

    private EquipmentPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull ChoiceElement[] elements
    ) {
        super(playerRef, elements, "Pages/JanielMenu/EquipmentHome.ui");
    }

    public static EquipmentPage create(@Nonnull PlayerRef playerRef) {
        ChoiceElement[] elements = new ChoiceElement[] {
                new MenuEntryElement(
                        "Coming Soon",
                        "This page will become the equipment and inventory clone.",
                        "PLACEHOLDER"
                )
        };

        return new EquipmentPage(playerRef, elements);
    }
}