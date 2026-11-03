package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Свои сердца. Красные всегда; при яде/иссушении правая половина каждого целого сердца
 * перекрашивается (зелёная/чёрная), так что количество ХП читается по красным левым половинкам.
 */
public final class HeartsHud {
    private static final Identifier CONTAINER = Identifier.ofVanilla("hud/heart/container");
    private static final Identifier FULL = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier HALF = Identifier.ofVanilla("hud/heart/half");
    private static final Identifier ABS_FULL = Identifier.ofVanilla("hud/heart/absorbing_full");
    private static final Identifier ABS_HALF = Identifier.ofVanilla("hud/heart/absorbing_half");
    private static final Identifier POISON_FULL = Identifier.ofVanilla("hud/heart/poisoned_full");
    private static final Identifier WITHER_FULL = Identifier.ofVanilla("hud/heart/withered_full");

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || !mc.interactionManager.hasStatusBars()) return;

        int maxHp = MathHelper.ceil(p.getMaxHealth());
        int hp = Math.max(0, MathHelper.ceil(p.getHealth()));
        int abs = MathHelper.ceil(p.getAbsorptionAmount());
        int healthHearts = MathHelper.ceil(maxHp / 2.0);
        int absHearts = MathHelper.ceil(abs / 2.0);
        int total = healthHearts + absHearts;

        int rows = MathHelper.ceil((maxHp + abs) / 2.0F / 10.0F);
        int rowH = Math.max(10 - (rows - 2), 3);

        int left = mc.getWindow().getScaledWidth() / 2 - 91;
        int top = mc.getWindow().getScaledHeight() - 39;

        boolean wither = p.hasStatusEffect(StatusEffects.WITHER);
        boolean poison = p.hasStatusEffect(StatusEffects.POISON);

        for (int i = total - 1; i >= 0; i--) {
            int x = left + (i % 10) * 8;
            int y = top - (i / 10) * rowH;
            sprite(ctx, CONTAINER, x, y);

            if (i < healthHearts) {
                int base = i * 2;
                boolean full = hp >= base + 2;
                boolean half = hp == base + 1;
                if (full) {
                    sprite(ctx, FULL, x, y);
                    if (wither || poison) {
                        ctx.enableScissor(x + 5, y, x + 9, y + 9);
                        sprite(ctx, wither ? WITHER_FULL : POISON_FULL, x, y);
                        ctx.disableScissor();
                    }
                } else if (half) {
                    sprite(ctx, HALF, x, y);
                }
            } else {
                int base = (i - healthHearts) * 2;
                if (abs >= base + 2) sprite(ctx, ABS_FULL, x, y);
                else if (abs == base + 1) sprite(ctx, ABS_HALF, x, y);
            }
        }
    }

    private static void sprite(DrawContext ctx, Identifier id, int x, int y) {
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 9, 9);
    }
}
