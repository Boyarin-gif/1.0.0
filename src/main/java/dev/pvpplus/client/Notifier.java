package dev.pvpplus.client;

import java.util.ArrayList;
import java.util.List;

public final class Notifier {
    public record Msg(String text, int tick) {}

    public static final List<Msg> LIST = new ArrayList<>();

    public static void push(String text) {
        if (!Config.on("notify")) return;
        LIST.add(new Msg(text, CombatTracker.tickCount));
        while (LIST.size() > 4) LIST.remove(0);
    }
}
