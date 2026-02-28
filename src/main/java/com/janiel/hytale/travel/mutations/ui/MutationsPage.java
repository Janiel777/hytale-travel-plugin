package com.janiel.hytale.travel.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.travel.mutations.MutationsProgression;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;

import javax.annotation.Nonnull;

public final class MutationsPage extends ChoiceBasePage {

    private MutationsPage(@Nonnull PlayerRef playerRef, @Nonnull ChoiceElement[] elements) {
        super(playerRef, elements, "Pages/JanielMutations/MutationsHome.ui");
    }

    public static MutationsPage create(@Nonnull PlayerRef playerRef) {

        MutationsState state = MutationsRepository.getOrLoadState(playerRef.getUuid());

        int blocksMined = state.getBlocksBroken();
        int miningLevel = state.getMiningLevel();
        int nextMiningLevelAt = MutationsProgression.miningGoalForLevel(miningLevel);

        String miningName = "Stonecutter's Pace";
        String miningDescription = "Mine blocks to permanently increase mining speed.";
        String miningLevelLabel = "Level " + miningLevel;
        String miningProgressLabel = blocksMined + "/" + nextMiningLevelAt + " blocks";

        int staminaDepletions = state.getStaminaDepletions();
        int staminaDelayLevel = state.getStaminaDelayLevel();
        int nextStaminaLevelAt = MutationsProgression.staminaDepletionsGoalForLevel(staminaDelayLevel);

        int extraDelaySeconds = MutationsProgression.staminaExtraDelaySecondsForLevel(staminaDelayLevel);

        String staminaName = "Stamina Recovery";
        String staminaDescription = "Deplete stamina to reduce the regen delay penalty.";
        String staminaLevelLabel = "Level " + staminaDelayLevel;

        String staminaProgressLabel;
        if (staminaDelayLevel >= 3) {
            staminaProgressLabel = staminaDepletions + " depletions (no extra delay)";
        } else {
            staminaProgressLabel = staminaDepletions + "/" + nextStaminaLevelAt
                    + " depletions (-" + extraDelaySeconds + "s extra delay)";
        }

        ChoiceElement[] elements = new ChoiceElement[] {
                new MutationEntryElement(miningName, miningDescription, miningLevelLabel, miningProgressLabel),
                new MutationEntryElement(staminaName, staminaDescription, staminaLevelLabel, staminaProgressLabel)
        };

        return new MutationsPage(playerRef, elements);
    }
}