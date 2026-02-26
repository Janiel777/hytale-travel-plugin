package com.janiel.hytale.travel.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Custom page that lists player mutations.
 *
 * Commit 1 goal: show the page with static content.
 * Persistence + mining detection will be added in later commits.
 */
public final class MutationsPage extends ChoiceBasePage {

    private MutationsPage(@Nonnull PlayerRef playerRef, @Nonnull ChoiceElement[] elements) {
        super(playerRef, elements, "Pages/JanielMutations/MutationsHome.ui");
    }

    public static MutationsPage create(@Nonnull PlayerRef playerRef) {

        // Static data for now.
        // Later: load from MutationsRepository (JSON) and compute level/progress.

        int blocksMined = 0;
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