package dev.pvpplus.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plus Visuals native friend book. Persistent data remains client-local; requests are transported
 * by the existing Plus Visuals room channel and are only delivered to members of that room.
 */
public final class FriendManager {
    public static final class FriendVisualSettings {
        public boolean highlightEnabled = true;
        public boolean hitboxEnabled = true;
        public int hitboxColor = 0xFF36E36B;
        public boolean armorEnabled;
        public int armorColor = 0xFF36A8FF;
        /** 0 helmet, 1 chestplate, 2 leggings, 3 boots. */
        public int armorPart = 1;
    }

    public static final class Data {
        /** Kept for compatibility with the original 5.2.x config format. */
        public List<String> friends = new ArrayList<>();
        /** Legacy global visual settings; retained and used for friends without overrides. */
        public boolean highlightEnabled = true;
        /** 0 = hitbox, 1 = tint the selected slot in the real vanilla armor render pass. */
        public int highlightMode = 0;
        public int hitboxColor = 0xFF36E36B;
        public int armorColor = 0xFF36A8FF;
        public int armorPart = 1;
        /** Requests are stored separately from accepted friends. */
        public List<String> incomingRequests = new ArrayList<>();
        public List<String> outgoingRequests = new ArrayList<>();
        /** Per-friend settings, keyed by Minecraft nickname (case-insensitive lookup). */
        public Map<String, FriendVisualSettings> visualSettings = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pvpplus_friends.json");
    private static Data data = new Data();

    private FriendManager() {}
    public static Data data() { return data; }

