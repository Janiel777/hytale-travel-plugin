package com.janiel.hytale.menu.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class MenuEntryElement extends ChoiceElement {

    private final String name;
    private final String description;
    private final String tag;

    public MenuEntryElement(
            @Nonnull String name,
            @Nonnull String description,
            @Nonnull String tag
    ) {
        super(name, description, new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.name = name;
        this.description = description;
        this.tag = tag;
    }

    @Override
    public void addButton(
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull String selector,
            @Nonnull PlayerRef playerRef
    ) {
        commands.append("#ElementList", "Pages/JanielMenu/MenuEntryButton.ui");
        commands.set(selector + " #Name.Text", name);
        commands.set(selector + " #Description.Text", description);
        commands.set(selector + " #Tag.Text", tag);
    }
}