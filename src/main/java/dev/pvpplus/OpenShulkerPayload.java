package dev.pvpplus;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenShulkerPayload(int slotId) implements CustomPayload {
    public static final CustomPayload.Id<OpenShulkerPayload> ID =
            new CustomPayload.Id<>(Identifier.of(PvpPlus.MOD_ID, "open_shulker"));

    public static final PacketCodec<RegistryByteBuf, OpenShulkerPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, OpenShulkerPayload::slotId, OpenShulkerPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
