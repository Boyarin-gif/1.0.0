package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Реестр функций и их настроек (то, что показывает меню). */
public final class Features {
    public enum Tab {
        VISUALS("Visuals"), HUD("HUD"), UTILS("Utils");
        public final String title;
        Tab(String title) { this.title = title; }
    }

    public enum Kind { SLIDER, CHOICE, TOGGLE }

    /** Config key for the witch hat cosmetic (equip state), toggled from {@link CosmeticsScreen}. */
    public static final String COSMETIC_WITCH_HAT = "cosmetic.witchHat";
    /** Config keys for the dragon wings cosmetic: equip state and its palette color index. */
    public static final String COSMETIC_DRAGON_WINGS = "cosmetic.dragonWings";
    public static final String COSMETIC_DRAGON_WINGS_COLOR = "cosmetic.dragonWings.color";
    /** Config key for the golden crown cosmetic (equip state only — fixed gold color). */
    public static final String COSMETIC_GOLD_CROWN = "cosmetic.goldCrown";
    /** Config key for the back katana cosmetic (equip state only — fixed steel/gold color). */
    public static final String COSMETIC_KATANA = "cosmetic.katana";

    // First six entries keep their historic indexes so older saved configs retain their colors.
    public static final String[] COLOR_NAMES = {"White", "Green", "Red", "Cyan", "Yellow", "Purple", "Blue", "Orange", "Pink", "Gray"};
    public static final int[] COLORS = {
            0xFFFFFFFF, // White
            0xFF55FF55, // Green
            0xFFFF5555, // Red
            0xFF55FFFF, // Cyan
            0xFFFFFF55, // Yellow
            0xFFAA7CFF, // Purple
            0xFF398BFF, // Blue
            0xFFFF9A32, // Orange
            0xFFFF69B4, // Pink
            0xFF9B9BA8  // Gray
    };

    /** Palette display order requested for the in-menu swatches; indexes still match saved config. */
    public static final int[] COLOR_PALETTE_ORDER = {5, 6, 1, 4, 7, 2, 8, 9, 0, 3};

    public static final class Setting {
        public final String key, label;
        public final Kind kind;
        public final double min, max, step, def;
        public final String[] options;

        Setting(String key, String label, Kind kind, double min, double max, double step, double def, String[] options) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.step = step;
            this.def = def;
            this.options = options;
        }

