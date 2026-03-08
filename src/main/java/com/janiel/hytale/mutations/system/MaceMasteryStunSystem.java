package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.persistence.MutationsRepository;
import com.janiel.hytale.mutations.persistence.MutationsState;
import com.janiel.hytale.mutations.weapon.WeaponType;
import com.janiel.hytale.mutations.weapon.effects.WeaponEffectDefinitions;
import com.janiel.hytale.mutations.weapon.effects.WeaponEffectEngine;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MaceMasteryStunSystem extends EntityEventSystem<EntityStore, Damage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Throttles to prevent log spam.
    private static volatile long lastHitLogMs = 0L;
    private static volatile long lastProcLogMs = 0L;
    private static volatile long lastSkipLogMs = 0L;

    public MaceMasteryStunSystem() {
        super(Damage.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Keep consistent with other combat hooks (adjust early in the pipeline).
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(
            int entityId,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Damage damage
    ) {
        if (damage == null) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityId);
        if (victimRef == null) return;

        // Need attacker entity ref (only works for entity-sourced damage).
        Ref<EntityStore> attackerRef = WeaponEffectEngine.tryGetEntityAttackerRef(damage);
        if (attackerRef == null) {
            throttleSkip("no entity attacker source", entityId);
            return;
        }

        // Only players can proc mastery.
        PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attackerPlayerRef == null) {
            throttleSkip("attacker is not PlayerRef", entityId);
            return;
        }

        UUID attackerUuid = attackerPlayerRef.getUuid();
        MutationsState state = MutationsRepository.getOrLoadState(attackerUuid);
        int maceLevel = state.getMaceLevel();
        if (maceLevel <= 0) {
            throttleSkip("maceLevel <= 0", entityId);
            return;
        }

        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) {
            throttleSkip("missing Player component on attacker", entityId);
            return;
        }

        Inventory inv = attackerPlayer.getInventory();
        if (inv == null) {
            throttleSkip("attacker inventory null", entityId);
            return;
        }

        ItemStack inHand = inv.getItemInHand();
        if (inHand == null || inHand.isEmpty()) {
            throttleSkip("attacker hand empty", entityId);
            return;
        }

        String itemId = inHand.getItemId();
        WeaponType weaponType = WeaponType.fromItemId(itemId);
        if (weaponType != WeaponType.MACE) {
            throttleSkip("not a mace in hand", entityId);
            return;
        }

        float chance = WeaponEffectDefinitions.stunProcChanceForMaceLevel(maceLevel);
        float roll = ThreadLocalRandom.current().nextFloat();

        // Periodic hit log (useful while tuning).
        long now = System.currentTimeMillis();
        if (now - lastHitLogMs >= 400L) {
            lastHitLogMs = now;
            LOGGER.atInfo().log("[MaceMastery] Hit detected. attackerUuid=" + attackerUuid
                    + " victimEntityId=" + entityId
                    + " itemId=" + itemId
                    + " maceLevel=" + maceLevel
                    + " roll=" + roll
                    + " chance=" + chance
                    + " damageAmount(beforeHook)=" + damage.getAmount());
        }

        if (roll > chance) {
            return;
        }

        float durationSeconds = WeaponEffectDefinitions.stunDurationSecondsForMaceLevel(maceLevel);
        long durationMs = (long) (durationSeconds * 1000.0f);

        // 1) Apply VFX EntityEffect (Stun).
        applyStunEffectVfx(victimRef, store, entityId, durationSeconds);

        // 2) Apply "real stun" using Frozen component (engine-level).
        applyFrozen(commandBuffer, victimRef, entityId);

        // 3) Schedule revert (remove Frozen) via our delayed system.
        MaceMasteryStunRevertSystem.markStunned(entityId, victimRef, System.currentTimeMillis() + durationMs);

        now = System.currentTimeMillis();
        if (now - lastProcLogMs >= 200L) {
            lastProcLogMs = now;
            LOGGER.atInfo().log("[MaceMastery] STUN PROC. attackerUuid=" + attackerUuid
                    + " victimEntityId=" + entityId
                    + " maceLevel=" + maceLevel
                    + " durationSeconds=" + durationSeconds
                    + " itemId=" + itemId);
        }
    }

    private static void applyStunEffectVfx(
            Ref<EntityStore> victimRef,
            Store<EntityStore> store,
            int victimEntityId,
            float durationSeconds
    ) {
        EffectControllerComponent effects = store.getComponent(victimRef, EffectControllerComponent.getComponentType());
        if (effects == null) {
            throttleSkip("victim has no EffectControllerComponent (cannot apply VFX)", victimEntityId);
            return;
        }

        String effectId = WeaponEffectDefinitions.stunEffectId();
        int effectIndex = EntityEffect.getAssetMap().getIndexOrDefault(effectId, -1);
        if (effectIndex < 0) {
            WeaponEffectEngine.logMissingStunEffectOnce(LOGGER, effectId);
            return;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) {
            WeaponEffectEngine.logMissingStunEffectOnce(LOGGER, effectId);
            return;
        }

        effects.addEffect(
                victimRef,
                effectIndex,
                effect,
                durationSeconds,
                OverlapBehavior.OVERWRITE,
                store
        );
    }

    private static void applyFrozen(
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> victimRef,
            int victimEntityId
    ) {
        // Re-apply is fine; this is idempotent from our perspective.
        commandBuffer.addComponent(
                victimRef,
                Frozen.getComponentType(),
                Frozen.get()
        );

        long now = System.currentTimeMillis();
        if (now - lastProcLogMs >= 200L) {
            // piggyback on proc throttle; no extra spam
            LOGGER.atInfo().log("[MaceMastery] Applied Frozen component. victimEntityId=" + victimEntityId);
        }
    }

    private static void throttleSkip(String reason, int victimEntityId) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs >= 2500L) {
            lastSkipLogMs = now;
            LOGGER.atWarning().log("[MaceMastery] Skip. reason=" + reason + " victimEntityId=" + victimEntityId);
        }
    }
}