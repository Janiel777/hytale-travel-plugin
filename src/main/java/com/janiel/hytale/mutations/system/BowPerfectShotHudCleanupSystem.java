package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.ui.hud.BowPerfectShotHudController;

public final class BowPerfectShotHudCleanupSystem extends DelayedEntitySystem<EntityStore> {

    private static volatile long lastSweepAtMs = 0L;

    public BowPerfectShotHudCleanupSystem() {
        super(0.05f);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(
            float delta,
            int entityId,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        long now = System.currentTimeMillis();
        if (now - lastSweepAtMs < 50L) {
            return;
        }

        lastSweepAtMs = now;
        BowPerfectShotHudController.cleanupExpired();
    }
}