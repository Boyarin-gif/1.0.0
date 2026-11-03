package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Applies the selected Item Glow palette to Minecraft's glint atlas. ItemStackMixin still decides
 * which held stacks gain glint. The atlas itself is shared by vanilla, so naturally enchanted
 * items also inherit the chosen tint while Item Glow is enabled.
 */
public final class ItemGlowRenderer {
    private static final Identifier GLINT_TEXTURE =
            Identifier.ofVanilla("textures/misc/enchanted_glint_item.png");

    private static NativeImage original;
    private static NativeImageBackedTexture activeTexture;
    private static boolean applied;
    private static int activeColor = Integer.MIN_VALUE;

    private ItemGlowRenderer() {}

    public static void tick(MinecraftClient client) {
        if (client == null || client.getResourceManager() == null || client.getTextureManager() == null) return;
        if (!Config.on("itemglow")) {
            restore(client);
            return;
        }

        int palette = Math.floorMod(Config.i("itemglow.color"), Features.COLORS.length);
        int color = Features.COLORS[palette];
        if (applied && activeColor == color
                && client.getTextureManager().getTexture(GLINT_TEXTURE) == activeTexture) return;

        try {
            ensureOriginal(client);
            if (original == null) return;
            NativeImage tinted = tint(original, color);
            activeTexture = new NativeImageBackedTexture(() -> "Plus Visual Item Glow", tinted);
            client.getTextureManager().registerTexture(GLINT_TEXTURE, activeTexture);
            applied = true;
            activeColor = color;
        } catch (Exception ignored) {
            // A texture reload or a missing resource must not take down the render thread.
        }
    }

    public static void restore(MinecraftClient client) {
        if (!applied || original == null || client == null || client.getTextureManager() == null) return;
        try {
            activeTexture = null;
            client.getTextureManager().registerTexture(
                    GLINT_TEXTURE,
                    new NativeImageBackedTexture(() -> "Plus Visual vanilla glint", copy(original))
            );
        } catch (Exception ignored) {
        }
        applied = false;
        activeColor = Integer.MIN_VALUE;
    }

    private static void ensureOriginal(MinecraftClient client) throws Exception {
        if (original != null) return;
        Resource resource = client.getResourceManager().getResource(GLINT_TEXTURE)
                .orElseThrow(() -> new IllegalStateException("Vanilla glint texture not found"));
        try (InputStream stream = resource.getInputStream()) {
            original = NativeImage.read(stream);
        }
    }

    private static NativeImage copy(NativeImage source) {
        NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) result.setColorArgb(x, y, source.getColorArgb(x, y));
        }
        return result;
    }

    /** Work with NativeImage's public ARGB accessors, keeping channel order explicit. */
    private static NativeImage tint(NativeImage source, int argb) {
        int tr = (argb >>> 16) & 255;
        int tg = (argb >>> 8) & 255;
        int tb = argb & 255;
        NativeImage result = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getColorArgb(x, y);
                int a = (pixel >>> 24) & 255;
                int r = (pixel >>> 16) & 255;
                int g = (pixel >>> 8) & 255;
                int b = pixel & 255;
                // Keep a visible base glint even for near-black palette colors.
                int nr = (r * (tr + 48)) / 303;
                int ng = (g * (tg + 48)) / 303;
                int nb = (b * (tb + 48)) / 303;
                result.setColorArgb(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return result;
    }
}
