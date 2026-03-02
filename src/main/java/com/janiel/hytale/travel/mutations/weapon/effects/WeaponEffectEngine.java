package com.janiel.hytale.travel.mutations.weapon.effects;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime engine for Sword Mastery vulnerable damage scaling.
 *
 * If the victim has a vulnerable tier tag active (T1/T2/T3), incoming damage is multiplied accordingly.
 */
public final class WeaponEffectEngine {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final AtomicBoolean MISSING_EFFECT_LOGGED = new AtomicBoolean(false);

    // Throttles to prevent log spam.
    private static volatile long lastMultiplyLogMs = 0L;

    private WeaponEffectEngine() {
    }

    public static void logMissingEffectOnce(HytaleLogger logger, String effectId) {
        if (logger == null) return;
        if (MISSING_EFFECT_LOGGED.compareAndSet(false, true)) {
            logger.atWarning().log("[SwordMastery] Missing EntityEffect asset: id=" + effectId
                    + " (expected in Server/Entity/Effects/Status/" + effectId + ".json)");
        }
    }

    /**
     * Global damage hook:
     * If victim has any of the vulnerable tier tags active, multiply incoming damage accordingly.
     */
    public static void onAnyDamage(
            int victimEntityId,
            Ref<EntityStore> victimRef,
            Store<EntityStore> store,
            Damage damage
    ) {
        if (damage == null) return;

        EffectControllerComponent effects = store.getComponent(victimRef, EffectControllerComponent.getComponentType());
        if (effects == null) {
            return;
        }

        // Resolve indices for each tier id and check actives.
        // Prefer highest tier if multiple are present (shouldn't happen, but be defensive).
        String t3Id = WeaponEffectDefinitions.vulnerableEffectIdForSwordLevel(3);
        int t3Index = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
                .getAssetMap()
                .getIndexOrDefault(t3Id, -1);

        if (t3Index >= 0 && effects.getActiveEffects().containsKey(t3Index)) {
            applyMultiplier(victimEntityId, damage, 1.30f);
            return;
        }

        String t2Id = WeaponEffectDefinitions.vulnerableEffectIdForSwordLevel(2);
        int t2Index = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
                .getAssetMap()
                .getIndexOrDefault(t2Id, -1);

        if (t2Index >= 0 && effects.getActiveEffects().containsKey(t2Index)) {
            applyMultiplier(victimEntityId, damage, 1.20f);
            return;
        }

        String t1Id = WeaponEffectDefinitions.vulnerableEffectIdForSwordLevel(1);
        int t1Index = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
                .getAssetMap()
                .getIndexOrDefault(t1Id, -1);

        if (t1Index >= 0 && effects.getActiveEffects().containsKey(t1Index)) {
            applyMultiplier(victimEntityId, damage, 1.10f);
        }
    }

    private static void applyMultiplier(int victimEntityId, Damage damage, float multiplier) {
        float initialAmount = damage.getAmount();
        float newAmount = initialAmount * multiplier;

        damage.putMetaObject(Damage.CAN_BE_PREDICTED, false);
        damage.setAmount(newAmount);

        long now = System.currentTimeMillis();
        if (now - lastMultiplyLogMs >= 250L) {
            lastMultiplyLogMs = now;

            Damage.Source src = damage.getSource();
            String srcType = (src == null) ? "null" : src.getClass().getName();

            LOGGER.atInfo().log("[SwordMastery] Applied vulnerable damage multiplier. victimEntityId=" + victimEntityId
                    + " sourceType=" + srcType
                    + " multiplier=" + multiplier
                    + " initialAmount=" + initialAmount
                    + " newAmount=" + newAmount);
        }
    }

    /**
     * Best-effort attacker extraction. Only EntitySource guarantees a direct entity ref.
     * (Some damage sources are environmental/projectile/etc.)
     *
     * Verified in your HytaleServer.zip:
     *   com.hypixel.hytale.server.core.modules.entity.damage.Damage$EntitySource
     */
    public static Ref<EntityStore> tryGetEntityAttackerRef(Damage damage) {
        if (damage == null) return null;

        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource) {
            return ((Damage.EntitySource) source).getRef();
        }

        return null;
    }
}