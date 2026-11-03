package dev.pvpplus.client;

/** Уровни оптимизации эффектов мода. Затрагивает только эффекты Plus Visual, а не всю графику Minecraft. */
public final class Optimization {
    public static final String[] NAMES = {"Минимальный", "Средний", "Высокий", "Максимальный"};

    private static final double[] PARTICLES = {1.0, 0.6, 0.3, 0.1};
    private static final int[] RING_SEG = {28, 20, 14, 8};
    private static final int[] PREDICT = {150, 110, 70, 45};
    private static final int[] STRIDE = {1, 1, 2, 3};
    private static final int[] NUMBERS = {12, 8, 5, 3};
    private static final int[] BUDGET = {200, 120, 60, 24};
    private static final double[] GLOW = {1.0, 0.75, 0.5, 0.25};

    private static int budget = 200;

    public static int level() {
        return Math.max(0, Math.min(3, Config.i("opt.level")));
    }

    /** Вызывается каждый тик: сбрасывает лимит частиц. */
    public static void beginTick() {
        budget = BUDGET[level()];
    }

    /** Сколько частиц реально можно создать из запрошенных (с учётом уровня и лимита на тик). */
    public static int takeParticles(int wanted) {
        int n = Math.max(1, (int) Math.round(wanted * PARTICLES[level()]));
        n = Math.min(n, budget);
        budget -= n;
        return Math.max(0, n);
    }

    public static int ringSegments(int level) { return RING_SEG[level]; }
    public static int ringSegments() { return RING_SEG[level()]; }
    public static int predictSteps() { return PREDICT[level()]; }
    public static int lineStride() { return STRIDE[level()]; }
    public static int maxNumbers() { return NUMBERS[level()]; }
    /** Множитель силы свечения предметов: чем выше уровень оптимизации, тем слабее. */
    public static double glowFactor() { return GLOW[level()]; }
    public static int glowPercent() { return (int) Math.round(GLOW[level()] * 100); }
    public static int budgetPerTick() { return BUDGET[level()]; }
    public static int particlePercent() { return (int) Math.round(PARTICLES[level()] * 100); }
    public static boolean simple() { return level() >= 2; }
}
