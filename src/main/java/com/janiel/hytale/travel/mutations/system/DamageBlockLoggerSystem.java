package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DamageBlockLoggerSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public DamageBlockLoggerSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Minimal: allow events from any archetype.
        return Query.any();
    }

    @Override
    public void handle(
            int entityId,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            DamageBlockEvent event
    ) {
        Vector3i pos = event.getTargetBlock();
        LOGGER.atInfo().log("DamageBlockEvent: entityId=" + entityId
                + " pos=" + pos
                + " blockType=" + event.getBlockType()
                + " itemInHand=" + event.getItemInHand()
                + " currentDamage=" + event.getCurrentDamage()
                + " damage=" + event.getDamage());
    }
}