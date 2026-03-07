package com.janiel.hytale.travel.bridges;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.net.BackendClient;
import com.janiel.hytale.travel.persistence.EngineWriteProbeRegistry;
import com.janiel.hytale.travel.persistence.InventorySessionStore;
import com.janiel.hytale.travel.persistence.PlayerStateFiles;
import com.janiel.hytale.travel.util.ConnectionKeyUtil;
import com.janiel.hytale.travel.util.PlayerIdUtil;
import com.janiel.hytale.travel.mutations.persistence.MutationsCache;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import java.util.UUID;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;

/**
 * On player setup/connect, acquire the inventory lock from backend, apply the backend snapshot locally,
 * and remember expectedVersion for later save.
 *
 * Uses reflection for event types and uuid extraction (patchline-safe).
 */
public final class InventoryAcquireBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TravelConfig cfg;
    private final BackendClient backend;

    public InventoryAcquireBridge(TravelConfig cfg, BackendClient backend) {
        this.cfg = cfg;
        this.backend = backend;
    }

    public void register(Object eventRegistry) {
        boolean okSetup = tryRegister(eventRegistry, "PlayerSetupConnectEvent", new String[] {
                "com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.events.player.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.event.player.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.universe.events.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.universe.event.PlayerSetupConnectEvent"
        });

        if (!okSetup) {
            LOGGER.atWarning().log("Inventory acquire hook NOT installed: no supported setup/connect event class found in this patchline.");
        }
    }

    private boolean tryRegister(Object eventRegistry, String label, String[] classNames) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = eventRegistry.getClass().getClassLoader();
        }

        Method register = findRegisterMethod(eventRegistry.getClass());
        if (register == null) {
            LOGGER.atWarning().log("Could not find EventRegistry.register(..) method; inventory acquire hook disabled");
            return false;
        }

        for (String name : classNames) {
            try {
                Class<?> eventClass = Class.forName(name, false, cl);
                register.invoke(eventRegistry, eventClass, (java.util.function.Consumer<Object>) this::onSetupConnect);
                LOGGER.atInfo().log("Registered inventory acquire listener for " + label + " using class: " + name);
                return true;
            } catch (ClassNotFoundException ignored) {
                // keep trying
            } catch (Throwable t) {
                LOGGER.atWarning().log("Failed to register inventory acquire listener for " + label + " class=" + name + " error=" + t);
            }
        }

        LOGGER.atInfo().log("No supported class found for " + label + " (tried " + classNames.length + " candidates)");
        return false;
    }

    private static Method findRegisterMethod(Class<?> registryClass) {
        for (Method m : registryClass.getMethods()) {
            if (!m.getName().equals("register")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 2) continue;
            if (p[0] != Class.class) continue;
            if (!java.util.function.Consumer.class.isAssignableFrom(p[1])) continue;
            return m;
        }
        return null;
    }

    private void onSetupConnect(Object event) {
        try {
            String serverId = cfg.resolveCurrentServerId();
            if (serverId == null || serverId.isBlank()) {
                LOGGER.atWarning().log("Inventory acquire skipped: resolveCurrentServerId() returned null. Ensure travel.properties has serverIdByGamePort.<port>=<serverId> and the server is launched with either --port or --bind including the port.");
                return;
            }

            String playerUuid = tryExtractPlayerUuid(event);
            if (playerUuid == null || playerUuid.isBlank()) {
                LOGGER.atWarning().log("Inventory acquire skipped: could not extract playerUuid from setup event. eventClass=" + event.getClass().getName());
                return;
            }

            String connectionKey = ConnectionKeyUtil.tryExtractConnectionKey(event);

            // Ensure EngineWriteProbe is running so FINAL_PERSIST gate can observe the post-disconnect engine write.
            try {
                if (!EngineWriteProbeRegistry.isRunning(playerUuid)) {
                    boolean started = EngineWriteProbeRegistry.startProbe(cfg.getUniverseDir(), playerUuid, 25L, 2);
                    LOGGER.atInfo().log("AUTO_PROBE_START playerUuid=" + playerUuid + " started=" + started);
                }
            } catch (Throwable t) {
                LOGGER.atWarning().log("AUTO_PROBE_START_FAILED playerUuid=" + playerUuid + " error=" + t);
            }

            long startMs = System.currentTimeMillis();
            int attempts = 0;

            while (true) {
                attempts++;

                try {
//                  BackendClient.InventorySessionAcquireResult res = backend.inventorySessionAcquire(playerUuid, serverId);
                    BackendClient.ProfileSessionAcquireResult res = backend.profileSessionAcquire(playerUuid, serverId);

                    long lockExpMs = parseIsoToEpochMs(res.lockExpiresAtIso);
                    InventorySessionStore.put(new InventorySessionStore.Session(
                            playerUuid,
                            serverId,
                            connectionKey,
                            res.version,
                            lockExpMs,
                            startMs
                    ));

                    Path universeDir = cfg.getUniverseDir();
                    Path written = PlayerStateFiles.writeInventoryOnlySnapshot(universeDir, playerUuid, res.inventoryJson);

                    // Apply mutations JSON from backend into local disk + invalidate cache
                    try {
                        UUID u = UUID.fromString(playerUuid);
                        MutationsRepository.overwriteFromRawJson(u, res.mutationsJson);
                        MutationsCache.invalidate(u);
                    } catch (Exception ignore) {
                        // best-effort
                    }

                    long tookMs = System.currentTimeMillis() - startMs;
                    LOGGER.atInfo().log("INVENTORY_ACQUIRE_OK playerUuid=" + playerUuid
                            + " serverId=" + serverId
                            + " version=" + res.version
                            + " lockExpiresAt=" + res.lockExpiresAtIso
                            + " wrote=" + written
                            + " attempts=" + attempts
                            + " tookMs=" + tookMs
                            + " connectionKey=" + (connectionKey == null ? "<none>" : connectionKey));

                    // Start engine write probe automatically so FinalPersistGate can detect the final persist.
                    try {
                        boolean started = EngineWriteProbeRegistry.startProbe(
                                cfg.getUniverseDir(),
                                playerUuid,
                                50L,
                                3
                        );
                        LOGGER.atInfo().log("ENGINE_WRITE_PROBE_AUTO_START playerUuid=" + playerUuid
                                + " started=" + started
                                + " intervalMs=50 readsPerTick=3");
                    } catch (Exception e) {
                        LOGGER.atWarning().log("ENGINE_WRITE_PROBE_AUTO_START_FAILED playerUuid=" + playerUuid + " error=" + e);
                    }

                    return;
                } catch (Exception ex) {
                    String msg = ex.getMessage() == null ? "" : ex.getMessage();
                    boolean locked = msg.contains("\"error\": \"LOCKED\"") || msg.contains("\"error\":\"LOCKED\"") || msg.contains("LOCKED");
                    long waitedMs = System.currentTimeMillis() - startMs;

                    if (locked && waitedMs < 25000) {
                        if (attempts == 1 || attempts % 10 == 0) {
                            LOGGER.atInfo().log("INVENTORY_ACQUIRE_LOCKED playerUuid=" + playerUuid
                                    + " serverId=" + serverId
                                    + " attempts=" + attempts
                                    + " waitedMs=" + waitedMs);
                        }
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }

                    LOGGER.atWarning().log("INVENTORY_ACQUIRE_FAILED playerUuid=" + playerUuid
                            + " serverId=" + serverId
                            + " attempts=" + attempts
                            + " waitedMs=" + waitedMs
                            + " error=" + ex);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.atWarning().log("Inventory acquire handler failed: " + t);
        }
    }

    private static long parseIsoToEpochMs(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String tryExtractPlayerUuid(Object event) {
        Object v;

        v = invokeNoArg(event, "getPlayerUuid");
        String s = coerceToUuidString(v);
        if (s != null) return s;

        v = invokeNoArg(event, "getUuid");
        s = coerceToUuidString(v);
        if (s != null) return s;

        v = invokeNoArg(event, "getPlayerId");
        s = coerceToUuidString(v);
        if (s != null) return s;

        v = invokeNoArg(event, "getPlayerRef");
        if (v instanceof PlayerRef) {
            return PlayerIdUtil.getPlayerUuid((PlayerRef) v);
        }

        v = invokeNoArg(event, "getPlayer");
        if (v != null) {
            Object u = invokeNoArg(v, "getPlayerUuid");
            s = coerceToUuidString(u);
            if (s != null) return s;

            u = invokeNoArg(v, "getUuid");
            s = coerceToUuidString(u);
            if (s != null) return s;
        }

        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String coerceToUuidString(Object v) {
        if (v == null) return null;
        if (v instanceof java.util.UUID) {
            return v.toString();
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }
}
