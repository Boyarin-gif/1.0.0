package dev.pvpplus.client;

import java.util.List;
import java.util.function.Predicate;

import dev.pvpplus.mixin.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

public final class Automation {
    // ---------- запись значения в SimpleOption в обход лимитов (яркость > 100%, зум < 30)
    public static void force(SimpleOption<?> o, Object v) {
        ((SimpleOptionAccessor) (Object) o).pvpplus$setValue(v);
    }

    // ---------- яркость
    private static boolean brightApplied = false;
    private static double savedGamma = 1.0;

    public static void tickBrightness(MinecraftClient mc) {
        SimpleOption<Double> g = mc.options.getGamma();
        if (Config.on("brightness")) {
            if (!brightApplied) {
                savedGamma = g.getValue();
                brightApplied = true;
            }
            force(g, Double.valueOf(Config.num("brightness.level")));
        } else if (brightApplied) {
            force(g, Double.valueOf(savedGamma));
            brightApplied = false;
        }
    }

    public static void restoreBrightness(MinecraftClient mc) {
        if (brightApplied) {
            force(mc.options.getGamma(), Double.valueOf(savedGamma));
            brightApplied = false;
        }
    }

    // ---------- сила свечения зачарования (настройка игры Glint Strength)
    private static boolean glintApplied = false;
    private static double savedGlint = 0.75;

    public static void tickGlint(MinecraftClient mc) {
        SimpleOption<Double> g = mc.options.getGlintStrength();
        if (Config.on("itemglow")) {
            if (!glintApplied) {
                savedGlint = g.getValue();
                glintApplied = true;
            }
            double v = Config.num("itemglow.power") / 100.0 * Optimization.glowFactor();
            force(g, Double.valueOf(Math.max(0.05, Math.min(1.0, v))));
        } else if (glintApplied) {
            force(g, Double.valueOf(savedGlint));
            glintApplied = false;
        }
    }

    public static void restoreGlint(MinecraftClient mc) {
        if (glintApplied) {
            force(mc.options.getGlintStrength(), Double.valueOf(savedGlint));
            glintApplied = false;
        }
    }

    // ---------- общий тик
    public static void tick(MinecraftClient mc) {
        tickBrightness(mc);
        tickGlint(mc);
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) {
            stopUse(mc);
            return;
        }
        tickUse(mc, p);
        tickFish(mc, p);
    }

    // ---------- авто-еда и авто-зелья: берём предмет из хотбара, держим ПКМ, потом возвращаем слот
    private static boolean active = false;
    private static int prevSlot = 0;
    private static int useTicks = 0;
    private static Predicate<ItemStack> check;

    private static void startUse(PlayerEntity p, int slot, Predicate<ItemStack> chk) {
        PlayerInventory inv = p.getInventory();
        prevSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        check = chk;
        active = true;
        useTicks = 0;
    }

    private static void stopUse(MinecraftClient mc) {
        if (!active) return;
        active = false;
        mc.options.useKey.setPressed(false);
        if (mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
    }

    private static boolean isFood(ItemStack st, boolean skipGold) {
        if (st.isEmpty() || !st.contains(DataComponentTypes.FOOD)) return false;
        if (skipGold && (st.isOf(Items.GOLDEN_APPLE) || st.isOf(Items.ENCHANTED_GOLDEN_APPLE))) return false;
        return !(st.isOf(Items.ROTTEN_FLESH) || st.isOf(Items.SPIDER_EYE) || st.isOf(Items.PUFFERFISH)
                || st.isOf(Items.POISONOUS_POTATO) || st.isOf(Items.CHORUS_FRUIT) || st.isOf(Items.SUSPICIOUS_STEW));
    }

    private static int findPotionSlot(PlayerEntity p, RegistryEntry<StatusEffect> eff) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isOf(Items.POTION)) continue;
            PotionContentsComponent pc = st.get(DataComponentTypes.POTION_CONTENTS);
            if (pc == null) continue;
            for (StatusEffectInstance e : pc.getEffects()) {
                if (e.getEffectType().equals(eff)) return i;
            }
        }
        return -1;
    }

    private static final String[] POTION_KEYS = {"autopotion.speed", "autopotion.strength", "autopotion.fireres"};
    private static final List<RegistryEntry<StatusEffect>> POTION_EFFECTS =
            List.of(StatusEffects.SPEED, StatusEffects.STRENGTH, StatusEffects.FIRE_RESISTANCE);

    private static void tickUse(MinecraftClient mc, PlayerEntity p) {
        if (active) {
            PlayerInventory inv = p.getInventory();
            ItemStack st = inv.getStack(inv.getSelectedSlot());
            if (!check.test(st) || ++useTicks > 120) {
                stopUse(mc);
                return;
            }
            mc.options.useKey.setPressed(true);
            return;
        }
        if (p.isUsingItem()) return;

        if (Config.on("autopotion")) {
            for (int i = 0; i < POTION_KEYS.length; i++) {
                final RegistryEntry<StatusEffect> eff = POTION_EFFECTS.get(i);
                if (Config.b(POTION_KEYS[i]) && !p.hasStatusEffect(eff)) {
                    int slot = findPotionSlot(p, eff);
                    if (slot >= 0) {
                        startUse(p, slot, st -> st.isOf(Items.POTION) && !p.hasStatusEffect(eff));
                        Notifier.push("Авто-зелье");
                        return;
                    }
                }
            }
        }

        if (Config.on("autoeat") && p.getHungerManager().getFoodLevel() <= Config.i("autoeat.threshold")) {
            final boolean skipGold = Config.b("autoeat.skipgold");
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < 9; i++) {
                if (isFood(inv.getStack(i), skipGold)) {
                    startUse(p, i, st -> isFood(st, skipGold) && p.getHungerManager().getFoodLevel() < 20);
                    return;
                }
            }
        }
    }

    // ---------- авто-рыбалка
    private static int fishAge = 0;
    private static int fishCooldown = 0;
    private static boolean needRecast = false;

    private static void tickFish(MinecraftClient mc, PlayerEntity p) {
        if (!Config.on("autofish") || !p.getMainHandStack().isOf(Items.FISHING_ROD)) {
            fishAge = 0;
            needRecast = false;
            return;
        }
        if (fishCooldown > 0) {
            fishCooldown--;
            return;
        }
        FishingBobberEntity hook = p.fishHook;
        if (hook == null) {
            fishAge = 0;
            if (needRecast) {
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                needRecast = false;
                fishCooldown = 20;
            }
            return;
        }
        fishAge++;
        if (fishAge > 40 && hook.isTouchingWater() && hook.getVelocity().y < -0.1) {
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            needRecast = true;
            fishCooldown = 25;
            fishAge = 0;
        }
    }
}
