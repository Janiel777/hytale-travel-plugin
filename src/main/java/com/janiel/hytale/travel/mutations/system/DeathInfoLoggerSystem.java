package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.meta.IMetaStoreImpl;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
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
 *
 * Feature:
 * - Increments per-weapon kill counters
 * - Shows Event Title + sound when a weapon level increases
 */
public final class DeathInfoLoggerSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Same sound as other mutation level-ups
    private static final String LEVEL_UP_SOUND_ID = "SFX_Discovery_Z1_Medium";

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

            // Extract attackerRef from source.*sourceRef
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

            // Read old level BEFORE increment
            MutationsState before = MutationsRepository.getOrLoadState(attackerUuid);
            int oldLevel = getWeaponLevel(before, weaponType);

            // Increment kills + update persisted level
            MutationsState after = MutationsRepository.incrementWeaponKillAndGetState(attackerUuid, weaponType);
            int newLevel = getWeaponLevel(after, weaponType);

            // Level up -> show title + sound
            if (newLevel > oldLevel && attackerPlayerRef != null) {
                showWeaponLevelUp(attackerPlayerRef, weaponType, newLevel);
            }

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
                            + " gunKills=" + after.getGunKills()
                            + " magicKills=" + after.getMagicKills()
                            + " throwableKills=" + after.getThrowableKills()
                            + " swordLevel=" + after.getSwordLevel()
                            + " axeLevel=" + after.getAxeLevel()
                            + " maceLevel=" + after.getMaceLevel()
                            + " spearLevel=" + after.getSpearLevel()
                            + " daggerLevel=" + after.getDaggerLevel()
                            + " bowLevel=" + after.getBowLevel()
                            + " crossbowLevel=" + after.getCrossbowLevel()
                            + " gunLevel=" + after.getGunLevel()
                            + " magicLevel=" + after.getMagicLevel()
                            + " throwableLevel=" + after.getThrowableLevel()
            );

        } catch (Throwable t) {
            LOGGER.atWarning().log("[DeathInfoLogger] exception: " + t);
        }
    }

    private static int getWeaponLevel(MutationsState state, WeaponType weaponType) {
        switch (weaponType) {
            case SWORD:
                return state.getSwordLevel();
            case AXE:
                return state.getAxeLevel();
            case MACE:
                return state.getMaceLevel();
            case SPEAR:
                return state.getSpearLevel();
            case DAGGER:
                return state.getDaggerLevel();
            case BOW:
                return state.getBowLevel();
            case CROSSBOW:
                return state.getCrossbowLevel();
            case GUN:
                return state.getGunLevel();
            case MAGIC:
                return state.getMagicLevel();
            case THROWABLE:
                return state.getThrowableLevel();
            default:
                return 0;
        }
    }

    private static void showWeaponLevelUp(PlayerRef playerRef, WeaponType weaponType, int newLevel) {
        String title = weaponTitle(weaponType);

        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw(title),
                Message.raw("Level " + newLevel),
                true
        );

        int soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(LEVEL_UP_SOUND_ID, SoundEvent.EMPTY_ID);
        if (soundIndex != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
        }
    }

    private static String weaponTitle(WeaponType weaponType) {
        switch (weaponType) {
            case SWORD:
                return "Sword Mutation";
            case AXE:
                return "Axe Mutation";
            case MACE:
                return "Mace Mutation";
            case SPEAR:
                return "Spear Mutation";
            case DAGGER:
                return "Dagger Mutation";
            case BOW:
                return "Bow Mutation";
            case CROSSBOW:
                return "Crossbow Mutation";
            case GUN:
                return "Gun Mutation";
            case MAGIC:
                return "Magic Mutation";
            case THROWABLE:
                return "Throwable Mutation";
            default:
                return "Weapon Mutation";
        }
    }

    /**
     * Best-effort: tries to read a field called "sourceRef" from the source object (or its superclasses).
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