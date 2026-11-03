package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

public final class Effects {
    public record Ring(Vec3d pos, int tick) {}
    public record JumpParticle(Vec3d pos, int tick, double scale) {}

    /** Small projected colored sparks that allow the user-selected palette to tint particle effects. */
    public static final class ColoredParticle {
        public double x, y, z, vx, vy, vz;
        public final int bornTick, lifetime, size;
        public final String effectId;
        public ColoredParticle(double x, double y, double z, double vx, double vy, double vz,
                               int bornTick, int lifetime, int size, String effectId) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.bornTick = bornTick; this.lifetime = lifetime; this.size = size; this.effectId = effectId;
        }
        public Vec3d pos() { return new Vec3d(x, y, z); }
        public void tick() {
            x += vx; y += vy; z += vz;
            vx *= 0.94; vy = vy * 0.94 - 0.004; vz *= 0.94;
        }
    }

    public static final List<Ring> jumpCircles = new ArrayList<>();
    public static final List<JumpParticle> jumpParticles = new ArrayList<>();
    public static final List<ColoredParticle> coloredParticles = new ArrayList<>();
    public static final List<Ring> bubbles = new ArrayList<>();
    private static boolean wasOnGround = true;
    private static int jumpStartTick = -1;
    private static boolean jumpAirborne;
    private static Vec3d jumpCircleCenter;
    private static Vec3d lastTrailPosition;
    private static final Random RNG = new Random();

    private static void coloredBurst(String effectId, double x, double y, double z, int amount, int lifetime, int size) {
        int now = CombatTracker.tickCount;
        int count = Math.max(0, Math.min(32, amount));
        for (int i = 0; i < count; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double speed = 0.018 + RNG.nextDouble() * 0.055;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.015 + RNG.nextDouble() * 0.06;
            coloredParticles.add(new ColoredParticle(
                    x + (RNG.nextDouble() - 0.5) * 0.22,
                    y + (RNG.nextDouble() - 0.5) * 0.22,
                    z + (RNG.nextDouble() - 0.5) * 0.22,
                    vx, vy, vz, now, Math.max(4, lifetime), Math.max(1, Math.min(3, size)), effectId));
        }
        // Hard cap protects low-end systems if many entities are hit in quick succession.
        while (coloredParticles.size() > 240) coloredParticles.remove(0);
    }

    public static boolean isCrit(PlayerEntity p) {
        return p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    private static ParticleEffect hitType(int i) {
        return switch (i) {
            case 1 -> ParticleTypes.DAMAGE_INDICATOR;
            case 2 -> ParticleTypes.HEART;
            case 3 -> ParticleTypes.FLAME;
            case 4 -> ParticleTypes.END_ROD;
            case 5 -> ParticleTypes.ENCHANTED_HIT;
            default -> ParticleTypes.CRIT;
        };
    }

    private static ParticleEffect killType(int i) {
        return switch (i) {
            case 1 -> ParticleTypes.SOUL;
            case 2 -> ParticleTypes.EXPLOSION;
            case 3 -> ParticleTypes.FIREWORK;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
    }

    /** Создаёт частицы с учётом уровня оптимизации и лимита на тик. */
    private static void burst(MinecraftClient mc, ParticleEffect type, double x, double y, double z, double spread, int count) {
        int n = Optimization.takeParticles(count);
        for (int i = 0; i < n; i++) {
            mc.particleManager.addParticle(type,
                    x + (RNG.nextDouble() - 0.5) * 0.6, y + RNG.nextDouble() * 0.6, z + (RNG.nextDouble() - 0.5) * 0.6,
                    (RNG.nextDouble() - 0.5) * spread, RNG.nextDouble() * spread, (RNG.nextDouble() - 0.5) * spread);
        }
    }

    public static void onHit(LivingEntity le, boolean crit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        double x = le.getX(), y = le.getY() + le.getHeight() * 0.6, z = le.getZ();
        if (Config.on("hitparticles")) {
            burst(mc, hitType(Config.i("hitparticles.type")), x, y, z, 0.3, Config.i("hitparticles.count"));
            coloredBurst("hitparticles", x, y, z, Math.max(2, Config.i("hitparticles.count") / 2), 12, 1);
        }
        if (crit && Config.on("crit")) {
            burst(mc, hitType(Config.i("crit.type")), x, y, z, 0.5, Config.i("crit.count"));
            coloredBurst("crit", x, y, z, Math.max(3, Config.i("crit.count") / 3), 15, 2);
        }
        if (Config.on("hitbubble")) bubbles.add(new Ring(new Vec3d(le.getX(), le.getY() + le.getHeight() * 0.5, le.getZ()), CombatTracker.tickCount));
    }

    public static void onKill(LivingEntity le) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !Config.on("killfx")) return;
        burst(mc, killType(Config.i("killfx.type")), le.getX(), le.getY() + le.getHeight() * 0.5, le.getZ(), 0.4, Config.i("killfx.count"));
        coloredBurst("killfx", le.getX(), le.getY() + le.getHeight() * 0.5, le.getZ(), 12, 20, 2);
    }

    private static void prune(List<Ring> list, int now, int life) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (now - list.get(i).tick() > life) list.remove(i);
        }
    }

    public static void tick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            jumpCircles.clear();
            jumpParticles.clear();
            bubbles.clear();
            coloredParticles.clear();
            lastTrailPosition = null;
            jumpStartTick = -1;
            jumpAirborne = false;
            jumpCircleCenter = null;
            return;
        }
        int now = CombatTracker.tickCount;
        for (int i = coloredParticles.size() - 1; i >= 0; i--) {
            ColoredParticle cp = coloredParticles.get(i);
            if (now - cp.bornTick >= cp.lifetime) coloredParticles.remove(i);
            else cp.tick();
        }

        boolean onGround = p.isOnGround();
        if (Config.on("jumpcircles") && wasOnGround && !onGround && p.getVelocity().y > 0.1) {
            jumpStartTick = now;
            jumpAirborne = true;
            jumpCircleCenter = new Vec3d(p.getX(), p.getY() + 0.025, p.getZ());
        }
        int jumpLife = Math.max(1, Config.i("jumpcircles.life"));
        if (!Config.on("jumpcircles")) {
            jumpStartTick = -1;
            jumpAirborne = false;
            jumpCircleCenter = null;
        } else if (jumpStartTick >= 0 && now - jumpStartTick < jumpLife) {
            if (!onGround && jumpAirborne) {
                jumpCircleCenter = new Vec3d(p.getX(), p.getY() + 0.025, p.getZ());
            } else if (onGround && jumpAirborne) {
                // Freeze the circle at the landing point, then let its configured lifetime fade it out.
                jumpCircleCenter = new Vec3d(p.getX(), p.getY() + 0.025, p.getZ());
                jumpAirborne = false;
            }
        } else {
            jumpStartTick = -1;
            jumpAirborne = false;
            jumpCircleCenter = null;
        }
        jumpParticles.clear();
        if (jumpCircleCenter != null && jumpStartTick >= 0 && now - jumpStartTick < jumpLife) {
            double ringRadius = 0.22 * Math.max(0.5, Math.min(3.0, Config.num("jumpcircles.radius")));
            for (int i = 0; i < 6; i++) {
                double angle = (Math.PI * 2.0 * i) / 6.0;
                jumpParticles.add(new JumpParticle(new Vec3d(
                        jumpCircleCenter.x + Math.cos(angle) * ringRadius,
                        jumpCircleCenter.y,
                        jumpCircleCenter.z + Math.sin(angle) * ringRadius), jumpStartTick,
                        Math.max(1.0, Config.num("jumpcircles.radius"))));
            }
        }
        wasOnGround = onGround;
        if (!bubbles.isEmpty()) prune(bubbles, now, Config.i("hitbubble.life"));

        if (Config.on("trails")) {
            Vec3d current = new Vec3d(p.getX(), p.getY(), p.getZ());
            if (lastTrailPosition != null) {
                double dx = current.x - lastTrailPosition.x;
                double dz = current.z - lastTrailPosition.z;
                double distanceSquared = dx * dx + dz * dz;
                // No displacement means no new particles. The actual movement vector
                // determines the trailing side, including immediately after a turn.
                if (distanceSquared > 0.0009 && distanceSquared < 16.0) {
                    double distance = Math.sqrt(distanceSquared);
                    double dirX = dx / distance;
                    double dirZ = dz / distance;
                    int density = Math.max(1, Math.min(5, Config.i("trails.density")));
                    int interval = density <= 1 ? 3 : density <= 3 ? 2 : 1;
                    if (now % interval == 0) {
                        int requested = Math.max(1, Math.min(3, (density + 1) / 2));
                        int spawn = Optimization.takeParticles(requested);
                        int size = Math.max(1, Math.min(4, Config.i("trails.size")));
                        for (int i = 0; i < spawn; i++) {
                            double jitter = 0.018;
                            double back = 0.30 + RNG.nextDouble() * 0.12;
                            coloredParticles.add(new ColoredParticle(
                                    p.getX() - dirX * back + (RNG.nextDouble() - 0.5) * 0.10,
                                    p.getY() + 0.38 + (RNG.nextDouble() - 0.5) * 0.20,
                                    p.getZ() - dirZ * back + (RNG.nextDouble() - 0.5) * 0.10,
                                    -dirX * 0.012 + (RNG.nextDouble() - 0.5) * jitter,
                                    0.006 + RNG.nextDouble() * 0.018,
                                    -dirZ * 0.012 + (RNG.nextDouble() - 0.5) * jitter,
                                    now, Math.max(20, Math.min(60, Config.i("trails.life"))), size, "trails"));
                        }
                    }
                }
            }
            lastTrailPosition = current;
        } else {
            lastTrailPosition = null;
            coloredParticles.removeIf(particle -> "trails".equals(particle.effectId));
        }
        while (coloredParticles.size() > 240) coloredParticles.remove(0);

        // частицы мира: на высоких уровнях оптимизации реже
        if (Config.on("worldparticles") && (!Optimization.simple() || (now & 1) == 0)) {
            int type = Config.i("worldparticles.type");
            ParticleEffect pe = type == 1 ? ParticleTypes.END_ROD : type == 2 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE;
            int n = Optimization.takeParticles(Config.i("worldparticles.density"));
            if ((now & 3) == 0) {
                int worldCount = Math.max(1, Math.min(3, Config.i("worldparticles.density")));
                coloredBurst("worldparticles", p.getX(), p.getY() + 0.8, p.getZ(), worldCount, 24, 1);
            }
            for (int i = 0; i < n; i++) {
                mc.particleManager.addParticle(pe,
                        p.getX() + (RNG.nextDouble() - 0.5) * 16, p.getY() + RNG.nextDouble() * 6, p.getZ() + (RNG.nextDouble() - 0.5) * 16,
                        0, -0.03, 0);
            }
        }
    }
}
