package com.janiel.hytale.travel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PortalChoicePage extends ChoiceBasePage {

    private static final int CATEGORY_INDEX_BASE = 1000;

    // Set false after it works.
    private static final boolean DEBUG = true;
    private static boolean printedBindingEnumOnce = false;

    private final PlayerRef playerRef;
    private final TravelConfig cfg;

    private final List<String> allServerIds;
    private final List<String> visibleServerIds;

    private final int selectedCategory;
    private final ChoiceElement[] categoryElements;

    private PortalChoicePage(@Nonnull PlayerRef playerRef,
                             @Nonnull TravelConfig cfg,
                             @Nonnull List<String> allServerIds,
                             @Nonnull List<String> visibleServerIds,
                             int selectedCategory,
                             @Nonnull ChoiceElement[] elements,
                             @Nonnull ChoiceElement[] categoryElements) {

        super(playerRef, elements, "Pages/JanielPortal/PortalHome.ui");

        this.playerRef = playerRef;
        this.cfg = cfg;
        this.allServerIds = allServerIds;
        this.visibleServerIds = visibleServerIds;
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
        List<String> visible = filterServers(allServerIds, cat);

        ChoiceElement[] elements = new ChoiceElement[visible.size()];
        for (int i = 0; i < visible.size(); i++) {
            String id = visible.get(i);

            Integer port = cfg.getListenerPort(id);
            String desc = (port == null)
                    ? "listener: (missing port)"
                    : ("listener: " + cfg.getProxyHost() + ":" + port);

            elements[i] = new PortalDestinationElement(id, desc);
        }

        ChoiceElement[] categories = new ChoiceElement[]{
                new PortalCategoryElement("Category1", 0),
                new PortalCategoryElement("Category2", 1),
                new PortalCategoryElement("Category3", 2)
        };

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
            System.out.println("[PortalUI] handleDataEvent index=" + index);
        }

        // Category clicks
        if (index >= CATEGORY_INDEX_BASE && index < CATEGORY_INDEX_BASE + categoryElements.length) {
            int cat = index - CATEGORY_INDEX_BASE;
            reopenWithCategory(ref, store, cat);
            return;
        }

        // Server clicks (index relative to visibleServerIds)
        if (index < 0 || index >= visibleServerIds.size()) {
            return;
        }

        String serverId = visibleServerIds.get(index);
        Integer port = cfg.getListenerPort(serverId);
        if (port == null) {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.sendMessage(Message.raw("portalui: invalid port for serverId: " + serverId));
            }
            return;
        }

        byte[] payloadBytes = null;

        String playerUuid = PlayerIdUtil.getPlayerUuid(playerRef);
        if (playerUuid != null) {
            String secret = cfg.getPayloadHmacSecret();
            if (secret != null && !secret.isBlank()) {
                try {
                    String nonce = UUID.randomUUID().toString();
                    payloadBytes = TravelPayload.createSignedBytes(playerUuid, serverId, "", secret, nonce);

                    if (payloadBytes.length > 4096) {
                        payloadBytes = null;
                    }
                } catch (Exception ignored) {
                    payloadBytes = null;
                }
            }
        }

        if (payloadBytes != null) {
            playerRef.referToServer(cfg.getProxyHost(), port, payloadBytes);
        } else {
            playerRef.referToServer(cfg.getProxyHost(), port);
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
        if (selectedCategory > 2) return 2;
        return selectedCategory;
    }

    private static List<String> filterServers(@Nonnull List<String> allServerIds, int category) {
        List<String> out = new ArrayList<>();
        if (allServerIds.isEmpty()) return out;

        // 1 server per category: [0]->home, [1]->world2, [2]->world3 (sorted order)
        if (category >= 0 && category <= 2 && category < allServerIds.size()) {
            out.add(allServerIds.get(category));
            return out;
        }

        out.addAll(allServerIds);
        return out;
    }

    private void bindCategoryIndexClick(@Nonnull UIEventBuilder events,
                                        @Nonnull String selector,
                                        int index) {

        // This MUST match ChoiceBasePage's binding logic:
        // addEventBinding(Activating, selector, EventData.of("Index", "<index>"), false)
        EventData data = EventData.of("Index", Integer.toString(index));

        // Some UI docs use the list entry as the button root; others require "#Button".
        String[] selectorCandidates = new String[] {
                selector,
                selector + " #Button"
        };

        for (String sel : selectorCandidates) {
            try {
                events.addEventBinding(CustomUIEventBindingType.Activating, sel, data, false);

                if (DEBUG) {
                    System.out.println("[PortalUI] Bound category click selector=" + sel + " index=" + index);
                }
                return;

            } catch (Exception ignored) {
                // Try next selector candidate.
            }
        }

        System.out.println("[PortalUI] Failed to bind category click selector=" + selector + " index=" + index);
    }



}
