package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Separate accepted-friends page with per-friend visual controls. */
public final class FriendsListScreen extends Screen {
    private static final int[] COLORS = {0xFF36E36B, 0xFFFF4D4D, 0xFFFFFF35, 0xFF398BFF, 0xFFB45CFF};
    private String selectedName;
    private int scrollOffset;
    private String message = "Выбери друга, чтобы изменить его визуальные настройки";

    public FriendsListScreen() { this(null, 0); }
    public FriendsListScreen(String selectedName, int scrollOffset) {
        super(Text.literal("Plus Visuals — Друзья"));
        this.selectedName = selectedName;
        this.scrollOffset = Math.max(0, scrollOffset);
    }

    private int panelX() { return (width - Math.min(610, width - 20)) / 2; }
    private int panelY() { return (height - Math.min(330, height - 20)) / 2; }
    private int panelW() { return Math.min(610, width - 20); }
    private int panelH() { return Math.min(330, height - 20); }

    @Override
    protected void init() {
        int x = panelX(), y = panelY(), w = panelW();
        int leftW = Math.max(180, Math.min(250, w / 2 - 16));
        int rightX = x + leftW + 14;
        int rightW = w - leftW - 26;
        List<String> friends = FriendManager.friends();
        if (selectedName == null || friends.stream().noneMatch(n -> n.equalsIgnoreCase(selectedName))) {
            selectedName = friends.isEmpty() ? null : friends.get(0);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b ->
                MinecraftClient.getInstance().setScreen(new CoOpScreen(0))).dimensions(x + 12, y + panelH() - 32, 90, 20).build());

