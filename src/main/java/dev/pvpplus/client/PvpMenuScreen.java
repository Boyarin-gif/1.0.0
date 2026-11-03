package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import dev.pvpplus.client.Features.Feature;
import dev.pvpplus.client.Features.Setting;
import dev.pvpplus.client.Features.Tab;
import dev.pvpplus.client.WaypointManager.Waypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** Меню Plus Visual: слева настройки пункта, по центру функции / оптимизация / автокоманды, справа метки. */
public class PvpMenuScreen extends Screen {
    private static final String TITLE = "Plus Visual";
    private static final int LW = 108, CW = 220, RW = 124, GAP = 6;
    private static final int CONTENT_H = 200;
    private static final int MAX_WP = 6;
    private static final int MAX_CMD_ROWS = 5;
    private static final int MAX_CMDS = 20;

    private enum Page { FEATURES, OPT, CMDS }

    private record Hit(int x, int y, int w, int h, Runnable click, IntConsumer drag) {}

    private static final String[] OPT_HELP = {
            "Все эффекты мода в полном качестве.",
            "Часть частиц убрана, кольца упрощены.",
            "Мало частиц, пунктирные линии, редкие обновления.",
            "Минимум частиц и самые простые эффекты."
    };

    private final List<Hit> hits = new ArrayList<>();
    private Hit active = null;
    private boolean wasDown = true;
    private boolean dirty = false;

    private Page page = Page.FEATURES;
    private Tab tab = Tab.VISUALS;
    private Feature selected = Features.inTab(Tab.VISUALS).get(0);

    private TextFieldWidget search, name, fx, fy, fz, cmdField;
    private String sSearch = "", sName = "", sX = "", sY = "", sZ = "", sCmd = "";
    private String status = "";
    private String selectedMarkerIcon = "pin";
    private static final String[] MARKER_ICONS = {"pin", "home", "chest", "parachute", "sword"};
    private static final String[] MARKER_ICON_NAMES = {"Метка", "Дом", "Сундук", "Парашют", "Меч"};

    // автокоманды
    private int editIndex = -1;
    private int capKey = 0;
    private boolean capturing = false;
    private final boolean[] heldAtStart = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private String cmdStatus = "";

    private int leftX, centerX, rightX, top, contentTop;
    private int settingsScroll = 0;

    public PvpMenuScreen() {
        super(Text.literal(TITLE));
    }

    @Override
    protected void init() {
        if (name != null) remember();

        int total = LW + GAP + CW + GAP + RW;
        int x0 = (width - total) / 2;
        leftX = x0;
        centerX = x0 + LW + GAP;
        rightX = centerX + CW + GAP;
        top = Math.max(2, (height - 262) / 2);
        contentTop = top + 26;

        search = new TextFieldWidget(textRenderer, centerX + 156, contentTop + 4, 58, 14, Text.literal("Поиск"));
        search.setDrawsBackground(false);
        search.setMaxLength(16);
        search.setPlaceholder(Text.literal("Поиск"));
        search.setText(sSearch);
        addDrawableChild(search);

        cmdField = new TextFieldWidget(textRenderer, centerX + 58, contentTop + 120, CW - 64, 14, Text.literal("Команда"));
        cmdField.setDrawsBackground(false);
        cmdField.setMaxLength(100);
        cmdField.setPlaceholder(Text.literal("/spawn"));
        cmdField.setText(sCmd);
        addDrawableChild(cmdField);

        name = new TextFieldWidget(textRenderer, rightX + 6, contentTop + 22, RW - 12, 14, Text.literal("Название"));
        name.setDrawsBackground(false);
        name.setMaxLength(24);
        name.setPlaceholder(Text.literal("Название"));
        name.setText(sName);
        addDrawableChild(name);
        fx = coord(rightX + 6, "X", sX);
        fy = coord(rightX + 43, "Y", sY);
        fz = coord(rightX + 80, "Z", sZ);
    }

