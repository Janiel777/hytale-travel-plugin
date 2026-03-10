package com.janiel.hytale.mutations.weapon.effects;

public final class BowPerfectShotDefinitions {

    private BowPerfectShotDefinitions() {
    }

    /**
     * Actual shortbow max-damage charge time from the weapon chain.
     */
    public static float fullChargeSeconds() {
        return 1.20f;
    }

    /**
     * Visual HUD timeline.
     * The bar keeps going after actual max damage so the player can
     * see both the perfect-shot window and the overshoot phase.
     */
    public static float visualTimelineSeconds() {
        return 2.00f;
    }

    /**
     * Perfect shot should happen after actual max damage is already reached.
     */
    public static float perfectShotMinSeconds() {
        return 1.20f;
    }

    public static float perfectShotMaxSeconds() {
        return 1.30f;
    }

    /**
     * Normalized against the visual timeline (1.5s).
     */
    public static float perfectShotMinNormalized() {
        return clamp01(perfectShotMinSeconds() / visualTimelineSeconds());
    }

    public static float perfectShotMaxNormalized() {
        return clamp01(perfectShotMaxSeconds() / visualTimelineSeconds());
    }

    public static float perfectShotDamageMultiplier() {
        return 1.50f;
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
        float value = clamp01(normalizedCharge);
        return value >= perfectShotMinNormalized() && value <= perfectShotMaxNormalized();
    }

    public static boolean isPerfectShotSeconds(float chargeSeconds) {
        float value = clampNonNegative(chargeSeconds);
        return value >= perfectShotMinSeconds() && value <= perfectShotMaxSeconds();
    }

    public static int toPercent(float normalizedCharge) {
        return Math.round(clamp01(normalizedCharge) * 100.0f);
    }

    public static String perfectWindowLabel() {
        return toPercent(perfectShotMinNormalized()) + "%-" + toPercent(perfectShotMaxNormalized()) + "%";
    }
}