package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Следит за твоими ударами: урон, комбо, убийства, направление полученного удара. */
public final class CombatTracker {
    public record DamageNumber(Vec3d pos, float amount, int tick, String text) {}
    public record FeedEntry(String text, int tick) {}

    public static int tickCount = 0;
    public static int combo = 0;
    public static int lastHitTick = -1000;
    public static int hitmarkerTick = -1000;
    public static boolean hitmarkerKill = false;
    public static int lastTargetId = -1;
    public static int lastAttackTick = -1000;
    public static int kills = 0;
    public static boolean lastAttackCrit = false;

    public static double hitDx, hitDz;
    public static int hitDirTick = -1000;

    public static final List<DamageNumber> numbers = new ArrayList<>();
    public static final List<FeedEntry> feed = new ArrayList<>();

    private static final Map<Integer, Float> health = new HashMap<>();
    private static final Map<Integer, Integer> attackedAt = new HashMap<>();
    private static int prevHurt = 0;

    public static void onAttack(Entity e) {
        if (!(e instanceof LivingEntity le) || le.isDead()) return;
        int id = le.getId();
        lastTargetId = id;
        lastAttackTick = tickCount;
        attackedAt.put(id, tickCount);
        health.putIfAbsent(id, le.getHealth());
        PlayerEntity pl = MinecraftClient.getInstance().player;
        lastAttackCrit = pl != null && Effects.isCrit(pl);
    }

    public static void reset() {
        health.clear();
        attackedAt.clear();
        numbers.clear();
        combo = 0;
        prevHurt = 0;
    }

    /** Текущая цель: сущность под прицелом или последняя, которую ты бил. */
    public static LivingEntity currentTarget(MinecraftClient mc) {
        if (mc.world == null) return null;
        if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) return le;
        if (tickCount - lastAttackTick < 100) {
            Entity e = mc.world.getEntityById(lastTargetId);
            if (e instanceof LivingEntity le2 && le2.isAlive()) return le2;
        }
        return null;
    }

    public static void tick(MinecraftClient mc) {
        tickCount++;
        ClientWorld w = mc.world;
        PlayerEntity p = mc.player;
        if (w == null || p == null) {
            reset();
            return;
        }

        if (!attackedAt.isEmpty()) {
            Iterator<Map.Entry<Integer, Integer>> it = attackedAt.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Integer> en = it.next();
                int id = en.getKey();
                Entity e = w.getEntityById(id);
                if (!(e instanceof LivingEntity le)) {
                    it.remove();
                    health.remove(id);
                    continue;
                }
                float last = health.getOrDefault(id, le.getHealth());
                float now = le.getHealth();
                if (now < last - 0.01f) onHit(le, last - Math.max(now, 0f));
                if (le.isDead() || now <= 0f) {
                    onKill(le);
                    it.remove();
                    health.remove(id);
                    continue;
                }
                health.put(id, now);
                if (tickCount - en.getValue() > 100) {
                    it.remove();
                    health.remove(id);
                }
            }
        }

        // получили удар
        if (p.hurtTime > prevHurt && p.hurtTime > 0) {
            combo = 0;
            LivingEntity attacker = p.getAttacker();
            if (attacker != null) {
                hitDx = attacker.getX() - p.getX();
                hitDz = attacker.getZ() - p.getZ();
                hitDirTick = tickCount;
            }
        }
        prevHurt = p.hurtTime;

        if (tickCount - lastHitTick > 40) combo = 0;
        for (int i = numbers.size() - 1; i >= 0; i--) {
            if (tickCount - numbers.get(i).tick() > 25) numbers.remove(i);
        }
    }

    private static void onHit(LivingEntity le, float dmg) {
        double v = Math.round(dmg * 10) / 10.0;
        String text = "-" + ((v == Math.floor(v)) ? String.valueOf((int) v) : String.valueOf(v));
        while (numbers.size() >= Optimization.maxNumbers()) numbers.remove(0);
        numbers.add(new DamageNumber(new Vec3d(le.getX(), le.getY() + le.getHeight() + 0.2, le.getZ()), dmg, tickCount, text));
        combo++;
        lastHitTick = tickCount;
        hitmarkerTick = tickCount;
        hitmarkerKill = false;
        Effects.onHit(le, lastAttackCrit);
        if (combo >= 5 && combo % 5 == 0) Notifier.push("Комбо x" + combo);
    }

    private static void onKill(LivingEntity le) {
        kills++;
        hitmarkerTick = tickCount;
        hitmarkerKill = true;
        Effects.onKill(le);
        String name = le.getName().getString();
        feed.add(new FeedEntry("Вы убили " + name, tickCount));
        while (feed.size() > 5) feed.remove(0);
        Notifier.push("Убийство: " + name);
    }
}
