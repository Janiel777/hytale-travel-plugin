package com.janiel.hytale.menu.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class MenuTabElement extends ChoiceElement {

    private final String label;
    private final boolean active;

    public MenuTabElement(
            @Nonnull String label,
            boolean active
    ) {
        super(label, "", new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.label = label;
        this.active = active;
    }

    @Override
    public void addButton(
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull String selector,
            @Nonnull PlayerRef playerRef
    ) {
        commands.append(
                "#TabsList",
                active
                        ? "Pages/JanielMenu/MenuTabButtonActive.ui"
                        : "Pages/JanielMenu/MenuTabButtonInactive.ui"
        );
        commands.set(selector + " #Label.Text", label);
    }
}