    public static void load() {
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d != null) data = d;
            }
        } catch (Exception ignored) { }
        if (data.friends == null) data.friends = new ArrayList<>();
        if (data.incomingRequests == null) data.incomingRequests = new ArrayList<>();
        if (data.outgoingRequests == null) data.outgoingRequests = new ArrayList<>();
        if (data.visualSettings == null) data.visualSettings = new LinkedHashMap<>();
        data.friends.removeIf(s -> !validName(s));
        data.incomingRequests.removeIf(s -> !validName(s));
        data.outgoingRequests.removeIf(s -> !validName(s));
        removeCaseInsensitiveDuplicates(data.friends);
        removeCaseInsensitiveDuplicates(data.incomingRequests);
        removeCaseInsensitiveDuplicates(data.outgoingRequests);
        data.incomingRequests.removeIf(FriendManager::isFriend);
        data.outgoingRequests.removeIf(FriendManager::isFriend);
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(data, w); }
        } catch (Exception ignored) { }
    }

    public static List<String> friends() { return List.copyOf(data.friends); }
    public static List<String> incomingRequests() { return List.copyOf(data.incomingRequests); }
    public static List<String> outgoingRequests() { return List.copyOf(data.outgoingRequests); }

    private static void removeCaseInsensitiveDuplicates(List<String> values) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        values.removeIf(value -> !seen.add(value.toLowerCase(java.util.Locale.ROOT)));
    }

    private static boolean validName(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty() || n.length() > 16) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
                    && !(c >= '0' && c <= '9') && c != '_') return false;
        }
        return true;
    }

    private static boolean containsIgnoreCase(List<String> values, String name) {
        if (values == null || name == null) return false;
        for (String value : values) if (value.equalsIgnoreCase(name)) return true;
        return false;
    }

    public static boolean isFriend(String name) { return containsIgnoreCase(data.friends, name); }
    public static boolean hasIncoming(String name) { return containsIgnoreCase(data.incomingRequests, name); }
    public static boolean hasOutgoing(String name) { return containsIgnoreCase(data.outgoingRequests, name); }

    /** Adds an accepted friend. Kept for old callers and migration compatibility. */
    public static boolean add(String name) {
        if (!validName(name)) return false;
        String n = name.trim();
        if (isFriend(n)) return false;
        data.friends.add(n);
        data.incomingRequests.removeIf(v -> v.equalsIgnoreCase(n));
        data.outgoingRequests.removeIf(v -> v.equalsIgnoreCase(n));
        save();
        return true;
    }

    /** Creates a local outgoing request; it does not silently turn the target into a friend. */
    public static boolean createOutgoingRequest(String name) {
        if (!validName(name)) return false;
        String n = name.trim();
        if (isFriend(n) || hasOutgoing(n) || hasIncoming(n)) return false;
        data.outgoingRequests.add(n);
        save();
        return true;
    }

    /** Called when a request packet reaches its intended recipient. */
    public static void receiveRequest(String name) {
        if (!validName(name)) return;
        MinecraftClient.getInstance().execute(() -> {
            String n = name.trim();
            if (isFriend(n) || hasIncoming(n)) {
                // A duplicate request is explicitly rejected so the sender does not keep
                // a stale outgoing entry waiting forever.
                CoOpManager.respondToFriendRequest(n, false);
                return;
            }
            data.incomingRequests.add(n);
            save();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.sendMessage(Text.literal("Plus Visuals: заявка в друзья от " + n), false);
            CoOpScreen.refreshFriendsPageIfOpen();
        });
    }

    /** Completes an outgoing request on the original sender's client. */
    public static void resolveOutgoing(String name, boolean accepted, String reason) {
        if (!validName(name)) return;
        MinecraftClient.getInstance().execute(() -> {
            String n = name.trim();
            data.outgoingRequests.removeIf(v -> v.equalsIgnoreCase(n));
            if (accepted && !isFriend(n)) data.friends.add(n);
            save();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                String result = accepted ? "Заявка принята: " + n
                        : (reason == null || reason.isBlank() ? "Заявка отклонена: " + n : reason);
                mc.player.sendMessage(Text.literal("Plus Visuals: " + result), false);
            }
            CoOpScreen.refreshFriendsPageIfOpen();
        });
    }

    /** Applies a local accept decision. The network response is sent by the UI caller. */
    public static boolean acceptIncoming(String name) {
        if (name == null || !hasIncoming(name)) return false;
        data.incomingRequests.removeIf(v -> v.equalsIgnoreCase(name));
        data.outgoingRequests.removeIf(v -> v.equalsIgnoreCase(name));
        boolean added = !isFriend(name);
        if (added) data.friends.add(name);
        save();
        return added;
    }

    public static void declineIncoming(String name) {
        if (name == null) return;
        data.incomingRequests.removeIf(v -> v.equalsIgnoreCase(name));
        save();
    }

    public static void remove(String name) {
        if (name == null) return;
        data.friends.removeIf(n -> n.equalsIgnoreCase(name));
        data.incomingRequests.removeIf(n -> n.equalsIgnoreCase(name));
        data.outgoingRequests.removeIf(n -> n.equalsIgnoreCase(name));
        data.visualSettings.keySet().removeIf(n -> n.equalsIgnoreCase(name));
        save();
    }

    public static FriendVisualSettings settingsFor(String name) {
        if (name == null) return new FriendVisualSettings();
        String key = findSettingsKey(name);
        if (key != null) {
            FriendVisualSettings existing = data.visualSettings.get(key);
            if (existing != null) return existing;
        }
        FriendVisualSettings created = new FriendVisualSettings();
        // Migrate the legacy global mode into an independent per-friend starting point.
        created.highlightEnabled = data.highlightEnabled;
        created.hitboxEnabled = data.highlightEnabled && data.highlightMode == 0;
        created.hitboxColor = data.hitboxColor;
        created.armorEnabled = data.highlightEnabled && data.highlightMode == 1;
        created.armorColor = data.armorColor;
        created.armorPart = Math.floorMod(data.armorPart, 4);
        data.visualSettings.put(name, created);
        save();
        return created;
    }

    private static String findSettingsKey(String name) {
        for (String key : data.visualSettings.keySet()) if (key.equalsIgnoreCase(name)) return key;
        return null;
    }

    public static String normalizeColorName(int c) {
        return switch (c) {
            case 0xFFFF4D4D -> "Красный";
            case 0xFFFFFF35 -> "Жёлтый";
            case 0xFF398BFF -> "Синий";
            case 0xFFB45CFF -> "Фиолетовый";
            default -> "Зелёный";
        };
    }

    /** Returns tint for the selected friend's real vanilla armor render pass. */
    public static int armorTintFor(BipedEntityRenderState state, ItemStack stack) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return 0;
        EquipmentSlot slot = stack == playerState.equippedHeadStack ? EquipmentSlot.HEAD
                : stack == playerState.equippedChestStack ? EquipmentSlot.CHEST
                : stack == playerState.equippedLegsStack ? EquipmentSlot.LEGS
                : stack == playerState.equippedFeetStack ? EquipmentSlot.FEET : null;
        if (slot == null) return 0;
        int selectedSlot = switch (slot) {
            case HEAD -> 0; case CHEST -> 1; case LEGS -> 2; case FEET -> 3;
            default -> -1;
        };
        if (selectedSlot < 0) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return 0;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.getId() != playerState.id || player.isRemoved() || !player.isAlive()) continue;
            String name = player.getGameProfile().name();
            if (!isFriend(name)) return 0;
            String key = findSettingsKey(name);
            if (key != null) {
                FriendVisualSettings settings = data.visualSettings.get(key);
                if (settings == null || !settings.highlightEnabled || !settings.armorEnabled
                        || selectedSlot != Math.floorMod(settings.armorPart, 4)) return 0;
                return settings.armorColor;
            }
            // Preserve old global settings for friends with no per-friend override.
            if (!data.highlightEnabled || data.highlightMode != 1 || selectedSlot != Math.floorMod(data.armorPart, 4)) return 0;
            return data.armorColor;
        }
        return 0;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.worldRenderer == null || data.friends.isEmpty()) return;
        Vec3d eye = mc.player.getEyePos();
        try (GizmoDrawing.CollectorScope ignored = mc.worldRenderer.startDrawingGizmos()) {
            for (PlayerEntity target : mc.world.getPlayers()) {
                if (target == mc.player || !isFriend(target.getGameProfile().name())
                        || target.isRemoved() || !target.isAlive()) continue;
                String name = target.getGameProfile().name();
                String key = findSettingsKey(name);
                boolean enabled;
                int color;
                if (key != null) {
                    FriendVisualSettings settings = data.visualSettings.get(key);
                    enabled = settings != null && settings.highlightEnabled && settings.hitboxEnabled;
                    color = settings == null ? data.hitboxColor : settings.hitboxColor;
                } else {
                    enabled = data.highlightEnabled && data.highlightMode == 0;
                    color = data.hitboxColor;
                }
                if (!enabled) continue;
                double distanceSquared = target.getEyePos().squaredDistanceTo(eye);
                if (distanceSquared < 0.01 || distanceSquared > 128.0 * 128.0) continue;
                Box box = target.getBoundingBox().expand(0.015);
                try {
                    GizmoDrawing.box(box, DrawStyle.stroked(color, 1.4F), false).withLifespan(2);
                } catch (Exception ignored) {
                    // One broken target should not interrupt other friend visuals.
                }
            }
        } catch (Exception ignored) {
            // Rendering should never interrupt the client's tick.
        }
    }
}
