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
import com.janiel.hytale.travel.bridges.GlobalChatBridge;
import com.janiel.hytale.travel.bridges.InstanceReturnBridge;
import com.janiel.hytale.travel.bridges.InventoryAcquireBridge;
import com.janiel.hytale.travel.bridges.RoutingUpdateLastBridge;
import com.janiel.hytale.travel.bridges.TransferInboundBridge;
import com.janiel.hytale.travel.commands.*;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.net.BackendClient;
import com.janiel.hytale.travel.persistence.FinalPersistGate;
import com.janiel.hytale.travel.services.CrashCheckpointService;
import com.janiel.hytale.travel.services.GlobalChatWebSocketService;
import com.janiel.hytale.travel.services.LeaseHeartbeatService;
import com.janiel.hytale.travel.services.ServerHeartbeatService;
import com.janiel.hytale.travel.ui.PortalChoicePage;
import com.janiel.hytale.travel.mutations.system.DamageBlockLoggerSystem;
import com.janiel.hytale.travel.mutations.system.BlockBreakLoggerSystem;
import com.janiel.hytale.travel.mutations.system.StaminaDepletionExtraRegenDelaySystem;
import com.janiel.hytale.travel.mutations.system.DeathInfoLoggerSystem;
import com.janiel.hytale.travel.mutations.system.SwordMasteryVulnerableDamageTakenSystem;
import com.janiel.hytale.travel.mutations.system.BattleaxeMasteryWeakenDamageTakenSystem;
import com.janiel.hytale.travel.mutations.system.MaceMasteryStunSystem;
import com.janiel.hytale.travel.mutations.system.MaceMasteryStunRevertSystem;
import com.janiel.hytale.travel.mutations.system.DaggerMasteryBleedSystem;
import com.janiel.hytale.travel.mutations.system.DaggerMasteryBleedTickSystem;
import com.janiel.hytale.travel.mutations.system.SpearMasteryKnockbackSystem;

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

        LOGGER.atInfo().log("TravelConfig loaded. serverIds=" + cfg.getListenerTargets().keySet()
                + " resolvedServerId=" + resolvedServerId
                + " backendBaseUrl=" + cfg.getBackendBaseUrl()
                + " backendTimeoutMs=" + cfg.getBackendTimeoutMs());

        BackendClient backend = new BackendClient(cfg.getBackendBaseUrl(), cfg.getBackendTimeoutMs());

        LeaseHeartbeatService.start(backend);

        CrashCheckpointService.start(cfg, backend);

        ServerHeartbeatService.start(cfg, backend);

        GlobalChatWebSocketService.start(cfg);

        // Initialize final-persist gate so it can save/release after engine writes.
        FinalPersistGate.initialize(cfg, backend);

        // Inbound transfer hook: on target server, verify referral payload and claim/apply snapshot.
        TransferInboundBridge inbound = new TransferInboundBridge(cfg, backend);
        inbound.register(getEventRegistry());

        // Inventory session acquire on connect: pulls backend snapshot and applies inventory before load.
        InventoryAcquireBridge invAcquire = new InventoryAcquireBridge(cfg, backend);
        invAcquire.register(getEventRegistry());

        RoutingUpdateLastBridge routingUpdateLastBridge = new RoutingUpdateLastBridge(cfg, backend);
        routingUpdateLastBridge.register(getEventRegistry());

        GlobalChatBridge globalChatBridge = new GlobalChatBridge(cfg);
        globalChatBridge.register(getEventRegistry());

        // Instrumentation: log disconnect timing vs last observed engine JSON write.
        DisconnectLogBridge disconnectLog = new DisconnectLogBridge();
        disconnectLog.register(getEventRegistry());
        InstanceReturnBridge.register(getEventRegistry());

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

        OpenCustomUIInteraction.registerSimple(
                this,
                TravelPlugin.class,
                PORTAL_UI_PAGE_ID,
                (Function<PlayerRef, com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage>) (playerRef) -> {
                    List<String> serverIds = new ArrayList<>(cfg.getListenerTargets().keySet());
                    Collections.sort(serverIds);
                    return PortalChoicePage.create(playerRef, cfg, serverIds, 0);
                }
        );

        PluginAssetPackRegistrar.registerSelfAsAssetPack(this);

        getCommandRegistry().registerCommand(new FindAssetCommand());

        getCommandRegistry().registerCommand(new DebugBlockCommand());
        getCommandRegistry().registerCommand(new PlaceBlockCommand());

        getCommandRegistry().registerCommand(new ProbeEngineWriteCommand(cfg));
        getCommandRegistry().registerCommand(new StopProbeEngineWriteCommand());

        getEntityStoreRegistry().registerSystem(new BlockBreakLoggerSystem());
        LOGGER.atInfo().log("Mutations: BlockBreakLoggerSystem registered (BreakBlockEvent)");

        getEntityStoreRegistry().registerSystem(new DamageBlockLoggerSystem());
        LOGGER.atInfo().log("Mutations: DamageBlockLoggerSystem registered (DamageBlockEvent)");

        getEntityStoreRegistry().registerSystem(new DeathInfoLoggerSystem());
        LOGGER.atInfo().log("Combat: DeathInfoLoggerSystem registered (DeathComponent/DeathInfo)");

        getEntityStoreRegistry().registerSystem(new StaminaDepletionExtraRegenDelaySystem());
        LOGGER.atInfo().log("Stamina: StaminaDepletionExtraRegenDelaySystem registered (DelayedEntitySystem)");

        getEntityStoreRegistry().registerSystem(new SwordMasteryVulnerableDamageTakenSystem());
        LOGGER.atInfo().log("Combat: SwordMasteryVulnerableDamageTakenSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new BattleaxeMasteryWeakenDamageTakenSystem());
        LOGGER.atInfo().log("Combat: BattleaxeMasteryWeakenDamageTakenSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new MaceMasteryStunSystem());
        LOGGER.atInfo().log("Combat: MaceMasteryStunSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new MaceMasteryStunRevertSystem());
        LOGGER.atInfo().log("Combat: MaceMasteryStunRevertSystem registered (DelayedEntitySystem)");
        LOGGER.atInfo().log("Combat: BattleaxeMasteryWeakenDamageTakenSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new DaggerMasteryBleedSystem());
        LOGGER.atInfo().log("Combat: DaggerMasteryBleedSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new DaggerMasteryBleedTickSystem());
        LOGGER.atInfo().log("Combat: DaggerMasteryBleedTickSystem registered (DelayedEntitySystem)");

        getEntityStoreRegistry().registerSystem(new SpearMasteryKnockbackSystem());
        LOGGER.atInfo().log("Combat: SpearMasteryKnockbackSystem registered (Damage)");

        LOGGER.atInfo().log("HytaleTravel setup done");
    }
}