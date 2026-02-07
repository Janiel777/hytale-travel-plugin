package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Handles inbound server referrals (player transfers) by:
 *  1) reading the referral payload
 *  2) verifying the HMAC signature
 *  3) claiming the transfer snapshot from backend
 *  4) overwriting the player's persisted JSON before the player fully loads
 *
 * This class uses reflection for the event types so we don't hardcode package
 * names that might drift between patchlines.
 */
public final class TransferInboundBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TravelConfig cfg;
    private final BackendClient backend;

    public TransferInboundBridge(TravelConfig cfg, BackendClient backend) {
        this.cfg = cfg;
        this.backend = backend;
    }

    public void register(Object eventRegistry) {
        // IMPORTANT:
        // In some patchlines, referral data is visible in setup stage, not in a "transfer" event.
        boolean okTransfer = tryRegister(eventRegistry, "PlayerTransferEvent", new String[] {
                "com.hypixel.hytale.server.core.universe.events.PlayerTransferEvent",
                "com.hypixel.hytale.server.core.universe.event.PlayerTransferEvent",
                "com.hypixel.hytale.server.core.event.player.PlayerTransferEvent",
                "com.hypixel.hytale.server.core.events.player.PlayerTransferEvent"
        });

        boolean okSetup = tryRegister(eventRegistry, "PlayerSetupConnectEvent", new String[] {
                "com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent",

                // Keep older guesses as fallback (harmless if missing):
                "com.hypixel.hytale.server.core.events.player.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.event.player.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.universe.events.PlayerSetupConnectEvent",
                "com.hypixel.hytale.server.core.universe.event.PlayerSetupConnectEvent"
        });

        if (!okTransfer && !okSetup) {
            LOGGER.atWarning().log("Inbound referral hook NOT installed: no supported event class found in this patchline.");
        }
    }

    private boolean tryRegister(Object eventRegistry, String label, String[] classNames) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = eventRegistry.getClass().getClassLoader();
        }

        Method register = findRegisterMethod(eventRegistry.getClass());
        if (register == null) {
            LOGGER.atWarning().log("Could not find EventRegistry.register(..) method; inbound hook disabled");
            return false;
        }

        for (String name : classNames) {
            try {
                Class<?> eventClass = Class.forName(name, false, cl);

                register.invoke(eventRegistry, eventClass, (java.util.function.Consumer<Object>) this::onInboundTransferEvent);
                LOGGER.atInfo().log("Registered inbound referral listener for " + label + " using class: " + name);
                return true;
            } catch (ClassNotFoundException ignored) {
                // keep trying
            } catch (Throwable t) {
                LOGGER.atWarning().log("Failed to register inbound listener for " + label + " class=" + name + " error=" + t);
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

    private void onInboundTransferEvent(Object event) {
        try {
            byte[] payload = tryExtractPayload(event);
            if (payload == null || payload.length == 0) {
                return;
            }

            LOGGER.atInfo().log("Inbound referral payload detected. bytes=" + payload.length
                    + " eventClass=" + event.getClass().getName());

            String secret = cfg.getPayloadHmacSecret();
            if (secret == null || secret.isBlank()) {
                LOGGER.atWarning().log("Inbound payload present but payloadHmacSecret is not configured; ignoring payload");
                return;
            }

            TravelPayload parsed = TravelPayload.parseAndVerify(payload, secret);
            if (parsed == null) {
                LOGGER.atWarning().log("Rejected inbound referral: invalid or unsigned payload");
                tryCancel(event);
                return;
            }

            String currentServerId = cfg.resolveCurrentServerId();
            if (currentServerId != null && !currentServerId.equals(parsed.getToServer())) {
                LOGGER.atWarning().log("Rejected inbound referral: payload.to_server=" + parsed.getToServer()
                        + " but this serverId=" + currentServerId);
                tryCancel(event);
                return;
            }

            // New flow: inventory is sourced from backend via /inventory/session/acquire on setup connect.
            // The referral payload is still validated to prevent tampering with target server id.
            LOGGER.atInfo().log("Inbound referral accepted. player_uuid=" + parsed.getPlayerUuid()
                    + " to_server=" + parsed.getToServer()
                    + " ticket=" + parsed.getTicketId());
        } catch (Throwable t) {
            LOGGER.atWarning().log("Inbound referral handler failed: " + t);
        }
    }

    private static byte[] tryExtractPayload(Object event) {
        Object v;

        v = invokeNoArg(event, "getPayload");
        if (v != null) return coerceToBytes(v);

        v = invokeNoArg(event, "getReferralPayload");
        if (v != null) return coerceToBytes(v);

        v = invokeNoArg(event, "getReferralData");
        if (v != null) return coerceToBytes(v);

        v = invokeNoArg(event, "getData");
        if (v != null) return coerceToBytes(v);

        Object referral = invokeNoArg(event, "getReferral");
        if (referral != null) {
            v = invokeNoArg(referral, "getPayload");
            if (v != null) return coerceToBytes(v);

            v = invokeNoArg(referral, "getData");
            if (v != null) return coerceToBytes(v);
        }

        return null;
    }

    private static byte[] coerceToBytes(Object v) {
        if (v instanceof byte[]) {
            return (byte[]) v;
        }
        if (v instanceof java.nio.ByteBuffer) {
            java.nio.ByteBuffer b = ((java.nio.ByteBuffer) v).slice();
            byte[] out = new byte[b.remaining()];
            b.get(out);
            return out;
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

    private static void tryCancel(Object event) {
        try {
            Method m = event.getClass().getMethod("setCancelled", boolean.class);
            m.invoke(event, true);
        } catch (Exception ignored) {
        }
    }
}
