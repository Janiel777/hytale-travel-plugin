package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.meta.IMetaStoreImpl;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;
import com.janiel.hytale.travel.mutations.weapon.WeaponType;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Debug/Instrumentation:
 * - Logs DeathComponent.getDeathInfo() (Damage)
 * - Resolves attackerRef from Damage source (best-effort, via sourceRef field like in your logs)
 * - Uses PlayerRef to log attacker UUID
 * - Uses Player.getInventory().getItemInHand() to log held item at death time
 */
public final class DeathInfoLoggerSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(
            Ref<EntityStore> ref,
            DeathComponent deathComponent,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            Damage damage = deathComponent.getDeathInfo();
            if (damage == null) {
                LOGGER.atInfo().log("[DeathInfoLogger] deathInfo=null");
                return;
            }

            Object cause = damage.getCause();
            Object source = damage.getSource();

            LOGGER.atInfo().log(
                    "[DeathInfoLogger] deadRef=" + ref
                            + " causeClass=" + safeClassName(cause)
                            + " sourceClass=" + safeClassName(source)
                            + " amount=" + damage.getAmount()
                            + " initialAmount=" + damage.getInitialAmount()
                            + " causeIndex=" + damage.getDamageCauseIndex()
            );

            if (cause != null) {
                LOGGER.atInfo().log("[DeathInfoLogger] causeDump=" + cause);
            }

            if (source != null) {
                LOGGER.atInfo().log("[DeathInfoLogger] sourceDump=" + source);
            }

            IMetaStoreImpl<Damage> metaStore = damage.getMetaStore();
            if (metaStore != null) {
                LOGGER.atInfo().log("[DeathInfoLogger] metaStore=" + metaStore);
            }

            // Extract attackerRef from source.*sourceRef (matches what you saw in logs: EntitySource.sourceRef=Ref@...)
            Ref<EntityStore> attackerRef = tryExtractSourceRef(source);
            if (attackerRef == null) {
                LOGGER.atInfo().log("[DeathInfoLogger] attackerRef=null (could not extract from source)");
                return;
            }

            // Attacker UUID from PlayerRef (NOT Player)
            PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
            UUID attackerUuid = (attackerPlayerRef == null) ? null : attackerPlayerRef.getUuid();

            LOGGER.atInfo().log(
                    "[DeathInfoLogger] attackerRef=" + attackerRef
                            + " attackerUuid=" + attackerUuid
                            + " attackerPlayerRef=" + (attackerPlayerRef == null ? "null" : attackerPlayerRef.getClass().getName())
            );

            if (attackerUuid == null) {
                return;
            }

            // If attacker is a Player entity, read inventory and item in hand
            Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
            if (attackerPlayer == null) {
                LOGGER.atInfo().log("[DeathInfoLogger] attackerPlayer=null (attacker is not a Player entity)");
                return;
            }

            Inventory inv = attackerPlayer.getInventory();
            if (inv == null) {
                LOGGER.atInfo().log("[DeathInfoLogger] attackerInventory=null");
                return;
            }

            ItemStack inHand = inv.getItemInHand();
            LOGGER.atInfo().log("[DeathInfoLogger] attackerItemInHand=" + summarizeItemStack(inHand));

            if (inHand == null || inHand.isEmpty()) {
                return;
            }

            String itemId = inHand.getItemId();
            WeaponType weaponType = WeaponType.fromItemId(itemId);
            if (weaponType == WeaponType.UNKNOWN) {
                LOGGER.atInfo().log("[WeaponMutations] weaponType=UNKNOWN itemId=" + itemId);
                return;
            }

            MutationsState after = MutationsRepository.incrementWeaponKillAndGetState(attackerUuid, weaponType);

            LOGGER.atInfo().log(
                    "[WeaponMutations] weaponType=" + weaponType
                            + " itemId=" + itemId
                            + " swordKills=" + after.getSwordKills()
                            + " axeKills=" + after.getAxeKills()
                            + " maceKills=" + after.getMaceKills()
                            + " spearKills=" + after.getSpearKills()
                            + " daggerKills=" + after.getDaggerKills()
                            + " bowKills=" + after.getBowKills()
                            + " crossbowKills=" + after.getCrossbowKills()
                            + " magicKills=" + after.getMagicKills()
                            + " throwableKills=" + after.getThrowableKills()
            );

        } catch (Throwable t) {
            LOGGER.atWarning().log("[DeathInfoLogger] exception: " + t);
        }
    }

    /**
     * Best-effort: Damage source classes in your logs include something like:
     * - Damage$EntitySource{EntitySource.sourceRef=Ref@...}
     * - Damage$ProjectileSource{..., EntitySource.sourceRef=Ref@...}
     *
     * So we try to read a field called "sourceRef" from the source object (or its superclasses).
     */
    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> tryExtractSourceRef(Object source) {
        if (source == null) return null;

        try {
            Field f = findFieldInHierarchy(source.getClass(), "sourceRef");
            if (f == null) return null;

            f.setAccessible(true);
            Object v = f.get(source);
            if (v instanceof Ref) {
                return (Ref<EntityStore>) v;
            }
        } catch (Throwable ignored) {
            // best-effort only
        }

        return null;
    }

    private static Field findFieldInHierarchy(Class<?> type, String fieldName) {
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static String summarizeItemStack(ItemStack stack) {
        if (stack == null) return "null";
        return "{itemId=" + stack.getItemId() + ", qty=" + stack.getQuantity() + ", empty=" + stack.isEmpty() + "}";
    }

    private static String safeClassName(Object o) {
        return (o == null) ? "null" : o.getClass().getName();
    }
}