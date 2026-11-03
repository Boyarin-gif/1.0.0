package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;

/**
 * Облегчение самого Minecraft через штатные настройки игры (дальность прорисовки и симуляции, частицы, тени,
 * режим графики) и ограничение FPS в свёрнутом окне. Исходные значения запоминаются и возвращаются при выключении.
 * К визуальным эффектам мода это отношения не имеет.
 */
public final class GameBoost {
    private static boolean applied = false;
    private static int appliedProfile = -1;

    private static int sView, sSim, sBlend;
    private static double sEntity;
    private static boolean sShadows;
    private static ParticlesMode sParticles;
    private static GraphicsMode sGraphics;

    private static boolean bgLimited = false;
    private static int sFps;

    public static void tick(MinecraftClient mc) {
        if (Config.on("gameboost")) {
            if (!applied) {
                save(mc);
                applied = true;
                appliedProfile = -1;
            }
            int profile = Math.max(0, Math.min(2, Config.i("gameboost.profile")));
            if (profile != appliedProfile) {
                apply(mc, profile);
                appliedProfile = profile;
            }
            background(mc);
        } else if (applied) {
            restore(mc);
        }
    }

    private static void save(MinecraftClient mc) {
        sView = mc.options.getViewDistance().getValue();
        sSim = mc.options.getSimulationDistance().getValue();
        sBlend = mc.options.getBiomeBlendRadius().getValue();
        sEntity = mc.options.getEntityDistanceScaling().getValue();
        sShadows = mc.options.getEntityShadows().getValue();
        sParticles = mc.options.getParticles().getValue();
        sGraphics = mc.options.getPreset().getValue();
    }

    /** Значения только уменьшаются относительно твоих, никогда не увеличиваются. */
    private static void apply(MinecraftClient mc, int profile) {
        // сначала возвращаем исходные, чтобы смена профиля считалась от них
        restoreValues(mc);
        switch (profile) {
            case 0 -> { // Light
                mc.options.getParticles().setValue(sParticles == ParticlesMode.ALL ? ParticlesMode.DECREASED : sParticles);
                mc.options.getEntityShadows().setValue(false);
                mc.options.getBiomeBlendRadius().setValue(Math.min(sBlend, 3));
            }
            case 1 -> { // Medium
                mc.options.getViewDistance().setValue(Math.min(sView, 10));
                mc.options.getSimulationDistance().setValue(Math.max(5, Math.min(sSim, 8)));
                mc.options.getEntityDistanceScaling().setValue(Math.max(0.5, Math.min(sEntity, 0.75)));
                mc.options.getParticles().setValue(sParticles == ParticlesMode.ALL ? ParticlesMode.DECREASED : sParticles);
                mc.options.getEntityShadows().setValue(false);
                mc.options.getBiomeBlendRadius().setValue(Math.min(sBlend, 2));
            }
            default -> { // Strong
                mc.options.getViewDistance().setValue(Math.min(sView, 6));
                mc.options.getSimulationDistance().setValue(Math.max(5, Math.min(sSim, 5)));
                mc.options.getEntityDistanceScaling().setValue(0.5);
                mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
                mc.options.getEntityShadows().setValue(false);
                mc.options.getBiomeBlendRadius().setValue(0);
                mc.options.applyGraphicsMode(GraphicsMode.FAST);
            }
        }
    }

    private static void restoreValues(MinecraftClient mc) {
        mc.options.getViewDistance().setValue(sView);
        mc.options.getSimulationDistance().setValue(sSim);
        mc.options.getBiomeBlendRadius().setValue(sBlend);
        mc.options.getEntityDistanceScaling().setValue(sEntity);
        mc.options.getEntityShadows().setValue(sShadows);
        mc.options.getParticles().setValue(sParticles);
        mc.options.applyGraphicsMode(sGraphics);
    }

    /** Ограничение FPS, пока окно игры не в фокусе (сворачивание, другое окно). */
    private static void background(MinecraftClient mc) {
        boolean wantLimit = Config.b("gameboost.bgfps") && !mc.isWindowFocused();
        if (wantLimit && !bgLimited) {
            sFps = mc.options.getMaxFps().getValue();
            mc.options.getMaxFps().setValue(Math.max(10, Config.i("gameboost.bgfpsval")));
            bgLimited = true;
        } else if (!wantLimit && bgLimited) {
            mc.options.getMaxFps().setValue(sFps);
            bgLimited = false;
        }
    }

    public static void restore(MinecraftClient mc) {
        if (bgLimited) {
            mc.options.getMaxFps().setValue(sFps);
            bgLimited = false;
        }
        if (applied) {
            restoreValues(mc);
            applied = false;
            appliedProfile = -1;
        }
    }
}
