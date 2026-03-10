package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.component.PendingPerfectShotComponent;
import com.janiel.hytale.mutations.weapon.effects.BowPerfectShotDefinitions;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.janiel.hytale.mutations.persistence.MutationsCache;
import com.janiel.hytale.mutations.persistence.MutationsState;

import java.util.UUID;

public final class BowPerfectShotDamageSystem extends EntityEventSystem<EntityStore, Damage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public BowPerfectShotDamageSystem() {
        super(Damage.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Run in FilterDamageGroup so the modified damage amount is the one later systems/UI inspect.
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
        if (damage == null) {
            return;
        }

        Damage.Source source = damage.getSource();
        if (source == null) {
            return;
        }

        Ref<EntityStore> attackerRef = null;

        if (source instanceof Damage.ProjectileSource) {
            attackerRef = ((Damage.ProjectileSource) source).getRef();
        } else if (source instanceof Damage.EntitySource) {
            attackerRef = ((Damage.EntitySource) source).getRef();
        } else {
            LOGGER.atInfo().log("[BowPerfectShot] Damage source ignored. sourceClass=" + source.getClass().getName());
            return;
        }

        if (attackerRef == null) {
            LOGGER.atInfo().log("[BowPerfectShot] Damage source had null attackerRef. sourceClass=" + source.getClass().getName());
            return;
        }

        UUID attackerUuid = null;
        PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attackerPlayerRef != null) {
            attackerUuid = attackerPlayerRef.getUuid();
        }

        PendingPerfectShotComponent pending = null;
        if (PendingPerfectShotComponent.getComponentType() != null) {
            pending = store.getComponent(attackerRef, PendingPerfectShotComponent.getComponentType());
        }

        float oldAmount = damage.getAmount();

        LOGGER.atInfo().log("[BowPerfectShot][PENDING] Damage event. sourceClass=" + source.getClass().getName()
                + " attackerRef=" + attackerRef
                + " attackerUuid=" + attackerUuid
                + " victimEntityId=" + entityId
                + " amountBefore=" + oldAmount
                + " hasPending=" + (pending != null));

        if (pending == null) {
            LOGGER.atInfo().log("[BowPerfectShot][PENDING] No pending shot on attacker.");
            return;
        }

        LOGGER.atInfo().log("[BowPerfectShot][PENDING] Pending shot data. shotSequence=" + pending.getShotSequence()
                + " projectileConfigId=" + pending.getProjectileConfigId()
                + " perfectShot=" + pending.isPerfectShot()
                + " chargeSeconds=" + pending.getChargeSeconds()
                + " maxChargeSeconds=" + pending.getMaxChargeSeconds()
                + " chargePercent=" + BowPerfectShotDefinitions.toPercent(pending.getNormalizedCharge()));

        boolean looksLikeBowShot = false;

        if (pending.getProjectileConfigId() != null) {
            String lower = pending.getProjectileConfigId().toLowerCase();
            looksLikeBowShot = lower.contains("shortbow") || lower.contains("arrow");
        }

        if (!looksLikeBowShot) {
            LOGGER.atInfo().log("[BowPerfectShot][PENDING] Pending shot ignored because config did not look like bow projectile.");
            commandBuffer.tryRemoveComponent(attackerRef, PendingPerfectShotComponent.getComponentType());
            return;
        }

        if (!pending.isPerfectShot()) {
            LOGGER.atInfo().log("[BowPerfectShot][PENDING] Pending shot was not perfect. Consuming without multiplier.");
            commandBuffer.tryRemoveComponent(attackerRef, PendingPerfectShotComponent.getComponentType());
            return;
        }

        int bowMutationLevel = 0;
        if (attackerUuid != null) {
            MutationsState state = MutationsCache.getOrLoad(attackerUuid);
            if (state != null) {
                bowMutationLevel = state.getBowLevel();
            }
        }

        float newAmount = oldAmount * BowPerfectShotDefinitions.perfectShotDamageMultiplier(bowMutationLevel);

        damage.putMetaObject(Damage.CAN_BE_PREDICTED, false);
        damage.setAmount(newAmount);

        LOGGER.atInfo().log("[BowPerfectShot] PERFECT SHOT damage multiplied via attacker pending state. attackerUuid=" + attackerUuid
                + " victimEntityId=" + entityId
                + " shotSequence=" + pending.getShotSequence()
                + " projectileConfigId=" + pending.getProjectileConfigId()
                + " oldAmount=" + oldAmount
                + " newAmount=" + newAmount
                + " bowMutationLevel=" + bowMutationLevel
                + " multiplier=" + BowPerfectShotDefinitions.perfectShotDamageMultiplier(bowMutationLevel));

        commandBuffer.tryRemoveComponent(attackerRef, PendingPerfectShotComponent.getComponentType());
    }
}