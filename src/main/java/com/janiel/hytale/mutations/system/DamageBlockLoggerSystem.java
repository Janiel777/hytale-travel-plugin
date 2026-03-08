package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.MutationsProgression;
import com.janiel.hytale.mutations.persistence.MutationsRepository;
import com.janiel.hytale.mutations.persistence.MutationsState;

import java.util.UUID;

public final class DamageBlockLoggerSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile long lastAppliedLogMs = 0L;

    public DamageBlockLoggerSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
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
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();

        MutationsState state = MutationsRepository.getOrLoadState(playerUuid);
        int level = state.getMiningLevel();

        float multiplier = MutationsProgression.miningDamageMultiplierForLevel(level);
        if (multiplier == 1.0f) {
            return;
        }

        float oldDamage = event.getDamage();
        float newDamage = oldDamage * multiplier;

        event.setDamage(newDamage);

        long now = System.currentTimeMillis();
        if (now - lastAppliedLogMs >= 1000L) {
            lastAppliedLogMs = now;

            Vector3i pos = event.getTargetBlock();
            LOGGER.atInfo().log("Applied mining speed: uuid=" + playerUuid
                    + " level=" + level
                    + " multiplier=" + multiplier
                    + " pos=" + pos
                    + " blockType=" + event.getBlockType()
                    + " oldDamage=" + oldDamage
                    + " newDamage=" + newDamage);
        }
    }
}