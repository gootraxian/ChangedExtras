package com.katt.changedextras.entity;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.client.renderer.JammerRenderTypes;
import com.katt.changedextras.entity.beasts.JammerEntity;
import com.katt.changedextras.entity.model.JammerEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleWolfModel;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class JammerRenderer extends AdvancedHumanoidRenderer<JammerEntity, JammerEntityModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "textures/entity/jammer/jammer.png");
    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "textures/entity/jammer/glow.png");
    private static final ResourceLocation OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "textures/entity/jammer/outline.png");
    private final JammerEntityModel outlineModel;

    public JammerRenderer(EntityRendererProvider.Context context) {
        super(context,
                new JammerEntityModel(context.bakeLayer(JammerEntityModel.LAYER_LOCATION)),
                ArmorLatexMaleWolfModel.MODEL_SET,
                0.5f);
        this.outlineModel = new JammerEntityModel(context.bakeLayer(JammerEntityModel.OUTLINE_LAYER_LOCATION));

        this.addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        this.addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
        this.addLayer(new JammerGlowLayer(this, outlineModel));
        this.addLayer(CustomEyesLayer.builder(this, context.getModelSet())
                .withSclera(CustomEyesLayer::scleraColor)
                .withLeftIris((entity, bpi) -> CustomEyesLayer.ColorData.ofColor(Color3.fromInt(0x50BED6)))
                .withRightIris((entity, bpi) -> CustomEyesLayer.ColorData.ofColor(Color3.fromInt(0x50BED6)))
                .build());
    }

    @Override
    public ResourceLocation getTextureLocation(JammerEntity entity) {
        return TEXTURE;
    }

    private static float[] activeGlowColor(JammerEntity entity) {
        if (!entity.isVip()) {
            return new float[]{0x50 / 255.0F, 0xBE / 255.0F, 0xD6 / 255.0F};
        }

        float hue = ((entity.tickCount + Minecraft.getInstance().getFrameTime()) % 120.0F) / 120.0F;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F);
        return new float[]{(rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F};
    }

    private static class JammerGlowLayer extends RenderLayer<JammerEntity, JammerEntityModel> {
        private final JammerEntityModel outlineModel;

        private JammerGlowLayer(JammerRenderer parent, JammerEntityModel outlineModel) {
            super(parent);
            this.outlineModel = outlineModel;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, JammerEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float[] color = activeGlowColor(entity);

            var glowConsumer = buffer.getBuffer(RenderType.eyes(GLOW_TEXTURE));
            getParentModel().renderToBuffer(poseStack, glowConsumer, 15728640, OverlayTexture.NO_OVERLAY,
                    color[0], color[1], color[2], 1.0F);

            outlineModel.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
            outlineModel.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            var outlineConsumer = buffer.getBuffer(JammerRenderTypes.frontCulledEmissive(OUTLINE_TEXTURE));
            outlineModel.renderToBuffer(poseStack, outlineConsumer, 15728640, OverlayTexture.NO_OVERLAY,
                    color[0], color[1], color[2], 1.0F);
        }
    }
}
