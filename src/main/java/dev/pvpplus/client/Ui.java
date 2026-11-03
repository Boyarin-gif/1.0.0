package dev.pvpplus.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

/**
 * PNG-only skin for Plus Visual interface surfaces.
 * Interaction, labels, hit targets and settings remain in their original Java classes.
 */
public final class Ui {
    public static final int PANEL = 0xE0141226;
    public static final int ROW = 0xFF1C1A32;
    public static final int ROW_SEL = 0xFF2A2650;
    public static final int ACCENT = 0xFF6C5CE7;
    public static final int TEXT = 0xFFE8E8F4;
    public static final int DIM = 0xFF8C8AA6;
    public static final int OFF = 0xFF3A3852;

    private static final Identifier BACKGROUND = Identifier.of("pvpplus", "textures/gui/neon_background.png");
    private static final Identifier PANEL_TEX = Identifier.of("pvpplus", "textures/gui/neon_panel.png");
    private static final Identifier FRAME = Identifier.of("pvpplus", "textures/gui/neon_frame.png");
    private static final Identifier FRAME_ACTIVE = Identifier.of("pvpplus", "textures/gui/neon_frame_active.png");
    private static final Identifier BUTTONS = Identifier.of("pvpplus", "textures/gui/neon_buttons.png");
    private static final Identifier TOGGLE = Identifier.of("pvpplus", "textures/gui/neon_toggle.png");
    private static final Identifier LOGO = Identifier.of("pvpplus", "textures/gui/neon_logo.png");
    private static final Identifier MARKERS = Identifier.of("pvpplus", "textures/gui/neon_markers.png");
    private static final Identifier SEPARATOR = Identifier.of("pvpplus", "textures/gui/neon_separator.png");
    private static final Identifier SLIDER = Identifier.of("pvpplus", "textures/gui/neon_slider.png");
    private static final Identifier KNOB = Identifier.of("pvpplus", "textures/gui/neon_knob.png");
    private static final Identifier INPUT = Identifier.of("pvpplus", "textures/gui/neon_input.png");
    private static final Identifier METRICS = Identifier.of("pvpplus", "textures/gui/neon_metrics.png");
    private static final Identifier ROOM_AVATARS = Identifier.of("pvpplus", "textures/gui/neon_room_avatars.png");
    private static final Identifier PALETTE = Identifier.of("pvpplus", "textures/gui/neon_palette.png");

    private Ui() {}

