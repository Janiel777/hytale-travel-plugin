package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;
import com.janiel.hytale.travel.mutations.weapon.WeaponType;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectDefinitions;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectEngine;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spear mastery: extra knockback to help keep distance.
 *
 * Implementation notes:
 * - Add KnockbackComponent to the victim with an outward velocity vector.
 * - Vector is attacker->victim direction, mostly horizontal, with a small Y lift.
 */
public final class SpearMasteryKnockbackSystem extends EntityEventSystem<EntityStore, Damage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Throttles to prevent log spam.
    private static volatile long lastHitLogMs = 0L;
    private static volatile long lastProcLogMs = 0L;
    private static volatile long lastSkipLogMs = 0L;

    public SpearMasteryKnockbackSystem() {
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
        int spearLevel = state.getSpearLevel();
        if (spearLevel <= 0) {
            throttleSkip("spearLevel <= 0", entityId);
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
        if (weaponType != WeaponType.SPEAR) {
            throttleSkip("not a spear in hand", entityId);
            return;
        }

        float chance = WeaponEffectDefinitions.knockbackProcChanceForSpearLevel(spearLevel);
        float roll = ThreadLocalRandom.current().nextFloat();

        long now = System.currentTimeMillis();
        if (now - lastHitLogMs >= 400L) {
            lastHitLogMs = now;
            LOGGER.atInfo().log("[SpearMastery] Hit detected. attackerUuid=" + attackerUuid
                    + " victimEntityId=" + entityId
                    + " itemId=" + itemId
                    + " spearLevel=" + spearLevel
                    + " roll=" + roll
                    + " chance=" + chance);
        }

        if (roll > chance) {
            return;
        }

        // Build knockback vector from attacker -> victim.
        TransformComponent attackerTransform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        TransformComponent victimTransform = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (attackerTransform == null || victimTransform == null) {
            throttleSkip("missing TransformComponent", entityId);
            return;
        }

        Vector3d aPos = attackerTransform.getPosition();
        Vector3d vPos = victimTransform.getPosition();
        if (aPos == null || vPos == null) {
            throttleSkip("null position", entityId);
            return;
        }

        Vector3d dir = Vector3d.directionTo(aPos, vPos);
        if (dir == null) {
            throttleSkip("directionTo returned null", entityId);
            return;
        }

        // Make knockback strictly horizontal (XZ only).
        dir.setY(0.0);

        double len = Math.sqrt((dir.x * dir.x) + (dir.y * dir.y) + (dir.z * dir.z));
        if (len <= 0.000001) {
            throttleSkip("zero-length direction", entityId);
            return;
        }

        dir.scale(1.0 / len);

        // Keep Y at 0 so we never try to lift the target.
        dir.setY(0.0);

        float force = WeaponEffectDefinitions.knockbackForceForSpearLevel(spearLevel);
        float duration = WeaponEffectDefinitions.knockbackDurationSecondsForSpearLevel(spearLevel);

        // If the damage came from a projectile hit (thrown spear), make it stronger.
        DamageCause cause = damage.getCause();
        boolean isProjectileHit = (cause == DamageCause.PROJECTILE);
        if (isProjectileHit) {
            force *= WeaponEffectDefinitions.knockbackForceMultiplierWhenProjectile(spearLevel);
        }

        Vector3d velocity = new Vector3d(dir);
        velocity.scale((double) force);
        velocity.setY(0.0);

        KnockbackComponent existingKb = store.getComponent(victimRef, KnockbackComponent.getComponentType());
        if (existingKb != null) {
            // Keep existing velocityConfig/modifiers; just update the impulse values.
            existingKb.setVelocity(velocity);
            existingKb.setVelocityType(ChangeVelocityType.Add);
            existingKb.setDuration(duration);
            existingKb.setTimer(0.0f);

            commandBuffer.replaceComponent(victimRef, KnockbackComponent.getComponentType(), existingKb);
        } else {
            KnockbackComponent kb = new KnockbackComponent();
            kb.setVelocity(velocity);
            kb.setVelocityType(ChangeVelocityType.Add);
            kb.setDuration(duration);
            kb.setTimer(0.0f);

            // putComponent is safe: add if missing, replace if present.
            commandBuffer.putComponent(victimRef, KnockbackComponent.getComponentType(), kb);
        }

        now = System.currentTimeMillis();
        if (now - lastProcLogMs >= 200L) {
            lastProcLogMs = now;
            LOGGER.atInfo().log("[SpearMastery] KNOCKBACK PROC. attackerUuid=" + attackerUuid
                    + " victimEntityId=" + entityId
                    + " spearLevel=" + spearLevel
                    + " cause=" + damage.getCause()
                    + " projectile=" + isProjectileHit
                    + " force=" + force
                    + " durationSeconds=" + duration);
        }
    }

    private static void throttleSkip(String reason, int victimEntityId) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs >= 2500L) {
            lastSkipLogMs = now;
            LOGGER.atWarning().log("[SpearMastery] Skip. reason=" + reason + " victimEntityId=" + victimEntityId);
        }
    }
}