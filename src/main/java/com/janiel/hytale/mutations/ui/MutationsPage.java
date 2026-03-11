package com.janiel.hytale.mutations.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.mutations.MutationsProgression;
import com.janiel.hytale.mutations.persistence.MutationsRepository;
import com.janiel.hytale.mutations.persistence.MutationsState;
import com.janiel.hytale.mutations.weapon.effects.BowPerfectShotDefinitions;
import com.janiel.hytale.mutations.weapon.effects.WeaponEffectDefinitions;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MutationsPage extends ChoiceBasePage {

    private static final String DEFAULT_CATALOG_ICON_UI_PATH = "Pages/JanielMenu/MutationCatalogTileIconPlaceholder.ui";

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
        MutationViewData[] views = createViewData(playerRef);
        ChoiceElement[] elements = new ChoiceElement[views.length];

        for (int i = 0; i < views.length; i++) {
            MutationViewData view = views[i];
            elements[i] = new MutationEntryElement(
                    view.getName(),
                    view.getDescription(),
                    "Level " + view.getLevel(),
                    view.getProgressLabel(),
                    containerSelector
            );
        }

        return elements;
    }

    public static MutationViewData[] createViewData(@Nonnull PlayerRef playerRef) {
        MutationsState state = MutationsRepository.getOrLoadState(playerRef.getUuid());
        List<MutationViewData> views = new ArrayList<>();

        views.add(buildMiningView(state));
        views.add(buildStaminaView(state));

        views.add(buildSwordView(state));
        views.add(buildAxeView(state));
        views.add(buildMaceView(state));
        views.add(buildSpearView(state));
        views.add(buildDaggerView(state));

        views.add(buildBowView(state));
        views.add(buildCrossbowView(state));
        views.add(buildGunView(state));

        views.add(buildMagicView(state));
        views.add(buildThrowableView(state));

        return views.toArray(new MutationViewData[0]);
    }

    public static final class MutationViewData {

        private final String key;
        private final String catalogLabel;
        private final String shortCode;
        private final String catalogRowSelector;
        private final String iconUiPath;
        private final String name;
        private final String description;
        private final int level;
        private final String progressLabel;
        private final String tierLine1;
        private final String tierLine2;
        private final String tierLine3;
        private final String summaryLine1;
        private final String summaryLine2;
        private final String summaryLine3;

        public MutationViewData(
                @Nonnull String key,
                @Nonnull String catalogLabel,
                @Nonnull String shortCode,
                @Nonnull String catalogRowSelector,
                @Nonnull String name,
                @Nonnull String description,
                int level,
                @Nonnull String progressLabel,
                @Nonnull String tierLine1,
                @Nonnull String tierLine2,
                @Nonnull String tierLine3,
                @Nonnull String summaryLine1,
                @Nonnull String summaryLine2,
                @Nonnull String summaryLine3
        ) {
            this(
                    key,
                    catalogLabel,
                    shortCode,
                    catalogRowSelector,
                    DEFAULT_CATALOG_ICON_UI_PATH,
                    name,
                    description,
                    level,
                    progressLabel,
                    tierLine1,
                    tierLine2,
                    tierLine3,
                    summaryLine1,
                    summaryLine2,
                    summaryLine3
            );
        }

        public MutationViewData(
                @Nonnull String key,
                @Nonnull String catalogLabel,
                @Nonnull String shortCode,
                @Nonnull String catalogRowSelector,
                @Nonnull String iconUiPath,
                @Nonnull String name,
                @Nonnull String description,
                int level,
                @Nonnull String progressLabel,
                @Nonnull String tierLine1,
                @Nonnull String tierLine2,
                @Nonnull String tierLine3,
                @Nonnull String summaryLine1,
                @Nonnull String summaryLine2,
                @Nonnull String summaryLine3
        ) {
            this.key = key;
            this.catalogLabel = catalogLabel;
            this.shortCode = shortCode;
            this.catalogRowSelector = catalogRowSelector;
            this.iconUiPath = iconUiPath;
            this.name = name;
            this.description = description;
            this.level = level;
            this.progressLabel = progressLabel;
            this.tierLine1 = tierLine1;
            this.tierLine2 = tierLine2;
            this.tierLine3 = tierLine3;
            this.summaryLine1 = summaryLine1;
            this.summaryLine2 = summaryLine2;
            this.summaryLine3 = summaryLine3;
        }

        @Nonnull
        public String getKey() {
            return key;
        }

        @Nonnull
        public String getCatalogLabel() {
            return catalogLabel;
        }

        @Nonnull
        public String getShortCode() {
            return shortCode;
        }

        @Nonnull
        public String getCatalogRowSelector() {
            return catalogRowSelector;
        }

        @Nonnull
        public String getIconUiPath() {
            return iconUiPath;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        @Nonnull
        public String getDescription() {
            return description;
        }

        public int getLevel() {
            return level;
        }

        @Nonnull
        public String getProgressLabel() {
            return progressLabel;
        }

        @Nonnull
        public String getTierLine1() {
            return tierLine1;
        }

        @Nonnull
        public String getTierLine2() {
            return tierLine2;
        }

        @Nonnull
        public String getTierLine3() {
            return tierLine3;
        }

        @Nonnull
        public String getSummaryLine1() {
            return summaryLine1;
        }

        @Nonnull
        public String getSummaryLine2() {
            return summaryLine2;
        }

        @Nonnull
        public String getSummaryLine3() {
            return summaryLine3;
        }
    }

    @Nonnull
    private static MutationViewData buildMiningView(@Nonnull MutationsState state) {
        int current = state.getBlocksBroken();
        int level = state.getMiningLevel();
        int nextGoal = MutationsProgression.miningGoalForLevel(level);

        return new MutationViewData(
                "mining",
                "Mining",
                "MIN",
                "#FoundationsRow",
                "Stonecutter's Pace",
                "Mine blocks to permanently increase mining speed.",
                level,
                progressLabel(current, nextGoal, level, "blocks"),
                tierLine(current, 100, "Tier 1 - Mine 100 blocks"),
                tierLine(current, 500, "Tier 2 - Mine 500 blocks"),
                tierLine(current, 2000, "Tier 3 - Mine 2000 blocks"),
                "Current mining multiplier: x" + formatFloat(MutationsProgression.miningDamageMultiplierForLevel(level)),
                nextTierLine(level, nextGoal, "blocks"),
                "This track caps at level 3."
        );
    }

    @Nonnull
    private static MutationViewData buildStaminaView(@Nonnull MutationsState state) {
        int current = state.getStaminaDepletions();
        int level = state.getStaminaDelayLevel();
        int nextGoal = MutationsProgression.staminaDepletionsGoalForLevel(level);
        int extraDelaySeconds = MutationsProgression.staminaExtraDelaySecondsForLevel(level);
        float regenMultiplier = MutationsProgression.staminaRegenSpeedMultiplierForLevel(level);

        String currentSummary;
        if (level >= 3) {
            currentSummary = "Current regen: x" + formatFloat(regenMultiplier) + ", no extra delay.";
        } else {
            currentSummary = "Current regen: x" + formatFloat(regenMultiplier) + ", +" + extraDelaySeconds + "s extra delay.";
        }

        return new MutationViewData(
                "stamina",
                "Stamina",
                "STA",
                "#FoundationsRow",
                "Stamina Recovery",
                "Deplete stamina to reduce the regen delay penalty and increase regen speed.",
                level,
                progressLabel(current, nextGoal, level, "depletions"),
                tierLine(current, 10, "Tier 1 - Reach 10 stamina depletions"),
                tierLine(current, 25, "Tier 2 - Reach 25 stamina depletions"),
                tierLine(current, 50, "Tier 3 - Reach 50 stamina depletions"),
                currentSummary,
                nextTierLine(level, nextGoal, "depletions"),
                "Built from full stamina depletion events."
        );
    }

    @Nonnull
    private static MutationViewData buildSwordView(@Nonnull MutationsState state) {
        int current = state.getSwordKills();
        int level = state.getSwordLevel();
        int nextGoal = MutationsProgression.swordKillsGoalForLevel(level);

        String currentSummary = level <= 0
                ? "Unlock at level 1: mark targets to take extra damage."
                : "Current tag bonus: +" + toPercentDelta(WeaponEffectDefinitions.damageTakenMultiplierForSwordLevel(level))
                + "% damage taken for "
                + formatFloat(WeaponEffectDefinitions.vulnerableDurationSeconds()) + "s.";

        return new MutationViewData(
                "sword",
                "Sword",
                "SWD",
                "#MeleeRowOne",
                "Sword Mastery",
                "Defeat enemies with swords to increase sword proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with swords"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with swords"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with swords"),
                currentSummary,
                nextTierLine(level, nextGoal, "kills"),
                "Sword kills feed this mastery track."
        );
    }

    @Nonnull
    private static MutationViewData buildAxeView(@Nonnull MutationsState state) {
        int current = state.getAxeKills();
        int level = state.getAxeLevel();
        int nextGoal = MutationsProgression.axeKillsGoalForLevel(level);

        String currentSummary = level <= 0
                ? "Unlock at level 1: apply weaken on hit."
                : "Current weaken: -" + toPercentReduction(WeaponEffectDefinitions.damageDealtMultiplierWhileWeakened(level))
                + "% damage dealt for "
                + formatFloat(WeaponEffectDefinitions.weakenDurationSecondsForAxeLevel(level)) + "s.";

        return new MutationViewData(
                "axe",
                "Axe",
                "AXE",
                "#MeleeRowOne",
                "Axe Mastery",
                "Defeat enemies with axes to increase axe proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with axes"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with axes"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with axes"),
                currentSummary,
                nextTierLine(level, nextGoal, "kills"),
                "Higher tiers extend weaken uptime."
        );
    }

    @Nonnull
    private static MutationViewData buildMaceView(@Nonnull MutationsState state) {
        int current = state.getMaceKills();
        int level = state.getMaceLevel();
        int nextGoal = MutationsProgression.maceKillsGoalForLevel(level);

        String currentSummary = level <= 0
                ? "Unlock at level 1: gain a stun proc on hit."
                : "Current stun: " + toPercent(WeaponEffectDefinitions.stunProcChanceForMaceLevel(level))
                + "% proc for "
                + formatFloat(WeaponEffectDefinitions.stunDurationSecondsForMaceLevel(level)) + "s.";

        return new MutationViewData(
                "mace",
                "Mace",
                "MAC",
                "#MeleeRowOne",
                "Mace Mastery",
                "Defeat enemies with maces to increase blunt weapon proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with maces"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with maces"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with maces"),
                currentSummary,
                nextTierLine(level, nextGoal, "kills"),
                "Higher tiers improve stun reliability."
        );
    }

    @Nonnull
    private static MutationViewData buildSpearView(@Nonnull MutationsState state) {
        int current = state.getSpearKills();
        int level = state.getSpearLevel();
        int nextGoal = MutationsProgression.spearKillsGoalForLevel(level);

        String currentSummary = level <= 0
                ? "Unlock at level 1: gain a knockback proc on hit."
                : "Current knockback: " + toPercent(WeaponEffectDefinitions.knockbackProcChanceForSpearLevel(level))
                + "% proc, force "
                + formatFloat(WeaponEffectDefinitions.knockbackForceForSpearLevel(level)) + ".";

        return new MutationViewData(
                "spear",
                "Spear",
                "SPR",
                "#MeleeRowOne",
                "Spear Mastery",
                "Defeat enemies with spears to increase spear proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with spears"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with spears"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with spears"),
                currentSummary,
                nextTierLine(level, nextGoal, "kills"),
                "Designed to keep enemies at range after a proc."
        );
    }

    @Nonnull
    private static MutationViewData buildDaggerView(@Nonnull MutationsState state) {
        int current = state.getDaggerKills();
        int level = state.getDaggerLevel();
        int nextGoal = MutationsProgression.daggerKillsGoalForLevel(level);

        String currentSummary = level <= 0
                ? "Unlock at level 1: gain bleed application on hit."
                : "Current bleed duration: " + formatSeconds(WeaponEffectDefinitions.bleedDurationMsForDaggerLevel(level))
                + ", up to " + WeaponEffectDefinitions.bleedMaxTier() + " stacks.";

        return new MutationViewData(
                "dagger",
                "Dagger",
                "DAG",
                "#MeleeRowOne",
                "Dagger Mastery",
                "Defeat enemies with daggers to increase dagger proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with daggers"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with daggers"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with daggers"),
                currentSummary,
                nextTierLine(level, nextGoal, "kills"),
                "Bleed decays over time if it is not refreshed."
        );
    }

    @Nonnull
    private static MutationViewData buildBowView(@Nonnull MutationsState state) {
        int current = state.getBowKills();
        int level = state.getBowLevel();
        int nextGoal = MutationsProgression.bowKillsGoalForLevel(level);

        return new MutationViewData(
                "bow",
                "Bow",
                "BOW",
                "#RangedRowOne",
                "Bow Mastery",
                "Defeat enemies with bows to increase bow proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with bows"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with bows"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with bows"),
                "Current perfect window: " + BowPerfectShotDefinitions.perfectWindowLabel(level) + ".",
                "Current perfect shot damage: x" + formatFloat(BowPerfectShotDefinitions.perfectShotDamageMultiplier(level)),
                nextTierLine(level, nextGoal, "kills")
        );
    }

    @Nonnull
    private static MutationViewData buildCrossbowView(@Nonnull MutationsState state) {
        int current = state.getCrossbowKills();
        int level = state.getCrossbowLevel();
        int nextGoal = MutationsProgression.crossbowKillsGoalForLevel(level);

        return new MutationViewData(
                "crossbow",
                "Crossbow",
                "XBW",
                "#RangedRowOne",
                "Crossbow Mastery",
                "Defeat enemies with crossbows to increase crossbow proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with crossbows"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with crossbows"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with crossbows"),
                "Progression is tracked and persisted.",
                nextTierLine(level, nextGoal, "kills"),
                "No crossbow passive hook is wired yet."
        );
    }

    @Nonnull
    private static MutationViewData buildGunView(@Nonnull MutationsState state) {
        int current = state.getGunKills();
        int level = state.getGunLevel();
        int nextGoal = MutationsProgression.gunKillsGoalForLevel(level);

        return new MutationViewData(
                "gun",
                "Gun",
                "GUN",
                "#RangedRowOne",
                "Gun Mastery",
                "Defeat enemies with guns to increase firearm proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with guns"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with guns"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with guns"),
                "Progression is tracked and persisted.",
                nextTierLine(level, nextGoal, "kills"),
                "No gun passive hook is wired yet."
        );
    }

    @Nonnull
    private static MutationViewData buildMagicView(@Nonnull MutationsState state) {
        int current = state.getMagicKills();
        int level = state.getMagicLevel();
        int nextGoal = MutationsProgression.magicKillsGoalForLevel(level);

        return new MutationViewData(
                "magic",
                "Magic",
                "MAG",
                "#SpecialRowOne",
                "Magic Mastery",
                "Defeat enemies with magic weapons to increase magical proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with magic"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with magic"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with magic"),
                "Progression is tracked and persisted.",
                nextTierLine(level, nextGoal, "kills"),
                "No magic passive hook is wired yet."
        );
    }

    @Nonnull
    private static MutationViewData buildThrowableView(@Nonnull MutationsState state) {
        int current = state.getThrowableKills();
        int level = state.getThrowableLevel();
        int nextGoal = MutationsProgression.throwableKillsGoalForLevel(level);

        return new MutationViewData(
                "throwable",
                "Throwables",
                "THR",
                "#SpecialRowOne",
                "Throwable Mastery",
                "Defeat enemies with throwables to increase throwable proficiency.",
                level,
                progressLabel(current, nextGoal, level, "kills"),
                tierLine(current, 50, "Tier 1 - Defeat 50 enemies with throwables"),
                tierLine(current, 150, "Tier 2 - Defeat 150 enemies with throwables"),
                tierLine(current, 300, "Tier 3 - Defeat 300 enemies with throwables"),
                "Progression is tracked and persisted.",
                nextTierLine(level, nextGoal, "kills"),
                "No throwable passive hook is wired yet."
        );
    }

    @Nonnull
    private static String progressLabel(int current, int nextGoal, int level, @Nonnull String unit) {
        if (level >= 3) {
            return current + " " + unit + " (MAX)";
        }
        return current + "/" + nextGoal + " " + unit;
    }

    private static String tierLine(int current, int goal, @Nonnull String text) {
        return (current >= goal ? "[Complete] " : "[Locked] ") + text;
    }

    @Nonnull
    private static String nextTierLine(int level, int nextGoal, @Nonnull String unit) {
        if (level >= 3) {
            return "Max level reached.";
        }
        return "Next tier unlocks at " + nextGoal + " " + unit + ".";
    }

    @Nonnull
    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Nonnull
    private static String formatSeconds(long milliseconds) {
        return String.format(Locale.US, "%.1fs", milliseconds / 1000.0f);
    }

    private static int toPercent(float probability) {
        return Math.round(probability * 100.0f);
    }

    private static int toPercentDelta(float multiplier) {
        return Math.round((multiplier - 1.0f) * 100.0f);
    }

    private static int toPercentReduction(float multiplier) {
        return Math.round((1.0f - multiplier) * 100.0f);
    }
}