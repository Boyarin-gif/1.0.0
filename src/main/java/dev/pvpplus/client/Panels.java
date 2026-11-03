package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/** Небольшие перетаскиваемые окошки: FPS/пинг, эффекты, цель, комбо, убийства, откаты, клавиши, уведомления, тотемы. */
public final class Panels {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    // ---------- кэши строк (чтобы не собирать одни и те же строки каждый кадр)
    private static final String[] TIME = new String[3600];
    private static final Map<RegistryEntry<StatusEffect>, String[]> NAMES = new HashMap<>();

    private static String fmtTime(int ticks) {
        int s = Math.max(0, ticks) / 20;
        if (s >= TIME.length) return (s / 60) + ":" + String.format("%02d", s % 60);
        String c = TIME[s];
        if (c == null) {
            c = (s / 60) + ":" + String.format("%02d", s % 60);
            TIME[s] = c;
        }
        return c;
    }

    private static String effectName(RegistryEntry<StatusEffect> eff, int amp) {
        String base;
        if (amp >= ROMAN.length) {
            base = eff.value().getName().getString();
            return base + " " + (amp + 1);
        }
        String[] arr = NAMES.get(eff);
        if (arr == null) {
            arr = new String[ROMAN.length];
            NAMES.put(eff, arr);
        }
        String s = arr[amp];
        if (s == null) {
            base = eff.value().getName().getString();
            s = amp > 0 ? base + " " + ROMAN[amp] : base;
            arr[amp] = s;
        }
        return s;
    }

    // ---------- FPS и пинг
    private static int lastFps = -1, lastPing = -1;
    private static String fpsStr = "", pingStr = "";

    public static void fpsPing(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("fps")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TextRenderer tr = mc.textRenderer;

        int fps = mc.getCurrentFps();
        if (fps != lastFps) {
            lastFps = fps;
            fpsStr = fps + " FPS";
        }
        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        if (ping != lastPing) {
            lastPing = ping;
            pingStr = ping + " мс";
        }

        int fpsWidth = tr.getWidth(fpsStr);
        int pingWidth = tr.getWidth(pingStr);
        int w = fpsWidth + pingWidth + 38;
        int h = 16;
        int[] p = HudPanels.place("fps", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 110);

        // Small low-cost HUD icons drawn from rectangles, so they never depend on missing texture files.
        int fpsIconX = p[0] + 4;
        int iconY = p[1] + 3;
        drawFpsIcon(ctx, fpsIconX, iconY, 0xFF55FF55);
        int fpsTextX = p[0] + 15;
        ctx.drawText(tr, fpsStr, fpsTextX, p[1] + 3, 0xFFFFFFFF, true);

        int pc = ping < 80 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : 0xFFFF5555;
        int pingIconX = fpsTextX + fpsWidth + 6;
        drawPingIcon(ctx, pingIconX, p[1] + 4, pc, ping);
        ctx.drawText(tr, pingStr, pingIconX + 11, p[1] + 3, pc, true);
    }

    private static void drawFpsIcon(DrawContext ctx, int x, int y, int color) {
        Ui.metricIcon(ctx, 0, x, y, 10);
    }

    private static void drawPingIcon(DrawContext ctx, int x, int y, int activeColor, int ping) {
        int activeBars = ping < 80 ? 4 : ping < 150 ? 3 : ping < 250 ? 2 : 1;
        Ui.metricIcon(ctx, activeBars, x - 1, y - 1, 10);
    }

    // ---------- Эффекты зелий: ванильные PNG-текстуры из ресурсов Minecraft
    private static final int MAX_POTIONS = 64;
    private static final String[] P_NAMES = new String[MAX_POTIONS];
    private static final String[] P_TIMES = new String[MAX_POTIONS];
    private static final int[] P_COLORS = new int[MAX_POTIONS];
    private static final boolean[] P_LOW = new boolean[MAX_POTIONS];
    private static final Identifier[] P_TEXTURES = new Identifier[MAX_POTIONS];
    private static final Map<RegistryEntry<StatusEffect>, Identifier> EFFECT_TEXTURES = new HashMap<>();
    private static final Identifier FALLBACK_EFFECT_TEXTURE = Identifier.ofVanilla("textures/mob_effect/speed.png");

