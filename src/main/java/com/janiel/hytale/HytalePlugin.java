package com.janiel.hytale;

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
import com.janiel.hytale.mutations.commands.MutationsUiCommand;
import com.janiel.hytale.portal.commands.PortalUiCommand;
import com.janiel.hytale.core.assets.PluginAssetPackRegistrar;
import com.janiel.hytale.network.bridge.DisconnectPersistBridge;
import com.janiel.hytale.globalchat.bridge.GlobalChatBridge;
import com.janiel.hytale.portal.bridge.InstanceReturnPointBridge;
import com.janiel.hytale.network.bridge.InventoryAcquireBridge;
import com.janiel.hytale.network.bridge.LastServerUpdateBridge;
import com.janiel.hytale.travel.bridges.TransferInboundBridge;
import com.janiel.hytale.travel.commands.*;
import com.janiel.hytale.core.config.TravelConfig;
import com.janiel.hytale.network.backend.BackendClient;
import com.janiel.hytale.network.persistence.FinalPersistGate;
import com.janiel.hytale.network.service.CrashCheckpointService;
import com.janiel.hytale.globalchat.service.GlobalChatWebSocketService;
import com.janiel.hytale.network.service.LeaseHeartbeatService;
import com.janiel.hytale.network.service.ServerHeartbeatService;
import com.janiel.hytale.portal.ui.PortalChoicePage;
import com.janiel.hytale.mutations.system.DamageBlockLoggerSystem;
import com.janiel.hytale.mutations.system.BlockBreakLoggerSystem;
import com.janiel.hytale.mutations.system.StaminaDepletionExtraRegenDelaySystem;
import com.janiel.hytale.mutations.system.DeathInfoLoggerSystem;
import com.janiel.hytale.mutations.system.SwordMasteryVulnerableDamageTakenSystem;
import com.janiel.hytale.mutations.system.BattleaxeMasteryWeakenDamageTakenSystem;
import com.janiel.hytale.mutations.system.MaceMasteryStunSystem;
import com.janiel.hytale.mutations.system.MaceMasteryStunRevertSystem;
import com.janiel.hytale.mutations.system.DaggerMasteryBleedSystem;
import com.janiel.hytale.mutations.system.DaggerMasteryBleedTickSystem;
import com.janiel.hytale.mutations.system.SpearMasteryKnockbackSystem;
import com.janiel.hytale.mutations.system.BowProjectileSpawnLoggerSystem;
import com.janiel.hytale.mutations.system.BowPerfectShotDamageSystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.janiel.hytale.mutations.component.PerfectShotMarkerComponent;
import com.janiel.hytale.mutations.interaction.PerfectShotChargingInteraction;
import com.janiel.hytale.mutations.interaction.PerfectShotProjectileInteraction;
import com.janiel.hytale.mutations.component.BowPerfectShotChargeTrackerComponent;
import com.janiel.hytale.mutations.component.PendingPerfectShotComponent;

import javax.annotation.Nonnull;

public class HytalePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Page Id used by the portal block JSON (OpenCustomUI interaction).
     * Keep this stable: assets reference this string.
     */
    private static final String PORTAL_UI_PAGE_ID = "JanielTravelPortal";

    public HytalePlugin(@Nonnull JavaPluginInit init) {
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

        LastServerUpdateBridge lastServerUpdateBridge = new LastServerUpdateBridge(cfg, backend);
        lastServerUpdateBridge.register(getEventRegistry());

        GlobalChatBridge globalChatBridge = new GlobalChatBridge(cfg);
        globalChatBridge.register(getEventRegistry());

        // Instrumentation: log disconnect timing vs last observed engine JSON write.
        DisconnectPersistBridge disconnectLog = new DisconnectPersistBridge();
        disconnectLog.register(getEventRegistry());
        InstanceReturnPointBridge.register(getEventRegistry());

        try {
            java.util.concurrent.CompletableFuture<Void> ready = Universe.get().getUniverseReady();
            if (ready != null) {
                ready.thenRun(InstanceReturnPointBridge::registerToAllLoadedWorlds);
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
                HytalePlugin.class,
                PORTAL_UI_PAGE_ID,
                (Function<PlayerRef, com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage>) (playerRef) -> {
                    List<String> serverIds = new ArrayList<>(cfg.getListenerTargets().keySet());
                    Collections.sort(serverIds);
                    return PortalChoicePage.create(playerRef, cfg, serverIds, 0);
                }
        );

        BowPerfectShotChargeTrackerComponent.setComponentType(
                getEntityStoreRegistry().registerComponent(
                        BowPerfectShotChargeTrackerComponent.class,
                        BowPerfectShotChargeTrackerComponent::new
                )
        );
        LOGGER.atInfo().log("Combat: BowPerfectShotChargeTrackerComponent registered");

        PendingPerfectShotComponent.setComponentType(
                getEntityStoreRegistry().registerComponent(
                        PendingPerfectShotComponent.class,
                        PendingPerfectShotComponent::new
                )
        );
        LOGGER.atInfo().log("Combat: PendingPerfectShotComponent registered");

        PerfectShotMarkerComponent.setComponentType(
                getEntityStoreRegistry().registerComponent(
                        PerfectShotMarkerComponent.class,
                        PerfectShotMarkerComponent::new
                )
        );
        LOGGER.atInfo().log("Combat: PerfectShotMarkerComponent registered");

        getCodecRegistry(Interaction.CODEC).register(
                "Janiel_PerfectShot_Charging",
                PerfectShotChargingInteraction.class,
                PerfectShotChargingInteraction.CODEC
        );
        LOGGER.atInfo().log("Combat: Janiel_PerfectShot_Charging interaction codec registered");

        getCodecRegistry(Interaction.CODEC).register(
                "Janiel_PerfectShot_Projectile",
                PerfectShotProjectileInteraction.class,
                PerfectShotProjectileInteraction.CODEC
        );
        LOGGER.atInfo().log("Combat: Janiel_PerfectShot_Projectile interaction codec registered");

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

        getEntityStoreRegistry().registerSystem(new DaggerMasteryBleedSystem());
        LOGGER.atInfo().log("Combat: DaggerMasteryBleedSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new DaggerMasteryBleedTickSystem());
        LOGGER.atInfo().log("Combat: DaggerMasteryBleedTickSystem registered (DelayedEntitySystem)");

        getEntityStoreRegistry().registerSystem(new SpearMasteryKnockbackSystem());
        LOGGER.atInfo().log("Combat: SpearMasteryKnockbackSystem registered (Damage)");

        getEntityStoreRegistry().registerSystem(new BowProjectileSpawnLoggerSystem());
        LOGGER.atInfo().log("Combat: BowProjectileSpawnLoggerSystem registered (Projectile spawn investigation)");

        getEntityStoreRegistry().registerSystem(new BowPerfectShotDamageSystem());
        LOGGER.atInfo().log("Combat: BowPerfectShotDamageSystem registered (Projectile damage -> perfect shot multiplier)");

        LOGGER.atInfo().log("HytaleTravel setup done");
    }
}