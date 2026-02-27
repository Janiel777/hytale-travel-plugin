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
}