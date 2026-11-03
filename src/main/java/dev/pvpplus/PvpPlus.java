package dev.pvpplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class PvpPlus implements ModInitializer {
    public static final String MOD_ID = "pvpplus";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(OpenShulkerPayload.ID, OpenShulkerPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenShulkerPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> ShulkerMenu.tryOpen(player, payload.slotId()));
        });
    }
}
