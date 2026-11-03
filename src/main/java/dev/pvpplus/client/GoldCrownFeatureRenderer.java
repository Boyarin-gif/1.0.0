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
 * Low-poly golden crown cosmetic: a band around the head plus four small points. Fixed gold
 * color (no palette tint, unlike {@link DragonWingsFeatureRenderer}) — see Cosmetics.java.
 */
public final class GoldCrownFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.of("pvpplus", "textures/cosmetic/gold_crown.png");

    private final ModelPart crown;

    public GoldCrownFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.crown = createCrownModel();
    }

    private static ModelPart createCrownModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartBuilder builder = ModelPartBuilder.create()
                // Band around the head, sitting just above eye level.
                .uv(0, 0).cuboid(-4.6F, -7.2F, -4.75F, 9.2F, 1.4F, 0.3F)   // front
                .uv(0, 0).cuboid(-4.6F, -7.2F, 4.45F, 9.2F, 1.4F, 0.3F)    // back
                .uv(0, 0).cuboid(-4.75F, -7.2F, -4.6F, 0.3F, 1.4F, 9.2F)   // left
                .uv(0, 0).cuboid(4.45F, -7.2F, -4.6F, 0.3F, 1.4F, 9.2F)    // right
                // Four small points, one per side of the band.
                .uv(0, 17).cuboid(-0.5F, -8.4F, -4.95F, 1.0F, 1.3F, 0.5F)  // front point
                .uv(0, 17).cuboid(-0.5F, -8.4F, 4.45F, 1.0F, 1.3F, 0.5F)   // back point
                .uv(0, 17).cuboid(-4.95F, -8.4F, -0.5F, 0.5F, 1.3F, 1.0F)  // left point
                .uv(0, 17).cuboid(4.45F, -8.4F, -0.5F, 0.5F, 1.3F, 1.0F)
                // Small raised central jewel and four finer side studs.
                .uv(32, 16).cuboid(-0.8F, -7.65F, -5.08F, 1.6F, 1.0F, 0.38F)
                .uv(40, 16).cuboid(-3.1F, -7.45F, -4.92F, 0.55F, 0.7F, 0.25F)
                .uv(40, 16).cuboid(2.55F, -7.45F, -4.92F, 0.55F, 0.7F, 0.25F)
                .uv(40, 16).cuboid(-0.3F, -7.45F, 4.68F, 0.6F, 0.7F, 0.25F);

        root.addChild("gold_crown", builder, ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 32).createModel().getChild("gold_crown");
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
        if (!Config.on(Features.COSMETIC_GOLD_CROWN)) return;
        if (mc.player == null || state.id != mc.player.getId()) return;
        matrices.push();
        getContextModel().getHead().applyTransform(matrices);
        queue.submitModelPart(
                crown,
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
