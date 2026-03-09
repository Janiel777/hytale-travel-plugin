package com.janiel.hytale.mutations.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.component.BowPerfectShotChargeTrackerComponent;
import com.janiel.hytale.mutations.weapon.effects.BowPerfectShotDefinitions;
import com.janiel.hytale.mutations.ui.hud.BowPerfectShotHudController;

public final class PerfectShotChargingInteraction extends ChargingInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final BuilderCodec<PerfectShotChargingInteraction> CODEC =
            BuilderCodec.builder(
                            PerfectShotChargingInteraction.class,
                            PerfectShotChargingInteraction::new,
                            ChargingInteraction.CODEC
                    )
                    .documentation("Charging interaction with debug logs for bow perfect shots.")
                    .build();

    public PerfectShotChargingInteraction() {
        super();
    }

    @Override
    protected void tick0(
            boolean held,
            float dt,
            InteractionType interactionType,
            InteractionContext context,
            CooldownHandler cooldownHandler
    ) {
        super.tick0(held, dt, interactionType, context, cooldownHandler);

        if (context == null) {
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> shooterRef = context.getEntity();

        if (commandBuffer == null || shooterRef == null) {
            LOGGER.atWarning().log("[BowPerfectShot] Charging tick skipped because commandBuffer/entity was null. interactionType="
                    + interactionType);
            return;
        }

        if (BowPerfectShotChargeTrackerComponent.getComponentType() == null) {
            LOGGER.atWarning().log("[BowPerfectShot] Charging tick skipped because BowPerfectShotChargeTrackerComponent type was null.");
            return;
        }

        float actualMaxChargeSeconds = BowPerfectShotDefinitions.resolveMaxChargeSeconds(this.highestChargeValue);
        float trackedChargeSeconds = Math.min(
                BowPerfectShotDefinitions.clampNonNegative(dt),
                BowPerfectShotDefinitions.visualTimelineSeconds()
        );
        float normalizedCharge = BowPerfectShotDefinitions.normalizeVisualChargeSeconds(trackedChargeSeconds);
        int chargePercent = BowPerfectShotDefinitions.toPercent(normalizedCharge);
        boolean inPerfectWindow = BowPerfectShotDefinitions.isPerfectShotSeconds(trackedChargeSeconds);

        BowPerfectShotChargeTrackerComponent tracker = new BowPerfectShotChargeTrackerComponent(
                trackedChargeSeconds,
                normalizedCharge,
                actualMaxChargeSeconds,
                held
        );

        BowPerfectShotChargeTrackerComponent existing = commandBuffer.getComponent(
                shooterRef,
                BowPerfectShotChargeTrackerComponent.getComponentType()
        );

        if (existing == null) {
            commandBuffer.addComponent(
                    shooterRef,
                    BowPerfectShotChargeTrackerComponent.getComponentType(),
                    tracker
            );
        } else {
            commandBuffer.putComponent(
                    shooterRef,
                    BowPerfectShotChargeTrackerComponent.getComponentType(),
                    tracker
            );
        }

        BowPerfectShotHudController.updateCharge(
                shooterRef,
                commandBuffer,
                normalizedCharge,
                actualMaxChargeSeconds
        );

        LOGGER.atInfo().log("[BowPerfectShot] Charging tick. interactionType=" + interactionType
                + " held=" + held
                + " dt=" + dt
                + " trackedChargeSeconds=" + trackedChargeSeconds
                + " actualMaxChargeSeconds=" + actualMaxChargeSeconds
                + " visualTimelineSeconds=" + BowPerfectShotDefinitions.visualTimelineSeconds()
                + " normalizedCharge=" + normalizedCharge
                + " chargePercent=" + chargePercent
                + " perfectWindow=" + BowPerfectShotDefinitions.perfectWindowLabel()
                + " inPerfectWindow=" + inPerfectWindow
                + " highestChargeValue=" + this.highestChargeValue);
    }
}