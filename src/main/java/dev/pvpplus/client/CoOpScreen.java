package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/** Functional neon co-op screen. Existing marker/friend behavior is kept; new room controls are real. */
public final class CoOpScreen extends Screen {
    private static final String[] TAB_NAMES = {"Друзья", "Комнаты", "Метки", "Чат", "Настройки"};
    private static final String[] AVATAR_NAMES = {"Дом", "Меч", "Алмаз", "Око", "Крипер", "Корона"};
    private static final int[] PLAYER_LIMITS = {2, 4, 8, 16};

    private final int activeTab;
    private ButtonWidget selectedTabButton;
    private TextFieldWidget roomNameField;
    private TextFieldWidget inviteAddressField;
    private TextFieldWidget portField;
    private TextFieldWidget inviteCodeField;
    private TextFieldWidget joinCodeField;
    private TextFieldWidget friendNameField;
    private TextFieldWidget playerSearchField;
    private TextFieldWidget chatField;
    private int playerScroll;
    private TextFieldWidget markerNameField, markerXField, markerYField, markerZField;
    private static String markerIcon = "pin";
    private static final String[] MARKER_ICONS = {"pin", "home", "chest", "parachute", "sword"};
    private static final String[] MARKER_ICON_NAMES = {"Метка", "Дом", "Сундук", "Парашют", "Меч"};
    private String message = "Создай комнату или введи код приглашения";

    public CoOpScreen() { this(1); }

    private CoOpScreen(int activeTab) {
        super(Text.literal("Plus Visual — Кооператив"));
        this.activeTab = Math.floorMod(activeTab, TAB_NAMES.length);
    }

    private int panelW() { return Math.min(670, width - 20); }
    private int panelH() { return Math.min(380, height - 20); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }
    private int cardY() { return panelY() + 60; }

    @Override
    protected void init() {
        int px = panelX(), py = panelY(), pw = panelW();
        contextTabs(px, py, pw);
        switch (activeTab) {
            case 0 -> initFriendsPage(px, py, pw);
            case 1 -> initRoomsPage(px, py, pw);
            case 2 -> initMarkersPage(px, py, pw);
            case 3 -> initChatPage(px, py, pw);
            case 4 -> initSettingsPage(px, py, pw);
            default -> initRoomsPage(px, py, pw);
        }
    }