    private static Identifier effectTexture(MinecraftClient mc, RegistryEntry<StatusEffect> effect) {
        return EFFECT_TEXTURES.computeIfAbsent(effect, key -> {
            Identifier effectId = Registries.STATUS_EFFECT.getId(key.value());
            if (effectId == null) return FALLBACK_EFFECT_TEXTURE;
            Identifier texture = Identifier.of(effectId.getNamespace(), "textures/mob_effect/" + effectId.getPath() + ".png");
            return mc.getResourceManager().getResource(texture).isPresent()
                    ? texture
                    : FALLBACK_EFFECT_TEXTURE;
        });
    }

    public static void potions(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("potions")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity pl = mc.player;
        if (pl == null || pl.getStatusEffects().isEmpty()) return;
        TextRenderer tr = mc.textRenderer;

        int n = 0;
        for (StatusEffectInstance e : pl.getStatusEffects()) {
            if (n >= MAX_POTIONS) break;
            P_NAMES[n] = effectName(e.getEffectType(), e.getAmplifier());
            P_TIMES[n] = e.isInfinite() ? "∞" : fmtTime(e.getDuration());
            P_COLORS[n] = e.getEffectType().value().getColor() | 0xFF000000;
            P_LOW[n] = !e.isInfinite() && e.getDuration() < 200;
            P_TEXTURES[n] = effectTexture(mc, e.getEffectType());
            n++;
        }

        int maxW = 0;
        for (int i = 0; i < n; i++) {
            maxW = Math.max(maxW, 19 + tr.getWidth(P_NAMES[i]) + 5 + tr.getWidth(P_TIMES[i]));
        }
        int w = maxW + 8;
        int h = n * 14 + 6;
        int[] p = HudPanels.place("potions", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 110);
        for (int i = 0; i < n; i++) {
            int y = p[1] + 3 + i * 14;
            int ix = p[0] + 4;
            // Original vanilla 18x18 status-effect PNG; drawTexture scales it without recolouring it.
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, P_TEXTURES[i], ix, y, 0.0F, 0.0F, 12, 12, 18, 18);
            ctx.drawText(tr, P_NAMES[i], p[0] + 19, y + 2, P_COLORS[i], true);
            ctx.drawText(tr, P_TIMES[i], p[0] + w - 4 - tr.getWidth(P_TIMES[i]), y + 2,
                    P_LOW[i] ? 0xFFFF5555 : 0xFFFFFFFF, true);
        }
    }

    // ---------- Цель
    public static void target(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("target")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity pl = mc.player;
        if (pl == null || mc.world == null) return;

        LivingEntity t = CombatTracker.currentTarget(mc);
        if (t == null) return;

        TextRenderer tr = mc.textRenderer;
        String name = t.getName().getString();
        float hp = t.getHealth();
        float max = Math.max(1f, t.getMaxHealth());
        int armor = t.getArmor();
        double dist = pl.distanceTo(t);

        int w = Math.max(100, tr.getWidth(name) + 10);
        int h = 42;
        int[] p = HudPanels.place("target", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 130);
        ctx.drawText(tr, name, p[0] + 4, p[1] + 3, 0xFFFFFFFF, true);

        int barW = w - 8;
        float frac = Math.max(0f, Math.min(1f, hp / max));
        Ui.track(ctx, p[0] + 4, p[1] + 14, barW, 5);
        int meterColor = frac > 0.5f ? 0 : frac > 0.25f ? 1 : 2;
        Ui.meter(ctx, p[0] + 4, p[1] + 14, Math.round(barW * frac), 5, meterColor);

        String hpText = (Math.round(hp * 10) / 10.0) + "/" + (int) max;
        ctx.drawText(tr, hpText, p[0] + 4, p[1] + 22, 0xFFFFFFFF, true);
        String ar = "Броня " + armor;
        ctx.drawText(tr, ar, p[0] + w - 4 - tr.getWidth(ar), p[1] + 22, 0xFFB0B0B0, true);
        ctx.drawText(tr, (Math.round(dist * 10) / 10.0) + " м", p[0] + 4, p[1] + 32, 0xFFB0B0B0, true);
    }

    // ---------- Комбо
    public static void combo(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("combo")) return;
        if (CombatTracker.combo < 2 || CombatTracker.tickCount - CombatTracker.lastHitTick > 40) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        String s = "Комбо x" + CombatTracker.combo;
        int w = tr.getWidth(s) + 10;
        int h = 13;
        int[] p = HudPanels.place("combo", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 130);
        ctx.drawText(tr, s, p[0] + 5, p[1] + 3, 0xFFFFD055, true);
    }

    // ---------- Лента убийств
    public static void killFeed(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("killfeed")) return;
        List<CombatTracker.FeedEntry> feed = CombatTracker.feed;
        for (int i = feed.size() - 1; i >= 0; i--) {
            if (CombatTracker.tickCount - feed.get(i).tick() > 100) feed.remove(i);
        }
        if (feed.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int maxW = 0;
        for (int i = 0; i < feed.size(); i++) maxW = Math.max(maxW, tr.getWidth(feed.get(i).text()));
        int w = maxW + 10;
        int h = feed.size() * 11 + 4;
        int[] p = HudPanels.place("killfeed", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 110);
        for (int i = 0; i < feed.size(); i++) {
            ctx.drawText(tr, feed.get(i).text(), p[0] + 5, p[1] + 3 + i * 11, 0xFFFFFFFF, true);
        }
    }

    // ---------- Откаты предметов (жемчуг, яблоки и т.д.)
    private static final ItemStack[] CD_ST = new ItemStack[41];
    private static final float[] CD_PR = new float[41];
    private static final Set<Item> CD_SEEN = new HashSet<>();

    public static void cooldowns(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("cooldowns")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity pl = mc.player;
        if (pl == null) return;
        ItemCooldownManager cm = pl.getItemCooldownManager();
        PlayerInventory inv = pl.getInventory();

        int n = 0;
        CD_SEEN.clear();
        for (int i = 0; i < inv.size() && n < CD_ST.length; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !CD_SEEN.add(st.getItem())) continue;
            float pr = cm.getCooldownProgress(st, 0f);
            if (pr > 0f) {
                CD_ST[n] = st;
                CD_PR[n] = pr;
                n++;
            }
        }
        if (n == 0) return;

        int w = 50;
        int h = n * 18 + 4;
        int[] p = HudPanels.place("cooldowns", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 110);
        for (int i = 0; i < n; i++) {
            int y = p[1] + 2 + i * 18;
            ctx.drawItem(CD_ST[i], p[0] + 3, y + 1);
            int bx = p[0] + 22;
            int bw = w - 26;
            Ui.track(ctx, bx, y + 7, bw, 4);
            Ui.meter(ctx, bx, y + 7, Math.round(bw * CD_PR[i]), 4, 3);
        }
    }

    // ---------- Список клавиш мода
    public static void hotkeys(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("hotkeys")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        List<String> lines = PvpPlusClient.hotkeyLines();
        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, tr.getWidth(s));
        int w = maxW + 10;
        int h = lines.size() * 10 + 4;
        int[] p = HudPanels.place("hotkeys", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 110);
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawText(tr, lines.get(i), p[0] + 5, p[1] + 3 + i * 10, 0xFFFFFFFF, true);
        }
    }

    // ---------- Уведомления сверху по центру (обычные + конец зелий)
    private static final List<String> NL = new ArrayList<>();
    private static final int[] NC = new int[8];

    public static void notifications(DrawContext ctx, RenderTickCounter tick) {
        boolean msgs = Config.on("notify");
        boolean potions = Config.on("potionalert");
        if (!msgs && !potions) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity pl = mc.player;
        if (pl == null) return;

        NL.clear();
        int n = 0;
        if (msgs) {
            List<Notifier.Msg> l = Notifier.LIST;
            for (int i = l.size() - 1; i >= 0; i--) {
                if (CombatTracker.tickCount - l.get(i).tick() > 70) l.remove(i);
            }
            for (int i = Math.max(0, l.size() - 3); i < l.size() && n < NC.length; i++) {
                NL.add(l.get(i).text());
                NC[n++] = 0xFFFFFFFF;
            }
        }
        if (potions) {
            // реальная длительность эффекта: за 10 секунд (200 тиков) до конца; продление эффекта убирает строку само
            for (StatusEffectInstance e : pl.getStatusEffects()) {
                if (n >= NC.length) break;
                if (e.isInfinite()) continue;
                int d = e.getDuration();
                if (d <= 0 || d > 200) continue;
                NL.add(effectName(e.getEffectType(), e.getAmplifier()) + " кончается: " + ((d + 19) / 20) + " с");
                NC[n++] = 0xFFFFB040;
            }
        }
        if (n == 0) return;

        TextRenderer tr = mc.textRenderer;
        int maxW = 0;
        for (int i = 0; i < n; i++) maxW = Math.max(maxW, tr.getWidth(NL.get(i)));
        int w = maxW + 16;
        int h = n * 12 + 5;
        int[] p = HudPanels.place("notify", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 140);
        for (int i = 0; i < n; i++) {
            String s = NL.get(i);
            ctx.drawText(tr, s, p[0] + (w - tr.getWidth(s)) / 2, p[1] + 4 + i * 12, NC[i], true);
        }
    }

    // ---------- Булава: высота падения и бонус урона
    public static void mace(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("mace")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity pl = mc.player;
        if (pl == null || !pl.getMainHandStack().isOf(Items.MACE)) return;
        double fall = pl.fallDistance;
        if (fall <= 0.5) return;
        double bonus = fall <= 3 ? 4 * fall : fall <= 8 ? 12 + 2 * (fall - 3) : 22 + (fall - 8);
        TextRenderer tr = mc.textRenderer;
        String s = "Падение " + (Math.round(fall * 10) / 10.0) + " м  +" + (int) bonus + " урона";
        int w = tr.getWidth(s) + 10;
        int h = 13;
        int[] p = HudPanels.place("mace", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 130);
        ctx.drawText(tr, s, p[0] + 5, p[1] + 3, fall >= 1.5 ? 0xFFFFD055 : 0xFFB0B0B0, true);
    }

    // ---------- Totem Bar
    private static int totems = 0;
    private static String totemStr = "x0";
    private static ItemStack totemStack = null;

    /** Считает тотемы в основном инвентаре, хотбаре и второй руке (каждый слот ровно один раз). */
    public static void updateTotems(PlayerEntity p) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        ItemStack off = p.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) n += off.getCount();
        if (n != totems) {
            totems = n;
            totemStr = "x" + n;
        }
    }

    public static void totem(DrawContext ctx, RenderTickCounter tick) {
        if (!Config.on("totem")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity pl = mc.player;
        if (pl == null || pl.isSpectator()) return;
        if (mc.interactionManager != null && !mc.interactionManager.hasStatusBars()) return;
        if (totemStack == null) totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        TextRenderer tr = mc.textRenderer;
        int w = 22 + tr.getWidth(totemStr) + 6;
        int h = 18;
        int[] p = HudPanels.place("totem", w, h);
        Gfx.panel(ctx, p[0], p[1], w, h, 130);
        ctx.drawItem(totemStack, p[0] + 3, p[1] + 1);
        ctx.drawText(tr, totemStr, p[0] + 22, p[1] + 5, totems > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);
    }
}
