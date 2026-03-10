package com.janiel.hytale.mutations.weapon.effects;

public final class BowPerfectShotDefinitions {

    private static final float FULL_CHARGE_SECONDS = 1.20f;
    private static final float VISUAL_TIMELINE_SECONDS = 2.00f;

    private static final float BASE_PERFECT_SHOT_MIN_SECONDS = 1.20f;
    private static final float BASE_PERFECT_SHOT_MAX_SECONDS = 1.30f;

    private static final float BASE_PERFECT_SHOT_DAMAGE_MULTIPLIER = 1.50f;
    private static final float PERFECT_SHOT_DAMAGE_BONUS_PER_LEVEL = 0.10f;

    /**
     * We keep the left boundary fixed at 60% of the 2.0s HUD bar.
     * Level 0 -> 60%-65%
     * Level 3 -> 60%-70%
     *
     * So the right boundary grows linearly by:
     * (0.70 - 0.65) / 3 = 0.016666667 per level
     */
    private static final float BASE_PERFECT_SHOT_MIN_NORMALIZED = 0.60f;
    private static final float BASE_PERFECT_SHOT_MAX_NORMALIZED = 0.65f;
    private static final float PERFECT_SHOT_MAX_NORMALIZED_BONUS_PER_LEVEL = 0.05f / 3.0f;

    private BowPerfectShotDefinitions() {
    }

    /**
     * Actual shortbow max-damage charge time from the weapon chain.
     */
    public static float fullChargeSeconds() {
        return FULL_CHARGE_SECONDS;
    }

    /**
     * Visual HUD timeline.
     * The bar keeps going after actual max damage so the player can
     * see both the perfect-shot window and the overshoot phase.
     */
    public static float visualTimelineSeconds() {
        return VISUAL_TIMELINE_SECONDS;
    }

    /**
     * Base no-level overloads kept for compatibility.
     * These preserve level 0 behavior.
     */
    public static float perfectShotMinSeconds() {
        return perfectShotMinSeconds(0);
    }

    public static float perfectShotMaxSeconds() {
        return perfectShotMaxSeconds(0);
    }

    public static float perfectShotMinNormalized() {
        return perfectShotMinNormalized(0);
    }

    public static float perfectShotMaxNormalized() {
        return perfectShotMaxNormalized(0);
    }

    public static float perfectShotDamageMultiplier() {
        return perfectShotDamageMultiplier(0);
    }

    /**
     * Level-aware overloads.
     */
    public static float perfectShotMinSeconds(int mutationLevel) {
        return visualTimelineSeconds() * perfectShotMinNormalized(mutationLevel);
    }

    public static float perfectShotMaxSeconds(int mutationLevel) {
        return visualTimelineSeconds() * perfectShotMaxNormalized(mutationLevel);
    }

    public static float perfectShotMinNormalized(int mutationLevel) {
        return BASE_PERFECT_SHOT_MIN_NORMALIZED;
    }

    public static float perfectShotMaxNormalized(int mutationLevel) {
        int level = clampMutationLevel(mutationLevel);
        return clamp01(BASE_PERFECT_SHOT_MAX_NORMALIZED + (PERFECT_SHOT_MAX_NORMALIZED_BONUS_PER_LEVEL * level));
    }

    public static float perfectShotDamageMultiplier(int mutationLevel) {
        int level = clampMutationLevel(mutationLevel);
        return BASE_PERFECT_SHOT_DAMAGE_MULTIPLIER + (PERFECT_SHOT_DAMAGE_BONUS_PER_LEVEL * level);
    }

    public static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }

    public static float clampNonNegative(float value) {
        return Math.max(0.0f, value);
    }

    public static int clampMutationLevel(int mutationLevel) {
        if (mutationLevel <= 0) {
            return 0;
        }
        if (mutationLevel >= 3) {
            return 3;
        }
        return mutationLevel;
    }

    /**
     * Resolves actual max weapon charge time, not the visual HUD timeline.
     */
    public static float resolveMaxChargeSeconds(float observedMaxChargeSeconds) {
        if (observedMaxChargeSeconds > 0.0f) {
            return observedMaxChargeSeconds;
        }
        return fullChargeSeconds();
    }

    /**
     * Normalized against the visual HUD timeline.
     */
    public static float normalizeVisualChargeSeconds(float chargeSeconds) {
        float total = visualTimelineSeconds();
        if (total <= 0.0f) {
            return 0.0f;
        }
        return clamp01(clampNonNegative(chargeSeconds) / total);
    }

    public static boolean isPerfectShotNormalized(float normalizedCharge) {
        return isPerfectShotNormalized(normalizedCharge, 0);
    }

    public static boolean isPerfectShotNormalized(float normalizedCharge, int mutationLevel) {
        float value = clamp01(normalizedCharge);
        return value >= perfectShotMinNormalized(mutationLevel)
                && value <= perfectShotMaxNormalized(mutationLevel);
    }

    public static boolean isPerfectShotSeconds(float chargeSeconds) {
        return isPerfectShotSeconds(chargeSeconds, 0);
    }

    public static boolean isPerfectShotSeconds(float chargeSeconds, int mutationLevel) {
        float value = clampNonNegative(chargeSeconds);
        return value >= perfectShotMinSeconds(mutationLevel)
                && value <= perfectShotMaxSeconds(mutationLevel);
    }

    public static int toPercent(float normalizedCharge) {
        return Math.round(clamp01(normalizedCharge) * 100.0f);
    }

    public static String perfectWindowLabel() {
        return perfectWindowLabel(0);
    }

    public static String perfectWindowLabel(int mutationLevel) {
        return toPercent(perfectShotMinNormalized(mutationLevel))
                + "%-"
                + toPercent(perfectShotMaxNormalized(mutationLevel))
                + "%";
    }
}