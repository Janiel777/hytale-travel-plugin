package com.janiel.hytale.core.util;

import java.lang.reflect.Method;

public final class ConnectionKeyUtil {

    private ConnectionKeyUtil() {
    }

    public static String tryExtractConnectionKey(Object event) {
        if (event == null) return null;

        // Common patterns in Hytale internals. Convert whatever is returned to a stable string key.
        Object v;

        v = invokeNoArg(event, "getConnectionAddress");
        String s = toKeyString(v);
        if (s != null) return s;

        v = invokeNoArg(event, "getQuicConnectionAddress");
        s = toKeyString(v);
        if (s != null) return s;

        v = invokeNoArg(event, "getRemoteAddress");
        s = toKeyString(v);
        if (s != null) return s;

        v = invokeNoArg(event, "getClientAddress");
        s = toKeyString(v);
        if (s != null) return s;

        // Sometimes the connection is nested under "getClient()" or "getConnection()"
        Object client = invokeNoArg(event, "getClient");
        if (client != null) {
            v = invokeNoArg(client, "getConnectionAddress");
            s = toKeyString(v);
            if (s != null) return s;

            v = invokeNoArg(client, "getRemoteAddress");
            s = toKeyString(v);
            if (s != null) return s;
        }

        Object conn = invokeNoArg(event, "getConnection");
        if (conn != null) {
            v = invokeNoArg(conn, "getConnectionAddress");
            s = toKeyString(v);
            if (s != null) return s;

            v = invokeNoArg(conn, "getRemoteAddress");
            s = toKeyString(v);
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

    private static String toKeyString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
