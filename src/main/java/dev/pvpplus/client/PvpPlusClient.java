package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.List;

import dev.pvpplus.OpenShulkerPayload;
import dev.pvpplus.PvpPlus;
import dev.pvpplus.ShulkerMenu;
import dev.pvpplus.mixin.HandledScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PvpPlusClient implements ClientModInitializer {
    private static KeyBinding menuKey;
    private static KeyBinding zoomKey;
    private static KeyBinding shulkerKey;
    private static KeyBinding markerKey;
    private static KeyBinding coopKey;
    private static final KeyBinding.Category PVP_VISUALS_CATEGORY =
            KeyBinding.Category.create(Identifier.of(PvpPlus.MOD_ID, "main"));

    private boolean shulkerWasDown = false;
    private boolean zoomActive = false;
    private int savedFov = 70;

    @Override
    public void onInitializeClient() {
        Config.load();
        WaypointManager.load();
        FriendManager.load();

        // Cosmetics: attach to player models on the client (see CosmeticsScreen for the equip UI).
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityType == net.minecraft.entity.EntityType.PLAYER
                    && entityRenderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer<?> playerRenderer) {
                helper.register(new WitchHatFeatureRenderer(playerRenderer));
                helper.register(new DragonWingsFeatureRenderer(playerRenderer));
                helper.register(new GoldCrownFeatureRenderer(playerRenderer));
                helper.register(new KatanaFeatureRenderer(playerRenderer));
            }
        });

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpplus.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, PVP_VISUALS_CATEGORY));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpplus.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, KeyBinding.Category.MISC));
        shulkerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpplus.shulker", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyBinding.Category.MISC));
        markerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpplus.marker", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, KeyBinding.Category.MISC));
        coopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpplus.coop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, PVP_VISUALS_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            CoOpManager.disconnect();
            restoreFov(mc);
            Automation.restoreBrightness(mc);
            Automation.restoreGlint(mc);
            ItemGlowRenderer.restore(mc);
            GameBoost.restore(mc);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player == MinecraftClient.getInstance().player) CombatTracker.onAttack(entity);
            return ActionResult.PASS;
        });

        // Замена ванильных элементов (если функция выключена, рисуется ванильный)
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> (ctx, tick) -> {
            if (Config.on("hearts")) HeartsHud.render(ctx, tick);
            else vanilla.render(ctx, tick);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (ctx, tick) -> {
            if (Config.on("crosshair")) Overlay.crosshair(ctx, tick);
            else vanilla.render(ctx, tick);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (ctx, tick) -> {
            if (!Config.on("potions")) vanilla.render(ctx, tick);
        });

        // Новые элементы
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "world_overlay"), (ctx, tick) -> WorldOverlay.render(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "armor_panel"), (ctx, tick) -> ArmorHud.render(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "waypoints"), (ctx, tick) -> WaypointHud.render(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "overlay"), (ctx, tick) -> Overlay.render(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "fps_ping"), (ctx, tick) -> Panels.fpsPing(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "potions"), (ctx, tick) -> Panels.potions(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "target"), (ctx, tick) -> Panels.target(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "combo"), (ctx, tick) -> Panels.combo(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "killfeed"), (ctx, tick) -> Panels.killFeed(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "cooldowns"), (ctx, tick) -> Panels.cooldowns(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "hotkeys"), (ctx, tick) -> Panels.hotkeys(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "mace"), (ctx, tick) -> Panels.mace(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "totem"), (ctx, tick) -> Panels.totem(ctx, tick));
        HudElementRegistry.addLast(Identifier.of(PvpPlus.MOD_ID, "notify"), (ctx, tick) -> Panels.notifications(ctx, tick));
    }

    public static List<String> hotkeyLines() {
        List<String> l = new ArrayList<>();
        l.add(menuKey.getBoundKeyLocalizedText().getString() + "  Меню");
        l.add(zoomKey.getBoundKeyLocalizedText().getString() + "  Зум");
        l.add(shulkerKey.getBoundKeyLocalizedText().getString() + "  Шалкер");
        l.add(markerKey.getBoundKeyLocalizedText().getString() + "  Метка");
        l.add(coopKey.getBoundKeyLocalizedText().getString() + "  Кооператив");
        return l;
    }

    private void restoreFov(MinecraftClient mc) {
        if (zoomActive) {
            Automation.force(mc.options.getFov(), Integer.valueOf(savedFov));
            zoomActive = false;
        }
    }

    private void tick(MinecraftClient mc) {
        Optimization.beginTick();
        GameBoost.tick(mc);
        CombatTracker.tick(mc);
        WorldOverlay.tick(mc);
        TargetEsp.tick(mc);
        FriendManager.tick(mc);
        CoOpManager.tick();
        ItemGlowRenderer.tick(mc);
        if (mc.player == null) return;
        Effects.tick(mc);
        Automation.tick(mc);
        Panels.updateTotems(mc.player);
        AutoCommands.tick(mc);
        QuickMarker.tick(mc, markerKey);

        while (coopKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new CoOpScreen());
        }

        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new PvpMenuScreen());
        }

        // Зум (держи клавишу)
        boolean wantZoom = Config.on("zoom") && mc.currentScreen == null && zoomKey.isPressed();
        if (wantZoom && !zoomActive) {
            savedFov = mc.options.getFov().getValue();
            Automation.force(mc.options.getFov(), Integer.valueOf(Config.i("zoom.fov")));
            zoomActive = true;
        } else if (!wantZoom && zoomActive) {
            Automation.force(mc.options.getFov(), Integer.valueOf(savedFov));
            zoomActive = false;
        }

        // Авто-респавн
        if (Config.on("autorespawn") && mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            Notifier.push("Возрождение");
        }

        // Шалкер из инвентаря: курсор на шалкер + клавиша
        Screen s = mc.currentScreen;
        boolean down = false;
        if (Config.on("shulker") && s instanceof HandledScreen<?> hs && !(s instanceof CreativeInventoryScreen)) {
            int code = KeyBindingHelper.getBoundKeyOf(shulkerKey).getCode();
            long window = mc.getWindow().getHandle();
            down = code >= 0 && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            if (down && !shulkerWasDown) {
                Slot slot = ((HandledScreenAccessor) hs).pvpplus$getFocusedSlot();
                if (slot != null && ShulkerMenu.isShulker(slot.getStack())) {
                    if (ClientPlayNetworking.canSend(OpenShulkerPayload.ID)) {
                        ClientPlayNetworking.send(new OpenShulkerPayload(slot.id));
                    } else {
                        mc.player.sendMessage(Text.literal("На сервере нет Plus Visual: шалкер не открыть"), true);
                    }
                }
            }
        }
        shulkerWasDown = down;
    }
}
