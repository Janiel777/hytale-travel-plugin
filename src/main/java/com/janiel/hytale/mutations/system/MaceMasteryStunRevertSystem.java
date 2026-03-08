package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MaceMasteryStunRevertSystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final class StunEntry {
        final Ref<EntityStore> ref;
        volatile long expireAtMs;

        StunEntry(Ref<EntityStore> ref, long expireAtMs) {
            this.ref = ref;
            this.expireAtMs = expireAtMs;
        }
    }

    // Keyed by victim entity id (stable within store lifetime). We also keep Ref to operate safely.
    private static final Map<Integer, StunEntry> STUNNED = new ConcurrentHashMap<>();

    public MaceMasteryStunRevertSystem() {
        // Run 10x per second; good enough for timing without being heavy.
        super(0.10f);
    }

    public static void markStunned(int victimEntityId, Ref<EntityStore> victimRef, long expireAtMs) {
        if (victimRef == null) return;

        STUNNED.compute(victimEntityId, (k, existing) -> {
            if (existing == null) return new StunEntry(victimRef, expireAtMs);
            existing.expireAtMs = Math.max(existing.expireAtMs, expireAtMs);
            return existing;
        });
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
        // We don't rely on iterating only stunned entities, because DelayedEntitySystem ticks per entity.
        // Instead, we process due entries once per tick call, but keep it light.
        if (STUNNED.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Only the entity matching this tick "entityId" is checked (cheap and deterministic).
        StunEntry entry = STUNNED.get(entityId);
        if (entry == null) {
            return;
        }

        if (now < entry.expireAtMs) {
            return;
        }

        Ref<EntityStore> ref = entry.ref;
        STUNNED.remove(entityId);

        if (ref == null || !ref.isValid()) {
            return;
        }

        commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());

        LOGGER.atInfo().log("[MaceMastery] Stun expired -> removed Frozen. victimEntityId=" + entityId);
    }
}