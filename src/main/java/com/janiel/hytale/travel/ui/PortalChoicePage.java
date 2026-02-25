package com.janiel.hytale.travel.ui;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceEntityConfig;
import com.hypixel.hytale.builtin.instances.config.WorldReturnPoint;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.janiel.hytale.travel.bridges.InstanceReturnBridge;
import com.janiel.hytale.travel.config.TravelConfig;
import com.janiel.hytale.travel.model.TravelPayload;
import com.janiel.hytale.travel.util.PlayerIdUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PortalChoicePage extends ChoiceBasePage {

    private static final int CATEGORY_INDEX_BASE = 1000;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Debug logging can be enabled with:
     * -Dhytale.travel.debug=true
     */
    private static final boolean DEBUG = Boolean.getBoolean("hytale.travel.debug");

    private enum PortalCategory {
        FEATURED("Featured", 0),
        ADVENTURE("Adventure", 1),
        OTHER("Other", 2),
        WORLDS("Worlds", 3);

        private final String title;
        private final int index;

        PortalCategory(String title, int index) {
            this.title = title;
            this.index = index;
        }
    }

    private final PlayerRef playerRef;
    private final TravelConfig cfg;

    private final List<String> allServerIds;
    private final List<Destination> visibleDestinations;

    private final int selectedCategory;
    private final ChoiceElement[] categoryElements;

    private PortalChoicePage(@Nonnull PlayerRef playerRef,
                             @Nonnull TravelConfig cfg,
                             @Nonnull List<String> allServerIds,
                             @Nonnull List<Destination> visibleDestinations,
                             int selectedCategory,
                             @Nonnull ChoiceElement[] elements,
                             @Nonnull ChoiceElement[] categoryElements) {

        super(playerRef, elements, "Pages/JanielPortal/PortalHome.ui");

        this.playerRef = playerRef;
        this.cfg = cfg;
        this.allServerIds = allServerIds;
        this.visibleDestinations = visibleDestinations;
        this.selectedCategory = selectedCategory;
        this.categoryElements = categoryElements;
    }

    public static PortalChoicePage create(@Nonnull PlayerRef playerRef,
                                          @Nonnull TravelConfig cfg,
                                          @Nonnull List<String> allServerIds) {
        return create(playerRef, cfg, allServerIds, 0);
    }

    public static PortalChoicePage create(@Nonnull PlayerRef playerRef,
                                          @Nonnull TravelConfig cfg,
                                          @Nonnull List<String> allServerIds,
                                          int selectedCategory) {

        int cat = normalizeCategory(selectedCategory);
        List<Destination> visible = filterDestinations(cfg, allServerIds, cat);

        ChoiceElement[] elements = new ChoiceElement[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            Destination d = visible.get(i);
            elements[i] = new PortalDestinationElement(d.title, d.description);
        }

        ChoiceElement[] categories = new ChoiceElement[PortalCategory.values().length];
        PortalCategory[] cats = PortalCategory.values();
        for (int i = 0; i < cats.length; i++) {
            categories[i] = new PortalCategoryElement(cats[i].title, cats[i].index);
        }

        return new PortalChoicePage(playerRef, cfg, allServerIds, visible, cat, elements, categories);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        // Right list (#ElementList) is built and bound by ChoiceBasePage.
        super.build(ref, commands, events, store);

        // Left categories: we append + bind manually.
        for (int i = 0; i < categoryElements.length; i++) {
            String selector = "#CategoryList[" + i + "]";
            categoryElements[i].addButton(commands, events, selector, playerRef);

            bindCategoryIndexClick(events, selector, CATEGORY_INDEX_BASE + i);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ChoicePageEventData data) {

        int index = data.getIndex();

        if (DEBUG) {
            LOGGER.atInfo().log("[PortalUI] handleDataEvent index=" + index);
        }

        // Category clicks
        if (index >= CATEGORY_INDEX_BASE && index < CATEGORY_INDEX_BASE + categoryElements.length) {
            int cat = index - CATEGORY_INDEX_BASE;
            reopenWithCategory(ref, store, cat);
            return;
        }

        // Destination clicks (index relative to visibleDestinations)
        if (index < 0 || index >= visibleDestinations.size()) {
            return;
        }

        Destination d = visibleDestinations.get(index);

        if (d.type == DestinationType.SERVER) {
            close();

            String serverId = d.id;
            Integer port = cfg.getListenerPort(serverId);
            if (port == null) {
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) {
                    p.sendMessage(Message.raw("portalui: invalid port for serverId: " + serverId));
                }
                return;
            }

            // Optional signed payload for proxy referral.
            // If anything goes wrong we fall back to unsigned referral.
            byte[] payloadBytes = null;

            String playerUuid = PlayerIdUtil.getPlayerUuid(playerRef);
            if (playerUuid != null) {
                String secret = cfg.getPayloadHmacSecret();
                if (secret != null && !secret.isBlank()) {
                    try {
                        String nonce = UUID.randomUUID().toString();
                        payloadBytes = TravelPayload.createSignedBytes(playerUuid, serverId, "", secret, nonce);

                        // Safety cap: do not exceed typical packet-friendly payload sizes.
                        if (payloadBytes.length > 4096) {
                            payloadBytes = null;
                        }
                    } catch (Exception e) {
                        if (DEBUG) {
                            LOGGER.atWarning().log("[PortalUI] Failed to build signed referral payload: " + e);
                        }
                        payloadBytes = null;
                    }
                }
            }

            if (payloadBytes != null) {
                playerRef.referToServer(cfg.getProxyHost(), port, payloadBytes);
            } else {
                playerRef.referToServer(cfg.getProxyHost(), port);
            }

            return;
        }

        if (d.type == DestinationType.INSTANCE_WORLD) {
            close();
            travelToInstanceWorld(store, ref, playerRef, d.id);
        }
    }

    private void reopenWithCategory(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull Store<EntityStore> store,
                                    int category) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PageManager pages = player.getPageManager();
        if (pages == null) {
            return;
        }

        PortalChoicePage page = PortalChoicePage.create(playerRef, cfg, allServerIds, category);
        pages.openCustomPage(ref, store, page);
    }

    private static int normalizeCategory(int selectedCategory) {
        if (selectedCategory < 0) return 0;
        if (selectedCategory > 3) return 3;
        return selectedCategory;
    }

    private static List<Destination> filterDestinations(@Nonnull TravelConfig cfg,
                                                        @Nonnull List<String> allServerIds,
                                                        int category) {
        List<Destination> out = new ArrayList<>();

        // Category 3 = instanced worlds (Forgotten Temple).
        if (category == 3) {
            String asset = resolveForgottenTempleAssetName();
            if (asset == null) {
                return out;
            }

            out.add(Destination.instanceWorld(
                    asset,
                    "Forgotten Temple",
                    "Enter the Forgotten Temple instance (vanilla instanced world)"
            ));
            return out;
        }

        if (allServerIds.isEmpty()) return out;

        // 1 server per category: [0]->home, [1]->world2, [2]->world3 (sorted order)
        if (category >= 0 && category <= 2 && category < allServerIds.size()) {
            String serverId = allServerIds.get(category);
            Integer port = cfg.getListenerPort(serverId);
            String desc = (port == null)
                    ? "listener: (missing port)"
                    : ("listener: " + cfg.getProxyHost() + ":" + port);
            out.add(Destination.server(serverId, serverId, desc));
            return out;
        }

        for (String serverId : allServerIds) {
            Integer port = cfg.getListenerPort(serverId);
            String desc = (port == null)
                    ? "listener: (missing port)"
                    : ("listener: " + cfg.getProxyHost() + ":" + port);
            out.add(Destination.server(serverId, serverId, desc));
        }

        return out;
    }

    private static String resolveForgottenTempleAssetName() {
        // Prefer known name (common convention in assets/IDs).
        String preferred = "Forgotten_Temple";
        if (InstancesPlugin.doesInstanceAssetExist(preferred)) {
            return preferred;
        }

        // Fallback: best-effort match from available instance assets.
        List<String> assets = InstancesPlugin.get().getInstanceAssets();
        for (String a : assets) {
            String lower = a.toLowerCase(Locale.ROOT);
            if (lower.contains("forgot") && lower.contains("temple")) {
                return a;
            }
        }

        return null;
    }

    private void travelToInstanceWorld(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> entityRef,
                                       @Nonnull PlayerRef playerRef,
                                       @Nonnull String instanceAssetName) {

        // Resolve a fresh PlayerRef (the UI-trigger path can use a stale ref).
        PlayerRef freshPlayerRef = null;

        UUID uuid = this.playerRef.getUuid();
        if (uuid != null) {
            freshPlayerRef = Universe.get().getPlayer(uuid);
        }

        if (freshPlayerRef == null) {
            freshPlayerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        }

        if (freshPlayerRef == null) {
            Player p = store.getComponent(entityRef, Player.getComponentType());
            if (p != null) {
                p.sendMessage(Message.raw("portalui: could not resolve fresh playerRef."));
            }
            return;
        }

        // Capture origin world + transform BEFORE spawning/teleporting into the instance.
        UUID returnWorldUuid = freshPlayerRef.getWorldUuid();

        // IMPORTANT:
        // Transform instances are mutable/reused by the engine. Clone to freeze the return point snapshot.
        com.hypixel.hytale.math.vector.Transform returnTransform = freshPlayerRef.getTransform().clone();

        UUID playerUuid = freshPlayerRef.getUuid();
        if (playerUuid == null) {
            Player p = store.getComponent(entityRef, Player.getComponentType());
            if (p != null) {
                p.sendMessage(Message.raw("portalui: could not resolve player uuid."));
            }
            return;
        }

        // Store our own one-shot return snapshot for the instance drain flow.
        InstanceReturnBridge.rememberReturnPoint(playerUuid, returnWorldUuid, returnTransform);

        if (DEBUG) {
            LOGGER.atInfo().log("[PortalUI] rememberReturnPoint player=" + playerUuid
                    + " world=" + returnWorldUuid
                    + " transform=" + returnTransform);
        }

        // IMPORTANT:
        // Do NOT rely on PlayerRef.getHolder() here (it can be null in this UI-trigger path).
        // Attach InstanceEntityConfig directly to the player entity via the Store.
        InstanceEntityConfig iec = store.ensureAndGetComponent(entityRef, InstanceEntityConfig.getComponentType());

        WorldReturnPoint rp = new WorldReturnPoint(returnWorldUuid, returnTransform, true);
        iec.setReturnPoint(rp);
        iec.setReturnPointOverride(rp);

        if (DEBUG) {
            LOGGER.atInfo().log("[PortalUI] Return point set world=" + returnWorldUuid + " transform=" + returnTransform);
        }

        // Current world is needed as spawn context for the instance.
        World currentWorld = Universe.get().getWorld(returnWorldUuid);
        if (currentWorld == null) {
            Player p = store.getComponent(entityRef, Player.getComponentType());
            if (p != null) {
                p.sendMessage(Message.raw("portalui: could not resolve current world."));
            }
            return;
        }

        // World-scoped events (Drain/Add) live on each World's EventRegistry.
        // Ensure our return bridge is hooked on the origin world.
        InstanceReturnBridge.registerToWorld(currentWorld);

        InstancesPlugin plugin = InstancesPlugin.get();

        // Spawn the instance and teleport player to the loading world.
        java.util.concurrent.CompletableFuture<World> instanceWorldFuture = plugin.spawnInstance(
                instanceAssetName,
                currentWorld,
                returnTransform
        );

        // Also hook the instance world's registry as soon as it's created/loaded.
        instanceWorldFuture.thenAccept(InstanceReturnBridge::registerToWorld);

        plugin.teleportPlayerToLoadingInstance(
                entityRef,
                store,
                instanceWorldFuture,
                returnTransform
        );
    }

    private enum DestinationType {
        SERVER,
        INSTANCE_WORLD
    }

    private static final class Destination {
        private final DestinationType type;
        private final String id;
        private final String title;
        private final String description;

        private Destination(DestinationType type, String id, String title, String description) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.description = description;
        }

        public static Destination server(String id, String title, String description) {
            return new Destination(DestinationType.SERVER, id, title, description);
        }

        public static Destination instanceWorld(String assetName, String title, String description) {
            return new Destination(DestinationType.INSTANCE_WORLD, assetName, title, description);
        }
    }

    private void bindCategoryIndexClick(@Nonnull UIEventBuilder events,
                                        @Nonnull String selector,
                                        int index) {

        // This MUST match ChoiceBasePage's binding logic:
        // addEventBinding(Activating, selector, EventData.of("Index", "<index>"), false)
        EventData data = EventData.of("Index", Integer.toString(index));

        // Some UI docs use the list entry as the button root; others require "#Button".
        String[] selectorCandidates = new String[]{
                selector,
                selector + " #Button"
        };

        for (String sel : selectorCandidates) {
            try {
                events.addEventBinding(CustomUIEventBindingType.Activating, sel, data, false);

                if (DEBUG) {
                    LOGGER.atInfo().log("[PortalUI] Bound category click selector=" + sel + " index=" + index);
                }
                return;

            } catch (Exception e) {
                // Try next selector candidate.
                if (DEBUG) {
                    LOGGER.atWarning().log("[PortalUI] Failed to bind selector candidate=" + sel + " err=" + e);
                }
            }
        }

        LOGGER.atWarning().log("[PortalUI] Failed to bind category click selector=" + selector + " index=" + index);
    }
}