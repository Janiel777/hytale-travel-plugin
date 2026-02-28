package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug system: logs when a player's stamina becomes empty (edge-triggered).
 *
 * Implementation detail:
 * - We sample the stamina stat periodically (DelayedEntitySystem).
 * - We log only on transitions from "not empty" -> "empty".
 */
public final class StaminaDepletedLoggerSystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final float EMPTY_EPSILON = 0.0001f;

    private static final Map<UUID, Boolean> LAST_EMPTY_BY_PLAYER = new ConcurrentHashMap<>();

    public StaminaDepletedLoggerSystem() {
        // 0.10s cadence keeps overhead low while still feeling instant.
        super(0.10f);
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Keep query broad; we filter by required components in tick().
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
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int staminaIndex = DefaultEntityStatTypes.getStamina();
        EntityStatValue stamina = statMap.get(staminaIndex);
        if (stamina == null) {
            return;
        }

        float value = stamina.get();
        boolean isEmpty = value <= EMPTY_EPSILON;

        UUID uuid = playerRef.getUuid();
        boolean wasEmpty = Boolean.TRUE.equals(LAST_EMPTY_BY_PLAYER.get(uuid));

        if (isEmpty && !wasEmpty) {
            LAST_EMPTY_BY_PLAYER.put(uuid, true);
            LOGGER.atInfo().log("Stamina depleted: uuid=" + uuid
                    + " value=" + value
                    + " max=" + stamina.getMax());
            return;
        }

        if (!isEmpty && wasEmpty) {
            LAST_EMPTY_BY_PLAYER.put(uuid, false);
        }
    }
}