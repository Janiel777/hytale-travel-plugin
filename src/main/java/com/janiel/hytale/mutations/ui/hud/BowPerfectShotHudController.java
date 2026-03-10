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
import java.util.Locale;

public final class BowPerfectShotHudController {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String CHARGE_BAR_ID = "janielPerfectShotChargeBar";
    private static final String CHARGE_BAR_FILL_ID = "janielPerfectShotChargeFill";
    private static final String PERFECT_WINDOW_LEFT_MARKER_ID = "janielPerfectShotWindowLeftMarker";
    private static final String PERFECT_WINDOW_RIGHT_MARKER_ID = "janielPerfectShotWindowRightMarker";

    private static final float HUD_SCALE = 0.50f;

    private static final int BASE_CHARGE_BAR_WIDTH = 300;
    private static final int BASE_CHARGE_BAR_HEIGHT = 24;
    private static final String CHARGE_BAR_IMAGE = "Charge-Bar/Bar.png";
    private static final String CHARGE_BAR_FILL_IMAGE = "Charge-Bar/Filler.png";

    private static final String PERFECT_WINDOW_MARKER_IMAGE = "Charge-Bar/Marker.png";
    private static final int BASE_PERFECT_WINDOW_MARKER_WIDTH = 5;
    private static final int BASE_PERFECT_WINDOW_MARKER_HEIGHT = 34;

    private static final int BASE_HUD_CANVAS_WIDTH = 300;
    private static final int BASE_HUD_CANVAS_HEIGHT = 114;
    private static final int BASE_CHARGE_BAR_TOP = 110;
    private static final int BASE_MARKER_TOP = 105;

    private static final int CHARGE_BAR_WIDTH = scale(BASE_CHARGE_BAR_WIDTH);
    private static final int CHARGE_BAR_HEIGHT = scale(BASE_CHARGE_BAR_HEIGHT);
    private static final int PERFECT_WINDOW_MARKER_WIDTH = scale(BASE_PERFECT_WINDOW_MARKER_WIDTH);
    private static final int PERFECT_WINDOW_MARKER_HEIGHT = scale(BASE_PERFECT_WINDOW_MARKER_HEIGHT);

    private static final int HUD_CANVAS_WIDTH = scale(BASE_HUD_CANVAS_WIDTH);
    private static final int HUD_CANVAS_HEIGHT = scale(BASE_HUD_CANVAS_HEIGHT);
    private static final int CHARGE_BAR_TOP = scale(BASE_CHARGE_BAR_TOP);
    private static final int MARKER_TOP = scale(BASE_MARKER_TOP);
    private static final float PERFECT_WINDOW_VISUAL_OFFSET = -0.01f;

    private static final int PERFECT_WINDOW_LEFT_MARKER_LEFT =
            resolveMarkerLeftPixels(
                    BowPerfectShotDefinitions.perfectShotMinNormalized() + PERFECT_WINDOW_VISUAL_OFFSET
            );

    private static final int PERFECT_WINDOW_RIGHT_MARKER_LEFT =
            resolveMarkerLeftPixels(
                    BowPerfectShotDefinitions.perfectShotMaxNormalized() + PERFECT_WINDOW_VISUAL_OFFSET
            );

    private static final long SESSION_TIMEOUT_NANOS = 400_000_000L;
    private static final long HUD_REFRESH_RATE_MS = 10L;

    private static final ConcurrentMap<UUID, ChargeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, HyUIHud> ACTIVE_HUDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PlayerRef> ACTIVE_PLAYER_REFS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_UPDATE_NANOS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Integer> LAST_LOGGED_FILL_WIDTH_BY_PLAYER = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_LOGGED_FILL_NANOS_BY_PLAYER = new ConcurrentHashMap<>();
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

        applyStateToHud(playerUuid, existingHud, session);
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
        LAST_LOGGED_FILL_WIDTH_BY_PLAYER.remove(playerRef.getUuid());
        LAST_LOGGED_FILL_NANOS_BY_PLAYER.remove(playerRef.getUuid());
//        showReticle(playerRef);
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
                applyStateToHud(playerUuid, existingHud, session);
            }
            return;
        }

        ChargeSession session = SESSIONS.get(playerUuid);
        if (session == null) {
            return;
        }

