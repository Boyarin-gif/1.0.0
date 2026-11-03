package dev.pvpplus.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent TCP channel for co-op data. Room codes encode a host endpoint; they do not relay traffic. */
public final class CoOpManager {
    private static final Gson GSON = new Gson();
    private static final ExecutorService IO = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pvpplus-coop-io"); t.setDaemon(true); return t;
    });
    private static final Set<Peer> PEERS = ConcurrentHashMap.newKeySet();
    private static final List<ChatMessage> CHAT = new CopyOnWriteArrayList<>();
    public record ChatMessage(String sender, String message, long timestamp) {}
    private static final ConcurrentHashMap<Peer, String> PEER_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Peer, Boolean> PEER_COMPATIBILITY = new ConcurrentHashMap<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile ServerSocket server;
    private static volatile Peer upstream;
    private static volatile boolean upstreamCompatible;
    private static volatile String status = "Отключено";
    private static volatile boolean host;
    private static volatile String roomName = "Нет активной комнаты";
    private static volatile int roomAvatar;
    private static volatile int roomMaxPlayers = 4;
    private static volatile int roomPlayers;
    private static volatile String roomCode = "";
    private static volatile List<String> roomMembers = List.of();
    private static int markerSyncTick;

    private CoOpManager() {}

    public static String status() { return status; }
    public static boolean isHost() { return host; }
    public static boolean isConnected() { return host || upstream != null; }
    public static String roomName() { return roomName; }
    public static int roomAvatar() { return roomAvatar; }
    public static int maxPlayers() { return roomMaxPlayers; }
    public static int playerCount() { return roomPlayers; }
    public static String roomCode() { return roomCode; }
    public static List<String> members() { return List.copyOf(roomMembers); }
    /** Room data features are allowed only after exact Plus Visuals file-fingerprint match. */
    public static boolean isCoOpCompatible() { return PlusVisualFingerprint.available() && (host || (upstream != null && upstreamCompatible)); }
    public static List<ChatMessage> chatMessages() { return List.copyOf(CHAT); }

    /** Private room chat. Messages are never forwarded to Minecraft's public server chat. */
    public static boolean sendChat(String rawMessage) {
        if (!isCoOpCompatible() || rawMessage == null) return false;
        String message = safeChatMessage(rawMessage);
        if (message.isBlank()) return false;
        addChatMessage(currentUsername(), message);
        JsonObject packet = packet("chat", "");
        packet.addProperty("from", currentUsername());
        packet.addProperty("message", message);
        if (host) broadcastCompatible(packet, null);
        else send(upstream, packet);
        return true;
    }

    private static void handleChat(Peer sender, JsonObject packet) {
        if (host) {
            if (!PEER_COMPATIBILITY.getOrDefault(sender, false)) return;
            String from = PEER_NAMES.getOrDefault(sender, "Игрок");
            String message = safeChatMessage(packet.has("message") ? packet.get("message").getAsString() : "");
            if (message.isBlank()) return;
            addChatMessage(from, message);
            JsonObject forwarded = packet("chat", "");
            forwarded.addProperty("from", from);
            forwarded.addProperty("message", message);
            broadcastCompatible(forwarded, sender);
        } else if (upstreamCompatible) {
            String from = packet.has("from") ? safePlayerName(packet.get("from").getAsString()) : "Игрок";
            String message = safeChatMessage(packet.has("message") ? packet.get("message").getAsString() : "");
            if (!message.isBlank()) addChatMessage(from, message);
        }
    }

    private static String safeChatMessage(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\r\\n]", " ").trim();
        return cleaned.substring(0, Math.min(280, cleaned.length()));
    }

    private static void addChatMessage(String sender, String message) {
        CHAT.add(new ChatMessage(safePlayerName(sender), message, System.currentTimeMillis()));
        while (CHAT.size() > 100) CHAT.remove(0);
    }

    /** Sends a friend request through the room host to a named room member. */
    public static boolean sendFriendRequest(String targetName) {
        String target = safePlayerName(targetName);
        if (!isConnected() || target.equalsIgnoreCase(currentUsername())) return false;
        JsonObject request = packet("friendRequest", "");
        request.addProperty("from", currentUsername());
        request.addProperty("to", target);
        if (host) {
            Peer recipient = findPeerByName(target);
            if (recipient == null) return false;
            send(recipient, request);
        } else {
            Peer connection = upstream;
            if (connection == null) return false;
            send(connection, request);
        }
        return true;
    }

    /** Responds to a received request; friendship is created only after an explicit accept. */
    public static boolean respondToFriendRequest(String requesterName, boolean accepted) {
        String requester = safePlayerName(requesterName);
        if (!isConnected()) return false;
        if (host) {
            Peer recipient = findPeerByName(requester);
            if (recipient == null) return false;
            JsonObject result = packet("friendResult", "");
            result.addProperty("peer", currentUsername());
            result.addProperty("accepted", accepted);
            result.addProperty("reason", accepted ? "" : "Заявка отклонена: " + currentUsername());
            send(recipient, result);
        } else {
            Peer connection = upstream;
            if (connection == null) return false;
            JsonObject response = packet("friendResponse", "");
            response.addProperty("to", requester);
            response.addProperty("accepted", accepted);
            send(connection, response);
        }
        return true;
    }

    /** Removes a reciprocal friend relationship on the remote participant when present in this room. */
    public static void removeFriend(String targetName) {
        if (!isConnected() || targetName == null || targetName.isBlank()) return;
        String target = safePlayerName(targetName);
        JsonObject removal = packet("friendRemove", "");
        removal.addProperty("from", currentUsername());
        removal.addProperty("to", target);
        if (host) {
            if (target.equalsIgnoreCase(currentUsername())) return;
            Peer recipient = findPeerByName(target);
            if (recipient != null) send(recipient, removal);
        } else if (upstream != null) {
            send(upstream, removal);
        }
    }

    private static void handleFriendRemoval(Peer sender, JsonObject removal) {
        if (!host) {
            String from = removal.has("from") ? safePlayerName(removal.get("from").getAsString()) : "Игрок";
            String to = removal.has("to") ? safePlayerName(removal.get("to").getAsString()) : "";
            if (to.equalsIgnoreCase(currentUsername())) {
                FriendManager.remove(from);
                notifyClient("Plus Visuals: дружба с " + from + " прекращена");
            }
            return;
        }
        String from = PEER_NAMES.getOrDefault(sender, "Игрок");
        String to = removal.has("to") ? safePlayerName(removal.get("to").getAsString()) : "";
        if (to.equalsIgnoreCase(currentUsername())) {
            FriendManager.remove(from);
            notifyClient("Plus Visuals: дружба с " + from + " прекращена");
            return;
        }
        Peer recipient = findPeerByName(to);
        if (recipient != null) {
            JsonObject forwarded = packet("friendRemove", "");
            forwarded.addProperty("from", from);
            forwarded.addProperty("to", to);
            send(recipient, forwarded);
        }
    }

    private static Peer findPeerByName(String name) {
        for (var entry : PEER_NAMES.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        }
        return null;
    }

    private static void handleFriendRequest(Peer sender, JsonObject packet) {
        if (!host) {
            String from = packet.has("from") ? packet.get("from").getAsString() : "Игрок";
            FriendManager.receiveRequest(safePlayerName(from));
            return;
        }
        String from = PEER_NAMES.getOrDefault(sender, "Игрок");
        String to = packet.has("to") ? safePlayerName(packet.get("to").getAsString()) : "";
        if (to.isBlank()) return;
        if (to.equalsIgnoreCase(currentUsername())) {
            FriendManager.receiveRequest(from);
            return;
        }
        Peer recipient = findPeerByName(to);
        if (recipient == null) {
            JsonObject failure = packet("friendResult", "");
            failure.addProperty("peer", to);
            failure.addProperty("accepted", false);
            failure.addProperty("reason", "Игрок не найден среди участников комнаты: " + to);
            send(sender, failure);
            return;
        }
        JsonObject forwarded = packet("friendRequest", "");
        forwarded.addProperty("from", from);
        forwarded.addProperty("to", to);
        send(recipient, forwarded);
    }

    private static void handleFriendResponse(Peer sender, JsonObject response) {
        if (!host) return;
        String responder = PEER_NAMES.getOrDefault(sender, "Игрок");
        String requester = response.has("to") ? safePlayerName(response.get("to").getAsString()) : "";
        boolean accepted = response.has("accepted") && response.get("accepted").getAsBoolean();
        if (requester.isBlank()) return;
        if (requester.equalsIgnoreCase(currentUsername())) {
            FriendManager.resolveOutgoing(responder, accepted, accepted ? "" : "Заявка отклонена: " + responder);
            return;
        }
        Peer recipient = findPeerByName(requester);
        if (recipient != null) {
            JsonObject result = packet("friendResult", "");
            result.addProperty("peer", responder);
            result.addProperty("accepted", accepted);
            result.addProperty("reason", accepted ? "" : "Заявка отклонена: " + responder);
            send(recipient, result);
        }
    }

    private static void handleFriendResult(JsonObject result) {
        String peer = result.has("peer") ? safePlayerName(result.get("peer").getAsString()) : "Игрок";
        boolean accepted = result.has("accepted") && result.get("accepted").getAsBoolean();
        String reason = result.has("reason") ? result.get("reason").getAsString() : "";
        FriendManager.resolveOutgoing(peer, accepted, reason);
    }

    /** Backwards-compatible entry point for the previous IP/port screen. */
    public static void host(int port) { startHost(port, "Моя комната", 0, 8, ""); }

    /** Starts a TCP host with visible room metadata and an enforced maximum player count. */
    public static synchronized boolean startHost(int port, String name, int avatar, int maximumPlayers) {
        return startHost(port, name, avatar, maximumPlayers, "");
    }

    /** Starts a room and shares its room-code string with each connected member. */
    public static synchronized boolean startHost(int port, String name, int avatar, int maximumPlayers, String inviteCode) {
        disconnect();
        if (port < 1024 || port > 65535) {
            status = "Порт должен быть от 1024 до 65535";
            notifyClient(status);
            return false;
        }
        roomName = safeRoomName(name);
        roomAvatar = Math.floorMod(avatar, 6);
        roomCode = inviteCode == null ? "" : inviteCode.trim();
        roomMaxPlayers = validMaxPlayers(maximumPlayers);
        roomPlayers = 1;
        String ownName = currentUsername();
        roomMembers = List.of(ownName);
        try {
            ServerSocket listening = new ServerSocket();
            listening.setReuseAddress(true);
            listening.bind(new InetSocketAddress(port));
            server = listening;
            RUNNING.set(true);
            host = true;
            status = "Хост: порт " + port;
            IO.execute(() -> {
                while (RUNNING.get() && !listening.isClosed()) {
                    Socket incoming = null;
                    try {
                        incoming = listening.accept();
                        if (!RUNNING.get() || listening.isClosed()) { incoming.close(); break; }
                        if (PEERS.size() >= roomMaxPlayers - 1) {
                            incoming.close();
                            notifyClient("Кооператив: комната заполнена (" + roomMaxPlayers + ")");
                            continue;
                        }
                        attach(incoming, false);
                        incoming = null; // ownership passes to Peer
                        broadcastRoomStatus();
                    } catch (Exception e) {
                        if (incoming != null) try { incoming.close(); } catch (Exception ignored) { }
                        if (RUNNING.get()) status = "Ошибка ожидания подключения";
                    }
                }
            });
            notifyClient("Кооператив: комната «" + roomName + "» создана на порту " + port);
            return true;
        } catch (Exception e) {
            RUNNING.set(false); host = false;
            status = "Не удалось открыть порт " + port;
            roomPlayers = 0; roomMembers = List.of();
            notifyClient(status);
            return false;
        }
    }

    public static synchronized void connect(String address, int port) {
        disconnect();
        upstreamCompatible = false;
        String target = address == null ? "" : address.trim();
        if (target.isEmpty() || port < 1024 || port > 65535) {
            status = "Неверный адрес или порт";
            notifyClient(status);
            return;
        }
        status = "Подключение...";
        IO.execute(() -> {
            try {
                Socket socket = new Socket();
                RUNNING.set(true); host = false;
                socket.connect(new InetSocketAddress(target, port), 7000);
                Peer peer = attach(socket, true); upstream = peer;
                status = "Подключено к комнате";
                JsonObject hello = packet("hello", currentUsername());
                hello.addProperty("modVersion", "1.0.0");
                hello.addProperty("fingerprint", PlusVisualFingerprint.value());
                send(peer, hello);
                notifyClient("Кооператив: подключено к " + target + ":" + port);
            } catch (Exception e) {
                RUNNING.set(false);
                host = false;
                status = "Ошибка подключения";
                roomPlayers = 0; roomMembers = List.of();
                Peer failedUpstream = upstream;
                upstream = null;
                if (failedUpstream != null) close(failedUpstream);
                notifyClient("Кооператив: подключиться не удалось. Проверьте доступность IP/порта или VPN.");
            }
        });
    }

    private static Peer attach(Socket socket, boolean clientSide) throws Exception {
        socket.setKeepAlive(true); socket.setTcpNoDelay(true);
        Peer p = new Peer(socket, new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)),
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
        PEERS.add(p);
        if (clientSide) upstream = p;
        IO.execute(() -> readLoop(p));
        if (!clientSide && host) {
            PEER_NAMES.put(p, "Подключение...");
            sendRoomInfo(p);
            broadcastRoomStatus();
        }
        return p;
    }

    private static void readLoop(Peer peer) {
        try {
            String line;
            while (RUNNING.get() && (line = peer.in.readLine()) != null) {
                if (line.length() > 8192) continue;
                JsonObject obj;
                try { obj = GSON.fromJson(line, JsonObject.class); } catch (Exception ex) { continue; }
                if (obj == null || !obj.has("type")) continue;
                String type;
                try { type = obj.get("type").getAsString(); } catch (Exception ignored) { continue; }
                if ("hello".equals(type)) {
                    if (host) {
                        String name = obj.has("value") ? obj.get("value").getAsString() : "Игрок";
                        PEER_NAMES.put(peer, safePlayerName(name));
                        String remoteFingerprint = obj.has("fingerprint") ? obj.get("fingerprint").getAsString() : "";
                        boolean compatible = PlusVisualFingerprint.available()
                                && remoteFingerprint.matches("[0-9a-f]{64}")
                                && PlusVisualFingerprint.value().equals(remoteFingerprint);
                        PEER_COMPATIBILITY.put(peer, compatible);
                        sendRoomInfo(peer);
                        if (compatible) sendExistingMarkers(peer);
                        else notifyClient("Кооператив: несовместимая версия Plus Visuals у " + safePlayerName(name));
                        broadcastRoomStatus();
                    }
                    continue;
                }
                if ("roomInfo".equals(type) && !host) {
                    applyRoomInfo(obj);
                    continue;
                }
                if ("roomStatus".equals(type) && !host) {
                    applyRoomStatus(obj);
                    continue;
                }
                if ("friendRequest".equals(type)) {
                    handleFriendRequest(peer, obj);
                    continue;
                }
                if ("chat".equals(type)) {
                    handleChat(peer, obj);
                    continue;
                }
                if ("friendResponse".equals(type) && host) {
                    handleFriendResponse(peer, obj);
                    continue;
                }
                if ("friendResult".equals(type) && !host) {
                    handleFriendResult(obj);
                    continue;
                }
                if ("friendRemove".equals(type)) {
                    handleFriendRemoval(peer, obj);
                    continue;
                }
                if ("marker".equals(type) && host) {
                    if (PEER_COMPATIBILITY.getOrDefault(peer, false) && validMarker(obj)) {
                        applyMarker(obj);
                        broadcastCompatible(obj, peer);
                    }
                } else if ("marker".equals(type) && !host) {
                    if (upstreamCompatible) applyMarker(obj);
                }
            }
        } catch (Exception ignored) {
        } finally {
            PEERS.remove(peer);
            PEER_NAMES.remove(peer);
            PEER_COMPATIBILITY.remove(peer);
            close(peer);
            if (peer == upstream) {
                upstream = null;
                if (RUNNING.get()) {
                    status = "Соединение потеряно";
                    roomPlayers = 0; roomMembers = List.of();
                    notifyClient(status);
                }
            }
            if (host && RUNNING.get()) broadcastRoomStatus();
        }
    }

    private static void sendRoomInfo(Peer peer) {
        JsonObject o = packet("roomInfo", "");
        o.addProperty("name", roomName);
        o.addProperty("avatar", roomAvatar);
        o.addProperty("maxPlayers", roomMaxPlayers);
        o.addProperty("code", roomCode);
        o.addProperty("compatible", PEER_COMPATIBILITY.getOrDefault(peer, false));
        send(peer, o);
    }

    private static void broadcastRoomStatus() {
        if (!host) return;
        List<String> members = new ArrayList<>();
        members.add(currentUsername());
        for (Peer peer : PEERS) members.add(PEER_NAMES.getOrDefault(peer, "Участник"));
        int count = Math.min(roomMaxPlayers, members.size());
        roomPlayers = count;
        roomMembers = List.copyOf(members.subList(0, count));
        JsonObject o = packet("roomStatus", "");
        o.addProperty("players", count);
        o.addProperty("maxPlayers", roomMaxPlayers);
        JsonArray array = new JsonArray();
        for (String member : roomMembers) array.add(member);
        o.add("members", array);
        broadcast(o, null);
    }

    private static void applyRoomInfo(JsonObject o) {
        try {
            if (o.has("name")) roomName = safeRoomName(o.get("name").getAsString());
            if (o.has("avatar")) roomAvatar = Math.floorMod(o.get("avatar").getAsInt(), 6);
            if (o.has("maxPlayers")) roomMaxPlayers = validMaxPlayers(o.get("maxPlayers").getAsInt());
            if (o.has("code")) roomCode = o.get("code").getAsString();
            upstreamCompatible = o.has("compatible") && o.get("compatible").getAsBoolean();
            status = upstreamCompatible ? "Подключено к «" + roomName + "»"
                    : "Несовместимая версия Plus Visuals";
        } catch (Exception ignored) { }
    }

    private static void applyRoomStatus(JsonObject o) {
        try {
            if (o.has("players")) roomPlayers = Math.max(1, Math.min(16, o.get("players").getAsInt()));
            if (o.has("maxPlayers")) roomMaxPlayers = validMaxPlayers(o.get("maxPlayers").getAsInt());
            if (o.has("members") && o.get("members").isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (var element : o.getAsJsonArray("members")) {
                    if (element.isJsonPrimitive()) names.add(safePlayerName(element.getAsString()));
                }
                roomMembers = List.copyOf(names);
            }
        } catch (Exception ignored) { }
    }

    private static int validMaxPlayers(int value) {
        return value == 2 || value == 4 || value == 8 || value == 16 ? value : 4;
    }

    private static String safeRoomName(String name) {
        String value = name == null ? "Наш мир" : name.replaceAll("[\\r\\n]", " ").trim();
        if (value.isBlank()) value = "Наш мир";
        return value.substring(0, Math.min(32, value.length()));
    }

    private static String safePlayerName(String name) {
        String value = name == null ? "Игрок" : name.replaceAll("[\\r\\n]", " ").trim();
        if (value.isBlank()) value = "Игрок";
        return value.substring(0, Math.min(16, value.length()));
    }

    private static String currentUsername() {
        try { return safePlayerName(MinecraftClient.getInstance().getSession().getUsername()); }
        catch (Exception ignored) { return "Игрок"; }
    }

    private static boolean validMarker(JsonObject o) {
        try {
            return o.has("id") && o.get("id").getAsString().length() <= 64
                    && o.has("name") && o.get("name").getAsString().length() <= 48
                    && o.has("dim") && o.get("dim").getAsString().length() <= 96
                    && o.has("x") && Math.abs(o.get("x").getAsDouble()) < 30_000_000
                    && o.has("z") && Math.abs(o.get("z").getAsDouble()) < 30_000_000
                    && o.has("y") && Math.abs(o.get("y").getAsDouble()) < 4096;
        } catch (Exception e) { return false; }
    }

    private static void applyMarker(JsonObject o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (!validMarker(o)) return;
            String id = o.get("id").getAsString();
            String action = o.has("action") ? o.get("action").getAsString() : "upsert";
            if ("remove".equals(action)) {
                WaypointManager.cooperative().removeIf(existing -> id.equals(existing.coopId));
                WaypointManager.save();
                return;
            }
            for (WaypointManager.Waypoint existing : WaypointManager.cooperative()) {
                if (id.equals(existing.coopId)) {
                    existing.name = o.get("name").getAsString();
                    existing.x = o.get("x").getAsDouble(); existing.y = o.get("y").getAsDouble(); existing.z = o.get("z").getAsDouble();
                    existing.dim = o.get("dim").getAsString();
                    existing.entityUuid = o.has("entityUuid") && !o.get("entityUuid").isJsonNull() ? o.get("entityUuid").getAsString() : null;
                    existing.icon = o.has("icon") ? o.get("icon").getAsString() : "pin";
                    existing.scope = "coop";
                    WaypointManager.save();
                    return;
                }
            }
            WaypointManager.Waypoint w = new WaypointManager.Waypoint();
            w.coopId = id; w.name = o.get("name").getAsString();
            w.x = o.get("x").getAsDouble(); w.y = o.get("y").getAsDouble(); w.z = o.get("z").getAsDouble();
            w.dim = o.get("dim").getAsString(); w.quick = false;
            w.entityUuid = o.has("entityUuid") && !o.get("entityUuid").isJsonNull() ? o.get("entityUuid").getAsString() : null;
            w.icon = o.has("icon") ? o.get("icon").getAsString() : "pin";
            WaypointManager.addCooperative(w); WaypointManager.save();
        });
    }

    /** Periodically publishes updated positions for markers attached to moving entities. */
    public static void tick() {
        if (!isCoOpCompatible()) return;
        if (++markerSyncTick % 5 != 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        for (WaypointManager.Waypoint w : WaypointManager.cooperative()) {
            if (w.coopId == null || w.entityUuid == null || w.entityUuid.isBlank()) continue;
            for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
                if (!w.entityUuid.equals(e.getUuidAsString())) continue;
                w.x = e.getX(); w.y = e.getY(); w.z = e.getZ();
                JsonObject packet = markerPacket(w, "upsert");
                if (host) broadcastCompatible(packet, null); else send(upstream, packet);
                break;
            }
        }
    }

    public static void shareMarker(WaypointManager.Waypoint w) {
        if (w == null || w.coopId != null || !isCoOpCompatible()) return;
        w.scope = "coop";
        w.coopId = java.util.UUID.randomUUID().toString();
        JsonObject o = markerPacket(w, "upsert");
        if (host) broadcastCompatible(o, null); else send(upstream, o);
    }

    public static void updateSharedMarker(WaypointManager.Waypoint w) {
        if (w == null || w.coopId == null || !isCoOpCompatible()) return;
        JsonObject o = markerPacket(w, "upsert");
        if (host) broadcastCompatible(o, null); else send(upstream, o);
    }

    public static void removeSharedMarker(WaypointManager.Waypoint w) {
        if (w == null || w.coopId == null || !isCoOpCompatible()) return;
        JsonObject o = markerPacket(w, "remove");
        if (host) broadcastCompatible(o, null); else send(upstream, o);
    }

    private static JsonObject markerPacket(WaypointManager.Waypoint w, String action) {
        JsonObject o = packet("marker", "");
        o.addProperty("action", action);
        o.addProperty("id", w.coopId);
        String name = w.name == null ? "Метка" : w.name;
        o.addProperty("name", name.substring(0, Math.min(48, name.length())));
        o.addProperty("x", w.x); o.addProperty("y", w.y); o.addProperty("z", w.z);
        o.addProperty("dim", w.dim == null ? "minecraft:overworld" : w.dim);
        if (w.entityUuid != null) o.addProperty("entityUuid", w.entityUuid); else o.add("entityUuid", null);
        o.addProperty("icon", w.icon == null ? "pin" : w.icon);
        return o;
    }

    private static void sendExistingMarkers(Peer peer) {
        if (!PEER_COMPATIBILITY.getOrDefault(peer, false)) return;
        MinecraftClient.getInstance().execute(() -> {
            for (WaypointManager.Waypoint w : WaypointManager.cooperative()) {
                if (w.coopId == null) w.coopId = java.util.UUID.randomUUID().toString();
                w.scope = "coop";
                send(peer, markerPacket(w, "upsert"));
            }
            WaypointManager.save();
        });
    }

    private static JsonObject packet(String type, String value) {
        JsonObject o = new JsonObject(); o.addProperty("type", type); o.addProperty("value", value); return o;
    }
    private static void broadcast(JsonObject obj, Peer except) {
        for (Peer p : PEERS) if (p != except) send(p, obj);
    }
    private static void broadcastCompatible(JsonObject obj, Peer except) {
        for (Peer p : PEERS) if (p != except && PEER_COMPATIBILITY.getOrDefault(p, false)) send(p, obj);
    }
    private static void send(Peer peer, JsonObject obj) {
        if (peer == null) return;
        IO.execute(() -> { try { synchronized (peer.out) { peer.out.write(GSON.toJson(obj)); peer.out.newLine(); peer.out.flush(); } } catch (Exception ignored) {} });
    }
    private static void notifyClient(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendMessage(Text.literal(message), false); });
    }

    public static synchronized void disconnect() {
        RUNNING.set(false); host = false; upstreamCompatible = false; status = "Отключено";
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null; upstream = null;
        for (Peer p : PEERS) close(p);
        PEERS.clear(); PEER_NAMES.clear(); PEER_COMPATIBILITY.clear(); CHAT.clear();
        roomName = "Нет активной комнаты"; roomAvatar = 0; roomMaxPlayers = 4; roomCode = "";
        roomPlayers = 0; roomMembers = List.of();
    }

    private static void close(Peer p) { try { p.socket.close(); } catch (Exception ignored) { } }
    private record Peer(Socket socket, BufferedReader in, BufferedWriter out) {}
}
