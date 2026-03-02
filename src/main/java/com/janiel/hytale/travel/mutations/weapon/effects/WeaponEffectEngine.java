package com.janiel.hytale.travel.mutations.weapon.effects;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime engine for Sword Mastery vulnerable marking and damage scaling.
 *
 * Why we keep a runtime map:
 * - The EntityEffect is only a tag (marker). It doesn't carry the multiplier.
 * - The multiplier depends on the LAST attacker who overwrote the tag (Overwrite behavior),
 *   so we store multiplier+expiry per victim entityId.
 */
public final class WeaponEffectEngine {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final AtomicBoolean MISSING_EFFECT_LOGGED = new AtomicBoolean(false);

    // Throttles to prevent log spam.
    private static volatile long lastMarkLogMs = 0L;
    private static volatile long lastMultiplyLogMs = 0L;
    private static volatile long lastStaleLogMs = 0L;
    private static volatile long lastExpiredLogMs = 0L;

    private static final class Mark {
        final long expireAtMs;
        final float multiplier;

        Mark(long expireAtMs, float multiplier) {
            this.expireAtMs = expireAtMs;
            this.multiplier = multiplier;
        }
    }

    // Keyed by victim entityId (fast + stable within a short duration window).
    private static final ConcurrentHashMap<Integer, Mark> VICTIM_MARKS = new ConcurrentHashMap<>();

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
     * Marks the victim in a fast map so onAnyDamage can scale damage without recomputing.
     * Overwrite behavior: newest mark wins.
     */
    public static void markVictim(int victimEntityId, int attackerSwordLevel) {
        float multiplier = WeaponEffectDefinitions.damageTakenMultiplierForSwordLevel(attackerSwordLevel);
        if (multiplier == 1.0f) {
            return;
        }

        long now = System.currentTimeMillis();
        long expireAt = now + WeaponEffectDefinitions.vulnerableDurationMs();
        VICTIM_MARKS.put(victimEntityId, new Mark(expireAt, multiplier));

        if (now - lastMarkLogMs >= 500L) {
            lastMarkLogMs = now;
            LOGGER.atInfo().log("[SwordMastery] Marked victim as vulnerable. victimEntityId=" + victimEntityId
                    + " swordLevel=" + attackerSwordLevel
                    + " multiplier=" + multiplier
                    + " expiresInMs=" + (expireAt - now));
        }
    }

    /**
     * Global damage hook: if victim is currently marked, multiply incoming damage.
     *
     * We also confirm the victim has the actual EntityEffect active to avoid stale marks if the engine removed it.
     */
    public static void onAnyDamage(
            int victimEntityId,
            Ref<EntityStore> victimRef,
            Store<EntityStore> store,
            Damage damage
    ) {
        if (damage == null) return;

        Mark mark = VICTIM_MARKS.get(victimEntityId);
        if (mark == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > mark.expireAtMs) {
            VICTIM_MARKS.remove(victimEntityId);

            if (now - lastExpiredLogMs >= 2000L) {
                lastExpiredLogMs = now;
                LOGGER.atInfo().log("[SwordMastery] Mark expired -> removed. victimEntityId=" + victimEntityId);
            }
            return;
        }

        // Double-check the engine still has the tag active.
        EffectControllerComponent effects = store.getComponent(victimRef, EffectControllerComponent.getComponentType());
        if (effects == null) {
            return;
        }

        int effectIndex = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
                .getAssetMap()
                .getIndexOrDefault(WeaponEffectDefinitions.vulnerableEffectId(), -1);

        if (effectIndex < 0) {
            return;
        }

        if (!effects.getActiveEffects().containsKey(effectIndex)) {
            // Tag is gone, so clear stale mark.
            VICTIM_MARKS.remove(victimEntityId);

            if (now - lastStaleLogMs >= 2000L) {
                lastStaleLogMs = now;
                LOGGER.atInfo().log("[SwordMastery] Stale mark cleared (tag not active). victimEntityId=" + victimEntityId
                        + " effectId=" + WeaponEffectDefinitions.vulnerableEffectId()
                        + " effectIndex=" + effectIndex);
            }
            return;
        }

        float initialAmount = damage.getAmount();
        float newAmount = initialAmount * mark.multiplier;

        damage.putMetaObject(Damage.CAN_BE_PREDICTED, false);
        damage.setAmount(newAmount);

        if (now - lastMultiplyLogMs >= 250L) {
            lastMultiplyLogMs = now;

            Damage.Source src = damage.getSource();
            String srcType = (src == null) ? "null" : src.getClass().getName();

            LOGGER.atInfo().log("[SwordMastery] Applied vulnerable damage multiplier. victimEntityId=" + victimEntityId
                    + " sourceType=" + srcType
                    + " multiplier=" + mark.multiplier
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