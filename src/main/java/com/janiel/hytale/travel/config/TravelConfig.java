package com.janiel.hytale.travel.config;

import com.janiel.hytale.travel.util.ProcessArgsUtil;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class TravelConfig {

    public static final class ListenerTarget {
        private final String host;
        private final int port;

        public ListenerTarget(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }

    private final Map<String, ListenerTarget> listenerTargets;

    private final String backendBaseUrl;
    private final int backendTimeoutMs;

    private final Map<Integer, String> serverIdByGamePort;

    // Shared secret used to HMAC-sign referral payloads.
    private final String payloadHmacSecret;

    private final java.nio.file.Path universeDir;

    private TravelConfig(Map<String, ListenerTarget> listenerTargets,
                         String backendBaseUrl,
                         int backendTimeoutMs,
                         Map<Integer, String> serverIdByGamePort,
                         String payloadHmacSecret,
                         java.nio.file.Path universeDir) {

        this.listenerTargets = Collections.unmodifiableMap(listenerTargets);

        this.backendBaseUrl = backendBaseUrl;
        this.backendTimeoutMs = backendTimeoutMs;

        this.serverIdByGamePort = Collections.unmodifiableMap(serverIdByGamePort);

        this.payloadHmacSecret = payloadHmacSecret;
        this.universeDir = universeDir;
    }

    public Map<String, ListenerTarget> getListenerTargets() {
        return listenerTargets;
    }

    public boolean hasServerId(String serverId) {
        return listenerTargets.containsKey(serverId);
    }

    public ListenerTarget getListenerTarget(String serverId) {
        return listenerTargets.get(serverId);
    }

    public Integer getListenerPort(String serverId) {
        ListenerTarget target = listenerTargets.get(serverId);
        return target == null ? null : target.getPort();
    }

    public String getListenerHost(String serverId) {
        ListenerTarget target = listenerTargets.get(serverId);
        return target == null ? null : target.getHost();
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public int getBackendTimeoutMs() {
        return backendTimeoutMs;
    }

    public String getPayloadHmacSecret() {
        return payloadHmacSecret;
    }

    public java.nio.file.Path getUniverseDir() {
        return universeDir;
    }

    public String resolveCurrentServerId() {
        Integer port = ProcessArgsUtil.tryGetIntFlagValue("--port");
        if (port == null) {
            String bind = ProcessArgsUtil.tryGetStringFlagValue("--bind");
            port = tryParsePortFromBind(bind);
        }

        if (port == null) {
            return null;
        }

        return serverIdByGamePort.get(port);
    }

    private static Integer tryParsePortFromBind(String bind) {
        if (bind == null) {
            return null;
        }
        String s = bind.trim();
        if (s.isEmpty()) {
            return null;
        }

        String candidate = s;
        int lastColon = s.lastIndexOf(':');
        if (lastColon >= 0 && lastColon + 1 < s.length()) {
            candidate = s.substring(lastColon + 1).trim();
        }

        int end = 0;
        while (end < candidate.length() && Character.isDigit(candidate.charAt(end))) {
            end++;
        }
        if (end == 0) {
            end = candidate.length();
        }
        String digits = candidate.substring(0, end).trim();

        if (digits.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static TravelConfig load() {
        String externalPath = System.getProperty("hytale.travel.config");
        Properties props = new Properties();

        try {
            if (externalPath != null && !externalPath.isBlank()) {
                try (InputStream in = new BufferedInputStream(new FileInputStream(externalPath))) {
                    props.load(in);
                }
            } else {
                try (InputStream in = TravelConfig.class.getClassLoader().getResourceAsStream("travel.properties")) {
                    if (in == null) {
                        throw new IllegalStateException("Missing resource travel.properties");
                    }
                    props.load(in);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load travel config", ex);
        }

        String backendBaseUrl = props.getProperty("backendBaseUrl", "http://127.0.0.1:8000").trim();
        while (backendBaseUrl.endsWith("/")) {
            backendBaseUrl = backendBaseUrl.substring(0, backendBaseUrl.length() - 1);
        }

        int backendTimeoutMs = 2000;
        try {
            backendTimeoutMs = Integer.parseInt(props.getProperty("backendTimeoutMs", "2000").trim());
        } catch (NumberFormatException ignored) {
            // Keep default timeout.
        }

        Map<Integer, String> serverIdByGamePort = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("serverIdByGamePort.")) {
                continue;
            }

            String rawPort = key.substring("serverIdByGamePort.".length()).trim();
            String serverId = props.getProperty(key, "").trim();

            if (rawPort.isEmpty() || serverId.isEmpty()) {
                continue;
            }

            try {
                int port = Integer.parseInt(rawPort);
                serverIdByGamePort.put(port, serverId);
            } catch (NumberFormatException ignored) {
                // ignore invalid
            }
        }

        Map<String, String> hosts = new LinkedHashMap<>();
        Map<String, Integer> ports = new LinkedHashMap<>();

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("listenerHost.")) {
                String serverId = key.substring("listenerHost.".length()).trim();
                String host = props.getProperty(key, "").trim();

                if (!serverId.isEmpty() && !host.isEmpty()) {
                    hosts.put(serverId, host);
                }
                continue;
            }

            if (key.startsWith("listenerPort.")) {
                String serverId = key.substring("listenerPort.".length()).trim();
                String rawPort = props.getProperty(key, "").trim();

                if (serverId.isEmpty()) {
                    continue;
                }

                try {
                    int port = Integer.parseInt(rawPort);
                    ports.put(serverId, port);
                } catch (NumberFormatException ignored) {
                    // Ignore invalid ports; command will behave as if serverId doesn't exist.
                }
            }
        }

        Map<String, ListenerTarget> listenerTargets = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : ports.entrySet()) {
            String serverId = entry.getKey();
            Integer port = entry.getValue();
            String host = hosts.get(serverId);

            if (host == null || host.isBlank()) {
                throw new IllegalStateException("Missing listenerHost." + serverId + " in travel.properties");
            }

            listenerTargets.put(serverId, new ListenerTarget(host, port));
        }

        if (listenerTargets.isEmpty()) {
            throw new IllegalStateException("travel.properties has no valid listenerHost/listenerPort server mappings");
        }

        String payloadHmacSecret = props.getProperty("payloadHmacSecret", "").trim();

        String universeDirStr = props.getProperty("universeDir", "universe").trim();
        java.nio.file.Path universeDir = java.nio.file.Path.of(universeDirStr);

        return new TravelConfig(
                listenerTargets,
                backendBaseUrl,
                backendTimeoutMs,
                serverIdByGamePort,
                payloadHmacSecret,
                universeDir
        );
    }
}