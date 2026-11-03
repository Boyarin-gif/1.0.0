package dev.pvpplus.client;

import dev.pvpplus.mixin.ModelPartAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Client-only skin upload and full biped model used by the customization preview. */
public final class PreviewSkinManager {
    private static final Identifier DEFAULT_SKIN = Identifier.of("pvpplus", "textures/cosmetic/preview_default_skin.png");
    private static final Identifier PREVIEW_TEXTURE = Identifier.of("pvpplus", "dynamic/preview_skin");
    private static final Path SKIN_FILE = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("plusvisual/preview_skin.png");

    private static NativeImage baseSkin;
    private static Identifier dynamicTexture;
    private static PlayerEntityModel previewModel;
    private static int lastWingColor = Integer.MIN_VALUE;

    private PreviewSkinManager() {}

    public static PlayerEntityModel getModel(MinecraftClient client) {
        ensureModel(client);
        return previewModel;
    }

    public static Identifier getTexture(MinecraftClient client) {
        ensureSkin(client);
        refreshTexture(client);
        return dynamicTexture != null ? dynamicTexture : DEFAULT_SKIN;
    }

    public static void setVisibleCosmetics(String selectedId, boolean previewSelected) {
        if (previewModel == null) return;
        boolean hat = Config.on(Features.COSMETIC_WITCH_HAT) || (previewSelected && "witch_hat".equals(selectedId));
        boolean wings = Config.on(Features.COSMETIC_DRAGON_WINGS) || (previewSelected && "dragon_wings".equals(selectedId));
        boolean crown = Config.on(Features.COSMETIC_GOLD_CROWN) || (previewSelected && "gold_crown".equals(selectedId));
        boolean katana = Config.on(Features.COSMETIC_KATANA) || (previewSelected && "katana".equals(selectedId));
        setPartVisible(previewModel.head, "pvpplus_preview_hat", hat);
        setPartVisible(previewModel.head, "pvpplus_preview_hat_details", hat);
        setPartVisible(previewModel.head, "pvpplus_preview_crown", crown);
        setPartVisible(previewModel.body, "pvpplus_preview_wings", wings);
        setPartVisible(previewModel.body, "pvpplus_preview_katana", katana);
    }

    private static void setPartVisible(ModelPart parent, String name, boolean visible) {
        if (parent != null && parent.hasChild(name)) parent.getChild(name).visible = visible;
    }

    private static void ensureModel(MinecraftClient client) {
        if (previewModel != null || client == null) return;
        ModelData data = PlayerEntityModel.getTexturedModelData(Dilation.NONE, false);
        ModelPart baked = TexturedModelData.of(data, 128, 128).createModel();
        previewModel = new PlayerEntityModel(baked, false);

        attach(previewModel.body, "pvpplus_preview_wings", buildWings());
        attach(previewModel.body, "pvpplus_preview_katana", buildKatana());
        attach(previewModel.head, "pvpplus_preview_crown", buildCrown());
        attach(previewModel.head, "pvpplus_preview_hat", buildWitchHat());
        attach(previewModel.head, "pvpplus_preview_hat_details", buildWitchHatDetails());
    }

    private static void attach(ModelPart parent, String name, ModelPart child) {
        ((ModelPartAccessor) (Object) parent).pvpplus$getChildren().put(name, child);
    }

