package dev.pvpplus.client;

import net.minecraft.client.gui.DrawContext;

public final class Gfx {
    public static void border(DrawContext c, int x, int y, int w, int h, int color) {
        // HUD edit selection uses the same neon PNG frame as the menu.
        Ui.frame(c, x, y, w, h, color);
    }

    public static void panel(DrawContext c, int x, int y, int w, int h, int bgAlpha) {
        Ui.hudPanel(c, x, y, w, h);
    }

    public static void px(DrawContext c, int x, int y, int color) {
        c.fill(x, y, x + 1, y + 1, color);
    }

    /** Styled pixel/point used by screen-projected visual effects. */
    public static void pointStyled(DrawContext c, int x, int y, int color, int size, String effectId) {
        boolean glow = Config.b(effectId + ".glow");
        boolean shadow = Config.b(effectId + ".shadow");
        int power = Math.max(1, Math.min(4, Config.i(effectId + ".glowPower")));
        int half = Math.max(1, size);
        if (shadow) c.fill(x - half + 1, y - half + 2, x + half + 2, y + half + 3, scaledAlpha(0x99000000, (color >>> 24) & 0xFF));
        if (glow) {
            for (int r = power; r >= 1; r--) {
                int a = Math.max(8, 42 / r);
                int gc = scaledAlpha(color, a);
                c.fill(x - half - r, y - half - r, x + half + r + 1, y + half + r + 1, gc);
            }
        }
        c.fill(x - half, y - half, x + half + 1, y + half + 1, color);
    }

    /** Styled line: independent per-effect glow strength and a subtle optional shadow. */
    public static void lineStyled(DrawContext c, int x0, int y0, int x1, int y1, int color, int stride, String effectId) {
        boolean glow = Config.b(effectId + ".glow");
        boolean shadow = Config.b(effectId + ".shadow");
        int power = Math.max(1, Math.min(4, Config.i(effectId + ".glowPower")));
        int st = Math.max(1, stride);
        if (shadow) line(c, x0 + 1, y0 + 2, x1 + 1, y1 + 2, scaledAlpha(0x88000000, (color >>> 24) & 0xFF), st);
        if (glow) {
            for (int r = power; r >= 1; r--) {
                int a = Math.max(8, 34 / r);
                int gc = scaledAlpha(color, a);
                line(c, x0 - r, y0, x1 - r, y1, gc, st);
                line(c, x0 + r, y0, x1 + r, y1, gc, st);
                line(c, x0, y0 - r, x1, y1 - r, gc, st);
                line(c, x0, y0 + r, x1, y1 + r, gc, st);
            }
        }
        line(c, x0, y0, x1, y1, color, st);
    }

    /** Colored, centered text with optional per-effect glow and shadow. */
    public static void centeredTextStyled(DrawContext c, net.minecraft.client.font.TextRenderer tr,
                                          String text, int centerX, int y, int color, String effectId) {
        boolean glow = Config.b(effectId + ".glow");
        boolean shadow = Config.b(effectId + ".shadow");
        int power = Math.max(1, Math.min(4, Config.i(effectId + ".glowPower")));
        int x = centerX - tr.getWidth(text) / 2;
        if (shadow) c.drawText(tr, text, x + 1, y + 1, scaledAlpha(0x99000000, (color >>> 24) & 0xFF), false);
        if (glow) {
            int a = Math.max(8, 22 * power);
            int halo = scaledAlpha(color, a);
            c.drawText(tr, text, x - 1, y, halo, false);
            c.drawText(tr, text, x + 1, y, halo, false);
            c.drawText(tr, text, x, y - 1, halo, false);
            c.drawText(tr, text, x, y + 1, halo, false);
        }
        c.drawText(tr, text, x, y, color, false);
    }

    /** Styled crosshair arm. Kept separate from lineStyled to avoid repeated per-pixel allocation. */
    public static void rectStyled(DrawContext c, int x, int y, int w, int h, int color, String effectId) {
        boolean glow = Config.b(effectId + ".glow");
        boolean shadow = Config.b(effectId + ".shadow");
        int power = Math.max(1, Math.min(4, Config.i(effectId + ".glowPower")));
        if (shadow) c.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x99000000);
        if (glow) {
            for (int r = power; r >= 1; r--) {
                c.fill(x - r, y - r, x + w + r, y + h + r, scaledAlpha(color, Math.max(8, 36 / r)));
            }
        }
        c.fill(x, y, x + w, y + h, color);
    }

    /** Replace alpha while preserving RGB. */
    public static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    /** Scale existing alpha, so glow follows a particle/text fade instead of staying fully visible. */
    private static int scaledAlpha(int color, int factor) {
        int sourceAlpha = (color >>> 24) & 0xFF;
        int resultAlpha = sourceAlpha * Math.max(0, Math.min(255, factor)) / 255;
        return (resultAlpha << 24) | (color & 0xFFFFFF);
    }

    public static void line(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        line(c, x0, y0, x1, y1, color, 1);
    }

    /** Линия из точек; stride > 1 даёт пунктир (дешевле при высоком уровне оптимизации). */
    public static void line(DrawContext c, int x0, int y0, int x1, int y1, int color, int stride) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps > 800) return;
        if (steps == 0) {
            px(c, x0, y0, color);
            return;
        }
        int st = Math.max(1, stride);
        for (int i = 0; i <= steps; i += st) {
            int x = x0 + Math.round((x1 - x0) * (float) i / steps);
            int y = y0 + Math.round((y1 - y0) * (float) i / steps);
            px(c, x, y, color);
        }
    }
}
