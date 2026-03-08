package com.janiel.hytale.mutations.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BowPerfectShotChargeTrackerComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, BowPerfectShotChargeTrackerComponent> COMPONENT_TYPE;

    private float chargeSeconds;
    private float normalizedCharge;
    private float maxChargeSeconds;
    private boolean held;

    public BowPerfectShotChargeTrackerComponent() {
    }

    public BowPerfectShotChargeTrackerComponent(
            float chargeSeconds,
            float normalizedCharge,
            float maxChargeSeconds,
            boolean held
    ) {
        this.chargeSeconds = chargeSeconds;
        this.normalizedCharge = normalizedCharge;
        this.maxChargeSeconds = maxChargeSeconds;
        this.held = held;
    }

    public BowPerfectShotChargeTrackerComponent(BowPerfectShotChargeTrackerComponent other) {
        this.chargeSeconds = other.chargeSeconds;
        this.normalizedCharge = other.normalizedCharge;
        this.maxChargeSeconds = other.maxChargeSeconds;
        this.held = other.held;
    }

    public static void setComponentType(
            ComponentType<EntityStore, BowPerfectShotChargeTrackerComponent> componentType
    ) {
        COMPONENT_TYPE = componentType;
    }

    public static ComponentType<EntityStore, BowPerfectShotChargeTrackerComponent> getComponentType() {
        return COMPONENT_TYPE;
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

    public boolean isHeld() {
        return held;
    }

    @Override
    public BowPerfectShotChargeTrackerComponent clone() {
        return new BowPerfectShotChargeTrackerComponent(this);
    }
}