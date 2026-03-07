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
import com.janiel.hytale.travel.mutations.MutationsProgression;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stamina Recovery:
 * - On full depletion: apply extra regen delay override (decreases with level).
 * - During regeneration: amplify observed positive stamina regen by a level-based multiplier.
 */
public final class StaminaDepletionExtraRegenDelaySystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final float EMPTY_EPSILON = 0.0001f;
    private static final float NEGATIVE_EPSILON = -0.0001f;
    private static final float POSITIVE_EPSILON = 0.0001f;

    private static final String STAMINA_REGEN_DELAY_STAT_ID = "StaminaRegenDelay";

    // Same sound used for mining level-up.
    private static final String LEVEL_UP_SOUND_ID = "SFX_Discovery_Z1_Medium";

    private static final class State {
        boolean wasEmpty;
        boolean appliedForThisDepletion;

        boolean hasLastStamina;
        float lastStamina;
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
        float staminaMax = stamina.getMax();
        boolean isEmpty = staminaValue <= EMPTY_EPSILON;

        // Always attempt regen acceleration first (it is safe: only amplifies positive regen).
        applyStaminaRegenAccelerationIfNeeded(statMap, stamina, regenDelay, uuid, st);

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

            // We intentionally DO NOT write here, because delay may still be 0 at the edge.
            return;
        }

        if (st.appliedForThisDepletion) {
            return;
        }

        // Apply only after the engine has already pushed delay negative (e.g., -0.75).
        float delayValue = regenDelay.get();
        if (delayValue < NEGATIVE_EPSILON) {

            // Increment depletions exactly once per depletion.
            MutationsState stateBefore = MutationsRepository.getOrLoadState(uuid);
            int previousLevel = stateBefore.getStaminaDelayLevel();

            // Increment depletions exactly once per depletion.
            MutationsState stateAfter = MutationsRepository.incrementStaminaDepletionsAndGetState(uuid);

            int staminaDelayLevel = stateAfter.getStaminaDelayLevel();
            if (staminaDelayLevel > previousLevel) {
                showStaminaLevelUp(playerRef, staminaDelayLevel);
            }

            int extraDelaySeconds = MutationsProgression.staminaExtraDelaySecondsForLevel(staminaDelayLevel);

            // Setting delay to -N.0 gives ~N seconds until it reaches 0 (based on default behavior observed).
            float targetDelayValue = -1.0f * (float) extraDelaySeconds;

            int delayIndex = regenDelay.getIndex();

            // Only push if our target increases the delay window.
            if (delayValue > targetDelayValue) {
                statMap.setStatValue(delayIndex, targetDelayValue);
                st.appliedForThisDepletion = true;

                LOGGER.atInfo().log("[STAMINA-DELAY] Applied extra regen delay on depletion uuid=" + uuid
                        + " stamina=" + staminaValue
                        + " delay " + delayValue + " -> " + targetDelayValue
                        + " level=" + staminaDelayLevel
                        + " depletions=" + stateAfter.getStaminaDepletions());
            } else {
                // Already more negative than our target (or level 3 => target 0); still mark as applied so we don't spam.
                st.appliedForThisDepletion = true;

                LOGGER.atInfo().log("[STAMINA-DELAY] No override needed on depletion uuid=" + uuid
                        + " stamina=" + staminaValue
                        + " delay=" + delayValue
                        + " target=" + targetDelayValue
                        + " level=" + staminaDelayLevel
                        + " depletions=" + stateAfter.getStaminaDepletions());
            }
        }
    }

    private static void applyStaminaRegenAccelerationIfNeeded(
            EntityStatMap statMap,
            EntityStatValue stamina,
            EntityStatValue regenDelay,
            UUID uuid,
            State st
    ) {
        float staminaValue = stamina.get();
        float staminaMax = stamina.getMax();

        // Initialize tracking.
        if (!st.hasLastStamina) {
            st.hasLastStamina = true;
            st.lastStamina = staminaValue;
            return;
        }

        float prev = st.lastStamina;
        st.lastStamina = staminaValue;

        // Only consider positive regen.
        float gained = staminaValue - prev;
        if (gained <= POSITIVE_EPSILON) {
            return;
        }

        // Don't accelerate while the engine is still in regen delay (negative delay).
        float delayValue = regenDelay.get();
        if (delayValue < NEGATIVE_EPSILON) {
            return;
        }

        // If already full, nothing to do.
        if (staminaValue >= staminaMax - POSITIVE_EPSILON) {
            return;
        }

        // Read cached mutations state (no disk hit; repository uses cache).
        MutationsState state = MutationsRepository.getOrLoadState(uuid);
        int level = state.getStaminaDelayLevel();
        float multiplier = MutationsProgression.staminaRegenSpeedMultiplierForLevel(level);

        if (multiplier <= 1.0f + POSITIVE_EPSILON) {
            return;
        }

        float extra = gained * (multiplier - 1.0f);
        if (extra <= POSITIVE_EPSILON) {
            return;
        }

        float newValue = staminaValue + extra;
        if (newValue > staminaMax) {
            newValue = staminaMax;
        }

        int staminaIndex = stamina.getIndex();
        statMap.setStatValue(staminaIndex, newValue);
    }

    private static void showStaminaLevelUp(PlayerRef playerRef, int newLevel) {
        // Title overlay (primary + secondary)
        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("Stamina Recovery"),
                Message.raw("Level " + newLevel),
                true
        );

        // Sound (plays only if the ID exists in the SoundEvent asset map)
        int soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(LEVEL_UP_SOUND_ID, SoundEvent.EMPTY_ID);
        if (soundIndex != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
        }
    }
}