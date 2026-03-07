package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectDefinitions;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectEngine;

import java.util.UUID;

/**
 * Battleaxe Mastery (weapon_axe but restricted to battleaxe item ids):
 * - When a player hits with a battleaxe and has axe level > 0, apply a "weaken" tag (EntityEffect) to the victim.
 * - While the ATTACKER has this weaken tag active, their outgoing damage is reduced (victim receives less damage).
 *
 * Notes:
 * - No combos, no windows. Just an effect tag and one rule in FilterDamageGroup.
 * - Uses CAN_BE_PREDICTED=false + setAmount() same as Sword mastery for consistent indicators.
 */
public final class BattleaxeMasteryWeakenDamageTakenSystem extends EntityEventSystem<EntityStore, Damage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Throttles to prevent log spam.
    private static volatile long lastApplyLogMs = 0L;
    private static volatile long lastNoAttackerLogMs = 0L;
    private static volatile long lastSkipLogMs = 0L;

    public BattleaxeMasteryWeakenDamageTakenSystem() {
        super(Damage.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Must run in FilterDamageGroup so setAmount() is not overwritten later
        // and the final damage number/health change matches what we compute (same as Sword).
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

        // 1) If this damage comes from an entity attacker holding a BATTLEAXE, apply/refresh weaken on the victim.
        Ref<EntityStore> attackerRef = WeaponEffectEngine.tryGetEntityAttackerRef(damage);
        if (attackerRef == null) {
            long now = System.currentTimeMillis();
            if (now - lastNoAttackerLogMs >= 5000L) {
                lastNoAttackerLogMs = now;

                Damage.Source src = damage.getSource();
                String srcType = (src == null) ? "null" : src.getClass().getName();

                LOGGER.atInfo().log("[BattleaxeMastery] Damage had no EntitySource attacker. victimEntityId=" + entityId
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

                    // Battleaxe is grouped under AXE in your mutation model.
                    int axeLevel = state.getAxeLevel();
                    if (axeLevel > 0) {
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
                                    if (isBattleaxeItemId(itemId)) {
                                        float durationSeconds = WeaponEffectDefinitions.weakenDurationSecondsForAxeLevel(axeLevel);
                                        float damageDealtMultiplier = WeaponEffectDefinitions.damageDealtMultiplierWhileWeakened(axeLevel);

                                        String effectId = WeaponEffectDefinitions.weakenEffectIdForAxeLevel(axeLevel);
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
                                                    long now = System.currentTimeMillis();
                                                    if (now - lastApplyLogMs >= 400L) {
                                                        lastApplyLogMs = now;

                                                        LOGGER.atInfo().log("[BattleaxeMastery] Battleaxe hit detected -> applying weaken tag. attackerUuid=" + attackerUuid
                                                                + " victimEntityId=" + entityId
                                                                + " axeLevel=" + axeLevel
                                                                + " damageDealtMultiplierWhileWeakened=" + damageDealtMultiplier
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

        // 2) Global rule: if the attacker is currently Weakened, reduce this damage amount.
        WeaponEffectEngine.reduceDamageIfAttackerWeakened(entityId, store, damage);
    }

    private static void throttleSkip(String reason, int victimEntityId) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs >= 5000L) {
            lastSkipLogMs = now;
            LOGGER.atInfo().log("[BattleaxeMastery] Skipping apply: " + reason + " victimEntityId=" + victimEntityId);
        }
    }

    private static boolean isBattleaxeItemId(String itemId) {
        if (itemId == null) return false;
        String n = itemId.trim().toLowerCase();
        return !n.isEmpty() && n.contains("battleaxe");
    }
}