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
 * Adds an extra stamina regeneration delay ONLY when stamina fully depletes (hits ~0).
 *
 * Assets stay default. We only manipulate the StaminaRegenDelay stat value at runtime.
 *
 * Key detail from logs:
 * - At the exact EMPTY edge, delay may still be 0.
 * - Shortly after, the engine sets delay negative (e.g., -0.75).
 * We apply our override AFTER we observe delay < 0 while stamina is still empty.
 */
public final class StaminaDepletionExtraRegenDelaySystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final float EMPTY_EPSILON = 0.0001f;
    private static final float NEGATIVE_EPSILON = -0.0001f;

    private static final String STAMINA_REGEN_DELAY_STAT_ID = "StaminaRegenDelay";

    /**
     * With default assets: StaminaRegenDelay regenerates +0.1 each 0.1s => +1.0 per second.
     * So setting delay to -3.0 gives ~3 seconds until it reaches 0.
     */
    private static final float TARGET_DELAY_VALUE_ON_DEPLETION = -3;

    private static final class State {
        boolean wasEmpty;
        boolean appliedForThisDepletion;
    }

    private static final Map<UUID, State> STATE_BY_PLAYER = new ConcurrentHashMap<>();

    public StaminaDepletionExtraRegenDelaySystem() {
        super(0.10f);
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

        EntityStatValue stamina = statMap.get(DefaultEntityStatTypes.getStamina());
        if (stamina == null) {
            return;
        }

        EntityStatValue regenDelay = statMap.get(STAMINA_REGEN_DELAY_STAT_ID);
        if (regenDelay == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        State st = STATE_BY_PLAYER.computeIfAbsent(uuid, k -> new State());

        float staminaValue = stamina.get();
        boolean isEmpty = staminaValue <= EMPTY_EPSILON;

        // Reset when we leave empty.
        if (!isEmpty) {
            st.wasEmpty = false;
            st.appliedForThisDepletion = false;
            return;
        }

        // We are empty here.
        if (!st.wasEmpty) {
            // Entering empty state.
            st.wasEmpty = true;
            st.appliedForThisDepletion = false;

            // We intentionally DO NOT write here, because your logs show delay may still be 0 at the edge.
            return;
        }

        if (st.appliedForThisDepletion) {
            return;
        }

        // Apply only after the engine has already pushed delay negative (e.g., -0.75).
        float delayValue = regenDelay.get();
        if (delayValue < NEGATIVE_EPSILON) {
            int delayIndex = regenDelay.getIndex();

            // Only push if our target increases the delay window.
            if (delayValue > TARGET_DELAY_VALUE_ON_DEPLETION) {
                statMap.setStatValue(delayIndex, TARGET_DELAY_VALUE_ON_DEPLETION);
                st.appliedForThisDepletion = true;

                LOGGER.atInfo().log("[STAMINA-DELAY] Applied extra regen delay on depletion uuid=" + uuid
                        + " stamina=" + staminaValue
                        + " delay " + delayValue + " -> " + TARGET_DELAY_VALUE_ON_DEPLETION);
            } else {
                // Already more negative than our target; still mark as applied so we don't spam.
                st.appliedForThisDepletion = true;
            }
        }
    }
}