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

    private TravelConfig(String proxyHost, Map<String, Integer> listenerPorts) {
        this.proxyHost = proxyHost;
        this.listenerPorts = Collections.unmodifiableMap(listenerPorts);
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

        return new TravelConfig(proxyHost, ports);
    }
}
