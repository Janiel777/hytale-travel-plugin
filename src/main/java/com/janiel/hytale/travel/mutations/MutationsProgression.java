package com.janiel.hytale.travel.mutations;

public final class MutationsProgression {

    private MutationsProgression() {
    }

    public static int computeMiningLevel(int blocksBroken) {
        if (blocksBroken < 100) {
            return 0;
        }
        if (blocksBroken < 500) {
            return 1;
        }
        if (blocksBroken < 2000) {
            return 2;
        }
        return 3;
    }

    public static int miningGoalForLevel(int level) {
        if (level <= 0) {
            return 100;
        }
        if (level == 1) {
            return 500;
        }
        return 2000;
    }

    public static float miningDamageMultiplierForLevel(int level) {
        if (level <= 0) {
            return 1.00f;
        }
        if (level == 1) {
            return 1.30f;
        }
        if (level == 2) {
            return 1.50f;
        }
        return 1.70f;
    }

    // -----------------------------
    // Stamina Recovery progression
    // -----------------------------

    /**
     * Compute stamina delay level based on how many times the player fully depleted stamina.
     *
     * Level rules:
     * 0: < 10 depletions
     * 1: < 25 depletions
     * 2: < 50 depletions
     * 3: 50+ depletions
     */
    public static int computeStaminaDelayLevel(int staminaDepletions) {
        if (staminaDepletions < 10) {
            return 0;
        }
        if (staminaDepletions < 25) {
            return 1;
        }
        if (staminaDepletions < 50) {
            return 2;
        }
        return 3;
    }

    public static int staminaDepletionsGoalForLevel(int level) {
        if (level <= 0) {
            return 10;
        }
        if (level == 1) {
            return 25;
        }
        return 50;
    }

    /**
     * Extra seconds added to the regen delay stat on depletion.
     * Level 3 returns 0 to represent "vanilla behavior" (no extra delay).
     */
    public static int staminaExtraDelaySecondsForLevel(int level) {
        if (level <= 0) {
            return 3;
        }
        if (level == 1) {
            return 2;
        }
        if (level == 2) {
            return 1;
        }
        return 0;
    }

    /**
     * Multiplier applied to the observed positive stamina regeneration.
     *
     * Proposed:
     * Level 0 -> 1.00x
     * Level 1 -> 1.20x
     * Level 2 -> 1.40x
     * Level 3 -> 1.60x
     */
    public static float staminaRegenSpeedMultiplierForLevel(int level) {
        if (level <= 0) {
            return 1.00f;
        }
        if (level == 1) {
            return 1.20f;
        }
        if (level == 2) {
            return 1.40f;
        }
        return 1.60f;
    }

    // -----------------------------
    // Weapon Kill progression
    // -----------------------------

    // NOTE: thresholds are intentionally duplicated per weapon type
    // so balance can diverge later without changing call sites.
    private static final int SWORD_LEVEL_1_KILLS = 50;
    private static final int SWORD_LEVEL_2_KILLS = 150;
    private static final int SWORD_LEVEL_3_KILLS = 300;

    private static final int AXE_LEVEL_1_KILLS = 50;
    private static final int AXE_LEVEL_2_KILLS = 150;
    private static final int AXE_LEVEL_3_KILLS = 300;

    private static final int MACE_LEVEL_1_KILLS = 50;
    private static final int MACE_LEVEL_2_KILLS = 150;
    private static final int MACE_LEVEL_3_KILLS = 300;

    private static final int SPEAR_LEVEL_1_KILLS = 50;
    private static final int SPEAR_LEVEL_2_KILLS = 150;
    private static final int SPEAR_LEVEL_3_KILLS = 300;

    private static final int DAGGER_LEVEL_1_KILLS = 50;
    private static final int DAGGER_LEVEL_2_KILLS = 150;
    private static final int DAGGER_LEVEL_3_KILLS = 300;

