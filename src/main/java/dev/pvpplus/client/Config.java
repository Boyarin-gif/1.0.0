package dev.pvpplus.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {
    /** Одна автокоманда: команда, клавиша (код GLFW), включена ли. */
    public static class AutoCommand {
        public String command = "";
        public int key = 0;
        public boolean enabled = true;
        public transient boolean wasDown = false;
    }

    public static class Data {
        public Map<String, Boolean> on = new HashMap<>();
        public Map<String, Double> num = new HashMap<>();
        public Map<String, int[]> pos = new HashMap<>();
        public List<AutoCommand> commands = new ArrayList<>();
        // Co-op room UI preferences are additive; all older config fields remain unchanged.
        public String coopRoomName = "Наш мир";
        public String coopAdvertisedAddress = "";
        public int coopPort = 25581;
        public int coopAvatar = 0;
        public int coopMaxPlayers = 4;
        public String coopLastCode = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Boolean> ON_CACHE = new HashMap<>();
    private static final Map<String, Double> NUM_CACHE = new HashMap<>();

    public static Data d = new Data();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpplus_config.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f)) {
                Data loaded = GSON.fromJson(r, Data.class);
                if (loaded != null) {
                    d = loaded;
                    if (d.on == null) d.on = new HashMap<>();
                    if (d.num == null) d.num = new HashMap<>();
                    if (d.pos == null) d.pos = new HashMap<>();
                    if (d.commands == null) d.commands = new ArrayList<>();
                    if (d.coopRoomName == null) d.coopRoomName = "Наш мир";
                    if (d.coopAdvertisedAddress == null) d.coopAdvertisedAddress = "";
                    if (d.coopLastCode == null) d.coopLastCode = "";
                    d.coopAvatar = Math.floorMod(d.coopAvatar, 6);
                    if (d.coopPort < 1024 || d.coopPort > 65535) d.coopPort = 25581;
                    if (d.coopMaxPlayers != 2 && d.coopMaxPlayers != 4 && d.coopMaxPlayers != 8 && d.coopMaxPlayers != 16) d.coopMaxPlayers = 4;
                }
            }
        } catch (Exception ignored) {
        }
        ON_CACHE.clear();
        NUM_CACHE.clear();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(d, w);
        } catch (Exception ignored) {
        }
    }

    public static boolean on(String id) {
        Boolean c = ON_CACHE.get(id);
        if (c != null) return c;
        Boolean v = d.on.get(id);
        boolean r = v != null ? v : Features.defOn(id);
        ON_CACHE.put(id, r);
        return r;
    }

    public static void setOn(String id, boolean v) {
        d.on.put(id, v);
        ON_CACHE.put(id, v);
    }

    public static double num(String key) {
        Double c = NUM_CACHE.get(key);
        if (c != null) return c;
        Double v = d.num.get(key);
        double r = v != null ? v : Features.defNum(key);
        NUM_CACHE.put(key, r);
        return r;
    }

    public static void setNum(String key, double v) {
        d.num.put(key, v);
        NUM_CACHE.put(key, v);
    }

    public static int i(String key) { return (int) Math.round(num(key)); }

    public static boolean b(String key) { return num(key) >= 0.5; }

    public static int color(String key) {
        return Features.COLORS[Math.floorMod(i(key), Features.COLORS.length)];
    }

    /** Включена ли панель с таким id (для редактора HUD). */
    public static boolean isOn(String id) {
        if (id.equals("notify")) return on("notify") || on("potionalert");
        return on(id);
    }
}
