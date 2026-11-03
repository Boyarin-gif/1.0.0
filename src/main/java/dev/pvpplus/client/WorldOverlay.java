package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Рисуется поверх экрана через проекцию точек мира (без настоящей 3D-отрисовки, поэтому видно и сквозь блоки). */
public final class WorldOverlay {
    private static final int[] A = new int[2];
    private static final int[] B = new int[2];
    private static final double[][] COS = new double[4][];
    private static final double[][] SIN = new double[4][];

    static {
        for (int l = 0; l < 4; l++) {
            int seg = Optimization.ringSegments(l);
            COS[l] = new double[seg + 1];
            SIN[l] = new double[seg + 1];
            for (int i = 0; i <= seg; i++) {
                double a = 2 * Math.PI * i / seg;
                COS[l][i] = Math.cos(a);
                SIN[l][i] = Math.sin(a);
            }
        }
    }

    /** Hit-range ring is submitted as native 3D world lines once per tick, not thousands of HUD pixels per frame. */
    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.worldRenderer == null || !Config.on("hitrange")) return;
        LivingEntity target = CombatTracker.currentTarget(mc);
        if (target == null || !target.isAlive()) return;

        double radius = Math.max(0.1, Config.num("hitrange.radius"));
        double cx = target.getX();
        double cy = target.getY() + 0.05;
        double cz = target.getZ();
        int color = Config.color("hitrange.color");
        int segments = Optimization.ringSegments();

        try (GizmoDrawing.CollectorScope ignored = mc.worldRenderer.startDrawingGizmos()) {
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2.0 * i / segments;
                double a1 = Math.PI * 2.0 * (i + 1) / segments;
                Vec3d from = new Vec3d(cx + Math.cos(a0) * radius, cy, cz + Math.sin(a0) * radius);
                Vec3d to = new Vec3d(cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius);
                if (Config.b("hitrange.shadow")) {
                    Vec3d shadowFrom = from.add(0, -0.025, 0);
                    Vec3d shadowTo = to.add(0, -0.025, 0);
                    GizmoDrawing.line(shadowFrom, shadowTo, 0x99000000, 2.5F)
                            .ignoreOcclusion().withLifespan(55);
                }
                if (Config.b("hitrange.glow")) {
                    int alpha = 18 + Math.max(1, Math.min(4, Config.i("hitrange.glowPower"))) * 9;
                    GizmoDrawing.line(from, to, Gfx.alpha(color, alpha), 3.5F)
                            .ignoreOcclusion().withLifespan(55);
                }
                GizmoDrawing.line(from, to, color, 1.5F)
                        .ignoreOcclusion()
                        .withLifespan(55);
            }
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        boolean bubble = Config.on("hitbubble"), jump = Config.on("jumpcircles");
        boolean arrows = Config.on("arrownametag");
        boolean predict = Config.on("prediction");
        boolean colored = !Effects.coloredParticles.isEmpty();
        boolean jumpDots = jump && !Effects.jumpParticles.isEmpty();
        if (!(bubble || jumpDots || arrows || predict || colored)) return;

        Projection.Frame f = Projection.begin(mc, tick.getTickProgress(false));
        int now = CombatTracker.tickCount;
        int lvl = Optimization.level();
        int stride = Optimization.lineStride();
        boolean simple = Optimization.simple();

        if (jumpDots) {
            int jumpColor = Config.color("jumpcircles.color");
            int life = Math.max(1, Config.i("jumpcircles.life"));
            for (int i = 0; i < Effects.jumpParticles.size(); i++) {
                Effects.JumpParticle dot = Effects.jumpParticles.get(i);
                double age = Math.max(0.0, (now - dot.tick()) / (double) life);
                if (age >= 1.0) continue;
                if (!f.project(dot.pos(), A)) continue;
                int alpha = (int) (255 * (1.0 - age));
                int color = Gfx.alpha(jumpColor, alpha);
                int size = Math.max(1, Math.min(4, (int) Math.round(dot.scale())));
                Gfx.pointStyled(ctx, A[0], A[1], color, size, "jumpcircles");
            }
        }

        if (bubble && !Effects.bubbles.isEmpty()) {
            int life = Math.max(1, Config.i("hitbubble.life"));
            int base = Config.color("hitbubble.color");
            double maxR = Config.num("hitbubble.radius");
            for (int i = 0; i < Effects.bubbles.size(); i++) {
                Effects.Ring r = Effects.bubbles.get(i);
                double t = Math.min(1.0, (now - r.tick()) / (double) life);
                int col = simple ? Gfx.alpha(base, 200) : Gfx.alpha(base, (int) (230 * (1 - t)));
                ring(ctx, f, r.pos().x, r.pos().y, r.pos().z, 0.3 + (maxR - 0.3) * t, col, lvl, stride, "hitbubble");
            }
        }


        if (arrows) {
            double range = Config.num("arrownametag.range");
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof PersistentProjectileEntity ar)) continue;
                if (ar.getVelocity().lengthSquared() < 0.01) continue;
                double d = p.distanceTo(ar);
                if (d > range || d < 2) continue;
                if (!f.project(ar.getX(), ar.getY() + 0.3, ar.getZ(), A)) continue;
                Entity owner = ar.getOwner();
                String text = (owner != null ? owner.getName().getString() + " " : "") + Math.round(d) + " м";
                int arrowColor = Config.color("arrownametag.color");
                Gfx.centeredTextStyled(ctx, mc.textRenderer, text, A[0], A[1] - 10, arrowColor, "arrownametag");
                Gfx.pointStyled(ctx, A[0], A[1], arrowColor, 1, "arrownametag");
            }
        }

        if (predict) predict(ctx, f, mc, p, stride);

        for (int i = 0; i < Effects.coloredParticles.size(); i++) {
            Effects.ColoredParticle particle = Effects.coloredParticles.get(i);
            if (!Config.on(particle.effectId)) continue;
            int age = now - particle.bornTick;
            if (age < 0 || age >= particle.lifetime || !f.project(particle.pos(), A)) continue;
            int alpha = Math.max(0, Math.min(255, (int) (255.0 * (1.0 - age / (double) particle.lifetime))));
            int particleColor = Gfx.alpha(Config.color(particle.effectId + ".color"), alpha);
            Gfx.pointStyled(ctx, A[0], A[1], particleColor, particle.size, particle.effectId);
        }
    }

    private static void ring(DrawContext ctx, Projection.Frame f, double cx, double cy, double cz, double r, int color, int lvl, int stride, String effectId) {
        double[] cs = COS[lvl], sn = SIN[lvl];
        boolean have = false;
        for (int i = 0; i < cs.length; i++) {
            boolean ok = f.project(cx + cs[i] * r, cy, cz + sn[i] * r, B);
            if (ok && have) Gfx.lineStyled(ctx, A[0], A[1], B[0], B[1], color, stride, effectId);
            if (ok) {
                A[0] = B[0];
                A[1] = B[1];
            }
            have = ok;
        }
    }

    private static void predict(DrawContext ctx, Projection.Frame f, MinecraftClient mc, PlayerEntity p, int stride) {
        ItemStack st = p.getMainHandStack();
        double speed, grav;
        if (st.isOf(Items.BOW) && p.isUsingItem() && p.getActiveItem().isOf(Items.BOW)) {
            float t = p.getItemUseTime() / 20.0f;
            float power = Math.min(1.0f, (t * t + t * 2.0f) / 3.0f);
            if (power < 0.1f) return;
            speed = power * 3.0;
            grav = 0.05;
        } else if (st.isOf(Items.ENDER_PEARL) || st.isOf(Items.SNOWBALL) || st.isOf(Items.EGG)) {
            speed = 1.5;
            grav = 0.03;
        } else {
            return;
        }

        int color = Config.color("prediction.color");
        Vec3d pos = new Vec3d(f.ex, f.ey - 0.1, f.ez);
        Vec3d vel = new Vec3d(f.fx * speed, f.fy * speed, f.fz * speed);
        boolean have = f.project(pos, A);
        int steps = Optimization.predictSteps();
        for (int i = 0; i < steps; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            boolean stop = hit.getType() != HitResult.Type.MISS;
            Vec3d end = stop ? hit.getPos() : next;
            boolean ok = f.project(end, B);
            if (have && ok) Gfx.lineStyled(ctx, A[0], A[1], B[0], B[1], color, stride, "prediction");
            if (ok) {
                A[0] = B[0];
                A[1] = B[1];
            }
            have = ok;
            if (stop) {
                if (ok) ctx.fill(B[0] - 2, B[1] - 2, B[0] + 3, B[1] + 3, color);
                break;
            }
            pos = next;
            vel = vel.multiply(0.99).add(0, -grav, 0);
        }
    }
}
