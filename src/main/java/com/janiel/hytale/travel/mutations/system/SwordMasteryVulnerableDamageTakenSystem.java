package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;
import com.janiel.hytale.travel.mutations.weapon.WeaponType;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectDefinitions;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectEngine;

import java.util.UUID;

/**
 * Weapon Mastery (weapon_sword):
 * - When a player hits with a sword and has sword level > 0, apply a "vulnerable" tag (EntityEffect) to the victim.
 * - While the victim is tagged, ANY incoming damage is multiplied by the sword level multiplier (1.10/1.20/1.30).
 *
 * Notes:
 * - The effect "lives" in the attacker (their sword level); the weapon is only used to detect a sword hit.
 * - The tag asset is just a marker (no stat changes), duration based.
 * - OverlapBehavior is OVERWRITE, so newest tag refresh wins.
 */
public final class SwordMasteryVulnerableDamageTakenSystem extends EntityEventSystem<EntityStore, Damage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Throttles to prevent log spam.
    private static volatile long lastSwordHitLogMs = 0L;
    private static volatile long lastNoAttackerLogMs = 0L;
    private static volatile long lastSkipLogMs = 0L;

    public SwordMasteryVulnerableDamageTakenSystem() {
        super(Damage.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Run in FilterDamageGroup so the damage amount is adjusted before inspect/UI systems process it.
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
        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityId);
        if (victimRef == null || damage == null) {
            return;
        }

        boolean attemptedApplyFromSwordHit = false;

        // 1) Best-effort: if this Damage has an EntitySource attacker, and attacker is a sword user with level>0,
        // apply/refresh the vulnerable tag to the victim FIRST (so the same hit can get multiplied).
        Ref<EntityStore> attackerRef = WeaponEffectEngine.tryGetEntityAttackerRef(damage);
        if (attackerRef == null) {
            // Useful debug: many damage sources are not entity-attacks (environment, etc.)
            long now = System.currentTimeMillis();
            if (now - lastNoAttackerLogMs >= 5000L) {
                lastNoAttackerLogMs = now;

                Damage.Source src = damage.getSource();
                String srcType = (src == null) ? "null" : src.getClass().getName();

                LOGGER.atInfo().log("[SwordMastery] Damage had no EntitySource attacker. victimEntityId=" + entityId
                        + " sourceType=" + srcType
                        + " amount=" + damage.getAmount());
            }
        } else {
            PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
            if (attackerPlayerRef == null) {
                throttleSkip("missing PlayerRef on attacker", entityId);
            } else {
                UUID attackerUuid = attackerPlayerRef.getUuid();
                if (attackerUuid == null) {
                    throttleSkip("attacker UUID null", entityId);
                } else {
                    MutationsState state = MutationsRepository.getOrLoadState(attackerUuid);
                    int swordLevel = state.getSwordLevel();
                    if (swordLevel > 0) {
                        // Confirm the attacker is actually holding a sword.
                        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
                        if (attackerPlayer == null) {
                            throttleSkip("missing Player component on attacker", entityId);
                        } else {
                            Inventory inv = attackerPlayer.getInventory();
                            if (inv == null) {
                                throttleSkip("attacker inventory null", entityId);
                            } else {
                                ItemStack inHand = inv.getItemInHand();
                                if (inHand == null || inHand.isEmpty()) {
                                    throttleSkip("attacker hand empty", entityId);
                                } else {
                                    String itemId = inHand.getItemId();
                                    WeaponType weaponType = WeaponType.fromItemId(itemId);
                                    if (weaponType == WeaponType.SWORD) {
                                        float multiplier = WeaponEffectDefinitions.damageTakenMultiplierForSwordLevel(swordLevel);
                                        float durationSeconds = WeaponEffectDefinitions.vulnerableDurationSeconds();

                                        // Resolve the effect asset and apply it to victim.
                                        String effectId = WeaponEffectDefinitions.vulnerableEffectIdForSwordLevel(swordLevel);
                                        int effectIndex = EntityEffect.getAssetMap().getIndexOrDefault(effectId, -1);
                                        if (effectIndex < 0) {
                                            WeaponEffectEngine.logMissingWeakenEffectOnce(LOGGER, effectId);
                                        } else {
                                            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
                                            if (effect == null) {
                                                WeaponEffectEngine.logMissingWeakenEffectOnce(LOGGER, effectId);
                                            } else {
                                                EffectControllerComponent effects = store.getComponent(victimRef, EffectControllerComponent.getComponentType());
                                                if (effects == null) {
                                                    throttleSkip("victim has no EffectControllerComponent", entityId);
                                                } else {
                                                    attemptedApplyFromSwordHit = true;

                                                    // Throttled info log when we detect a valid sword hit.
                                                    long now = System.currentTimeMillis();
                                                    if (now - lastSwordHitLogMs >= 400L) {
                                                        lastSwordHitLogMs = now;

                                                        LOGGER.atInfo().log("[SwordMastery] Sword hit detected -> applying vulnerable tag. attackerUuid=" + attackerUuid
                                                                + " victimEntityId=" + entityId
                                                                + " swordLevel=" + swordLevel
                                                                + " multiplier=" + multiplier
                                                                + " durationSeconds=" + durationSeconds
                                                                + " itemId=" + itemId
                                                                + " damageAmount(beforeHook)=" + damage.getAmount());
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
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2) Global: multiply ANY damage if victim has a vulnerable tier tag active.
        // This is intentionally AFTER the sword-hit tag application so the same hit can be multiplied too.
        WeaponEffectEngine.onAnyDamage(entityId, victimRef, store, damage);

        // Optional: extra throttled signal for debugging ordering, if you want.
        // (I left it out to avoid more spam; your existing logs are enough.)
    }

    private static void throttleSkip(String reason, int victimEntityId) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs >= 5000L) {
            lastSkipLogMs = now;
            LOGGER.atInfo().log("[SwordMastery] Skipping apply: " + reason + " victimEntityId=" + victimEntityId);
        }
    }
}