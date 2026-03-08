package com.janiel.hytale.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
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

/**
 * Weapon Mastery (weapon_dagger) - Bleed tiers 1..10
 * - On dagger hits, chance to apply/upgrade bleed on victim.
 * - Bleed deals periodic damage.
 * - Bleed tier decays when the attacker stops refreshing it.
 */
public final class DaggerMasteryBleedSystem extends EntityEventSystem<EntityStore, Damage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Throttles to prevent log spam.
    private static volatile long lastHitLogMs = 0L;
    private static volatile long lastProcLogMs = 0L;
    private static volatile long lastSkipLogMs = 0L;

    public DaggerMasteryBleedSystem() {
        super(Damage.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
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

        Ref<EntityStore> attackerRef = WeaponEffectEngine.tryGetEntityAttackerRef(damage);
        if (attackerRef == null) {
            throttleSkip("no entity attacker source", entityId);
            return;
        }

        PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attackerPlayerRef == null) {
            throttleSkip("attacker is not PlayerRef", entityId);
            return;
        }

        UUID attackerUuid = attackerPlayerRef.getUuid();
        MutationsState state = MutationsRepository.getOrLoadState(attackerUuid);
        int daggerLevel = state.getDaggerLevel();
        if (daggerLevel <= 0) {
            throttleSkip("daggerLevel <= 0", entityId);
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
        if (weaponType != WeaponType.DAGGER) {
            throttleSkip("not a dagger in hand", entityId);
            return;
        }

        float chance = WeaponEffectDefinitions.bleedProcChanceForDaggerLevel(daggerLevel);
        float roll = ThreadLocalRandom.current().nextFloat();

        long now = System.currentTimeMillis();

        if (now - lastHitLogMs >= 400L) {
            lastHitLogMs = now;
            LOGGER.atInfo().log("[DaggerMastery] Hit detected. attackerUuid=" + attackerUuid
                    + " victimEntityId=" + entityId
                    + " itemId=" + itemId
                    + " daggerLevel=" + daggerLevel
                    + " roll=" + roll
                    + " chance=" + chance);
        }

        if (roll > chance) {
            // Even if we didn't proc, hitting with a dagger should reset decay timing
            // so tiers can be built up with consecutive hits.
            DaggerMasteryBleedTickSystem.refreshBleedDecayOnHit(entityId, now);
            return;
        }

        // Upgrade/refresh bleed.
        int causeIndex = damage.getDamageCauseIndex();

        DaggerMasteryBleedTickSystem.markBleeding(
                entityId,
                victimRef,
                daggerLevel,
                causeIndex,
                now
        );

        if (now - lastProcLogMs >= 200L) {
            lastProcLogMs = now;
            LOGGER.atInfo().log("[DaggerMastery] BLEED PROC. attackerUuid=" + attackerUuid
                    + " victimEntityId=" + entityId
                    + " daggerLevel=" + daggerLevel
                    + " causeIndex=" + causeIndex);
        }
    }

    private static void throttleSkip(String reason, int victimEntityId) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs >= 2500L) {
            lastSkipLogMs = now;
            LOGGER.atInfo().log("[DaggerMastery] Skip. reason=" + reason + " victimEntityId=" + victimEntityId);
        }
    }
}