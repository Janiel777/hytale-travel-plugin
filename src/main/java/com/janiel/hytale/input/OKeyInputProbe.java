package com.janiel.hytale.input;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.ui.MutationsPage;

public final class OKeyInputProbe {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private OKeyInputProbe() {
    }

    public static void register() {
        PacketAdapters.registerInbound((PlayerPacketFilter) OKeyInputProbe::handleInbound);
        LOGGER.atInfo().log("Input: OKeyInputProbe registered");
    }

    private static boolean handleInbound(PlayerRef playerRef, Packet packet) {
        if (playerRef == null || packet == null) {
            return false;
        }

        if (!(packet instanceof ChatMessage)) {
            return false;
        }

        ChatMessage chatMessage = (ChatMessage) packet;
        if (chatMessage.message == null) {
            return false;
        }

        String message = chatMessage.message.trim();

        if (!isOKeyHiddenCommand(message)) {
            return false;
        }

        LOGGER.atInfo().log("[OKeyInputProbe] Hidden O-chat trigger detected. player="
                + playerRef.getUsername()
                + " uuid=" + playerRef.getUuid()
                + " message=" + message);

        openMutationsPage(playerRef);

        LOGGER.atInfo().log("[OKeyInputProbe] Blocked hidden O command and scheduled Mutations page open. player="
                + playerRef.getUsername()
                + " uuid=" + playerRef.getUuid()
                + " message=" + message);

        return true;
    }

    private static boolean isOKeyHiddenCommand(String message) {
        return message.equalsIgnoreCase("/gm c")
                || message.equalsIgnoreCase("/gm a")
                || message.equalsIgnoreCase("/gm creative")
                || message.equalsIgnoreCase("/gm adventure");
    }

    @SuppressWarnings("unchecked")
    private static void openMutationsPage(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            LOGGER.atWarning().log("[OKeyInputProbe] Could not schedule Mutations page open because player ref was null/invalid. uuid="
                    + playerRef.getUuid());
            return;
        }

        Store<EntityStore> store = (Store<EntityStore>) ref.getStore();
        if (store == null) {
            LOGGER.atWarning().log("[OKeyInputProbe] Could not schedule Mutations page open because store was null. uuid="
                    + playerRef.getUuid());
            return;
        }

        EntityStore entityStore = (EntityStore) store.getExternalData();
        if (entityStore == null) {
            LOGGER.atWarning().log("[OKeyInputProbe] Could not schedule Mutations page open because EntityStore was null. uuid="
                    + playerRef.getUuid());
            return;
        }

        World world = entityStore.getWorld();
        if (world == null) {
            LOGGER.atWarning().log("[OKeyInputProbe] Could not schedule Mutations page open because world was null. uuid="
                    + playerRef.getUuid());
            return;
        }

        world.execute(() -> {
            if (!ref.isValid()) {
                LOGGER.atWarning().log("[OKeyInputProbe] Player ref became invalid before opening Mutations page. uuid="
                        + playerRef.getUuid());
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                LOGGER.atWarning().log("[OKeyInputProbe] Could not resolve Player component on world thread. uuid="
                        + playerRef.getUuid());
                return;
            }

            PageManager pages = player.getPageManager();
            if (pages == null) {
                LOGGER.atWarning().log("[OKeyInputProbe] Could not resolve PageManager on world thread. uuid="
                        + playerRef.getUuid());
                return;
            }

            pages.openCustomPage(ref, store, MutationsPage.create(playerRef));

            LOGGER.atInfo().log("[OKeyInputProbe] Mutations page opened from O key. player="
                    + playerRef.getUsername()
                    + " uuid=" + playerRef.getUuid());
        });
    }
}