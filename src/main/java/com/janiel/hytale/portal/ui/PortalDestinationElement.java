package com.janiel.hytale.portal.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class PortalDestinationElement extends ChoiceElement {

    private final String name;
    private final String description;

    public PortalDestinationElement(@Nonnull String name, @Nonnull String description) {
        super(name, description, new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.name = name;
        this.description = description;
    }

    @Override
    public void addButton(@Nonnull UICommandBuilder commands,
                          @Nonnull UIEventBuilder events,
                          @Nonnull String selector,
                          @Nonnull PlayerRef playerRef) {

        // Append our custom entry template into the list container in PortalHome.ui
        commands.append("#ElementList", "Pages/JanielPortal/PortalEntryButton.ui");

        // Set text on our labels (ids defined in PortalEntryButton.ui)
        commands.set(selector + " #Name.Text", name);
        commands.set(selector + " #Description.Text", description);
        commands.set(selector + " #Tag.Text", "DEFAULT");
    }
}