    private TextFieldWidget coord(int x, String placeholder, String value) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, contentTop + 40, 34, 14, Text.literal(placeholder));
        f.setDrawsBackground(false);
        f.setMaxLength(8);
        f.setPlaceholder(Text.literal(placeholder));
        f.setText(value);
        addDrawableChild(f);
        return f;
    }

    private void remember() {
        sSearch = search.getText();
        sCmd = cmdField.getText();
        sName = name.getText();
        sX = fx.getText();
        sY = fy.getText();
        sZ = fz.getText();
    }

    // ------------------------------------------------------------ метки
    private void fillHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        fx.setText(String.valueOf(mc.player.getBlockX()));
        fy.setText(String.valueOf(mc.player.getBlockY()));
        fz.setText(String.valueOf(mc.player.getBlockZ()));
    }

    private void addWaypoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        String n = name.getText().trim();
        if (n.isEmpty()) {
            status = "Введите название";
            return;
        }
        try {
            double x = Double.parseDouble(fx.getText().trim().replace(',', '.'));
            double z = Double.parseDouble(fz.getText().trim().replace(',', '.'));
            String ys = fy.getText().trim();
            double y = ys.isEmpty() ? mc.player.getY() : Double.parseDouble(ys.replace(',', '.'));
            Waypoint wp = new Waypoint();
            wp.name = n;
            wp.x = x;
            wp.y = y;
            wp.z = z;
            wp.dim = mc.world.getRegistryKey().getValue().toString();
            wp.icon = selectedMarkerIcon;
            WaypointManager.addPersonal(wp);
            WaypointManager.save();
            name.setText("");
            fx.setText("");
            fy.setText("");
            fz.setText("");
            status = "";
        } catch (NumberFormatException e) {
            status = "Координаты: только числа";
        }
    }

    private void cycleSelectedMarkerIcon() { selectedMarkerIcon = nextIcon(selectedMarkerIcon); }

    private static String nextIcon(String icon) {
        for (int i = 0; i < MARKER_ICONS.length; i++) if (MARKER_ICONS[i].equals(icon)) return MARKER_ICONS[(i + 1) % MARKER_ICONS.length];
        return MARKER_ICONS[0];
    }

    private static String markerIconName(String icon) {
        for (int i = 0; i < MARKER_ICONS.length; i++) if (MARKER_ICONS[i].equals(icon)) return MARKER_ICON_NAMES[i];
        return MARKER_ICON_NAMES[0];
    }

    private static void drawMiniMarkerIcon(DrawContext ctx, String icon, int x, int y) {
        Ui.marker(ctx, icon, x - 6, y - 6, 12);
    }

    private static String dimShort(String d) {
        if (d == null) return "";
        return switch (d) {
            case "minecraft:overworld" -> "Мир";
            case "minecraft:the_nether" -> "Незер";
            case "minecraft:the_end" -> "Энд";
            default -> d;
        };
    }

    // ------------------------------------------------------------ автокоманды
    private void startEdit(int idx) {
        if (idx < 0 || idx >= Config.d.commands.size()) return;
        Config.AutoCommand c = Config.d.commands.get(idx);
        editIndex = idx;
        cmdField.setText(c.command);
        capKey = c.key;
        capturing = false;
        cmdStatus = "";
    }

    private void clearEdit() {
        editIndex = -1;
        cmdField.setText("");
        capKey = 0;
        capturing = false;
    }

    private void beginCapture() {
        capturing = true;
        cmdField.setFocused(false);
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        for (int k : AutoCommands.KEYS) heldAtStart[k] = GLFW.glfwGetKey(handle, k) == GLFW.GLFW_PRESS;
    }

    private void pollCapture() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        for (int k : AutoCommands.KEYS) {
            boolean down = GLFW.glfwGetKey(handle, k) == GLFW.GLFW_PRESS;
            if (!down) {
                heldAtStart[k] = false;
                continue;
            }
            if (heldAtStart[k]) continue;
            capturing = false;
            capKey = k == GLFW.GLFW_KEY_BACKSPACE ? 0 : k;
            String cf = capKey > 0 ? AutoCommands.conflict(MinecraftClient.getInstance(), capKey, editIndex) : null;
            cmdStatus = cf != null ? cf : "";
            return;
        }
    }

    private void commitCommand() {
        capturing = false;
        String cmd = cmdField.getText().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
        if (cmd.isEmpty()) {
            cmdStatus = "Введите команду";
            return;
        }
        if (capKey <= 0) {
            cmdStatus = "Назначь клавишу";
            return;
        }
        String cf = AutoCommands.conflict(MinecraftClient.getInstance(), capKey, editIndex);
        if (cf != null) {
            cmdStatus = cf;
            return;
        }
        List<Config.AutoCommand> list = Config.d.commands;
        boolean editing = editIndex >= 0 && editIndex < list.size();
        if (!editing && list.size() >= MAX_CMDS) {
            cmdStatus = "Максимум " + MAX_CMDS + " команд";
            return;
        }
        Config.AutoCommand c = editing ? list.get(editIndex) : new Config.AutoCommand();
        c.command = "/" + cmd;
        c.key = capKey;
        if (!editing) list.add(c);
        Config.save();
        clearEdit();
        cmdStatus = "Сохранено";
    }

    // ------------------------------------------------------------ рисование
    private void region(int x, int y, int w, int h, Runnable click) {
        hits.add(new Hit(x, y, w, h, click, null));
    }

    private void pill(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, int bg, Runnable click) {
        Ui.rrect(ctx, x, y, w, h, bg);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2, Ui.TEXT);
        region(x, y, w, h, click);
    }

    // этот метод пустой, чтобы не было размытия фона (меню рисует себя само)
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        TextRenderer tr = textRenderer;
        search.visible = page == Page.FEATURES;
        cmdField.visible = page == Page.CMDS;

        Ui.background(ctx, width, height);
        drawHeader(ctx, tr);
        drawLeft(ctx, tr);
        drawCenter(ctx, tr);
        drawRight(ctx, tr);
        drawBottom(ctx, tr);

        inputBase(ctx, search);
        inputBase(ctx, cmdField);
        inputBase(ctx, name);
        inputBase(ctx, fx);
        inputBase(ctx, fy);
        inputBase(ctx, fz);
        super.render(ctx, mouseX, mouseY, delta);
        // The text-field controls use the same PNG neon frame as the rest of the UI.
        frameField(ctx, search);
        frameField(ctx, cmdField);
        frameField(ctx, name);
        frameField(ctx, fx);
        frameField(ctx, fy);
        frameField(ctx, fz);
        if (capturing) pollCapture();
        poll(mouseX, mouseY);
    }


    private static void inputBase(DrawContext ctx, TextFieldWidget field) {
        if (field != null && field.visible) Ui.inputBase(ctx, field.getX(), field.getY(), field.getWidth(), field.getHeight());
    }

    private static void frameField(DrawContext ctx, TextFieldWidget field) {
        if (field != null && field.visible) {
            Ui.frame(ctx, field.getX() - 1, field.getY() - 1, field.getWidth() + 2, field.getHeight() + 2);
        }
    }

    private void drawHeader(DrawContext ctx, TextRenderer tr) {
        Text t = Text.literal(TITLE).formatted(Formatting.BOLD);
        int tw = tr.getWidth(t) + 18;
        int x = width / 2 - tw / 2;
        Ui.logo(ctx, x, top + 4, Ui.ACCENT);
        ctx.drawTextWithShadow(tr, t, x + 18, top + 7, Ui.TEXT);
    }

    private static String fmt(Setting s, double v) {
        if (s.kind == Features.Kind.CHOICE) return s.options[Math.floorMod((int) Math.round(v), s.options.length)];
        return s.step >= 1 ? String.valueOf((int) Math.round(v)) : String.format("%.1f", v);
    }

    private void setSlider(Setting s, int mx, int tx, int tw) {
        double t = Math.max(0, Math.min(1, (mx - tx) / (double) tw));
        double v = s.min + t * (s.max - s.min);
        v = Math.round(v / s.step) * s.step;
        v = Math.max(s.min, Math.min(s.max, v));
        Config.setNum(s.key, v);
        dirty = true;
    }

    private static List<String> wrap(TextRenderer tr, String text, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (tr.getWidth(test) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void leftInfo(DrawContext ctx, TextRenderer tr, String title, String text) {
        ctx.drawTextWithShadow(tr, tr.trimToWidth(title, LW - 12), leftX + 6, contentTop + 6, Ui.ACCENT);
        Ui.separator(ctx, leftX + 6, contentTop + 16, LW - 12);
        int dy = contentTop + 23;
        for (String line : wrap(tr, text, LW - 12)) {
            ctx.drawTextWithShadow(tr, line, leftX + 6, dy, Ui.DIM);
            dy += 10;
        }
    }

    private static int settingHeight(Setting s) {
        if (s.key.endsWith(".color")) return 34;
        return switch (s.kind) {
            case SLIDER, CHOICE -> 24;
            case TOGGLE -> 19;
        };
    }

    private void drawColorPalette(DrawContext ctx, TextRenderer tr, Setting s, int y, int viewportTop, int viewportBottom) {
        int current = Math.floorMod(Config.i(s.key), Features.COLORS.length);
        ctx.drawTextWithShadow(tr, tr.trimToWidth(s.label, 56), leftX + 6, y, Ui.DIM);
        String currentName = Features.COLOR_NAMES[current];
        ctx.drawTextWithShadow(tr, tr.trimToWidth(currentName, 42), leftX + LW - 6 - tr.getWidth(tr.trimToWidth(currentName, 42)), y, Ui.TEXT);
        int baseX = leftX + 7;
        int baseY = y + 11;
        for (int slot = 0; slot < Features.COLOR_PALETTE_ORDER.length; slot++) {
            final int paletteIndex = Features.COLOR_PALETTE_ORDER[slot];
            int col = slot % 5;
            int row = slot / 5;
            int sx = baseX + col * 18;
            int sy = baseY + row * 10;
            int sw = 14, sh = 8;
            if (sy + sh >= viewportTop && sy < viewportBottom) {
                Ui.paletteSwatch(ctx, paletteIndex, sx, sy, sw, sh, paletteIndex == current);
                final int chosen = paletteIndex;
                int hitTop = Math.max(sy - 2, viewportTop);
                int hitBottom = Math.min(sy + sh + 2, viewportBottom);
                if (hitBottom > hitTop) {
                    hits.add(new Hit(sx - 2, hitTop, sw + 4, hitBottom - hitTop, () -> {
                        Config.setNum(s.key, chosen);
                        dirty = true;
                    }, null));
                }
            }
        }
    }

    private void drawLeft(DrawContext ctx, TextRenderer tr) {
        Ui.panel(ctx, leftX, contentTop, LW, CONTENT_H);
        if (page == Page.OPT) {
            leftInfo(ctx, tr, "Оптимизация", OPT_HELP[Optimization.level()]);
            return;
        }
        if (page == Page.CMDS) {
            leftInfo(ctx, tr, "Автокоманды", "Команда уходит штатно, как из чата: права и ограничения сервера действуют. Backspace снимает клавишу.");
            return;
        }
        if (selected == null) return;
        ctx.drawTextWithShadow(tr, tr.trimToWidth(selected.name, LW - 12), leftX + 6, contentTop + 6, Ui.ACCENT);
        Ui.separator(ctx, leftX + 6, contentTop + 16, LW - 12);

        int dy = contentTop + 23;
        for (String line : wrap(tr, Features.desc(selected.id), LW - 12)) {
            if (dy < contentTop + CONTENT_H - 4) ctx.drawTextWithShadow(tr, line, leftX + 6, dy, Ui.DIM);
            dy += 10;
        }
        if (selected.settings.isEmpty()) return;
        Ui.separator(ctx, leftX + 6, dy, LW - 12);
        int settingsTop = dy + 6;
        int totalHeight = 0;
        for (Setting setting : selected.settings) totalHeight += settingHeight(setting);
        int fullBottom = contentTop + CONTENT_H - 4;
        int viewportBottom = fullBottom;
        int maxScroll = Math.max(0, totalHeight - (viewportBottom - settingsTop));
        if (maxScroll > 0) {
            viewportBottom = fullBottom - 18;
            maxScroll = Math.max(0, totalHeight - (viewportBottom - settingsTop));
        }
        settingsScroll = Math.max(0, Math.min(settingsScroll, maxScroll));
        final int scrollLimit = maxScroll;

        // Clip the settings list so long effect configurations remain fully usable.
        ctx.enableScissor(leftX + 3, settingsTop, leftX + LW - 3, viewportBottom);
        int y = settingsTop - settingsScroll;
        for (Setting s : selected.settings) {
            int h = settingHeight(s);
            int rowTop = y;
            int rowBottom = y + h;
            boolean visible = rowBottom > settingsTop && rowTop < viewportBottom;
            if (visible) {
                if (s.key.endsWith(".color")) {
                    drawColorPalette(ctx, tr, s, y, settingsTop, viewportBottom);
                } else if (s.kind == Features.Kind.SLIDER) {
                    double v = Config.num(s.key);
                    ctx.drawTextWithShadow(tr, tr.trimToWidth(s.label, LW - 34), leftX + 6, y, Ui.DIM);
                    String vs = fmt(s, v);
                    ctx.drawTextWithShadow(tr, vs, leftX + LW - 6 - tr.getWidth(vs), y, Ui.TEXT);
                    final int tx = leftX + 6, tw = LW - 12, ty = y + 12;
                    double t = Math.max(0, Math.min(1, (v - s.min) / (s.max - s.min)));
                    int fill = (int) (tw * t);
                    Ui.track(ctx, tx, ty, tw, 4);
                    Ui.progress(ctx, tx, ty, Math.max(2, fill), 4);
                    Ui.knob(ctx, tx + fill - 4, ty - 4, 8, 12);
                    int hitTop = Math.max(settingsTop, ty - 5);
                    int hitBottom = Math.min(viewportBottom, ty + 9);
                    if (hitBottom > hitTop) hits.add(new Hit(tx - 2, hitTop, tw + 4, hitBottom - hitTop, null, mx -> setSlider(s, mx, tx, tw)));
                } else if (s.kind == Features.Kind.CHOICE) {
                    final int idx = Math.floorMod(Config.i(s.key), s.options.length);
                    ctx.drawTextWithShadow(tr, tr.trimToWidth(s.label, LW - 12), leftX + 6, y, Ui.DIM);
                    Ui.rrect(ctx, leftX + 6, y + 10, LW - 12, 12, Ui.ROW);
                    ctx.drawCenteredTextWithShadow(tr, "< " + s.options[idx] + " >", leftX + LW / 2, y + 12, Ui.TEXT);
                    int hitTop = Math.max(settingsTop, y + 10);
                    int hitBottom = Math.min(viewportBottom, y + 22);
                    if (hitBottom > hitTop) region(leftX + 6, hitTop, LW - 12, hitBottom - hitTop, () -> {
                        Config.setNum(s.key, (idx + 1) % s.options.length);
                        dirty = true;
                    });
                } else {
                    boolean on = Config.b(s.key);
                    ctx.drawTextWithShadow(tr, tr.trimToWidth(s.label, LW - 38), leftX + 6, y + 1, Ui.DIM);
                    Ui.toggle(ctx, leftX + LW - 26, y, on);
                    int hitTop = Math.max(settingsTop, y - 2);
                    int hitBottom = Math.min(viewportBottom, y + 12);
                    if (hitBottom > hitTop) region(leftX + 6, hitTop, LW - 12, hitBottom - hitTop, () -> {
                        Config.setNum(s.key, Config.b(s.key) ? 0 : 1);
                        dirty = true;
                    });
                }
            }
            y += h;
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int fy = fullBottom - 14;
            ctx.drawTextWithShadow(tr, "Настройки", leftX + 6, fy + 3, Ui.DIM);
            Ui.rrect(ctx, leftX + LW - 38, fy, 14, 13, settingsScroll > 0 ? Ui.ROW_SEL : Ui.OFF);
            ctx.drawCenteredTextWithShadow(tr, "↑", leftX + LW - 31, fy + 3, Ui.TEXT);
            region(leftX + LW - 38, fy, 14, 13, () -> settingsScroll = Math.max(0, settingsScroll - 24));
            Ui.rrect(ctx, leftX + LW - 20, fy, 14, 13, settingsScroll < maxScroll ? Ui.ROW_SEL : Ui.OFF);
            ctx.drawCenteredTextWithShadow(tr, "↓", leftX + LW - 13, fy + 3, Ui.TEXT);
            region(leftX + LW - 20, fy, 14, 13, () -> settingsScroll = Math.min(scrollLimit, settingsScroll + 24));
        }
    }

    private List<Feature> visibleFeatures() {
        String q = search == null ? "" : search.getText().trim().toLowerCase();
        if (q.isEmpty()) return Features.inTab(tab);
        List<Feature> l = new ArrayList<>();
        for (Feature f : Features.all()) if (f.name.toLowerCase().contains(q)) l.add(f);
        return l;
    }

    private void drawCenter(DrawContext ctx, TextRenderer tr) {
        Ui.panel(ctx, centerX, contentTop, CW, CONTENT_H);

        boolean searching = page == Page.FEATURES && search != null && !search.getText().trim().isEmpty();
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final Tab tt = tabs[i];
            int tx = centerX + 6 + i * 50;
            boolean sel = page == Page.FEATURES && tt == tab && !searching;
            Ui.rrect(ctx, tx, contentTop + 4, 46, 14, sel ? Ui.ACCENT : Ui.ROW);
            ctx.drawCenteredTextWithShadow(tr, tt.title, tx + 23, contentTop + 7, Ui.TEXT);
            region(tx, contentTop + 4, 46, 14, () -> {
                page = Page.FEATURES;
                tab = tt;
                search.setText("");
                capturing = false;
                selected = Features.inTab(tt).get(0);
                settingsScroll = 0;
            });
        }

        if (page == Page.OPT) {
            drawOpt(ctx, tr);
            return;
        }
        if (page == Page.CMDS) {
            drawCmds(ctx, tr);
            return;
        }

        List<Feature> shown = visibleFeatures();
        int colW = 101, rowH = 18, listTop = contentTop + 24;
        for (int i = 0; i < Math.min(shown.size(), 18); i++) {
            final Feature f = shown.get(i);
            int col = i % 2, row = i / 2;
            int rx = centerX + 6 + col * (colW + 6);
            int ry = listTop + row * rowH;
            Ui.rrect(ctx, rx, ry, colW, rowH - 2, f == selected ? Ui.ROW_SEL : Ui.ROW);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(f.name, colW - 30), rx + 5, ry + 4, Ui.TEXT);
            Ui.toggle(ctx, rx + colW - 24, ry + 3, Config.on(f.id));
            region(rx, ry, colW - 26, rowH - 2, () -> { selected = f; settingsScroll = 0; });
            region(rx + colW - 24, ry + 3, 20, 10, () -> {
                Config.setOn(f.id, !Config.on(f.id));
                selected = f;
                dirty = true;
            });
        }
    }

    // ------------------------------------------------------------ раздел «Оптимизация»
    private void setLevel(int lvl) {
        Config.setNum("opt.level", Math.max(0, Math.min(3, lvl)));
        dirty = true;
    }

    private void drawOpt(DrawContext ctx, TextRenderer tr) {
        int x = centerX + 6, y = contentTop + 26;
        ctx.drawTextWithShadow(tr, "Уровень оптимизации", x, y, Ui.TEXT);
        int lvl = Optimization.level();
        for (int i = 0; i < 4; i++) {
            final int li = i;
            pill(ctx, tr, x + (i % 2) * 106, y + 14 + (i / 2) * 20, 102, 16, Optimization.NAMES[i],
                    lvl == i ? Ui.ACCENT : Ui.ROW, () -> setLevel(li));
        }

        int sy = y + 64;
        ctx.drawTextWithShadow(tr, "Ползунок", x, sy, Ui.DIM);
        String nm = Optimization.NAMES[lvl];
        ctx.drawTextWithShadow(tr, nm, x + 208 - tr.getWidth(nm), sy, Ui.TEXT);
        final int tx = x, tw = 208, ty = sy + 14;
        int fill = (int) (tw * lvl / 3.0);
        Ui.track(ctx, tx, ty, tw, 4);
        Ui.progress(ctx, tx, ty, Math.max(2, fill), 4);
        for (int i = 0; i < 4; i++) Ui.knob(ctx, tx + (int) (tw * i / 3.0) - 1, ty + 5, 3, 3);
        Ui.knob(ctx, tx + fill - 4, ty - 4, 8, 12);
        hits.add(new Hit(tx - 2, ty - 5, tw + 4, 14, null,
                mx -> setLevel((int) Math.round(Math.max(0, Math.min(1, (mx - tx) / (double) tw)) * 3))));

        int iy = ty + 18;
        ctx.drawTextWithShadow(tr, "Сейчас:", x, iy, Ui.DIM);
        iy += 12;
        ctx.drawTextWithShadow(tr, "Частицы мода: " + Optimization.particlePercent() + "%", x, iy, Ui.TEXT);
        iy += 10;
        ctx.drawTextWithShadow(tr, "Сегментов в кольцах: " + Optimization.ringSegments(), x, iy, Ui.TEXT);
        iy += 10;
        ctx.drawTextWithShadow(tr, "Пунктир линий: каждый " + Optimization.lineStride() + "-й пиксель", x, iy, Ui.TEXT);
        iy += 10;
        ctx.drawTextWithShadow(tr, "Чисел урона одновременно: " + Optimization.maxNumbers(), x, iy, Ui.TEXT);
        iy += 10;
        ctx.drawTextWithShadow(tr, "Частиц за тик: до " + Optimization.budgetPerTick(), x, iy, Ui.TEXT);
        iy += 10;
        ctx.drawTextWithShadow(tr, "Сила Item Glow: " + Optimization.glowPercent() + "%", x, iy, Ui.TEXT);
    }

    // ------------------------------------------------------------ раздел «Автокоманды»
    private void drawCmds(DrawContext ctx, TextRenderer tr) {
        int x = centerX + 6;
        int listTop = contentTop + 24;
        List<Config.AutoCommand> cmds = Config.d.commands;
        if (cmds.isEmpty()) ctx.drawTextWithShadow(tr, "Команд пока нет. Добавь ниже.", x + 2, listTop + 5, Ui.DIM);

        int shown = Math.min(cmds.size(), MAX_CMD_ROWS);
        for (int i = 0; i < shown; i++) {
            final int idx = i;
            final Config.AutoCommand c = cmds.get(i);
            int ry = listTop + i * 18;
            Ui.rrect(ctx, x, ry, 208, 16, idx == editIndex ? Ui.ROW_SEL : Ui.ROW);
            Ui.toggle(ctx, x + 4, ry + 3, c.enabled);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(c.command, 84), x + 30, ry + 4, c.enabled ? Ui.TEXT : Ui.DIM);
            Ui.rrect(ctx, x + 118, ry + 2, 62, 12, Ui.OFF);
            ctx.drawCenteredTextWithShadow(tr, tr.trimToWidth(AutoCommands.keyName(c.key), 58), x + 149, ry + 4, Ui.TEXT);
            Ui.rrect(ctx, x + 186, ry + 2, 16, 12, Ui.OFF);
            ctx.drawCenteredTextWithShadow(tr, "x", x + 194, ry + 4, 0xFFFF7070);
            region(x, ry, 118, 16, () -> startEdit(idx));
            region(x + 118, ry + 2, 62, 12, () -> {
                startEdit(idx);
                beginCapture();
            });
            region(x + 4, ry + 3, 20, 10, () -> {
                c.enabled = !c.enabled;
                dirty = true;
            });
            region(x + 186, ry + 2, 16, 12, () -> {
                Config.d.commands.remove(c);
                if (editIndex == idx) clearEdit();
                else if (editIndex > idx) editIndex--;
                dirty = true;
            });
        }
        if (cmds.size() > MAX_CMD_ROWS) {
            ctx.drawTextWithShadow(tr, "...и ещё " + (cmds.size() - MAX_CMD_ROWS), x + 2, listTop + MAX_CMD_ROWS * 18 + 1, Ui.DIM);
        }

        int ey = contentTop + 120;
        ctx.drawTextWithShadow(tr, "Команда", x, ey + 3, Ui.DIM);
        int ky = ey + 20;
        ctx.drawTextWithShadow(tr, "Клавиша", x, ky + 3, Ui.DIM);
        String keyLabel = capturing ? "Нажми клавишу..." : AutoCommands.keyName(capKey);
        pill(ctx, tr, x + 52, ky, 100, 14, tr.trimToWidth(keyLabel, 96), capturing ? Ui.ACCENT : Ui.ROW_SEL, this::beginCapture);

        boolean editing = editIndex >= 0 && editIndex < Config.d.commands.size();
        pill(ctx, tr, x, ky + 20, 104, 14, editing ? "Сохранить" : "Добавить команду", Ui.ACCENT, this::commitCommand);
        pill(ctx, tr, x + 108, ky + 20, 100, 14, editing ? "Отмена" : "Очистить", Ui.ROW_SEL, () -> {
            clearEdit();
            cmdStatus = "";
        });
        if (!cmdStatus.isEmpty()) {
            boolean ok = cmdStatus.equals("Сохранено");
            ctx.drawTextWithShadow(tr, tr.trimToWidth(cmdStatus, 206), x + 2, ky + 38, ok ? 0xFF55FF55 : 0xFFFF6060);
        }
    }

    // ------------------------------------------------------------ метки (справа)
    private void drawRight(DrawContext ctx, TextRenderer tr) {
        Ui.panel(ctx, rightX, contentTop, RW, CONTENT_H);
        ctx.drawTextWithShadow(tr, "Личные метки", rightX + 6, contentTop + 6, Ui.ACCENT);
        ctx.drawTextWithShadow(tr, "Видны только тебе", rightX + 6, contentTop + 22, Ui.DIM);
        Ui.separator(ctx, rightX + 6, contentTop + 16, RW - 12);

        pill(ctx, tr, rightX + 6, contentTop + 62, 55, 14, "Добавить", Ui.ACCENT, this::addWaypoint);
        pill(ctx, tr, rightX + 65, contentTop + 62, 53, 14, "Моя поз.", Ui.ROW_SEL, this::fillHere);
        pill(ctx, tr, rightX + 6, contentTop + 80, RW - 12, 14, "Значок: " + markerIconName(selectedMarkerIcon), Ui.ROW_SEL, this::cycleSelectedMarkerIcon);
        if (!status.isEmpty()) ctx.drawTextWithShadow(tr, tr.trimToWidth(status, RW - 12), rightX + 6, contentTop + 92, 0xFFFF6060);

        List<Waypoint> list = WaypointManager.personal();
        int shown = Math.min(list.size(), MAX_WP);
        for (int i = 0; i < shown; i++) {
            final Waypoint wp = list.get(i);
            int ry = contentTop + 108 + i * 14;
            Ui.rrect(ctx, rightX + 6, ry, RW - 12, 14, Ui.ROW);
            drawMiniMarkerIcon(ctx, wp.icon, rightX + 13, ry + 7);
            String line = wp.name + " " + (int) wp.x + " " + (int) wp.y + " " + (int) wp.z + " " + dimShort(wp.dim);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(line, RW - 46), rightX + 22, ry + 3, Ui.TEXT);
            region(rightX + 6, ry, 14, 14, () -> { wp.icon = nextIcon(wp.icon); WaypointManager.save(); });
            Ui.rrect(ctx, rightX + RW - 22, ry + 1, 14, 12, Ui.OFF);
            ctx.drawCenteredTextWithShadow(tr, "x", rightX + RW - 15, ry + 3, 0xFFFF7070);
            region(rightX + RW - 22, ry + 1, 14, 12, () -> {
                WaypointManager.removePersonal(wp);
                WaypointManager.save();
            });
        }
        if (list.size() > MAX_WP) {
            ctx.drawTextWithShadow(tr, "...и ещё " + (list.size() - MAX_WP), rightX + 6, contentTop + 108 + MAX_WP * 14, Ui.DIM);
        }
    }

    private void drawBottom(DrawContext ctx, TextRenderer tr) {
        int y = contentTop + CONTENT_H + 5;
        pill(ctx, tr, centerX, y, 108, 14, "Оптимизация", page == Page.OPT ? Ui.ACCENT : Ui.ROW_SEL, () -> {
            page = Page.OPT;
            capturing = false;
        });
        pill(ctx, tr, centerX + 112, y, 108, 14, "Автокоманды", page == Page.CMDS ? Ui.ACCENT : Ui.ROW_SEL, () -> {
            page = Page.CMDS;
            capturing = false;
        });
        pill(ctx, tr, centerX, y + 17, 108, 14, "Редактор HUD", Ui.ROW_SEL,
                () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(this)));
        pill(ctx, tr, centerX + 112, y + 17, 108, 14, "Закрыть", Ui.ROW_SEL, this::close);
    }

    // ------------------------------------------------------------ ввод мышью (опрос состояния кнопки)
    private void poll(int mx, int my) {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !wasDown) {
            active = null;
            capturing = false;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                    if (h.click() != null) h.click().run();
                    if (h.drag() != null) h.drag().accept(mx);
                    active = h;
                    break;
                }
            }
        } else if (down && active != null && active.drag() != null) {
            active.drag().accept(mx);
        }
        if (!down && wasDown) {
            active = null;
            if (dirty) {
                Config.save();
                dirty = false;
            }
        }
        wasDown = down;
    }

    @Override
    public void removed() {
        Config.save();
    }
}
