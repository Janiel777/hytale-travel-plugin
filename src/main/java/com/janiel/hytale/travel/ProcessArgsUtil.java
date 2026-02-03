package com.janiel.hytale.travel;

import java.util.List;

public final class ProcessArgsUtil {

    private ProcessArgsUtil() {
    }

    public static Integer tryGetIntFlagValue(String flagName) {
        // Reads JVM process args: ... --port 7001 ...
        // Works if the game server is launched with the flag in the same process.
        List<String> args = ProcessHandle.current().info().arguments()
                .map(List::of)
                .orElse(List.of());

        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!flagName.equals(a)) {
                continue;
            }
            if (i + 1 >= args.size()) {
                return null;
            }
            try {
                return Integer.parseInt(args.get(i + 1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        // Support --port=7001 style
        String prefix = flagName + "=";
        for (String a : args) {
            if (!a.startsWith(prefix)) {
                continue;
            }
            String v = a.substring(prefix.length());
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }
}
