package com.janiel.hytale.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class MutationEntryElement extends ChoiceElement {

    private final String name;
    private final String description;
    private final String levelLabel;
    private final String progressLabel;

    public MutationEntryElement(@Nonnull String name,
                                @Nonnull String description,
                                @Nonnull String levelLabel,
                                @Nonnull String progressLabel) {

        super(name, description, new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.name = name;
        this.description = description;
        this.levelLabel = levelLabel;
        this.progressLabel = progressLabel;
    }

    @Override
    public void addButton(@Nonnull UICommandBuilder commands,
                          @Nonnull UIEventBuilder events,
                          @Nonnull String selector,
                          @Nonnull PlayerRef playerRef) {

        // Append our custom entry template into the list container in MutationsHome.ui
        commands.append("#ElementList", "Pages/JanielMutations/MutationEntryButton.ui");

        // Set text on our labels (ids defined in MutationEntryButton.ui)
        commands.set(selector + " #Name.Text", name);
        commands.set(selector + " #Description.Text", description);
        commands.set(selector + " #Level.Text", levelLabel);
        commands.set(selector + " #Progress.Text", progressLabel);
    }
}