        FriendManager.FriendVisualSettings settings = selectedName == null ? null : FriendManager.settingsFor(selectedName);
        int buttonW = Math.max(110, rightW - 18);
        int bx = rightX + 8;
        int by = y + 80;
        if (settings != null) {
            addSettingButton(bx, by, buttonW, "Подсветка: " + onOff(settings.highlightEnabled), () -> {
                settings.highlightEnabled = !settings.highlightEnabled; FriendManager.save(); refresh();
            });
            addSettingButton(bx, by + 27, buttonW, "Хитбокс: " + onOff(settings.hitboxEnabled), () -> {
                settings.hitboxEnabled = !settings.hitboxEnabled; FriendManager.save(); refresh();
            });
            addSettingButton(bx, by + 54, buttonW, "Цвет хитбокса: " + FriendManager.normalizeColorName(settings.hitboxColor), () -> {
                settings.hitboxColor = nextColor(settings.hitboxColor); FriendManager.save(); refresh();
            });
            addSettingButton(bx, by + 81, buttonW, "Визуальная броня: " + onOff(settings.armorEnabled), () -> {
                settings.armorEnabled = !settings.armorEnabled; FriendManager.save(); refresh();
            });
            addSettingButton(bx, by + 108, buttonW, "Цвет брони: " + FriendManager.normalizeColorName(settings.armorColor), () -> {
                settings.armorColor = nextColor(settings.armorColor); FriendManager.save(); refresh();
            });
            addSettingButton(bx, by + 135, buttonW, "Часть брони: " + armorPartName(settings.armorPart), () -> {
                settings.armorPart = (settings.armorPart + 1) % 4; FriendManager.save(); refresh();
            });
            addDrawableChild(ButtonWidget.builder(Text.literal("Удалить друга"), b -> {
                String removed = selectedName;
                CoOpManager.removeFriend(removed);
                FriendManager.remove(removed);
                selectedName = null;
                message = "Друг удалён: " + removed;
                refresh();
            }).dimensions(bx, by + 162, buttonW, 20).build());
        }
    }

    private void addSettingButton(int x, int y, int w, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run()).dimensions(x, y, w, 20).build());
    }

    private void refresh() { MinecraftClient.getInstance().setScreen(new FriendsListScreen(selectedName, scrollOffset)); }
    private static String onOff(boolean value) { return value ? "ВКЛ" : "ВЫКЛ"; }
    private static String armorPartName(int part) {
        return switch (Math.floorMod(part, 4)) { case 0 -> "Шлем"; case 1 -> "Нагрудник"; case 2 -> "Поножи"; default -> "Ботинки"; };
    }
    private static int nextColor(int current) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == current) return COLORS[(i + 1) % COLORS.length];
        return COLORS[0];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        Ui.panel(context, x, y, w, h);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Друзья Plus Visuals"), width / 2, y + 12, Ui.TEXT);
        int leftW = Math.max(180, Math.min(250, w / 2 - 16));
        int rightX = x + leftW + 14;
        int rightW = w - leftW - 26;
        Ui.panel(context, x + 10, y + 34, leftW, h - 78);
        Ui.panel(context, rightX, y + 34, rightW, h - 78);
        context.drawTextWithShadow(textRenderer, Text.literal("Список друзей · " + FriendManager.friends().size()), x + 20, y + 42, Ui.TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("Индивидуальные настройки"), rightX + 10, y + 42, Ui.TEXT);

        List<String> friends = FriendManager.friends();
        int visible = Math.max(1, (h - 112) / 20);
        scrollOffset = Math.min(scrollOffset, Math.max(0, friends.size() - visible));
        for (int i = 0; i < visible && i + scrollOffset < friends.size(); i++) {
            String name = friends.get(i + scrollOffset);
            int rowY = y + 64 + i * 20;
            boolean selected = selectedName != null && selectedName.equalsIgnoreCase(name);
            Ui.rrect(context, x + 16, rowY - 3, leftW - 12, 18, selected ? Ui.ROW_SEL : (i % 2 == 0 ? Ui.ROW : Ui.ROW_SEL));
            context.drawTextWithShadow(textRenderer, Text.literal(name), x + 24, rowY, selected ? Ui.ACCENT : Ui.TEXT);
            String status = client != null && client.getNetworkHandler() != null
                    && client.getNetworkHandler().getPlayerList().stream()
                    .anyMatch(entry -> entry.getProfile().name().equalsIgnoreCase(name)) ? "В сети" : "Не в сети";
            context.drawTextWithShadow(textRenderer, Text.literal(status), x + leftW - 78, rowY, Ui.DIM);
        }
        if (friends.isEmpty()) context.drawTextWithShadow(textRenderer, Text.literal("Пока нет друзей"), x + 22, y + 72, Ui.DIM);
        if (selectedName == null) {
            context.drawTextWithShadow(textRenderer, Text.literal("Прими заявку или добавь игрока из комнаты."), rightX + 10, y + 78, Ui.DIM);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Настройки для: " + selectedName), rightX + 10, y + 64, Ui.ACCENT);
            context.drawTextWithShadow(textRenderer, Text.literal("Хитбокс и броня меняются независимо для каждого друга."), rightX + 10, y + h - 66, Ui.DIM);
            context.drawTextWithShadow(textRenderer, Text.literal("Цвета и параметры сохраняются локально."), rightX + 10, y + h - 52, Ui.DIM);
        }
        context.drawTextWithShadow(textRenderer, Text.literal(message), x + 116, y + h - 27, Ui.DIM);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() == 0) {
            int x = panelX(), y = panelY(), w = panelW(), h = panelH();
            int leftW = Math.max(180, Math.min(250, w / 2 - 16));
            int visible = Math.max(1, (h - 112) / 20);
            List<String> friends = FriendManager.friends();
            for (int i = 0; i < visible && i + scrollOffset < friends.size(); i++) {
                int rowY = y + 64 + i * 20;
                if (click.x() >= x + 16 && click.x() <= x + leftW && click.y() >= rowY - 4 && click.y() <= rowY + 16) {
                    selectedName = friends.get(i + scrollOffset);
                    refresh();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < xRightBound() && mouseX > panelX() && mouseY > panelY() && mouseY < panelY() + panelH()) {
            scrollOffset = Math.max(0, Math.min(Math.max(0, FriendManager.friends().size() - 1), scrollOffset - (int) Math.signum(verticalAmount) * 3));
            refresh();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int xRightBound() { return panelX() + Math.max(180, Math.min(250, panelW() / 2 - 16)); }
    @Override public boolean shouldPause() { return false; }
}
