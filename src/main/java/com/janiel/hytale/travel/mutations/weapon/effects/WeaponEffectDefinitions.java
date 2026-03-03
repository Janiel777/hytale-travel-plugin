package com.janiel.hytale.travel.mutations.weapon.effects;

/**
 * Central definitions for weapon effect tuning.
 * Keep numbers here so systems remain simple and consistent.
 */
public final class WeaponEffectDefinitions {

    private WeaponEffectDefinitions() {
    }

    /**
     * Base ID is the filename (without .json) inside:
     *   Server/Entity/Effects/Status/<id>.json
     */
    public static String vulnerableEffectBaseId() {
        return "Janiel_Vulnerable_Tag";
    }

    /**
     * Level-specific vulnerable tag ids.
     * The tier is encoded in the effect id itself (T1/T2/T3).
     */
    public static String vulnerableEffectIdForSwordLevel(int level) {
        if (level <= 0) {
            return vulnerableEffectBaseId();
        }
        if (level == 1) return vulnerableEffectBaseId() + "_T1";
        if (level == 2) return vulnerableEffectBaseId() + "_T2";
        return vulnerableEffectBaseId() + "_T3";
    }

    /**
     * How long the vulnerable tag lasts.
     * IMPORTANT: This should match (or be compatible with) the Duration in the JSON asset.
     */
    public static float vulnerableDurationSeconds() {
        return 5.0f;
    }

    public static long vulnerableDurationMs() {
        return 5000L;
    }

    /**
     * Damage taken multiplier applied to victims while the tag is active,
     * based on the tier encoded by the effect id (T1,T2,T3).
     */
    public static float damageTakenMultiplierForSwordLevel(int level) {
        if (level <= 0) return 1.0f;
        if (level == 1) return 1.10f;
        if (level == 2) return 1.20f;
        return 1.30f; // level >= 3
    }

    // --------------------------------------------------------------------------------------------
    // Battleaxe mastery: Weaken (attacker deals less damage)
    // --------------------------------------------------------------------------------------------

    public static String weakenEffectBaseId() {
        return "Janiel_Weaken_Tag";
    }

    public static String weakenEffectIdForAxeLevel(int level) {
        if (level <= 0) {
            return weakenEffectBaseId();
        }
        if (level == 1) return weakenEffectBaseId() + "_T1";
        if (level == 2) return weakenEffectBaseId() + "_T2";
        return weakenEffectBaseId() + "_T3";
    }

    public static float weakenDurationSecondsForAxeLevel(int level) {
        if (level <= 0) return 0.0f;
        if (level == 1) return 5.0f;
        if (level == 2) return 10.0f;
        return 15f;
    }

    public static float damageDealtMultiplierWhileWeakened(int level) {
        if (level <= 0) return 1.0f;
        if (level == 1) return 0.85f;
        if (level == 2) return 0.80f;
        return 0.75f;
    }
}