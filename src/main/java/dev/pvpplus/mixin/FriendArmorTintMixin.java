package dev.pvpplus.mixin;

import dev.pvpplus.client.FriendManager;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Tints the real vanilla equipment model submission for selected friends.
 *
 * Minecraft 1.21.11 changed the render command argument pipeline. ModifyArgs
 * is used here because the tint argument belongs to submitModel(), while the
 * render-state and equipment stack belong to the surrounding EquipmentRenderer
 * method. Args exposes the actual submitModel arguments and the remaining
 * parameters provide the context required to identify the friend and armor slot.
 */
@Mixin(EquipmentRenderer.class)
public abstract class FriendArmorTintMixin {

    @ModifyArgs(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;"
                    + "Lnet/minecraft/registry/RegistryKey;"
                    + "Lnet/minecraft/client/model/Model;"
                    + "Ljava/lang/Object;"
                    + "Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "ILnet/minecraft/util/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderCommandQueue;"
                            + "submitModel(Lnet/minecraft/client/model/Model;"
                            + "Ljava/lang/Object;"
                            + "Lnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/client/render/RenderLayer;"
                            + "IIILnet/minecraft/client/texture/Sprite;"
                            + "ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
            )
    )
    private void pvpplus$tintFriendArmor(
            Args args,
            EquipmentModel.LayerType layerType,
            RegistryKey<?> assetKey,
            Model<?> model,
            Object renderState,
            ItemStack stack,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            Identifier textureId,
            int outlineColor,
            int initialOrder
    ) {
        if (!(renderState instanceof BipedEntityRenderState bipedState)) {
            return;
        }

        int tint = FriendManager.armorTintFor(bipedState, stack);
        if (tint != 0) {
            // submitModel arguments:
            // 0=model, 1=state, 2=matrices, 3=layer,
            // 4=light, 5=overlay, 6=tintedColor, 7=sprite,
            // 8=outlineColor, 9=crumblingOverlay.
            args.set(6, tint);
        }
    }
}
