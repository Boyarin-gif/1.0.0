package dev.pvpplus.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Low-poly dragon wings cosmetic, attached to the player's back ({@code PlayerEntityModel.body}).
 * The texture is a near-white base so it can be tinted at render time with the color chosen in
 * {@link CosmeticsScreen} (Features.COSMETIC_DRAGON_WINGS_COLOR), reusing the same palette as the
 * existing Item Glow color picker (Features.COLORS).
 *
 * Scope/verification note: the attachment pivot, spread angle and tint-submit overload were
 * checked against the real Yarn 1.21.11 mappings for {@code BipedEntityModel.body} (public field,
 * not a getter) and {@code RenderCommandQueue#submitModelPart(..., int tintedColor, ...)}, but the
 * exact visual placement on the back was not checked in a running client — see VALIDATION.md.
 */
public final class DragonWingsFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.of("pvpplus", "textures/cosmetic/dragon_wings.png");

    private final ModelPart wings;

    public DragonWingsFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.wings = createWingsModel();
    }

    private static ModelPart createWingsModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        addWing(root, "wing_right", 1);
        addWing(root, "wing_left", -1);
        return TexturedModelData.of(data, 64, 32).createModel();
    }

    /**
     * One wing: a short arm bone plus three fanned-out membrane panels. The left wing reuses the
     * negative and positive local X ranges respectively, so their geometry is symmetric around
     * the player's spine; UV mirroring is not used as a substitute for spatial mirroring.
     */
    private static void addWing(ModelPartData root, String name, int side) {
        // Both sides use positive cuboid dimensions. The left wing is positioned in
        // negative local X; mirrored() only mirrors UVs and does not mirror geometry.
        float x = side > 0 ? 0.0F : -9.0F;
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(0, 0).cuboid(x, -0.6F, -0.5F, 9.0F, 1.0F, 1.0F)
                .uv(0, 2).cuboid(x + (side > 0 ? 1.0F : 0.0F), -0.4F, -0.2F, 8.0F, 0.3F, 6.5F)
                .uv(0, 10).cuboid(x + (side > 0 ? 2.0F : 1.0F), 0.3F, -0.2F, 6.0F, 0.3F, 5.5F)
                .uv(0, 18).cuboid(x + (side > 0 ? 3.0F : 2.0F), 1.0F, -0.2F, 4.0F, 0.3F, 4.0F);
        float pivotX = 2.0F * side;
        float yaw = (float) Math.toRadians(side * 12.0);
        float roll = (float) Math.toRadians(side * 7.0);
        root.addChild(name, builder, ModelTransform.of(pivotX, 0.0F, 2.1F, 0.0F, yaw, roll));
    }

    @Override
    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Config.on(Features.COSMETIC_DRAGON_WINGS)) return;
        if (mc.player == null || state.id != mc.player.getId()) return;
        int tint = Config.color(Features.COSMETIC_DRAGON_WINGS_COLOR);
        matrices.push();
        // Follow the torso's rotation/lean so the wings move with the body, not just the world.
        getContextModel().body.applyTransform(matrices);
        queue.submitModelPart(
                wings,
                matrices,
                RenderLayers.entityCutoutNoCull(TEXTURE),
                light,
                OverlayTexture.DEFAULT_UV,
                null,
                tint,
                null);
        matrices.pop();
    }
}
