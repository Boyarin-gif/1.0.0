package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

public final class Overlay {
    private static final int[] OUT = new int[2];

    /** Свой прицел (заменяет ванильный, если включён). */
    public static void crosshair(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int s = Config.i("crosshair.size"), g = Config.i("crosshair.gap"), t = Config.i("crosshair.thick");
        int color = Config.color("crosshair.color");
        int half = t / 2;

        Gfx.rectStyled(ctx, cx - g - s, cy - half, s, t, color, "crosshair");
        Gfx.rectStyled(ctx, cx + 1 + g, cy - half, s, t, color, "crosshair");
        Gfx.rectStyled(ctx, cx - half, cy - g - s, t, s, color, "crosshair");
        Gfx.rectStyled(ctx, cx - half, cy + 1 + g, t, s, color, "crosshair");
        if (Config.b("crosshair.dot")) Gfx.pointStyled(ctx, cx, cy, color, 1, "crosshair");
    }

    /** Хитмаркер, индикатор направления удара и числа урона. */
    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int cx = w / 2, cy = h / 2;
        int now = CombatTracker.tickCount;

        if (Config.on("hitmarker")) {
            int age = now - CombatTracker.hitmarkerTick;
            if (age >= 0 && age <= 6) {
                int col = CombatTracker.hitmarkerKill ? 0xFFFF4040 : Config.color("hitmarker.color");
                int len = Config.i("hitmarker.size");
                Gfx.lineStyled(ctx, cx + 3, cy + 3, cx + 2 + len, cy + 2 + len, col, 1, "hitmarker");
                Gfx.lineStyled(ctx, cx - 3, cy + 3, cx - 2 - len, cy + 2 + len, col, 1, "hitmarker");
                Gfx.lineStyled(ctx, cx + 3, cy - 3, cx + 2 + len, cy - 2 - len, col, 1, "hitmarker");
                Gfx.lineStyled(ctx, cx - 3, cy - 3, cx - 2 - len, cy - 2 - len, col, 1, "hitmarker");
            }
        }

        if (Config.on("hitdir")) {
            int age = now - CombatTracker.hitDirTick;
            if (age >= 0 && age <= 30) {
                double yaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
                double dx = CombatTracker.hitDx, dz = CombatTracker.hitDz;
                double rx = dx * (-Math.cos(yaw)) + dz * (-Math.sin(yaw));
                double fz = dx * (-Math.sin(yaw)) + dz * Math.cos(yaw);
                double angle = Math.atan2(rx, fz);
                int alpha = Math.max(30, (int) (255 * (1.0 - age / 30.0)));
                int col = Gfx.alpha(Config.color("hitdir.color"), alpha);
                for (int r = 34; r <= 46; r += 4) {
                    int sx = cx + (int) Math.round(Math.sin(angle) * r);
                    int sy = cy - (int) Math.round(Math.cos(angle) * r);
                    Gfx.pointStyled(ctx, sx, sy, col, 2, "hitdir");
                }
            }
        }

        if (Config.on("damage") && !CombatTracker.numbers.isEmpty()) {
            Projection.Frame f = Projection.begin(mc, tick.getTickProgress(false));
            TextRenderer tr = mc.textRenderer;
            for (int i = 0; i < CombatTracker.numbers.size(); i++) {
                CombatTracker.DamageNumber n = CombatTracker.numbers.get(i);
                int age = now - n.tick();
                Vec3d pos = n.pos();
                if (!f.project(pos.x, pos.y + age * 0.03, pos.z, OUT)) continue;
                int alpha = age < 15 ? 255 : Math.max(30, (int) (255 * (1.0 - (age - 15) / 10.0)));
                Gfx.centeredTextStyled(ctx, tr, n.text(), OUT[0], OUT[1], Gfx.alpha(Config.color("damage.color"), alpha), "damage");
            }
        }
    }
}
