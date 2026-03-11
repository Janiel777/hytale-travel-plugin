package com.janiel.hytale.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class MutationDetailElement extends ChoiceElement {

    private final String name;
    private final String description;
    private final String levelLabel;
    private final String progressLabel;
    private final String tierLine1;
    private final String tierLine2;
    private final String tierLine3;
    private final String summaryLine1;
    private final String summaryLine2;
    private final String summaryLine3;
    private final String containerSelector;

    public MutationDetailElement(
            @Nonnull String name,
            @Nonnull String description,
            @Nonnull String levelLabel,
            @Nonnull String progressLabel,
            @Nonnull String tierLine1,
            @Nonnull String tierLine2,
            @Nonnull String tierLine3,
            @Nonnull String summaryLine1,
            @Nonnull String summaryLine2,
            @Nonnull String summaryLine3,
            @Nonnull String containerSelector
    ) {
        super(name, description, new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.name = name;
        this.description = description;
        this.levelLabel = levelLabel;
        this.progressLabel = progressLabel;
        this.tierLine1 = tierLine1;
        this.tierLine2 = tierLine2;
        this.tierLine3 = tierLine3;
        this.summaryLine1 = summaryLine1;
        this.summaryLine2 = summaryLine2;
        this.summaryLine3 = summaryLine3;
        this.containerSelector = containerSelector;
    }

    @Override
    public void addButton(
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull String selector,
            @Nonnull PlayerRef playerRef
    ) {
        commands.append(containerSelector, "Pages/JanielMenu/MutationDetailCard.ui");
        commands.set(selector + " #Name.Text", name);
        commands.set(selector + " #Description.Text", description);
        commands.set(selector + " #Level.Text", levelLabel);
        commands.set(selector + " #Progress.Text", progressLabel);
        commands.set(selector + " #Tier1.Text", tierLine1);
        commands.set(selector + " #Tier2.Text", tierLine2);
        commands.set(selector + " #Tier3.Text", tierLine3);
        commands.set(selector + " #Summary1.Text", summaryLine1);
        commands.set(selector + " #Summary2.Text", summaryLine2);
        commands.set(selector + " #Summary3.Text", summaryLine3);
    }
}