package dev.pvpplus.mixin;

import dev.pvpplus.client.CosmeticsScreen;
import dev.pvpplus.client.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the title-screen controls with Plus Visuals' compact two-column menu. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final String DISCORD_URL = "https://discord.gg/AqkYWH85UQ";

    private TitleScreenMixin(Text title) {
        super(title);
    }

    private int pvPanelWidth() { return Math.min(440, Math.max(280, this.width - 20)); }
    private int pvPanelHeight() { return Math.min(204, Math.max(170, this.height - 16)); }
    private int pvPanelX() { return (this.width - pvPanelWidth()) / 2; }
    private int pvPanelY() { return (this.height - pvPanelHeight()) / 2; }
    private int pvButtonWidth() { return Math.max(112, Math.min(190, (pvPanelWidth() - 50) / 2)); }
    private int pvLeftButtonX() { return this.width / 2 - pvButtonWidth() - 6; }
    private int pvRightButtonX() { return this.width / 2 + 6; }
    private int pvRowOneY() { return pvPanelY() + 70; }
    private int pvRowTwoY() { return pvRowOneY() + 33; }
    private int pvExitY() { return pvRowTwoY() + 35; }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void pvpplus$buildMainMenu(CallbackInfo ci) {
        TitleScreen parent = (TitleScreen) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        this.clearChildren();

        int bw = pvButtonWidth();
        int h = 25;
        int left = pvLeftButtonX();
        int right = pvRightButtonX();
        int rowOne = pvRowOneY();
        int rowTwo = pvRowTwoY();

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Одиночная игра"),
                b -> mc.setScreen(new SelectWorldScreen(parent)))
                .dimensions(left, rowOne, bw, h).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Настройки"),
                b -> mc.setScreen(new OptionsScreen(parent, mc.options)))
                .dimensions(right, rowOne, bw, h).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сетевая игра"),
                b -> mc.setScreen(new MultiplayerScreen(parent)))
                .dimensions(left, rowTwo, bw, h).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Кастомизация"),
                b -> mc.setScreen(new CosmeticsScreen(parent)))
                .dimensions(right, rowTwo, bw, h).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Выйти из игры"),
                b -> mc.scheduleStop())
                .dimensions(this.width / 2 - bw / 2, pvExitY(), bw, h).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Discord"),
                b -> Util.getOperatingSystem().open(DISCORD_URL))
                .dimensions(this.width - 82, this.height - 26, 72, 18).build());
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void pvpplus$renderNeonBackground(DrawContext context, int mouseX, int mouseY,
                                               float deltaTicks, CallbackInfo ci) {
        Ui.background(context, this.width, this.height);
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void pvpplus$skinMainMenu(DrawContext context, int mouseX, int mouseY,
                                      float deltaTicks, CallbackInfo ci) {
        int x = pvPanelX(), y = pvPanelY(), w = pvPanelWidth(), h = pvPanelHeight();
        Ui.panel(context, x, y, w, h);
        Ui.logo(context, x + 17, y + 15, Ui.ACCENT);
        context.drawTextWithShadow(this.textRenderer, "PLUS VISUALS", x + 40, y + 18, Ui.TEXT);
        context.drawTextWithShadow(this.textRenderer, "0.9.8 R  •  VISUAL CLIENT", x + 40, y + 31, Ui.DIM);
        Ui.separator(context, x + 14, y + 53, w - 28);

        for (var child : this.children()) {
            if (child instanceof ButtonWidget button) {
                Ui.renderButton(context, this.textRenderer, button);
            }
        }
        context.drawCenteredTextWithShadow(this.textRenderer, "Minecraft Fabric 1.21.11",
                this.width / 2, y + h - 13, Ui.DIM);
    }
}
