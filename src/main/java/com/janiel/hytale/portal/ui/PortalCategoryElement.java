package com.janiel.hytale.portal.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class PortalCategoryElement extends ChoiceElement {

    private final String label;
    private final int categoryIndex;

    public PortalCategoryElement(@Nonnull String label, int categoryIndex) {
        super(label, "", new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.label = label;
        this.categoryIndex = categoryIndex;
    }

    @Override
    public void addButton(@Nonnull UICommandBuilder commands,
                          @Nonnull UIEventBuilder events,
                          @Nonnull String selector,
                          @Nonnull PlayerRef playerRef) {

        commands.append("#CategoryList", "Pages/JanielPortal/CategoryButton.ui");
        commands.set(selector + " #Name.Text", label);
    }

    public int getCategoryIndex() {
        return categoryIndex;
    }
}
