package com.janiel.hytale.mutations.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PendingPerfectShotComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, PendingPerfectShotComponent> COMPONENT_TYPE;

    private boolean perfectShot;
    private float chargeSeconds;
    private float normalizedCharge;
    private float maxChargeSeconds;
    private long shotSequence;
    private String projectileConfigId;

    public PendingPerfectShotComponent() {
    }

    public PendingPerfectShotComponent(
            boolean perfectShot,
            float chargeSeconds,
            float normalizedCharge,
            float maxChargeSeconds,
            long shotSequence,
            String projectileConfigId
    ) {
        this.perfectShot = perfectShot;
        this.chargeSeconds = chargeSeconds;
        this.normalizedCharge = normalizedCharge;
        this.maxChargeSeconds = maxChargeSeconds;
        this.shotSequence = shotSequence;
        this.projectileConfigId = projectileConfigId;
    }

    public PendingPerfectShotComponent(PendingPerfectShotComponent other) {
        this.perfectShot = other.perfectShot;
        this.chargeSeconds = other.chargeSeconds;
        this.normalizedCharge = other.normalizedCharge;
        this.maxChargeSeconds = other.maxChargeSeconds;
        this.shotSequence = other.shotSequence;
        this.projectileConfigId = other.projectileConfigId;
    }

    public static void setComponentType(
            ComponentType<EntityStore, PendingPerfectShotComponent> componentType
    ) {
        COMPONENT_TYPE = componentType;
    }

    public static ComponentType<EntityStore, PendingPerfectShotComponent> getComponentType() {
        return COMPONENT_TYPE;
    }

    public boolean isPerfectShot() {
        return perfectShot;
    }

    public float getChargeSeconds() {
        return chargeSeconds;
    }

    public float getNormalizedCharge() {
        return normalizedCharge;
    }

    public float getMaxChargeSeconds() {
        return maxChargeSeconds;
    }

    public long getShotSequence() {
        return shotSequence;
    }

    public String getProjectileConfigId() {
        return projectileConfigId;
    }

    @Override
    public PendingPerfectShotComponent clone() {
        return new PendingPerfectShotComponent(this);
    }
}