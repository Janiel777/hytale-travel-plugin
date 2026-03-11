package com.janiel.hytale.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.mutations.MutationsProgression;
import com.janiel.hytale.mutations.persistence.MutationsRepository;
import com.janiel.hytale.mutations.persistence.MutationsState;

import javax.annotation.Nonnull;

public final class MutationsPage extends ChoiceBasePage {

    private MutationsPage(@Nonnull PlayerRef playerRef, @Nonnull ChoiceElement[] elements) {
        super(playerRef, elements, "Pages/JanielMutations/MutationsHome.ui");
    }

    public static MutationsPage create(@Nonnull PlayerRef playerRef) {
        return new MutationsPage(playerRef, createElements(playerRef, "#ElementList"));
    }

    public static ChoiceElement[] createElements(
            @Nonnull PlayerRef playerRef,
            @Nonnull String containerSelector
    ) {
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
        float regenMultiplier = MutationsProgression.staminaRegenSpeedMultiplierForLevel(staminaDelayLevel);

        String staminaName = "Stamina Recovery";
        String staminaDescription = "Deplete stamina to reduce the regen delay penalty and increase regen speed.";
        String staminaLevelLabel = "Level " + staminaDelayLevel;

        String staminaProgressLabel;
        if (staminaDelayLevel >= 3) {
            staminaProgressLabel = staminaDepletions + " depletions (x" + regenMultiplier + " regen, no extra delay)";
        } else {
            staminaProgressLabel = staminaDepletions + "/" + nextStaminaLevelAt
                    + " depletions (-" + extraDelaySeconds + "s extra delay, x" + regenMultiplier + " regen)";
        }

        int swordKills = state.getSwordKills();
        int swordLevel = state.getSwordLevel();
        int swordNextAt = MutationsProgression.swordKillsGoalForLevel(swordLevel);

        String swordName = "Sword Mastery";
        String swordDescription = "Defeat enemies with swords to increase sword proficiency.";
        String swordLevelLabel = "Level " + swordLevel;
        String swordProgressLabel = (swordLevel >= 3)
                ? (swordKills + " kills (MAX)")
                : (swordKills + "/" + swordNextAt + " kills");

        int axeKills = state.getAxeKills();
        int axeLevel = state.getAxeLevel();
        int axeNextAt = MutationsProgression.axeKillsGoalForLevel(axeLevel);

        String axeName = "Axe Mastery";
        String axeDescription = "Defeat enemies with axes to increase axe proficiency.";
        String axeLevelLabel = "Level " + axeLevel;
        String axeProgressLabel = (axeLevel >= 3)
                ? (axeKills + " kills (MAX)")
                : (axeKills + "/" + axeNextAt + " kills");

        int maceKills = state.getMaceKills();
        int maceLevel = state.getMaceLevel();
        int maceNextAt = MutationsProgression.maceKillsGoalForLevel(maceLevel);

        String maceName = "Mace Mastery";
        String maceDescription = "Defeat enemies with maces to increase blunt weapon proficiency.";
        String maceLevelLabel = "Level " + maceLevel;
        String maceProgressLabel = (maceLevel >= 3)
                ? (maceKills + " kills (MAX)")
                : (maceKills + "/" + maceNextAt + " kills");

        int spearKills = state.getSpearKills();
        int spearLevel = state.getSpearLevel();
        int spearNextAt = MutationsProgression.spearKillsGoalForLevel(spearLevel);

        String spearName = "Spear Mastery";
        String spearDescription = "Defeat enemies with spears to increase spear proficiency.";
        String spearLevelLabel = "Level " + spearLevel;
        String spearProgressLabel = (spearLevel >= 3)
                ? (spearKills + " kills (MAX)")
                : (spearKills + "/" + spearNextAt + " kills");

        int daggerKills = state.getDaggerKills();
        int daggerLevel = state.getDaggerLevel();
        int daggerNextAt = MutationsProgression.daggerKillsGoalForLevel(daggerLevel);

        String daggerName = "Dagger Mastery";
        String daggerDescription = "Defeat enemies with daggers to increase dagger proficiency.";
        String daggerLevelLabel = "Level " + daggerLevel;
        String daggerProgressLabel = (daggerLevel >= 3)
                ? (daggerKills + " kills (MAX)")
                : (daggerKills + "/" + daggerNextAt + " kills");

        int bowKills = state.getBowKills();
        int bowLevel = state.getBowLevel();
        int bowNextAt = MutationsProgression.bowKillsGoalForLevel(bowLevel);

        String bowName = "Bow Mastery";
        String bowDescription = "Defeat enemies with bows to increase bow proficiency.";
        String bowLevelLabel = "Level " + bowLevel;
        String bowProgressLabel = (bowLevel >= 3)
                ? (bowKills + " kills (MAX)")
                : (bowKills + "/" + bowNextAt + " kills");

        int crossbowKills = state.getCrossbowKills();
        int crossbowLevel = state.getCrossbowLevel();
        int crossbowNextAt = MutationsProgression.crossbowKillsGoalForLevel(crossbowLevel);

        String crossbowName = "Crossbow Mastery";
        String crossbowDescription = "Defeat enemies with crossbows to increase crossbow proficiency.";
        String crossbowLevelLabel = "Level " + crossbowLevel;
        String crossbowProgressLabel = (crossbowLevel >= 3)
                ? (crossbowKills + " kills (MAX)")
                : (crossbowKills + "/" + crossbowNextAt + " kills");

        int gunKills = state.getGunKills();
        int gunLevel = state.getGunLevel();
        int gunNextAt = MutationsProgression.gunKillsGoalForLevel(gunLevel);

        String gunName = "Gun Mastery";
        String gunDescription = "Defeat enemies with guns to increase firearm proficiency.";
        String gunLevelLabel = "Level " + gunLevel;
        String gunProgressLabel = (gunLevel >= 3)
                ? (gunKills + " kills (MAX)")
                : (gunKills + "/" + gunNextAt + " kills");

        int magicKills = state.getMagicKills();
        int magicLevel = state.getMagicLevel();
        int magicNextAt = MutationsProgression.magicKillsGoalForLevel(magicLevel);

        String magicName = "Magic Mastery";
        String magicDescription = "Defeat enemies with magic weapons to increase magical proficiency.";
        String magicLevelLabel = "Level " + magicLevel;
        String magicProgressLabel = (magicLevel >= 3)
                ? (magicKills + " kills (MAX)")
                : (magicKills + "/" + magicNextAt + " kills");

        int throwableKills = state.getThrowableKills();
        int throwableLevel = state.getThrowableLevel();
        int throwableNextAt = MutationsProgression.throwableKillsGoalForLevel(throwableLevel);

        String throwableName = "Throwable Mastery";
        String throwableDescription = "Defeat enemies with throwables to increase throwable proficiency.";
        String throwableLevelLabel = "Level " + throwableLevel;
        String throwableProgressLabel = (throwableLevel >= 3)
                ? (throwableKills + " kills (MAX)")
                : (throwableKills + "/" + throwableNextAt + " kills");

        return new ChoiceElement[] {
                new MutationEntryElement(miningName, miningDescription, miningLevelLabel, miningProgressLabel, containerSelector),
                new MutationEntryElement(staminaName, staminaDescription, staminaLevelLabel, staminaProgressLabel, containerSelector),

                new MutationEntryElement(swordName, swordDescription, swordLevelLabel, swordProgressLabel, containerSelector),
                new MutationEntryElement(axeName, axeDescription, axeLevelLabel, axeProgressLabel, containerSelector),
                new MutationEntryElement(maceName, maceDescription, maceLevelLabel, maceProgressLabel, containerSelector),
                new MutationEntryElement(spearName, spearDescription, spearLevelLabel, spearProgressLabel, containerSelector),
                new MutationEntryElement(daggerName, daggerDescription, daggerLevelLabel, daggerProgressLabel, containerSelector),
                new MutationEntryElement(bowName, bowDescription, bowLevelLabel, bowProgressLabel, containerSelector),
                new MutationEntryElement(crossbowName, crossbowDescription, crossbowLevelLabel, crossbowProgressLabel, containerSelector),
                new MutationEntryElement(gunName, gunDescription, gunLevelLabel, gunProgressLabel, containerSelector),
                new MutationEntryElement(magicName, magicDescription, magicLevelLabel, magicProgressLabel, containerSelector),
                new MutationEntryElement(throwableName, throwableDescription, throwableLevelLabel, throwableProgressLabel, containerSelector)
        };
    }
}