    private static void texture(DrawContext c, Identifier id, int x, int y,
                                float u, float v, int w, int h,
                                int regionW, int regionH, int textureW, int textureH) {
        if (w <= 0 || h <= 0) return;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h,
                regionW, regionH, textureW, textureH);
    }

    /** Full-screen PNG scene; all UI backgrounds are shipped in the mod resources. */
    public static void background(DrawContext c, int w, int h) {
        texture(c, BACKGROUND, 0, 0, 0, 0, w, h, 640, 360, 640, 360);
    }

    /** Opaque-ish reusable PNG panel with a nine-slice neon outline. */
    public static void panel(DrawContext c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        texture(c, PANEL_TEX, x, y, 0, 0, w, h, 128, 128, 128, 128);
        frame(c, x - 1, y - 1, w + 2, h + 2);
        separator(c, x + 4, y + 2, w - 8);
    }

    /** Panel skin used for in-game HUD cards. */
    public static void hudPanel(DrawContext c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        texture(c, PANEL_TEX, x, y, 0, 0, w, h, 128, 128, 128, 128);
        frame(c, x, y, w, h);
    }

    /** PNG nine-slice frame: edge thickness remains crisp at every size. */
    public static void frame(DrawContext c, int x, int y, int w, int h) {
        nineSlice(c, FRAME, x, y, w, h, 0, 24, 24);
    }

    public static void frame(DrawContext c, int x, int y, int w, int h, int stateColor) {
        Identifier skin = stateColor == 0xFF50DCFF ? FRAME_ACTIVE : FRAME;
        nineSlice(c, skin, x, y, w, h, 0, 24, 24);
    }

    /** Paint a PNG skin over a vanilla button without replacing its callback or keyboard handling. */
    public static void renderButton(DrawContext c, TextRenderer tr, ButtonWidget button) {
        renderButton(c, tr, button, false);
    }

    public static void renderButton(DrawContext c, TextRenderer tr, ButtonWidget button, boolean selected) {
        int skin = !button.active ? OFF : (selected || button.isHovered()) ? ACCENT : ROW_SEL;
        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        rrect(c, x, y, w, h, skin);
        c.drawCenteredTextWithShadow(tr, button.getMessage(), x + w / 2, y + (h - 8) / 2,
                button.active ? TEXT : DIM);
    }

    /** Textured button/row skin selected using the original color-state parameter. */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int col) {
        int state = col == ACCENT ? 2 : col == ROW_SEL ? 1 : col == OFF ? 3 : 0;
        nineSlice(c, BUTTONS, x, y, w, h, state * 24, 24, 96);
    }

    private static void nineSlice(DrawContext c, Identifier id, int x, int y, int w, int h,
                                 int vOffset, int texW, int fullTexH) {
        if (w <= 0 || h <= 0) return;
        int corner = Math.min(8, Math.min(w / 2, h / 2));
        int centerW = Math.max(0, w - corner * 2);
        int centerH = Math.max(0, h - corner * 2);
        // corners
        patch(c,id,x,y,corner,corner,0,vOffset,8,8,texW,fullTexH);
        patch(c,id,x+w-corner,y,corner,corner,16,vOffset,8,8,texW,fullTexH);
        patch(c,id,x,y+h-corner,corner,corner,0,vOffset+16,8,8,texW,fullTexH);
        patch(c,id,x+w-corner,y+h-corner,corner,corner,16,vOffset+16,8,8,texW,fullTexH);
        // edges
        patch(c,id,x+corner,y,centerW,corner,8,vOffset,8,8,texW,fullTexH);
        patch(c,id,x+corner,y+h-corner,centerW,corner,8,vOffset+16,8,8,texW,fullTexH);
        patch(c,id,x,y+corner,corner,centerH,0,vOffset+8,8,8,texW,fullTexH);
        patch(c,id,x+w-corner,y+corner,corner,centerH,16,vOffset+8,8,8,texW,fullTexH);
        // center
        patch(c,id,x+corner,y+corner,centerW,centerH,8,vOffset+8,8,8,texW,fullTexH);
    }

    private static void patch(DrawContext c, Identifier id, int x, int y, int w, int h,
                              int u, int v, int sw, int sh, int texW, int texH) {
        texture(c,id,x,y,u,v,w,h,sw,sh,texW,texH);
    }

    public static void toggle(DrawContext c, int x, int y, boolean on) {
        texture(c, TOGGLE, x, y, on ? 24 : 0, 0, 20, 10, 24, 16, 48, 16);
    }

    public static void logo(DrawContext c, int x, int y, int ignoredColor) {
        texture(c, LOGO, x, y, 0, 0, 16, 16, 32, 32, 32, 32);
    }

    /** Color swatch is a PNG sprite; selected swatches get the active neon PNG frame. */
    public static void paletteSwatch(DrawContext c, int paletteIndex, int x, int y, int w, int h, boolean selected) {
        int index = Math.max(0, Math.min(9, paletteIndex));
        texture(c, PALETTE, x, y, index * 16, 0, w, h, 16, 10, 160, 10);
        if (selected) frame(c, x - 2, y - 2, w + 4, h + 4, 0xFF50DCFF);
    }

    /** Room emblem sheet, all six selectable emblems are PNG sprites. */
    public static void roomAvatar(DrawContext c, int index, int x, int y, int size) {
        int slot = Math.floorMod(index, 6);
        texture(c, ROOM_AVATARS, x, y, slot * 32, 0, size, size, 32, 32, 192, 32);
    }

    /** Draw one of the built-in PNG waypoint symbols at the supplied top-left. */
    public static void marker(DrawContext c, String name, int x, int y, int size) {
        int index = switch (name == null ? "pin" : name) {
            case "home" -> 1;
            case "chest" -> 2;
            case "parachute" -> 3;
            case "sword" -> 4;
            default -> 0;
        };
        texture(c, MARKERS, x, y, index * 16, 0, size, size, 16, 16, 80, 16);
    }

    public static void separator(DrawContext c, int x, int y, int w) {
        if (w <= 0) return;
        texture(c, SEPARATOR, x, y, 0, 0, w, 2, 128, 4, 128, 4);
    }

    /** Raster metric icon: slot 0 is FPS, slots 1-4 are ping levels. */
    public static void metricIcon(DrawContext c, int slot, int x, int y, int size) {
        int index = Math.max(0, Math.min(4, slot));
        texture(c, METRICS, x, y, index * 16, 0, size, size, 16, 16, 80, 16);
    }

    public static void inputBase(DrawContext c, int x, int y, int w, int h) {
        texture(c, INPUT, x, y, 0, 0, w, h, 64, 24, 64, 24);
    }

    public static void track(DrawContext c, int x, int y, int w, int h) {
        texture(c, SLIDER, x, y, 0, 0, w, h, 32, 8, 32, 48);
    }

    public static void progress(DrawContext c, int x, int y, int w, int h) {
        texture(c, SLIDER, x, y, 0, 8, w, h, 32, 8, 32, 48);
    }

    /** PNG meter variant: 0=green, 1=yellow, 2=red, 3=orange. */
    public static void meter(DrawContext c, int x, int y, int w, int h, int variant) {
        int index = Math.max(0, Math.min(3, variant));
        texture(c, SLIDER, x, y, 0, 16 + index * 8, w, h, 32, 8, 32, 48);
    }

    public static void knob(DrawContext c, int x, int y, int w, int h) {
        texture(c, KNOB, x, y, 0, 0, w, h, 16, 16, 16, 16);
    }
}
