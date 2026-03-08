package com.janiel.hytale.core.util;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;

public final class PlayerIdUtil {

    private PlayerIdUtil() {
    }

    public static String getPlayerUuid(PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }

        // Try common getter names across APIs.
        String[] candidates = new String[] {
                "getUuid",
                "getUUID",
                "getPlayerUuid",
                "getPlayerUUID",
                "getId",
                "getPlayerId",
                "getUniqueId",
                "getUniqueID"
        };

        for (String name : candidates) {
            try {
                Method m = playerRef.getClass().getMethod(name);
                Object v = m.invoke(playerRef);
                if (v != null) {
                    return String.valueOf(v);
                }
            } catch (Exception ignored) {
                // Keep trying other method names.
            }
        }

        return null;
    }
}
