package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import com.hypixel.hytale.server.core.universe.Universe;
import com.janiel.hytale.travel.assets.PluginAssetPackRegistrar;
import com.janiel.hytale.travel.bridges.DisconnectLogBridge;
import com.janiel.hytale.travel.bridges.InstanceReturnBridge;
import com.janiel.hytale.travel.bridges.InventoryAcquireBridge;
import com.janiel.hytale.travel.bridges.TransferInboundBridge;
import com.janiel.hytale.travel.commands.*;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.net.BackendClient;
import com.janiel.hytale.travel.persistence.FinalPersistGate;
import com.janiel.hytale.travel.services.CrashCheckpointService;
import com.janiel.hytale.travel.services.LeaseHeartbeatService;
import com.janiel.hytale.travel.ui.PortalChoicePage;
import com.janiel.hytale.travel.mutations.system.DamageBlockLoggerSystem;
import com.janiel.hytale.travel.mutations.system.BlockBreakLoggerSystem;
import com.janiel.hytale.travel.mutations.system.StaminaDepletionExtraRegenDelaySystem;
import com.janiel.hytale.travel.mutations.system.DeathInfoLoggerSystem;
import com.janiel.hytale.travel.mutations.system.SwordMasteryVulnerableDamageTakenSystem;

import javax.annotation.Nonnull;

public class TravelPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Page Id used by the portal block JSON (OpenCustomUI interaction).
     * Keep this stable: assets reference this string.
     */
    private static final String PORTAL_UI_PAGE_ID = "JanielTravelPortal";

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

        LeaseHeartbeatService.start(backend);

        CrashCheckpointService.start(cfg, backend);

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
        InstanceReturnBridge.register(getEventRegistry());

        // IMPORTANT:
        // DrainPlayerFromWorldEvent / AddPlayerToWorldEvent are fired on each World's EventRegistry,
        // not necessarily on the plugin's EventRegistry.
        // Hook all currently loaded worlds once the Universe is ready.
        // IMPORTANT:
// DrainPlayerFromWorldEvent / AddPlayerToWorldEvent are fired on each World's EventRegistry.
// Hook all currently loaded worlds once Universe is ready (if this patchline exposes a non-null future).
        try {
            java.util.concurrent.CompletableFuture<Void> ready = Universe.get().getUniverseReady();
            if (ready != null) {
                ready.thenRun(InstanceReturnBridge::registerToAllLoadedWorlds);
            } else {
                LOGGER.atInfo().log("UniverseReady future was null during setup; using lazy world hooks on travel.");
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to attach UniverseReady hook: " + e);
        }


        getCommandRegistry().registerCommand(new TravelCommand(cfg, backend));
        getCommandRegistry().registerCommand(new ClaimLatestCommand(cfg, backend));

        getCommandRegistry().registerCommand(new PortalUiCommand(cfg));

        // Temporary dev command: open Mutations custom page.
        getCommandRegistry().registerCommand(new MutationsUiCommand());

        // Register the CustomUI page supplier used by the portal block's OpenCustomUI interaction.
        // This lets the engine open our server-side custom page via asset JSON, without polling.
        OpenCustomUIInteraction.registerSimple(
                this,
                TravelPlugin.class,
                PORTAL_UI_PAGE_ID,
                (Function<PlayerRef, com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage>) (playerRef) -> {
                    List<String> serverIds = new ArrayList<>(cfg.getListenerPorts().keySet());
                    Collections.sort(serverIds);
                    return PortalChoicePage.create(playerRef, cfg, serverIds, 0);
                }
        );

        PluginAssetPackRegistrar.registerSelfAsAssetPack(this);

        getCommandRegistry().registerCommand(new FindAssetCommand());

        getCommandRegistry().registerCommand(new DebugBlockCommand());
        getCommandRegistry().registerCommand(new PlaceBlockCommand());

        // Debug: probe how often the engine overwrites the persisted player JSON.
        getCommandRegistry().registerCommand(new ProbeEngineWriteCommand(cfg));
        getCommandRegistry().registerCommand(new StopProbeEngineWriteCommand());

        // Mutations (debug): log when a block is broken
        getEntityStoreRegistry().registerSystem(new BlockBreakLoggerSystem());
        LOGGER.atInfo().log("Mutations: BlockBreakLoggerSystem registered (BreakBlockEvent)");

        // Mutations (debug): log when a block is damaged (mining tick while holding click)
        getEntityStoreRegistry().registerSystem(new DamageBlockLoggerSystem());
        LOGGER.atInfo().log("Mutations: DamageBlockLoggerSystem registered (DamageBlockEvent)");

        // Mutations (debug): log death component + death info (Damage) when any entity dies
        getEntityStoreRegistry().registerSystem(new DeathInfoLoggerSystem());
        LOGGER.atInfo().log("Combat: DeathInfoLoggerSystem registered (DeathComponent/DeathInfo)");

        getEntityStoreRegistry().registerSystem(new StaminaDepletionExtraRegenDelaySystem());
        LOGGER.atInfo().log("Stamina: StaminaDepletionExtraRegenDelaySystem registered (DelayedEntitySystem)");

        getEntityStoreRegistry().registerSystem(new SwordMasteryVulnerableDamageTakenSystem());
        LOGGER.atInfo().log("Combat: SwordMasteryVulnerableDamageTakenSystem registered (Damage)");

        LOGGER.atInfo().log("HytaleTravel setup done");
    }
}
