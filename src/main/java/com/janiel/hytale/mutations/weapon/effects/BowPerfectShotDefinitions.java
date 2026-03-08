package com.janiel.hytale.mutations.weapon.effects;

public final class BowPerfectShotDefinitions {

    private BowPerfectShotDefinitions() {
    }

    /**
     * Default full charge for the shortbow chain.
     * Runtime will prefer the value observed from ChargingInteraction.highestChargeValue when available.
     */
    public static float fullChargeSeconds() {
        return 1.20f;
    }

    /**
     * Normalized range [0..1].
     * Example: 0.10f = 10%, 0.95f = 95%.
     */
    public static float perfectShotMinNormalized() {
        return 0.10f;
    }

    public static float perfectShotMaxNormalized() {
        return 0.95f;
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

    public static float resolveMaxChargeSeconds(float observedMaxChargeSeconds) {
        if (observedMaxChargeSeconds > 0.0f) {
            return observedMaxChargeSeconds;
        }
        return fullChargeSeconds();
    }

    public static float normalizeChargeSeconds(float chargeSeconds, float maxChargeSeconds) {
        float resolvedMax = resolveMaxChargeSeconds(maxChargeSeconds);
        if (resolvedMax <= 0.0f) {
            return 0.0f;
        }
        return clamp01(clampNonNegative(chargeSeconds) / resolvedMax);
    }

    public static boolean isPerfectShotNormalized(float normalizedCharge) {
        float value = clamp01(normalizedCharge);
        return value >= perfectShotMinNormalized() && value <= perfectShotMaxNormalized();
    }

    public static boolean isPerfectShotSeconds(float chargeSeconds, float maxChargeSeconds) {
        return isPerfectShotNormalized(normalizeChargeSeconds(chargeSeconds, maxChargeSeconds));
    }

    public static int toPercent(float normalizedCharge) {
        return Math.round(clamp01(normalizedCharge) * 100.0f);
    }

    public static String perfectWindowLabel() {
        return toPercent(perfectShotMinNormalized()) + "%-" + toPercent(perfectShotMaxNormalized()) + "%";
    }
}