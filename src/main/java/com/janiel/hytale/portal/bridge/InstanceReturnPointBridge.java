package com.janiel.hytale.portal.bridge;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import java.util.Map;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InstanceReturnPointBridge {

    private static final boolean DEBUG = true;

    private static final class ReturnData {
        private final UUID worldUuid;
        private final Transform transform;

        private ReturnData(@Nonnull UUID worldUuid, @Nonnull Transform transform) {
            this.worldUuid = worldUuid;
            this.transform = transform;
        }
    }

    private static final ConcurrentHashMap<UUID, ReturnData> RETURN_BY_PLAYER = new ConcurrentHashMap<>();

    // World-scoped events (Add/Drain) are fired on World.getEventRegistry(), not the plugin EventRegistry.
    // Keep track of which worlds we already hooked to avoid duplicate registrations.
    private static final ConcurrentHashMap<UUID, Boolean> WORLD_REGISTRATION_GUARD = new ConcurrentHashMap<>();

    private InstanceReturnPointBridge() {}

    public static void rememberReturnPoint(@Nonnull UUID playerUuid,
                                           @Nonnull UUID worldUuid,
                                           @Nonnull Transform transform) {
        // IMPORTANT:
        // Transform instances are mutable/reused by the engine. Clone it to freeze the snapshot.
        RETURN_BY_PLAYER.put(playerUuid, new ReturnData(worldUuid, transform.clone()));

        if (DEBUG) {
            System.out.println("[InstanceReturnBridge] rememberReturnPoint player=" + playerUuid
                    + " world=" + worldUuid + " transform=" + transform);
        }
    }

    public static void register(@Nonnull Object eventRegistry) {
        boolean drainOk = tryRegister(eventRegistry, "registerGlobal", DrainPlayerFromWorldEvent.class, InstanceReturnPointBridge::onDrain)
                || tryRegister(eventRegistry, "register", DrainPlayerFromWorldEvent.class, InstanceReturnPointBridge::onDrain);

        boolean addOk = tryRegister(eventRegistry, "registerGlobal", AddPlayerToWorldEvent.class, InstanceReturnPointBridge::onAdd)
                || tryRegister(eventRegistry, "register", AddPlayerToWorldEvent.class, InstanceReturnPointBridge::onAdd);

        if (DEBUG) {
            System.out.println("[InstanceReturnBridge] Registered handlers. drainOk=" + drainOk + " addOk=" + addOk);
        }
    }

    private static boolean tryRegister(@Nonnull Object eventRegistry,
                                       @Nonnull String methodName,
                                       @Nonnull Class<?> eventClass,
                                       @Nonnull Consumer<Object> handler) {
        try {
            eventRegistry.getClass()
                    .getMethod(methodName, Class.class, Consumer.class)
                    .invoke(eventRegistry, eventClass, handler);
            return true;
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] tryRegister failed method=" + methodName
                        + " event=" + eventClass.getName()
                        + " err=" + e);
            }
            return false;
        }
    }

    private static void onDrain(@Nonnull Object eventObj) {
        if (!(eventObj instanceof DrainPlayerFromWorldEvent)) {
            return;
        }

        DrainPlayerFromWorldEvent ev = (DrainPlayerFromWorldEvent) eventObj;

        if (DEBUG) {
            System.out.println("[InstanceReturnBridge] onDrain: FIRED world=" + ev.getWorld());
        }

        Holder<EntityStore> holder = ev.getHolder();

        // We can get PlayerRef from the holder; then UUID from PlayerRef (per your API index).
        PlayerRef pr;
        try {
            pr = holder.ensureAndGetComponent(PlayerRef.getComponentType());
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] onDrain: failed to resolve PlayerRef: " + e);
            }
            return;
        }

        UUID playerUuid = pr.getUuid();

        ReturnData rd = RETURN_BY_PLAYER.get(playerUuid);
        if (rd == null) {
            return;
        }

        // Draining world UUID (you already noticed getWorldUuid() isn't there, so we use WorldConfig.getUuid()).
        UUID drainingWorldUuid;
        try {
            drainingWorldUuid = ev.getWorld().getWorldConfig().getUuid();
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] onDrain: failed to resolve drainingWorldUuid: " + e);
            }
            return;
        }

        // Only apply when we're draining FROM the instance (i.e., draining world != original return world).
        if (drainingWorldUuid.equals(rd.worldUuid)) {
            return;
        }

        World targetWorld = Universe.get().getWorld(rd.worldUuid);
        if (targetWorld == null) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] onDrain: targetWorld was null for worldUuid=" + rd.worldUuid);
            }
            return;
        }

        // This is the key: override where the engine will place the player after draining.
        ev.setWorld(targetWorld);
        ev.setTransform(rd.transform.clone());

        // One-shot: consume it so it doesn't affect future drains.
        RETURN_BY_PLAYER.remove(playerUuid);

        if (DEBUG) {
            System.out.println("[InstanceReturnBridge] onDrain: OVERRIDE player=" + playerUuid
                    + " fromWorld=" + drainingWorldUuid
                    + " -> toWorld=" + rd.worldUuid
                    + " transform=" + rd.transform);
        }
    }

    private static void onAdd(@Nonnull Object eventObj) {
        if (!(eventObj instanceof AddPlayerToWorldEvent)) {
            return;
        }

        AddPlayerToWorldEvent ev = (AddPlayerToWorldEvent) eventObj;

        Holder<EntityStore> holder = ev.getHolder();

        PlayerRef pr;
        try {
            pr = holder.ensureAndGetComponent(PlayerRef.getComponentType());
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] onAdd: failed to resolve PlayerRef: " + e);
            }
            return;
        }

        UUID playerUuid = pr.getUuid();
        ReturnData rd = RETURN_BY_PLAYER.get(playerUuid);
        if (rd == null) {
            return;
        }

        UUID addWorldUuid;
        try {
            addWorldUuid = ev.getWorld().getWorldConfig().getUuid();
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] onAdd: failed to resolve addWorldUuid: " + e);
            }
            return;
        }

        // Only apply when the player is being added into the remembered return world.
        if (!addWorldUuid.equals(rd.worldUuid)) {
            return;
        }

        try {
            TransformComponent tc = holder.ensureAndGetComponent(TransformComponent.getComponentType());
            tc.teleportPosition(rd.transform.getPosition());
            tc.teleportRotation(rd.transform.getRotation());
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("[InstanceReturnBridge] onAdd: failed to apply TransformComponent: " + e);
            }
            return;
        }

        // One-shot: consume it so it doesn't affect future adds.
        RETURN_BY_PLAYER.remove(playerUuid);

        if (DEBUG) {
            System.out.println("[InstanceReturnBridge] onAdd: APPLIED_RETURN_POINT player=" + playerUuid
                    + " toWorld=" + addWorldUuid
                    + " transform=" + rd.transform);
        }
    }


    public static void registerToWorld(@Nonnull World world) {
        UUID worldUuid = world.getWorldConfig().getUuid();

        if (WORLD_REGISTRATION_GUARD.putIfAbsent(worldUuid, Boolean.TRUE) != null) {
            return;
        }

        register(world.getEventRegistry());

        if (DEBUG) {
            System.out.println("[InstanceReturnBridge] registerToWorld hooked world=" + worldUuid);
        }
    }

    public static void registerToAllLoadedWorlds() {
        Map<String, World> worlds = Universe.get().getWorlds();
        for (World w : worlds.values()) {
            if (w != null) {
                registerToWorld(w);
            }
        }
    }

}