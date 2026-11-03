package dev.pvpplus.client;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Keeps personal and cooperative waypoints in separate collections.
 * The on-disk format stays a single JSON list so existing installations migrate automatically.
 */
public final class WaypointManager {
    public static class Waypoint {
        public String name;
        public double x, y, z;
        public String dim;
        public boolean quick;
        /** Non-null only for markers assigned an identity in the cooperative protocol. */
        public String coopId;
        /** "personal" or "coop"; coopId is also accepted for migration from older versions. */
        public String scope = "personal";
        /** Optional entity UUID for a marker that follows a living entity. */
        public String entityUuid;
        /** Minimal marker icon key: pin, home, chest, parachute, sword. */
        public String icon = "pin";

        public boolean isCooperative() {
            return quick || coopId != null || "coop".equals(scope);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Waypoint> PERSONAL = new ArrayList<>();
    private static final List<Waypoint> COOPERATIVE = new ArrayList<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpplus_waypoints.json");
    }

    /** Private markers created from the K menu. They are never sent over the co-op connection. */
    public static List<Waypoint> personal() {
        return PERSONAL;
    }

    /** Shared markers created in the co-op page or by the quick-marker key. */
    public static List<Waypoint> cooperative() {
        return COOPERATIVE;
    }

    /** Read-only-by-convention combined snapshot for the in-world HUD. */
    public static List<Waypoint> all() {
        List<Waypoint> combined = new ArrayList<>(PERSONAL.size() + COOPERATIVE.size());
        combined.addAll(PERSONAL);
        combined.addAll(COOPERATIVE);
        return combined;
    }

    public static void addPersonal(Waypoint waypoint) {
        if (waypoint == null) return;
        waypoint.scope = "personal";
        waypoint.coopId = null;
        PERSONAL.add(waypoint);
    }

    public static void addCooperative(Waypoint waypoint) {
        if (waypoint == null) return;
        waypoint.scope = "coop";
        COOPERATIVE.add(waypoint);
    }

    public static void removePersonal(Waypoint waypoint) {
        PERSONAL.remove(waypoint);
    }

    public static void removeCooperative(Waypoint waypoint) {
        COOPERATIVE.remove(waypoint);
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f)) {
                Type t = new TypeToken<List<Waypoint>>() {}.getType();
                List<Waypoint> loaded = GSON.fromJson(r, t);
                if (loaded != null) {
                    PERSONAL.clear();
                    COOPERATIVE.clear();
                    for (Waypoint waypoint : loaded) {
                        if (waypoint == null) continue;
                        if (waypoint.icon == null || waypoint.icon.isBlank()) waypoint.icon = "pin";
                        if (waypoint.isCooperative()) {
                            waypoint.scope = "coop";
                            COOPERATIVE.add(waypoint);
                        } else {
                            waypoint.scope = "personal";
                            waypoint.coopId = null;
                            PERSONAL.add(waypoint);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            List<Waypoint> combined = all();
            try (Writer w = Files.newBufferedWriter(f)) {
                GSON.toJson(combined, w);
            }
        } catch (Exception ignored) {
        }
    }
}
