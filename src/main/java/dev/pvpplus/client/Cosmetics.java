package dev.pvpplus.client;

import net.minecraft.util.Identifier;

/**
 * Catalog of cosmetics shown in {@link CosmeticsScreen}. Each entry only describes what the UI
 * needs (icon, labels, config keys); the actual 3D geometry lives in each cosmetic's own
 * {@code *FeatureRenderer} class, which reads the same {@code equipKey}/{@code colorKey}.
 */
public final class Cosmetics {

    public static final class Item {
        public final String id;
        public final String label;
        public final Identifier icon;
        public final int iconTextureW, iconTextureH;
        public final String equipKey;
        /** Config key (palette index, see {@link Features#COLORS}) or null if this item has a fixed color. */
        public final String colorKey;
        public final String note;

        Item(String id, String label, Identifier icon, int iconTextureW, int iconTextureH,
                String equipKey, String colorKey, String note) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.iconTextureW = iconTextureW;
            this.iconTextureH = iconTextureH;
            this.equipKey = equipKey;
            this.colorKey = colorKey;
            this.note = note;
        }

        public boolean hasColor() { return colorKey != null; }

        public boolean isEquipped() { return Config.on(equipKey); }

        public void setEquipped(boolean equipped) {
            Config.setOn(equipKey, equipped);
            Config.save();
        }
    }

    public static final Item[] ALL = {
            new Item("witch_hat", "Ведьминская шляпа",
                    Identifier.of("pvpplus", "textures/cosmetic/witch_hat.png"), 512, 512,
                    Features.COSMETIC_WITCH_HAT, null,
                    "Видна только тебе, от третьего лица (F5)."),
            new Item("dragon_wings", "Драконьи крылья",
                    Identifier.of("pvpplus", "textures/cosmetic/dragon_wings.png"), 64, 32,
                    Features.COSMETIC_DRAGON_WINGS, Features.COSMETIC_DRAGON_WINGS_COLOR,
                    "Цвет крыльев выбирается ниже. Видны только тебе, от третьего лица (F5)."),
            new Item("gold_crown", "Золотая корона",
                    Identifier.of("pvpplus", "textures/cosmetic/gold_crown.png"), 64, 32,
                    Features.COSMETIC_GOLD_CROWN, null,
                    "Фиксированный золотой цвет. Видна только тебе, от третьего лица (F5)."),
            new Item("katana", "Катана (на спине)",
                    Identifier.of("pvpplus", "textures/cosmetic/katana.png"), 64, 32,
                    Features.COSMETIC_KATANA, null,
                    "Фиксированный цвет клинка. Видна только тебе, от третьего лица (F5)."),
    };

    private Cosmetics() {}
}