    private static final int BOW_LEVEL_1_KILLS = 50;
    private static final int BOW_LEVEL_2_KILLS = 150;
    private static final int BOW_LEVEL_3_KILLS = 300;

    private static final int CROSSBOW_LEVEL_1_KILLS = 50;
    private static final int CROSSBOW_LEVEL_2_KILLS = 150;
    private static final int CROSSBOW_LEVEL_3_KILLS = 300;

    private static final int GUN_LEVEL_1_KILLS = 50;
    private static final int GUN_LEVEL_2_KILLS = 150;
    private static final int GUN_LEVEL_3_KILLS = 300;

    private static final int MAGIC_LEVEL_1_KILLS = 50;
    private static final int MAGIC_LEVEL_2_KILLS = 150;
    private static final int MAGIC_LEVEL_3_KILLS = 300;

    private static final int THROWABLE_LEVEL_1_KILLS = 50;
    private static final int THROWABLE_LEVEL_2_KILLS = 150;
    private static final int THROWABLE_LEVEL_3_KILLS = 300;

    public static int computeSwordWeaponLevel(int swordKills) {
        return computeWeaponLevel(swordKills, SWORD_LEVEL_1_KILLS, SWORD_LEVEL_2_KILLS, SWORD_LEVEL_3_KILLS);
    }

    public static int computeAxeWeaponLevel(int axeKills) {
        return computeWeaponLevel(axeKills, AXE_LEVEL_1_KILLS, AXE_LEVEL_2_KILLS, AXE_LEVEL_3_KILLS);
    }

    public static int computeMaceWeaponLevel(int maceKills) {
        return computeWeaponLevel(maceKills, MACE_LEVEL_1_KILLS, MACE_LEVEL_2_KILLS, MACE_LEVEL_3_KILLS);
    }

    public static int computeSpearWeaponLevel(int spearKills) {
        return computeWeaponLevel(spearKills, SPEAR_LEVEL_1_KILLS, SPEAR_LEVEL_2_KILLS, SPEAR_LEVEL_3_KILLS);
    }

    public static int computeDaggerWeaponLevel(int daggerKills) {
        return computeWeaponLevel(daggerKills, DAGGER_LEVEL_1_KILLS, DAGGER_LEVEL_2_KILLS, DAGGER_LEVEL_3_KILLS);
    }

    public static int computeBowWeaponLevel(int bowKills) {
        return computeWeaponLevel(bowKills, BOW_LEVEL_1_KILLS, BOW_LEVEL_2_KILLS, BOW_LEVEL_3_KILLS);
    }

    public static int computeCrossbowWeaponLevel(int crossbowKills) {
        return computeWeaponLevel(crossbowKills, CROSSBOW_LEVEL_1_KILLS, CROSSBOW_LEVEL_2_KILLS, CROSSBOW_LEVEL_3_KILLS);
    }

    public static int computeGunWeaponLevel(int gunKills) {
        return computeWeaponLevel(gunKills, GUN_LEVEL_1_KILLS, GUN_LEVEL_2_KILLS, GUN_LEVEL_3_KILLS);
    }

    public static int computeMagicWeaponLevel(int magicKills) {
        return computeWeaponLevel(magicKills, MAGIC_LEVEL_1_KILLS, MAGIC_LEVEL_2_KILLS, MAGIC_LEVEL_3_KILLS);
    }

    public static int computeThrowableWeaponLevel(int throwableKills) {
        return computeWeaponLevel(throwableKills, THROWABLE_LEVEL_1_KILLS, THROWABLE_LEVEL_2_KILLS, THROWABLE_LEVEL_3_KILLS);
    }

    private static int computeWeaponLevel(int kills, int level1Kills, int level2Kills, int level3Kills) {
        if (kills < level1Kills) {
            return 0;
        }
        if (kills < level2Kills) {
            return 1;
        }
        if (kills < level3Kills) {
            return 2;
        }
        return 3;
    }
}