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
 * Low-poly katana cosmetic, worn diagonally across the back ({@code PlayerEntityModel.body}).
 * Fixed steel/gold color, like {@link GoldCrownFeatureRenderer} — see Cosmetics.java.
 */
public final class KatanaFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.of("pvpplus", "textures/cosmetic/katana.png");

    private final ModelPart katana;

    public KatanaFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.katana = createKatanaModel();
    }

    private static ModelPart createKatanaModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Built straight (hilt up, blade down); the diagonal "worn on the back" look comes from
        // the child's own ModelTransform rotation below, not from the cuboid coordinates.
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(48, 0).cuboid(-0.65F, -3.2F, -0.55F, 1.3F, 3.2F, 1.1F)  // wrapped handle
                .uv(40, 0).cuboid(-1.55F, -0.35F, -0.7F, 3.1F, 0.55F, 1.4F) // ornate tsuba
                .uv(0, 0).cuboid(-0.42F, 0.2F, -0.3F, 0.84F, 13.0F, 0.6F) // longer blade
                .uv(24, 0).cuboid(-0.12F, 0.8F, -0.34F, 0.18F, 11.7F, 0.08F) // blade fuller
                .uv(32, 0).cuboid(-0.82F, -2.8F, -0.62F, 1.64F, 0.35F, 1.24F); // pommel

        // Body pivot is near the top of the torso (y=0) with the hips around y=12, so the pivot
        // below sits at upper-back height, with the blade running diagonally down toward a hip.
        float roll = (float) Math.toRadians(-24.0);
        root.addChild("katana", builder, ModelTransform.of(0.3F, 2.0F, 2.45F, 0.0F, 0.0F, roll));
        return TexturedModelData.of(data, 64, 32).createModel().getChild("katana");
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
        if (!Config.on(Features.COSMETIC_KATANA)) return;
        if (mc.player == null || state.id != mc.player.getId()) return;
        matrices.push();
        getContextModel().body.applyTransform(matrices);
        queue.submitModelPart(
                katana,
                matrices,
                RenderLayers.entityCutoutNoCull(TEXTURE),
                light,
                OverlayTexture.DEFAULT_UV,
                null,
                false,
                false);
        matrices.pop();
    }
}
