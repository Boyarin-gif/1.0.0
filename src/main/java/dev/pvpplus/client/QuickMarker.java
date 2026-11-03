package dev.pvpplus.client;

import java.util.List;

import dev.pvpplus.client.WaypointManager.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/** Быстрая метка: по клавише (по умолчанию средняя кнопка мыши) ставит метку в точку взгляда. */
public final class QuickMarker {
    public static void tick(MinecraftClient mc, KeyBinding key) {
        while (key.wasPressed()) {
            if (mc.currentScreen != null || mc.player == null || mc.world == null) continue;
            if (!Config.on("quickmarker")) continue;
            place(mc);
        }
    }

    private static void place(MinecraftClient mc) {
        if (!CoOpManager.isConnected()) {
            Notifier.push("Для общей метки сначала подключись к комнате");
            return;
        }
        double range = Config.num("quickmarker.range");
        HitResult hr = mc.crosshairTarget;
        LivingEntity livingTarget = hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le != mc.player ? le : null;
        if (livingTarget != null && mc.player.distanceTo(livingTarget) > range) livingTarget = null;
        BlockPos bp = null;
        if (livingTarget == null) {
            hr = mc.player.raycast(range, 1.0f, false);
            if (hr.getType() != HitResult.Type.BLOCK) {
                Notifier.push("Метка: наведи на блок или существо в " + (int) range + " блоках");
                return;
            }
            bp = ((BlockHitResult) hr).getBlockPos();
        }
        String dim = mc.world.getRegistryKey().getValue().toString();
        List<Waypoint> list = WaypointManager.cooperative();
        double tx = livingTarget != null ? livingTarget.getX() : bp.getX();
        double ty = livingTarget != null ? livingTarget.getY() : bp.getY();
        double tz = livingTarget != null ? livingTarget.getZ() : bp.getZ();

        // Re-clicking the same quick marker removes it; otherwise replace the previous quick marker.
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = list.get(i);
            if (!w.quick || !dim.equals(w.dim)) continue;
            if (livingTarget != null && livingTarget.getUuidAsString().equals(w.entityUuid)) {
                CoOpManager.removeSharedMarker(w); WaypointManager.removeCooperative(w); WaypointManager.save(); Notifier.push("Метка удалена"); return;
            }
            double dx = w.x - tx, dy = w.y - ty, dz = w.z - tz;
            if (livingTarget == null && w.entityUuid == null && dx * dx + dy * dy + dz * dz < 9.0) {
                CoOpManager.removeSharedMarker(w); WaypointManager.removeCooperative(w); WaypointManager.save(); Notifier.push("Метка удалена"); return;
            }
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            Waypoint old = list.get(i);
            if (old.quick) { CoOpManager.removeSharedMarker(old); WaypointManager.removeCooperative(old); }
        }
        Waypoint wp = new Waypoint();
        wp.name = livingTarget != null ? "Цель: " + livingTarget.getName().getString() : "Метка";
        wp.x = tx; wp.y = ty; wp.z = tz; wp.dim = dim; wp.quick = true;
        if (livingTarget != null) { wp.entityUuid = livingTarget.getUuidAsString(); wp.icon = "sword"; }
        else wp.icon = "pin";
        WaypointManager.addCooperative(wp); CoOpManager.shareMarker(wp); WaypointManager.save();
        Notifier.push(livingTarget != null ? "Метка закреплена за " + livingTarget.getName().getString() : "Метка: " + bp.getX() + " " + bp.getY() + " " + bp.getZ());
    }
}