//        hideReticle(playerRef);

        HyUIHud hud = buildHud(playerRef, session);
        ACTIVE_HUDS.put(playerUuid, hud);
        ACTIVE_PLAYER_REFS.put(playerUuid, playerRef);
        applyStateToHud(playerUuid, hud, session);
    }

    private static HyUIHud buildHud(PlayerRef playerRef, ChargeSession session) {
        HyUIHud hud = HudBuilder.hudForPlayer(playerRef)
                .withRefreshRate(HUD_REFRESH_RATE_MS)
                .fromHtml(buildHudHtml())
                .show(playerRef);

        hud.setRefreshListener(refreshedHud -> {
            ChargeSession latestSession = SESSIONS.get(playerRef.getUuid());
            if (latestSession == null) {
                return;
            }

            applyStateToHud(playerRef.getUuid(), refreshedHud, latestSession);
        });

        return hud;
    }

    private static void applyStateToHud(UUID playerUuid, HyUIHud hud, ChargeSession session) {
        if (session == null) {
            return;
        }

        long now = System.nanoTime();

        float visualChargeSeconds = resolveVisualChargeSeconds(session);
        int filledPixels = resolveFillWidthPixels(visualChargeSeconds);

        hud.getById(CHARGE_BAR_ID, ImageBuilder.class).ifPresent(image -> {
            image.withImage(CHARGE_BAR_IMAGE);
            image.withVisible(true);
        });

        hud.getById(CHARGE_BAR_FILL_ID, ImageBuilder.class).ifPresent(image -> {
            HyUIAnchor anchor = image.getAnchor();
            if (anchor == null) {
                anchor = new HyUIAnchor();
            }

            anchor.setLeft(0);
            anchor.setTop(CHARGE_BAR_TOP);
            anchor.setWidth(filledPixels);
            anchor.setHeight(CHARGE_BAR_HEIGHT);

            image.withAnchor(anchor);
            image.withImage(CHARGE_BAR_FILL_IMAGE);
            image.withVisible(filledPixels > 0);
        });

        hud.getById(PERFECT_WINDOW_LEFT_MARKER_ID, ImageBuilder.class).ifPresent(image -> {
            image.withImage(PERFECT_WINDOW_MARKER_IMAGE);
            image.withVisible(true);
        });

        hud.getById(PERFECT_WINDOW_RIGHT_MARKER_ID, ImageBuilder.class).ifPresent(image -> {
            image.withImage(PERFECT_WINDOW_MARKER_IMAGE);
            image.withVisible(true);
        });

        hud.updatePage(true);

        Integer previousFillWidth = LAST_LOGGED_FILL_WIDTH_BY_PLAYER.put(playerUuid, filledPixels);
        Long previousFillNanos = LAST_LOGGED_FILL_NANOS_BY_PLAYER.put(playerUuid, now);

        if (previousFillWidth == null || previousFillWidth != filledPixels) {
            long deltaMsSinceLastFill = previousFillNanos == null
                    ? -1L
                    : (now - previousFillNanos) / 1_000_000L;

            int deltaPixels = previousFillWidth == null
                    ? 0
                    : filledPixels - previousFillWidth;

            boolean fillReset = previousFillWidth != null && filledPixels < previousFillWidth;

            LOGGER.atInfo().log(
                    "[BowPerfectShotHud] Fill updated. playerUuid=" + playerUuid
                            + " previousFillWidth=" + previousFillWidth
                            + " currentFillWidth=" + filledPixels + "/" + CHARGE_BAR_WIDTH
                            + " deltaPixels=" + deltaPixels
                            + " fillReset=" + fillReset
                            + " deltaMsSinceLastFill=" + deltaMsSinceLastFill
                            + " normalizedCharge=" + session.getNormalizedCharge()
                            + " visualChargeSeconds=" + visualChargeSeconds
                            + " visualTimelineSeconds=" + BowPerfectShotDefinitions.visualTimelineSeconds()
                            + " leftMarkerLeft=" + PERFECT_WINDOW_LEFT_MARKER_LEFT
                            + " rightMarkerLeft=" + PERFECT_WINDOW_RIGHT_MARKER_LEFT
                            + " actualMaxChargeSeconds=" + session.getMaxChargeSeconds()
            );
        }
    }

    private static float resolveVisualChargeSeconds(ChargeSession session) {
        return Math.min(
                BowPerfectShotDefinitions.clamp01(session.getNormalizedCharge())
                        * BowPerfectShotDefinitions.visualTimelineSeconds(),
                BowPerfectShotDefinitions.visualTimelineSeconds()
        );
    }

    private static int resolveFillWidthPixels(float visualChargeSeconds) {
        float visualTimelineSeconds = BowPerfectShotDefinitions.visualTimelineSeconds();
        if (visualTimelineSeconds <= 0.0f) {
            return 0;
        }

        float normalized =
                BowPerfectShotDefinitions.clampNonNegative(visualChargeSeconds)
                        / visualTimelineSeconds;

        int filledPixels = Math.round(
                BowPerfectShotDefinitions.clamp01(normalized) * CHARGE_BAR_WIDTH
        );

        if (filledPixels < 0) {
            return 0;
        }

        if (filledPixels > CHARGE_BAR_WIDTH) {
            return CHARGE_BAR_WIDTH;
        }

        return filledPixels;
    }

    private static int scale(int value) {
        return Math.max(1, Math.round(value * HUD_SCALE));
    }

    private static int resolveMarkerLeftPixels(float normalizedBoundary) {
        int boundaryPixelsFromLeft = Math.round(
                BowPerfectShotDefinitions.clamp01(normalizedBoundary) * CHARGE_BAR_WIDTH
        );

        if (boundaryPixelsFromLeft < 0) {
            return 0;
        }

        int maxLeft = CHARGE_BAR_WIDTH - PERFECT_WINDOW_MARKER_WIDTH;
        if (boundaryPixelsFromLeft > maxLeft) {
            return maxLeft;
        }

        return boundaryPixelsFromLeft;
    }

    private static String buildHudHtml() {
        return String.format(
                Locale.ROOT,
                """
                <div id="perfect-shot-root" style="layout-mode: Full; anchor-full: 0;">
                  <div id="perfect-shot-wrapper" style="layout-mode: CenterMiddle; anchor-full: 0;">
                    <div id="perfect-shot-canvas" style="layout-mode: Full; anchor-width: %d; anchor-height: %d;">
                      <img id="%s"
                           src="%s"
                           style="anchor-left: 0; anchor-top: %d; anchor-width: %d; anchor-height: %d;" />
                      <img id="%s"
                           src="%s"
                           style="anchor-left: 0; anchor-top: %d; anchor-width: 0; anchor-height: %d;" />
                      <img id="%s"
                           src="%s"
                           style="anchor-left: %d; anchor-top: %d; anchor-width: %d; anchor-height: %d;" />
                      <img id="%s"
                           src="%s"
                           style="anchor-left: %d; anchor-top: %d; anchor-width: %d; anchor-height: %d;" />
                    </div>
                  </div>
                </div>
                """,
                HUD_CANVAS_WIDTH,
                HUD_CANVAS_HEIGHT,
                CHARGE_BAR_ID,
                CHARGE_BAR_IMAGE,
                CHARGE_BAR_TOP,
                CHARGE_BAR_WIDTH,
                CHARGE_BAR_HEIGHT,
                CHARGE_BAR_FILL_ID,
                CHARGE_BAR_FILL_IMAGE,
                CHARGE_BAR_TOP,
                CHARGE_BAR_HEIGHT,
                PERFECT_WINDOW_LEFT_MARKER_ID,
                PERFECT_WINDOW_MARKER_IMAGE,
                PERFECT_WINDOW_LEFT_MARKER_LEFT,
                MARKER_TOP,
                PERFECT_WINDOW_MARKER_WIDTH,
                PERFECT_WINDOW_MARKER_HEIGHT,
                PERFECT_WINDOW_RIGHT_MARKER_ID,
                PERFECT_WINDOW_MARKER_IMAGE,
                PERFECT_WINDOW_RIGHT_MARKER_LEFT,
                MARKER_TOP,
                PERFECT_WINDOW_MARKER_WIDTH,
                PERFECT_WINDOW_MARKER_HEIGHT
        );
    }

    private static void removeHud(PlayerRef playerRef, HyUIHud hud) {
        if (playerRef != null) {
            UUID playerUuid = playerRef.getUuid();
            ACTIVE_HUDS.remove(playerUuid, hud);
            ACTIVE_PLAYER_REFS.remove(playerUuid);
            LAST_UPDATE_NANOS.remove(playerUuid);
            SESSIONS.remove(playerUuid);
            LAST_LOGGED_FILL_WIDTH_BY_PLAYER.remove(playerUuid);
            LAST_LOGGED_FILL_NANOS_BY_PLAYER.remove(playerUuid);
//            showReticle(playerRef);
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