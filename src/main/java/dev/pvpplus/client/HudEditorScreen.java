package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    private String dragging = null;
    private int offX, offY;
    private boolean wasDown = true;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Редактор HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HudPanels.editing = true;
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(width / 2 - 102, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить позиции"), b -> {
            HudPanels.resetAll();
            Config.save();
        }).dimensions(width / 2 + 2, height - 28, 100, 20).build());
    }

    // без размытия, чтобы панели были видны
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.background(ctx, width, height);
        Ui.panel(ctx, Math.max(8, width / 2 - 170), 2, Math.min(340, width - 16), 22);
        super.render(ctx, mouseX, mouseY, delta);
        for (var child : children()) {
            if (child instanceof ButtonWidget button) Ui.renderButton(ctx, textRenderer, button);
        }

        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !wasDown) {
            dragging = null;
            for (int i = HudPanels.DEFS.size() - 1; i >= 0; i--) {
                HudPanels.Def d = HudPanels.DEFS.get(i);
                if (!Config.isOn(d.id)) continue;
                int[] r = HudPanels.rect(d.id);
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    dragging = d.id;
                    offX = mouseX - r[0];
                    offY = mouseY - r[1];
                    break;
                }
            }
        }
        if (down && dragging != null) {
            HudPanels.setPos(dragging, mouseX - offX, mouseY - offY);
        }
        if (!down && wasDown && dragging != null) {
            Config.save();
            dragging = null;
        }
        wasDown = down;

        ctx.drawCenteredTextWithShadow(textRenderer, "Тяни панели мышью. Esc или «Готово» - сохранить",
                width / 2, 6, 0xFFFFFFFF);
        for (HudPanels.Def d : HudPanels.DEFS) {
            if (!Config.isOn(d.id)) continue;
            int[] r = HudPanels.rect(d.id);
            int col = d.id.equals(dragging) ? 0xFF50DCFF : 0xB0FFFFFF;
            Gfx.border(ctx, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, col);
            ctx.drawTextWithShadow(textRenderer, d.title, r[0] + 2, r[1] - 10, col);
        }
    }

    @Override
    public void removed() {
        HudPanels.editing = false;
        Config.save();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
