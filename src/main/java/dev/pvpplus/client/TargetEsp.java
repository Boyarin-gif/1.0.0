package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** Лёгкая 3D-рамка хитбокса одной текущей цели через штатный gizmo-рендер Minecraft. */
public final class TargetEsp {
    private TargetEsp() {}

    public static void tick(MinecraftClient mc) {
        if (!Config.on("targetesp") || mc.player == null || mc.world == null || mc.worldRenderer == null) return;

        LivingEntity target = CombatTracker.currentTarget(mc);
        if (target == null || target == mc.player || target.isRemoved() || !target.isAlive()) return;

        // Используем реальный AABB цели без второй системы экранной проекции.
        Box box = target.getBoundingBox().expand(0.002);
        Vec3d eye = target.getEyePos();
        Vec3d lookEnd = eye.add(target.getRotationVec(1.0F).multiply(0.45));
        int color = Config.color("targetesp.color");

        try (GizmoDrawing.CollectorScope ignored = mc.worldRenderer.startDrawingGizmos()) {
            boolean shadow = Config.b("targetesp.shadow");
            boolean glow = Config.b("targetesp.glow");
            int glowPower = Math.max(1, Math.min(4, Config.i("targetesp.glowPower")));
            if (shadow) {
                GizmoDrawing.box(box.offset(0, -0.025, 0), DrawStyle.stroked(0x99000000, 2.0F), false)
                        .ignoreOcclusion().withLifespan(55);
            }
            if (glow) {
                for (int layer = glowPower; layer >= 1; layer--) {
                    Box glowBox = box.expand(0.008 * layer);
                    GizmoDrawing.box(glowBox, DrawStyle.stroked(Gfx.alpha(color, 18 + (glowPower - layer + 1) * 8), 1.25F + layer), false)
                            .ignoreOcclusion().withLifespan(55);
                }
            }
            GizmoDrawing.box(box, DrawStyle.stroked(color, 1.25F), false)
                    .ignoreOcclusion()
                    .withLifespan(55);
            // Target look line follows the same per-effect color and style.
            if (glow) GizmoDrawing.line(eye, lookEnd, Gfx.alpha(color, 40 + glowPower * 12), 2.5F)
                    .ignoreOcclusion().withLifespan(55);
            if (shadow) GizmoDrawing.line(eye.add(0, -0.02, 0), lookEnd.add(0, -0.02, 0), 0x99000000, 1.8F)
                    .ignoreOcclusion().withLifespan(55);
            GizmoDrawing.line(eye, lookEnd, color, 1.0F)
                    .ignoreOcclusion()
                    .withLifespan(55);
        }
    }
}
