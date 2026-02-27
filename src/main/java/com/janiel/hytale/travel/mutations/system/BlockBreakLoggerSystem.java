package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.ui.MutationsPage;

import java.util.UUID;

public final class BlockBreakLoggerSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public BlockBreakLoggerSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
            int entityId,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            BreakBlockEvent event
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();

        int newCount = MutationsRepository.incrementBlocksBroken(playerUuid);

        Vector3i pos = event.getTargetBlock();
        LOGGER.atInfo().log("BreakBlockEvent: entityId=" + entityId
                + " uuid=" + playerUuid
                + " pos=" + pos
                + " blockType=" + event.getBlockType()
                + " itemInHand=" + event.getItemInHand()
                + " blocksBroken=" + newCount);

        // Si el jugador tiene la MutationsPage abierta, re-abrimos la misma página para reflejar el contador nuevo.
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PageManager pages = player.getPageManager();
        if (pages == null) {
            return;
        }

        CustomUIPage current = pages.getCustomPage();
        if (current instanceof MutationsPage) {
            pages.openCustomPage(ref, store, MutationsPage.create(playerRef));
        }
    }
}