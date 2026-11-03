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

public final class WitchHatFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.of("pvpplus", "textures/cosmetic/witch_hat.png");
    private static final Identifier DETAILS_TEXTURE =
            Identifier.of("pvpplus", "textures/cosmetic/witch_hat_details.png");

    private final ModelPart hat;
    private final ModelPart details;

    public WitchHatFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.hat = createHatModel();
        this.details = createDetailsModel();
    }

    private static ModelPart createHatModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Low-poly witch hat: broad brim and a tapered, slightly crooked crown.
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-6.5F, -9.0F, -6.5F, 13.0F, 1.0F, 13.0F)
                .uv(0, 16).cuboid(-4.5F, -13.0F, -4.5F, 9.0F, 4.0F, 9.0F)
                .uv(40, 16).cuboid(-3.0F, -17.0F, -3.0F, 6.0F, 4.0F, 6.0F)
                .uv(64, 16).cuboid(-1.5F, -20.0F, -1.5F, 3.0F, 3.0F, 3.0F);

        root.addChild("witch_hat", builder, ModelTransform.NONE);
        return TexturedModelData.of(data, 128, 64).createModel().getChild("witch_hat");
    }

    /** Small separate geometry keeps the original Claude model silhouette intact. */
    private static ModelPart createDetailsModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartBuilder detail = ModelPartBuilder.create()
                // Pumpkin-orange ribbon around the lower crown.
                .uv(0, 0).cuboid(-4.55F, -12.25F, -4.62F, 9.1F, 1.15F, 0.28F)
                .uv(0, 0).cuboid(-4.55F, -12.25F, 4.34F, 9.1F, 1.15F, 0.28F)
                .uv(0, 0).cuboid(-4.62F, -12.25F, -4.34F, 0.28F, 1.15F, 8.68F)
                .uv(0, 0).cuboid(4.34F, -12.25F, -4.34F, 0.28F, 1.15F, 8.68F)
                // Small golden front buckle and violet centre stone.
                .uv(32, 0).cuboid(-1.45F, -12.65F, -4.88F, 2.9F, 1.85F, 0.35F)
                .uv(64, 0).cuboid(-0.62F, -12.28F, -5.02F, 1.24F, 1.05F, 0.25F)
                // Two tiny amethyst studs set into the ribbon.
                .uv(64, 0).cuboid(-3.25F, -12.35F, -4.82F, 0.85F, 0.85F, 0.3F)
                .uv(64, 0).cuboid(2.40F, -12.35F, -4.82F, 0.85F, 0.85F, 0.3F)
                // Discreet stitching along the front and rear brim edge.
                .uv(96, 0).cuboid(-4.6F, -8.95F, -6.45F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(-1.9F, -8.95F, -6.45F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(1.9F, -8.95F, -6.45F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(4.0F, -8.95F, -6.45F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(-4.6F, -8.95F, 6.23F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(-1.9F, -8.95F, 6.23F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(1.9F, -8.95F, 6.23F, 1.0F, 0.18F, 0.22F)
                .uv(96, 0).cuboid(4.0F, -8.95F, 6.23F, 1.0F, 0.18F, 0.22F);
        root.addChild("witch_hat_details", detail, ModelTransform.NONE);
        return TexturedModelData.of(data, 128, 64).createModel().getChild("witch_hat_details");
    }

    @Override
    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance) {
        // The option is saved locally. Render only for the local player's entity id;
        // other players, including co-op members, never receive this cosmetic locally.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Config.on(Features.COSMETIC_WITCH_HAT)) return;
        if (mc.player == null || state.id != mc.player.getId()) return;
        matrices.push();
        // Follow the player's head rotation and place the hat directly above it.
        // Minecraft 1.21.11 renamed ModelPart#rotate(MatrixStack) to #applyTransform(MatrixStack).
        getContextModel().getHead().applyTransform(matrices);
        queue.submitModelPart(
                hat,
                matrices,
                RenderLayers.entityCutoutNoCull(TEXTURE),
                light,
                OverlayTexture.DEFAULT_UV,
                null,
                false,
                false);
        queue.submitModelPart(
                details,
                matrices,
                RenderLayers.entityCutoutNoCull(DETAILS_TEXTURE),
                light,
                OverlayTexture.DEFAULT_UV,
                null,
                false,
                false);
        matrices.pop();
    }
}
