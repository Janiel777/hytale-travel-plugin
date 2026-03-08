package com.janiel.hytale.mutations.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PerfectShotMarkerComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, PerfectShotMarkerComponent> COMPONENT_TYPE;

    private boolean perfectShot;
    private float releaseChargeSeconds;
    private float releaseNormalizedCharge;
    private float maxChargeSeconds;

    public PerfectShotMarkerComponent() {
    }

    public PerfectShotMarkerComponent(
            boolean perfectShot,
            float releaseChargeSeconds,
            float releaseNormalizedCharge,
            float maxChargeSeconds
    ) {
        this.perfectShot = perfectShot;
        this.releaseChargeSeconds = releaseChargeSeconds;
        this.releaseNormalizedCharge = releaseNormalizedCharge;
        this.maxChargeSeconds = maxChargeSeconds;
    }

    public PerfectShotMarkerComponent(PerfectShotMarkerComponent other) {
        this.perfectShot = other.perfectShot;
        this.releaseChargeSeconds = other.releaseChargeSeconds;
        this.releaseNormalizedCharge = other.releaseNormalizedCharge;
        this.maxChargeSeconds = other.maxChargeSeconds;
    }

    public static void setComponentType(ComponentType<EntityStore, PerfectShotMarkerComponent> componentType) {
        COMPONENT_TYPE = componentType;
    }

    public static ComponentType<EntityStore, PerfectShotMarkerComponent> getComponentType() {
        return COMPONENT_TYPE;
    }

    public boolean isPerfectShot() {
        return perfectShot;
    }

    public float getReleaseChargeSeconds() {
        return releaseChargeSeconds;
    }

    public float getReleaseNormalizedCharge() {
        return releaseNormalizedCharge;
    }

    public float getMaxChargeSeconds() {
        return maxChargeSeconds;
    }

    @Override
    public PerfectShotMarkerComponent clone() {
        return new PerfectShotMarkerComponent(this);
    }
}