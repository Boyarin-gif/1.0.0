package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Автокоманды: команда выполняется по нажатию назначенной клавиши штатной отправкой команды клиента. */
public final class AutoCommands {
    /** Коды клавиш, которые можно назначить (только существующие коды GLFW, без Esc и модификаторов). */
    public static final int[] KEYS = buildKeys();

    private static final Map<Integer, String> NAMES = new HashMap<>();

    private static int[] buildKeys() {
        List<Integer> l = new ArrayList<>();
        l.add(GLFW.GLFW_KEY_SPACE);
        l.add(GLFW.GLFW_KEY_APOSTROPHE);
        for (int k = GLFW.GLFW_KEY_COMMA; k <= GLFW.GLFW_KEY_9; k++) l.add(k);
        l.add(GLFW.GLFW_KEY_SEMICOLON);
        l.add(GLFW.GLFW_KEY_EQUAL);
        for (int k = GLFW.GLFW_KEY_A; k <= GLFW.GLFW_KEY_RIGHT_BRACKET; k++) l.add(k);
        l.add(GLFW.GLFW_KEY_GRAVE_ACCENT);
        for (int k = GLFW.GLFW_KEY_ENTER; k <= GLFW.GLFW_KEY_END; k++) l.add(k);
        for (int k = GLFW.GLFW_KEY_CAPS_LOCK; k <= GLFW.GLFW_KEY_PAUSE; k++) l.add(k);
        for (int k = GLFW.GLFW_KEY_F1; k <= GLFW.GLFW_KEY_F25; k++) l.add(k);
        for (int k = GLFW.GLFW_KEY_KP_0; k <= GLFW.GLFW_KEY_KP_EQUAL; k++) l.add(k);
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    public static String keyName(int code) {
        if (code <= 0) return "не задана";
        String s = NAMES.get(code);
        if (s == null) {
            s = InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
            NAMES.put(code, s);
        }
        return s;
    }

    /** Возвращает текст конфликта или null, если клавиша свободна. */
    public static String conflict(MinecraftClient mc, int key, int ignoreIdx) {
        List<Config.AutoCommand> list = Config.d.commands;
        for (int i = 0; i < list.size(); i++) {
            if (i != ignoreIdx && list.get(i).key == key) return "Занята другой автокомандой";
        }
        for (KeyBinding kb : mc.options.allKeys) {
            InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
            if (k.getCategory() == InputUtil.Type.KEYSYM && k.getCode() == key) return "Занята в настройках управления";
        }
        return null;
    }

    public static void tick(MinecraftClient mc) {
        List<Config.AutoCommand> list = Config.d.commands;
        if (list.isEmpty()) return;
        boolean canFire = mc.player != null && mc.getNetworkHandler() != null && mc.currentScreen == null;
        long handle = mc.getWindow().getHandle();
        for (int i = 0; i < list.size(); i++) {
            Config.AutoCommand c = list.get(i);
            if (!c.enabled || c.key <= 0 || c.command.isBlank()) {
                c.wasDown = false;
                continue;
            }
            boolean down = canFire && GLFW.glfwGetKey(handle, c.key) == GLFW.GLFW_PRESS;
            if (down && !c.wasDown) run(mc, c.command);
            c.wasDown = down;
        }
    }

    private static void run(MinecraftClient mc, String cmd) {
        String s = cmd.trim();
        if (s.startsWith("/")) s = s.substring(1);
        if (s.isEmpty()) return;
        // штатная отправка команды: серверные права и ограничения применяются как обычно
        mc.getNetworkHandler().sendChatCommand(s);
        Notifier.push("/" + s);
    }
}
