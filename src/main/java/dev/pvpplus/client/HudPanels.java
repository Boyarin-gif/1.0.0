package dev.pvpplus.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.MinecraftClient;

/** Положение перетаскиваемых панелей. Сохранённая позиция берётся из конфига, иначе - по умолчанию у края экрана. */
public final class HudPanels {
    public static boolean editing = false;

    public static final class Def {
        public final String id, title, anchor;
        public final int offX, offY, defW, defH;

        Def(String id, String title, String anchor, int offX, int offY, int defW, int defH) {
            this.id = id;
            this.title = title;
            this.anchor = anchor;
            this.offX = offX;
            this.offY = offY;
            this.defW = defW;
            this.defH = defH;
        }
    }

    public static final List<Def> DEFS = List.of(
            new Def("fps", "FPS и пинг", "TL", 4, 4, 70, 13),
            new Def("potions", "Эффекты", "TL", 4, 22, 90, 34),
            new Def("target", "Цель", "TR", 4, 40, 100, 42),
            new Def("combo", "Комбо", "TC", 0, 40, 60, 13),
            new Def("killfeed", "Убийства", "TR", 4, 94, 100, 24),
            new Def("cooldowns", "Откаты", "CR", 4, 0, 50, 40),
            new Def("hotkeys", "Клавиши", "TL", 4, 62, 80, 40),
            new Def("notify", "Уведомления", "TC", 0, 4, 100, 17),
            new Def("mace", "Булава", "TC", 0, 60, 120, 13),
            new Def("totem", "Тотемы", "BC", 76, 62, 30, 18),
            new Def("armor", "Броня", "BC", 131, 2, 70, 40)
    );

    /** Последняя нарисованная рамка каждой панели: {x, y, w, h}. Массивы переиспользуются. */
    private static final Map<String, int[]> RECTS = new HashMap<>();

    public static Def def(String id) {
        for (Def d : DEFS) if (d.id.equals(id)) return d;
        throw new IllegalArgumentException(id);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int defaultX(Def d, int w, int sw) {
        return switch (d.anchor) {
            case "TR", "CR" -> sw - w - d.offX;
            case "TC", "BC" -> (sw - w) / 2 + d.offX;
            default -> d.offX;
        };
    }

    private static int defaultY(Def d, int h, int sh) {
        return switch (d.anchor) {
            case "BC" -> sh - h - d.offY;
            case "CR" -> (sh - h) / 2 + d.offY;
            default -> d.offY;
        };
    }

    /** Возвращает {x, y, w, h} панели нужного размера и запоминает её рамку. Читать только индексы 0 и 1. */
    public static int[] place(String id, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        Def d = def(id);
        int[] saved = Config.d.pos.get(id);
        int x = saved != null ? saved[0] : defaultX(d, w, sw);
        int y = saved != null ? saved[1] : defaultY(d, h, sh);
        int[] r = RECTS.get(id);
        if (r == null) {
            r = new int[4];
            RECTS.put(id, r);
        }
        r[0] = clamp(x, 0, Math.max(0, sw - w));
        r[1] = clamp(y, 0, Math.max(0, sh - h));
        r[2] = w;
        r[3] = h;
        return r;
    }

    /** Рамка {x, y, w, h}: последняя нарисованная или по умолчанию. */
    public static int[] rect(String id) {
        int[] r = RECTS.get(id);
        if (r != null) return r;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        Def d = def(id);
        int[] saved = Config.d.pos.get(id);
        int x = saved != null ? saved[0] : defaultX(d, d.defW, sw);
        int y = saved != null ? saved[1] : defaultY(d, d.defH, sh);
        return new int[]{clamp(x, 0, Math.max(0, sw - d.defW)), clamp(y, 0, Math.max(0, sh - d.defH)), d.defW, d.defH};
    }

    public static void setPos(String id, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int[] r = rect(id);
        x = clamp(x, 0, Math.max(0, sw - r[2]));
        y = clamp(y, 0, Math.max(0, sh - r[3]));
        Config.d.pos.put(id, new int[]{x, y});
        int[] cur = RECTS.get(id);
        if (cur == null) {
            cur = new int[4];
            RECTS.put(id, cur);
        }
        cur[0] = x;
        cur[1] = y;
        cur[2] = r[2];
        cur[3] = r[3];
    }

    public static void resetAll() {
        Config.d.pos.clear();
        RECTS.clear();
    }
}
