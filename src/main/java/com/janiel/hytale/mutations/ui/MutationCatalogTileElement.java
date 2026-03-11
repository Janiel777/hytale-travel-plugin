package com.janiel.hytale.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class MutationCatalogTileElement extends ChoiceElement {

    private static final String DEFAULT_ICON_UI_PATH = "Pages/JanielMenu/MutationCatalogTileIconPlaceholder.ui";

    private final String iconUiPath;
    private final int level;
    private final String containerSelector;

    public MutationCatalogTileElement(
            @Nonnull String name,
            @Nonnull String iconUiPath,
            int level,
            @Nonnull String containerSelector
    ) {
        super(name, "", new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.iconUiPath = iconUiPath;
        this.level = clampLevel(level);
        this.containerSelector = containerSelector;
    }

    @Override
    public void addButton(
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull String selector,
            @Nonnull PlayerRef playerRef
    ) {
        commands.append(containerSelector, "Pages/JanielMenu/MutationCatalogTile.ui");
        commands.append(selector + " #IconHost", resolveIconUiPath(iconUiPath));
        commands.append(selector + " #LevelRowHost", resolveLevelUiPath(level));
    }

    @Nonnull
    private static String resolveIconUiPath(String iconUiPath) {
        if (iconUiPath == null || iconUiPath.isBlank()) {
            return DEFAULT_ICON_UI_PATH;
        }
        return iconUiPath;
    }

    @Nonnull
    private static String resolveLevelUiPath(int level) {
        return "Pages/JanielMenu/MutationCatalogTileLevel" + clampLevel(level) + ".ui";
    }

    private static int clampLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level >= 3) {
            return 3;
        }
        return level;
    }
}