    private static ModelPart modelPart(String key, ModelPartBuilder builder, ModelTransform transform) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild(key, builder, transform);
        return TexturedModelData.of(data, 128, 128).createModel().getChild(key);
    }

    private static ModelPart buildWings() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartBuilder left = ModelPartBuilder.create()
                .uv(64, 64).cuboid(-10.0F, -5.0F, 0.0F, 8.0F, 10.0F, 0.7F)
                .uv(64, 64).cuboid(-15.0F, -3.0F, 0.0F, 6.0F, 8.0F, 0.6F)
                .uv(64, 64).cuboid(-8.0F, 3.0F, 0.0F, 7.0F, 5.0F, 0.6F);
        ModelPartBuilder right = ModelPartBuilder.create()
                .uv(64, 64).cuboid(2.0F, -5.0F, 0.0F, 8.0F, 10.0F, 0.7F)
                .uv(64, 64).cuboid(9.0F, -3.0F, 0.0F, 6.0F, 8.0F, 0.6F)
                .uv(64, 64).cuboid(1.0F, 3.0F, 0.0F, 7.0F, 5.0F, 0.6F);
        root.addChild("wing_left", left, ModelTransform.of(0.0F, 0.0F, 2.25F, 0.0F, 0.0F, -0.10F));
        root.addChild("wing_right", right, ModelTransform.of(0.0F, 0.0F, 2.25F, 0.0F, 0.0F, 0.10F));
        return TexturedModelData.of(data, 128, 128).createModel();
    }

    private static ModelPart buildKatana() {
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(96, 64).cuboid(-0.45F, -8.0F, 0.0F, 0.9F, 15.0F, 0.65F)
                .uv(96, 64).cuboid(-1.5F, -7.2F, -0.25F, 3.0F, 0.8F, 1.1F)
                .uv(96, 64).cuboid(-0.5F, -11.0F, 0.0F, 1.0F, 3.0F, 0.8F)
                .uv(96, 64).cuboid(-0.8F, 6.5F, -0.1F, 1.6F, 1.1F, 0.9F);
        return modelPart("katana", builder, ModelTransform.of(2.6F, 2.5F, 2.2F, 0.0F, 0.0F, -0.30F));
    }

    private static ModelPart buildCrown() {
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(64, 96).cuboid(-4.5F, -8.5F, -4.5F, 9.0F, 1.0F, 1.0F)
                .uv(64, 96).cuboid(-4.5F, -8.5F, 3.5F, 9.0F, 1.0F, 1.0F)
                .uv(64, 96).cuboid(-4.5F, -8.5F, -3.5F, 1.0F, 1.0F, 7.0F)
                .uv(64, 96).cuboid(3.5F, -8.5F, -3.5F, 1.0F, 1.0F, 7.0F)
                .uv(64, 96).cuboid(-0.7F, -11.0F, -4.4F, 1.4F, 2.7F, 1.2F)
                .uv(64, 96).cuboid(-3.7F, -10.0F, -4.4F, 1.1F, 1.8F, 1.2F)
                .uv(64, 96).cuboid(2.6F, -10.0F, -4.4F, 1.1F, 1.8F, 1.2F);
        return modelPart("crown", builder, ModelTransform.NONE);
    }

    private static ModelPart buildWitchHat() {
        // Match the original runtime Claude hat silhouette: wide brim and four tapered tiers.
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(96, 96).cuboid(-6.5F, -9.0F, -6.5F, 13.0F, 1.0F, 13.0F)
                .uv(96, 96).cuboid(-4.5F, -13.0F, -4.5F, 9.0F, 4.0F, 9.0F)
                .uv(96, 96).cuboid(-3.0F, -17.0F, -3.0F, 6.0F, 4.0F, 6.0F)
                .uv(96, 96).cuboid(-1.5F, -20.0F, -1.5F, 3.0F, 3.0F, 3.0F);
        return modelPart("witch_hat", builder, ModelTransform.NONE);
    }

    private static ModelPart buildWitchHatDetails() {
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(96, 112).cuboid(-4.55F, -12.25F, -4.62F, 9.1F, 1.15F, 0.28F)
                .uv(96, 112).cuboid(-1.45F, -12.65F, -4.90F, 2.9F, 1.85F, 0.35F)
                .uv(96, 112).cuboid(-0.62F, -12.28F, -5.03F, 1.24F, 1.05F, 0.25F);
        return modelPart("witch_hat_details", builder, ModelTransform.NONE);
    }

    private static void ensureSkin(MinecraftClient client) {
        if (baseSkin != null || client == null) return;
        try {
            if (Files.isRegularFile(SKIN_FILE)) {
                try (InputStream in = Files.newInputStream(SKIN_FILE)) {
                    baseSkin = NativeImage.read(in);
                }
            } else {
                try (InputStream in = client.getResourceManager().getResource(DEFAULT_SKIN)
                        .orElseThrow().getInputStream()) {
                    baseSkin = NativeImage.read(in);
                }
            }
            if (baseSkin.getWidth() != 64 || baseSkin.getHeight() != 64) {
                baseSkin.close();
                baseSkin = null;
            }
        } catch (Exception ignored) {
            baseSkin = null;
        }
    }

    private static void refreshTexture(MinecraftClient client) {
        if (baseSkin == null || client == null || client.getTextureManager() == null) return;
        int wingColor = Features.COLORS[Math.floorMod(Config.i(Features.COSMETIC_DRAGON_WINGS_COLOR), Features.COLORS.length)];
        if (dynamicTexture != null && wingColor == lastWingColor) return;

        NativeImage composite = new NativeImage(128, 128, true);
        fill(composite, 0, 0, 128, 128, 0x00000000);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) composite.setColorArgb(x, y, baseSkin.getColorArgb(x, y));
        }
        fillPattern(composite, 64, 64, 32, 32, wingColor, darken(wingColor, 0.58F));
        fillPattern(composite, 96, 64, 32, 32, 0xFFD9E1EE, 0xFF596477);
        fillPattern(composite, 64, 96, 32, 32, 0xFFFFD43B, 0xFFB87915);
        fillPattern(composite, 96, 96, 32, 16, 0xFF34245F, 0xFF17112F);
        fillPattern(composite, 96, 112, 32, 16, 0xFFFF982E, 0xFF742E28);

        if (dynamicTexture != null) client.getTextureManager().destroyTexture(dynamicTexture);
        dynamicTexture = PREVIEW_TEXTURE;
        client.getTextureManager().registerTexture(
                dynamicTexture,
                new NativeImageBackedTexture(() -> "Plus Visual 3D cosmetics preview", composite)
        );
        lastWingColor = wingColor;
    }

    private static void fillPattern(NativeImage image, int x, int y, int width, int height, int primary, int secondary) {
        fill(image, x, y, width, height, primary);
        int second = secondary;
        for (int row = 0; row < height; row += 8) {
            for (int col = 0; col < width; col += 8) {
                if (((row / 8) + (col / 8)) % 2 == 0) {
                    for (int py = row; py < Math.min(height, row + 2); py++)
                        for (int px = col; px < Math.min(width, col + 8); px++) image.setColorArgb(x + px, y + py, second);
                }
            }
        }
    }

    private static void fill(NativeImage image, int x, int y, int width, int height, int argb) {
        for (int py = y; py < y + height; py++)
            for (int px = x; px < x + width; px++) image.setColorArgb(px, py, argb);
    }

    private static int darken(int color, float amount) {
        int a = color & 0xFF000000;
        int r = (int) (((color >>> 16) & 255) * amount);
        int g = (int) (((color >>> 8) & 255) * amount);
        int b = (int) ((color & 255) * amount);
        return a | (r << 16) | (g << 8) | b;
    }

    /**
     * Opens a native file picker and, on selection, validates a standard 64x64 PNG skin and
     * persists it locally.
     *
     * Uses LWJGL's TinyFileDialogs (native OS dialog via GLFW/LWJGL, already bundled with the
     * vanilla Minecraft client — see lwjgl-tinyfd in the game's own library list) instead of
     * java.awt.FileDialog: AWT dialogs don't work when the JVM is started with
     * -Djava.awt.headless=true, which is how most Minecraft launchers start the game process.
     * TinyFileDialogs talks to the OS directly and is unaffected by that flag.
     */
    public static String chooseAndLoadSkin(MinecraftClient client) {
        String selectedPath;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                    "Выберите PNG-скин Minecraft (64×64)", null, filters, "PNG images", false);
        } catch (Exception exception) {
            return "Не удалось открыть диалог выбора файла: " + exception.getMessage();
        }
        if (selectedPath == null) return "Загрузка скина отменена";

        Path selected = Path.of(selectedPath);
        try {
            NativeImage candidate;
            try (InputStream in = Files.newInputStream(selected)) {
                candidate = NativeImage.read(in);
            }
            int w = candidate.getWidth(), h = candidate.getHeight();
            candidate.close();
            if (w != 64 || h != 64) return "Нужен PNG-скин 64×64 (классический/современный формат)";

            Files.createDirectories(SKIN_FILE.getParent());
            Files.copy(selected, SKIN_FILE, StandardCopyOption.REPLACE_EXISTING);
            if (baseSkin != null) baseSkin.close();
            baseSkin = null;
            if (dynamicTexture != null) {
                client.getTextureManager().destroyTexture(dynamicTexture);
                dynamicTexture = null;
            }
            lastWingColor = Integer.MIN_VALUE;
            ensureSkin(client);
            refreshTexture(client);
            return "Скин загружен: " + selected.getFileName();
        } catch (Exception exception) {
            return "Не удалось загрузить PNG: " + exception.getMessage();
        }
    }
}
