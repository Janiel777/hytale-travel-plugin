package com.janiel.hytale.mutations.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.interaction.ProjectileInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.janiel.hytale.mutations.component.BowPerfectShotChargeTrackerComponent;
import com.janiel.hytale.mutations.component.PendingPerfectShotComponent;
import com.janiel.hytale.mutations.component.PerfectShotMarkerComponent;
import com.janiel.hytale.mutations.weapon.effects.BowPerfectShotDefinitions;
import com.janiel.hytale.mutations.ui.hud.BowPerfectShotHudController;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.janiel.hytale.mutations.persistence.MutationsCache;
import com.janiel.hytale.mutations.persistence.MutationsState;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PerfectShotProjectileInteraction extends ProjectileInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicLong SHOT_SEQUENCE = new AtomicLong(0L);

    private static final String PERFECT_SHOT_LOCAL_SOUND_EVENT_ID = "SFX_Bow_T2_Signature_Shoot_Local";

    public static final BuilderCodec<PerfectShotProjectileInteraction> CODEC =
            BuilderCodec.builder(
                            PerfectShotProjectileInteraction.class,
                            PerfectShotProjectileInteraction::new,
                            ProjectileInteraction.CODEC
                    )
                    .documentation("Projectile interaction that marks shortbow projectiles for perfect shot logic.")
                    .build();

    public PerfectShotProjectileInteraction() {
        super();
    }

    @Override
    protected void firstRun(
            InteractionType interactionType,
            InteractionContext context,
            CooldownHandler cooldownHandler
    ) {
        ProjectileConfig projectileConfig = this.getConfig();
        if (projectileConfig == null) {
            LOGGER.atWarning().log("[BowPerfectShot] ProjectileInteraction skipped because config resolved to null. interactionType="
                    + interactionType + " configId=" + this.config);
            return;
        }

        Ref<EntityStore> shooterRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        if (commandBuffer == null || shooterRef == null) {
            LOGGER.atWarning().log("[BowPerfectShot] ProjectileInteraction skipped because commandBuffer/entity was null. interactionType="
                    + interactionType + " configId=" + this.config);
            return;
        }

        InteractionSyncData clientState = context.getClientState();
        InteractionSyncData state = context.getState();

        boolean hasClientAimData = clientState != null
                && clientState.attackerPos != null
                && clientState.attackerRot != null;

        Vector3d spawnPosition;
        Vector3d direction;
        UUID generatedUuid;

        if (hasClientAimData) {
            spawnPosition = PositionUtil.toVector3d(clientState.attackerPos);

            Vector3f rotation = PositionUtil.toRotation(clientState.attackerRot);
            direction = new Vector3d(rotation.getYaw(), rotation.getPitch());
            generatedUuid = clientState.generatedUUID;
        } else {
            Transform look = TargetUtil.getLook(shooterRef, commandBuffer);
            spawnPosition = look.getPosition();
            direction = look.getDirection();
            generatedUuid = state == null ? null : state.generatedUUID;
        }

        float chargeSeconds = 0.0f;
        float normalizedCharge = 0.0f;
        float maxChargeSeconds = BowPerfectShotDefinitions.fullChargeSeconds();
        boolean trackerHeld = false;

        if (BowPerfectShotChargeTrackerComponent.getComponentType() != null) {
            BowPerfectShotChargeTrackerComponent tracker = commandBuffer.getComponent(
                    shooterRef,
                    BowPerfectShotChargeTrackerComponent.getComponentType()
            );

            if (tracker != null) {
                chargeSeconds = tracker.getChargeSeconds();
                normalizedCharge = tracker.getNormalizedCharge();
                maxChargeSeconds = tracker.getMaxChargeSeconds();
                trackerHeld = tracker.isHeld();
            }
        }

        normalizedCharge = BowPerfectShotDefinitions.normalizeVisualChargeSeconds(chargeSeconds);

        int bowMutationLevel = 0;
        PlayerRef shooterPlayerRef = commandBuffer.getComponent(
                shooterRef,
                PlayerRef.getComponentType()
        );

        if (shooterPlayerRef != null) {
            MutationsState mutationState = MutationsCache.getOrLoad(shooterPlayerRef.getUuid());
            if (mutationState != null) {
                bowMutationLevel = mutationState.getBowLevel();
            }
        }

        int chargePercent = BowPerfectShotDefinitions.toPercent(normalizedCharge);
        boolean perfectShot = BowPerfectShotDefinitions.isPerfectShotSeconds(chargeSeconds, bowMutationLevel);
        long shotSequence = SHOT_SEQUENCE.incrementAndGet();

        LOGGER.atInfo().log("[BowPerfectShot] Projectile firstRun. interactionType=" + interactionType
                + " configId=" + this.config
                + " chargeSeconds=" + chargeSeconds
                + " maxChargeSeconds=" + maxChargeSeconds
                + " normalizedCharge=" + normalizedCharge
                + " chargePercent=" + chargePercent
                + " bowMutationLevel=" + bowMutationLevel
                + " perfectWindow=" + BowPerfectShotDefinitions.perfectWindowLabel(bowMutationLevel)
                + " perfectShot=" + perfectShot
                + " trackerHeld=" + trackerHeld
                + " shotSequence=" + shotSequence
                + " hasClientAimData=" + hasClientAimData);

        PendingPerfectShotComponent pending = new PendingPerfectShotComponent(
                perfectShot,
                chargeSeconds,
                normalizedCharge,
                maxChargeSeconds,
                shotSequence,
                this.config
        );

        if (PendingPerfectShotComponent.getComponentType() != null) {
            PendingPerfectShotComponent existingPending = commandBuffer.getComponent(
                    shooterRef,
                    PendingPerfectShotComponent.getComponentType()
            );

            if (existingPending == null) {
                commandBuffer.addComponent(
                        shooterRef,
                        PendingPerfectShotComponent.getComponentType(),
                        pending
                );
            } else {
                commandBuffer.putComponent(
                        shooterRef,
                        PendingPerfectShotComponent.getComponentType(),
                        pending
                );
            }

            LOGGER.atInfo().log("[BowPerfectShot] Pending shot state stored on attacker. attackerRef=" + shooterRef
                    + " shotSequence=" + shotSequence
                    + " configId=" + this.config
                    + " perfectShot=" + perfectShot
                    + " chargePercent=" + chargePercent);
        } else {
            LOGGER.atWarning().log("[BowPerfectShot] PendingPerfectShotComponent type was null during projectile spawn.");
        }

        BowPerfectShotHudController.release(shooterRef, commandBuffer);

        Ref<EntityStore> projectileRef = ProjectileModule.get().spawnProjectile(
                generatedUuid,
                shooterRef,
                commandBuffer,
                projectileConfig,
                spawnPosition,
                direction
        );

        if (projectileRef == null) {
            LOGGER.atWarning().log("[BowPerfectShot] Projectile spawn returned null. configId=" + this.config
                    + " chargeSeconds=" + chargeSeconds
                    + " chargePercent=" + chargePercent);
            return;
        }

        if (PerfectShotMarkerComponent.getComponentType() != null) {
            commandBuffer.addComponent(
                    projectileRef,
                    PerfectShotMarkerComponent.getComponentType(),
                    new PerfectShotMarkerComponent(
                            perfectShot,
                            chargeSeconds,
                            normalizedCharge,
                            maxChargeSeconds
                    )
            );

            LOGGER.atInfo().log("[BowPerfectShot] Projectile marker added. projectileRef=" + projectileRef
                    + " configId=" + this.config
                    + " perfectShot=" + perfectShot
                    + " chargeSeconds=" + chargeSeconds
                    + " maxChargeSeconds=" + maxChargeSeconds
                    + " chargePercent=" + chargePercent);
        }

        if (BowPerfectShotChargeTrackerComponent.getComponentType() != null) {
            commandBuffer.tryRemoveComponent(
                    shooterRef,
                    BowPerfectShotChargeTrackerComponent.getComponentType()
            );
        }

        if (perfectShot) {
            playPerfectShotLocalSound(shooterRef, commandBuffer);

            LOGGER.atInfo().log("[BowPerfectShot] PERFECT SHOT confirmed at projectile spawn. projectileRef=" + projectileRef
                    + " configId=" + this.config
                    + " shotSequence=" + shotSequence
                    + " bowMutationLevel=" + bowMutationLevel
                    + " multiplier=" + BowPerfectShotDefinitions.perfectShotDamageMultiplier(bowMutationLevel));
        } else {
            LOGGER.atInfo().log("[BowPerfectShot] Release was NOT a perfect shot. projectileRef=" + projectileRef
                    + " configId=" + this.config
                    + " shotSequence=" + shotSequence
                    + " bowMutationLevel=" + bowMutationLevel
                    + " perfectWindow=" + BowPerfectShotDefinitions.perfectWindowLabel(bowMutationLevel));
        }
    }

    private static void playPerfectShotLocalSound(
            Ref<EntityStore> shooterRef,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        if (shooterRef == null || commandBuffer == null) {
            return;
        }

        if (PlayerRef.getComponentType() == null) {
            LOGGER.atWarning().log("[BowPerfectShot] Could not play perfect shot sound because PlayerRef component type was null.");
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(
                shooterRef,
                PlayerRef.getComponentType()
        );

        if (playerRef == null || !playerRef.isValid()) {
            LOGGER.atWarning().log("[BowPerfectShot] Could not play perfect shot sound because shooter PlayerRef was null/invalid.");
            return;
        }

        if (SoundEvent.getAssetMap() == null) {
            LOGGER.atWarning().log("[BowPerfectShot] Could not play perfect shot sound because SoundEvent asset map was null.");
            return;
        }

        int soundEventIndex = SoundEvent.getAssetMap().getIndexOrDefault(
                PERFECT_SHOT_LOCAL_SOUND_EVENT_ID,
                -1
        );

        if (soundEventIndex < 0) {
            LOGGER.atWarning().log("[BowPerfectShot] Could not resolve perfect shot sound event id: "
                    + PERFECT_SHOT_LOCAL_SOUND_EVENT_ID);
            return;
        }

        SoundUtil.playSoundEvent2dToPlayer(
                playerRef,
                soundEventIndex,
                SoundCategory.SFX
        );

        LOGGER.atInfo().log("[BowPerfectShot] Perfect shot local sound played. player=" + playerRef.getUsername()
                + " soundEventId=" + PERFECT_SHOT_LOCAL_SOUND_EVENT_ID
                + " soundEventIndex=" + soundEventIndex);
    }
}