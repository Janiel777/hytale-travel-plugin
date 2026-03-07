package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.weapon.effects.WeaponEffectDefinitions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DaggerMasteryBleedTickSystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ActiveEntityEffect BLEED_SOURCE = new ActiveEntityEffect();

    private static final class BleedEntry {
        final Ref<EntityStore> ref;

        int tier;              // 1..MAX
        int daggerLevel;       // 1..3
        int damageCauseIndex;  // copied from hit

        long expireAtMs;       // hard expiration (refresh on proc)
        long lastRefreshAtMs;  // last time we upgraded/refreshed
        long nextTickAtMs;     // next dot tick time
        long nextDecayAtMs;    // next tier decay time

        BleedEntry(Ref<EntityStore> ref) {
            this.ref = ref;
        }
    }

    private static final Map<Integer, BleedEntry> BLEEDS = new ConcurrentHashMap<>();

    public DaggerMasteryBleedTickSystem() {
        // 10x per second, same cadence as stun revert.
        super(0.05f);
    }

    public static void markBleeding(
            int victimEntityId,
            Ref<EntityStore> victimRef,
            int daggerLevel,
            int damageCauseIndex,
            long nowMs
    ) {
        if (victimRef == null) return;

        BLEEDS.compute(victimEntityId, (k, existing) -> {
            BleedEntry e = existing;
            if (e == null) {
                e = new BleedEntry(victimRef);
                e.tier = 0;
            }

            // Upgrade tier and clamp.
            int maxTier = WeaponEffectDefinitions.bleedMaxTier();
            e.tier = Math.min(maxTier, e.tier + 1);

            e.daggerLevel = daggerLevel;
            e.damageCauseIndex = damageCauseIndex;

            // Refresh hard duration on every successful proc.
            long durationMs = WeaponEffectDefinitions.bleedDurationMsForDaggerLevel(daggerLevel);
            e.expireAtMs = nowMs + durationMs;

            // Update refresh time (used for decay).
            e.lastRefreshAtMs = nowMs;

            // Tick + decay scheduling.
            long tickMs = WeaponEffectDefinitions.bleedTickIntervalMs();
            // Always schedule the next tick from now on proc/refresh.
            e.nextTickAtMs = nowMs + tickMs;

            long graceMs = WeaponEffectDefinitions.bleedDecayGraceMs();
            long decayStepMs = WeaponEffectDefinitions.bleedDecayStepMs();
            e.nextDecayAtMs = nowMs + graceMs + decayStepMs;

            return e;
        });
    }

    public static void refreshBleedDecayOnHit(int victimEntityId, long nowMs) {
        BleedEntry entry = BLEEDS.get(victimEntityId);
        if (entry == null) return;

        // Reset the decay timer on any dagger hit (even if proc failed).
        entry.lastRefreshAtMs = nowMs;

        long graceMs = WeaponEffectDefinitions.bleedDecayGraceMs();
        long decayStepMs = WeaponEffectDefinitions.bleedDecayStepMs();
        entry.nextDecayAtMs = nowMs + graceMs + decayStepMs;
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
        BleedEntry entry = BLEEDS.get(entityId);
        if (entry == null) return;

        long now = System.currentTimeMillis();

        Ref<EntityStore> ref = entry.ref;
        if (ref == null || !ref.isValid()) {
            BLEEDS.remove(entityId);
            return;
        }

        // Hard expiration (no matter what).
        if (now >= entry.expireAtMs) {
            BLEEDS.remove(entityId);
            LOGGER.atInfo().log("[DaggerMastery] Bleed expired (hard). victimEntityId=" + entityId);
            return;
        }

        // Decay tiers if we stopped refreshing.
//        long graceMs = WeaponEffectDefinitions.bleedDecayGraceMs();
        long decayStepMs = WeaponEffectDefinitions.bleedDecayStepMs();

        if (now >= entry.nextDecayAtMs) {
            entry.tier -= 1;
            entry.nextDecayAtMs = now + decayStepMs;

            if (entry.tier <= 0) {
                BLEEDS.remove(entityId);
                LOGGER.atInfo().log("[DaggerMastery] Bleed removed (tier reached 0). victimEntityId=" + entityId);
                return;
            }
        }

        // Periodic damage tick.
        if (now < entry.nextTickAtMs) {
            return;
        }

        entry.nextTickAtMs = now + WeaponEffectDefinitions.bleedTickIntervalMs();

        float amount = WeaponEffectDefinitions.bleedDamagePerTick(entry.daggerLevel, entry.tier);
        if (amount <= 0.0f) return;

        Damage dot = new Damage(BLEED_SOURCE, entry.damageCauseIndex, amount);

        // Execute damage through engine systems.
        // Use the Ref overload in delayed systems to guarantee the correct target.
        DamageSystems.executeDamage(ref, commandBuffer, dot);

        LOGGER.atInfo().log("[DaggerMastery] Bleed tick. victimEntityId=" + entityId
                + " tier=" + entry.tier
                + " daggerLevel=" + entry.daggerLevel
                + " amount=" + amount);
    }
}