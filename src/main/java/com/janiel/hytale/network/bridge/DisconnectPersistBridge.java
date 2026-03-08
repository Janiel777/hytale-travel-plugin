package com.janiel.hytale.network.bridge;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.janiel.hytale.network.persistence.EngineWriteProbeRegistry;
import com.janiel.hytale.network.persistence.FinalPersistGate;
import com.janiel.hytale.core.util.ConnectionKeyUtil;
import com.janiel.hytale.core.util.PlayerIdUtil;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

/**
 * Best-effort disconnect hook for instrumentation.
 *
 * Logs the disconnect time and compares it to the last detected engine JSON write time
 * (as observed by EngineWriteProbe).
 */
public final class DisconnectPersistBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void register(Object eventRegistry) {
        boolean ok = tryRegister(eventRegistry, "PlayerDisconnectEvent", new String[] {
                "com.hypixel.hytale.server.core.universe.events.PlayerDisconnectEvent",
                "com.hypixel.hytale.server.core.universe.event.PlayerDisconnectEvent",
                "com.hypixel.hytale.server.core.event.player.PlayerDisconnectEvent",
                "com.hypixel.hytale.server.core.events.player.PlayerDisconnectEvent",
                "com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent"
        });

        if (!ok) {
            LOGGER.atWarning().log("Disconnect hook NOT installed: no supported event class found in this patchline.");
        }
    }

    private boolean tryRegister(Object eventRegistry, String label, String[] classNames) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = eventRegistry.getClass().getClassLoader();
        }

        Method register = findRegisterMethod(eventRegistry.getClass());
        if (register == null) {
            LOGGER.atWarning().log("Could not find EventRegistry.register(..) method; disconnect hook disabled");
            return false;
        }

        for (String name : classNames) {
            try {
                Class<?> eventClass = Class.forName(name, false, cl);
                register.invoke(eventRegistry, eventClass, (java.util.function.Consumer<Object>) this::onDisconnectEvent);
                LOGGER.atInfo().log("Registered disconnect listener for " + label + " using class: " + name);
                return true;
            } catch (ClassNotFoundException ignored) {
                // keep trying
            } catch (Throwable t) {
                LOGGER.atWarning().log("Failed to register disconnect listener for " + label + " class=" + name + " error=" + t);
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

    private void onDisconnectEvent(Object event) {
        try {
            long nowMs = System.currentTimeMillis();
            String playerUuid = tryExtractPlayerUuid(event);

            long lastWriteMs = playerUuid == null ? 0L : EngineWriteProbeRegistry.getLastEngineWriteDetectedAtMs(playerUuid);
            long writeCount = playerUuid == null ? 0L : EngineWriteProbeRegistry.getEngineWriteCount(playerUuid);

            long deltaMs = (lastWriteMs <= 0L) ? -1L : (nowMs - lastWriteMs);

            String connectionKey = ConnectionKeyUtil.tryExtractConnectionKey(event);

            LOGGER.atInfo().log("DISCONNECT_EVENT playerUuid=" + playerUuid
                    + " at=" + Instant.ofEpochMilli(nowMs)
                    + " lastEngineWriteAt=" + (lastWriteMs <= 0L ? "<none>" : Instant.ofEpochMilli(lastWriteMs))
                    + " deltaSinceWriteMs=" + deltaMs
                    + " engineWriteCount=" + writeCount
                    + " eventClass=" + event.getClass().getName()
                    + " connectionKey=" + (connectionKey == null ? "<none>" : connectionKey));

            // Arm the gate so the next ENGINE_WRITE_DETECTED can be treated as the "final persist" after disconnect.
            if (playerUuid != null && !playerUuid.isBlank()) {
                FinalPersistGate.markDisconnect(playerUuid, nowMs, connectionKey);
            }

            String msg = tryExtractDisconnectMessage(event);
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("logged in again")) {
                    LOGGER.atInfo().log("DISCONNECT_IGNORED_DUPLICATE_LOGIN playerUuid=" + playerUuid
                            + " message=\"" + msg + "\"");
                    return;
                }
            }

        } catch (Throwable t) {
            LOGGER.atWarning().log("Disconnect handler failed: " + t);
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
        if (v instanceof UUID) {
            return v.toString();
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static String tryExtractDisconnectMessage(Object event) {
        if (event == null) return null;

        // Best-effort: scan common method names seen in kick/disconnect events.
        String[] methodNames = new String[] {
                "getMessage",
                "getKickMessage",
                "getDisconnectMessage",
                "getReason",
                "getReasonMessage",
                "getCloseReason"
        };

        for (String name : methodNames) {
            try {
                java.lang.reflect.Method m = event.getClass().getMethod(name);
                Object v = m.invoke(event);
                if (v == null) continue;
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            } catch (Throwable ignored) {
                // ignore
            }
        }

        return null;
    }

}
