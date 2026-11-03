package dev.pvpplus.mixin;

import dev.pvpplus.client.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item Glow: предмет в основной/второй руке выглядит зачарованным (ванильное свечение, точно по форме предмета). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true, require = 0)
    private void pvpplus$heldGlint(CallbackInfoReturnable<Boolean> cir) {
        if (!Config.on("itemglow")) return;
        PlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        ItemStack self = (ItemStack) (Object) this;
        if (self.isEmpty()) return;
        if (self == p.getMainHandStack()) {
            if (Config.b("itemglow.main")) cir.setReturnValue(true);
        } else if (self == p.getOffHandStack()) {
            if (Config.b("itemglow.off")) cir.setReturnValue(true);
        }
    }
}
