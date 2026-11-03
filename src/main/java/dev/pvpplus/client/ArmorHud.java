package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Маленькая панель с надетой бронёй и прочностью (цифры и полоска). */
public final class ArmorHud {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("armor")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || p.isSpectator()) return;

        List<ItemStack> worn = new ArrayList<>();
        for (EquipmentSlot s : SLOTS) {
            ItemStack st = p.getEquippedStack(s);
            if (!st.isEmpty()) worn.add(st);
        }
        if (worn.isEmpty()) return;

        int rowH = 18;
        int panelW = 70;
        int panelH = worn.size() * rowH + 4;
        int[] pos = HudPanels.place("armor", panelW, panelH);
        int x0 = pos[0], y0 = pos[1];

        Gfx.panel(ctx, x0, y0, panelW, panelH, 178);

        for (int i = 0; i < worn.size(); i++) {
            ItemStack st = worn.get(i);
            int y = y0 + 2 + i * rowH;
            ctx.drawItem(st, x0 + 3, y + 1);

            if (st.isDamageable()) {
                int max = st.getMaxDamage();
                int leftDur = Math.max(0, max - st.getDamage());
                float frac = max <= 0 ? 0f : (float) leftDur / (float) max;
                int color = frac < 0.1f ? 0xFFFF5555 : 0xFFFFFFFF;
                ctx.drawText(mc.textRenderer, leftDur + "/" + max, x0 + 22, y + 2, color, true);

                int barX = x0 + 22;
                int barY = y + 12;
                int barW = 44;
                ctx.fill(barX, barY, barX + barW, barY + 2, 0xFF202020);
                ctx.fill(barX, barY, barX + Math.round(barW * frac), barY + 2, st.getItemBarColor() | 0xFF000000);
            }
        }
    }
}
