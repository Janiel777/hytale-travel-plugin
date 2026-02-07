package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProcessArgsUtil {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ProcessArgsUtil() {
    }

    public static Integer tryGetIntFlagValue(String flagName) {
        List<String> args = getBestEffortArgs();

        // Reads JVM process args: ... --port 7001 ...
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

    public static String tryGetStringFlagValue(String flagName) {
        List<String> args = getBestEffortArgs();

        // Reads JVM process args: ... --bind 0.0.0.0:7001 ...
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (!flagName.equals(a)) {
                continue;
            }
            if (i + 1 >= args.size()) {
                return null;
            }
            String v = args.get(i + 1);
            return (v == null || v.isBlank()) ? null : v;
        }

        // Support --bind=0.0.0.0:7001 style
        String prefix = flagName + "=";
        for (String a : args) {
            if (!a.startsWith(prefix)) {
                continue;
            }
            String v = a.substring(prefix.length());
            return (v == null || v.isBlank()) ? null : v;
        }

        return null;
    }

    private static List<String> getBestEffortArgs() {
        // 1) Try ProcessHandle arguments (may be empty depending on launcher/OS).
        List<String> args = ProcessHandle.current().info().arguments()
                .map(List::of)
                .orElse(List.of());

        if (!args.isEmpty()) {
            return args;
        }

        // 2) Fallback: sun.java.command usually contains jar/main + args.
        // Example: "HytaleServer.jar --assets Assets.zip --bind 0.0.0.0:7001"
        String cmd = System.getProperty("sun.java.command");

        if (cmd == null || cmd.isBlank()) {
            LOGGER.atWarning().log("ProcessArgsUtil: No process args found (ProcessHandle empty, sun.java.command missing).");
            return Collections.emptyList();
        }

        List<String> parsed = splitCommandLine(cmd);

        LOGGER.atInfo().log("ProcessArgsUtil: ProcessHandle args empty; using sun.java.command parsedArgsCount="
                + parsed.size()
                + " sun.java.command=\"" + cmd + "\"");

        return parsed;
    }

    private static List<String> splitCommandLine(String s) {
        // Minimal quote-aware splitter.
        // Supports: foo "bar baz" --bind 0.0.0.0:7001
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes && Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(c);
        }

        if (cur.length() > 0) {
            out.add(cur.toString());
        }

        return out;
    }
}