    private void contextTabs(int px, int py, int pw) {
        int gap = 5;
        int tabW = (pw - 32 - gap * (TAB_NAMES.length - 1)) / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tab = i;
            ButtonWidget button = ButtonWidget.builder(Text.literal(TAB_NAMES[i]), b -> openTab(tab))
                    .dimensions(px + 16 + i * (tabW + gap), py + 32, tabW, 20).build();
            addDrawableChild(button);
            if (i == activeTab) selectedTabButton = button;
        }
    }

    public static void refreshFriendsPageIfOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof CoOpScreen screen && screen.activeTab == 0) {
            mc.setScreen(new CoOpScreen(0));
        }
    }

    private void openTab(int tab) {
        saveDraft();
        MinecraftClient.getInstance().setScreen(new CoOpScreen(tab));
    }

    private void initRoomsPage(int px, int py, int pw) {
        int top = cardY();
        int leftX = px + 12, leftW = 282;
        int rightX = leftX + leftW + 12, rightW = pw - (rightX - px) - 12;

        addDrawableChild(ButtonWidget.builder(Text.literal("Выбрать аватар: " + avatarName(Config.d.coopAvatar)), b -> {
            saveDraft();
            Config.d.coopAvatar = (Config.d.coopAvatar + 1) % AVATAR_NAMES.length;
            Config.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(1));
        }).dimensions(leftX + 12, top + 12, leftW - 24, 22).build());

        roomNameField = makeField(leftX + 12, top + 54, leftW - 24, 20, "Например: Наш мир", Config.d.coopRoomName, 32);

        int addressW = leftW - 112;
        inviteAddressField = makeField(leftX + 12, top + 92, addressW, 20, "IP LAN / VPN / домен", inviteAddressInitial(), 253);
        portField = makeField(leftX + leftW - 88, top + 92, 76, 20, "25581", String.valueOf(Config.d.coopPort), 5);

        addDrawableChild(ButtonWidget.builder(Text.literal("Игроков: " + Config.d.coopMaxPlayers), b -> {
            saveDraft();
            Config.d.coopMaxPlayers = nextLimit(Config.d.coopMaxPlayers);
            Config.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(1));
        }).dimensions(leftX + 12, top + 122, 122, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Создать комнату"), b -> createRoom())
                .dimensions(leftX + 12, top + 151, leftW - 24, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отключиться"), b -> {
            CoOpManager.disconnect();
            message = "Отключено";
            MinecraftClient.getInstance().setScreen(new CoOpScreen(1));
        }).dimensions(leftX + 12, top + 178, leftW - 24, 20).build());

        // Room status, shared room avatar, shareable host code, and joining by code are all live values.
        addDrawableChild(ButtonWidget.builder(Text.literal("Копировать код"), b -> copyRoomCode())
                .dimensions(rightX + 12, top + 114, Math.min(142, rightW - 24), 20).build());
        inviteCodeField = makeField(rightX + 12, top + 91, rightW - 24, 20, "Код появится после создания комнаты", currentCode(), 512);

        joinCodeField = makeField(rightX + 12, top + 154, rightW - 24, 20, "PV4-...", "", 512);
        addDrawableChild(ButtonWidget.builder(Text.literal("Войти по коду"), b -> joinByCode())
                .dimensions(rightX + 12, top + 181, rightW - 24, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Подключиться напрямую по IP/порту"), b -> connectDirect())
                .dimensions(rightX + 12, top + 207, rightW - 24, 20).build());
    }

    private String inviteAddressInitial() {
        if (Config.d.coopAdvertisedAddress != null && !Config.d.coopAdvertisedAddress.isBlank()) return Config.d.coopAdvertisedAddress;
        return RoomCode.detectInviteAddress();
    }

    private String currentCode() {
        if (CoOpManager.isConnected() && !CoOpManager.roomCode().isBlank()) return CoOpManager.roomCode();
        return Config.d.coopLastCode == null ? "" : Config.d.coopLastCode;
    }

    private void createRoom() {
        saveDraft();
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1024 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            message = "Порт должен быть числом от 1024 до 65535";
            return;
        }
        final String code;
        try {
            code = RoomCode.encode(inviteAddressField.getText(), port);
        } catch (IllegalArgumentException ex) {
            message = ex.getMessage();
            return;
        }
        String name = roomNameField.getText().trim();
        if (name.isBlank()) name = "Наш мир";
        if (CoOpManager.startHost(port, name, Config.d.coopAvatar, Config.d.coopMaxPlayers, code)) {
            Config.d.coopLastCode = code;
            Config.save();
            if (inviteCodeField != null) inviteCodeField.setText(code);
            message = "Комната создана. Отправь другу код приглашения.";
        } else {
            message = CoOpManager.status();
        }
    }

    private void joinByCode() {
        String code = joinCodeField == null ? "" : joinCodeField.getText().trim();
        try {
            RoomCode.Endpoint endpoint = RoomCode.decode(code);
            saveDraft();
            message = "Подключение к комнате...";
            CoOpManager.connect(endpoint.host(), endpoint.port());
        } catch (IllegalArgumentException ex) {
            message = ex.getMessage();
        }
    }

    private void connectDirect() {
        saveDraft();
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1024 || port > 65535) throw new NumberFormatException();
            if (inviteAddressField.getText().isBlank()) { message = "Укажите IP или домен хоста"; return; }
            CoOpManager.connect(inviteAddressField.getText().trim(), port);
            message = "Прямое подключение запущено...";
        } catch (NumberFormatException ex) {
            message = "Порт должен быть числом от 1024 до 65535";
        }
    }

    private void copyRoomCode() {
        String code = CoOpManager.isConnected() && !CoOpManager.roomCode().isBlank() ? CoOpManager.roomCode() : currentCode();
        if (code == null || code.isBlank()) { message = "Сначала создай комнату или получи код"; return; }
        MinecraftClient.getInstance().keyboard.setClipboard(code);
        message = "Код комнаты скопирован";
    }

    private void initFriendsPage(int px, int py, int pw) {
        int top = cardY();
        int leftX = px + 12, leftW = Math.min(302, pw / 2 - 18);
        int rightX = leftX + leftW + 12, rightW = pw - (rightX - px) - 12;
        playerSearchField = makeField(leftX + 10, top + 34, leftW - 20, 18, "Поиск игрока", "", 16);

        // Incoming requests are explicit: no player is added to the accepted list before consent.
        List<String> incoming = FriendManager.incomingRequests();
        for (int i = 0; i < Math.min(4, incoming.size()); i++) {
            final String requester = incoming.get(i);
            int rowY = top + 34 + i * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("Принять"), b -> acceptFriendRequest(requester))
                    .dimensions(rightX + rightW - 108, rowY, 48, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Отклонить"), b -> declineFriendRequest(requester))
                    .dimensions(rightX + rightW - 56, rowY, 50, 18).build());
        }

        friendNameField = makeField(rightX + 12, top + 198, rightW - 24, 18, "Ник Minecraft (необязательно)", "", 16);
        addDrawableChild(ButtonWidget.builder(Text.literal("Отправить заявку по нику"), b -> {
            sendFriendRequest(friendNameField == null ? "" : friendNameField.getText().trim());
            if (friendNameField != null) friendNameField.setText("");
        }).dimensions(rightX + 12, top + 220, rightW - 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Друзья · " + FriendManager.friends().size()), b ->
                MinecraftClient.getInstance().setScreen(new FriendsListScreen())).dimensions(rightX + 12, top + 245, rightW - 24, 22).build());
    }

    private void sendFriendRequest(String name) {
        if (name == null || name.isBlank()) { message = "Укажи ник игрока"; return; }
        if (!CoOpManager.isConnected()) {
            message = "Чтобы доставить заявку, оба игрока должны быть участниками комнаты Plus Visuals";
            return;
        }
        if (!FriendManager.createOutgoingRequest(name)) {
            message = FriendManager.isFriend(name) ? "Этот игрок уже в друзьях" : "Заявка уже существует или ник неверный";
            return;
        }
        if (CoOpManager.sendFriendRequest(name)) message = "Заявка отправлена: " + name;
        else FriendManager.resolveOutgoing(name, false, "Игрок не найден среди участников комнаты: " + name);
    }

    private void acceptFriendRequest(String requester) {
        if (!CoOpManager.respondToFriendRequest(requester, true)) {
            message = "Не удалось ответить: соединение с комнатой потеряно";
            return;
        }
        FriendManager.acceptIncoming(requester);
        message = "Заявка принята: " + requester;
        MinecraftClient.getInstance().setScreen(new CoOpScreen(0));
    }

    private void declineFriendRequest(String requester) {
        if (!CoOpManager.respondToFriendRequest(requester, false)) {
            message = "Не удалось отправить отказ: соединение с комнатой потеряно";
            return;
        }
        FriendManager.declineIncoming(requester);
        message = "Заявка отклонена: " + requester;
        MinecraftClient.getInstance().setScreen(new CoOpScreen(0));
    }

    private void initMarkersPage(int px, int py, int pw) {
        int top = cardY();
        int leftX = px + 12, leftW = 248, rightX = leftX + leftW + 10, rightW = pw - (rightX - px) - 12;
        markerNameField = makeField(leftX + 12, top + 42, leftW - 24, 18, "Название метки", "", 48);
        markerXField = makeField(leftX + 12, top + 78, 64, 18, "X", "", 12);
        markerYField = makeField(leftX + 84, top + 78, 64, 18, "Y", "", 12);
        markerZField = makeField(leftX + 156, top + 78, 64, 18, "Z", "", 12);
        addDrawableChild(ButtonWidget.builder(Text.literal("Моя позиция"), b -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                markerXField.setText(String.valueOf((int)Math.floor(mc.player.getX())));
                markerYField.setText(String.valueOf((int)Math.floor(mc.player.getY())));
                markerZField.setText(String.valueOf((int)Math.floor(mc.player.getZ())));
            }
        }).dimensions(leftX + 12, top + 108, leftW - 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Иконка: " + markerIconName()), b -> {
            markerIcon = nextMarkerIcon(markerIcon);
            b.setMessage(Text.literal("Иконка: " + markerIconName()));
        }).dimensions(leftX + 12, top + 134, leftW - 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Добавить общую метку"), b -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!CoOpManager.isConnected()) { message = "Сначала подключись к комнате"; return; }
            if (!CoOpManager.isCoOpCompatible()) { message = "Несовместимая версия Plus Visuals. Установите ту же версию мода."; return; }
            try {
                WaypointManager.Waypoint w = new WaypointManager.Waypoint();
                String name = markerNameField.getText().trim();
                w.name = name.isBlank() ? "Общая метка" : name;
                w.x = Double.parseDouble(markerXField.getText().trim());
                w.y = Double.parseDouble(markerYField.getText().trim());
                w.z = Double.parseDouble(markerZField.getText().trim());
                w.dim = mc.world == null ? "minecraft:overworld" : mc.world.getRegistryKey().getValue().toString();
                w.icon = markerIcon;
                WaypointManager.addCooperative(w);
                CoOpManager.shareMarker(w);
                WaypointManager.save();
                message = "Общая метка добавлена";
                markerNameField.setText("");
            } catch (NumberFormatException ex) { message = "Введи координаты X, Y и Z числами"; }
        }).dimensions(leftX + 12, top + 160, leftW - 24, 22).build());

        // Each shared marker gets its own removal action; the removal packet is broadcast to the room.
        List<WaypointManager.Waypoint> shared = WaypointManager.cooperative().stream()
                .filter(w -> w.coopId != null).limit(12).toList();
        for (int i = 0; i < shared.size(); i++) {
            final WaypointManager.Waypoint marker = shared.get(i);
            int rowY = top + 46 + i * 18;
            addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> {
                CoOpManager.removeSharedMarker(marker);
                WaypointManager.removeCooperative(marker);
                WaypointManager.save();
                message = "Метка удалена у участников комнаты";
                MinecraftClient.getInstance().setScreen(new CoOpScreen(2));
            }).dimensions(rightX + rightW - 35, rowY - 2, 25, 16).build());
        }
    }

    private String markerIconName() {
        for (int i = 0; i < MARKER_ICONS.length; i++)
            if (MARKER_ICONS[i].equals(markerIcon)) return MARKER_ICON_NAMES[i];
        return MARKER_ICON_NAMES[0];
    }

    private static String nextMarkerIcon(String current) {
        for (int i = 0; i < MARKER_ICONS.length; i++)
            if (MARKER_ICONS[i].equals(current)) return MARKER_ICONS[(i + 1) % MARKER_ICONS.length];
        return MARKER_ICONS[0];
    }

    private void initChatPage(int px, int py, int pw) {
        int top = cardY();
        chatField = makeField(px + 18, top + panelH() - 105, pw - 142, 20, "Сообщение участникам комнаты", "", 280);
        addDrawableChild(ButtonWidget.builder(Text.literal("Отправить"), b -> sendChatMessage())
                .dimensions(px + pw - 114, top + panelH() - 105, 96, 20).build());
    }

    private void sendChatMessage() {
        String text = chatField == null ? "" : chatField.getText().trim();
        if (!CoOpManager.isCoOpCompatible()) {
            message = CoOpManager.isConnected() ? "Чат заблокирован: несовместимая версия Plus Visuals" : "Сначала подключись к совместимой комнате";
            return;
        }
        if (!CoOpManager.sendChat(text)) { message = "Сообщение пустое или не удалось отправить"; return; }
        if (chatField != null) chatField.setText("");
        MinecraftClient.getInstance().setScreen(new CoOpScreen(3));
    }

    private void initSettingsPage(int px, int py, int pw) {
        int top = cardY();
        int colW = (pw - 42) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Подсветка друзей: " + onOff(FriendManager.data().highlightEnabled)), b -> {
            FriendManager.data().highlightEnabled = !FriendManager.data().highlightEnabled; FriendManager.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(4));
        }).dimensions(px + 16, top + 34, colW, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Режим: " + (FriendManager.data().highlightMode == 0 ? "Хитбокс" : "Настоящая броня")), b -> {
            FriendManager.data().highlightMode = (FriendManager.data().highlightMode + 1) % 2; FriendManager.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(4));
        }).dimensions(px + 22 + colW, top + 34, colW, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Цвет хитбокса: " + FriendManager.normalizeColorName(FriendManager.data().hitboxColor)), b -> {
            FriendManager.data().hitboxColor = nextColor(FriendManager.data().hitboxColor); FriendManager.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(4));
        }).dimensions(px + 16, top + 65, colW, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Часть брони: " + armorPartName(FriendManager.data().armorPart)), b -> {
            FriendManager.data().armorPart = (FriendManager.data().armorPart + 1) % 4; FriendManager.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(4));
        }).dimensions(px + 22 + colW, top + 65, colW, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Цвет брони: " + FriendManager.normalizeColorName(FriendManager.data().armorColor)), b -> {
            FriendManager.data().armorColor = nextColor(FriendManager.data().armorColor); FriendManager.save();
            MinecraftClient.getInstance().setScreen(new CoOpScreen(4));
        }).dimensions(px + 16, top + 96, colW, 22).build());
    }

    private TextFieldWidget makeField(int x, int y, int w, int h, String placeholder, String value, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, w, h, Text.literal(placeholder));
        field.setDrawsBackground(false);
        field.setMaxLength(maxLength);
        field.setPlaceholder(Text.literal(placeholder));
        if (value != null) field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void saveDraft() {
        if (roomNameField != null) {
            String name = roomNameField.getText().trim();
            Config.d.coopRoomName = name.isBlank() ? "Наш мир" : truncate(name, 32);
        }
        if (inviteAddressField != null) Config.d.coopAdvertisedAddress = inviteAddressField.getText().trim();
        if (portField != null) {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                if (port >= 1024 && port <= 65535) Config.d.coopPort = port;
            } catch (NumberFormatException ignored) { }
        }
        Config.save();
    }

    private static int nextLimit(int current) {
        for (int i = 0; i < PLAYER_LIMITS.length; i++) if (PLAYER_LIMITS[i] == current) return PLAYER_LIMITS[(i + 1) % PLAYER_LIMITS.length];
        return 2;
    }

    private static String avatarName(int idx) { return AVATAR_NAMES[Math.floorMod(idx, AVATAR_NAMES.length)]; }
    private static String onOff(boolean value) { return value ? "ВКЛ" : "ВЫКЛ"; }
    private static int nextColor(int current) {
        int[] colors = {0xFF36E36B, 0xFFFF4D4D, 0xFFFFFF35, 0xFF398BFF, 0xFFB45CFF};
        for (int i = 0; i < colors.length; i++) if (colors[i] == current) return colors[(i + 1) % colors.length];
        return colors[0];
    }
    private static String armorPartName(int part) {
        return switch (Math.floorMod(part, 4)) { case 0 -> "Шлем"; case 2 -> "Поножи"; case 3 -> "Ботинки"; default -> "Нагрудник"; };
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Ui.background(context, width, height);
        Ui.panel(context, panelX(), panelY(), panelW(), panelH());
        Ui.logo(context, panelX() + 12, panelY() + 8, Ui.ACCENT);
        context.drawTextWithShadow(textRenderer, title, panelX() + 34, panelY() + 12, Ui.TEXT);
        drawPage(context);
        drawInputBases(context);
        super.render(context, mouseX, mouseY, delta);
        for (var child : children()) if (child instanceof ButtonWidget button) {
            Ui.renderButton(context, textRenderer, button, button == selectedTabButton);
        }
        frameInputs(context);
        if (activeTab == 1) {
            int cx = panelX() + 24, cy = cardY() + 13;
            Ui.roomAvatar(context, Config.d.coopAvatar, cx, cy, 18);
            int rx = panelX() + 12 + 282 + 24;
            Ui.roomAvatar(context, CoOpManager.isConnected() ? CoOpManager.roomAvatar() : Config.d.coopAvatar, rx, cardY() + 13, 28);
            if (CoOpManager.isConnected() && !CoOpManager.roomCode().isBlank()) {
                if (inviteCodeField != null && !CoOpManager.roomCode().equals(inviteCodeField.getText())) inviteCodeField.setText(CoOpManager.roomCode());
            }
        }
    }

    private void drawPage(DrawContext c) {
        int px = panelX(), py = panelY(), pw = panelW(), top = cardY();
        if (activeTab == 1) {
            int leftX = px + 12, leftW = 282, rightX = leftX + leftW + 12, rightW = pw - (rightX - px) - 12;
            Ui.panel(c, leftX, top, leftW, panelH() - 72);
            Ui.panel(c, rightX, top, rightW, panelH() - 72);
            c.drawTextWithShadow(textRenderer, Text.literal("Параметры комнаты"), leftX + 12, top + 2, Ui.TEXT);
            c.drawTextWithShadow(textRenderer, Text.literal("Название комнаты"), leftX + 12, top + 42, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("IP / домен для кода приглашения"), leftX + 12, top + 80, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Порт"), leftX + leftW - 88, top + 80, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Код и подключение"), rightX + 48, top + 12, Ui.TEXT);
            String activeTitle = CoOpManager.isConnected() ? CoOpManager.roomName() : Config.d.coopRoomName;
            c.drawTextWithShadow(textRenderer, Text.literal(activeTitle == null || activeTitle.isBlank() ? "Новая комната" : truncate(activeTitle, 28)), rightX + 48, top + 25, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Статус: " + CoOpManager.status()), rightX + 12, top + 48, Ui.TEXT);
            c.drawTextWithShadow(textRenderer, Text.literal("Участники: " + CoOpManager.playerCount() + "/" + CoOpManager.maxPlayers()), rightX + 12, top + 61, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Код приглашения"), rightX + 12, top + 79, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Ввести код комнаты"), rightX + 12, top + 142, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal(truncate(message, 46)), rightX + 12, top + 234, Ui.ACCENT);
            List<String> members = CoOpManager.members();
            if (!members.isEmpty()) {
                c.drawTextWithShadow(textRenderer, Text.literal("Участники комнаты"), rightX + 12, top + 253, Ui.TEXT);
                int count = Math.min(3, members.size());
                for (int i = 0; i < count; i++) {
                    c.drawTextWithShadow(textRenderer, Text.literal("• " + truncate(members.get(i), 35)), rightX + 14, top + 267 + i * 11, Ui.DIM);
                }
            }
            c.drawTextWithShadow(textRenderer, Text.literal("Код содержит IP/домен и порт; это не ретранслятор."), leftX + 12, top + 211, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Для друга в другой сети нужен VPN или открытый порт."), leftX + 12, top + 224, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Для Radmin VPN укажи адрес Radmin-интерфейса."), leftX + 12, top + 237, Ui.DIM);
        } else if (activeTab == 0) {
            int leftX = px + 12, topX = top, leftW = Math.min(302, pw / 2 - 18);
            int rightX = leftX + leftW + 12, rightW = pw - (rightX - px) - 12;
            Ui.panel(c, leftX, topX, leftW, panelH() - 72);
            Ui.panel(c, rightX, topX, rightW, panelH() - 72);
            c.drawTextWithShadow(textRenderer, Text.literal("Игроки на сервере — нажми, чтобы добавить друзей"), leftX + 10, topX + 12, Ui.TEXT);
            c.drawTextWithShadow(textRenderer, Text.literal("Входящие заявки"), rightX + 12, topX + 12, Ui.TEXT);
            MinecraftClient mc = MinecraftClient.getInstance();
            String filter = playerSearchField == null ? "" : playerSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
            List<String> onlineNames = onlinePlayerNames(mc).stream()
                    .filter(name -> !name.equalsIgnoreCase(mc.getSession().getUsername()))
                    .filter(name -> filter.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(filter))
                    .distinct().toList();
            playerScroll = Math.min(playerScroll, Math.max(0, onlineNames.size() - 10));
            int onlineCount = Math.min(10, Math.max(0, onlineNames.size() - playerScroll));
            for (int i = 0; i < onlineCount; i++) {
                int rowY = topX + 59 + i * 18;
                String name = onlineNames.get(i + playerScroll);
                boolean isFriend = FriendManager.isFriend(name);
                boolean outgoing = FriendManager.hasOutgoing(name);
                boolean incoming = FriendManager.hasIncoming(name);
                Ui.rrect(c, leftX + 8, rowY - 3, leftW - 16, 17, i % 2 == 0 ? Ui.ROW_SEL : Ui.ROW);
                c.drawTextWithShadow(textRenderer, Text.literal(name), leftX + 14, rowY,
                        isFriend ? Ui.DIM : (outgoing || incoming ? Ui.ACCENT : 0xFF55FF88));
                String state = isFriend ? "✓" : outgoing ? "…" : incoming ? "!" : "+";
                c.drawTextWithShadow(textRenderer, Text.literal(state), leftX + leftW - 24, rowY,
                        isFriend ? Ui.DIM : Ui.ACCENT);
            }
            if (onlineNames.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal("Игроки появятся здесь"), leftX + 14, topX + 62, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Поиск по нику"), leftX + 12, topX + 25, Ui.DIM);

            List<String> incomingRequests = FriendManager.incomingRequests();
            for (int i = 0; i < Math.min(4, incomingRequests.size()); i++) {
                String name = incomingRequests.get(i);
                int rowY = topX + 35 + i * 24;
                Ui.rrect(c, rightX + 8, rowY - 2, rightW - 16, 20, i % 2 == 0 ? Ui.ROW_SEL : Ui.ROW);
                c.drawTextWithShadow(textRenderer, Text.literal(truncate(name, 13)), rightX + 14, rowY + 4, Ui.TEXT);
            }
            if (incomingRequests.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal("Новых заявок нет"), rightX + 14, topX + 40, Ui.DIM);

            c.drawTextWithShadow(textRenderer, Text.literal("Отправленные: " + FriendManager.outgoingRequests().size()), rightX + 12, topX + 137, Ui.TEXT);
            List<String> outgoingRequests = FriendManager.outgoingRequests();
            for (int i = 0; i < Math.min(3, outgoingRequests.size()); i++) {
                c.drawTextWithShadow(textRenderer, Text.literal("• " + truncate(outgoingRequests.get(i), 24) + "  · ожидание"),
                        rightX + 14, topX + 153 + i * 14, Ui.DIM);
            }
            c.drawTextWithShadow(textRenderer, Text.literal(CoOpManager.isConnected()
                    ? (CoOpManager.isCoOpCompatible() ? "Заявки и кооператив подключены" : "Заявки доступны; функции комнаты заблокированы")
                    : "Заявки доступны, когда оба игрока в комнате"),
                    rightX + 12, topX + 190, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal(truncate(message, 43)), rightX + 12, topX + 276, Ui.ACCENT);
        } else if (activeTab == 2) {
            int leftX = px + 12, leftW = 248, rightX = leftX + leftW + 10, rightW = pw - (rightX - px) - 12;
            Ui.panel(c, leftX, top, leftW, panelH() - 72);
            Ui.panel(c, rightX, top, rightW, panelH() - 72);
            c.drawTextWithShadow(textRenderer, Text.literal("Создание общей метки"), leftX + 12, top + 12, Ui.TEXT);
            c.drawTextWithShadow(textRenderer, Text.literal("Название"), leftX + 12, top + 30, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("X"), leftX + 12, top + 66, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Y"), leftX + 84, top + 66, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Z"), leftX + 156, top + 66, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Только для участников комнаты"), leftX + 12, top + 190, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Общие метки комнаты"), rightX + 12, top + 12, Ui.TEXT);
            List<WaypointManager.Waypoint> marks = WaypointManager.cooperative().stream().filter(w -> w.coopId != null).toList();
            c.drawTextWithShadow(textRenderer, Text.literal("Всего: " + marks.size()), rightX + 12, top + 28, Ui.DIM);
            int count = Math.min(12, marks.size());
            for (int i = 0; i < count; i++) {
                WaypointManager.Waypoint w = marks.get(i);
                int y = top + 48 + i * 18;
                Ui.rrect(c, rightX + 8, y - 3, rightW - 16, 17, i % 2 == 0 ? Ui.ROW_SEL : Ui.ROW);
                Ui.marker(c, w.icon, rightX + 13, y, 11);
                c.drawTextWithShadow(textRenderer, Text.literal(truncate(w.name, 20)), rightX + 30, y + 1, Ui.TEXT);
                String coords = String.format(java.util.Locale.ROOT, "%.0f, %.0f, %.0f", w.x, w.y, w.z);
                c.drawTextWithShadow(textRenderer, Text.literal(truncate(coords, 18)), rightX + rightW - 145, y + 1, Ui.DIM);
            }
            if (marks.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal("Общих меток пока нет"), rightX + 12, top + 50, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal(message), rightX + 12, top + panelH() - 92, Ui.ACCENT);
        } else if (activeTab == 3) {
            Ui.panel(c, px + 12, top, pw - 24, panelH() - 72);
            c.drawTextWithShadow(textRenderer, Text.literal("Приватный чат участников комнаты"), px + 24, top + 12, Ui.TEXT);
            if (!CoOpManager.isConnected()) {
                c.drawTextWithShadow(textRenderer, Text.literal("Подключись к комнате, чтобы открыть общий чат."), px + 24, top + 42, Ui.DIM);
            } else if (!CoOpManager.isCoOpCompatible()) {
                c.drawTextWithShadow(textRenderer, Text.literal("Несовместимая версия Plus Visuals. Чат и кооперативные функции заблокированы."), px + 24, top + 42, 0xFFFF7777);
            } else {
                List<CoOpManager.ChatMessage> messages = CoOpManager.chatMessages();
                int visible = Math.min(12, messages.size());
                int first = Math.max(0, messages.size() - visible);
                for (int i = first; i < messages.size(); i++) {
                    CoOpManager.ChatMessage chat = messages.get(i);
                    int rowY = top + 34 + (i - first) * 17;
                    c.drawTextWithShadow(textRenderer, Text.literal("<" + truncate(chat.sender(), 16) + "> " + truncate(chat.message(), 80)),
                            px + 24, rowY, Ui.TEXT);
                }
                if (messages.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal("Пока нет сообщений. Напиши первое!"), px + 24, top + 42, Ui.DIM);
            }
            c.drawTextWithShadow(textRenderer, Text.literal(truncate(message, 70)), px + 24, top + panelH() - 78, Ui.ACCENT);
        } else {
            Ui.panel(c, px + 12, top, pw - 24, panelH() - 72);
            c.drawTextWithShadow(textRenderer, Text.literal("Настройки визуальной подсветки друзей"), px + 26, top + 12, Ui.TEXT);
            c.drawTextWithShadow(textRenderer, Text.literal("Эти параметры используют существующие функции FriendManager."), px + 26, top + 100, Ui.DIM);
            c.drawTextWithShadow(textRenderer, Text.literal("Изменения применяются сразу и сохраняются локально."), px + 26, top + 116, Ui.DIM);
        }
    }

    /** Returns the server's player-list entries, not only player entities in loaded chunks. */
    private static List<String> onlinePlayerNames(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return List.of();
        return mc.getNetworkHandler().getPlayerList().stream()
                .map(entry -> entry.getProfile().name())
                .filter(name -> name != null && !name.isBlank())
                .distinct().toList();
    }

    private void drawInputBases(DrawContext c) {
        for (TextFieldWidget field : fields()) if (field != null && field.visible) Ui.inputBase(c, field.getX(), field.getY(), field.getWidth(), field.getHeight());
    }

    private void frameInputs(DrawContext c) {
        for (TextFieldWidget field : fields()) if (field != null && field.visible) Ui.frame(c, field.getX() - 1, field.getY() - 1, field.getWidth() + 2, field.getHeight() + 2);
    }

    private List<TextFieldWidget> fields() {
        return java.util.Arrays.asList(roomNameField, inviteAddressField, portField, inviteCodeField, joinCodeField, friendNameField, playerSearchField, chatField, markerNameField, markerXField, markerYField, markerZField)
                .stream().filter(java.util.Objects::nonNull).toList();
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mx = click.x(), my = click.y();
        if (activeTab == 0 && click.button() == 0) {
            int px = panelX(), pw = panelW(), top = cardY();
            int leftX = px + 12, leftW = Math.min(302, pw / 2 - 18);
            int rightX = leftX + leftW + 12;
            MinecraftClient mc = MinecraftClient.getInstance();
            String filter = playerSearchField == null ? "" : playerSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
            List<String> onlineNames = onlinePlayerNames(mc).stream()
                    .filter(name -> !name.equalsIgnoreCase(mc.getSession().getUsername()))
                    .filter(name -> filter.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(filter))
                    .distinct().toList();
            playerScroll = Math.min(playerScroll, Math.max(0, onlineNames.size() - 10));
            for (int i = 0; i < Math.min(10, Math.max(0, onlineNames.size() - playerScroll)); i++) {
                double rowY = top + 59 + i * 18;
                if (mx >= leftX + 8 && mx <= leftX + leftW - 8 && my >= rowY - 4 && my <= rowY + 14) {
                    String name = onlineNames.get(i + playerScroll);
                    if (FriendManager.isFriend(name)) message = "Этот игрок уже в друзьях";
                    else if (FriendManager.hasOutgoing(name)) message = "Заявка уже отправлена";
                    else sendFriendRequest(name);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeTab == 0) {
            int left = panelX() + 12;
            int right = left + Math.min(302, panelW() / 2 - 18);
            if (mouseX >= left && mouseX <= right && mouseY >= cardY() + 52 && mouseY <= cardY() + panelH() - 76) {
                playerScroll = Math.max(0, playerScroll - (int) Math.signum(verticalAmount) * 3);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void close() { saveDraft(); super.close(); }
    @Override public boolean shouldPause() { return false; }
}
public static CoOpScreen create() {
    return new CoOpScreen(0);
}
