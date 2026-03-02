package com.janiel.hytale.travel.mutations.weapon.effects;

/**
 * Central definitions for weapon effect tuning.
 * Keep numbers here so systems remain simple and consistent.
 */
public final class WeaponEffectDefinitions {

    private WeaponEffectDefinitions() {
    }

    /**
     * ID is the filename (without .json) inside:
     *   Server/Entity/Effects/Status/<id>.json
     */
    public static String vulnerableEffectId() {
        return "Janiel_Vulnerable_Tag";
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
     * based on the attacker's sword level (1..3).
     */
    public static float damageTakenMultiplierForSwordLevel(int level) {
        if (level <= 0) return 1.0f;
        if (level == 1) return 1.10f;
        if (level == 2) return 1.20f;
        return 1.30f; // level >= 3
    }
}