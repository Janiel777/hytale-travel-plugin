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
}