        Setting withKey(String full) {
            return new Setting(full, label, kind, min, max, step, def, options);
        }
    }

    public static final class Feature {
        public final String id, name;
        public final Tab tab;
        public final boolean def;
        public final List<Setting> settings;

        Feature(String id, String name, Tab tab, boolean def, List<Setting> settings) {
            this.id = id;
            this.name = name;
            this.tab = tab;
            this.def = def;
            this.settings = settings;
        }
    }

    private static final List<Feature> ALL = new ArrayList<>();
    private static final Map<String, Boolean> DEF_ON = new HashMap<>();
    private static final Map<String, Double> DEF_NUM = new HashMap<>();
    private static final Map<String, String> DESC = new HashMap<>();

    public static List<Feature> all() { return ALL; }

    public static List<Feature> inTab(Tab t) {
        List<Feature> l = new ArrayList<>();
        for (Feature f : ALL) if (f.tab == t) l.add(f);
        return l;
    }

    public static boolean defOn(String id) { return DEF_ON.getOrDefault(id, true); }

    public static double defNum(String key) { return DEF_NUM.getOrDefault(key, 0.0); }

    public static String desc(String id) { return DESC.getOrDefault(id, ""); }

    private static Setting slider(String key, String label, double min, double max, double step, double def) {
        return new Setting(key, label, Kind.SLIDER, min, max, step, def, null);
    }

    private static Setting choice(String key, String label, int def, String... options) {
        return new Setting(key, label, Kind.CHOICE, 0, options.length - 1, 1, def, options);
    }

    private static Setting color(int def) { return choice("color", "Цвет", def, COLOR_NAMES); }

    /** Shared visual controls. They are applied only to render paths that support them. */
    private static Setting glow() { return toggle("glow", "Glow", false); }
    private static Setting glowPower() { return slider("glowPower", "Сила Glow", 1, 4, 1, 2); }
    private static Setting shadow() { return toggle("shadow", "Тень", false); }

    private static Setting toggle(String key, String label, boolean def) {
        return new Setting(key, label, Kind.TOGGLE, 0, 1, 1, def ? 1 : 0, null);
    }

    private static void f(String id, String name, Tab tab, boolean def, Setting... s) {
        List<Setting> list = new ArrayList<>();
        for (Setting st : s) {
            Setting full = st.withKey(id + "." + st.key);
            list.add(full);
            DEF_NUM.put(full.key, full.def);
        }
        ALL.add(new Feature(id, name, tab, def, list));
        DEF_ON.put(id, def);
    }

    static {
        // ----- Visuals
        f("hitparticles", "Hit Particles", Tab.VISUALS, true,
                choice("type", "Type", 0, "Crit", "Damage", "Heart", "Flame", "End Rod", "Ench. Hit"),
                slider("count", "Count", 1, 20, 1, 6), color(5), glow(), glowPower(), shadow());
        f("crit", "Crit Effects", Tab.VISUALS, true,
                choice("type", "Type", 5, "Crit", "Damage", "Heart", "Flame", "End Rod", "Ench. Hit"),
                slider("count", "Count", 2, 30, 1, 10), color(5), glow(), glowPower(), shadow());
        f("killfx", "Kill Effects", Tab.VISUALS, true,
                choice("type", "Effect", 0, "Totem", "Souls", "Explosion", "Firework"),
                slider("count", "Count", 10, 60, 1, 30), color(5), glow(), glowPower(), shadow());
        f("hitmarker", "Hitmarkers", Tab.VISUALS, true, color(0), slider("size", "Size", 2, 8, 1, 4), glow(), glowPower(), shadow());
        f("hitbubble", "Hit Bubble", Tab.VISUALS, false, color(3),
                slider("radius", "Radius", 0.5, 2.5, 0.1, 1.2), slider("life", "Time", 5, 30, 1, 12), glow(), glowPower(), shadow());
        f("hitrange", "Hit Range", Tab.VISUALS, false, color(2), slider("radius", "Radius", 2, 6, 0.5, 3), glow(), glowPower(), shadow());
        f("jumpcircles", "Jump Circles", Tab.VISUALS, true, color(5),
                slider("radius", "Radius", 0.5, 3, 0.1, 1.5), slider("life", "Time", 10, 40, 1, 20), glow(), glowPower(), shadow());
        f("trails", "Trails", Tab.VISUALS, false, color(5),
                slider("life", "Lifetime", 10, 100, 5, 40),
                slider("size", "Particle size", 1, 4, 1, 2),
                slider("density", "Particle density", 1, 5, 1, 3), glow(), glowPower(), shadow());
        f("targetesp", "Target ESP", Tab.VISUALS, true, color(2), glow(), glowPower(), shadow());
        f("arrownametag", "Arrow Nametag", Tab.VISUALS, false, slider("range", "Range", 16, 128, 8, 64), color(4), glow(), glowPower(), shadow());
        f("prediction", "Prediction", Tab.VISUALS, true, color(4), glow(), glowPower(), shadow());
        f("itemglow", "Item Glow", Tab.VISUALS, false,
                toggle("main", "Main hand", true), toggle("off", "Off hand", true),
                slider("power", "Power", 10, 100, 5, 70), color(5));
        f("norain", "No Rain", Tab.VISUALS, false);
        f("worldparticles", "World Particles", Tab.VISUALS, false,
                choice("type", "Type", 0, "Snow", "End Rod", "Cloud"),
                slider("density", "Density", 1, 10, 1, 3), color(6), glow(), glowPower(), shadow());

        // ----- HUD
        f("potions", "Potion HUD", Tab.HUD, true);
        f("fps", "FPS & Ping", Tab.HUD, true);
        f("target", "Target HUD", Tab.HUD, true);
        f("armor", "Armor HUD", Tab.HUD, true);
        f("hearts", "Hearts", Tab.HUD, true);
        f("crosshair", "Crosshair", Tab.HUD, true, color(0),
                slider("size", "Size", 1, 12, 1, 5), slider("gap", "Gap", 0, 8, 1, 3),
                slider("thick", "Thickness", 1, 3, 1, 1), toggle("dot", "Dot", false), glow(), glowPower(), shadow());
        f("totem", "Totem Bar", Tab.HUD, true);
        f("combo", "Combo", Tab.HUD, true);
        f("killfeed", "Kill Feed", Tab.HUD, true);
        f("cooldowns", "Cooldowns", Tab.HUD, true);
        f("hotkeys", "Hotkeys", Tab.HUD, false);
        f("notify", "Notifications", Tab.HUD, true);
        f("damage", "Damage Numbers", Tab.HUD, true, color(2), glow(), glowPower(), shadow());
        f("hitdir", "Hit Direction", Tab.HUD, true, color(2), glow(), glowPower(), shadow());
        f("waypoints", "Waypoints", Tab.HUD, true);
        f("mace", "Mace Helper", Tab.HUD, true);

        // ----- Utils
        f("potionalert", "Potion Alert", Tab.UTILS, true);
        f("quickmarker", "Quick Marker", Tab.UTILS, true, slider("range", "Range", 16, 256, 16, 128));
        f("gameboost", "Game Boost", Tab.UTILS, false,
                choice("profile", "Profile", 1, "Light", "Medium", "Strong"),
                toggle("bgfps", "Limit in background", true), slider("bgfpsval", "Background FPS", 10, 60, 5, 15));
        f("autorespawn", "Auto Respawn", Tab.UTILS, true);
        f("autoeat", "Auto Eat", Tab.UTILS, false,
                slider("threshold", "Eat at hunger", 4, 19, 1, 14), toggle("skipgold", "Skip golden", true));
        f("autopotion", "Auto Potion", Tab.UTILS, false,
                toggle("speed", "Speed", true), toggle("strength", "Strength", true), toggle("fireres", "Fire Res.", false));
        f("autofish", "Auto Fish", Tab.UTILS, false);
        f("brightness", "Brightness", Tab.UTILS, false, slider("level", "Boost x", 1, 15, 0.5, 5));
        f("zoom", "Zoom (C)", Tab.UTILS, true, slider("fov", "Zoom FOV", 10, 60, 5, 30));
        f("shulker", "Shulker (V)", Tab.UTILS, true);

        DEF_NUM.put("opt.level", 0.0);

        // ----- описания
        DESC.put("hitparticles", "Частицы вокруг цели при каждом попадании.");
        DESC.put("crit", "Дополнительные частицы при критическом ударе.");
        DESC.put("killfx", "Эффект на месте убитой цели.");
        DESC.put("hitmarker", "Крестик у прицела при попадании. Красный, если цель убита.");
        DESC.put("hitbubble", "Расширяющееся кольцо вокруг цели при ударе.");
        DESC.put("hitrange", "Кольцо радиуса атаки вокруг цели.");
        DESC.put("jumpcircles", "Небольшие фиолетовые частицы при прыжке.");
        DESC.put("trails", "Цветные частицы появляются позади игрока во время движения.");
        DESC.put("targetesp", "Рамка вокруг текущей цели, видна сквозь блоки.");
        DESC.put("arrownametag", "Подпись у летящих стрел: кто выпустил и расстояние.");
        DESC.put("prediction", "Линия полёта лука, жемчуга, снежка и яйца.");
        DESC.put("worldparticles", "Частицы вокруг игрока: снег, искры или облака.");
        DESC.put("potions", "Маленькое окно с эффектами и временем действия.");
        DESC.put("fps", "Окно с FPS и пингом. Двигается в редакторе HUD.");
        DESC.put("target", "Ник, здоровье, броня и расстояние до цели.");
        DESC.put("armor", "Надетая броня и её прочность рядом с хотбаром.");
        DESC.put("hearts", "Свои сердца: при яде и иссушении правая половина цветная.");
        DESC.put("crosshair", "Свой прицел: цвет, размер, зазор.");
        DESC.put("combo", "Счётчик ударов подряд без получения урона.");
        DESC.put("killfeed", "Список твоих последних убийств.");
        DESC.put("cooldowns", "Время восстановления предметов: жемчуг, яблоки и др.");
        DESC.put("hotkeys", "Список клавиш мода на экране.");
        DESC.put("notify", "Короткие уведомления сверху по центру.");
        DESC.put("damage", "Всплывающие числа нанесённого урона.");
        DESC.put("hitdir", "Красная метка у прицела: откуда тебя ударили.");
        DESC.put("waypoints", "Показ меток на экране с расстоянием.");
        DESC.put("mace", "Высота падения и бонусный урон булавы.");
        DESC.put("autorespawn", "Сам возрождает после смерти.");
        DESC.put("autoeat", "Сам ест еду из хотбара, когда голод ниже порога.");
        DESC.put("autopotion", "Сам пьёт зелья из хотбара, когда эффект закончился.");
        DESC.put("autofish", "Сам подсекает и забрасывает удочку.");
        DESC.put("brightness", "Яркость выше обычного максимума. Сила от x1 до x15.");
        DESC.put("zoom", "Приближение при удержании клавиши C.");
        DESC.put("itemglow", "Цветное свечение зачарования на предметах в руках. Цвет берётся из палитры; сила управляется отдельно.");
        DESC.put("norain", "Убирает дождь и снег только на твоём экране.");
        DESC.put("totem", "Число тотемов бессмертия в инвентаре и руках.");
        DESC.put("potionalert", "За 10 секунд до конца зелья показывает уведомление.");
        DESC.put("quickmarker", "Метка в точку взгляда на среднюю кнопку мыши.");
        DESC.put("gameboost", "Облегчает сам Minecraft: дальность, частицы, тени. Выключишь - всё вернётся.");
        DESC.put("shulker", "Открыть шалкер из инвентаря клавишей V.");

        // Cosmetics: off by default until the player equips them from the Customization window.
        DEF_ON.put(COSMETIC_WITCH_HAT, false);
        DEF_ON.put(COSMETIC_DRAGON_WINGS, false);
        DEF_ON.put(COSMETIC_GOLD_CROWN, false);
        DEF_ON.put(COSMETIC_KATANA, false);
        // Default wing color: palette index 6 = Blue (see COLORS above).
        DEF_NUM.put(COSMETIC_DRAGON_WINGS_COLOR, 6.0);
    }
}
