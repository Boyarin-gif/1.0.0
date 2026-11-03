package dev.pvpplus.client;

import dev.pvpplus.client.WaypointManager.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

/** Рисует метки на экране: проекция точки мира в 2D по углам камеры и FOV. */
public final class WaypointHud {
    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!Config.on("waypoints")) return;
        java.util.List<Waypoint> visibleWaypoints = WaypointManager.all();
        if (visibleWaypoints.isEmpty()) return;

        String dim = mc.world.getRegistryKey().getValue().toString();
        float tp = tick.getTickProgress(false);
        Vec3d eye = mc.player.getLerpedPos(tp).add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);

        Camera cam = mc.gameRenderer.getCamera();
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        Vec3d fwd = new Vec3d(-sy * cp, -sp, cy * cp);
        Vec3d right = new Vec3d(-cy, 0, -sy);
        Vec3d up = new Vec3d(-sy * sp, cp, cy * sp);

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double t = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) w / (double) h;
        TextRenderer tr = mc.textRenderer;

        for (Waypoint wp : visibleWaypoints) {
            if (!dim.equals(wp.dim)) continue;
            if (wp.entityUuid != null && !wp.entityUuid.isBlank()) {
                Entity attached = findEntity(mc, wp.entityUuid);
                if (attached != null && !attached.isRemoved()) {
                    wp.x = attached.getX(); wp.y = attached.getY(); wp.z = attached.getZ();
                }
            }
            Vec3d d = new Vec3d(wp.x + 0.5, wp.y + 0.5, wp.z + 0.5).subtract(eye);
            double dist = d.length();
            double cx = d.dotProduct(right);
            double cyv = d.dotProduct(up);
            double cz = d.dotProduct(fwd);

            double nx, ny;
            if (cz > 0.05) {
                nx = cx / (cz * t * aspect);
                ny = cyv / (cz * t);
            } else { // позади - прижимаем к краю экрана
                nx = cx >= 0 ? 10 : -10;
                ny = 0;
            }
            nx = Math.max(-0.95, Math.min(0.95, nx));
            ny = Math.max(-0.92, Math.min(0.92, ny));

            int sx = (int) ((nx * 0.5 + 0.5) * w);
            int sy2 = (int) ((0.5 - ny * 0.5) * h);

            drawIcon(ctx, wp.icon, sx, sy2);
            ctx.drawCenteredTextWithShadow(tr, wp.name, sx, sy2 - 15, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, Math.round(dist) + " м", sx, sy2 + 8, 0xFFB0B0B0);
        }
    }
    private static Entity findEntity(MinecraftClient mc, String uuid) {
        if (mc.world == null) return null;
        for (Entity e : mc.world.getEntities()) if (uuid.equals(e.getUuidAsString())) return e;
        return null;
    }

    /** Marker glyph rendered from the same PNG atlas used by the menu. */
    private static void drawIcon(DrawContext ctx, String icon, int x, int y) {
        Ui.marker(ctx, icon, x - 8, y - 8, 16);
    }

}
