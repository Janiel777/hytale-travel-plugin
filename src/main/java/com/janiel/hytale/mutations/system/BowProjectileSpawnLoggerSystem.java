package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.component.PerfectShotMarkerComponent;
import com.janiel.hytale.mutations.weapon.effects.BowPerfectShotDefinitions;

public final class BowProjectileSpawnLoggerSystem extends HolderSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onEntityAdd(
            Holder<EntityStore> holder,
            AddReason addReason,
            Store<EntityStore> store
    ) {
        if (holder == null) {
            return;
        }

        if (addReason != AddReason.SPAWN) {
            return;
        }

        ProjectileComponent projectileComponent = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectileComponent == null) {
            return;
        }

        String projectileAssetName = projectileComponent.getProjectileAssetName();
        if (projectileAssetName == null || projectileAssetName.isEmpty()) {
            return;
        }

        if (!projectileAssetName.toLowerCase().contains("arrow")) {
            return;
        }

        UUIDComponent uuidComponent = holder.getComponent(UUIDComponent.getComponentType());
        String projectileUuid = (uuidComponent == null || uuidComponent.getUuid() == null)
                ? "null"
                : uuidComponent.getUuid().toString();

        PerfectShotMarkerComponent marker = null;
        if (PerfectShotMarkerComponent.getComponentType() != null) {
            marker = holder.getComponent(PerfectShotMarkerComponent.getComponentType());
        }

        LOGGER.atInfo().log("[BowPerfectShot] Arrow projectile spawned. projectileAssetName=" + projectileAssetName
                + " projectileUuid=" + projectileUuid
                + " addReason=" + addReason
                + " hasMarker=" + (marker != null)
                + " perfectShot=" + (marker != null && marker.isPerfectShot())
                + " releaseChargeSeconds=" + (marker == null ? "null" : marker.getReleaseChargeSeconds())
                + " maxChargeSeconds=" + (marker == null ? "null" : marker.getMaxChargeSeconds())
                + " releaseChargePercent=" + (marker == null ? "null" : BowPerfectShotDefinitions.toPercent(marker.getReleaseNormalizedCharge())));
    }

    @Override
    public void onEntityRemoved(
            Holder<EntityStore> holder,
            RemoveReason removeReason,
            Store<EntityStore> store
    ) {
        // No-op
    }
}