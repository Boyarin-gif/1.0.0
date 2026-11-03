package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Перевод точки мира в координаты экрана (по углам камеры и FOV). Объект кадра переиспользуется. */
public final class Projection {
    public static final class Frame {
        double ex, ey, ez;
        double fx, fy, fz;
        double rx, ry, rz;
        double ux, uy, uz;
        double t, aspect;
        int w, h;

        /** Пишет {x, y} в out. Возвращает false, если точка позади камеры. */
        public boolean project(double x, double y, double z, int[] out) {
            double dx = x - ex, dy = y - ey, dz = z - ez;
            double cz = dx * fx + dy * fy + dz * fz;
            if (cz <= 0.05) return false;
            double cx = dx * rx + dy * ry + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;
            out[0] = (int) ((cx / (cz * t * aspect) * 0.5 + 0.5) * w);
            out[1] = (int) ((0.5 - cy / (cz * t) * 0.5) * h);
            return true;
        }

        public boolean project(Vec3d p, int[] out) {
            return project(p.x, p.y, p.z, out);
        }
    }

    private static final Frame FRAME = new Frame();

    public static Frame begin(MinecraftClient mc, float tp) {
        Frame f = FRAME;
        Camera cam = mc.gameRenderer.getCamera();
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        Vec3d e = mc.player.getLerpedPos(tp);
        f.ex = e.x;
        f.ey = e.y + mc.player.getEyeHeight(mc.player.getPose());
        f.ez = e.z;
        f.fx = -sy * cp;
        f.fy = -sp;
        f.fz = cy * cp;
        f.rx = -cy;
        f.ry = 0;
        f.rz = -sy;
        f.ux = -sy * sp;
        f.uy = cp;
        f.uz = cy * sp;
        f.w = mc.getWindow().getScaledWidth();
        f.h = mc.getWindow().getScaledHeight();
        f.t = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        f.aspect = (double) f.w / (double) f.h;
        return f;
    }
}
