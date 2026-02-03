package com.janiel.hytale.travel;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class TravelConfig {

    private final String proxyHost;
    private final Map<String, Integer> listenerPorts;

    private final String backendBaseUrl;
    private final int backendTimeoutMs;

    private final Map<Integer, String> serverIdByGamePort;

    private TravelConfig(String proxyHost,
                         Map<String, Integer> listenerPorts,
                         String backendBaseUrl,
                         int backendTimeoutMs,
                         Map<Integer, String> serverIdByGamePort) {

        this.proxyHost = proxyHost;
        this.listenerPorts = Collections.unmodifiableMap(listenerPorts);

        this.backendBaseUrl = backendBaseUrl;
        this.backendTimeoutMs = backendTimeoutMs;

        this.serverIdByGamePort = Collections.unmodifiableMap(serverIdByGamePort);
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public Map<String, Integer> getListenerPorts() {
        return listenerPorts;
    }

    public boolean hasServerId(String serverId) {
        return listenerPorts.containsKey(serverId);
    }

    public Integer getListenerPort(String serverId) {
        return listenerPorts.get(serverId);
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public int getBackendTimeoutMs() {
        return backendTimeoutMs;
    }

    public String resolveCurrentServerId() {
        Integer port = ProcessArgsUtil.tryGetIntFlagValue("--port");
        if (port == null) {
            return null;
        }
        return serverIdByGamePort.get(port);
    }

    public static TravelConfig load() {
        // Optional override: -Dhytale.travel.config="C:\path\travel.properties"
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

        String proxyHost = props.getProperty("proxyHost", "127.0.0.1").trim();

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

        Map<String, Integer> ports = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("listenerPort.")) {
                continue;
            }
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

        if (ports.isEmpty()) {
            throw new IllegalStateException("travel.properties has no listenerPort.<serverId>=<port> entries");
        }

        return new TravelConfig(proxyHost, ports, backendBaseUrl, backendTimeoutMs, serverIdByGamePort);
    }
}
