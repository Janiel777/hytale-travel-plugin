package com.janiel.hytale.travel.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;

import javax.annotation.Nonnull;

public final class MutationsPage extends ChoiceBasePage {

    private MutationsPage(@Nonnull PlayerRef playerRef, @Nonnull ChoiceElement[] elements) {
        super(playerRef, elements, "Pages/JanielMutations/MutationsHome.ui");
    }

    public static MutationsPage create(@Nonnull PlayerRef playerRef) {

        int blocksMined = MutationsRepository.getBlocksBroken(playerRef.getUuid());
        int nextLevelAt = 20;

        String name = "Stonecutter's Pace";
        String description = "Mine blocks to permanently increase mining speed.";
        String levelLabel = "Level 0";
        String progressLabel = blocksMined + "/" + nextLevelAt + " blocks";

        ChoiceElement[] elements = new ChoiceElement[] {
                new MutationEntryElement(name, description, levelLabel, progressLabel)
        };

        return new MutationsPage(playerRef, elements);
    }
}