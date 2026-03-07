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

    // --------------------------------------------------------------------------------------------
    // Mace mastery: Stun (real freeze using Frozen component + optional VFX EntityEffect "Stun")
    // --------------------------------------------------------------------------------------------

    public static String stunEffectId() {
        // Asset filename: Server/Entity/Effects/Status/Stun.json
        return "Stun";
    }

    public static float stunDurationSecondsForMaceLevel(int level) {
        if (level <= 0) return 0.0f;
        if (level == 1) return 2.0f;
        if (level == 2) return 3.0f;
        return 4.0f;
    }

    public static float stunProcChanceForMaceLevel(int level) {
        if (level <= 0) return 0.0f;
        if (level == 1) return 0.15f;
        if (level == 2) return 0.20f;
        return 0.99f;
    }

    // --------------------------------------------------------------------------------------------
// Dagger mastery: Bleed tiers (1..10), decays if not refreshed
// --------------------------------------------------------------------------------------------

    public static int bleedMaxTier() {
        return 10;
    }

    public static float bleedProcChanceForDaggerLevel(int level) {
        return (level > 0) ? 1.0f : 0.0f;
    }

    public static long bleedDurationMsForDaggerLevel(int level) {
        // Hard cap: even if something goes wrong, bleed cannot live forever.
        // Also prevents map entries from surviving too long on edge cases.
        if (level <= 0) return 0L;
        if (level == 1) return 12000L;
        if (level == 2) return 15000L;
        return 18000L;
    }

    public static long bleedTickIntervalMs() {
        // 4 ticks per second
        return 250L;
    }

    public static long bleedDecayGraceMs() {
        // No grace: decay schedule is driven by a fixed 2s step from last refresh.
        return 0L;
    }

    public static long bleedDecayStepMs() {
        // Every 2 seconds without refresh, lose 1 tier.
        return 2000L;
    }

    public static float bleedDamagePerTick(int daggerLevel, int tier) {
        if (daggerLevel <= 0) return 0.0f;
        if (tier <= 0) return 0.0f;

        // Design:
        // - Tier drives bleed intensity (stack).
        // - Dagger mastery level scales the intensity.
        // - Target baseline: moderate DPS, requires sustained hits for high tiers.

        // Base DPS per tier at level 1.
        float baseDpsPerTier = 0.50f; // Tier 4 ~= 2 DPS at level 1

        float levelMultiplier;
        if (daggerLevel == 1) levelMultiplier = 1.00f;
        else if (daggerLevel == 2) levelMultiplier = 1.25f;
        else levelMultiplier = 1.50f;

        float dps = (baseDpsPerTier * (float) tier) * levelMultiplier;

        // Convert DPS into damage-per-tick.
        float ticksPerSecond = 1000.0f / (float) bleedTickIntervalMs(); // 4.0f
        return dps / ticksPerSecond;
    }


    // --------------------------------------------------------------------------------------------
    // Spear mastery: Knockback (keep distance)
    // --------------------------------------------------------------------------------------------

    public static float knockbackProcChanceForSpearLevel(int level) {
        if (level <= 0) return 0.0f;
        if (level == 1) return 0.50f;
        if (level == 2) return 0.75f;
        return 1.0f;
    }

    public static float knockbackForceForSpearLevel(int level) {
        return (level <= 0) ? 0.0f : 0.08f;
    }

    public static float knockbackDurationSecondsForSpearLevel(int level) {
        return (level <= 0) ? 0.0f : 0.1f;
    }

    public static float knockbackForceMultiplierWhenProjectile(int level){
        return (level <= 0) ? 0.0f : 1.2f;
    }
}