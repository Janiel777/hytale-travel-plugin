package com.janiel.hytale.mutations.ui.hud;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.ImageBuilder;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.weapon.effects.BowPerfectShotDefinitions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BowPerfectShotHudController {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String SHRINKING_CIRCLE_ID = "janielPerfectShotShrinkingCircle";
    private static final String PERFECT_WINDOW_CIRCLE_ID = "janielPerfectShotWindowCircle";

    private static final int SHRINKING_FRAME_COUNT = 36;
    private static final int SHRINKING_DISPLAY_SIZE = 132;
    private static final String GUIDE_CIRCLE_IMAGE = "Melee.png";

    private static final int PERFECT_WINDOW_DIAMETER = 34;

    private static final long SESSION_TIMEOUT_NANOS = 400_000_000L;
    private static final long HUD_REFRESH_RATE_MS = 16L;

    private static final ConcurrentMap<UUID, ChargeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, HyUIHud> ACTIVE_HUDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PlayerRef> ACTIVE_PLAYER_REFS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_UPDATE_NANOS = new ConcurrentHashMap<>();

    private BowPerfectShotHudController() {
    }

    public static void updateCharge(
            Ref<EntityStore> entityRef,
            CommandBuffer<EntityStore> commandBuffer,
            float normalizedCharge,
            float maxChargeSeconds
    ) {
        PlayerRef playerRef = getPlayerRef(entityRef, commandBuffer);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        long now = System.nanoTime();

        ChargeSession session = new ChargeSession(
                BowPerfectShotDefinitions.clamp01(normalizedCharge),
                BowPerfectShotDefinitions.clampNonNegative(maxChargeSeconds),
                now + SESSION_TIMEOUT_NANOS
        );

        SESSIONS.put(playerUuid, session);
        ACTIVE_PLAYER_REFS.put(playerUuid, playerRef);
        LAST_UPDATE_NANOS.put(playerUuid, now);

        HyUIHud existingHud = ACTIVE_HUDS.get(playerUuid);
        if (existingHud == null) {
            ensureHudShown(playerRef);
            return;
        }

        applyStateToHud(existingHud, session);
    }

    public static void release(
            Ref<EntityStore> entityRef,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        PlayerRef playerRef = getPlayerRef(entityRef, commandBuffer);
        if (playerRef == null) {
            return;
        }

        HyUIHud hud = ACTIVE_HUDS.get(playerRef.getUuid());
        if (hud != null) {
            removeHud(playerRef, hud);
            return;
        }

        SESSIONS.remove(playerRef.getUuid());
        ACTIVE_PLAYER_REFS.remove(playerRef.getUuid());
        LAST_UPDATE_NANOS.remove(playerRef.getUuid());
        showReticle(playerRef);
    }

    public static void cleanupExpired() {
        long now = System.nanoTime();

        for (Map.Entry<UUID, HyUIHud> entry : ACTIVE_HUDS.entrySet()) {
            UUID playerUuid = entry.getKey();
            HyUIHud hud = entry.getValue();

            Long lastUpdate = LAST_UPDATE_NANOS.get(playerUuid);
            if (lastUpdate == null) {
                PlayerRef playerRef = ACTIVE_PLAYER_REFS.get(playerUuid);
                removeHud(playerRef, hud);
                continue;
            }

            if (now - lastUpdate <= SESSION_TIMEOUT_NANOS) {
                continue;
            }

            PlayerRef playerRef = ACTIVE_PLAYER_REFS.get(playerUuid);
            removeHud(playerRef, hud);
        }
    }

    private static void ensureHudShown(PlayerRef playerRef) {
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();

        HyUIHud existingHud = ACTIVE_HUDS.get(playerUuid);
        if (existingHud != null) {
            ChargeSession session = SESSIONS.get(playerUuid);
            if (session != null) {
                applyStateToHud(existingHud, session);
            }
            return;
        }

        ChargeSession session = SESSIONS.get(playerUuid);
        if (session == null) {
            return;
        }

        hideReticle(playerRef);

        HyUIHud hud = buildHud(playerRef, session);
        ACTIVE_HUDS.put(playerUuid, hud);
        ACTIVE_PLAYER_REFS.put(playerUuid, playerRef);
        applyStateToHud(hud, session);
    }

    private static HyUIHud buildHud(PlayerRef playerRef, ChargeSession session) {
        HudBuilder builder = HudBuilder.hudForPlayer(playerRef)
                .withRefreshRate(HUD_REFRESH_RATE_MS)
                .addElement(
                        ImageBuilder.image()
                                .withId(SHRINKING_CIRCLE_ID)
                                .withImage(resolveShrinkingFrameImage(session.getNormalizedCharge()))
                                .withLayoutMode("CenterMiddle")
                                .withHitTestVisible(false)
                                .withVisible(true)
                                .withAnchor(centeredSquareAnchor(SHRINKING_DISPLAY_SIZE))
                )
                .addElement(
                        ImageBuilder.image()
                                .withId(PERFECT_WINDOW_CIRCLE_ID)
                                .withImage(GUIDE_CIRCLE_IMAGE)
                                .withLayoutMode("CenterMiddle")
                                .withHitTestVisible(false)
                                .withVisible(true)
                                .withAnchor(centeredSquareAnchor(PERFECT_WINDOW_DIAMETER))
                );

        return builder.show(playerRef);
    }

    private static void applyStateToHud(HyUIHud hud, ChargeSession session) {
        if (session == null) {
            return;
        }

        String frameImage = resolveShrinkingFrameImage(session.getNormalizedCharge());

        hud.getById(SHRINKING_CIRCLE_ID, ImageBuilder.class).ifPresent(image -> {
            image.withImage(frameImage);
            image.withLayoutMode("CenterMiddle");
            image.withAnchor(centeredSquareAnchor(SHRINKING_DISPLAY_SIZE));
            image.withVisible(true);
        });

        hud.getById(PERFECT_WINDOW_CIRCLE_ID, ImageBuilder.class).ifPresent(image -> {
            image.withImage(GUIDE_CIRCLE_IMAGE);
            image.withLayoutMode("CenterMiddle");
            image.withAnchor(centeredSquareAnchor(PERFECT_WINDOW_DIAMETER));
            image.withVisible(true);
        });

        hud.updatePage(true);
    }

    private static String resolveShrinkingFrameImage(float normalizedCharge) {
        int frameIndex = resolveShrinkingFrameIndex(normalizedCharge);
        return String.format("perfect_shoot_frame_%02d.png", frameIndex);
    }

    private static int resolveShrinkingFrameIndex(float normalizedCharge) {
        int frameIndex = (int) Math.floor(
                BowPerfectShotDefinitions.clamp01(normalizedCharge) * (SHRINKING_FRAME_COUNT - 1)
        );

        if (frameIndex < 0) {
            return 0;
        }

        if (frameIndex >= SHRINKING_FRAME_COUNT) {
            return SHRINKING_FRAME_COUNT - 1;
        }

        return frameIndex;
    }


    private static HyUIAnchor centeredSquareAnchor(int size) {
        return new HyUIAnchor()
                .setHorizontal(0)
                .setVertical(0)
                .setWidth(size)
                .setHeight(size);
    }

    private static void removeHud(PlayerRef playerRef, HyUIHud hud) {
        if (playerRef != null) {
            UUID playerUuid = playerRef.getUuid();
            ACTIVE_HUDS.remove(playerUuid, hud);
            ACTIVE_PLAYER_REFS.remove(playerUuid);
            LAST_UPDATE_NANOS.remove(playerUuid);
            SESSIONS.remove(playerUuid);
            showReticle(playerRef);
        }

        try {
            hud.remove();
        } catch (Exception ex) {
            LOGGER.atWarning().log("[BowPerfectShotHud] Failed to remove HUD cleanly: " + ex);
        }
    }

    private static void hideReticle(PlayerRef playerRef) {
        Player player = getPlayer(playerRef);
        if (player == null) {
            LOGGER.atWarning().log("[BowPerfectShotHud] hideReticle skipped because Player component resolved to null. playerRef=" + playerRef);
            return;
        }

        player.getHudManager().hideHudComponents(playerRef, HudComponent.Reticle);
        LOGGER.atInfo().log("[BowPerfectShotHud] hideReticle sent. player=" + playerRef.getUsername());
    }

    private static void showReticle(PlayerRef playerRef) {
        Player player = getPlayer(playerRef);
        if (player == null) {
            LOGGER.atWarning().log("[BowPerfectShotHud] showReticle skipped because Player component resolved to null. playerRef=" + playerRef);
            return;
        }

        player.getHudManager().showHudComponents(playerRef, HudComponent.Reticle);
        LOGGER.atInfo().log("[BowPerfectShotHud] showReticle sent. player=" + playerRef.getUsername());
    }

    private static PlayerRef getPlayerRef(
            Ref<EntityStore> entityRef,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        if (entityRef == null || !entityRef.isValid()) {
            return null;
        }

        if (commandBuffer != null && PlayerRef.getComponentType() != null) {
            PlayerRef playerRef = commandBuffer.getComponent(
                    entityRef,
                    PlayerRef.getComponentType()
            );
            if (playerRef != null) {
                return playerRef;
            }
        }

        return null;
    }

    private static Player getPlayer(PlayerRef playerRef) {
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }

        if (Player.getComponentType() == null) {
            return null;
        }

        return playerRef.getComponent(Player.getComponentType());
    }

    private static final class ChargeSession {

        private final float normalizedCharge;
        private final float maxChargeSeconds;
        private final long expiresAtNanos;

        private ChargeSession(
                float normalizedCharge,
                float maxChargeSeconds,
                long expiresAtNanos
        ) {
            this.normalizedCharge = normalizedCharge;
            this.maxChargeSeconds = maxChargeSeconds;
            this.expiresAtNanos = expiresAtNanos;
        }

        public float getNormalizedCharge() {
            return normalizedCharge;
        }

        public float getMaxChargeSeconds() {
            return maxChargeSeconds;
        }

        public long getExpiresAtNanos() {
            return expiresAtNanos;
        }
    }
}