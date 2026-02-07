package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class TravelPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TravelPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("HytaleTravel plugin constructed");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("HytaleTravel setup start");

        TravelConfig cfg = TravelConfig.load();
        String resolvedServerId = cfg.resolveCurrentServerId();

        LOGGER.atInfo().log("TravelConfig loaded. proxyHost=" + cfg.getProxyHost()
                + " serverIds=" + cfg.getListenerPorts().keySet()
                + " resolvedServerId=" + resolvedServerId
                + " backendBaseUrl=" + cfg.getBackendBaseUrl()
                + " backendTimeoutMs=" + cfg.getBackendTimeoutMs());

        BackendClient backend = new BackendClient(cfg.getBackendBaseUrl(), cfg.getBackendTimeoutMs());

        // Initialize final-persist gate so it can save/release after engine writes.
        FinalPersistGate.initialize(cfg, backend);

        // Inbound transfer hook: on target server, verify referral payload and claim/apply snapshot.
        TransferInboundBridge inbound = new TransferInboundBridge(cfg, backend);
        inbound.register(getEventRegistry());

        // Inventory session acquire on connect: pulls backend snapshot and applies inventory before load.
        InventoryAcquireBridge invAcquire = new InventoryAcquireBridge(cfg, backend);
        invAcquire.register(getEventRegistry());

        // Instrumentation: log disconnect timing vs last observed engine JSON write.
        DisconnectLogBridge disconnectLog = new DisconnectLogBridge();
        disconnectLog.register(getEventRegistry());

        getCommandRegistry().registerCommand(new TravelCommand(cfg, backend));
        getCommandRegistry().registerCommand(new ClaimLatestCommand(cfg, backend));

        // Debug: probe how often the engine overwrites the persisted player JSON.
        getCommandRegistry().registerCommand(new ProbeEngineWriteCommand(cfg));
        getCommandRegistry().registerCommand(new StopProbeEngineWriteCommand());

        LOGGER.atInfo().log("HytaleTravel setup done");
    }
}
