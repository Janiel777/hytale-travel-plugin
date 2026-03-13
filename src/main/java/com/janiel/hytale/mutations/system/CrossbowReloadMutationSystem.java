package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.persistence.MutationsRepository;
import com.janiel.hytale.mutations.persistence.MutationsState;
import com.janiel.hytale.mutations.weapon.WeaponType;
import com.janiel.hytale.mutations.weapon.effects.WeaponEffectDefinitions;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrossbowReloadMutationSystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final float FLOAT_EPSILON = 0.0001f;
    private static final long SKIP_LOG_INTERVAL_MS = 3000L;
    private static final Set<String> MISSING_EFFECT_IDS_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, State> STATE_BY_PLAYER = new ConcurrentHashMap<>();

    private static final class State {
        String lastItemId = "";
        int lastDesiredTier = Integer.MIN_VALUE;
        int lastAppliedTier = Integer.MIN_VALUE;

        boolean hasLastAmmoSample;
        float lastAmmoCurrent;
        float lastAmmoMin;
        float lastAmmoMax;
        float lastAmmoPercent;
    }

    private static volatile long lastSkipLogMs = 0L;

    public CrossbowReloadMutationSystem() {
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
        Ref<EntityStore> playerRefEntity = chunk.getReferenceTo(entityId);
        if (playerRefEntity == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(playerRefEntity, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }

        Player player = store.getComponent(playerRefEntity, Player.getComponentType());
        if (player == null) {
            throttleSkip("missing Player component", entityId);
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            throttleSkip("player inventory null", entityId);
            return;
        }

        ItemStack inHand = inventory.getItemInHand();
        String itemId = (inHand == null || inHand.isEmpty()) ? "" : inHand.getItemId();
        boolean holdingCrossbow = WeaponType.fromItemId(itemId) == WeaponType.CROSSBOW;

        int mutationLevel = 0;
        int desiredTier = 0;
        if (holdingCrossbow) {
            MutationsState state = MutationsRepository.getOrLoadState(playerUuid);
            mutationLevel = state.getCrossbowLevel();
            desiredTier = WeaponEffectDefinitions.crossbowReloadTierFromMutationLevel(mutationLevel);
        }

        State runtimeState = STATE_BY_PLAYER.computeIfAbsent(playerUuid, ignored -> new State());

        EffectControllerComponent effects = store.getComponent(playerRefEntity, EffectControllerComponent.getComponentType());
        int activeTierBeforeSync = getHighestActiveCrossbowTier(effects);

        if (!itemId.equals(runtimeState.lastItemId) || desiredTier != runtimeState.lastDesiredTier || activeTierBeforeSync != runtimeState.lastAppliedTier) {
            LOGGER.atInfo().log("[CrossbowReload] Tier sync candidate. uuid=" + playerUuid
                    + " itemId=" + itemId
                    + " holdingCrossbow=" + holdingCrossbow
                    + " mutationLevel=" + mutationLevel
                    + " desiredTier=" + desiredTier
                    + " activeTierBeforeSync=" + activeTierBeforeSync);
        }

        int activeTierAfterSync = syncCrossbowTierEffect(
                playerRefEntity,
                store,
                effects,
                desiredTier,
                playerUuid,
                itemId
        );

        runtimeState.lastItemId = itemId;
        runtimeState.lastDesiredTier = desiredTier;
        runtimeState.lastAppliedTier = activeTierAfterSync;

        if (holdingCrossbow) {
            logAmmoSampleIfChanged(playerRefEntity, store, playerUuid, itemId, desiredTier, activeTierAfterSync, runtimeState);
        } else {
            runtimeState.hasLastAmmoSample = false;
        }
    }

    private static int syncCrossbowTierEffect(
            Ref<EntityStore> playerRefEntity,
            Store<EntityStore> store,
            EffectControllerComponent effects,
            int desiredTier,
            UUID playerUuid,
            String itemId
    ) {
        if (effects == null) {
            if (desiredTier > 0) {
                throttleSkip("player missing EffectControllerComponent", playerRefEntity.getIndex());
            }
            return 0;
        }

        int activeTier = getHighestActiveCrossbowTier(effects);
        if (activeTier == desiredTier) {
            return activeTier;
        }

        removeAllCrossbowTierEffects(playerRefEntity, store, effects);

        if (desiredTier <= 0) {
            LOGGER.atInfo().log("[CrossbowReload] Cleared crossbow reload tier. uuid=" + playerUuid
                    + " itemId=" + itemId
                    + " activeTierBeforeClear=" + activeTier);
            return 0;
        }

        String effectId = WeaponEffectDefinitions.crossbowReloadEffectIdForLevel(desiredTier);
        int effectIndex = EntityEffect.getAssetMap().getIndexOrDefault(effectId, -1);
        if (effectIndex < 0) {
            logMissingEffectOnce(effectId);
            return 0;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) {
            logMissingEffectOnce(effectId);
            return 0;
        }

        effects.addInfiniteEffect(playerRefEntity, effectIndex, effect, store);

        LOGGER.atInfo().log("[CrossbowReload] Applied crossbow reload tier. uuid=" + playerUuid
                + " itemId=" + itemId
                + " desiredTier=" + desiredTier
                + " effectId=" + effectId
                + " reloadSecondsPerBolt=" + WeaponEffectDefinitions.crossbowReloadSecondsForLevel(desiredTier));

        return desiredTier;
    }

    private static void logAmmoSampleIfChanged(
            Ref<EntityStore> playerRefEntity,
            Store<EntityStore> store,
            UUID playerUuid,
            String itemId,
            int desiredTier,
            int activeTier,
            State runtimeState
    ) {
        EntityStatMap statMap = store.getComponent(playerRefEntity, EntityStatMap.getComponentType());
        if (statMap == null) {
            throttleSkip("player missing EntityStatMap", playerRefEntity.getIndex());
            return;
        }

        EntityStatValue ammo = statMap.get(DefaultEntityStatTypes.getAmmo());
        if (ammo == null) {
            throttleSkip("player missing Ammo stat", playerRefEntity.getIndex());
            return;
        }

        float ammoCurrent = ammo.get();
        float ammoMin = ammo.getMin();
        float ammoMax = ammo.getMax();
        float ammoPercent = ammo.asPercentage();

        if (runtimeState.hasLastAmmoSample
                && almostEqual(runtimeState.lastAmmoCurrent, ammoCurrent)
                && almostEqual(runtimeState.lastAmmoMin, ammoMin)
                && almostEqual(runtimeState.lastAmmoMax, ammoMax)
                && almostEqual(runtimeState.lastAmmoPercent, ammoPercent)) {
            return;
        }

        runtimeState.hasLastAmmoSample = true;
        runtimeState.lastAmmoCurrent = ammoCurrent;
        runtimeState.lastAmmoMin = ammoMin;
        runtimeState.lastAmmoMax = ammoMax;
        runtimeState.lastAmmoPercent = ammoPercent;

        LOGGER.atInfo().log("[CrossbowReload] Ammo sample. uuid=" + playerUuid
                + " itemId=" + itemId
                + " desiredTier=" + desiredTier
                + " activeTier=" + activeTier
                + " ammoCurrent=" + ammoCurrent
                + " ammoMin=" + ammoMin
                + " ammoMax=" + ammoMax
                + " ammoPercent=" + ammoPercent);
    }

    private static int getHighestActiveCrossbowTier(EffectControllerComponent effects) {
        if (effects == null) {
            return 0;
        }

        if (hasActiveCrossbowTierEffect(effects, 3)) {
            return 3;
        }
        if (hasActiveCrossbowTierEffect(effects, 2)) {
            return 2;
        }
        if (hasActiveCrossbowTierEffect(effects, 1)) {
            return 1;
        }

        return 0;
    }

    private static boolean hasActiveCrossbowTierEffect(EffectControllerComponent effects, int tier) {
        String effectId = WeaponEffectDefinitions.crossbowReloadEffectIdForLevel(tier);
        if (effectId == null) {
            return false;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndexOrDefault(effectId, -1);
        if (effectIndex < 0) {
            logMissingEffectOnce(effectId);
            return false;
        }

        return effects.getActiveEffects().containsKey(effectIndex);
    }

    private static void removeAllCrossbowTierEffects(
            Ref<EntityStore> playerRefEntity,
            Store<EntityStore> store,
            EffectControllerComponent effects
    ) {
        for (int tier = 1; tier <= 3; tier++) {
            String effectId = WeaponEffectDefinitions.crossbowReloadEffectIdForLevel(tier);
            if (effectId == null) {
                continue;
            }

            int effectIndex = EntityEffect.getAssetMap().getIndexOrDefault(effectId, -1);
            if (effectIndex < 0) {
                logMissingEffectOnce(effectId);
                continue;
            }

            if (effects.getActiveEffects().containsKey(effectIndex)) {
                effects.removeEffect(playerRefEntity, effectIndex, store);
            }
        }
    }

    private static void logMissingEffectOnce(String effectId) {
        if (effectId == null) {
            return;
        }

        if (MISSING_EFFECT_IDS_LOGGED.add(effectId)) {
            LOGGER.atWarning().log("[CrossbowReload] Missing EntityEffect asset: id=" + effectId
                    + " (expected in Server/Entity/Effects/Status/" + effectId + ".json)");
        }
    }

    private static boolean almostEqual(float a, float b) {
        return Math.abs(a - b) <= FLOAT_EPSILON;
    }

    private static void throttleSkip(String reason, int entityId) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs >= SKIP_LOG_INTERVAL_MS) {
            lastSkipLogMs = now;
            LOGGER.atWarning().log("[CrossbowReload] Skip. reason=" + reason + " entityId=" + entityId);
        }
    }
}