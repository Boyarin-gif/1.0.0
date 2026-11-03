package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Full customization catalogue with a rotatable 3D player skin preview and cosmetic overlays. */
public final class CosmeticsScreen extends Screen {
    private static final int LIST_W = 154;
    private static final int ROW_H = 25;
    private static final int ROW_GAP = 4;

    private final Screen parent;
    private int selected = 0;
    private boolean showSelectedPreview = true;
    private boolean draggingModel;
    private float modelYaw = 18.0F;
    private float modelPitch = 7.0F;
    private float modelScale = 1.0F;
    private String status = "";

    /** Swatch hit boxes from the last render pass: {x, y, w, h, paletteIndex}. */
    private final List<int[]> swatchHits = new ArrayList<>();
    private int previewLeft, previewTop, previewRight, previewBottom;

    public CosmeticsScreen(Screen parent) {
        super(Text.literal("Plus Visual — Кастомизация"));
        this.parent = parent;
    }

    private int panelW() { return Math.min(900, width - 16); }
    private int panelH() { return Math.min(440, height - 16); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }

    @Override
    protected void init() {
        buildWidgets();
        PreviewSkinManager.getModel(MinecraftClient.getInstance());
    }

    private void buildWidgets() {
        clearChildren();
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        int listX = px + 12, listY = py + 54;
        int bottom = py + ph - 12;
        boolean settings = Cosmetics.ALL[selected].hasColor();
        int previewX = px + 178;
        int previewW = Math.max(190, pw - 178 - (settings ? 190 : 22));
        int half = (previewW - 8) / 2;

        for (int i = 0; i < Cosmetics.ALL.length; i++) {
            Cosmetics.Item item = Cosmetics.ALL[i];
            final int idx = i;
            ButtonWidget row = ButtonWidget.builder(rowLabel(item), b -> {
                        selected = idx;
                        showSelectedPreview = true;
                        status = "";
                        buildWidgets();
                    })
                    .dimensions(listX, listY + i * (ROW_H + ROW_GAP), LIST_W, ROW_H).build();
            addDrawableChild(row);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Добавить скин"), b -> {
                    status = PreviewSkinManager.chooseAndLoadSkin(MinecraftClient.getInstance());
                })
                .dimensions(previewX, bottom - 26, half, 22).build());

        Cosmetics.Item item = Cosmetics.ALL[selected];
        addDrawableChild(ButtonWidget.builder(equipLabel(item), b -> {
                    item.setEquipped(!item.isEquipped());
                    showSelectedPreview = item.isEquipped();
                    status = item.isEquipped() ? "Косметика надета" : "Косметика снята";
                    buildWidgets();
                })
                .dimensions(previewX + half + 8, bottom - 26, half, 22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
                .dimensions(listX, bottom - 26, LIST_W, 22).build());
    }

    private Text rowLabel(Cosmetics.Item item) {
        return Text.literal((item.isEquipped() ? "● " : "○ ") + item.label);
    }

    private Text equipLabel(Cosmetics.Item item) {
        return Text.literal(item.isEquipped() ? "Снять" : "Надеть");
    }

    private static String bodyPart(Cosmetics.Item item) {
        return switch (item.id) {
            case "witch_hat", "gold_crown" -> "Голова";
            case "katana", "dragon_wings" -> "Спина";
            default -> "Персонаж";
        };
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Ui.background(context, width, height);
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        Ui.panel(context, px, py, pw, ph);
        Ui.logo(context, px + 12, py + 9, Ui.ACCENT);
        context.drawTextWithShadow(textRenderer, title, px + 34, py + 13, Ui.TEXT);
        Ui.separator(context, px + 12, py + 34, pw - 24);

        Cosmetics.Item item = Cosmetics.ALL[selected];
        boolean hasSettings = item.hasColor();
        int listX = px + 12, listY = py + 54;
        int bottom = py + ph - 12;
        int previewX = px + 178;
        int previewW = Math.max(190, pw - 178 - (hasSettings ? 190 : 22));
        int previewY = py + 62;
        int previewH = ph - 112;

        // Left catalogue.
        context.drawTextWithShadow(textRenderer, "КОСМЕТИКА", listX + 2, py + 41, Ui.ACCENT);
        for (int i = 0; i < Cosmetics.ALL.length; i++) {
            int y = listY + i * (ROW_H + ROW_GAP);
            int color = i == selected ? Ui.ROW_SEL : Ui.ROW;
            Ui.rrect(context, listX, y, LIST_W, ROW_H, color);
            if (i == selected) Ui.rrect(context, listX, y, 3, ROW_H, Ui.ACCENT);
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(Cosmetics.ALL[i].label, LIST_W - 16), listX + 8, y + 8, Ui.TEXT);
        }

        // Preview title and actual 3D model.
        String titleLine = item.label + "  ·  " + bodyPart(item);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(titleLine, previewW - 12),
                previewX + 6, py + 43, Ui.TEXT);
        Ui.rrect(context, previewX, previewY, previewW, previewH, Ui.ROW);
        context.drawTextWithShadow(textRenderer, "ЛКМ: вращение 360° · Колёсико: масштаб",
                previewX + 8, previewY + 7, Ui.DIM);

        previewLeft = previewX + 6;
        previewTop = previewY + 22;
        previewRight = previewX + previewW - 6;
        previewBottom = previewY + previewH - 6;
        try {
            PreviewSkinManager.setVisibleCosmetics(item.id, showSelectedPreview);
            // PlayerSkinGuiElementRenderer treats this as a world-to-GUI scale (not pixels per model cuboid).
            // Keep the full 2.3-block accessory silhouette inside both the height and width of the viewport.
            float fitScale = Math.max(45.0F, Math.min(135.0F,
                    Math.min(previewH * 0.86F / 2.3F, (previewW - 20) * 0.86F / 2.375F)));
            float previewScale = fitScale * modelScale;
            context.addPlayerSkin(
                    PreviewSkinManager.getModel(MinecraftClient.getInstance()),
                    PreviewSkinManager.getTexture(MinecraftClient.getInstance()),
                    previewScale,
                    modelPitch,
                    modelYaw,
                    0.0F,
                    previewLeft, previewTop, previewRight, previewBottom
            );
        } catch (Exception ignored) {
            context.drawCenteredTextWithShadow(textRenderer, "Не удалось отобразить модель",
                    previewX + previewW / 2, previewY + previewH / 2, 0xFFFF7777);
        }

        // Compact settings column appears only for cosmetics that actually have settings.
        swatchHits.clear();
        if (hasSettings) {
            int sx = px + pw - 180;
            int sy = py + 62;
            Ui.rrect(context, sx, sy, 168, 94, Ui.ROW);
            context.drawTextWithShadow(textRenderer, "ЦВЕТ", sx + 10, sy + 10, Ui.ACCENT);
            int current = Math.floorMod(Config.i(item.colorKey), Features.COLORS.length);
            int baseX = sx + 10, baseY = sy + 31;
            for (int slot = 0; slot < Features.COLOR_PALETTE_ORDER.length; slot++) {
                int paletteIndex = Features.COLOR_PALETTE_ORDER[slot];
                int col = slot % 5, row = slot / 5;
                int wx = baseX + col * 29, wy = baseY + row * 25;
                Ui.paletteSwatch(context, paletteIndex, wx, wy, 22, 14, paletteIndex == current);
                swatchHits.add(new int[]{wx, wy, 22, 14, paletteIndex});
            }
            String currentName = Features.COLOR_NAMES[current];
            context.drawTextWithShadow(textRenderer, currentName, sx + 10, sy + 81, Ui.DIM);
        }

        super.render(context, mouseX, mouseY, delta);
        for (var child : children()) {
            if (child instanceof ButtonWidget button) Ui.renderButton(context, textRenderer, button);
        }
        if (!status.isEmpty()) {
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, previewW - 4),
                    previewX, bottom - 3, status.startsWith("Не удалось") || status.startsWith("Нужен") ? 0xFFFF7777 : Ui.DIM);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            for (int[] hit : swatchHits) {
                if (mouseX >= hit[0] && mouseX < hit[0] + hit[2]
                        && mouseY >= hit[1] && mouseY < hit[1] + hit[3]) {
                    Cosmetics.Item item = Cosmetics.ALL[selected];
                    if (item.hasColor()) {
                        Config.setNum(item.colorKey, hit[4]);
                        Config.save();
                    }
                    return true;
                }
            }

            if (mouseX >= previewLeft && mouseX <= previewRight
                    && mouseY >= previewTop && mouseY <= previewBottom) {
                draggingModel = true;
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingModel && click.button() == 0) {
            // In Minecraft 1.21.11 the drag callback supplies movement deltas directly.
            // Horizontal drag spins the model around (front/back/left/right); vertical drag
            // tilts it (top/down views). Pitch is clamped short of the poles so the model
            // doesn't flip upside down when dragged too far up or down.
            modelYaw += (float) (offsetX * 0.75);
            modelPitch = Math.max(-80.0F, Math.min(80.0F, modelPitch - (float) (offsetY * 0.75)));
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= previewLeft && mouseX <= previewRight
                && mouseY >= previewTop && mouseY <= previewBottom && verticalAmount != 0.0) {
            // Keep the initial fit-to-window scale, then allow a modest zoom range.
            modelScale = Math.max(0.65F, Math.min(1.55F, modelScale * (verticalAmount > 0 ? 1.10F : 0.90F)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && draggingModel) {
            draggingModel = false;
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
