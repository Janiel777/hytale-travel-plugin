package com.janiel.hytale.travel.mutations.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.janiel.hytale.travel.mutations.persistence.MutationsRepository;
import com.janiel.hytale.travel.mutations.persistence.MutationsState;
import com.janiel.hytale.travel.mutations.ui.MutationsPage;

import java.util.UUID;

public final class BlockBreakLoggerSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Exists in assets_tree.txt:
    // Server/Audio/SoundEvents/SFX/Crafting/SFX_Workbench_Upgrade_Complete_Default.json :contentReference[oaicite:2]{index=2}
    private static final String LEVEL_UP_SOUND_ID = "SFX_Discovery_Z1_Medium";

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

        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) {
            // Example: placing torch can trigger BreakBlockEvent with null hand item; do not count.
            return;
        }

        String itemId = itemInHand.getItemId();
        if (itemId == null || !itemId.startsWith("Tool_Pickaxe_")) {
            // Only count breaks done with a pickaxe for the mining mutation.
            return;
        }

        Object blockId = event.getBlockType().getId();
        if (blockId != null && "Empty".equals(blockId.toString())) {
            return;
        }

        int beforeLevel = MutationsRepository.getMiningLevel(playerUuid);
        MutationsState state = MutationsRepository.incrementBlocksBrokenAndGetState(playerUuid);

        int afterLevel = state.getMiningLevel();
        if (afterLevel > beforeLevel) {
            showMiningLevelUp(playerRef, afterLevel);
        }

        Vector3i pos = event.getTargetBlock();
        LOGGER.atInfo().log("BreakBlockEvent: entityId=" + entityId
                + " uuid=" + playerUuid
                + " pos=" + pos
                + " blockType=" + event.getBlockType()
                + " itemInHand=" + event.getItemInHand()
                + " blocksBroken=" + state.getBlocksBroken()
                + " miningLevel=" + state.getMiningLevel());

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

    private static void showMiningLevelUp(PlayerRef playerRef, int newLevel) {
        // Title overlay (primary + secondary)
        EventTitleUtil.showEventTitleToPlayer(
                playerRef,
                Message.raw("Mining Mutation"),
                Message.raw("Level " + newLevel),
                true
        );

        // Sound (plays only if the ID exists in the SoundEvent asset map)
        int soundIndex = SoundEvent.getAssetMap().getIndexOrDefault(LEVEL_UP_SOUND_ID, SoundEvent.EMPTY_ID);
        if (soundIndex != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
        }
    }
}